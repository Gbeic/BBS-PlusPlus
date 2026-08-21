package gbeic.bbsplusplus.mixin;

import gbeic.bbsplusplus.client.UndoHistoryLabeler;
import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.settings.values.core.ValueGroup;
import mchorse.bbs_mod.ui.film.utils.undo.ValueChangeUndo;
import mchorse.bbs_mod.utils.DataPath;
import mchorse.bbs_mod.utils.undo.IUndo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 修正值变更撤销记录的合并语义。
 * <p>
 * 新版历史面板可以跳转到指定历史位置，但 {@link ValueChangeUndo} 的合并逻辑仍只处理数据值。
 * BBS++ 保留默认的数据合并结果，只补充同步 UI 快照，并把关键帧数量变化作为不可继续合并的边界，
 * 避免“添加关键帧”和“调整关键帧”被压成同一步。
 * </p>
 */
@Mixin(ValueChangeUndo.class)
public class ValueChangeUndoMixin
{
    @Shadow
    public DataPath name;

    @Shadow
    public MapType uiAfter;

    @Shadow
    public BaseType oldValue;

    @Shadow
    public BaseType newValue;

    /**
     * 注入目标：判断两条值修改历史是否允许合并时。
     * 注入原因：添加/删除关键帧之后再调整同一轨道时，默认合并会让撤销粒度过粗。
     * 修改行为：只要当前历史或新历史改变了关键帧数量，就禁止继续合并。
     */
    @Inject(method = "isMergeable", at = @At("HEAD"), cancellable = true, remap = false)
    private void bbsplusplus$stopMergingAfterKeyframeCountChange(IUndo<ValueGroup> undo, CallbackInfoReturnable<Boolean> cir)
    {
        ValueChangeUndo self = (ValueChangeUndo) (Object) this;

        if (UndoHistoryLabeler.changesKeyframeCount(self) || UndoHistoryLabeler.changesKeyframeCount(undo))
        {
            cir.setReturnValue(false);
        }
    }

    /**
     * 注入目标：同一路径历史合并完成后。
     * 注入原因：默认合并时已经更新 {@code newValue}，但没有同步更新 {@code uiAfter}，
     * 导致撤销/重做后列表选中、面板状态可能回到更早的一次操作。
     * 修改行为：只补充同步 UI 快照，让界面状态跟随最后一次修改。
     */
    @Inject(method = "merge", at = @At("TAIL"), remap = false)
    private void bbsplusplus$mergeLatestUiState(IUndo<ValueGroup> undo, CallbackInfo ci)
    {
        if (undo instanceof ValueChangeUndo valueUndo)
        {
            this.uiAfter = valueUndo.uiAfter;
        }
    }

    /**
     * 注入目标：{@code ValueChangeUndo#undo(ValueGroup)} 开头。
     * 注入原因：外部快捷键录制结束时，BBSFS 会用 {@code BaseValue.edit(film, ...)}
     * 记录整部影片的变更，生成的历史路径为空；原版用空路径反查不到根 Film，
     * 导致 Ctrl+Z 看似执行但实际不会撤回录制内容。
     * 修改行为：空路径历史直接把撤销上下文本身当作目标值，让整部影片快照能正常撤回。
     *
     * <p>
     * 1.20.1 主线的 BBS 2.4 把查找逻辑抽成了私有的 {@code resolveValue}，所以那边只注入
     * 一处即可；FSR 所基于的版本仍在 {@code undo} 与 {@code redo} 中各自内联
     * {@code context.getRecursively(name)}，因此这里改为分别注入两个方法。
     * </p>
     */
    @Inject(method = "undo(Lmchorse/bbs_mod/settings/values/core/ValueGroup;)V", at = @At("HEAD"), cancellable = true, remap = false)
    private void bbspp$undoRootValue(ValueGroup context, CallbackInfo ci)
    {
        if (this.name.strings.isEmpty())
        {
            context.fromData(this.oldValue);
            ci.cancel();
        }
    }

    /**
     * 注入目标：{@code ValueChangeUndo#redo(ValueGroup)} 开头。
     * 注入原因与行为同 {@link #bbspp$undoRootValue}，只是应用的是新值。
     */
    @Inject(method = "redo(Lmchorse/bbs_mod/settings/values/core/ValueGroup;)V", at = @At("HEAD"), cancellable = true, remap = false)
    private void bbspp$redoRootValue(ValueGroup context, CallbackInfo ci)
    {
        if (this.name.strings.isEmpty())
        {
            context.fromData(this.newValue);
            ci.cancel();
        }
    }
}
