package gbeic.bbsplusplus.mixin;

import gbeic.bbsplusplus.client.WorldFilmShaderCurveState;
import mchorse.bbs_mod.camera.clips.misc.CurveClip;
import mchorse.bbs_mod.client.BBSRendering;
import mchorse.bbs_mod.utils.iris.ShaderCurves;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 让世界内影片播放也能向 BBSRendering 提供曲线剪辑里的光影参数。
 * <p>
 * 原版只从当前相机控制器读取曲线上下文；第一人称世界播放没有该上下文，所以太阳、天气、亮度、
 * 色度天空颜色以及 Iris 光影包选项曲线都不会生效。本 Mixin 在原版读取失败时补读 BBS++ 的世界播放采样结果。
 * </p>
 */
@Mixin(BBSRendering.class)
public class BBSRenderingMixin
{
    /**
     * 注入目标：{@code BBSRendering#getTimeOfDay()} 返回处。
     * 注入原因：世界播放第一人称影片时，原版通常返回 null，导致太阳旋转曲线不生效。
     * 修改行为：原版没有值时，从世界影片曲线状态读取太阳旋转并转换为 Minecraft 时间。
     */
    @Inject(method = "getTimeOfDay", at = @At("RETURN"), cancellable = true, remap = false)
    private static void bbspp$getWorldFilmTimeOfDay(CallbackInfoReturnable<Long> cir)
    {
        if (cir.getReturnValue() != null)
        {
            return;
        }

        Double value = WorldFilmShaderCurveState.getValue(ShaderCurves.SUN_ROTATION);

        if (value != null)
        {
            cir.setReturnValue((long) (value * 1000L));
        }
    }

    /**
     * 注入目标：{@code BBSRendering#getBrightness()} 返回处。
     * 注入原因：世界播放路径缺少相机控制器上下文，原版亮度曲线读取不到数据。
     * 修改行为：原版没有值时，补用世界影片采样到的亮度曲线值。
     */
    @Inject(method = "getBrightness", at = @At("RETURN"), cancellable = true, remap = false)
    private static void bbspp$getWorldFilmBrightness(CallbackInfoReturnable<Double> cir)
    {
        if (cir.getReturnValue() == null)
        {
            cir.setReturnValue(WorldFilmShaderCurveState.getValue(ShaderCurves.BRIGHTNESS));
        }
    }

    /**
     * 注入目标：{@code BBSRendering#getWeather()} 返回处。
     * 注入原因：世界播放路径缺少相机控制器上下文，原版天气曲线读取不到数据。
     * 修改行为：原版没有值时，补用世界影片采样到的天气曲线值。
     */
    @Inject(method = "getWeather", at = @At("RETURN"), cancellable = true, remap = false)
    private static void bbspp$getWorldFilmWeather(CallbackInfoReturnable<Double> cir)
    {
        if (cir.getReturnValue() == null)
        {
            cir.setReturnValue(WorldFilmShaderCurveState.getValue(ShaderCurves.WEATHER));
        }
    }

    /**
     * 注入目标：{@code BBSRendering#getChromaSkyColorArgb()} 返回处。
     * 注入原因：世界播放路径缺少相机控制器上下文，原版色度天空颜色曲线读取不到数据。
     * 修改行为：原版没有值时，补用世界影片采样到的颜色曲线值。
     */
    @Inject(method = "getChromaSkyColorArgb", at = @At("RETURN"), cancellable = true, remap = false)
    private static void bbspp$getWorldFilmChromaSkyColor(CallbackInfoReturnable<Integer> cir)
    {
        if (cir.getReturnValue() == null)
        {
            cir.setReturnValue(WorldFilmShaderCurveState.getColorValue(CurveClip.CHROMA_SKY_COLOR));
        }
    }
}
