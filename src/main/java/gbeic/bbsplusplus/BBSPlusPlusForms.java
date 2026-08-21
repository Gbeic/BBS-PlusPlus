package gbeic.bbsplusplus;

import gbeic.bbsplusplus.forms.AAAParticleForm;
import gbeic.bbsplusplus.forms.ItemSprayForm;
import mchorse.bbs_mod.events.Subscribe;
import mchorse.bbs_mod.events.register.RegisterFormsEvent;
import mchorse.bbs_mod.resources.Link;

/**
 * 向 BBS 注册 BBS++ 自定义表单类型（物品喷射、AAA 粒子）。
 * <p>
 * 早先的实现是用 Mixin 注入 {@code BBSMod.onInitialize()} 的 TAIL，以确保
 * {@code forms = new FormArchitect()} 已经执行完毕。但 FSR 迁移到 NeoForge 后，
 * Fabric 的入口方法 {@code onInitialize} 已不复存在（改为私有的 {@code onCommonSetup}），
 * 继续按方法名注入既会崩溃，也会在 BBS 内部重构时反复失效。
 * </p>
 * <p>
 * 因此改用 BBS 官方提供的 {@link RegisterFormsEvent}：该事件正是在 FormArchitect
 * 构造完毕之后、其余子系统初始化之前派发的，时机与原先的注入点等价，而且属于对外
 * 契约，不依赖任何私有方法名。
 * </p>
 * <p>
 * <b>时序要求</b>：事件在 BBS 的 {@code FMLCommonSetupEvent} 阶段派发，所以订阅动作
 * 必须早于该阶段完成——即在 {@link BBSPlusPlusMod} 的构造器里注册，不能推迟到客户端
 * setup，否则事件早已发完，注册不会生效。
 * </p>
 */
public class BBSPlusPlusForms
{
    /**
     * 表单注册事件回调。
     * <p>
     * 注意：BBS 的 EventBus 只扫描订阅者<b>自身声明</b>的方法（{@code getDeclaredMethods}），
     * 且要求方法有且仅有一个参数，参数类型即事件类型。
     * </p>
     */
    @Subscribe
    public void onRegisterForms(RegisterFormsEvent event)
    {
        // 物品喷射表单不依赖任何前置模组，始终注册
        try
        {
            event.register(Link.bbs("item_spray"), ItemSprayForm.class);
            BBSPlusPlusMod.LOGGER.info("已注册 ItemSprayForm 到 FormArchitect");
        }
        catch (Exception e)
        {
            BBSPlusPlusMod.LOGGER.error("注册 ItemSprayForm 失败", e);
        }

        // AAA 粒子表单依赖 Effekseer 引擎，缺少前置时跳过，避免加载表单类时抛出 NoClassDefFoundError
        if (!BBSPlusPlusMod.isModLoaded("aaa_particles"))
        {
            BBSPlusPlusMod.LOGGER.info("未检测到 aaa_particles 模组，跳过注册 AAAParticleForm");

            return;
        }

        try
        {
            event.register(Link.bbs("aaa_particle"), AAAParticleForm.class);
            BBSPlusPlusMod.LOGGER.info("已注册 AAAParticleForm 到 FormArchitect");
        }
        catch (Exception e)
        {
            BBSPlusPlusMod.LOGGER.error("注册 AAAParticleForm 失败", e);
        }
    }
}
