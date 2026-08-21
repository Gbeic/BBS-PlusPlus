package gbeic.bbsplusplus;

import net.fabricmc.api.ClientModInitializer;

/**
 * IRLightsZH 汉化插件入口点。
 * 翻译逻辑完全由 StringKeyMixin 在运行时拦截完成，无需额外初始化。
 */
public class IRLightsZH implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        System.out.println("[IRLightsZH] IRLights 中文汉化插件已加载。");
    }
}
