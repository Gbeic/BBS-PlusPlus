package gbeic.bbsplusplus.client;

import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.BBSModClient;
import gbeic.bbsplusplus.BBSPlusPlusMod;
import gbeic.bbsplusplus.forms.AAAParticleForm;
import gbeic.bbsplusplus.client.renderer.AAAParticleFormRenderer;
import gbeic.bbsplusplus.client.renderer.BBSEffectLoader;
import gbeic.bbsplusplus.client.ui.forms.editors.forms.UIAAAParticleForm;
import mchorse.bbs_mod.forms.FormUtilsClient;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.resources.packs.InternalAssetsSourcePack;
import mchorse.bbs_mod.ui.forms.editors.UIFormEditor;
import gbeic.bbsplusplus.util.FilmAutoGameModeRestoreState;
import net.minecraft.client.Minecraft;

import java.util.ArrayList;
import java.util.List;

/**
 * BBS++ 客户端初始化与逐帧逻辑。
 * <p>
 * 负责注册 AAA 粒子表单的渲染器、UI 编辑器面板和特效加载器。
 * </p>
 * <p>
 * 原先这是一个 Fabric 的 {@code ClientModInitializer}，回调由 Fabric API 直接投递；
 * 迁移到 NeoForge 后改由 {@link BBSPlusPlusClientEvents} 转接事件后调用这里的静态方法。
 * </p>
 */
public class BBSPlusPlusModClient
{
    /** BBS 事件总线要求注册实例对象，这里保留一个单例供其订阅 */
    private static final BBSPlusPlusModClient INSTANCE = new BBSPlusPlusModClient();

    private static boolean hasAAAParticles;

    /**
     * 客户端 setup 阶段调用，只做不依赖游戏完全启动的准备工作。
     */
    public static void init()
    {
        // 注册到 BBS 事件总线，接收 @Subscribe 事件
        BBSMod.events.register(INSTANCE);

        hasAAAParticles = BBSPlusPlusMod.isModLoaded("aaa_particles");

        if (!hasAAAParticles)
        {
            BBSPlusPlusMod.LOGGER.info("未检测到 aaa_particles 前置模组，跳过 AAA 粒子功能注册。");
        }
    }

    /**
     * 客户端完全启动后执行一次，对应 Fabric 的 CLIENT_STARTED。
     * <p>
     * 表单注册必须放在这里：BBS 的 {@code FormUtilsClient} 与表单分类要等本体初始化完毕才可用。
     * </p>
     */
    public static void onClientStarted(Minecraft client)
    {
        // ItemSprayForm 不依赖 aaa_particles，始终注册
        try
        {
            FormUtilsClient.register(gbeic.bbsplusplus.forms.ItemSprayForm.class, gbeic.bbsplusplus.client.renderer.ItemSprayFormRenderer::new);
            UIFormEditor.register(gbeic.bbsplusplus.forms.ItemSprayForm.class, gbeic.bbsplusplus.client.ui.forms.editors.forms.UIItemSprayForm::new);

            addFormToExtraCategory(new gbeic.bbsplusplus.forms.ItemSprayForm(), "ItemSprayForm");
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }

        // 注册资源包，用于提供粒子预览图等资源
        try
        {
            BBSMod.getProvider().register(new InternalAssetsSourcePack("bbsplusplus", "assets/bbsplusplus", BBSPlusPlusModClient.class));
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }

        if (hasAAAParticles)
        {
            registerAAAParticles();
        }
    }

    /**
     * 每客户端 tick 末尾执行，对应 Fabric 的 END_CLIENT_TICK。
     */
    public static void onClientTick(Minecraft client)
    {
        // 自动旁观模式异常退出兜底：启动后检测残留状态
        FilmAutoGameModeRestoreState.tick(client);

        if (hasAAAParticles)
        {
            tickAAAParticleCleanup();
        }
    }

    /**
     * 游戏关闭前执行，对应 Fabric 的 CLIENT_STOPPING。
     */
    public static void onClientStopping(Minecraft client)
    {
        // 自动旁观模式异常退出兜底：关闭前尽量恢复
        FilmAutoGameModeRestoreState.tryRestoreBeforeShutdown(client);

        if (hasAAAParticles)
        {
            gbeic.bbsplusplus.utils.XRayManager.shutdown();
        }
    }

    /**
     * AAA 粒子表单及其关键帧工厂的注册，仅在检测到 aaa_particles 时执行。
     */
    private static void registerAAAParticles()
    {
        try
        {
            FormUtilsClient.register(AAAParticleForm.class, AAAParticleFormRenderer::new);
            UIFormEditor.register(AAAParticleForm.class, UIAAAParticleForm::new);

            mchorse.bbs_mod.ui.framework.elements.input.keyframes.factories.UIKeyframeFactory.register(
                gbeic.bbsplusplus.keyframes.BBSPlusPlusKeyframeFactories.AAA_EFFECT,
                gbeic.bbsplusplus.client.ui.keyframes.UIAAAParticleLinkKeyframeFactory::new
            );
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }

        // 确保 effekseer 特效文件夹存在
        try
        {
            java.io.File effeksDir = new java.io.File(
                mchorse.bbs_mod.BBSMod.getAssetsFolder(), "effeks");
            if (!effeksDir.exists())
            {
                effeksDir.mkdirs();
                BBSPlusPlusMod.LOGGER.info("已创建 effeks 特效文件夹: {}",
                    effeksDir.getAbsolutePath());
            }
        }
        catch (Exception e)
        {
            BBSPlusPlusMod.LOGGER.error("创建 effeks 特效文件夹失败", e);
        }

        addFormToExtraCategory(new AAAParticleForm(), "AAAParticleForm");
    }

    /**
     * 全局清理观察器 —— 当表单渲染器不再活跃时停止特效。
     */
    private static void tickAAAParticleCleanup()
    {
        if (BBSEffectLoader.beginReload())
        {
            try
            {
                List<AAAParticleFormRenderer> renderers = new ArrayList<>(AAAParticleFormRenderer.activeRenderers);

                for (AAAParticleFormRenderer renderer : renderers)
                {
                    renderer.cleanup();
                }

                BBSEffectLoader.markCacheDirty();
            }
            finally
            {
                BBSEffectLoader.endReload();
            }
        }

        if (!AAAParticleFormRenderer.activeRenderers.isEmpty())
        {
            List<AAAParticleFormRenderer> renderers = new ArrayList<>(AAAParticleFormRenderer.activeRenderers);

            for (AAAParticleFormRenderer renderer : renderers)
            {
                renderer.checkCleanup();
            }
        }
    }

    /**
     * 把表单挂到 BBS 表单选择器的「额外」分类下。
     * <p>
     * BBS 没有对外暴露该分类，只能反射进 {@code FormCategories} 里找到 ExtraFormSection。
     * ItemSpray 与 AAA 粒子两处逻辑完全一致，故合并到这里。
     * </p>
     *
     * @param form      要添加的表单实例
     * @param debugName 日志中显示的表单名
     */
    private static void addFormToExtraCategory(Form form, String debugName)
    {
        try
        {
            if (BBSModClient.getFormCategories() == null)
            {
                return;
            }

            var fc = BBSModClient.getFormCategories();
            var sectionsField = fc.getClass().getDeclaredField("sections");
            sectionsField.setAccessible(true);
            @SuppressWarnings("unchecked")
            var sections = (java.util.List<Object>) sectionsField.get(fc);

            for (var section : sections)
            {
                if ("ExtraFormSection".equals(section.getClass().getSimpleName()))
                {
                    var extraField = section.getClass().getDeclaredField("extra");
                    extraField.setAccessible(true);
                    var extraCategory = extraField.get(section);
                    if (extraCategory != null)
                    {
                        var addFormMethod = extraCategory.getClass().getMethod("addForm", Form.class);
                        addFormMethod.invoke(extraCategory, form);
                        BBSPlusPlusMod.LOGGER.info("已将 {} 添加到额外分类", debugName);
                    }
                }
            }
        }
        catch (Exception ex)
        {
            BBSPlusPlusMod.LOGGER.warn("无法将 {} 添加到额外分类: {}", debugName, ex.getMessage());
        }
    }
}
