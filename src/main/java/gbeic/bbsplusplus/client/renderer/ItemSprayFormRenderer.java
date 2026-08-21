package gbeic.bbsplusplus.client.renderer;

import com.mojang.blaze3d.systems.RenderSystem;
import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.blocks.entities.ModelBlockEntity;
import mchorse.bbs_mod.client.BBSRendering;
import mchorse.bbs_mod.events.ModelBlockEntityUpdateCallback;
import mchorse.bbs_mod.forms.ITickable;
import mchorse.bbs_mod.forms.entities.IEntity;
import mchorse.bbs_mod.forms.renderers.FormRenderType;
import mchorse.bbs_mod.forms.renderers.FormRenderingContext;
import mchorse.bbs_mod.forms.renderers.FormRenderer;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.utils.MatrixStackUtils;
import mchorse.bbs_mod.utils.colors.Color;
import gbeic.bbsplusplus.BBSAddonsSettings;
import gbeic.bbsplusplus.forms.ItemSprayForm;
import gbeic.bbsplusplus.mixin.GameRendererAccessor;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.Frustum;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.registry.RegistryKey;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.item.ItemStack;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3d;
import org.joml.Vector3f;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.WeakHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 物品喷射形态渲染器。
 *
 * 核心架构：
 * - render3D()：每帧调用，负责缓存当前形态在世界空间中的原点位置和旋转矩阵，并按预览/世界语义绘制辅助线。
 * - tick()：每游戏刻调用，利用 render3D() 的缓存来生成物品，并将新物品丢进 GLOBAL_ITEMS 全局列表。
 * - 确定性采样：实时模式关闭时按 simulationTime 直接生成当前帧粒子，这是默认预览行为。
 * - 本地预览：实时模式开启但处于 UI/模型方块编辑器预览时，使用独立本地粒子池显示实时效果。
 * - 全局更新钩子（ClientTickEvents.END_CLIENT_TICK）：接管所有在半空中的粒子的物理飞行、阻力、重力与碰撞。
 * - 全局渲染钩子（WorldRenderEvents.AFTER_ENTITIES）：负责在世界渲染阶段独立绘制飞行粒子，并按设置做距离、视锥与数量保护。
 */
public class ItemSprayFormRenderer extends FormRenderer<ItemSprayForm> implements ITickable
{
    private static final int MAX_PREVIEW_ITEMS = 2048;
    private static final int DEFAULT_MAX_RENDERED_ITEMS = 1024;
    private static final int DEFAULT_MAX_RENDER_DISTANCE = 0;
    private static final double CENTER_STOP_EPSILON = 0.000001D;
    private static final Matrix4f IDENTITY_TRANSFORM = new Matrix4f();

    // ==================== 全局粒子系统 ====================
    /** 存放所有已经发射出来的独立物理粒子。使用 CopyOnWriteArrayList 防止并发修改异常 */
    public static final List<SprayedItem> GLOBAL_ITEMS = new CopyOnWriteArrayList<>();
    /** 存放确定性预览在真实世界中的最后一次可见快照，让预览粒子脱离源模型方块裁剪继续显示 */
    private static final List<SprayedItem> DETERMINISTIC_WORLD_ITEMS = new CopyOnWriteArrayList<>();
    /** 记录每个模型方块源在当前世界渲染帧是否已经清过旧预览，避免同一模型内多个喷射形态互相覆盖 */
    private static final Map<Source, Long> DETERMINISTIC_SOURCE_CLEAR_FRAMES = new HashMap<>();
    /** 记录模型方块实体和真实方块位置的对应关系，用来判断发射源是否还存在 */
    private static final Map<IEntity, Source> MODEL_BLOCK_SOURCES = new WeakHashMap<>();
    /** IRLights 未作为编译依赖引入，运行时通过反射识别它自己的阴影烘焙状态 */
    private static Method irliteShadowBakeStateIsBaking;
    private static boolean irliteShadowBakeStateChecked;
    private static long currentWorldRenderFrame;

    static
    {
        ModelBlockEntityUpdateCallback.EVENT.register(modelBlock ->
        {
            World world = modelBlock.getWorld();
            IEntity entity = modelBlock.getEntity();

            if (world != null && entity != null)
            {
                MODEL_BLOCK_SOURCES.put(entity, new Source(world, modelBlock.getPos(), modelBlock));
            }
        });

        // 全局物理更新钩子 (独立于发射器本身，即使发射器被破坏，已存在的粒子仍会按物理规律继续飞行)
        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_CLIENT_TICK.register(client ->
        {
            if (client.world == null)
            {
                GLOBAL_ITEMS.clear();
                DETERMINISTIC_WORLD_ITEMS.clear();
                DETERMINISTIC_SOURCE_CLEAR_FRAMES.clear();

                return;
            }

            if (client.isPaused()) return;

            updateItems(GLOBAL_ITEMS, client.world);
            DETERMINISTIC_WORLD_ITEMS.removeIf((item) -> item.source != null && !item.source.isAlive(client.world));
            DETERMINISTIC_SOURCE_CLEAR_FRAMES.keySet().removeIf((source) -> !source.isAlive(client.world));
        });

        net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents.START.register(ctx -> currentWorldRenderFrame++);

        // 全局渲染钩子 (负责把所有飞行的粒子独立画出来)
        net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents.AFTER_ENTITIES.register(ctx ->
        {
            if (isShadowLikePass()) return;
            if (GLOBAL_ITEMS.isEmpty() && DETERMINISTIC_WORLD_ITEMS.isEmpty()) return;

            net.minecraft.client.util.math.MatrixStack stack = ctx.matrixStack();
            Vec3d camPos = ctx.camera().getPos();
            Frustum frustum = isFrustumCullingEnabled() ? ctx.frustum() : null;
            double maxDistanceSq = getMaxRenderDistanceSquared();

            ItemSprayWorldItemRenderer.renderWorldItems(stack, ctx.tickDelta(), ctx.world(), 0, OverlayTexture.DEFAULT_UV, camPos, frustum, getMaxRenderedItems(), maxDistanceSq, GLOBAL_ITEMS, DETERMINISTIC_WORLD_ITEMS);
        });
    }

    // ==================== 发射源数据结构 ====================
    static class Source
    {
        public final RegistryKey<World> worldKey;
        public final BlockPos pos;
        /** 只保存模型方块实例身份码，避免 Source 反向强引用实体导致 WeakHashMap 无法释放旧条目 */
        public final int blockEntityIdentity;

        public Source(World world, BlockPos pos, ModelBlockEntity modelBlock)
        {
            this.worldKey = world.getRegistryKey();
            this.pos = pos.toImmutable();
            this.blockEntityIdentity = System.identityHashCode(modelBlock);
        }

        public boolean isAlive(World world)
        {
            ModelBlockEntity modelBlock = this.getModelBlock(world);

            if (modelBlock == null) return false;

            try
            {
                return modelBlock.getProperties() != null && modelBlock.getProperties().isEnabled();
            }
            catch (Throwable ignored)
            {
                return false;
            }
        }

        public ModelBlockEntity getModelBlock(World world)
        {
            if (world == null || !world.getRegistryKey().equals(this.worldKey))
            {
                return null;
            }

            if (world.getBlockEntity(this.pos) instanceof ModelBlockEntity modelBlock
                && System.identityHashCode(modelBlock) == this.blockEntityIdentity)
            {
                return modelBlock;
            }

            return null;
        }

        @Override
        public boolean equals(Object object)
        {
            if (this == object)
            {
                return true;
            }

            if (!(object instanceof Source source))
            {
                return false;
            }

            return this.blockEntityIdentity == source.blockEntityIdentity
                && Objects.equals(this.worldKey, source.worldKey)
                && Objects.equals(this.pos, source.pos);
        }

        @Override
        public int hashCode()
        {
            return Objects.hash(this.worldKey, this.pos, this.blockEntityIdentity);
        }
    }

    /**
     * 单次发射采样结果。
     *
     * offset 是粒子生成点相对发射器原点的局部偏移，direction 是粒子初速度方向。
     * 实时模式和确定性模式都通过该结构生成粒子，避免两套采样逻辑产生不同形态。
     */
    private static class EmissionSample
    {
        public final Vector3f offset = new Vector3f();
        public final Vector3f direction = new Vector3f(0F, 0F, 1F);
    }

    // ==================== 喷射物品数据结构 ====================
    static class SprayedItem
    {
        public ItemStack stack;
        /** 当前世界坐标位置 */
        public Vector3d pos = new Vector3d();
        /** 上一刻世界坐标位置 */
        public Vector3d prevPos = new Vector3d();
        /** 世界空间速度向量 */
        public Vector3d velocity = new Vector3d();
        public Vector3f rotation = new Vector3f();
        public Vector3f prevRotation = new Vector3f();
        public Vector3f initialRotation = new Vector3f();
        public Vector3f rotationSpeed = new Vector3f();
        public int age;
        public int maxAge;
        public boolean stopped;
        public boolean stopAtCenter;
        public Vector3d centerStopTarget;
        public Vector3d centerStopDirection;

        // 绑定属于它自己的物理与渲染参数，脱离发射器后依然能独立结算
        public boolean useGravity;
        public float gravitySpeed;
        public boolean useCollision;
        public boolean billboard;
        public float scale;
        public int scaleInTime;
        public float renderAge = -1F;
        public Matrix3f renderRotation;
        public Color color;
        /** 生成该世界粒子的渲染器，用于切换到确定性采样时清理旧的实时粒子 */
        public final ItemSprayFormRenderer owner;
        /** 如果来自模型方块，则记录源方块；源方块被破坏时粒子会在下次全局更新中移除 */
        public final Source source;

        public SprayedItem(ItemSprayFormRenderer owner, ItemStack stack, Vector3d pos, Vector3d velocity, Vector3f initialRotation, Vector3f rotationSpeed, int maxAge, boolean useGravity, float gravitySpeed, boolean useCollision, boolean billboard, float scale, int scaleInTime, Color color, Source source)
        {
            this.stack = stack;
            this.pos.set(pos);
            this.prevPos.set(pos);
            this.velocity.set(velocity);
            this.rotation.set(initialRotation);
            this.prevRotation.set(initialRotation);
            this.initialRotation.set(initialRotation);
            this.maxAge = maxAge;
            this.useGravity = useGravity;
            this.gravitySpeed = gravitySpeed;
            this.useCollision = useCollision;
            this.billboard = billboard;
            this.scale = scale;
            this.scaleInTime = scaleInTime;
            this.color = new Color().copy(color);
            this.owner = owner;
            this.source = source;
            this.rotationSpeed.set(rotationSpeed);
        }

        public float getRenderScale(float tickDelta)
        {
            float target = Math.max(0F, this.scale);

            if (target <= 0F || this.scaleInTime <= 0)
            {
                return target;
            }

            float age = this.renderAge >= 0F ? this.renderAge : this.age + Math.max(0F, tickDelta);
            float progress = MathHelper.clamp(age / this.scaleInTime, 0F, 1F);
            float smooth = progress * progress * (3F - 2F * progress);

            return target * smooth;
        }
    }

    // ==================== 实例字段 ====================
    private final List<SprayedItem> deterministicItems = new ArrayList<>();
    private final List<SprayedItem> localPreviewItems = new ArrayList<>();
    private int cooldown = 0;
    private int localPreviewCooldown = 0;
    private int lastLocalPreviewTick = -1;
    private final Random random = new Random();

    /**
     * render3D() 每帧缓存的世界空间原点位置。
     * tick() 使用此值来确定物品的生成点。
     */
    private Vector3d cachedWorldOrigin = null;

    /**
     * render3D() 每帧缓存的世界空间旋转矩阵（3x3，仅方向，不含缩放/平移）。
     * tick() 使用此矩阵来将局部锥体方向变换到世界空向。
     */
    private Matrix3f cachedWorldRotation = null;

    /**
     * 最近一次真实世界渲染刷新缓存的时间。
     * 只有世界渲染刚刚刷新过缓存时，tick() 才允许继续向 GLOBAL_ITEMS 发射，防止编辑器预览或隐藏模型方块时沿用旧坐标。
     */
    private long lastWorldRenderTime = 0L;

    // ==================== 构造 ====================
    public ItemSprayFormRenderer(ItemSprayForm form)
    {
        super(form);
    }

    // ==================== tick：仅负责真实世界发射 ====================
    @Override
    public void tick(IEntity entity)
    {
        if (entity instanceof mchorse.bbs_mod.forms.entities.MCEntity mcEntity)
        {
            net.minecraft.entity.Entity mcEnt = mcEntity.getMcEntity();
            if (mcEnt != null)
            {
                // 禁用实体的视锥体裁剪，防止源头方块在屏幕外时停止渲染从而导致缓存不再更新
                mcEnt.ignoreCameraFrustum = true;
            }
        }

        if (this.usesPreviewMode())
        {
            this.cooldown = 0;
            this.clearOwnedGlobalItems();

            return;
        }

        Source source = this.getSource(entity);
        World world = entity == null ? null : entity.getWorld();

        if (source != null && world != null && !source.isAlive(world))
        {
            this.clearDeterministicWorldItems(source);
            this.clearOwnedDeterministicWorldItems();
            this.clearOwnedGlobalItems();

            return;
        }

        this.clearDeterministicWorldItems(source);
        this.clearOwnedDeterministicWorldItems();

        if (this.cachedWorldOrigin != null && this.cachedWorldRotation != null && this.hasFreshWorldTransform())
        {
            this.cooldown = this.emit(this.cooldown, GLOBAL_ITEMS, this.cachedWorldOrigin, this.cachedWorldRotation, source);
        }
    }

    private boolean usesPreviewMode()
    {
        return !this.form.previewMode.get();
    }

    private boolean usesLocalPreview(FormRenderingContext context)
    {
        return !this.usesPreviewMode() && (context.type == FormRenderType.PREVIEW || context.modelRenderer);
    }

    private boolean usesGlobalDeterministicPreview(FormRenderingContext context)
    {
        return this.usesPreviewMode()
            && this.isMainWorldRenderPass()
            && context.type == FormRenderType.MODEL_BLOCK
            && !context.modelRenderer
            && !context.ui
            && this.getSource(context.entity) != null;
    }

    private boolean usesEditorPreviewSurface(FormRenderingContext context)
    {
        return context.type == FormRenderType.PREVIEW || context.modelRenderer || context.ui;
    }

    private boolean isItemDisplayRender(FormRenderingContext context)
    {
        return context.type == FormRenderType.ITEM_FP
            || context.type == FormRenderType.ITEM_TP
            || context.type == FormRenderType.ITEM_INVENTORY
            || context.type == FormRenderType.ITEM;
    }

    private void clearItemDisplayRenderState()
    {
        this.cachedWorldOrigin = null;
        this.cachedWorldRotation = null;
        this.lastWorldRenderTime = 0L;
        this.cooldown = 0;
        this.localPreviewCooldown = 0;
        this.lastLocalPreviewTick = -1;
        this.deterministicItems.clear();
        this.localPreviewItems.clear();
        this.clearOwnedGlobalItems();
        this.clearOwnedDeterministicWorldItems();
    }

    private void renderItemDisplayIcon(FormRenderingContext context)
    {
        BBSModClient.getTextures().bindTexture(ICON);

        context.stack.push();

        try
        {
            context.stack.translate(0F, 0.5F, 0F);
            applyBillboard(context.stack);

            float half = 0.45F;
            boolean flipInventoryIconVertically = context.type == FormRenderType.ITEM_INVENTORY;
            float u0 = 0F;
            float u1 = 1F;
            float v0 = flipInventoryIconVertically ? 1F : 0F;
            float v1 = flipInventoryIconVertically ? 0F : 1F;
            Matrix4f matrix = context.stack.peek().getPositionMatrix();
            BufferBuilder builder = Tessellator.getInstance().getBuffer();

            builder.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE);

            // GUI 物品渲染的矩阵朝向和手持不同，单独上下翻转图标。
            builder.vertex(matrix, -half, -half, 0F).texture(u0, v1).next();
            builder.vertex(matrix, half, -half, 0F).texture(u1, v1).next();
            builder.vertex(matrix, half, half, 0F).texture(u1, v0).next();
            builder.vertex(matrix, -half, half, 0F).texture(u0, v0).next();

            builder.vertex(matrix, -half, half, 0F).texture(u0, v0).next();
            builder.vertex(matrix, half, half, 0F).texture(u1, v0).next();
            builder.vertex(matrix, half, -half, 0F).texture(u1, v1).next();
            builder.vertex(matrix, -half, -half, 0F).texture(u0, v1).next();

            RenderSystem.setShader(GameRenderer::getPositionTexProgram);
            RenderSystem.defaultBlendFunc();
            RenderSystem.enableBlend();
            BufferRenderer.drawWithGlobalProgram(builder.end());
            RenderSystem.enableDepthTest();
        }
        finally
        {
            context.stack.pop();
        }
    }

    private void clearOwnedGlobalItems()
    {
        GLOBAL_ITEMS.removeIf((item) -> item.owner == this);
    }

    private void clearOwnedDeterministicWorldItems()
    {
        DETERMINISTIC_WORLD_ITEMS.removeIf((item) -> item.owner == this);
    }

    private void clearDeterministicWorldItems(Source source)
    {
        if (source != null)
        {
            DETERMINISTIC_WORLD_ITEMS.removeIf((item) -> source.equals(item.source));
        }
    }

    private void clearDeterministicWorldItemsForEditorPreview(FormRenderingContext context)
    {
        Source source = this.getSource(context.entity);

        if (source != null)
        {
            this.clearDeterministicWorldItems(source);
            DETERMINISTIC_SOURCE_CLEAR_FRAMES.remove(source);

            return;
        }

        this.clearOwnedDeterministicWorldItems();

        /* 表单预览可能使用临时实体，无法反查模型方块源。
         * 这时世界确定性快照本身也不该在预览界面背后显示，直接清掉这条专用缓存。 */
        DETERMINISTIC_WORLD_ITEMS.clear();
        DETERMINISTIC_SOURCE_CLEAR_FRAMES.clear();
    }

    private Source getSource(IEntity entity)
    {
        return entity == null ? null : MODEL_BLOCK_SOURCES.get(entity);
    }

    private boolean hasFreshWorldTransform()
    {
        return this.lastWorldRenderTime > 0L && System.currentTimeMillis() - this.lastWorldRenderTime < 500L;
    }

    private int emit(int currentCooldown, List<SprayedItem> target, Vector3d origin, Matrix3f rotation, Source source)
    {
        int frequency = this.form.frequency.get();
        boolean spawn = false;

        if (frequency <= 1)
        {
            spawn = true;
        }
        else
        {
            if (currentCooldown <= 0)
            {
                spawn = true;
                currentCooldown = frequency;
            }
            else
            {
                currentCooldown--;
            }
        }

        if (spawn)
        {
            this.spawnItems(target, origin, rotation, source);
        }

        return currentCooldown;
    }

    private void spawnItems(List<SprayedItem> target, Vector3d origin, Matrix3f rotation, Source source)
    {
        List<ItemStack> items = new ArrayList<>();
        for (mchorse.bbs_mod.settings.values.mc.ValueItemStack vis : this.form.items.getList())
        {
            if (vis.get() != null && !vis.get().isEmpty()) items.add(vis.get());
        }

        if (items.isEmpty())
        {
            return;
        }

        int amount = this.form.amount.get();
        float speed = this.form.speed.get();
        float range = this.form.range.get();
        boolean useGravity = this.form.gravity.get();
        boolean useCollision = this.form.collision.get();
        Color formColor = this.form.color.get();

        for (int i = 0; i < amount; i++)
        {
            ItemStack stack = items.get(this.random.nextInt(items.size())).copy();
            EmissionSample sample = this.createEmissionSample(this.random);

            // 速度散布
            float currentSpeed = speed;
            float speedOffset = this.form.speedOffset.get();
            if (speedOffset > 0)
            {
                currentSpeed += (this.random.nextFloat() * 2 - 1) * speedOffset;

                if (speed >= 0 && currentSpeed < 0)
                {
                    currentSpeed = 0;
                }
                else if (speed < 0 && currentSpeed > 0)
                {
                    currentSpeed = 0;
                }
            }

            // 局部速度向量
            Vector3f localVel = new Vector3f(sample.direction).normalize().mul(currentSpeed);
            rotation.transform(localVel);
            Vector3d velocity = new Vector3d(localVel.x(), localVel.y(), localVel.z());

            Vector3d pos = new Vector3d(origin);
            Vector3d centerStopTarget = this.shouldStopAtCenter(currentSpeed) ? new Vector3d(origin) : null;
            Vector3f localOffset = new Vector3f(sample.offset);

            rotation.transform(localOffset);
            pos.add(localOffset.x(), localOffset.y(), localOffset.z());

            // 位置散布在发射形状基础上继续叠加，保留旧工程的随机扩散手感。
            float scatter = this.form.scatter.get();
            if (scatter > 0)
            {
                Vector3f localScatter = new Vector3f(
                    (this.random.nextFloat() * 2 - 1) * scatter,
                    (this.random.nextFloat() * 2 - 1) * scatter,
                    (this.random.nextFloat() * 2 - 1) * scatter
                );
                rotation.transform(localScatter);
                pos.add(localScatter.x(), localScatter.y(), localScatter.z());

                if (centerStopTarget != null)
                {
                    centerStopTarget.add(localScatter.x(), localScatter.y(), localScatter.z());
                }
            }

            // 存活时间。负速度表示反向发射，也应该按速度绝对值计算射程对应的寿命。
            int maxAge = this.form.lifetime.get();
            if (maxAge <= 0)
            {
                float speedAbs = Math.abs(speed);
                maxAge = speedAbs > 0.0001F ? Math.max(1, (int) (range / speedAbs)) : 20;
            }

            Vector3f initialRotation = this.getInitialRotation();
            Vector3f rotationSpeed = this.createRotationSpeed(this.random);
            float itemScale = this.createItemScale(this.random);
            int scaleInTime = Math.max(0, this.form.scaleInTime.get());

            SprayedItem item = new SprayedItem(this, stack, pos, velocity, initialRotation, rotationSpeed, maxAge, useGravity, this.form.gravitySpeed.get(), useCollision, this.form.billboard.get(), itemScale, scaleInTime, formColor, source);

            configureCenterStop(item, centerStopTarget);
            target.add(item);
        }
    }

    private EmissionSample createEmissionSample(Random random)
    {
        EmissionSample sample = new EmissionSample();
        int shape = this.getEmissionShape();

        switch (shape)
        {
            case ItemSprayForm.SHAPE_PLANE:
                sample.offset.set(
                    (random.nextFloat() * 2F - 1F) * this.form.spawnWidth.get() * 0.5F,
                    (random.nextFloat() * 2F - 1F) * this.form.spawnHeight.get() * 0.5F,
                    0F
                );
                sample.direction.set(0F, 0F, 1F);
                break;
            case ItemSprayForm.SHAPE_SPHERE_OUT:
                sample.direction.set(this.randomUnitVector(random));
                sample.offset.set(sample.direction).mul(this.form.spawnOffset.get());
                break;
            case ItemSprayForm.SHAPE_SPHERE_IN:
                Vector3f outward = this.randomUnitVector(random);
                float inwardStart = Math.max(this.form.range.get() - this.form.spawnOffset.get(), 0F);

                sample.offset.set(outward).mul(inwardStart);
                sample.direction.set(outward).negate();
                break;
            default:
                sample.direction.set(this.randomConeDirection(random));
                break;
        }

        return sample;
    }

    private int getEmissionShape()
    {
        return Math.max(ItemSprayForm.SHAPE_CONE, Math.min(ItemSprayForm.SHAPE_COUNT - 1, this.form.emissionShape.get()));
    }

    private boolean shouldStopAtCenter(float currentSpeed)
    {
        return this.form.stopAtCenter.get()
            && this.getEmissionShape() == ItemSprayForm.SHAPE_SPHERE_IN
            && currentSpeed > 0.0001F;
    }

    private Vector3f randomConeDirection(Random random)
    {
        float radius = this.form.radius.get();
        float halfAngle = Math.min(radius, 180F) * 0.5F;
        float phi = (float) Math.acos(1 - random.nextFloat() * (1 - Math.cos(Math.toRadians(halfAngle))));
        float theta = random.nextFloat() * (float) Math.PI * 2F;

        /* BBS 表单和原版粒子形态都把局部 +Z 当作正向，因此正速度应当朝 +Z 发射。
         * 这里保留旧版随机数顺序，确保默认锥形模式的确定性结果不乱跳。 */
        return new Vector3f(
            MathHelper.sin(theta) * MathHelper.sin(phi),
            MathHelper.cos(theta) * MathHelper.sin(phi),
            MathHelper.cos(phi)
        );
    }

    private Vector3f randomUnitVector(Random random)
    {
        float z = random.nextFloat() * 2F - 1F;
        float theta = random.nextFloat() * (float) Math.PI * 2F;
        float radius = (float) Math.sqrt(Math.max(0F, 1F - z * z));

        return new Vector3f(
            MathHelper.cos(theta) * radius,
            MathHelper.sin(theta) * radius,
            z
        );
    }

    private void sampleDeterministicItems(FormRenderingContext context)
    {
        this.deterministicItems.clear();

        float time = this.getDeterministicSimulationTime(context);

        int frequency = Math.max(1, this.form.frequency.get());
        int amount = Math.max(1, this.form.amount.get());
        float speed = this.form.speed.get();
        float range = this.form.range.get();
        int maxAge = this.getMaxAge(speed, range);
        List<ItemStack> items = this.getItemStacks();
        World world = this.getPreviewWorld(context);
        World collisionWorld = this.usesWorldCollision(context) ? world : null;
        Matrix4f previewTransform = this.getDeterministicMotionTransform(context);
        Vector3d motionWorldOffset = this.getDeterministicMotionWorldOffset(context);
        Matrix3f gravityTransform = previewTransform.get3x3(new Matrix3f());
        Vector3f gravityLocal = new Vector3f(0F, -this.form.gravitySpeed.get(), 0F);

        if (this.form.gravity.get())
        {
            gravityTransform.invert();
            gravityTransform.transform(gravityLocal);
        }
        else
        {
            gravityLocal.set(0F, 0F, 0F);
        }

        if (maxAge <= 0 || items.isEmpty())
        {
            return;
        }

        int endTick = (int) Math.floor(time);
        int startTick = Math.max(0, endTick - maxAge + 1);
        startTick += Math.floorMod(frequency - Math.floorMod(startTick, frequency), frequency);

        Vector3d origin = new Vector3d();
        Matrix3f identity = new Matrix3f();

        for (int spawnTick = startTick; spawnTick <= endTick; spawnTick += frequency)
        {
            float age = time - spawnTick;
            if (age < 0F || age >= maxAge)
            {
                continue;
            }

            for (int index = 0; index < amount; index++)
            {
                if (this.deterministicItems.size() >= MAX_PREVIEW_ITEMS)
                {
                    return;
                }

                this.deterministicItems.add(this.createDeterministicItem(items, collisionWorld, previewTransform, motionWorldOffset, gravityLocal, origin, identity, spawnTick, index, age, maxAge));
            }
        }
    }

    private void publishDeterministicWorldItems(FormRenderingContext context)
    {
        Source source = this.getSource(context.entity);

        if (source == null)
        {
            return;
        }

        this.prepareDeterministicWorldPublish(source);
        this.clearOwnedDeterministicWorldItems();

        if (this.deterministicItems.isEmpty())
        {
            return;
        }

        Matrix4f modelMatrix = this.getDeterministicMotionTransform(context);
        Vector3d worldOffset = this.getDeterministicMotionWorldOffset(context);
        Matrix3f renderRotation = modelMatrix.get3x3(new Matrix3f());

        for (SprayedItem item : this.deterministicItems)
        {
            Vector3d pos = transformPosition(modelMatrix, worldOffset, item.pos);
            Vector3d prevPos = transformPosition(modelMatrix, worldOffset, item.prevPos);
            SprayedItem copy = new SprayedItem(
                this,
                item.stack.copy(),
                pos,
                new Vector3d(item.velocity),
                new Vector3f(item.initialRotation),
                new Vector3f(item.rotationSpeed),
                item.maxAge,
                item.useGravity,
                item.gravitySpeed,
                item.useCollision,
                item.billboard,
                item.scale,
                item.scaleInTime,
                item.color,
                source
            );

            copy.prevPos.set(prevPos);
            copy.rotation.set(item.rotation);
            copy.prevRotation.set(item.prevRotation);
            copy.age = item.age;
            copy.renderAge = item.renderAge;
            copy.stopped = item.stopped;
            copy.renderRotation = new Matrix3f(renderRotation);

            DETERMINISTIC_WORLD_ITEMS.add(copy);
        }
    }

    private void prepareDeterministicWorldPublish(Source source)
    {
        Long clearedFrame = DETERMINISTIC_SOURCE_CLEAR_FRAMES.get(source);

        if (clearedFrame == null || clearedFrame != currentWorldRenderFrame)
        {
            this.clearDeterministicWorldItems(source);
            DETERMINISTIC_SOURCE_CLEAR_FRAMES.put(source, currentWorldRenderFrame);
        }
    }

    private Matrix4f getWorldModelMatrix(FormRenderingContext context)
    {
        Matrix4f modelMatrix = this.getWorldViewInverseMatrix(context);

        modelMatrix.mul(context.stack.peek().getPositionMatrix());

        return modelMatrix;
    }

    private Matrix4f getWorldViewInverseMatrix(FormRenderingContext context)
    {
        net.minecraft.client.render.Camera camera = MinecraftClient.getInstance().gameRenderer.getCamera();

        if (camera == null)
        {
            return new Matrix4f(RenderSystem.getInverseViewRotationMatrix());
        }

        Matrix4f matrix = createCleanWorldViewMatrix(camera);

        if (this.shouldStripIrisViewBobbing())
        {
            /* Iris 为了兼容光影包，会把视野摇晃从投影矩阵转移到世界渲染的 model-view 矩阵。
             * BBS 在这个矩阵上继续渲染模型方块，因此这里反推世界坐标时必须把同一份 bobView 剥掉。 */
            Matrix4f bobbing = this.createViewBobbingMatrix(context.getTransition());

            bobbing.mul(matrix);
            matrix = bobbing;
        }

        matrix.invert();

        return matrix;
    }

    private static Matrix4f createCleanWorldViewMatrix(net.minecraft.client.render.Camera camera)
    {
        Matrix4f matrix = new Matrix4f();

        matrix.rotate(RotationAxis.POSITIVE_X.rotationDegrees(camera.getPitch()));
        matrix.rotate(RotationAxis.POSITIVE_Y.rotationDegrees(camera.getYaw() + 180F));

        return matrix;
    }

    private boolean shouldStripIrisViewBobbing()
    {
        MinecraftClient client = MinecraftClient.getInstance();

        return BBSRendering.isIrisShadersEnabled()
            && client != null
            && client.options != null
            && Boolean.TRUE.equals(client.options.getBobView().getValue());
    }

    private Matrix4f createViewBobbingMatrix(float tickDelta)
    {
        net.minecraft.client.util.math.MatrixStack stack = new net.minecraft.client.util.math.MatrixStack();
        MinecraftClient client = MinecraftClient.getInstance();

        try
        {
            ((GameRendererAccessor) client.gameRenderer).bbspp$invokeBobView(stack, tickDelta);
        }
        catch (Throwable ignored)
        {
            return new Matrix4f();
        }

        return new Matrix4f(stack.peek().getPositionMatrix());
    }

    private boolean usesWorldCollision(FormRenderingContext context)
    {
        return this.form.collision.get()
            && this.isMainWorldRenderPass()
            && context.type != FormRenderType.PREVIEW
            && !context.modelRenderer
            && !context.ui;
    }

    private void updateLocalPreview(FormRenderingContext context)
    {
        int tick = context.entity == null ? 0 : context.entity.getAge();

        if (this.lastLocalPreviewTick < 0 || tick < this.lastLocalPreviewTick)
        {
            this.localPreviewItems.clear();
            this.localPreviewCooldown = 0;
            this.lastLocalPreviewTick = tick - 1;
        }

        int steps = Math.min(Math.max(tick - this.lastLocalPreviewTick, 0), 4);
        Vector3d origin = new Vector3d();
        Matrix3f identity = new Matrix3f();

        for (int i = 0; i < steps; i++)
        {
            this.localPreviewCooldown = this.emit(this.localPreviewCooldown, this.localPreviewItems, origin, identity, null);

            // 本地预览只负责在编辑器里看见喷射效果，不拿局部坐标去误判真实世界碰撞。
            updateItems(this.localPreviewItems, null);
        }

        this.lastLocalPreviewTick = tick;
    }

    private float getDeterministicSimulationTime(FormRenderingContext context)
    {
        return Math.max(0F, this.form.simulationTime.get());
    }

    private World getPreviewWorld(FormRenderingContext context)
    {
        if (context.entity != null && context.entity.getWorld() != null)
        {
            return context.entity.getWorld();
        }

        return MinecraftClient.getInstance().world;
    }

    private Matrix4f getPreviewTransform(FormRenderingContext context)
    {
        if (context.world != null)
        {
            return new Matrix4f(context.world.peek().getPositionMatrix());
        }

        return new Matrix4f();
    }

    private Matrix4f getDeterministicMotionTransform(FormRenderingContext context)
    {
        if (this.usesWorldModelBlockTransform(context))
        {
            Source source = this.getSource(context.entity);
            Matrix4f stableMatrix = this.getStableWorldModelBlockMatrix(context, source);

            if (stableMatrix != null)
            {
                return stableMatrix;
            }

            return this.getWorldModelMatrix(context);
        }

        return this.getPreviewTransform(context);
    }

    private Vector3d getDeterministicMotionWorldOffset(FormRenderingContext context)
    {
        if (this.usesWorldModelBlockTransform(context))
        {
            Source source = this.getSource(context.entity);

            if (this.getStableWorldModelBlock(context, source) != null)
            {
                return new Vector3d(source.pos.getX(), source.pos.getY(), source.pos.getZ());
            }

            return context.camera.position;
        }

        return null;
    }

    private Matrix4f getStableWorldModelBlockMatrix(FormRenderingContext context, Source source)
    {
        ModelBlockEntity modelBlock = this.getStableWorldModelBlock(context, source);

        if (modelBlock == null)
        {
            return null;
        }

        MatrixStack stack = new MatrixStack();

        /* 默认模式会每帧重放碰撞。这里用方块整数坐标作为双精度世界偏移，
         * 矩阵只保留小范围局部变换，避免玩家移动视角时相机相对坐标的 float 误差让已碰撞粒子轻微抖动。 */
        stack.translate(0.5F, 0F, 0.5F);
        MatrixStackUtils.applyTransform(stack, modelBlock.getProperties().getTransform());
        stack.peek().getPositionMatrix().mul(context.world.peek().getPositionMatrix());

        return new Matrix4f(stack.peek().getPositionMatrix());
    }

    private ModelBlockEntity getStableWorldModelBlock(FormRenderingContext context, Source source)
    {
        if (source == null || context.world == null)
        {
            return null;
        }

        ModelBlockEntity modelBlock = source.getModelBlock(this.getPreviewWorld(context));

        if (modelBlock == null || modelBlock.getProperties() == null)
        {
            return null;
        }

        // 看向玩家模式本身会跟随相机方向变化，继续走当前渲染栈，避免错误冻结模型方块朝向。
        return modelBlock.getProperties().isLookAt() ? null : modelBlock;
    }

    private boolean usesWorldModelBlockTransform(FormRenderingContext context)
    {
        return this.isMainWorldRenderPass()
            && context.type == FormRenderType.MODEL_BLOCK
            && !context.modelRenderer
            && !context.ui;
    }

    private boolean isMainWorldRenderPass()
    {
        return BBSRendering.isRenderingWorld() && !isShadowLikePass();
    }

    private static boolean isShadowLikePass()
    {
        // IRLights 阴影烘焙会用灯光矩阵重渲染模型方块，不能让它刷新物品喷射的世界坐标快照。
        return BBSRendering.isIrisShadowPass() || isIRLiteShadowBakePass();
    }

    private static boolean isIRLiteShadowBakePass()
    {
        Method method = getIRLiteShadowBakeStateIsBaking();

        if (method == null)
        {
            return false;
        }

        try
        {
            return Boolean.TRUE.equals(method.invoke(null));
        }
        catch (Throwable ignored)
        {
            return false;
        }
    }

    private static Method getIRLiteShadowBakeStateIsBaking()
    {
        if (!irliteShadowBakeStateChecked)
        {
            irliteShadowBakeStateChecked = true;

            irliteShadowBakeStateIsBaking = findIRLiteShadowBakeStateIsBaking();
        }

        return irliteShadowBakeStateIsBaking;
    }

    private static Method findIRLiteShadowBakeStateIsBaking()
    {
        String[] classNames = {
            "org.qualet.irl.light.shadow.ShadowBakeState",
            "qualet.irlite.client.light.shadow.ShadowBakeState"
        };

        for (String className : classNames)
        {
            try
            {
                return Class.forName(className).getMethod("isBaking");
            }
            catch (Throwable ignored)
            {
                // 没安装对应版本 IRLights 时继续尝试其它包名。
            }
        }

        return null;
    }

    private SprayedItem createDeterministicItem(List<ItemStack> items, World collisionWorld, Matrix4f previewTransform, Vector3d motionWorldOffset, Vector3f gravityLocal, Vector3d origin, Matrix3f rotation, int spawnTick, int index, float age, int maxAge)
    {
        ItemStack stack = items.get(this.randomIndex(spawnTick, index, 0, items.size())).copy();
        Random seeded = new Random(this.mixSeed(spawnTick, index));

        float speed = this.form.speed.get();
        EmissionSample sample = this.createEmissionSample(seeded);

        float currentSpeed = speed;
        float speedOffset = this.form.speedOffset.get();
        if (speedOffset > 0)
        {
            currentSpeed += (seeded.nextFloat() * 2 - 1) * speedOffset;

            if (speed >= 0 && currentSpeed < 0)
            {
                currentSpeed = 0;
            }
            else if (speed < 0 && currentSpeed > 0)
            {
                currentSpeed = 0;
            }
        }

        Vector3f localVelocity = new Vector3f(sample.direction).normalize().mul(currentSpeed);
        rotation.transform(localVelocity);

        Vector3d pos = new Vector3d(origin);
        Vector3d centerStopTarget = this.shouldStopAtCenter(currentSpeed) ? new Vector3d(origin) : null;
        Vector3f localOffset = new Vector3f(sample.offset);

        rotation.transform(localOffset);
        pos.add(localOffset.x(), localOffset.y(), localOffset.z());

        float scatter = this.form.scatter.get();
        if (scatter > 0)
        {
            Vector3f localScatter = new Vector3f(
                (seeded.nextFloat() * 2 - 1) * scatter,
                (seeded.nextFloat() * 2 - 1) * scatter,
                (seeded.nextFloat() * 2 - 1) * scatter
            );
            rotation.transform(localScatter);
            pos.add(localScatter.x(), localScatter.y(), localScatter.z());

            if (centerStopTarget != null)
            {
                centerStopTarget.add(localScatter.x(), localScatter.y(), localScatter.z());
            }
        }

        Vector3d velocity = new Vector3d(localVelocity.x(), localVelocity.y(), localVelocity.z());

        Vector3f initialRotation = this.getInitialRotation();
        Vector3f rotationSpeed = this.createRotationSpeed(seeded);
        float itemScale = this.createItemScale(seeded);
        int scaleInTime = Math.max(0, this.form.scaleInTime.get());
        boolean useCollision = collisionWorld != null;

        SprayedItem item = new SprayedItem(this, stack, pos, velocity, initialRotation, rotationSpeed, maxAge, this.form.gravity.get(), this.form.gravitySpeed.get(), useCollision, this.form.billboard.get(), itemScale, scaleInTime, this.form.color.get(), null);

        configureCenterStop(item, centerStopTarget);

        if (useCollision || item.stopAtCenter)
        {
            this.replayDeterministicMotion(item, collisionWorld, previewTransform, motionWorldOffset, gravityLocal, age);
        }
        else
        {
            this.applyDeterministicMotion(item, gravityLocal, age);
        }

        item.age = Math.max(0, (int) Math.floor(age));
        item.renderAge = Math.max(0F, age);
        item.prevPos.set(item.pos);
        item.prevRotation.set(item.rotation);

        return item;
    }

    private List<ItemStack> getItemStacks()
    {
        List<ItemStack> items = new ArrayList<>();
        for (mchorse.bbs_mod.settings.values.mc.ValueItemStack vis : this.form.items.getList())
        {
            if (vis.get() != null && !vis.get().isEmpty()) items.add(vis.get());
        }

        return items;
    }

    private int getMaxAge(float speed, float range)
    {
        int maxAge = this.form.lifetime.get();
        if (maxAge <= 0)
        {
            float speedAbs = Math.abs(speed);
            maxAge = speedAbs > 0.0001F ? Math.max(1, (int) (range / speedAbs)) : 20;
        }

        return maxAge;
    }

    private long mixSeed(int spawnTick, int index)
    {
        long value = this.form.seed.get();

        value = value * 31L + spawnTick;
        value = value * 31L + index;
        value ^= (value >>> 33);
        value *= 0xff51afd7ed558ccdL;
        value ^= (value >>> 33);
        value *= 0xc4ceb9fe1a85ec53L;
        value ^= (value >>> 33);

        return value;
    }

    private int randomIndex(int spawnTick, int index, int salt, int bound)
    {
        if (bound <= 1)
        {
            return 0;
        }

        return new Random(this.mixSeed(spawnTick, index + salt * 100000)).nextInt(bound);
    }

    private double randomSigned(Random random)
    {
        return random.nextDouble() * 2D - 1D;
    }

    private Vector3f getInitialRotation()
    {
        return new Vector3f(
            this.form.itemPitch.get(),
            this.form.itemYaw.get(),
            this.form.itemRoll.get()
        );
    }

    private Vector3f createRotationSpeed(Random random)
    {
        float randomSpeed = Math.max(0F, this.form.rotationRandomSpeed.get());

        return new Vector3f(
            this.form.rotationSpeedX.get() + (float) (this.randomSigned(random) * randomSpeed),
            this.form.rotationSpeedY.get() + (float) (this.randomSigned(random) * randomSpeed),
            this.form.rotationSpeedZ.get() + (float) (this.randomSigned(random) * randomSpeed)
        );
    }

    private float createItemScale(Random random)
    {
        float scale = Math.max(0F, this.form.itemScale.get());
        float scatter = Math.max(0F, this.form.scaleScatter.get());

        if (scatter > 0F)
        {
            scale += (float) (this.randomSigned(random) * scatter);
        }

        return Math.max(0F, scale);
    }

    private static void configureCenterStop(SprayedItem item, Vector3d target)
    {
        if (target == null || item.velocity.lengthSquared() <= CENTER_STOP_EPSILON)
        {
            return;
        }

        item.stopAtCenter = true;
        item.centerStopTarget = new Vector3d(target);
        item.centerStopDirection = new Vector3d(item.velocity).normalize();

        if (getCenterRemainingDistance(item, item.pos) <= CENTER_STOP_EPSILON)
        {
            stopItemAt(item, item.centerStopTarget);
        }
    }

    private static void stopItemAt(SprayedItem item, Vector3d position)
    {
        item.stopped = true;
        item.pos.set(position);
        item.prevPos.set(position);
        item.velocity.set(0D, 0D, 0D);
        item.rotationSpeed.set(0F, 0F, 0F);
    }

    private void replayDeterministicMotion(SprayedItem item, World world, Matrix4f previewTransform, Vector3d motionWorldOffset, Vector3f gravityLocal, float age)
    {
        int wholeTicks = Math.max(0, (int) Math.floor(age));
        float remainder = age - wholeTicks;

        for (int i = 0; i < wholeTicks; i++)
        {
            applyMotionStep(item, world, previewTransform, motionWorldOffset, gravityLocal, 1F);

            if (item.stopped)
            {
                return;
            }
        }

        if (remainder > 0F)
        {
            applyMotionStep(item, world, previewTransform, motionWorldOffset, gravityLocal, remainder);
        }
    }

    private void applyDeterministicMotion(SprayedItem item, Vector3f gravityLocal, float age)
    {
        double gravityFactor = 0.5D * age * (age + 1D);

        item.pos.x += item.velocity.x * age + gravityLocal.x * gravityFactor;
        item.pos.y += item.velocity.y * age + gravityLocal.y * gravityFactor;
        item.pos.z += item.velocity.z * age + gravityLocal.z * gravityFactor;
        item.rotation.set(item.initialRotation).add(
            item.rotationSpeed.x * age,
            item.rotationSpeed.y * age,
            item.rotationSpeed.z * age
        );
    }

    private static void applyMotionStep(SprayedItem item, World world, Matrix4f previewTransform, Vector3f gravityLocal, float dt)
    {
        applyMotionStep(item, world, previewTransform, null, gravityLocal, dt);
    }

    private static void applyMotionStep(SprayedItem item, World world, Matrix4f previewTransform, Vector3d motionWorldOffset, Vector3f gravityLocal, float dt)
    {
        if (dt <= 0F)
        {
            return;
        }

        item.prevPos.set(item.pos);
        item.prevRotation.set(item.rotation);

        if (!item.stopped)
        {
            Vector3d currentPos = new Vector3d(item.pos);
            Vector3d nextPos = new Vector3d(item.pos);
            Vector3d nextVelocity = new Vector3d(item.velocity);
            Vector3f nextRotationSpeed = new Vector3f(item.rotationSpeed);

            if (item.useGravity)
            {
                nextVelocity.x += gravityLocal.x * dt;
                nextVelocity.y += gravityLocal.y * dt;
                nextVelocity.z += gravityLocal.z * dt;
            }

            nextPos.x += nextVelocity.x * dt;
            nextPos.y += nextVelocity.y * dt;
            nextPos.z += nextVelocity.z * dt;

            Vector3d centerStopPos = traceCenterStop(item, currentPos, nextPos);
            Vector3d collisionPos = null;

            if (item.useCollision && world != null)
            {
                collisionPos = traceCollision(world, previewTransform, motionWorldOffset, currentPos, nextPos);
            }

            Vector3d stopPos = pickNearestStop(currentPos, centerStopPos, collisionPos);

            if (stopPos != null)
            {
                item.stopped = true;
                nextVelocity.set(0, 0, 0);
                nextRotationSpeed.set(0, 0, 0);
                nextPos.set(stopPos);
            }

            item.velocity.set(nextVelocity);
            item.rotationSpeed.set(nextRotationSpeed);
            item.pos.set(nextPos);
            item.rotation.add(
                item.rotationSpeed.x * dt,
                item.rotationSpeed.y * dt,
                item.rotationSpeed.z * dt
            );
        }
    }

    private static Vector3d traceCenterStop(SprayedItem item, Vector3d start, Vector3d end)
    {
        if (!item.stopAtCenter || item.centerStopTarget == null || item.centerStopDirection == null)
        {
            return null;
        }

        double startDistance = getCenterRemainingDistance(item, start);
        double endDistance = getCenterRemainingDistance(item, end);

        if (startDistance <= CENTER_STOP_EPSILON || (startDistance > CENTER_STOP_EPSILON && endDistance <= CENTER_STOP_EPSILON))
        {
            return new Vector3d(item.centerStopTarget);
        }

        return null;
    }

    private static double getCenterRemainingDistance(SprayedItem item, Vector3d position)
    {
        return (item.centerStopTarget.x - position.x) * item.centerStopDirection.x
            + (item.centerStopTarget.y - position.y) * item.centerStopDirection.y
            + (item.centerStopTarget.z - position.z) * item.centerStopDirection.z;
    }

    private static Vector3d pickNearestStop(Vector3d start, Vector3d a, Vector3d b)
    {
        if (a == null)
        {
            return b;
        }

        if (b == null)
        {
            return a;
        }

        return distanceSquared(start, a) <= distanceSquared(start, b) ? a : b;
    }

    private static double distanceSquared(Vector3d a, Vector3d b)
    {
        double dx = a.x - b.x;
        double dy = a.y - b.y;
        double dz = a.z - b.z;

        return dx * dx + dy * dy + dz * dz;
    }

    private static Vector3d traceCollision(World world, Matrix4f localToWorld, Vector3d worldOffset, Vector3d startLocal, Vector3d endLocal)
    {
        Vector3d startWorld = transformPosition(localToWorld, worldOffset, startLocal);
        Vector3d endWorld = transformPosition(localToWorld, worldOffset, endLocal);

        if (!isFinite(startWorld) || !isFinite(endWorld))
        {
            return null;
        }

        net.minecraft.entity.Entity cameraEntity = MinecraftClient.getInstance().cameraEntity;

        if (cameraEntity == null)
        {
            return hasCollisionAt(world, endWorld) ? findLastSafeCollisionLocal(world, localToWorld, worldOffset, startLocal, endLocal) : null;
        }

        Vec3d start = new Vec3d(startWorld.x, startWorld.y, startWorld.z);
        Vec3d end = new Vec3d(endWorld.x, endWorld.y, endWorld.z);
        BlockHitResult hit = world.raycast(new RaycastContext(
            start,
            end,
            RaycastContext.ShapeType.COLLIDER,
            RaycastContext.FluidHandling.NONE,
            cameraEntity
        ));

        if (hit.getType() != HitResult.Type.MISS)
        {
            Vector3d hitLocal = inverseTransformPosition(localToWorld, worldOffset, hit.getPos());
            Vector3d motion = new Vector3d(endLocal).sub(startLocal);

            if (motion.lengthSquared() > 0.000001D)
            {
                hitLocal.sub(motion.normalize().mul(0.001D));
            }

            return hitLocal;
        }

        if (hasCollisionAt(world, endWorld))
        {
            return findLastSafeCollisionLocal(world, localToWorld, worldOffset, startLocal, endLocal);
        }

        return null;
    }

    private static Vector3d findLastSafeCollisionLocal(World world, Matrix4f localToWorld, Vector3d worldOffset, Vector3d startLocal, Vector3d endLocal)
    {
        Vector3d startWorld = transformPosition(localToWorld, worldOffset, startLocal);

        if (hasCollisionAt(world, startWorld))
        {
            return new Vector3d(startLocal);
        }

        Vector3d safe = new Vector3d(startLocal);
        Vector3d blocked = new Vector3d(endLocal);

        for (int i = 0; i < 12; i++)
        {
            Vector3d mid = new Vector3d(
                (safe.x + blocked.x) * 0.5D,
                (safe.y + blocked.y) * 0.5D,
                (safe.z + blocked.z) * 0.5D
            );
            Vector3d midWorld = transformPosition(localToWorld, worldOffset, mid);

            if (hasCollisionAt(world, midWorld))
            {
                blocked.set(mid);
            }
            else
            {
                safe.set(mid);
            }
        }

        return safe;
    }

    private static boolean isFinite(Vector3d vector)
    {
        return Double.isFinite(vector.x) && Double.isFinite(vector.y) && Double.isFinite(vector.z);
    }

    private static Vector3d transformPosition(Matrix4f matrix, Vector3d worldOffset, Vector3d position)
    {
        Vector3f transformed = new Vector3f((float) position.x, (float) position.y, (float) position.z);

        matrix.transformPosition(transformed);

        if (worldOffset == null)
        {
            return new Vector3d(transformed.x(), transformed.y(), transformed.z());
        }

        return new Vector3d(transformed.x() + worldOffset.x, transformed.y() + worldOffset.y, transformed.z() + worldOffset.z);
    }

    private static Vector3d inverseTransformPosition(Matrix4f matrix, Vector3d worldOffset, Vec3d position)
    {
        Matrix4f inverse = new Matrix4f(matrix).invert();
        double x = position.x;
        double y = position.y;
        double z = position.z;

        if (worldOffset != null)
        {
            x -= worldOffset.x;
            y -= worldOffset.y;
            z -= worldOffset.z;
        }

        Vector3f transformed = new Vector3f((float) x, (float) y, (float) z);

        inverse.transformPosition(transformed);

        return new Vector3d(transformed.x(), transformed.y(), transformed.z());
    }

    private static boolean hasCollisionAt(World world, Vector3d position)
    {
        BlockPos blockPos = new BlockPos(
            (int) Math.floor(position.x),
            (int) Math.floor(position.y),
            (int) Math.floor(position.z)
        );
        net.minecraft.block.BlockState state = world.getBlockState(blockPos);
        VoxelShape shape = state == null ? null : state.getCollisionShape(world, blockPos);

        if (state == null || state.isAir() || shape == null || shape.isEmpty())
        {
            return false;
        }

        double x = position.x - blockPos.getX();
        double y = position.y - blockPos.getY();
        double z = position.z - blockPos.getZ();
        double epsilon = 0.000001D;

        for (Box box : shape.getBoundingBoxes())
        {
            if (x >= box.minX - epsilon && x <= box.maxX + epsilon
                && y >= box.minY - epsilon && y <= box.maxY + epsilon
                && z >= box.minZ - epsilon && z <= box.maxZ + epsilon)
            {
                return true;
            }
        }

        return false;
    }

    private static void updateItems(List<SprayedItem> items, World world)
    {
        for (int i = items.size() - 1; i >= 0; i--)
        {
            SprayedItem item = items.get(i);

            // 只有来自模型方块的世界粒子才跟随源方块生命周期清理；其他来源仍按自身 lifetime 结束。
            if (world != null && item.source != null && !item.source.isAlive(world))
            {
                items.remove(i);
                continue;
            }

            item.age++;
            if (item.age >= item.maxAge)
            {
                items.remove(i);
                continue;
            }

            item.prevPos.set(item.pos);
            item.prevRotation.set(item.rotation);

            applyMotionStep(item, world, IDENTITY_TRANSFORM, new Vector3f(0F, -item.gravitySpeed, 0F), 1F);
        }
    }

    private static boolean isFrustumCullingEnabled()
    {
        return BBSAddonsSettings.itemSprayFrustumCulling == null || BBSAddonsSettings.itemSprayFrustumCulling.get();
    }

    static int getMaxRenderedItems()
    {
        if (BBSAddonsSettings.itemSprayMaxRenderedItems == null)
        {
            return DEFAULT_MAX_RENDERED_ITEMS;
        }

        return Math.max(0, BBSAddonsSettings.itemSprayMaxRenderedItems.get());
    }

    static double getMaxRenderDistanceSquared()
    {
        int distance = DEFAULT_MAX_RENDER_DISTANCE;

        if (BBSAddonsSettings.itemSprayMaxRenderDistance != null)
        {
            distance = Math.max(0, BBSAddonsSettings.itemSprayMaxRenderDistance.get());
        }

        if (distance <= 0)
        {
            MinecraftClient client = MinecraftClient.getInstance();

            if (client == null || client.options == null)
            {
                return -1D;
            }

            distance = Math.max(16, client.options.getClampedViewDistance() * 16);
        }

        return (double) distance * (double) distance;
    }

    /**
     * 给 IR Lights 阴影烘焙追加物品喷射投影物。
     *
     * 注入方只传入 IRL 的 OccluderSink 对象；这里通过反射调用 sink.emit(...)，避免 BBS++ 编译期依赖 IRL。
     * 所有喷射物品都按动态投影处理，防止停止/旋转/关键帧变化后复用到过期阴影。
     */
    public static void collectIRLiteShadowCasters(World world, Vec3d cameraPos, float tickDelta, Object sink)
    {
        ItemSprayIRLiteBridge.collectShadowCasters(world, cameraPos, tickDelta, sink, GLOBAL_ITEMS, DETERMINISTIC_WORLD_ITEMS);
    }

    /**
     * 在 IR Lights 当前阴影 batch 中绘制一个物品喷射投影物。
     *
     * 返回 true 表示这个 caster 属于 BBS++，调用方应取消 IRL 原本的 emitOccluder 分支，避免它把该对象当成实体强转。
     */
    public static boolean renderIRLiteShadowCaster(Object caster, float tickDelta, Object batch)
    {
        return ItemSprayIRLiteBridge.renderShadowCaster(caster, tickDelta, batch);
    }

    static void applyBillboard(net.minecraft.client.util.math.MatrixStack stack)
    {
        Matrix4f modelMatrix = stack.peek().getPositionMatrix();
        Vector3f scale = new Vector3f();

        modelMatrix.getScale(scale);
        modelMatrix.m00(1F).m01(0F).m02(0F);
        modelMatrix.m10(0F).m11(1F).m12(0F);
        modelMatrix.m20(0F).m21(0F).m22(1F);
        modelMatrix.scale(scale);

        stack.peek().getNormalMatrix().identity();
    }

    public static final Link ICON = Link.assets("textures/itemspray.png");

    // ==================== 渲染 ====================
    @Override
    public void renderInUI(UIContext context, int x1, int y1, int x2, int y2)
    {
        // 绘制固定预览图
        var texture = context.render.getTextures().getTexture(ICON);

        if (texture != null)
        {
            float min = Math.min(texture.width, texture.height);

            if (min <= 0)
            {
                min = 1;
            }

            int ow = (x2 - x1) - 4;
            int oh = (y2 - y1) - 4;

            int w = (int) ((texture.width / min) * ow);
            int h = (int) ((texture.height / min) * ow);

            int x = x1 + (ow - w) / 2 + 2;
            int y = y1 + (oh - h) / 2 + 2;

            context.batcher.fullTexturedBox(texture, x, y, w, h);
        }

        // 在底部显示名称
        String name = this.form.getDefaultDisplayName();
        if (!this.form.items.getList().isEmpty())
        {
            ItemStack firstItem = this.form.items.getList().get(0).get();
            if (!firstItem.isEmpty())
            {
                name = firstItem.getName().getString();
            }
        }
        context.batcher.text(name, x1 + 4, y2 - 12, 0xffffffff);
    }

    /**
     * 每帧调用。
     * 1. 在确定性模式下直接按当前模拟时间采样并渲染物品。
     * 2. 在实时世界模式下缓存当前形态的世界空间位置和旋转，供 tick() 生成物品使用。
     * 3. 绘制辅助线。
     */
    @Override
    protected void render3D(FormRenderingContext context)
    {
        try
        {
            this.render3DInner(context);
        }
        catch (Throwable throwable)
        {
            throwable.printStackTrace();
        }
    }

    private void render3DInner(FormRenderingContext context)
    {
        if (context.isPicking()) return;
        if (isShadowLikePass()) return;

        if (this.isItemDisplayRender(context))
        {
            this.clearItemDisplayRenderState();
            this.renderItemDisplayIcon(context);

            return;
        }

        boolean deterministic = this.usesPreviewMode();
        boolean localPreview = this.usesLocalPreview(context);
        boolean globalDeterministic = this.usesGlobalDeterministicPreview(context);

        if (this.usesEditorPreviewSurface(context))
        {
            this.clearDeterministicWorldItemsForEditorPreview(context);
        }

        if (deterministic)
        {
            this.sampleDeterministicItems(context);
            this.clearOwnedGlobalItems();

            if (globalDeterministic)
            {
                this.publishDeterministicWorldItems(context);
            }
        }
        else if (localPreview)
        {
            this.updateLocalPreview(context);
        }
        else if (this.isMainWorldRenderPass())
        {
            this.cacheWorldTransform(context);
        }

        // ---- 绘制辅助线 ----
        context.stack.push();
        try
        {
            if (this.shouldRenderGuide(context))
            {
                SprayGuideRenderer.renderSprayGuide(
                    context.stack, Color.white(),
                    this.getEmissionShape(),
                    this.form.range.get(),
                    this.form.radius.get(),
                    this.form.spawnWidth.get(),
                    this.form.spawnHeight.get(),
                    this.form.spawnOffset.get()
                );
            }
        }
        finally
        {
            context.stack.pop();
        }

        if (deterministic && !globalDeterministic)
        {
            ItemSprayWorldItemRenderer.renderItems(context.stack, this.deterministicItems, context.getTransition(), this.getPreviewWorld(context), context.light, context.overlay, null);
        }
        else if (localPreview)
        {
            ItemSprayWorldItemRenderer.renderItems(context.stack, this.localPreviewItems, context.getTransition(), this.getPreviewWorld(context), context.light, context.overlay, null);
        }
    }

    private void cacheWorldTransform(FormRenderingContext context)
    {
        Matrix4f posMat = context.stack.peek().getPositionMatrix();

        // 抵消当前世界渲染视图栈，只保留模型自身的旋转和平移。
        Matrix4f modelMat = this.getWorldViewInverseMatrix(context);
        modelMat.mul(posMat);

        // 从 Model 矩阵提取位置（平移分量），需要加上相机位置得到世界坐标
        Vector3f tempVec = new Vector3f();
        modelMat.getTranslation(tempVec);
        org.joml.Vector3d camPos = context.camera.position;
        this.cachedWorldOrigin = new Vector3d(
            tempVec.x() + camPos.x,
            tempVec.y() + camPos.y,
            tempVec.z() + camPos.z
        );

        // 从 Model 矩阵提取世界空间旋转（3x3 旋转分量）
        Matrix3f modelRot = new Matrix3f();
        modelMat.get3x3(modelRot);
        this.cachedWorldRotation = modelRot;
        this.lastWorldRenderTime = System.currentTimeMillis();
    }

    private boolean shouldRenderGuide(FormRenderingContext context)
    {
        if (this.usesEditorPreviewSurface(context))
        {
            return true;
        }

        return this.isMainWorldRenderPass() && this.form.showGuide.get();
    }
}
