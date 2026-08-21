package gbeic.bbsplusplus.mixin;

import gbeic.bbsplusplus.IMBlockerCompat;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.input.text.UIBaseTextbox;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 为 {@link UIBaseTextbox} 的焦点事件添加 IMBlocker 输入法管理支持。
 * <p>
 * 文本框获得焦点时启用输入法（{@link IMBlockerCompat#enableIM()}），
 * 失去焦点时禁用输入法（{@link IMBlockerCompat#disableIM()}）。
 * 若 IMBlocker 未安装，调用会被静默忽略。
 * </p>
 */
@Mixin(UIBaseTextbox.class)
public class UIBaseTextboxMixin
{
    @Inject(method = "focus", at = @At("TAIL"), remap = false)
    private void onFocus(UIContext context, CallbackInfo ci)
    {
        IMBlockerCompat.setTextInputFocused(true);
    }

    @Inject(method = "unfocus", at = @At("TAIL"), remap = false)
    private void onUnfocus(UIContext context, CallbackInfo ci)
    {
        IMBlockerCompat.setTextInputFocused(false);
    }
}
