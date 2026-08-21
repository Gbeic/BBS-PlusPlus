package gbeic.bbsplusplus.mixin;

import mchorse.bbs_mod.ui.film.UIFilmPanel;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframes;
import mchorse.bbs_mod.ui.framework.elements.utils.UIDraggable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 在布局锁定后隐藏 {@link UIKeyframes} 中的标签列宽度调节柄
 * （左侧轨道名称与右侧曲线图之间的竖线）。
 * <p>
 * 原版只在 {@code resize()} 中判断 {@code currentGraph == dopeSheet} 来控制可见性，
 * 不检查布局锁定状态。此 Mixin 在 {@code resize()} 返回时沿父级链查找
 * {@link UIFilmPanel}，若布局已锁定则隐藏该调节柄。
 * </p>
 */
@Mixin(UIKeyframes.class)
public class UIKeyframesLayoutLockMixin
{
    @Shadow(remap = false)
    private UIDraggable labelResizer;

    @Inject(method = "resize", at = @At("RETURN"), remap = false)
    private void onResizeReturn(CallbackInfo ci)
    {
        if (this.labelResizer == null) return;
        if (!gbeic.bbsplusplus.BBSAddonsSettings.enableUiKeyframesLayoutLock.get()) return;

        UIElement p = ((UIElement) (Object) this).getParent();

        while (p != null)
        {
            if (p instanceof UIFilmPanel)
            {
                if (((UIFilmPanel) p).isLayoutLocked())
                {
                    this.labelResizer.setVisible(false);
                }

                return;
            }

            p = p.getParent();
        }
    }
}
