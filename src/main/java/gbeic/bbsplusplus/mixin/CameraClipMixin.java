package gbeic.bbsplusplus.mixin;

import mchorse.bbs_mod.camera.clips.CameraClip;
import mchorse.bbs_mod.camera.data.Position;
import mchorse.bbs_mod.utils.clips.Clip;
import mchorse.bbs_mod.utils.clips.ClipContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 修复禁用镜头剪辑在暂停边界仍然生效的问题。
 * <p>
 * 原版暂停在正在编辑的镜头剪辑末尾时，会通过 {@code CameraClip#applyLast(ClipContext<?, ?>, Position)}
 * 额外采样该剪辑的最后一帧，用于编辑右边界关键帧。
 * 这条路径没有检查剪辑的启用状态，导致禁用剪辑在暂停时仍会改变镜头。
 * </p>
 */
@Mixin(value = CameraClip.class, remap = false)
public abstract class CameraClipMixin
{
    /**
     * 注入目标：{@code CameraClip#applyLast(ClipContext<?, ?>, Position)} 入口。
     * 注入原因：暂停边界预览路径绕过了 {@code CameraClip#apply(ClipContext<?, ?>, Position)} 中的启用检查。
     * 修改行为：剪辑被禁用时直接取消末帧采样，避免暂停时产生镜头偏移。
     */
    @Inject(method = "applyLast", at = @At("HEAD"), cancellable = true)
    private void bbspp$skipDisabledLastFrame(ClipContext<?, ?> context, Position position, CallbackInfo ci)
    {
        Clip clip = (Clip) (Object) this;

        if (!clip.enabled.get())
        {
            ci.cancel();
        }
    }
}
