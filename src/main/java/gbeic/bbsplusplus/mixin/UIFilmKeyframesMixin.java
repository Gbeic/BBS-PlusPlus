package gbeic.bbsplusplus.mixin;

import mchorse.bbs_mod.ui.film.UIClips;
import mchorse.bbs_mod.ui.film.utils.keyframes.UIFilmKeyframes;
import mchorse.bbs_mod.ui.framework.UIContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import mchorse.bbs_mod.ui.utils.Area;

/**
 * 修复回放编辑器界面（UIFilmKeyframes）中时间指针偏左未居中的问题。
 */
@Mixin(value = UIFilmKeyframes.class, remap = false)
public class UIFilmKeyframesMixin
{
    @Redirect(method = "renderOverlay", at = @At(value = "INVOKE", target = "Lmchorse/bbs_mod/ui/film/UIClips;renderCursor(Lmchorse/bbs_mod/ui/framework/UIContext;Ljava/lang/String;Lmchorse/bbs_mod/ui/utils/Area;I)V"))
    private void bbspp$fixCursorOffset(UIContext context, String label, Area area, int originalX)
    {
        // 通过矩阵平移 0.5 像素，实现真正的完美居中
        context.batcher.getContext().pose().pushPose();
        context.batcher.getContext().pose().translate(0.5F, 0.0F, 0.0F);

        UIClips.renderCursor(context, label, area, originalX);

        context.batcher.getContext().pose().popPose();
    }
}
