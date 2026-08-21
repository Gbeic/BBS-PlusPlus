package gbeic.bbsplusplus.mixin;

import gbeic.bbsplusplus.util.ClipOverlapFixer;
import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.utils.clips.Clip;
import mchorse.bbs_mod.utils.clips.Clips;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

/**
 * 为 BBS 剪辑容器补上同层重叠数据的自动修复。
 * <p>
 * 原版拖拽偶发产生重叠剪辑后，坏数据会随影片保存下来，
 * 再次打开影片时就可能在时间线或回放查询中崩溃。
 * 此 Mixin 在加载、添加和保存前整理剪辑层级，
 * 让旧影片能打开，并阻止新的坏数据继续写盘。
 * </p>
 */
@Mixin(value = Clips.class, remap = false)
public class ClipsMixin
{
    @Shadow
    private List<Clip> clips;

    /**
     * 注入目标：{@link Clips#fromData(BaseType)} 调用 {@code sync()} 之前。
     * 注入原因：旧影片可能已经包含同层重叠剪辑，必须在 UI 和回放读取之前修正。
     * 修改行为：把后发生冲突的剪辑抬到最近可用层，保留原 tick 和 duration。
     */
    @Inject(method = "fromData", at = @At(value = "INVOKE", target = "Lmchorse/bbs_mod/utils/clips/Clips;sync()V", shift = At.Shift.BEFORE))
    private void bbspp$repairAfterLoad(BaseType base, CallbackInfo ci)
    {
        ClipOverlapFixer.repair(this.clips);
    }

    /**
     * 注入目标：{@link Clips#addClip(Clip)} 调用 {@code sync()} 之前。
     * 注入原因：新增或粘贴剪辑时也可能绕开 UI 的尺寸检查。
     * 修改行为：新增后立即整理同层冲突，避免内存中留下坏时间线。
     */
    @Inject(method = "addClip", at = @At(value = "INVOKE", target = "Lmchorse/bbs_mod/utils/clips/Clips;sync()V", shift = At.Shift.BEFORE))
    private void bbspp$repairAfterAdd(Clip clip, CallbackInfo ci)
    {
        ClipOverlapFixer.repair(this.clips);
    }

    /**
     * 注入目标：{@link Clips#toData()} 序列化开头。
     * 注入原因：服务端保存影片时会直接写客户端传来的数据，保存前必须再做一次兜底。
     * 修改行为：序列化前整理层级，确保写盘数据不再包含同层重叠片段。
     */
    @Inject(method = "toData", at = @At("HEAD"))
    private void bbspp$repairBeforeSave(CallbackInfoReturnable<BaseType> cir)
    {
        ClipOverlapFixer.repair(this.clips);
    }
}
