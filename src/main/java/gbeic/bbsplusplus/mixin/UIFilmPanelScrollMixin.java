package gbeic.bbsplusplus.mixin;

import gbeic.bbsplusplus.BBSAddonsSettings;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 拦截影片编辑器中的鼠标滚轮控制时间线的逻辑，实现时间线反向滚动功能。
 * 目标类为 UIFilmPanel 中的匿名内部类 UIElement（负责拦截全局滚动），通常为 UIFilmPanel$1 等。
 */
@Mixin(targets = {
    "mchorse.bbs_mod.ui.film.UIFilmPanel$1"
})
public class UIFilmPanelScrollMixin {

    /**
     * 重定向影片时间线滚轮步进方向，在 BBS++ 设置启用时反转时间线滚动。
     */
    @Redirect(
        method = "subMouseScrolled",
        at = @At(value = "INVOKE", target = "Ljava/lang/Math;copySign(DD)D"),
        require = 0,
        remap = false
    )
    private double redirectCopySign(double magnitude, double sign) {
        if (BBSAddonsSettings.reverseTimelineScroll != null && BBSAddonsSettings.reverseTimelineScroll.get()) {
            return Math.copySign(magnitude, -sign);
        }
        return Math.copySign(magnitude, sign);
    }
}
