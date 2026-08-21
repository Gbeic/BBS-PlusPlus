package gbeic.bbsplusplus.client.ui.utils;

import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.l10n.L10n;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.film.UIFilmPanel;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIIcon;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.Direction;

/**
 * 为录像编辑器添加循环模式图标。
 * <p>
 * BBS 2.2 原版在录像编辑器的右下角强制绘制一个刷新图标，点击后会重置当前时间轴位置。此 Mixin 将取消原版的绘制逻辑，并在顶部工具栏添加一个循环模式图标，点击后切换循环模式开关，并显示提示信息。
 * </p>
 */

public class UILoopIconUtils {
    public static void addLoopIcon(UIFilmPanel panel, UIIcon openCameraEditor) {
        int buttonSize = 20;

        UILoopIcon loopIcon = new UILoopIcon(Icons.REFRESH, (b) -> {
            BBSSettings.editorLoop.set(!BBSSettings.editorLoop.get());
            panel.getContext().notifyInfo(UIKeys.CAMERA_EDITOR_KEYS_LOOPING_TOGGLE_NOTIFICATION);
        });

        loopIcon.wh(buttonSize, buttonSize).tooltip(L10n.lang("bbs.ui.film.looping_enabled"), Direction.BOTTOM);
        loopIcon.relative(openCameraEditor).x(-buttonSize).y(0);

        panel.tabBar.add(loopIcon);
    }
}
