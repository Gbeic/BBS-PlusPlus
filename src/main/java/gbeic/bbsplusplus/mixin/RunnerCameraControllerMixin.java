package gbeic.bbsplusplus.mixin;

import mchorse.bbs_mod.camera.Camera;
import mchorse.bbs_mod.camera.controller.CameraWorkCameraController;
import mchorse.bbs_mod.camera.controller.RunnerCameraController;
import mchorse.bbs_mod.camera.data.Position;
import mchorse.bbs_mod.utils.clips.Clip;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin — 修复飞行模式下曲线剪辑不生效的问题。
 * <p>
 * bbs-fs 的 {@link RunnerCameraController#setup} 中，当 {@code manual} 不为 null
 * （飞行模式）时直接跳过了 {@code apply()}，导致曲线剪辑的 {@code applyClip()} 不会执行。
 * 此处通过 TAIL 注入手动补齐剪辑处理。
 * </p>
 */
@Mixin(RunnerCameraController.class)
public abstract class RunnerCameraControllerMixin extends CameraWorkCameraController
{
    @Shadow(remap = false)
    private Position manual;

    @Shadow(remap = false)
    public int ticks;

    @Inject(
        method = "setup",
        at = @At("TAIL"),
        remap = false
    )
    private void onSetupTail(Camera camera, float transition, CallbackInfo ci)
    {
        /* 飞行模式 (manual != null) 时原版跳过了 apply()，此处补齐剪辑处理 */
        if (this.manual != null && this.context.clips != null)
        {
            this.context.clipData.clear();
            this.context.setup(this.ticks, this.context.playing ? transition : 0F);

            for (Clip clip : this.context.clips.getClips(this.ticks))
            {
                this.context.apply(clip, this.position);
            }

            this.context.currentLayer = 0;
        }
    }
}
