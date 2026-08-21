package gbeic.bbsplusplus.client.renderer;

import gbeic.bbsplusplus.BBSAddonsSettings;
import gbeic.bbsplusplus.client.debug.ItemSprayDebug;
import mchorse.bbs_mod.client.BBSRendering;
import mchorse.bbs_mod.events.ModelBlockEntityUpdateCallback;
import mchorse.bbs_mod.forms.entities.IEntity;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Frustum;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 物品喷射的全局粒子系统与渲染设置。
 *
 * 存放所有渲染器共享的静态粒子列表与发射源映射，并在类加载时注册全局生命周期钩子
 * （模型方块源追踪、逐刻物理更新、世界渲染阶段绘制）。渲染设置读取、IRLights 阴影烘焙
 * 状态反射与广告牌矩阵工具也集中在这里，让主渲染器只负责形态自身的调度。
 */
final class ItemSprayGlobalSystem
{
    private static final int DEFAULT_MAX_RENDERED_ITEMS = 1024;
    private static final int DEFAULT_MAX_RENDER_DISTANCE = 0;

    /** 存放所有已经发射出来的独立物理粒子。使用 CopyOnWriteArrayList 防止并发修改异常 */
    public static final List<SprayedItem> GLOBAL_ITEMS = new CopyOnWriteArrayList<>();
    /** 存放确定性预览在真实世界中的最后一次可见快照，让预览粒子脱离源模型方块裁剪继续显示 */
    public static final List<SprayedItem> DETERMINISTIC_WORLD_ITEMS = new CopyOnWriteArrayList<>();
    /** 记录每个模型方块源在当前世界渲染帧是否已经清过旧预览，避免同一模型内多个喷射形态互相覆盖 */
    public static final Map<ItemSpraySource, Long> DETERMINISTIC_SOURCE_CLEAR_FRAMES = new HashMap<>();
    /** 记录模型方块实体和真实方块位置的对应关系，用来判断发射源是否还存在 */
    private static final Map<IEntity, ItemSpraySource> MODEL_BLOCK_SOURCES = new WeakHashMap<>();
    /** IRLights 未作为编译依赖引入，运行时通过反射识别它自己的阴影烘焙状态 */
    private static Method irliteShadowBakeStateIsBaking;
    private static boolean irliteShadowBakeStateChecked;
    static long currentWorldRenderFrame;

    static
    {
        ModelBlockEntityUpdateCallback.EVENT.register(modelBlock ->
        {
            World world = modelBlock.getWorld();
            IEntity entity = modelBlock.getEntity();

            if (world != null && entity != null)
            {
                MODEL_BLOCK_SOURCES.put(entity, new ItemSpraySource(world, modelBlock.getPos(), modelBlock));
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

            ItemSprayDebug.recordGlobalItems(GLOBAL_ITEMS.size(), DETERMINISTIC_WORLD_ITEMS.size());

            // 调试开启时记录物理更新耗时，未开启时只有一次布尔判断的开销
            long physicsStartNanos = ItemSprayDebug.isEnabled() ? System.nanoTime() : 0L;
            ItemSprayPhysics.updateItems(GLOBAL_ITEMS, client.world);
            DETERMINISTIC_WORLD_ITEMS.removeIf((item) -> item.source != null && !item.source.isAlive(client.world));
            DETERMINISTIC_SOURCE_CLEAR_FRAMES.keySet().removeIf((source) -> !source.isAlive(client.world));

            if (ItemSprayDebug.isEnabled())
            {
                ItemSprayDebug.recordPhysicsNanos(System.nanoTime() - physicsStartNanos);
            }
        });

        WorldRenderEvents.START.register(ctx -> currentWorldRenderFrame++);

        // 全局渲染钩子 (负责把所有飞行的粒子独立画出来)
        WorldRenderEvents.AFTER_ENTITIES.register(ctx ->
        {
            if (isShadowLikePass()) return;
            if (GLOBAL_ITEMS.isEmpty() && DETERMINISTIC_WORLD_ITEMS.isEmpty()) return;

            // 每帧开始时清零剔除计数，HUD 只显示本帧数据
            ItemSprayDebug.resetFrameCounters();

            MatrixStack stack = ctx.matrixStack();
            Vec3d camPos = ctx.camera().getPos();
            Frustum frustum = isFrustumCullingEnabled() ? ctx.frustum() : null;
            double maxDistanceSq = getMaxRenderDistanceSquared();
            int maxRendered = getMaxRenderedItems();

            // 调试开启时记录世界渲染耗时，未开启时只有一次布尔判断的开销
            long renderStartNanos = ItemSprayDebug.isEnabled() ? System.nanoTime() : 0L;
            ItemSprayWorldItemRenderer.renderWorldItems(stack, ctx.tickDelta(), ctx.world(), 0, OverlayTexture.DEFAULT_UV, camPos, frustum, maxRendered, maxDistanceSq, GLOBAL_ITEMS, DETERMINISTIC_WORLD_ITEMS);
            ItemSprayDebug.recordRenderBudget(maxRendered, maxDistanceSq);

            if (ItemSprayDebug.isEnabled())
            {
                ItemSprayDebug.recordRenderNanos(System.nanoTime() - renderStartNanos);
            }
        });
    }

    private ItemSprayGlobalSystem()
    {}

    /**
     * 触发本类类加载，从而执行静态块中的全局钩子注册。
     * 由主渲染器类加载时调用，保证注册时机与原实现的静态块一致。
     */
    static void ensureLoaded()
    {
        // 静态块即类加载时执行，这里仅作为显式触发入口。
    }

    static ItemSpraySource getSource(IEntity entity)
    {
        return entity == null ? null : MODEL_BLOCK_SOURCES.get(entity);
    }

    static void clearOwnedGlobalItems(ItemSprayFormRenderer owner)
    {
        GLOBAL_ITEMS.removeIf((item) -> item.owner == owner);
    }

    static void clearOwnedDeterministicWorldItems(ItemSprayFormRenderer owner)
    {
        DETERMINISTIC_WORLD_ITEMS.removeIf((item) -> item.owner == owner);
    }

    static void clearDeterministicWorldItems(ItemSpraySource source)
    {
        if (source != null)
        {
            DETERMINISTIC_WORLD_ITEMS.removeIf((item) -> source.equals(item.source));
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
     * IRLights 阴影烘焙会用灯光矩阵重渲染模型方块，这类 pass 不能刷新物品喷射的世界坐标快照。
     */
    static boolean isShadowLikePass()
    {
        return BBSRendering.isIrisShadowPass() || isIRLiteShadowBakePass();
    }

    /**
     * 当前渲染是否属于主世界渲染阶段（排除各种阴影/烘焙 pass）。
     */
    static boolean isMainWorldRenderPass()
    {
        return BBSRendering.isRenderingWorld() && !isShadowLikePass();
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

    /**
     * 把当前矩阵的 3x3 旋转分量拍平成广告牌朝向，同时保留原始缩放。
     * 供普通世界渲染与物品展示图标共用。
     */
    static void applyBillboard(MatrixStack stack)
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
}
