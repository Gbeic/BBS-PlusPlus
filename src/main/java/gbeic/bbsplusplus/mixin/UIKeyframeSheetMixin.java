package gbeic.bbsplusplus.mixin;

import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.settings.values.base.BaseValueBasic;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframeSheet;
import mchorse.bbs_mod.ui.utils.icons.Icon;
import mchorse.bbs_mod.utils.keyframes.KeyframeChannel;
import gbeic.bbsplusplus.KeyframeLocalizer;
import gbeic.bbsplusplus.client.KeyframeTrackStyle;
import gbeic.bbsplusplus.api.KeyframeTrackExtensionRegistry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 为 {@link UIKeyframeSheet} 的轨道名称添加中文本地化支持。
 * <p>
 * 当 {@link gbeic.bbsplusplus.BBSAddonsSettings#chineseKeyframeNames} 开启时，
 * 拦截 {@code IKey.constant(name)} 调用，将已知的英文轨道名称替换为中文。
 * </p>
 */
@Mixin(UIKeyframeSheet.class)
public class UIKeyframeSheetMixin
{
    @Shadow
    private Icon icon;

    /**
     * 注入目标：{@link UIKeyframeSheet#getTrackTitle} 静态辅助方法中的首个
     * {@code IKey.constant(...)} 调用（FSR 0.0.11 起轨道标题生成从构造器抽到了该方法）。
     * 注入原因：4 参构造器现在只是委托，不再直接调用 {@code IKey.constant}，原注入点已失效。
     * 修改行为：当 {@code chineseKeyframeNames} 开启时把英文轨道名替换为中文。
     */
    @ModifyArg(
        method = "getTrackTitle",
        at = @At(
            value = "INVOKE",
            target = "Lmchorse/bbs_mod/l10n/keys/IKey;constant(Ljava/lang/String;)Lmchorse/bbs_mod/l10n/keys/IKey;"
        ),
        index = 0,
        remap = false
    )
    private static String localizeTrackName(String name)
    {
        String localized = KeyframeLocalizer.localize(name);

        return localized != null ? localized : KeyframeTrackExtensionRegistry.replaceTailWithDefaultName(name);
    }

    /**
     * 注入目标：{@link UIKeyframeSheet} 完整构造函数结束处。
     * 注入原因：构造完成后才能通过 {@code property} 反查轨道所属形态。
     * 修改行为：只对 BBS++ 指定形态应用上下文相关的名称、颜色和图标，避免按 key 全局覆盖。
     */
    @Inject(
        method = "<init>(Ljava/lang/String;Lmchorse/bbs_mod/l10n/keys/IKey;IZLmchorse/bbs_mod/utils/keyframes/KeyframeChannel;Lmchorse/bbs_mod/settings/values/base/BaseValueBasic;Z)V",
        at = @At("RETURN"),
        remap = false
    )
    private void bbspp$applyContextualTrackStyle(String id, IKey title, int color, boolean separator, KeyframeChannel<?> channel, BaseValueBasic<?> property, boolean isBoneTrack, CallbackInfo ci)
    {
        KeyframeTrackStyle.apply((UIKeyframeSheet) (Object) this);
    }

    /**
     * 注入目标：{@link UIKeyframeSheet#icon(Icon)}。
     * 注入原因：调用方会在构造后再次按全局 key 设置图标，可能覆盖物品喷射专属图标。
     * 修改行为：当轨道属于 BBS++ 专属形态时，使用上下文图标并跳过全局图标。
     */
    @Inject(method = "icon", at = @At("HEAD"), cancellable = true, remap = false)
    private void bbspp$useContextualIcon(Icon icon, CallbackInfoReturnable<UIKeyframeSheet> cir)
    {
        Icon contextual = KeyframeTrackStyle.getIconOverride((UIKeyframeSheet) (Object) this);

        if (contextual != null)
        {
            this.icon = contextual;
            cir.setReturnValue((UIKeyframeSheet) (Object) this);
        }
    }
}
