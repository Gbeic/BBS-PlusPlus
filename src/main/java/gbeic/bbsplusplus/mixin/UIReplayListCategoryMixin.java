package gbeic.bbsplusplus.mixin;

import mchorse.bbs_mod.film.Film;
import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.graphics.window.Window;
import mchorse.bbs_mod.l10n.L10n;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.film.UIFilmPanel;
import mchorse.bbs_mod.ui.film.replays.ReplayListEntry;
import mchorse.bbs_mod.ui.film.replays.UIReplayList;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlay;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIPromptOverlayPanel;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/**
 * 改进影片编辑器回放类别的管理体验。
 *
 * 原版类别更像隐藏在右键菜单里的单层标签：可以创建和移动，但缺少重命名、
 * 多选拖入文件夹等常用整理操作。这里在不改变存档结构的前提下，
 * 继续使用 {@link Replay#category} 和 {@link Film#replayCategoryNames}，
 * 只增强列表交互层。
 */
@Mixin(value = UIReplayList.class, remap = false)
public abstract class UIReplayListCategoryMixin
{
    @Unique
    private static final IKey BBSPP_RENAME_CATEGORY = L10n.lang("bbspp.ui.scene.replays.context.rename_category");

    @Unique
    private static final IKey BBSPP_RENAME_CATEGORY_TITLE = L10n.lang("bbspp.ui.scene.replays.rename_category.title");

    @Unique
    private static final IKey BBSPP_RENAME_CATEGORY_DESCRIPTION = L10n.lang("bbspp.ui.scene.replays.rename_category.description");

    @Shadow
    public UIFilmPanel panel;

    @Shadow
    @Final
    private Set<String> collapsedCategories;

    @Shadow
    private String contextFolderCategoryName;

    @Unique
    private List<Replay> bbspp$pendingCategoryDrop;

    @Unique
    private String bbspp$pendingCategoryDropTarget;

    @Unique
    private List<Replay> bbspp$dragSelectionSnapshot;

    @Shadow
    public abstract List<Replay> getSelectedReplays();

    @Shadow
    public abstract void refreshReplayList();

    @Invoker("updateFilmEditor")
    protected abstract void bbspp$updateFilmEditor();

    @Unique
    private UIReplayList bbspp$self()
    {
        return (UIReplayList) (Object) this;
    }

    /**
     * 注入目标：{@link UIReplayList} 构造器末尾。
     *
     * 注入原因：原版文件夹行的右键菜单只有移除类别，缺少重命名等整理操作。
     * 修改后行为：当右键落在类别行上时，追加“重命名类别”。
     */
    @Inject(method = "<init>", at = @At("TAIL"))
    private void bbspp$addCategoryContextActions(Consumer<List<Replay>> callback, Consumer<Form> formConsumer, UIFilmPanel panel, CallbackInfo ci)
    {
        ((UIReplayList) (Object) this).context((menu) ->
        {
            Film film = this.panel.getData();
            String category = this.contextFolderCategoryName;

            if (film == null || category == null)
            {
                return;
            }

            menu.action(Icons.EDIT, BBSPP_RENAME_CATEGORY, () -> this.bbspp$openRenameCategory(category)).order(90);
        });
    }

    /**
     * 注入目标：{@link UIReplayList#subMouseClicked(UIContext)} 开头。
     *
     * 注入原因：原版点击已多选的回放开始拖拽时，会先把选择缩成当前单项，
     * 导致释放到类别行时已经拿不到完整多选集合。
     * 修改后行为：在原版选择逻辑运行前缓存本次拖拽的多选回放，释放时用于批量移动。
     */
    @Inject(method = "subMouseClicked", at = @At("HEAD"))
    private void bbspp$captureMultiSelectionBeforeDrag(UIContext context, CallbackInfoReturnable<Boolean> cir)
    {
        this.bbspp$dragSelectionSnapshot = null;

        UIReplayList self = this.bbspp$self();

        if (self.isFiltering() || !self.sorting || context.mouseButton != 0 || Window.isCtrlPressed() || Window.isShiftPressed() || !self.area.isInside(context))
        {
            return;
        }

        int index = self.scroll.getIndex(context.mouseX, context.mouseY);

        if (!self.exists(index) || !self.getCurrentIndices().contains(index))
        {
            return;
        }

        ReplayListEntry entry = self.getList().get(index);

        if (!entry.isReplay())
        {
            return;
        }

        List<Replay> selected = new ArrayList<>(this.getSelectedReplays());

        if (selected.size() > 1 && selected.contains(entry.replay))
        {
            this.bbspp$dragSelectionSnapshot = selected;
        }
    }

    /**
     * 注入目标：{@link UIReplayList#subMouseReleased(UIContext)} 开头。
     *
     * 注入原因：原版拖拽到类别行时只移动正在拖动的单条回放，即使列表已经多选。
     * 修改后行为：如果拖动的回放属于拖拽前的多选集合，则先记录整组选中回放和目标类别，等待原逻辑完成后统一应用。
     */
    @Inject(method = "subMouseReleased", at = @At("HEAD"))
    private void bbspp$captureMultiCategoryDrop(UIContext context, CallbackInfoReturnable<Boolean> cir)
    {
        this.bbspp$pendingCategoryDrop = null;
        this.bbspp$pendingCategoryDropTarget = null;

        UIReplayList self = this.bbspp$self();

        if (self.isFiltering() || !self.isDragging())
        {
            return;
        }

        int draggingIndex = self.getDraggingIndex();

        if (!self.exists(draggingIndex))
        {
            return;
        }

        ReplayListEntry dragged = self.getList().get(draggingIndex);

        if (!dragged.isReplay())
        {
            return;
        }

        List<Replay> selected = this.bbspp$dragSelectionSnapshot == null
            ? new ArrayList<>(this.getSelectedReplays())
            : new ArrayList<>(this.bbspp$dragSelectionSnapshot);

        if (selected.size() <= 1 || !selected.contains(dragged.replay))
        {
            return;
        }

        int targetIndex = self.scroll.getIndex(context.mouseX, context.mouseY);

        if (targetIndex == -2)
        {
            this.bbspp$pendingCategoryDrop = selected;
            this.bbspp$pendingCategoryDropTarget = "";

            return;
        }

        if (!self.exists(targetIndex))
        {
            return;
        }

        ReplayListEntry target = self.getList().get(targetIndex);

        if (target.isFolder())
        {
            this.bbspp$pendingCategoryDrop = selected;
            this.bbspp$pendingCategoryDropTarget = target.folderName;
        }
    }

    /**
     * 注入目标：{@link UIReplayList#subMouseReleased(UIContext)} 末尾。
     *
     * 注入原因：需要等原版拖拽逻辑先完成一次单条移动，再覆盖为多选批量移动，
     * 这样不会破坏原本的拖拽判定、滚动条释放和拖拽状态清理。
     * 修改后行为：把拖拽前的多选集合全部移动到同一个类别，并恢复多选状态。
     */
    @Inject(method = "subMouseReleased", at = @At("TAIL"))
    private void bbspp$applyMultiCategoryDrop(UIContext context, CallbackInfoReturnable<Boolean> cir)
    {
        if (this.bbspp$pendingCategoryDrop != null)
        {
            this.bbspp$applyCategory(this.bbspp$pendingCategoryDrop, this.bbspp$pendingCategoryDropTarget);
            this.bbspp$pendingCategoryDrop = null;
            this.bbspp$pendingCategoryDropTarget = null;
        }

        this.bbspp$dragSelectionSnapshot = null;
    }

    @Unique
    private void bbspp$openRenameCategory(String rawCategory)
    {
        String category = Replay.normalizeCategory(rawCategory);

        if (category.isEmpty())
        {
            return;
        }

        UIPromptOverlayPanel panel = new UIPromptOverlayPanel(
            BBSPP_RENAME_CATEGORY_TITLE,
            BBSPP_RENAME_CATEGORY_DESCRIPTION,
            (text) -> this.bbspp$renameCategory(category, text)
        );

        panel.text.setText(category);

        UIOverlay.addOverlay(((UIReplayList) (Object) this).getContext(), panel);
    }

    @Unique
    private void bbspp$renameCategory(String rawOldCategory, String rawNewCategory)
    {
        Film film = this.panel.getData();
        String oldCategory = Replay.normalizeCategory(rawOldCategory);
        String newCategory = Replay.normalizeCategory(rawNewCategory);

        if (film == null || oldCategory.isEmpty() || newCategory.isEmpty() || oldCategory.equals(newCategory))
        {
            return;
        }

        List<Replay> selected = new ArrayList<>(this.getSelectedReplays());
        Set<String> names = new HashSet<>(film.replayCategoryNames.get());

        names.remove(oldCategory);
        names.add(newCategory);
        film.replayCategoryNames.set(names);

        for (Replay replay : film.replays.getList())
        {
            if (oldCategory.equals(Replay.normalizeCategory(replay.category.get())))
            {
                replay.category.set(newCategory);
            }
        }

        boolean wasCollapsed = this.collapsedCategories.remove(oldCategory);

        if (wasCollapsed)
        {
            this.collapsedCategories.add(newCategory);
        }

        this.refreshReplayList();
        this.bbspp$restoreReplaySelection(selected);
        this.bbspp$updateFilmEditor();
    }

    @Unique
    private void bbspp$applyCategory(List<Replay> selected, String rawCategory)
    {
        String category = Replay.normalizeCategory(rawCategory);

        for (Replay replay : selected)
        {
            replay.category.set(category);
        }

        if (!category.isEmpty())
        {
            this.collapsedCategories.remove(category);
        }

        this.refreshReplayList();
        this.bbspp$restoreReplaySelection(selected);
        this.bbspp$updateFilmEditor();
    }

    @Unique
    private void bbspp$restoreReplaySelection(List<Replay> replays)
    {
        UIReplayList self = this.bbspp$self();

        self.getCurrentIndices().clear();

        for (Replay replay : replays)
        {
            for (int i = 0; i < self.getList().size(); i++)
            {
                ReplayListEntry entry = self.getList().get(i);

                if (entry.isReplay() && entry.replay == replay)
                {
                    self.addIndex(i);

                    break;
                }
            }
        }

        if (self.callback != null && !self.getCurrentIndices().isEmpty())
        {
            self.callback.accept(self.getCurrent());
        }
    }
}
