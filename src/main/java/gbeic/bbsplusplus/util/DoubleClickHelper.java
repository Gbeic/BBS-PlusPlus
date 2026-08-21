package gbeic.bbsplusplus.util;

import mchorse.bbs_mod.ui.film.clips.UIClip;
import mchorse.bbs_mod.ui.film.clips.UIKeyframeClip;
import mchorse.bbs_mod.ui.film.clips.UIRemapperClip;
import mchorse.bbs_mod.ui.film.clips.UICurveClip;

/**
 * 双击剪辑辅助工具 — 直接调用剪辑编辑面板的 "编辑" 按钮。
 * <p>
 * 代替 {@link DblClickHandler} 接口链，避免 mixin {@code @Implements}
 * 的兼容问题。
 * </p>
 */
public class DoubleClickHelper
{
    /**
     * 尝试触发剪辑编辑面板的编辑按钮。
     *
     * @param clipPanel 当前打开的剪辑编辑面板
     * @return 如果成功触发了编辑则返回 {@code true}
     */
    public static boolean triggerEdit(UIClip<?> clipPanel)
    {
        if (clipPanel instanceof UIKeyframeClip)
        {
            ((UIKeyframeClip) clipPanel).edit.clickItself();
            return true;
        }

        if (clipPanel instanceof UIRemapperClip)
        {
            ((UIRemapperClip) clipPanel).edit.clickItself();
            return true;
        }

        if (clipPanel instanceof UICurveClip)
        {
            ((UICurveClip) clipPanel).edit.clickItself();
            return true;
        }

        return false;
    }
}
