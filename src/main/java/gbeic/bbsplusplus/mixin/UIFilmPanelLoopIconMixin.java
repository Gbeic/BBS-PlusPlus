package gbeic.bbsplusplus.mixin;

import mchorse.bbs_mod.ui.dashboard.UIDashboard;
import mchorse.bbs_mod.ui.film.UIFilmPanel;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIIcon;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 为录像编辑器添加循环模式图标。
 * <p>
 * BBS 2.2 原版在录像编辑器的右下角强制绘制一个刷新图标，点击后会重置当前时间轴位置。此 Mixin 将取消原版的绘制逻辑，并在顶部工具栏添加一个循环模式图标，点击后切换循环模式开关，并显示提示信息。
 * </p>
 */

@Mixin(UIFilmPanel.class)
public class UIFilmPanelLoopIconMixin {

    @Shadow(remap = false)
    public UIIcon openCameraEditor;

    @Inject(method = "<init>(Lmchorse/bbs_mod/ui/dashboard/UIDashboard;)V", at = @At("RETURN"), remap = false)
    private void bbsplusplus$addLoopIcon(UIDashboard dashboard, CallbackInfo ci) {
        UIFilmPanel self = (UIFilmPanel) (Object) this;

        gbeic.bbsplusplus.client.ui.utils.UILoopIconUtils.addLoopIcon(self, this.openCameraEditor);
    }
}
