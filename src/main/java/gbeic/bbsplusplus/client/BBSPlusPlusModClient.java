package gbeic.bbsplusplus.client;

import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.BBSModClient;
import gbeic.bbsplusplus.BBSPlusPlusMod;
import gbeic.bbsplusplus.forms.AAAParticleForm;
import gbeic.bbsplusplus.forms.StructureForm;
import gbeic.bbsplusplus.client.renderer.AAAParticleFormRenderer;
import gbeic.bbsplusplus.client.renderer.BBSEffectLoader;
import gbeic.bbsplusplus.client.renderer.StructureFormRenderer;
import gbeic.bbsplusplus.client.structure.StructureStickSelection;
import gbeic.bbsplusplus.client.structure.StructureStickTooltip;
import gbeic.bbsplusplus.client.structure.VFXDestructionWandSelection;
import gbeic.bbsplusplus.client.structure.VFXDestructionWandTooltip;
import gbeic.bbsplusplus.client.ui.forms.editors.forms.UIAAAParticleForm;
import gbeic.bbsplusplus.client.ui.forms.editors.forms.UIStructureForm;
import gbeic.bbsplusplus.structure.StructureStickRegistry;
import mchorse.bbs_mod.forms.FormUtilsClient;
import mchorse.bbs_mod.resources.packs.InternalAssetsSourcePack;
import mchorse.bbs_mod.ui.forms.editors.UIFormEditor;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import gbeic.bbsplusplus.network.BBSPlusPlusNetwork;
import gbeic.bbsplusplus.util.FilmAutoGameModeRestoreState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.util.math.BlockPos;
import mchorse.bbs_mod.blocks.entities.ModelBlockEntity;

import java.util.ArrayList;
import java.util.List;
import net.fabricmc.loader.api.FabricLoader;

/**
 * BBS++ 客户端入口点。
 * <p>
 * 负责注册 AAA 粒子表单的渲染器、UI 编辑器面板和特效加载器。
 * </p>
 */
public class BBSPlusPlusModClient implements ClientModInitializer
{
    @Override
    public void onInitializeClient()
    {
        // 注册到 BBS 事件总线，接收 @Subscribe 事件
        BBSMod.events.register(this);

        // 自动旁观模式异常退出兜底：启动后检测残留状态，关闭前尽量恢复。
        ClientTickEvents.END_CLIENT_TICK.register(FilmAutoGameModeRestoreState::tick);
        ClientLifecycleEvents.CLIENT_STOPPING.register(FilmAutoGameModeRestoreState::tryRestoreBeforeShutdown);

        boolean hasAAAParticles = FabricLoader.getInstance().isModLoaded("aaa_particles");

        if (hasAAAParticles)
        {
            this.registerAAAParticles();
        }
        else
        {
            BBSPlusPlusMod.LOGGER.info("未检测到 aaa_particles 前置模组，跳过 AAA 粒子功能注册。");
        }

        // ItemSprayForm 不依赖 aaa_particles，始终注册
        ClientLifecycleEvents.CLIENT_STARTED.register(client ->
        {
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
        });

        this.registerStructureStick();
        this.registerVFXDestructionWand();

        // 注册资源包，用于提供粒子预览图等资源
        ClientLifecycleEvents.CLIENT_STARTED.register(client ->
        {
            try
            {
                BBSMod.getProvider().register(new InternalAssetsSourcePack("bbsplusplus", "assets/bbsplusplus", BBSPlusPlusModClient.class));
            }
            catch (Exception e)
            {
                e.printStackTrace();
            }
        });
    }

    private void registerAAAParticles()
    {
        // 注册全局清理观察器 —— 当表单渲染器不再活跃时停止特效
        ClientTickEvents.END_CLIENT_TICK.register(client ->
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
        });

        // 注册客户端网络接收器，处理服务端发来的触发信号
        ClientPlayNetworking.registerGlobalReceiver(BBSPlusPlusNetwork.TRIGGER_PARTICLE, (client, handler, buf, responseSender) ->
        {
            BlockPos pos = buf.readBlockPos();
            int triggerIndex = buf.readInt();

            client.execute(() ->
            {
                if (client.world != null)
                {
                    BlockEntity be = client.world.getBlockEntity(pos);
                    if (be instanceof ModelBlockEntity modelBe)
                    {
                        if (modelBe.getProperties().getForm() instanceof AAAParticleForm aaaForm)
                        {
                            if (triggerIndex >= 0 && triggerIndex < aaaForm.manualTriggerPulse.length)
                            {
                                aaaForm.manualTriggerPulse[triggerIndex] = true;
                            }
                        }
                    }
                }
            });
        });

        // 注册游戏退出时的资源清理
        ClientLifecycleEvents.CLIENT_STOPPING.register(client ->
        {
            gbeic.bbsplusplus.utils.XRayManager.shutdown();
        });

        // 在客户端启动完成后注册表单组件
        ClientLifecycleEvents.CLIENT_STARTED.register(client ->
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

            // 将 AAA 粒子表单添加到额外分类
            try
            {
                addFormToExtraCategory(new AAAParticleForm(), "AAAParticleForm");
            }
            catch (Exception ex)
            {
                BBSPlusPlusMod.LOGGER.warn("无法将 AAA 粒子表单添加到额外分类: {}", ex.getMessage());
            }
        });
    }

    /**
     * 注册结构棒的客户端交互与结构表单。
     * <p>
     * 移植自 BBSTools 4.1。结构棒的所有操作（框选、画线框、导出）都在客户端完成，
     * 因此这里同时接上按键轮询、世界渲染回调和物品提示。
     * </p>
     */
    private void registerStructureStick()
    {
        StructureStickSelection.register();
        StructureStickTooltip.register();

        ClientLifecycleEvents.CLIENT_STARTED.register(client ->
        {
            try
            {
                FormUtilsClient.register(StructureForm.class, StructureFormRenderer::new);
                UIFormEditor.register(StructureForm.class, UIStructureForm::new);

                StructureStickRegistry.prepareAssetsFolder();
                addFormToExtraCategory(createPreviewStructureForm(), "StructureForm");
            }
            catch (Exception e)
            {
                BBSPlusPlusMod.LOGGER.warn("注册结构表单失败: {}", e.getMessage());
            }
        });
    }

    /**
     * 注册 BBS VFX 破坏魔杖的手感增强。
     * <p>
     * 该功能只在 {@code xavin} 已加载时启用，并且通过物品 ID 与反射对接 VFX，
     * 避免 BBS++ 对第三方插件形成硬依赖。
     * </p>
     */
    private void registerVFXDestructionWand()
    {
        if (!FabricLoader.getInstance().isModLoaded("xavin"))
        {
            return;
        }

        VFXDestructionWandSelection.register();
        VFXDestructionWandTooltip.register();
    }

    /**
     * 给「额外」分类里的结构表单预填一个已有的结构文件。
     * <p>
     * 否则分类里显示的是一个空表单，预览框里什么都看不到。
     * </p>
     */
    private static StructureForm createPreviewStructureForm()
    {
        StructureForm form = new StructureForm();

        try
        {
            for (mchorse.bbs_mod.resources.Link link : mchorse.bbs_mod.BBSMod.getProvider().getLinksFromPath(mchorse.bbs_mod.resources.Link.assets("structures")))
            {
                if (link.path.toLowerCase().endsWith(".nbt"))
                {
                    form.structureFile.set(link.path);

                    break;
                }
            }
        }
        catch (Exception ignored)
        {}

        return form;
    }

    /**
     * 把一个表单实例塞进伪装界面的「额外」分类。
     * <p>
     * BBS 没有公开的注册入口，只能反射拿到 {@code ExtraFormSection} 里的分类对象再调 {@code addForm}。
     * </p>
     */
    private static void addFormToExtraCategory(mchorse.bbs_mod.forms.forms.Form form, String name) throws Exception
    {
        if (BBSModClient.getFormCategories() == null)
        {
            return;
        }

        var categories = BBSModClient.getFormCategories();
        var sectionsField = categories.getClass().getDeclaredField("sections");

        sectionsField.setAccessible(true);

        @SuppressWarnings("unchecked")
        var sections = (java.util.List<Object>) sectionsField.get(categories);

        for (var section : sections)
        {
            if (!"ExtraFormSection".equals(section.getClass().getSimpleName()))
            {
                continue;
            }

            var extraField = section.getClass().getDeclaredField("extra");

            extraField.setAccessible(true);

            var extraCategory = extraField.get(section);

            if (extraCategory != null)
            {
                var addFormMethod = extraCategory.getClass().getMethod("addForm", mchorse.bbs_mod.forms.forms.Form.class);

                addFormMethod.invoke(extraCategory, form);
                BBSPlusPlusMod.LOGGER.info("已将 {} 添加到额外分类", name);
            }
        }
    }
}
