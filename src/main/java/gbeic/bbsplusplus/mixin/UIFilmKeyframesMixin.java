package gbeic.bbsplusplus.mixin;

import gbeic.bbsplusplus.client.renderer.VideoTimelineState;
import mchorse.bbs_mod.ui.film.UIClips;
import mchorse.bbs_mod.ui.film.utils.keyframes.UIFilmKeyframes;
import mchorse.bbs_mod.ui.framework.UIContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import mchorse.bbs_mod.ui.utils.Area;

/**
 * 修复回放编辑器界面（UIFilmKeyframes）中时间指针偏左未居中的问题。
 */
@Mixin(value = UIFilmKeyframes.class, remap = false)
public class UIFilmKeyframesMixin
{
    /**
     * 注入目标：{@link UIFilmKeyframes#moveNoKeyframes(UIContext)} 开始处。
     * 注入原因：轨道编辑器在空白区域拖动播放头时使用独立入口，不会经过顶部时间标尺的拖动状态。
     * 修改行为：首次实际移动播放头时通知视频渲染器冻结当前画面，直到鼠标释放。
     */
    @Inject(method = "moveNoKeyframes", at = @At("HEAD"))
    private void bbspp$beginTrackEditorScrubbing(UIContext context, CallbackInfo ci)
    {
        VideoTimelineState.beginScrubbing(this);
    }

    @Redirect(method = "renderOverlay", at = @At(value = "INVOKE", target = "Lmchorse/bbs_mod/ui/film/UIClips;renderCursor(Lmchorse/bbs_mod/ui/framework/UIContext;Ljava/lang/String;Lmchorse/bbs_mod/ui/utils/Area;I)V"))
    private void bbspp$fixCursorOffset(UIContext context, String label, Area area, int originalX)
    {
        // 通过矩阵平移 0.5 像素，实现真正的完美居中
        context.batcher.getContext().getMatrices().push();
        context.batcher.getContext().getMatrices().translate(0.5F, 0.0F, 0.0F);
        
        UIClips.renderCursor(context, label, area, originalX);
        
        context.batcher.getContext().getMatrices().pop();
    }
}
