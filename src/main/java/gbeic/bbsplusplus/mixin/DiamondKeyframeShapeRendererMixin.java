package gbeic.bbsplusplus.mixin;

import mchorse.bbs_mod.ui.framework.elements.input.keyframes.shapes.DiamondKeyframeShapeRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

/**
 * 将菱形关键帧渲染尺寸恢复为 BBS++ 适配的旧尺寸。
 * <p>
 * BBSFS 2.4 的 99b1118 把菱形关键帧缩放从 1.3F 改为 1.5F，
 * 会让关键帧视觉中心与时间指针对齐产生偏差。这里只修改该渲染常量，
 * 不影响其它关键帧形状。
 * </p>
 */
@Mixin(value = DiamondKeyframeShapeRenderer.class, remap = false)
public class DiamondKeyframeShapeRendererMixin
{
    /**
     * 注入目标：DiamondKeyframeShapeRenderer#renderKeyframe(...) 中计算 fOffset 的常量 1.5F。
     * 注入原因：新版 1.5F 菱形尺寸会让关键帧和时间指针对齐出现偏差。
     * 修改行为：恢复为旧版 1.3F。
     */
    @ModifyConstant(
        method = "renderKeyframe",
        constant = @Constant(floatValue = 1.5F)
    )
    private float bbsplusplus$restoreDiamondKeyframeSize(float value)
    {
        return 1.3F;
    }
}
