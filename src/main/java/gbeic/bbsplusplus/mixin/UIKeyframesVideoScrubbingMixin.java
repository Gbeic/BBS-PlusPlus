package gbeic.bbsplusplus.mixin;

import gbeic.bbsplusplus.client.renderer.VideoTimelineState;
import mchorse.bbs_mod.ui.film.utils.keyframes.UIFilmKeyframes;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 补齐影片轨道编辑器播放头拖动结束时的视频状态清理。
 *
 * {@link UIFilmKeyframes} 继承父类的鼠标释放实现，因此在父类统一识别影片轨道实例，
 * 只清理由轨道空白区域播放头拖动建立的冻结状态，不影响普通关键帧编辑器。
 */
@Mixin(value = UIKeyframes.class, remap = false)
public class UIKeyframesVideoScrubbingMixin
{
    /**
     * 注入目标：{@link UIKeyframes#subMouseReleased(UIContext)} 返回处。
     * 注入原因：轨道播放头拖动结束后必须解除视频冻结，才能在下一次画面渲染时寻到最终帧。
     * 修改行为：仅当当前实例属于影片轨道编辑器时，移除对应的拖动来源。
     */
    @Inject(method = "subMouseReleased", at = @At("RETURN"))
    private void bbspp$endTrackEditorScrubbing(UIContext context, CallbackInfoReturnable<Boolean> cir)
    {
        if ((Object) this instanceof UIFilmKeyframes)
        {
            VideoTimelineState.endScrubbing(this);
        }
    }
}
