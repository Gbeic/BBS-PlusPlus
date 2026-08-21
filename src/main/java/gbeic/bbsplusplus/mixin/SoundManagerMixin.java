package gbeic.bbsplusplus.mixin;

import mchorse.bbs_mod.audio.SoundBuffer;
import mchorse.bbs_mod.audio.SoundManager;
import mchorse.bbs_mod.audio.SoundPlayer;
import mchorse.bbs_mod.resources.Link;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 音频管理器容错补丁。
 * <p>
 * 影片预览渲染波形或回放音频时，空音频链接会一路传到 {@code AudioReader} 并在渲染线程打印异常栈。
 * 本补丁在 {@link SoundManager} 入口处直接跳过空链接，避免进入影片回放时因为无效音频轨道反复同步报错。
 * </p>
 */
@Mixin(SoundManager.class)
public abstract class SoundManagerMixin
{
    /**
     * 注入目标：{@code SoundManager#get(Link, boolean)} 入口。
     * 注入原因：音频轨道可能存在空链接，原逻辑会继续加载并触发空指针异常。
     * 修改行为：空链接直接视为没有可播放音频。
     */
    @Inject(method = "get", at = @At("HEAD"), cancellable = true, remap = false)
    private void bbspp$skipNullSoundBuffer(Link link, boolean includeWaveform, CallbackInfoReturnable<SoundBuffer> cir)
    {
        if (link == null)
        {
            cir.setReturnValue(null);
        }
    }

    /**
     * 注入目标：{@code SoundManager#playUnique(Link)} 入口。
     * 注入原因：回放启动会通过唯一音频播放器加载音频，空链接无需继续创建播放器。
     * 修改行为：空链接直接返回空播放器。
     */
    @Inject(method = "playUnique", at = @At("HEAD"), cancellable = true, remap = false)
    private void bbspp$skipNullUniqueSound(Link link, CallbackInfoReturnable<SoundPlayer> cir)
    {
        if (link == null)
        {
            cir.setReturnValue(null);
        }
    }
}
