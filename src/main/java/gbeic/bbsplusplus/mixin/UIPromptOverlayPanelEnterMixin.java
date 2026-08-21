package gbeic.bbsplusplus.mixin;

import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlayPanel;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIPromptOverlayPanel;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Mixin — 在 {@link UIPromptOverlayPanel} 中输入文本时，按回车快速确认。
 * <p>
 * bbs-fs 的 {@link UIOverlayPanel#subKeyPressed} 仅在
 * {@code !context.isFocused()} 时响应确认快捷键，而输入框有焦点时不会触发。
 * 此 Mixin 在 {@link UIOverlayPanel} 层注入，当实例为 {@link UIPromptOverlayPanel}
 * 且输入框有焦点并按下回车时直接调用 {@link UIPromptOverlayPanel#confirm()}。
 * </p>
 */
@Mixin(UIOverlayPanel.class)
public abstract class UIPromptOverlayPanelEnterMixin
{
    @Inject(
        method = "subKeyPressed",
        at = @At("HEAD"),
        cancellable = true,
        remap = false
    )
    private void onSubKeyPressed(UIContext context, CallbackInfoReturnable<Boolean> cir)
    {
        Object self = this;

        if (!(self instanceof UIPromptOverlayPanel panel))
        {
            return;
        }

        if (panel.text != null && panel.text.isFocused() && context.isPressed(GLFW.GLFW_KEY_ENTER))
        {
            panel.confirm();
            cir.setReturnValue(true);
        }
    }
}
