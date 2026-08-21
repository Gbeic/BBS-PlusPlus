package gbeic.bbsplusplus;

import mchorse.bbs_mod.events.BBSAddonMod;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import gbeic.bbsplusplus.api.KeyframeTrackExtensionRegistry;
import gbeic.bbsplusplus.command.BBSPlusPlusCommand;
import gbeic.bbsplusplus.network.StructureStickNetworking;
import gbeic.bbsplusplus.structure.StructureStickRegistry;

/**
 * BBS++ 主入口点。
 * <p>
 * 实际初始化由 {@link gbeic.bbsplusplus.mixin.BBSModMixin} 在 BBS 初始化末尾完成。
 * </p>
 */
public class BBSPlusPlusMod implements ModInitializer, BBSAddonMod
{
    public static final String MOD_ID = "bbsplusplus";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize()
    {
        LOGGER.info("BBS++ initialized!");

        BBSPlusPlusBlocks.register();
        StructureStickRegistry.register();
        StructureStickNetworking.registerServer();
        this.registerKeyframeTrackExtensions();
        CommandRegistrationCallback.EVENT.register(BBSPlusPlusCommand::register);


        if (net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("aaa_particles"))
        {
            gbeic.bbsplusplus.keyframes.BBSPlusPlusKeyframeFactories.register();
        }
        // 注意：AAA 粒子表单由 BBSModMixin 在 BBSMod.onInitialize() TAIL 中注册，
        // 此处不做注册是因为 BBSMod.getForms() 此时尚未初始化。
    }

    /**
     * 注册 BBS++ 注入到原版形态上的关键帧轨道显示信息。
     * <p>
     * 这些轨道的真实属性 ID 必须稳定且带命名空间，避免和原版或第三方形态冲突；
     * 因此这里额外提供面向时间轴显示的默认英文名、中文名和排序锚点。
     * </p>
     */
    private void registerKeyframeTrackExtensions()
    {
        KeyframeTrackExtensionRegistry.register("bbspp_uv_transform", "Texture Transform", "纹理变换", "texture", null, null, "model");
    }
}
