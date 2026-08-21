package gbeic.bbsplusplus.mixin;

import mchorse.bbs_mod.audio.wav.WaveReader;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.io.EOFException;

/**
 * WAV 读取器日志降噪补丁。
 * <p>
 * {@link WaveReader} 在循环读取 RIFF 子块时，用 {@link EOFException} 作为正常结束信号，
 * 但原实现会把这个正常结束打印成完整异常栈。影片回放首次加载多个 WAV 时，这些栈会在渲染线程同步输出，
 * 造成明显卡顿和误导性的错误日志。
 * </p>
 */
@Mixin(WaveReader.class)
public abstract class WaveReaderMixin
{
    /**
     * 注入目标：{@code WaveReader#read(InputStream)} 内部捕获 EOF 后的 {@code printStackTrace()}。
     * 注入原因：读到 WAV 文件末尾是循环结束条件，不是需要展示给用户的异常。
     * 修改行为：吞掉这个正常 EOF 的栈输出，保留其它真实异常的原有处理。
     */
    @Redirect(
        method = "read",
        at = @At(value = "INVOKE", target = "Ljava/io/EOFException;printStackTrace()V"),
        remap = false
    )
    private void bbspp$ignoreExpectedEndOfWave(EOFException exception)
    {}
}
