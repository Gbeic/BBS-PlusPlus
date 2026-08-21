package gbeic.bbsplusplus.mixin;

import gbeic.bbsplusplus.BBSAddonsSettings;
import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.graphics.window.Window;
import mchorse.bbs_mod.ui.film.UIClipsPanel;
import mchorse.bbs_mod.ui.film.UIFilmPanel;
import mchorse.bbs_mod.ui.film.replays.UIReplaysEditor;
import mchorse.bbs_mod.ui.forms.editors.states.keyframes.UIAnimationStateKeyframes;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframes;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.graphs.UIKeyframeDopeSheet;
import mchorse.bbs_mod.utils.keyframes.Keyframe;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 控制编辑器中关键帧摄影表的 Alt+滚轮轨道高度调整功能。
 * <p>
 * 原版在没有选中关键帧时，会把 Alt+鼠标滚轮解释为调整轨道高度。
 * 该 Mixin 通过 BBS++ 三挡模式拦截这一分支，让用户可以禁用该操作，
 * 或将它改成类似 Final Cut Pro X 的左右滚动时间线。影片编辑器和模型方块的动画状态编辑器
 * 都会应用该设置；若当前已选中关键帧，则始终保留原版 Alt+滚轮移动选中关键帧的行为。
 * </p>
 */
@Mixin(UIKeyframeDopeSheet.class)
public abstract class UIKeyframeDopeSheetTrackHeightScrollMixin
{
    @Shadow(remap = false)
    private UIKeyframes keyframes;

    @Shadow(remap = false)
    public abstract Keyframe<?> getSelected();

    /**
     * 注入目标：摄影表滚轮处理入口。
     * 注入原因：轨道高度调整与选中关键帧移动共用 Alt+滚轮入口，只能在执行前按上下文区分。
     * 修改行为：处于受支持编辑器且没有选中关键帧时，根据 BBS++ 模式禁用或改为左右滚动时间线。
     */
    @Inject(method = "mouseScrolled", at = @At("HEAD"), cancellable = true, remap = false)
    private void bbspp$disableAltScrollTrackHeight(UIContext context, CallbackInfo ci)
    {
        if (!Window.isAltPressed()
            || context.mouseWheel == 0D
            || this.getSelected() != null
            || !this.bbspp$shouldApplyAltWheelMode())
        {
            return;
        }

        BBSAddonsSettings.AltWheelTimelineMode mode = BBSAddonsSettings.getFilmAltWheelTimelineMode();

        if (mode == BBSAddonsSettings.AltWheelTimelineMode.DISABLED)
        {
            ci.cancel();
        }
        else if (mode == BBSAddonsSettings.AltWheelTimelineMode.HORIZONTAL_SCROLL)
        {
            this.bbspp$scrollTimelineHorizontally(context);
            ci.cancel();
        }
    }

    private void bbspp$scrollTimelineHorizontally(UIContext context)
    {
        double offsetX = this.bbspp$getAltWheelHorizontalOffset(context);

        this.keyframes.getXAxis().setShift(this.keyframes.getXAxis().getShift() + offsetX);
    }

    private double bbspp$getAltWheelHorizontalOffset(UIContext context)
    {
        double offsetX = (25F * BBSSettings.scrollingSensitivityHorizontal.get() * context.mouseWheel) / this.keyframes.getXAxis().getZoom();

        return BBSAddonsSettings.reverseTimelineScroll != null && BBSAddonsSettings.reverseTimelineScroll.get()
            ? -offsetX
            : offsetX;
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
}
