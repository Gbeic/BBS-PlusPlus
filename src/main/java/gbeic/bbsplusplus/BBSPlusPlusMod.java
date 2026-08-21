package gbeic.bbsplusplus;

import gbeic.bbsplusplus.client.BBSPlusPlusClientEvents;
import gbeic.bbsplusplus.command.BBSPlusPlusCommand;
import gbeic.bbsplusplus.network.BBSPlusPlusNetwork;
import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.events.BBSAddonMod;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * BBS++ 主入口点。
 * <p>
 * NeoForge 通过 {@link Mod} 注解发现本类，并在构造时注入模组事件总线。与 Fabric 的
 * {@code ModInitializer} 不同，这里必须区分两条总线：模组总线（modBus）用于注册表和
 * 生命周期事件，游戏总线（{@link NeoForge#EVENT_BUS}）用于命令注册等运行期事件。
 * </p>
 * <p>
 * 实际的功能初始化仍由 {@link gbeic.bbsplusplus.mixin.BBSModMixin} 在 BBS 初始化末尾完成。
 * </p>
 */
@Mod(BBSPlusPlusMod.MOD_ID)
public class BBSPlusPlusMod implements BBSAddonMod
{
    public static final String MOD_ID = "bbsplusplus";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public BBSPlusPlusMod(IEventBus modBus)
    {
        LOGGER.info("BBS++ initialized!");

        // 表单注册必须在此处订阅：BBS 在自己的 FMLCommonSetupEvent 里派发 RegisterFormsEvent，
        // 推迟到客户端 setup 就赶不上了。
        BBSMod.events.register(new BBSPlusPlusForms());

        // 方块与物品走 DeferredRegister，必须在构造阶段挂到模组总线上
        BBSPlusPlusBlocks.register(modBus);

        // 自定义数据包类型同样只能在模组总线的注册事件里声明
        modBus.addListener(BBSPlusPlusNetwork::onRegisterPayloadHandlers);

        if (isModLoaded("aaa_particles"))
        {
            gbeic.bbsplusplus.keyframes.BBSPlusPlusKeyframeFactories.register();

            NeoForge.EVENT_BUS.addListener(this::onRegisterCommands);
        }
        // 注意：AAA 粒子表单由 BBSModMixin 在 BBSMod.onInitialize() TAIL 中注册，
        // 此处不做注册是因为 BBSMod.getForms() 此时尚未初始化。

        if (FMLEnvironment.dist == Dist.CLIENT)
        {
            BBSPlusPlusClientEvents.register(modBus);
        }
    }

    /**
     * 命令注册。Fabric 的 CommandRegistrationCallback 在 NeoForge 上对应
     * {@link RegisterCommandsEvent}，事件本身携带 dispatcher 与注册环境。
     */
    private void onRegisterCommands(RegisterCommandsEvent event)
    {
        BBSPlusPlusCommand.register(event.getDispatcher(), event.getBuildContext(), event.getCommandSelection());
    }

    /**
     * 判断指定模组是否已加载。替代 Fabric 的 {@code FabricLoader.isModLoaded}。
     */
    public static boolean isModLoaded(String modId)
    {
        return ModList.get().isLoaded(modId);
    }

    /**
     * 是否处于开发环境。替代 Fabric 的 {@code FabricLoader.isDevelopmentEnvironment}。
     */
    public static boolean isDevelopmentEnvironment()
    {
        return !FMLEnvironment.production;
    }
}
