package gbeic.bbsplusplus.mixin;

import mchorse.bbs_mod.ui.utils.keys.KeyCombo;
import mchorse.bbs_mod.ui.utils.keys.Keybind;
import mchorse.bbs_mod.ui.utils.keys.KeyAction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 修复：当快捷键未设置（KeyCombo为空）时，bbs 会默认将其识别为鼠标右键的严重 bug。
 * 
 * 原因：如果快捷键为空，combo.getMainKey() 会返回 -1。
 * 而在 bbs 中，鼠标右键的内部 ID 恰好也是 -1！
 * 这导致所有未设置的快捷键都会在玩家按下鼠标右键时被全部触发。
 */
@Mixin(value = Keybind.class, remap = false)
public class KeybindMixin
{
    @Shadow public KeyCombo combo;

    @Inject(method = "check", at = @At("HEAD"), cancellable = true)
    private void bbspp$checkEmptyCombo(int keyCode, KeyAction keyAction, boolean inside, CallbackInfoReturnable<Boolean> cir)
    {
        if (this.combo.keys.isEmpty())
        {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "checkMouse", at = @At("HEAD"), cancellable = true)
    private void bbspp$checkMouseEmptyCombo(int mouseButton, boolean inside, CallbackInfoReturnable<Boolean> cir)
    {
        if (this.combo.keys.isEmpty())
        {
            cir.setReturnValue(false);
        }
    }
}
