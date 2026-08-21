package gbeic.bbsplusplus.mixin;

import mchorse.bbs_mod.BBSMod;
import gbeic.bbsplusplus.forms.AAAParticleForm;
import gbeic.bbsplusplus.forms.StructureForm;
import gbeic.bbsplusplus.structure.StructureStickRegistry;
import mchorse.bbs_mod.resources.Link;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 在 BBSMod 初始化完成后注册 AAA 粒子表单类型。
 * <p>
 * 通过 Mixin 注入确保 {@link BBSMod#onInitialize()} 中的
 * {@code forms = new FormArchitect()} 已经执行完毕，
 * 避免 Fabric 入口点执行时序导致 {@code getForms()} 返回 null。
 * </p>
 */
@Mixin(BBSMod.class)
public class BBSModMixin
{
    private static final Logger LOGGER = LoggerFactory.getLogger("bbsplusplus");

    @Inject(method = "onInitialize", at = @At("TAIL"), remap = false)
    private void afterInit(CallbackInfo ci)
    {
        if (net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("aaa_particles"))
        {
            try
            {
                BBSMod.getForms().register(Link.bbs("aaa_particle"), AAAParticleForm.class);
                LOGGER.info("已注册 AAAParticleForm 到 FormArchitect");
            }
            catch (Exception e)
            {
                LOGGER.error("注册 AAAParticleForm 失败", e);
            }
        }
        else
        {
            LOGGER.info("未检测到 aaa_particles 模组，跳过注册 AAAParticleForm");
        }

        try
        {
            BBSMod.getForms().register(Link.bbs("item_spray"), gbeic.bbsplusplus.forms.ItemSprayForm.class);
            LOGGER.info("已注册 ItemSprayForm 到 FormArchitect");
        }
        catch (Exception e)
        {
            LOGGER.error("注册 ItemSprayForm 失败", e);
        }

        /* 结构表单沿用 BBSTools 的注册 ID，这样用老版 BBSTools 做的工程可以直接读取 */
        try
        {
            BBSMod.getForms().register(Link.bbs("structure"), StructureForm.class);
            StructureStickRegistry.prepareAssetsFolder();
            LOGGER.info("已注册 StructureForm 到 FormArchitect");
        }
        catch (Exception e)
        {
            LOGGER.error("注册 StructureForm 失败", e);
        }
    }
}