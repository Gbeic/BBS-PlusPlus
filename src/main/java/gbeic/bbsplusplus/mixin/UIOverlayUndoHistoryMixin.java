package gbeic.bbsplusplus.mixin;

import mchorse.bbs_mod.ui.film.utils.undo.UIUndoHistoryOverlay;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlay;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlayPanel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 放大新版撤销历史弹窗，给可读化后的操作摘要留出足够宽度。
 * <p>
 * BBS FS 2.4 把历史窗口整理成通用 {@link UIUndoHistoryOverlay}，影片编辑器和模型编辑器
 * 都会以 200 像素默认宽度打开它。这个宽度适合原始短路径，但不适合 BBS++ 的可读摘要。
 * 本 Mixin 只替换撤销历史面板的默认尺寸，不影响播放器设置、移动场景等其它同类弹窗。
 * </p>
 */
@Mixin(UIOverlay.class)
public class UIOverlayUndoHistoryMixin
{
    private static final int UNDO_HISTORY_WIDTH = 520;
    private static final float UNDO_HISTORY_HEIGHT = 0.6F;

    /**
     * 注入目标：{@link UIOverlay#addOverlay(UIContext, UIOverlayPanel, int, float)} 开头。
     * 注入原因：默认撤销历史窗口宽度只有 200 像素，无法完整展示 BBS++ 的可读历史摘要。
     * 修改行为：当面板是撤销历史且仍使用原版默认尺寸时，改用更宽更高的弹窗尺寸。
     */
    @Inject(
        method = "addOverlay(Lmchorse/bbs_mod/ui/framework/UIContext;Lmchorse/bbs_mod/ui/framework/elements/overlay/UIOverlayPanel;IF)Lmchorse/bbs_mod/ui/framework/elements/overlay/UIOverlay;",
        at = @At("HEAD"),
        cancellable = true,
        remap = false
    )
    private static void bbspp$resizeUndoHistory(UIContext context, UIOverlayPanel panel, int w, float h, CallbackInfoReturnable<UIOverlay> cir)
    {
        if (panel instanceof UIUndoHistoryOverlay && w == 200 && h == 0.6F)
        {
            cir.setReturnValue(UIOverlay.addOverlay(context, panel, UNDO_HISTORY_WIDTH, UNDO_HISTORY_HEIGHT));
        }
    }
}
