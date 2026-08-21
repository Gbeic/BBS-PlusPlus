package gbeic.bbsplusplus.utils;

import mod.chloeprime.aaaparticles.api.client.effekseer.EffekseerManager;
import mod.chloeprime.aaaparticles.api.client.effekseer.ParticleEmitter;
import mod.chloeprime.aaaparticles.client.internal.CollisionCallbackSupport;

import java.lang.reflect.Field;

/**
 * X-Ray 管理器，用于处理穿透方块的粒子效果渲染。
*/

public class XRayManager {

    private static EffekseerManager XRAY_MANAGER = null;

    public static EffekseerManager get() {
        if (XRAY_MANAGER == null) {
            com.mojang.blaze3d.systems.RenderSystem.assertOnRenderThread();
            XRAY_MANAGER = new EffekseerManager();
            if (!XRAY_MANAGER.init(100000)) {
                System.err.println("Failed to initialize XRAY EffekseerManager");
            }
            XRAY_MANAGER.setCollisionCallback(CollisionCallbackSupport.Impl.DEFAULT_TRACER);
            XRAY_MANAGER.setupWorkerThreads(2);
        }
        return XRAY_MANAGER;
    }

    /**
     * 关闭 X-Ray 管理器，释放其持有的 EffekseerManager 原生资源。
     * <p>
     * 必须在游戏退出时调用，否则 Effekseer 原生 DLL 会在 JVM 卸载时因悬挂资源
     * 触发 STATUS_STACK_BUFFER_OVERRUN（退出码 -1073740791）。
     * </p>
     */
    public static void shutdown() {
        if (XRAY_MANAGER != null) {
            try {
                XRAY_MANAGER.stopAllEffects();
                XRAY_MANAGER.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
            XRAY_MANAGER = null;
        }
    }

    public static void migrate(ParticleEmitter emitter, boolean shouldBeXRay, mod.chloeprime.aaaparticles.api.client.EffectDefinition effectDef, ParticleEmitter.Type targetType) {
        try {
            Field managerField = ParticleEmitter.class.getDeclaredField("manager");
            managerField.setAccessible(true);
            Field handleField = ParticleEmitter.class.getDeclaredField("handle");
            handleField.setAccessible(true);

            EffekseerManager currentManager = (EffekseerManager) managerField.get(emitter);
            EffekseerManager targetManager;

            if (shouldBeXRay) {
                targetManager = get();
            } else {
                Field theOneManagersField = mod.chloeprime.aaaparticles.api.client.EffectDefinition.class.getDeclaredField("THE_ONE_MANAGERS");
                theOneManagersField.setAccessible(true);
                java.util.function.Supplier<?> supplier = (java.util.function.Supplier<?>) theOneManagersField.get(null);
                @SuppressWarnings("unchecked")
                java.util.EnumMap<ParticleEmitter.Type, EffekseerManager> map = (java.util.EnumMap<ParticleEmitter.Type, EffekseerManager>) supplier.get();
                targetManager = map.get(targetType);
            }

            if (currentManager == targetManager) {
                return;
            }

            int oldHandle = (int) handleField.get(emitter);
            currentManager.getImpl().Stop(oldHandle);

            int newHandle = targetManager.getImpl().Play(effectDef.getEffect().getImpl());

            managerField.set(emitter, targetManager);
            handleField.set(emitter, newHandle);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
