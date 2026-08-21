package gbeic.bbsplusplus.mixin.shadercurves;

import gbeic.bbsplusplus.client.compat.iris.ShaderCurveState;
import mchorse.bbs_mod.BBSSettings;
import net.irisshaders.iris.shadows.ShadowRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * 让阴影贴图的投影方向跟随日月偏角曲线。
 * <p>
 * 移植自 BBSTools 4.1。如果只改了天体位置而不改阴影矩阵，
 * 太阳动了但地面阴影不动，画面会明显穿帮。
 * </p>
 */
@Mixin(value = ShadowRenderer.class, remap = false)
public class ShadowRendererMixin
{
    /**
     * 注入目标：{@code ShadowRenderer#createShadowModelView} 的 {@code sunPathRotation} 参数。
     * 注入原因：阴影相机的朝向由这个参数决定，必须和天体位置用同一个角度。
     * 修改行为：光影曲线总开关打开时把传入值替换为曲线当前值。
     */
    @ModifyVariable(method = "createShadowModelView", at = @At("HEAD"), ordinal = 0, argsOnly = true, remap = false)
    private static float bbspp$modifySunPathRotation(float original)
    {
        if (!BBSSettings.shaderCurvesEnabled.get())
        {
            return original;
        }

        return ShaderCurveState.getSunPathRotation(original);
    }
}
