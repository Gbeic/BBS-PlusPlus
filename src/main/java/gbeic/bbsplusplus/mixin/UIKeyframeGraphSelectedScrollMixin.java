package gbeic.bbsplusplus.mixin;

import gbeic.bbsplusplus.BBSAddonsSettings;
import mchorse.bbs_mod.ui.film.UIClipsPanel;
import mchorse.bbs_mod.ui.film.UIFilmPanel;
import mchorse.bbs_mod.ui.film.replays.UIReplaysEditor;
import mchorse.bbs_mod.ui.forms.editors.states.keyframes.UIAnimationStateKeyframes;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframes;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.graphs.UIKeyframeGraph;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * 让关键帧曲线图中选中关键帧的 Alt+滚轮移动方向跟随 BBS++ 的反转时间线滚轮方向设置。
 * <p>
 * 摄影表和曲线图各自实现滚轮处理；摄影表还承担轨道高度调整逻辑，曲线图只需要修正
 * 选中关键帧的移动增量。这里按所属编辑器做上下文判断，避免把普通曲线面板的操作一并改掉。
 * </p>
 */
@Mixin(UIKeyframeGraph.class)
public abstract class UIKeyframeGraphSelectedScrollMixin
{
    @Shadow(remap = false)
    private UIKeyframes keyframes;

    /**
     * 修改目标：曲线图选中关键帧时由滚轮计算出的移动增量。
     * 注入原因：原版曲线图选中态 Alt+滚轮移动不经过影片编辑器全局 Ctrl+滚轮入口，
     * 因此不会自动应用 BBS++ 的反转时间线滚轮方向设置。
     * 修改行为：仅在受支持编辑器中启用反转设置时翻转移动增量，其余情况保持原版方向。
     */
    @ModifyVariable(
        method = "mouseScrolled",
        at = @At("STORE"),
        ordinal = 0,
        remap = false
    )
    private float bbspp$reverseSelectedKeyframeAltWheel(float diff)
    {
        return this.bbspp$shouldReverseTimelineScroll() && this.bbspp$shouldApplyAltWheelMode() ? -diff : diff;
    }

    private boolean bbspp$shouldApplyAltWheelMode()
    {
        return this.keyframes instanceof UIAnimationStateKeyframes || this.bbspp$isInFilmEditorKeyframes();
    }

    private boolean bbspp$isInFilmEditorKeyframes()
    {
        UIElement parent = this.keyframes;

        while (parent != null)
        {
            if (parent instanceof UIFilmPanel
                || parent instanceof UIClipsPanel
                || parent instanceof UIReplaysEditor)
            {
                return true;
            }

            parent = parent.getParent();
        }

        return false;
    }

    private boolean bbspp$shouldReverseTimelineScroll()
    {
        return BBSAddonsSettings.reverseTimelineScroll != null && BBSAddonsSettings.reverseTimelineScroll.get();
    }
}
