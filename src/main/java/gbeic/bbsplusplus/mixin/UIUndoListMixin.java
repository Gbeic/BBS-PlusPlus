package gbeic.bbsplusplus.mixin;

import gbeic.bbsplusplus.client.UndoHistoryLabeler;
import mchorse.bbs_mod.ui.film.utils.undo.UIUndoList;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.utils.undo.IUndo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 为新版撤销历史列表补充可读摘要。
 * <p>
 * BBS FS 的历史面板已经支持点击跳转到指定历史位置，也会绘制悬浮提示，
 * 但列表仍显示 {@code camera/9/fov/2} 这类内部路径。BBS++ 只替换元素文案，
 * 让原有列表和悬浮提示自动复用可读文本，不改变撤销记录本身、当前索引或点击跳转逻辑。
 * </p>
 */
@Mixin(UIUndoList.class)
public abstract class UIUndoListMixin<T>
{
    /**
     * 注入目标：撤销历史列表把元素转换为可见字符串时。
     * 注入原因：默认显示原始 {@link mchorse.bbs_mod.utils.DataPath}，缺少可读操作描述。
     * 修改行为：使用 {@link UndoHistoryLabeler} 生成中文摘要。
     */
    @Inject(method = "elementToString", at = @At("HEAD"), cancellable = true, remap = false)
    private void bbsplusplus$labelUndo(UIContext context, int i, IUndo<T> element, CallbackInfoReturnable<String> cir)
    {
        cir.setReturnValue(UndoHistoryLabeler.label(element));
    }
}
