package gbeic.bbsplusplus.mixin;

import mchorse.bbs_mod.settings.values.core.ValueGroup;
import mchorse.bbs_mod.ui.film.UIFilmPanel;
import mchorse.bbs_mod.ui.film.utils.undo.ValueChangeUndo;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.forms.editors.UIFormUndoHandler;
import mchorse.bbs_mod.utils.undo.CompoundUndo;
import mchorse.bbs_mod.utils.undo.IUndo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 收窄撤销/恢复后的界面快照恢复范围。
 * <p>
 * BBS FS 的历史记录会保存 UI 快照，方便部分编辑器在历史跳转后重建面板状态。
 * 但普通数据撤销不应该把当前页签、列表选中项、轨道缩放和滚动位置拉回旧画面。
 * BBS++ 只在普通表单编辑器中跳过无关快照；影片编辑器仍保留快照流程，
 * 因为整部影片撤销会重建 Replay 对象，时间轴和录制控制器都需要重新绑定新对象。
 * </p>
 */
@Mixin(UIFormUndoHandler.class)
public class UIFormUndoHandlerMixin
{
    @Shadow
    protected UIElement uiElement;

    /**
     * 注入目标：{@link UIFormUndoHandler} 处理撤销/恢复回调时。
     * 注入原因：默认流程在这里调用 {@code applyAllUndoData(...)}，把历史里的界面快照重新套回当前界面。
     * 修改行为：影片编辑器始终保留原版快照恢复；其它编辑器仅为布局历史恢复快照。
     */
    @Inject(method = "handleUndos", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void bbspp$skipSnapshotForDataChanges(IUndo<ValueGroup> undo, boolean redo, CallbackInfo ci)
    {
        if (this.uiElement instanceof UIFilmPanel || bbspp$shouldApplyUiSnapshot(undo))
        {
            return;
        }

        ci.cancel();
    }

    /**
     * 注入目标：{@link UIFormUndoHandler} 完成撤销/恢复后的界面快照处理。
     * 注入原因：世界内录制通过空路径历史覆盖整部影片，撤销时会重建 Replay 对象；
     * 仅恢复通用 UI 快照仍可能让回放时间轴持有撤销前的旧对象，导致残留关键帧和录制索引失效。
     * 修改行为：整部影片撤销/恢复完成后，强制把回放列表、时间轴和实体控制器重新绑定到当前影片对象。
     */
    @Inject(method = "handleUndos", at = @At("TAIL"), remap = false)
    private void bbspp$rebindReplayEditorAfterWholeFilmChange(IUndo<ValueGroup> undo, boolean redo, CallbackInfo ci)
    {
        if (!(this.uiElement instanceof UIFilmPanel panel) || panel.getData() == null || !bbspp$containsWholeFilmChange(undo))
        {
            return;
        }

        panel.replayEditor.setFilm(panel.getData());
        panel.fillData();
        panel.getController().createEntities();
    }

    private static boolean bbspp$containsWholeFilmChange(IUndo<?> undo)
    {
        if (undo instanceof ValueChangeUndo valueUndo)
        {
            return valueUndo.name.strings.isEmpty();
        }

        if (undo instanceof CompoundUndo<?> compoundUndo)
        {
            for (IUndo<?> child : compoundUndo.getUndos())
            {
                if (bbspp$containsWholeFilmChange(child))
                {
                    return true;
                }
            }
        }

        return false;
    }

    private static boolean bbspp$shouldApplyUiSnapshot(IUndo<?> undo)
    {
        if (undo instanceof ValueChangeUndo valueUndo)
        {
            return bbspp$isLayoutPath(valueUndo);
        }

        if (undo instanceof CompoundUndo<?> compoundUndo)
        {
            for (IUndo<?> child : compoundUndo.getUndos())
            {
                if (bbspp$shouldApplyUiSnapshot(child))
                {
                    return true;
                }
            }
        }

        return false;
    }

    private static boolean bbspp$isLayoutPath(ValueChangeUndo undo)
    {
        for (String segment : undo.name.strings)
        {
            if (
                segment.equals("layout") ||
                segment.equals("film_layout") ||
                segment.equals("particle_layout") ||
                segment.equals("film_editor_layouts") ||
                segment.equals("film_editor_layout_bindings") ||
                segment.equals("state_editor_size_h") ||
                segment.equals("state_editor_size_v") ||
                segment.equals("keyframe_label_width")
            ) {
                return true;
            }
        }

        return false;
    }
}
