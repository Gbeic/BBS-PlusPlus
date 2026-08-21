package gbeic.bbsplusplus.mixin;

import mchorse.bbs_mod.ui.film.UIClipsPanel;
import mchorse.bbs_mod.ui.film.replays.UIReplaysEditor;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframes;
import mchorse.bbs_mod.ui.utils.Area;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = UIKeyframes.class, remap = false)
public class UIKeyframesBoxSelectionMixin
{
    @Inject(method = "getGrabbingArea", at = @At("RETURN"), cancellable = true)
    private void bbspp$clipBoxSelection(UIContext context, CallbackInfoReturnable<Area> cir)
    {
        Area area = cir.getReturnValue();
        UIKeyframes self = (UIKeyframes) (Object) this;
        
        UIElement parent = self.getParent();
        boolean hasHeader = false;
        
        // 向上层遍历寻找是否处于拥有 20px 顶部控制栏的编辑器中（如 UIReplaysEditor, UIClipsPanel）
        while (parent != null)
        {
            if (parent instanceof UIReplaysEditor || parent instanceof UIClipsPanel)
            {
                hasHeader = true;
                break;
            }
            parent = parent.getParent();
        }
        
        if (hasHeader)
        {
            // 如果存在顶部控制栏，则限制框选区域的 y 坐标不得进入顶部的 20 像素内，
            // 避免长按拖拽框选时误选中那些被遮挡在时间轴或工具栏下方的关键帧。
            int headerBottom = parent.area.y + 20;
            if (area.y < headerBottom)
            {
                int diff = headerBottom - area.y;
                area.y = headerBottom;
                area.h = Math.max(0, area.h - diff);
            }
        }
    }
}
