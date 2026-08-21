package gbeic.bbsplusplus.client.ui.utils;

import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIIcon;
import mchorse.bbs_mod.ui.utils.icons.Icon;
import java.util.function.Consumer;

/**
 * 循环模式图标。
 * <p>
 * 这是一个自定义的 UIIcon，用于在录像编辑器中显示循环模式状态。当循环模式开启时，图标会被绘制出来；当循环模式关闭时，图标不会被绘制。点击图标会切换循环模式的开关状态。
 * </p>
 */

public class UILoopIcon extends UIIcon {

    public UILoopIcon(Icon icon, Consumer<UIIcon> callback) {
        super(icon, callback);
    }

    @Override
    public void render(UIContext context) {
        if (BBSSettings.editorLoop.get()) {
            super.render(context);
        }
    }

    @Override
    public boolean isVisible() {
        return super.isVisible() && BBSSettings.editorLoop.get();
    }
}
