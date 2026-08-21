package gbeic.bbsplusplus.mixin;

import mchorse.bbs_mod.l10n.keys.LangKey;
import gbeic.bbsplusplus.IRLightsZHTranslator;
import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Locale;

/**
 * 拦截 LangKey.get()，对受支持外部插件注册的 BBS 设置项键
 * 返回中文翻译。LangKey 用于 BBS L10n 系统中的设置面板标签和注释。
 */
@Mixin(value = LangKey.class, remap = false)
public class LangKeyMixin
{
    /**
     * 注入目标：{@link LangKey#get()}。
     * 注入原因：部分外部插件会在 L10n 重载时直接写入英文 {@code LangKey.content}，普通 JSON 语言文件无法稳定覆盖。
     * 修改行为：按 key 查询插件汉化表，命中 IRLights 的键时返回中文，其余键保持原逻辑。
     */
    @Inject(method = "get", at = @At("HEAD"), cancellable = true)
    private void onGet(CallbackInfoReturnable<String> cir)
    {
        LangKey self = (LangKey) (Object) this;
        if (self.key == null || self.key.isEmpty()) return;
        if (!bbspp$isChineseLanguage()) return;

        // 仅翻译已登记的外部插件 key，不干涉其他模组。
        String chinese = IRLightsZHTranslator.getChineseForKey(self.key);

        if (chinese != null)
        {
            cir.setReturnValue(chinese);
        }
    }

    private static boolean bbspp$isChineseLanguage()
    {
        try
        {
            MinecraftClient client = MinecraftClient.getInstance();

            if (client != null && client.getLanguageManager() != null)
            {
                String language = client.getLanguageManager().getLanguage();

                return language != null && language.toLowerCase(Locale.ROOT).startsWith("zh");
            }
        }
        catch (Throwable ignored)
        {
            return true;
        }

        return true;
    }
}
