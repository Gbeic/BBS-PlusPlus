package gbeic.bbsplusplus.mixin.shadercurves;

import gbeic.bbsplusplus.client.compat.iris.ShaderCurveState;
import mchorse.bbs_mod.BBSSettings;
import net.irisshaders.iris.uniforms.CelestialUniforms;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 让 Iris 计算日月位置时改用曲线剪辑里的日月偏角。
 * <p>
 * 移植自 BBSTools 4.1。{@code sunPathRotation} 原本只能在光影包设置里手动填一个固定角度，
 * 接上曲线后就能在影片里做出太阳轨道随时间变化的效果。
 * </p>
 */
@Mixin(value = CelestialUniforms.class, remap = false)
public class CelestialUniformsMixin
{
    @Shadow(remap = false)
    @Final
    private float sunPathRotation;

    /**
     * 注入目标：{@code CelestialUniforms#getCelestialPositionInWorldSpace} 里对 {@code sunPathRotation} 字段的读取。
     * 注入原因：这里算的是世界空间下的太阳/月亮位置，光影用它来定阴影方向。
     * 修改行为：光影曲线总开关打开时，改用曲线当前值；否则保持光影包里配置的原值。
     */
    @Redirect(
        method = "getCelestialPositionInWorldSpace",
        at = @At(value = "FIELD", target = "Lnet/irisshaders/iris/uniforms/CelestialUniforms;sunPathRotation:F", opcode = org.objectweb.asm.Opcodes.GETFIELD),
        remap = false
    )
    private float bbspp$redirectSunPathRotationWorld(CelestialUniforms instance)
    {
        return this.bbspp$resolveSunPathRotation();
    }

    /**
     * 注入目标：{@code CelestialUniforms#getCelestialPosition} 里对 {@code sunPathRotation} 字段的读取。
     * 注入原因：这里算的是屏幕空间下的太阳/月亮位置，光影用它来画天体和算光照方向。
     * 修改行为：同上，光影曲线总开关打开时改用曲线当前值。
     */
    @Redirect(
        method = "getCelestialPosition",
        at = @At(value = "FIELD", target = "Lnet/irisshaders/iris/uniforms/CelestialUniforms;sunPathRotation:F", opcode = org.objectweb.asm.Opcodes.GETFIELD),
        remap = false
    )
    private float bbspp$redirectSunPathRotation(CelestialUniforms instance)
    {
        return this.bbspp$resolveSunPathRotation();
    }

    @org.spongepowered.asm.mixin.Unique
    private float bbspp$resolveSunPathRotation()
    {
        if (!BBSSettings.shaderCurvesEnabled.get())
        {
            return this.sunPathRotation;
        }

        return ShaderCurveState.getSunPathRotation(this.sunPathRotation);
    }
}
