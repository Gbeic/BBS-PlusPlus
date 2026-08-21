package gbeic.bbsplusplus.mixin;

import gbeic.bbsplusplus.BBSAddonsSettings;
import mchorse.bbs_mod.utils.undo.IUndo;
import mchorse.bbs_mod.utils.undo.UndoManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

/**
 * 让撤销历史容量跟随 BBS++ 设置项。
 * <p>
 * 新版 BBS FS 已经有更完整的历史面板和跳转能力，但每个编辑器的历史容量仍来自
 * {@link UndoManager} 的构造参数。BBS++ 在推入新历史时应用用户设置，并在超出容量时
 * 丢弃最旧记录，实现统一的“历史记录步数”控制。
 * </p>
 */
@Mixin(UndoManager.class)
public class UndoManagerMixin<T>
{
    @Shadow
    private List<IUndo<T>> undos;

    @Shadow
    private int position;

    @Shadow
    private int limit;

    /**
     * 注入目标：推入新的撤销记录前。
     * 注入原因：容量来自构造参数，无法由设置界面统一控制；如果直接把容量改小，
     * 可能在用户从历史中间继续编辑时跳过重做分支清理。
     * 修改行为：本次推入先临时放宽容量，让原逻辑完成重做分支删除；真正裁剪交给返回后统一处理。
     */
    @Inject(method = "pushUndo", at = @At("HEAD"), remap = false)
    private void bbsplusplus$applyConfiguredLimit(IUndo<T> undo, CallbackInfoReturnable<IUndo<T>> cir)
    {
        this.limit = Math.max(getConfiguredLimit(), this.undos.size() + 1);
    }

    /**
     * 注入目标：推入新的撤销记录后。
     * 注入原因：当用户把历史步数调小后，已有历史可能已经超过新上限。
     * 修改行为：从最旧记录开始裁剪，并同步当前撤销指针。
     */
    @Inject(method = "pushUndo", at = @At("RETURN"), remap = false)
    private void bbsplusplus$trimToConfiguredLimit(IUndo<T> undo, CallbackInfoReturnable<IUndo<T>> cir)
    {
        int max = getConfiguredLimit();

        while (this.undos.size() > max)
        {
            this.undos.remove(0);
            this.position = Math.max(-1, this.position - 1);
        }

        this.limit = max;
    }

    private static int getConfiguredLimit()
    {
        if (BBSAddonsSettings.undoHistoryLimit == null)
        {
            return 50;
        }

        return Math.max(1, BBSAddonsSettings.undoHistoryLimit.get());
    }
}
