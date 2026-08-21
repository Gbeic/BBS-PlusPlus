package gbeic.bbsplusplus.mixin;

import mchorse.bbs_mod.l10n.keys.StringKey;
import gbeic.bbsplusplus.BBSVFXZHTranslator;
import gbeic.bbsplusplus.IRLightsZHTranslator;
import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Locale;

/**
 * 拦截 StringKey.get()，如果其持有的字符串是外部插件的硬编码文本，
 * 则直接返回对应的中文翻译。
 * 不依赖 BBS L10n 系统，不存在时序问题。
 */
@Mixin(value = StringKey.class, remap = false)
public class StringKeyMixin
{
    /**
     * 注入目标：{@link StringKey#get()}。
     * 注入原因：IRLights 与 BBS VFX 都存在大量 {@code IKey.constant("英文")} 硬编码文本，无法依赖资源语言文件覆盖。
     * 修改行为：读取字符串时查询插件汉化表，命中后直接返回中文。
     */
    @Inject(method = "get", at = @At("HEAD"), cancellable = true)
    private void onGet(CallbackInfoReturnable<String> cir)
    {
        StringKey self = (StringKey) (Object) this;
        if (self.string == null || self.string.isEmpty()) return;
        if (!bbspp$isChineseLanguage()) return;

        String chinese = IRLightsZHTranslator.getChinese(self.string);
        if (chinese == null)
        {
            chinese = BBSVFXZHTranslator.getChinese(self.string);
        }

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
