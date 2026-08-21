package gbeic.bbsplusplus.mixin;

import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.l10n.L10n;
import mchorse.bbs_mod.resources.Link;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.io.InputStream;
import java.util.Collections;
import java.util.List;

/**
 * 向 {@link L10n} 注册 BBS-Addons 自定义的本地化文件。
 * <p>
 * 在 L10n 构造完成后，注册额外的语言文件加载源。
 * BBS 会先请求默认语言 {@code en_us}，再请求玩家当前语言；这里按语言代码动态查找对应文件。
 * 文件位于 {@code assets/bbs/assets/lang/} 下，通过 Fabric 资源合并机制与 BBS 原有文件共存。
 * 如果目标语言文件不存在，则返回空列表，让 BBS 安静回落到默认英文。
 * </p>
 */
@Mixin(L10n.class)
public class L10nMixin
{
    /**
     * 注入目标：{@link L10n} 构造方法结束处。
     * 注入原因：BBS++ 的 UI 文本放在 BBS 自建 L10n 系统目录中，需要额外注册语言文件来源。
     * 修改后的行为：按当前语言代码加载对应 JSON，缺失时安静回落到 BBS 已加载的默认英文。
     */
    @Inject(method = "<init>()V", at = @At("RETURN"), remap = false)
    private void afterInit(CallbackInfo ci)
    {
        L10n self = (L10n) (Object) this;

        self.register(L10nMixin::bbspp$getLanguageLinks);
    }

    private static List<Link> bbspp$getLanguageLinks(String lang)
    {
        Link link = Link.assets("lang/" + lang + ".json");

        return bbspp$hasLanguageFile(link)
            ? Collections.singletonList(link)
            : Collections.emptyList();
    }

    private static boolean bbspp$hasLanguageFile(Link link)
    {
        try (InputStream ignored = BBSMod.getProvider().getAsset(link))
        {
            return true;
        }
        catch (Exception e)
        {
            return false;
        }
    }
}
