package gbeic.bbsplusplus.client.renderer;

import com.mojang.blaze3d.systems.RenderSystem;
import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.forms.ITickable;
import mchorse.bbs_mod.forms.entities.IEntity;
import mchorse.bbs_mod.forms.renderers.FormRenderType;
import mchorse.bbs_mod.forms.renderers.FormRenderingContext;
import mchorse.bbs_mod.forms.renderers.FormRenderer;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.utils.colors.Color;
import gbeic.bbsplusplus.forms.ItemSprayForm;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3d;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * 物品喷射形态渲染器。
 *
 * 只负责形态级调度：每帧按当前模式（确定性预览/本地实时预览/真实世界发射）分流到对应模块，
 * 缓存真实世界发射原点与旋转矩阵，绘制辅助线、物品展示图标与 UI 预览图，并作为 IRLights
 * 阴影桥接的静态入口。发射采样、确定性模拟、物理碰撞、全局粒子系统分别由
 * {@link ItemSprayEmitter}、{@link ItemSpraySimulator}、{@link ItemSprayPhysics}、
 * {@link ItemSprayGlobalSystem} 承担。
 */
public class ItemSprayFormRenderer extends FormRenderer<ItemSprayForm> implements ITickable
{
    static
    {
        // 提前触发全局系统类加载，保持与原实现相同的钩子注册时机
        ItemSprayGlobalSystem.ensureLoaded();
    }

    // ==================== 实例状态 ====================
    private final List<SprayedItem> localPreviewItems = new ArrayList<>();
    private int cooldown = 0;
    private int localPreviewCooldown = 0;
    private int lastLocalPreviewTick = -1;

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

    /** 发射与采样模块：负责按频率生成粒子 */
    private final ItemSprayEmitter emitter;
    /** 确定性模拟模块：负责确定性预览采样与真实世界快照发布 */
    private final ItemSpraySimulator simulator;

    // ==================== 构造 ====================
    public ItemSprayFormRenderer(ItemSprayForm form)
    {
        super(form);
        this.emitter = new ItemSprayEmitter(this, form, new Random());
        this.simulator = new ItemSpraySimulator(this, this.emitter, form);
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

        ItemSpraySource source = ItemSprayGlobalSystem.getSource(entity);
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
            this.cooldown = this.emitter.emit(this.cooldown, ItemSprayGlobalSystem.GLOBAL_ITEMS, this.cachedWorldOrigin, this.cachedWorldRotation, source);
        }
    }

    // ==================== 渲染模式判定 ====================
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
            && ItemSprayGlobalSystem.isMainWorldRenderPass()
            && context.type == FormRenderType.MODEL_BLOCK
            && !context.modelRenderer
            && !context.ui
            && ItemSprayGlobalSystem.getSource(context.entity) != null;
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

    // ==================== 物品展示图标 ====================
    private void clearItemDisplayRenderState()
    {
        this.cachedWorldOrigin = null;
        this.cachedWorldRotation = null;
        this.lastWorldRenderTime = 0L;
        this.cooldown = 0;
        this.localPreviewCooldown = 0;
        this.lastLocalPreviewTick = -1;
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
            ItemSprayGlobalSystem.applyBillboard(context.stack);

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

    // ==================== 粒子列表清理 ====================
    private void clearOwnedGlobalItems()
    {
        ItemSprayGlobalSystem.clearOwnedGlobalItems(this);
    }

    private void clearOwnedDeterministicWorldItems()
    {
        ItemSprayGlobalSystem.clearOwnedDeterministicWorldItems(this);
    }

    private void clearDeterministicWorldItems(ItemSpraySource source)
    {
        ItemSprayGlobalSystem.clearDeterministicWorldItems(source);
    }

    private void clearDeterministicWorldItemsForEditorPreview(FormRenderingContext context)
    {
        ItemSpraySource source = ItemSprayGlobalSystem.getSource(context.entity);

        if (source != null)
        {
            ItemSprayGlobalSystem.clearDeterministicWorldItems(source);
            ItemSprayGlobalSystem.DETERMINISTIC_SOURCE_CLEAR_FRAMES.remove(source);

            return;
        }

        this.clearOwnedDeterministicWorldItems();

        /* 表单预览可能使用临时实体，无法反查模型方块源。
         * 这时世界确定性快照本身也不该在预览界面背后显示，直接清掉这条专用缓存。 */
        ItemSprayGlobalSystem.DETERMINISTIC_WORLD_ITEMS.clear();
        ItemSprayGlobalSystem.DETERMINISTIC_SOURCE_CLEAR_FRAMES.clear();
    }

    private boolean hasFreshWorldTransform()
    {
        return this.lastWorldRenderTime > 0L && System.currentTimeMillis() - this.lastWorldRenderTime < 500L;
    }

    // ==================== 本地实时预览 ====================
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
            this.localPreviewCooldown = this.emitter.emit(this.localPreviewCooldown, this.localPreviewItems, origin, identity, null);

            // 本地预览只负责在编辑器里看见喷射效果，不拿局部坐标去误判真实世界碰撞。
            ItemSprayPhysics.updateItems(this.localPreviewItems, null);
        }

        this.lastLocalPreviewTick = tick;
    }

    // ==================== IRLights 阴影桥接入口（供 Mixin 调用，签名保持稳定） ====================
    /**
     * 给 IR Lights 阴影烘焙追加物品喷射投影物。
     *
     * 注入方只传入 IRL 的 OccluderSink 对象；这里通过反射调用 sink.emit(...)，避免 BBS++ 编译期依赖 IRL。
     * 所有喷射物品都按动态投影处理，防止停止/旋转/关键帧变化后复用到过期阴影。
     */
    public static void collectIRLiteShadowCasters(World world, Vec3d cameraPos, float tickDelta, Object sink)
    {
        ItemSprayIRLiteBridge.collectShadowCasters(world, cameraPos, tickDelta, sink, ItemSprayGlobalSystem.GLOBAL_ITEMS, ItemSprayGlobalSystem.DETERMINISTIC_WORLD_ITEMS);
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

    // ==================== 渲染 ====================
    public static final Link ICON = Link.assets("textures/itemspray.png");

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
        if (ItemSprayGlobalSystem.isShadowLikePass()) return;

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
            this.simulator.sampleDeterministicItems(context);
            this.clearOwnedGlobalItems();

            if (globalDeterministic)
            {
                this.simulator.publishDeterministicWorldItems(context);
            }
        }
        else if (localPreview)
        {
            this.updateLocalPreview(context);
        }
        else if (ItemSprayGlobalSystem.isMainWorldRenderPass())
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
                    this.emitter.getEmissionShape(),
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
            ItemSprayWorldItemRenderer.renderItems(context.stack, this.simulator.getDeterministicItems(), context.getTransition(), this.simulator.getPreviewWorld(context), context.light, context.overlay, null);
        }
        else if (localPreview)
        {
            ItemSprayWorldItemRenderer.renderItems(context.stack, this.localPreviewItems, context.getTransition(), this.simulator.getPreviewWorld(context), context.light, context.overlay, null);
        }
    }

    private void cacheWorldTransform(FormRenderingContext context)
    {
        Matrix4f posMat = context.stack.peek().getPositionMatrix();

        // 抵消当前世界渲染视图栈，只保留模型自身的旋转和平移。
        Matrix4f modelMat = this.simulator.getWorldViewInverseMatrix(context);
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

        return ItemSprayGlobalSystem.isMainWorldRenderPass() && this.form.showGuide.get();
    }
}
