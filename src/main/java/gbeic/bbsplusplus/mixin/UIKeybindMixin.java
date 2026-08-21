package gbeic.bbsplusplus.mixin;

import mchorse.bbs_mod.graphics.window.Window;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.input.UIKeybind;
import mchorse.bbs_mod.ui.utils.keys.KeyAction;
import mchorse.bbs_mod.ui.utils.keys.KeyCombo;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.function.Consumer;

/**
 * 修复快捷键设置界面中无法使用 ESC 取消绑定的问题。
 *
 * BBS 原版在 escape 模式下（允许 ESC 被绑定为快捷键），
 * 完全禁用了 ESC 取消功能，导致用户没有任何办法取消正在进行的快捷键录入。
 *
 * 修复逻辑：当处于 escape 模式且用户单独按下 ESC（不组合任何修饰键且当前没有其他键被录入）时，
 * 视为"取消绑定"操作，清空快捷键并结束录入。
 * 如果已经有其他键被按住/录入后再按 ESC，则正常将 ESC 作为组合键的一部分。
 */
@Mixin(value = UIKeybind.class, remap = false)
public class UIKeybindMixin
{
    @Shadow public KeyCombo combo;
    @Shadow public boolean reading;
    @Shadow public Consumer<KeyCombo> callback;

    /**
     * 在 subKeyPressed 的最开头拦截。
     * 当正在录入快捷键时，如果单独按下了 ESC（无修饰键、当前组合键列表为空），
     * 则清空快捷键并结束录入——视为"取消"操作。
     */
    @Inject(method = "subKeyPressed", at = @At("HEAD"), cancellable = true)
    private void bbspp$allowEscCancel(UIContext context, CallbackInfoReturnable<Boolean> cir)
    {
        if (this.reading
            && context.getKeyAction() == KeyAction.PRESSED
            && context.getKeyCode() == GLFW.GLFW_KEY_ESCAPE
            && this.combo.keys.isEmpty()
            && !Window.isCtrlPressed()
            && !Window.isShiftPressed()
            && !Window.isAltPressed())
        {
            // 单独按 ESC（无修饰键、无其他已录入的键）→ 清空快捷键并结束录入
            this.combo.keys.clear();
            this.reading = false;

            if (this.callback != null)
            {
                this.callback.accept(this.combo);
            }

            cir.setReturnValue(true);
        }
    }
}
