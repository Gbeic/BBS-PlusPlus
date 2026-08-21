package gbeic.bbsplusplus.client.renderer;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.math.Axis;
import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.blocks.entities.ModelBlockEntity;
import mchorse.bbs_mod.client.BBSRendering;
import mchorse.bbs_mod.events.ModelBlockEntityUpdateCallback;
import mchorse.bbs_mod.forms.ITickable;
import mchorse.bbs_mod.forms.entities.IEntity;
import mchorse.bbs_mod.forms.renderers.FormRenderType;
import mchorse.bbs_mod.forms.renderers.FormRenderingContext;
import mchorse.bbs_mod.forms.renderers.FormRenderer;
import mchorse.bbs_mod.graphics.InverseView;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.utils.MatrixStackUtils;
import mchorse.bbs_mod.utils.colors.Color;
import gbeic.bbsplusplus.BBSAddonsSettings;
import gbeic.bbsplusplus.forms.ItemSprayForm;
import gbeic.bbsplusplus.mixin.GameRendererAccessor;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.common.NeoForge;
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
            Level world = modelBlock.getLevel();
            IEntity entity = modelBlock.getEntity();

            if (world != null && entity != null)
            {
                MODEL_BLOCK_SOURCES.put(entity, new Source(world, modelBlock.getBlockPos(), modelBlock));
            }
        });

        /* Fabric API 的回调在 NeoForge 上都没有直接等价物，改为向 NeoForge 游戏总线注册：
         * - ClientTickEvents.END_CLIENT_TICK  → ClientTickEvent.Post
         * - WorldRenderEvents.AFTER_ENTITIES  → RenderLevelStageEvent Stage.AFTER_ENTITIES
         *   （同时承担原 WorldRenderEvents.START 的帧号自增职责，见 onRenderLevelStage）
         * BBS 本体在 FSR 里也只用 AFTER_ENTITIES / AFTER_LEVEL，保持一致可让两边渲染时机对齐。 */
        NeoForge.EVENT_BUS.addListener(ItemSprayFormRenderer::onClientTickPost);
        NeoForge.EVENT_BUS.addListener(ItemSprayFormRenderer::onRenderLevelStage);
    }

    /**
     * 全局物理更新钩子。
     *
     * 独立于发射器本身，即使发射器被破坏，已存在的粒子仍会按物理规律继续飞行。
     * NeoForge 的 tick 事件不携带 Minecraft 实例，这里自行取单例。
     */
    private static void onClientTickPost(ClientTickEvent.Post event)
    {
        Minecraft client = Minecraft.getInstance();

        if (client.level == null)
        {
            GLOBAL_ITEMS.clear();
            DETERMINISTIC_WORLD_ITEMS.clear();
            DETERMINISTIC_SOURCE_CLEAR_FRAMES.clear();

            return;
        }

        if (client.isPaused()) return;

        updateItems(GLOBAL_ITEMS, client.level);
        DETERMINISTIC_WORLD_ITEMS.removeIf((item) -> item.source != null && !item.source.isAlive(client.level));
        DETERMINISTIC_SOURCE_CLEAR_FRAMES.keySet().removeIf((source) -> !source.isAlive(client.level));
    }

    /**
     * 全局渲染钩子，负责把所有飞行的粒子独立画出来。
     *
     * NeoForge 把整个世界渲染拆成若干 Stage，一个监听器会被各阶段各调用一次，因此需要自行分派。
     */
    private static void onRenderLevelStage(RenderLevelStageEvent event)
    {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES) return;

        /* 帧号必须每个世界渲染帧都变化，prepareDeterministicWorldPublish 靠它做「每帧只清一次」。
         * 原先放在 AFTER_SKY 自增，但那个阶段在 Sodium 等重写渲染管线的模组下未必触发；
         * 一旦不触发，帧号就永久停住，快照永远不再清空，粒子会无限累积并拖慢渲染。
         * AFTER_ENTITIES 是 FSR 本体也在用的阶段，可以确定每帧触发，故改到这里自增。 */
        currentWorldRenderFrame++;

        if (isShadowLikePass()) return;
        if (GLOBAL_ITEMS.isEmpty() && DETERMINISTIC_WORLD_ITEMS.isEmpty()) return;

        PoseStack stack = event.getPoseStack();

        if (stack == null) return;

        Vec3 camPos = event.getCamera().getPosition();
        Frustum frustum = isFrustumCullingEnabled() ? event.getFrustum() : null;
        double maxDistanceSq = getMaxRenderDistanceSquared();
        // 1.21.1 的 partialTick 被包进 DeltaTracker，取「不受冻结影响」的世界插值系数才等价于旧的 tickDelta。
        float tickDelta = event.getPartialTick().getGameTimeDeltaPartialTick(false);

        ItemSprayWorldItemRenderer.renderWorldItems(stack, tickDelta, Minecraft.getInstance().level, 0, OverlayTexture.NO_OVERLAY, camPos, frustum, getMaxRenderedItems(), maxDistanceSq, GLOBAL_ITEMS, DETERMINISTIC_WORLD_ITEMS);
    }

    // ==================== 发射源数据结构 ====================
    static class Source
    {
        public final ResourceKey<Level> worldKey;
        public final BlockPos pos;
        /** 只保存模型方块实例身份码，避免 Source 反向强引用实体导致 WeakHashMap 无法释放旧条目 */
        public final int blockEntityIdentity;

        public Source(Level world, BlockPos pos, ModelBlockEntity modelBlock)
        {
            this.worldKey = world.dimension();
            this.pos = pos.immutable();
            this.blockEntityIdentity = System.identityHashCode(modelBlock);
        }

        public boolean isAlive(Level world)
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

        public ModelBlockEntity getModelBlock(Level world)
        {
            if (world == null || !world.dimension().equals(this.worldKey))
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
            float progress = Mth.clamp(age / this.scaleInTime, 0F, 1F);
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
            net.minecraft.world.entity.Entity mcEnt = mcEntity.getMcEntity();
            if (mcEnt != null)
            {
                // 禁用实体的视锥体裁剪，防止源头方块在屏幕外时停止渲染从而导致缓存不再更新
                mcEnt.noCulling = true;
            }
        }

        if (this.usesPreviewMode())
        {
            this.cooldown = 0;
            this.clearOwnedGlobalItems();

            return;
        }

        Source source = this.getSource(entity);
        Level world = entity == null ? null : entity.getWorld();

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

        context.stack.pushPose();

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
            Matrix4f matrix = context.stack.last().pose();

            /* 1.21.1 起 Tesselator.getBuffer() 已删除，begin() 直接返回一次性 BufferBuilder；
             * 顶点写入改为 addVertex/setUv，且不再需要（也不存在）.next() 收尾。 */
            BufferBuilder builder = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);

            // GUI 物品渲染的矩阵朝向和手持不同，单独上下翻转图标。
            builder.addVertex(matrix, -half, -half, 0F).setUv(u0, v1);
            builder.addVertex(matrix, half, -half, 0F).setUv(u1, v1);
            builder.addVertex(matrix, half, half, 0F).setUv(u1, v0);
            builder.addVertex(matrix, -half, half, 0F).setUv(u0, v0);

            builder.addVertex(matrix, -half, half, 0F).setUv(u0, v0);
            builder.addVertex(matrix, half, half, 0F).setUv(u1, v0);
            builder.addVertex(matrix, half, -half, 0F).setUv(u1, v1);
            builder.addVertex(matrix, -half, -half, 0F).setUv(u0, v1);

            RenderSystem.setShader(GameRenderer::getPositionTexShader);
            RenderSystem.defaultBlendFunc();
            RenderSystem.enableBlend();
            BufferUploader.drawWithShader(builder.buildOrThrow());
            RenderSystem.enableDepthTest();
        }
        finally
        {
            context.stack.popPose();
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
            Mth.sin(theta) * Mth.sin(phi),
            Mth.cos(theta) * Mth.sin(phi),
            Mth.cos(phi)
        );
    }

    private Vector3f randomUnitVector(Random random)
    {
        float z = random.nextFloat() * 2F - 1F;
        float theta = random.nextFloat() * (float) Math.PI * 2F;
        float radius = (float) Math.sqrt(Math.max(0F, 1F - z * z));

        return new Vector3f(
            Mth.cos(theta) * radius,
            Mth.sin(theta) * radius,
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
        Level world = this.getPreviewWorld(context);
        Level collisionWorld = this.usesWorldCollision(context) ? world : null;
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

    /**
     * 取模型方块在世界渲染时的模型矩阵（相机相对坐标，不含视图旋转）。
     *
     * <p>
     * 1.20.1 时期这里必须先乘一次视图逆矩阵，因为那时相机视图变换被烘焙进了 form 的矩阵栈，
     * 需要抵消掉才能还原模型自身的变换。<b>1.21.1 起 BBS 的 form 矩阵栈栈基是单位阵</b>——
     * 视图变换移到了 {@code RenderSystem.getModelViewMatrix()}，由着色器在绘制时再乘回去
     * （见 FSR 的 {@code MatrixStackUtils.billboard()} 注释）。此时栈里已经只有模型自身的
     * 变换，再乘视图逆矩阵等于凭空多转一次，粒子会被抛到错误的世界位置。
     * </p>
     * <p>
     * FSR 自己的 {@code ParticleFormRenderer.render3D()} 同样按渲染空间分流：
     * {@code CAMERA_RELATIVE_WORLD} 直接取 {@code context.stack.last().pose()}，
     * 只有 UI 预览路径才乘 {@code context.camera.view} 的逆。这里对齐该做法。
     * </p>
     */
    private Matrix4f getWorldModelMatrix(FormRenderingContext context)
    {
        return new Matrix4f(context.stack.last().pose());
    }

    /**
     * 反推「世界渲染视图矩阵」的逆矩阵。
     *
     * <p>
     * <b>1.21.1 起已不再使用</b>：form 矩阵栈栈基改为单位阵后，栈里不含视图变换，也就不含
     * Iris 转移进来的视野摇晃，因此既不需要抵消视图、也不需要剥离 bobbing。
     * 保留这套实现（连同 {@link #createCleanWorldViewMatrix}、
     * {@link #shouldStripIrisViewBobbing}、{@link #createViewBobbingMatrix}）是为了在 Iris
     * 光影下若真出现坐标偏差时，能立刻对照 1.20.1 的原始算法排查，不必翻历史提交。
     * </p>
     */
    @SuppressWarnings("unused")
    private Matrix4f getWorldViewInverseMatrix(FormRenderingContext context)
    {
        Camera camera = Minecraft.getInstance().gameRenderer.getMainCamera();

        if (camera == null)
        {
            /* 1.21.1 的 RenderSystem 不再保存逆视图旋转矩阵，BBS 改由自己的 InverseView 维护同一份数据，
             * 这里沿用它作为取不到相机时的兜底。 */
            return new Matrix4f(InverseView.get());
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

    private static Matrix4f createCleanWorldViewMatrix(Camera camera)
    {
        Matrix4f matrix = new Matrix4f();

        matrix.rotate(Axis.XP.rotationDegrees(camera.getXRot()));
        matrix.rotate(Axis.YP.rotationDegrees(camera.getYRot() + 180F));

        return matrix;
    }

    private boolean shouldStripIrisViewBobbing()
    {
        Minecraft client = Minecraft.getInstance();

        return BBSRendering.isIrisShadersEnabled()
            && client != null
            && client.options != null
            && Boolean.TRUE.equals(client.options.bobView().get());
    }

    private Matrix4f createViewBobbingMatrix(float tickDelta)
    {
        PoseStack stack = new PoseStack();
        Minecraft client = Minecraft.getInstance();

        try
        {
            ((GameRendererAccessor) client.gameRenderer).bbspp$invokeBobView(stack, tickDelta);
        }
        catch (Throwable ignored)
        {
            return new Matrix4f();
        }

        return new Matrix4f(stack.last().pose());
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

    private Level getPreviewWorld(FormRenderingContext context)
    {
        if (context.entity != null && context.entity.getWorld() != null)
        {
            return context.entity.getWorld();
        }

        return Minecraft.getInstance().level;
    }

    private Matrix4f getPreviewTransform(FormRenderingContext context)
    {
        if (context.world != null)
        {
            return new Matrix4f(context.world.last().pose());
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

        PoseStack stack = new PoseStack();

        /* 默认模式会每帧重放碰撞。这里用方块整数坐标作为双精度世界偏移，
         * 矩阵只保留小范围局部变换，避免玩家移动视角时相机相对坐标的 float 误差让已碰撞粒子轻微抖动。 */
        stack.translate(0.5F, 0F, 0.5F);
        MatrixStackUtils.applyTransform(stack, modelBlock.getProperties().getTransform());
        stack.last().pose().mul(context.world.last().pose());

        return new Matrix4f(stack.last().pose());
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

            try
            {
                Class<?> state = Class.forName("qualet.irlite.client.light.shadow.ShadowBakeState");

                irliteShadowBakeStateIsBaking = state.getMethod("isBaking");
            }
            catch (Throwable ignored)
            {
                // 没安装 IRLights 时保持空方法即可，避免每帧重复反查类。
            }
        }

        return irliteShadowBakeStateIsBaking;
    }

    private SprayedItem createDeterministicItem(List<ItemStack> items, Level collisionWorld, Matrix4f previewTransform, Vector3d motionWorldOffset, Vector3f gravityLocal, Vector3d origin, Matrix3f rotation, int spawnTick, int index, float age, int maxAge)
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

    private void replayDeterministicMotion(SprayedItem item, Level world, Matrix4f previewTransform, Vector3d motionWorldOffset, Vector3f gravityLocal, float age)
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

    private static void applyMotionStep(SprayedItem item, Level world, Matrix4f previewTransform, Vector3f gravityLocal, float dt)
    {
        applyMotionStep(item, world, previewTransform, null, gravityLocal, dt);
    }

    private static void applyMotionStep(SprayedItem item, Level world, Matrix4f previewTransform, Vector3d motionWorldOffset, Vector3f gravityLocal, float dt)
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

    private static Vector3d traceCollision(Level world, Matrix4f localToWorld, Vector3d worldOffset, Vector3d startLocal, Vector3d endLocal)
    {
        Vector3d startWorld = transformPosition(localToWorld, worldOffset, startLocal);
        Vector3d endWorld = transformPosition(localToWorld, worldOffset, endLocal);

        if (!isFinite(startWorld) || !isFinite(endWorld))
        {
            return null;
        }

        net.minecraft.world.entity.Entity cameraEntity = Minecraft.getInstance().cameraEntity;

        if (cameraEntity == null)
        {
            return hasCollisionAt(world, endWorld) ? findLastSafeCollisionLocal(world, localToWorld, worldOffset, startLocal, endLocal) : null;
        }

        Vec3 start = new Vec3(startWorld.x, startWorld.y, startWorld.z);
        Vec3 end = new Vec3(endWorld.x, endWorld.y, endWorld.z);
        // RaycastContext → ClipContext，ShapeType/FluidHandling 对应改名为 Block/Fluid 两个内部枚举。
        BlockHitResult hit = world.clip(new ClipContext(
            start,
            end,
            ClipContext.Block.COLLIDER,
            ClipContext.Fluid.NONE,
            cameraEntity
        ));

        if (hit.getType() != HitResult.Type.MISS)
        {
            Vector3d hitLocal = inverseTransformPosition(localToWorld, worldOffset, hit.getLocation());
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

    private static Vector3d findLastSafeCollisionLocal(Level world, Matrix4f localToWorld, Vector3d worldOffset, Vector3d startLocal, Vector3d endLocal)
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

    private static Vector3d inverseTransformPosition(Matrix4f matrix, Vector3d worldOffset, Vec3 position)
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

    private static boolean hasCollisionAt(Level world, Vector3d position)
    {
        BlockPos blockPos = new BlockPos(
            (int) Math.floor(position.x),
            (int) Math.floor(position.y),
            (int) Math.floor(position.z)
        );
        BlockState state = world.getBlockState(blockPos);
        VoxelShape shape = state == null ? null : state.getCollisionShape(world, blockPos);

        if (state == null || state.isAir() || shape == null || shape.isEmpty())
        {
            return false;
        }

        double x = position.x - blockPos.getX();
        double y = position.y - blockPos.getY();
        double z = position.z - blockPos.getZ();
        double epsilon = 0.000001D;

        for (AABB box : shape.toAabbs())
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

    private static void updateItems(List<SprayedItem> items, Level world)
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
            Minecraft client = Minecraft.getInstance();

            if (client == null || client.options == null)
            {
                return -1D;
            }

            distance = Math.max(16, client.options.getEffectiveRenderDistance() * 16);
        }

        return (double) distance * (double) distance;
    }

    /**
     * 给 IR Lights 阴影烘焙追加物品喷射投影物。
     *
     * 注入方只传入 IRL 的 OccluderSink 对象；这里通过反射调用 sink.emit(...)，避免 BBS++ 编译期依赖 IRL。
     * 所有喷射物品都按动态投影处理，防止停止/旋转/关键帧变化后复用到过期阴影。
     */
    public static void collectIRLiteShadowCasters(Level world, Vec3 cameraPos, float tickDelta, Object sink)
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

    static void applyBillboard(PoseStack stack)
    {
        Matrix4f modelMatrix = stack.last().pose();
        Vector3f scale = new Vector3f();

        modelMatrix.getScale(scale);
        modelMatrix.m00(1F).m01(0F).m02(0F);
        modelMatrix.m10(0F).m11(1F).m12(0F);
        modelMatrix.m20(0F).m21(0F).m22(1F);
        modelMatrix.scale(scale);

        stack.last().normal().identity();
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
                // Yarn 的 ItemStack.getName() 在 Mojmap 里叫 getHoverName()。
                name = firstItem.getHoverName().getString();
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
        context.stack.pushPose();
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
            context.stack.popPose();
        }

        float tickDelta = resolvePreviewPartialTick(context);

        if (deterministic && !globalDeterministic)
        {
            ItemSprayWorldItemRenderer.renderItems(context.stack, this.deterministicItems, tickDelta, this.getPreviewWorld(context), context.light, context.overlay, null);
        }
        else if (localPreview)
        {
            ItemSprayWorldItemRenderer.renderItems(context.stack, this.localPreviewItems, tickDelta, this.getPreviewWorld(context), context.light, context.overlay, null);
        }
    }

    /**
     * 取用于粒子插值的 partial tick（0~1）。
     *
     * <p>
     * 不能直接用 {@code context.getTransition()}：1.21.1 起 Minecraft 传给
     * {@code Screen.render(..., float)} 的第四个参数由「0~1 的插值系数」换成了
     * {@code DeltaTracker.getGameTimeDeltaTicks()}——本帧经过的 tick 数（60fps 下恒约 0.33）。
     * BBS 的 {@code UIScreen} 仍按旧语义把它塞给 {@code UIContext.setTransition}，于是
     * {@code lerp} 每帧都用同一个固定系数，粒子位置卡在两个 tick 之间的固定比例处不再推进，
     * 表现为明显的低帧率跳动。
     * </p>
     * <p>
     * 这里直接向 {@code DeltaTracker} 取真正的 partial tick。传 false 表示不受渲染冻结影响，
     * 与世界渲染路径（{@code RenderLevelStageEvent#getPartialTick}）取的是同一个量，
     * 保证预览与世界两条路径的插值行为一致。
     * </p>
     */
    private static float resolvePreviewPartialTick(FormRenderingContext context)
    {
        Minecraft client = Minecraft.getInstance();

        if (client != null && client.getTimer() != null)
        {
            return client.getTimer().getGameTimeDeltaPartialTick(false);
        }

        /* 理论上取不到计时器，退回 BBS 提供的值，至少不会比原来更差。 */
        return context.getTransition();
    }

    private void cacheWorldTransform(FormRenderingContext context)
    {
        /* 1.21.1 起 form 矩阵栈栈基为单位阵，栈里已经只剩模型自身的旋转和平移，
         * 不能再乘视图逆矩阵去「抵消视图栈」——那会多转一次，让世界坐标整体偏掉。
         * 详见 getWorldModelMatrix 的说明。 */
        Matrix4f modelMat = new Matrix4f(context.stack.last().pose());

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
