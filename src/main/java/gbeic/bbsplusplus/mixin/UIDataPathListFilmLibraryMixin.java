package gbeic.bbsplusplus.mixin;

import gbeic.bbsplusplus.client.ui.film.IFilmLibraryPathList;
import mchorse.bbs_mod.ui.dashboard.list.UIDataPathList;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.input.list.UIList;
import mchorse.bbs_mod.utils.DataPath;
import mchorse.bbs_mod.utils.NaturalOrderComparator;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/**
 * 为影片库中的路径列表补充完整路径搜索与名称降序排序。
 * <p>
 * 默认不改变 {@link UIDataPathList} 的行为，只有影片库显式开启模式后才生效，避免影响其它资源选择器。
 * </p>
 */
@Mixin(UIDataPathList.class)
public abstract class UIDataPathListFilmLibraryMixin extends UIList<DataPath> implements IFilmLibraryPathList
{
    @Shadow private Set<DataPath> hierarchy;
    @Shadow private DataPath path;
    @Shadow private DataPath previousPath;

    @Unique private boolean bbspp$filmLibraryMode;
    @Unique private boolean bbspp$showFullPaths;
    @Unique private boolean bbspp$sortDescending;
    @Unique private boolean bbspp$showAllFilms = true;
    @Unique private final DataPath bbspp$currentFolder = new DataPath(true);
    @Unique private final Set<DataPath> bbspp$knownFolders = new HashSet<>();
    @Unique private String bbspp$selectAfterRefresh = "";

    public UIDataPathListFilmLibraryMixin(Consumer<java.util.List<DataPath>> callback)
    {
        super(callback);
    }

    @Override
    public void bbspp$setFilmLibraryMode(boolean enabled)
    {
        this.bbspp$filmLibraryMode = enabled;
    }

    @Override
    public boolean bbspp$isFilmLibraryMode()
    {
        return this.bbspp$filmLibraryMode;
    }

    @Override
    public void bbspp$filterFilmLibrary(String query)
    {
        if (!this.bbspp$filmLibraryMode)
        {
            this.filter(query);

            return;
        }

        String normalized = query == null ? "" : query.trim().toLowerCase();

        this.bbspp$showFullPaths = !normalized.isEmpty() && this.bbspp$showAllFilms;
        this.list.clear();

        if (normalized.isEmpty())
        {
            this.bbspp$fillCurrentFilmLibraryView();
        }
        else
        {
            for (DataPath dataPath : this.hierarchy)
            {
                if (dataPath.folder)
                {
                    continue;
                }

                if (!this.bbspp$showAllFilms && !dataPath.startsWith(this.bbspp$currentFolder, 1))
                {
                    continue;
                }

                String full = dataPath.toString().toLowerCase();
                String last = dataPath.getLast().toLowerCase();

                if (full.contains(normalized) || last.contains(normalized))
                {
                    this.list.add(dataPath);
                }
            }
        }

        this.bbspp$clearFilmLibrarySelection();
        this.sort();
        this.update();
    }

    @Override
    public void bbspp$showAllFilmLibraryFilms()
    {
        this.bbspp$showAllFilms = true;
        this.bbspp$currentFolder.copy(DataPath.EMPTY);
        this.bbspp$fillCurrentFilmLibraryView();
    }

    @Override
    public void bbspp$showFilmLibraryFolder(DataPath folder)
    {
        this.bbspp$showAllFilms = false;
        this.bbspp$currentFolder.copy(folder == null ? DataPath.EMPTY : folder);
        this.bbspp$currentFolder.folder = true;
        this.bbspp$fillCurrentFilmLibraryView();
    }

    @Override
    public Collection<DataPath> bbspp$getFilmLibraryFolders()
    {
        Set<DataPath> folders = new HashSet<>(this.bbspp$knownFolders);

        for (DataPath dataPath : this.hierarchy)
        {
            int folderDepth = dataPath.folder ? dataPath.strings.size() : dataPath.strings.size() - 1;

            for (int i = 1; i <= folderDepth; i++)
            {
                DataPath folder = dataPath.getTo(i);

                folder.folder = true;
                folders.add(folder);
            }
        }

        List<DataPath> sorted = new ArrayList<>(folders);

        sorted.sort((a, b) -> NaturalOrderComparator.compare(true, a.toString(), b.toString()));

        return sorted;
    }

    @Override
    public boolean bbspp$isShowingAllFilmLibraryFilms()
    {
        return this.bbspp$showAllFilms;
    }

    @Override
    public DataPath bbspp$getFilmLibraryFolder()
    {
        return this.bbspp$currentFolder.copy();
    }

    @Override
    public void bbspp$selectFilmLibraryAfterRefresh(String id)
    {
        this.bbspp$selectAfterRefresh = id == null ? "" : id;
    }

    @Override
    public boolean bbspp$isFilmLibraryFolderEmpty(DataPath folder)
    {
        if (folder == null)
        {
            return true;
        }

        for (DataPath dataPath : this.hierarchy)
        {
            if (dataPath.startsWith(folder) && !dataPath.equals(folder))
            {
                return false;
            }
        }

        for (DataPath dataPath : this.bbspp$knownFolders)
        {
            if (dataPath.startsWith(folder) && !dataPath.equals(folder))
            {
                return false;
            }
        }

        return true;
    }

    @Override
    public void bbspp$rememberFilmLibraryFolder(DataPath folder)
    {
        if (folder != null && !folder.strings.isEmpty())
        {
            DataPath copy = folder.copy();

            copy.folder = true;
            this.bbspp$knownFolders.add(copy);
        }
    }

    @Override
    public void bbspp$forgetFilmLibraryFolder(DataPath folder)
    {
        if (folder != null)
        {
            this.bbspp$knownFolders.removeIf((known) -> known.equals(folder) || known.startsWith(folder));
        }
    }

    @Override
    public void bbspp$renameKnownFilmLibraryFolder(DataPath folder, String name)
    {
        if (folder == null || folder.strings.isEmpty() || name == null || name.trim().isEmpty())
        {
            return;
        }

        List<DataPath> renamed = new ArrayList<>();
        String from = folder.toString();
        DataPath parent = folder.getParent();
        String prefix = parent.strings.isEmpty() ? name : parent + "/" + name;

        for (DataPath known : this.bbspp$knownFolders)
        {
            if (!known.equals(folder) && !known.startsWith(folder))
            {
                continue;
            }

            String current = known.toString();
            DataPath copy = new DataPath(current.equals(from) ? prefix : prefix + "/" + current.substring((from + "/").length()));

            copy.folder = true;
            renamed.add(copy);
        }

        this.bbspp$forgetFilmLibraryFolder(folder);
        this.bbspp$knownFolders.addAll(renamed);
    }

    @Override
    public void bbspp$setFilmLibrarySortDescending(boolean descending)
    {
        this.bbspp$sortDescending = descending;
        this.sort();
        this.update();
    }

    @Override
    public boolean bbspp$isFilmLibrarySortDescending()
    {
        return this.bbspp$sortDescending;
    }

    @Override
    protected boolean subKeyPressed(UIContext context)
    {
        if (this.bbspp$filmLibraryMode && context.isPressed(GLFW.GLFW_KEY_ESCAPE) && this.isSelected())
        {
            this.bbspp$clearFilmLibrarySelection();

            return true;
        }

        return super.subKeyPressed(context);
    }

    /**
     * 注入目标：{@link UIDataPathList#subMouseClicked(UIContext)} 开始处。
     * 注入原因：新版影片库中右侧空白区域应取消影片选择，避免旧选中项误导后续操作。
     * 修改后的行为：影片库模式下，点击右侧列表空白处会清空选择；条目点击仍交给原版逻辑处理。
     */
    @Inject(method = "subMouseClicked", at = @At("HEAD"), remap = false)
    private void bbspp$deselectOnFilmLibraryEmptyClick(UIContext context, CallbackInfoReturnable<Boolean> cir)
    {
        if (this.bbspp$filmLibraryMode && this.area.isInside(context) && this.getIndexAtCursor(context) < 0)
        {
            this.bbspp$clearFilmLibrarySelection();
        }
    }

    @Unique
    private void bbspp$clearFilmLibrarySelection()
    {
        this.deselect();
        this.previousPath = null;
    }

    @Unique
    private void bbspp$fillCurrentFilmLibraryView()
    {
        this.list.clear();

        if (this.bbspp$showAllFilms)
        {
            for (DataPath dataPath : this.hierarchy)
            {
                if (!dataPath.folder)
                {
                    this.list.add(dataPath);
                }
            }
        }
        else
        {
            for (DataPath dataPath : this.hierarchy)
            {
                if (!dataPath.folder && dataPath.startsWith(this.bbspp$currentFolder, 1))
                {
                    this.list.add(dataPath);
                }
            }
        }

        this.path.copy(this.bbspp$currentFolder);
        this.bbspp$showFullPaths = this.bbspp$showAllFilms;
        this.bbspp$clearFilmLibrarySelection();
        this.sort();

        if (!this.bbspp$selectAfterRefresh.isEmpty())
        {
            DataPath selected = new DataPath(this.bbspp$selectAfterRefresh);

            if (this.list.contains(selected))
            {
                this.setCurrentScroll(selected);
                this.bbspp$selectAfterRefresh = "";
            }
        }

        this.update();
    }

    @Unique
    private void bbspp$captureKnownFilmLibraryFolders()
    {
        for (DataPath dataPath : this.hierarchy)
        {
            int folderDepth = dataPath.folder ? dataPath.strings.size() : dataPath.strings.size() - 1;

            for (int i = 1; i <= folderDepth; i++)
            {
                DataPath folder = dataPath.getTo(i);

                folder.folder = true;
                this.bbspp$knownFolders.add(folder);
            }
        }
    }

    /**
     * 注入目标：{@link UIDataPathList#fill(Collection)} 返回后。
     * 注入原因：原版填充后会回到根目录并把文件夹混入右侧列表；影片库需要由左侧树承担文件夹导航。
     * 修改后的行为：影片库模式下刷新当前影片库视图，并隐藏右侧文件夹项。
     */
    @Inject(method = "fill", at = @At("RETURN"), remap = false)
    private void bbspp$showFilmsAfterFill(Collection<String> hierarchy, CallbackInfo ci)
    {
        if (this.bbspp$filmLibraryMode)
        {
            this.bbspp$captureKnownFilmLibraryFolders();
            this.bbspp$fillCurrentFilmLibraryView();
        }
    }

    /**
     * 注入目标：{@link UIDataPathList#sortElements()} 返回前。
     * 注入原因：影片库需要保存名称排序偏好，而原版列表只提供固定自然升序。
     * 修改后的行为：影片库模式下按文件夹优先、返回上级优先、名称升降序重新排序。
     */
    @Inject(method = "sortElements", at = @At("RETURN"), remap = false)
    private void bbspp$sortFilmLibrary(CallbackInfoReturnable<Boolean> cir)
    {
        if (!this.bbspp$filmLibraryMode)
        {
            return;
        }

        this.list.sort((a, b) ->
        {
            if (a.folder && !b.folder) return -1;
            if (b.folder && !a.folder) return 1;

            if (a.toString().endsWith("/..")) return -1;
            if (b.toString().endsWith("/..")) return 1;

            int result = NaturalOrderComparator.compare(true, a.toString(), b.toString());

            return this.bbspp$sortDescending ? -result : result;
        });
    }

    /**
     * 注入目标：{@link UIDataPathList#elementToString(UIContext, int, DataPath)} 开始处。
     * 注入原因：完整路径搜索结果只显示末端文件名会丢失目录语境。
     * 修改后的行为：影片库路径搜索时显示完整路径，普通浏览仍显示当前文件夹内名称。
     */
    @Inject(method = "elementToString", at = @At("HEAD"), cancellable = true, remap = false)
    private void bbspp$showFullPathWhenSearching(UIContext context, int i, DataPath element, CallbackInfoReturnable<String> cir)
    {
        if (this.bbspp$filmLibraryMode && this.bbspp$showFullPaths)
        {
            cir.setReturnValue(element.toString() + (element.folder ? "/" : ""));
        }
    }
}
