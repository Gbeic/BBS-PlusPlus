package gbeic.bbsplusplus.mixin;

import gbeic.bbsplusplus.client.WorldFilmShaderCurveState;
import mchorse.bbs_mod.film.Films;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 清理世界影片光影曲线的帧状态。
 * <p>
 * 世界内影片可能随时停止播放；如果不在每帧采样前清空状态，BBSRendering 可能继续读到上一帧的旧曲线值。
 * </p>
 */
@Mixin(Films.class)
public class FilmsMixin
{
    /**
     * 注入目标：{@code Films#startRenderFrame(float)} 入口。
     * 注入原因：该方法会逐个通知当前播放的影片控制器准备渲染帧，是清理上一帧临时曲线数据的稳定入口。
     * 修改行为：先清空 BBS++ 保存的世界影片光影曲线状态，后续仍由各影片控制器重新采样当前帧。
     */
    @Inject(method = "startRenderFrame", at = @At("HEAD"), remap = false)
    private void bbspp$clearWorldFilmShaderCurves(float transition, CallbackInfo ci)
    {
        WorldFilmShaderCurveState.clear();
    }
}
