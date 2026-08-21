package gbeic.bbsplusplus.mixin;

import gbeic.bbsplusplus.ui.list.UIAudioTreeList;

import mchorse.bbs_mod.ui.framework.elements.overlay.UIStringOverlayPanel;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Mixin — 为 {@link UIStringOverlayPanel#set(String)} 注入树展开行为。
 */
@Mixin(UIStringOverlayPanel.class)
public class UIStringOverlayPanelMixin
{
    @Inject(
        method = "set(Ljava/lang/String;)Lmchorse/bbs_mod/ui/framework/elements/overlay/UIStringOverlayPanel;",
        at = @At("HEAD"),
        remap = false
    )
    private void onSet(String string, CallbackInfoReturnable<UIStringOverlayPanel> cir)
    {
        UIStringOverlayPanel self = (UIStringOverlayPanel) (Object) this;

        if (string != null && !string.isEmpty()
            && self.strings.list instanceof UIAudioTreeList treeList)
        {
            treeList.expandToShow(string);
        }
    }
}
