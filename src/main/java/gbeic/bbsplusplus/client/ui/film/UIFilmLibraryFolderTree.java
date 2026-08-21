package gbeic.bbsplusplus.client.ui.film;

import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.l10n.L10n;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.dashboard.list.UIDataPathList;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.input.text.UITextbox;
import mchorse.bbs_mod.ui.framework.elements.utils.FontRenderer;
import mchorse.bbs_mod.ui.utils.Scroll;
import mchorse.bbs_mod.ui.utils.icons.Icon;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.DataPath;
import mchorse.bbs_mod.utils.NaturalOrderComparator;
import mchorse.bbs_mod.utils.colors.Colors;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 影片库左侧文件夹树。
 * <p>
 * 文件夹树直接读取原版路径列表中的影片层级，不创建新的数据格式。默认只显示顶层文件夹，
 * 用户点击箭头后展开子文件夹；点击文件夹本身时，右侧主列表切换为该文件夹直属影片。
 * </p>
 */
public class UIFilmLibraryFolderTree extends UIElement
{
    private static final int ROW_HEIGHT = 20;
    private static final int INDENT = 12;

    private final UITextbox search;
    private final UIDataPathList list;
    private final Actions actions;
    private final Scroll scroll = new Scroll(this.area, ROW_HEIGHT);
    private final Set<String> expanded = new HashSet<>();
    private FolderRow contextRow;

    public UIFilmLibraryFolderTree(UITextbox search, UIDataPathList list, Actions actions)
    {
        this.search = search;
        this.list = list;
        this.actions = actions;

        this.context((menu) ->
        {
            FolderRow row = this.contextRow;

            if (row == null)
            {
                return;
            }

            menu.action(Icons.FOLDER, UIKeys.PANELS_MODALS_ADD_FOLDER_TITLE, () -> this.actions.createFolder(row.path));

            if (!row.background)
            {
                menu.action(Icons.ADD, L10n.lang("bbspp.ui.film.library.new_film"), () -> this.actions.createFilm(row.path));
                menu.action(Icons.FOLDER, UIKeys.PANELS_CONTEXT_OPEN, () -> this.actions.openFolder(row.path));

                if (!row.root)
                {
                    menu.action(Icons.EDIT, UIKeys.PANELS_MODALS_RENAME_FOLDER_TITLE, () -> this.actions.renameFolder(row.path));

                    if (this.list instanceof IFilmLibraryPathList pathList && pathList.bbspp$isFilmLibraryFolderEmpty(row.path))
                    {
                        menu.action(Icons.REMOVE, UIKeys.PANELS_MODALS_REMOVE_FOLDER_TITLE, () -> this.actions.deleteFolder(row.path));
                    }
                }
            }
            else
            {
                menu.action(Icons.FOLDER, UIKeys.PANELS_CONTEXT_OPEN, () -> this.actions.openFolder(row.path));
            }

            if (!FilmLibraryDefaultLocation.isFolder(row.path))
            {
                menu.action(Icons.BOOKMARK, L10n.lang("bbspp.ui.film.library.set_default_open"), () -> FilmLibraryDefaultLocation.setFolder(row.path));
            }

            menu.action(Icons.REFRESH, L10n.lang("bbspp.ui.film.library.refresh"), this.actions::refresh);
        });
    }

    @Override
    protected boolean subMouseClicked(UIContext context)
    {
        if (!this.area.isInside(context) || !(this.list instanceof IFilmLibraryPathList pathList))
        {
            return false;
        }

        if (this.scroll.mouseClicked(context))
        {
            return true;
        }

        List<FolderRow> rows = this.buildRows(pathList.bbspp$getFilmLibraryFolders());
        int index = (int) ((context.mouseY - this.area.y + this.scroll.getScroll()) / ROW_HEIGHT);

        if (index < 0 || index >= rows.size())
        {
            this.contextRow = new FolderRow(DataPath.EMPTY.copy(), 0, false, true, true);

            return false;
        }

        FolderRow row = rows.get(index);

        if (context.mouseButton == 1)
        {
            this.contextRow = row;
            this.search.setText("");
            pathList.bbspp$showFilmLibraryFolder(row.path);

            return false;
        }

        if (context.mouseButton != 0)
        {
            return false;
        }

        int arrowX = this.area.ex() - 18;

        if (!row.root && row.hasChildren && context.mouseX >= arrowX && context.mouseX < arrowX + 16)
        {
            String key = row.path.toString();

            if (this.expanded.contains(key))
            {
                this.expanded.remove(key);
            }
            else
            {
                this.expanded.add(key);
            }
        }
        else
        {
            this.search.setText("");
            pathList.bbspp$showFilmLibraryFolder(row.path);
        }

        return true;
    }

    @Override
    protected boolean subMouseScrolled(UIContext context)
    {
        return this.scroll.mouseScroll(context);
    }

    @Override
    protected boolean subMouseReleased(UIContext context)
    {
        this.scroll.mouseReleased(context);

        return super.subMouseReleased(context);
    }

    @Override
    public void render(UIContext context)
    {
        if (!(this.list instanceof IFilmLibraryPathList pathList))
        {
            super.render(context);

            return;
        }

        List<FolderRow> rows = this.buildRows(pathList.bbspp$getFilmLibraryFolders());
        DataPath current = pathList.bbspp$getFilmLibraryFolder();
        FontRenderer font = context.batcher.getFont();

        this.scroll.setSize(rows.size());
        this.scroll.drag(context);
        context.batcher.clip(this.area, context);

        for (int i = 0; i < rows.size(); i++)
        {
            int y = this.area.y + i * ROW_HEIGHT - (int) this.scroll.getScroll();

            if (y + ROW_HEIGHT <= this.area.y)
            {
                continue;
            }

            if (y >= this.area.ey())
            {
                break;
            }

            FolderRow row = rows.get(i);
            boolean active = !pathList.bbspp$isShowingAllFilmLibraryFilms() && row.path.equals(current);
            int color = active ? Colors.WHITE : Colors.GRAY;

            if (active)
            {
                context.batcher.box(this.area.x, y, this.area.ex(), y + ROW_HEIGHT, Colors.A25 | BBSSettings.primaryColor.get());
            }

            int indent = row.depth * INDENT;
            int iconX = this.area.x + indent + 10;
            int textX = this.area.x + indent + 24;
            int arrowX = this.area.ex() - 18;
            int centerY = y + ROW_HEIGHT / 2;

            for (int depth = 0; depth < row.depth; depth++)
            {
                int lineX = this.area.x + depth * INDENT + 4;

                context.batcher.box(lineX, y, lineX + 1, y + ROW_HEIGHT, Colors.A25 | Colors.WHITE);
            }

            if (!row.root && row.hasChildren)
            {
                Icon arrow = this.expanded.contains(row.path.toString()) ? Icons.ARROW_DOWN : Icons.ARROW_RIGHT;

                context.batcher.icon(arrow, color, arrowX, centerY, 0F, 0.5F);
            }

            context.batcher.icon(Icons.FOLDER, color, iconX, centerY, 0.5F, 0.5F);

            String label = row.root ? L10n.lang("bbspp.ui.film.library.root_folder").get() : row.path.getLast();
            int rightPadding = row.hasChildren || this.scroll.hasScrollbar() ? 22 : 4;
            String text = font.limitToWidth(label, this.area.ex() - textX - rightPadding);

            context.batcher.textShadow(text, textX, y + (ROW_HEIGHT - font.getHeight()) / 2, color);
        }

        this.scroll.renderScrollbar(context.batcher);
        context.batcher.unclip(context);

        super.render(context);
    }

    private List<FolderRow> buildRows(Collection<DataPath> folders)
    {
        List<DataPath> sorted = new ArrayList<>(folders);
        List<FolderRow> rows = new ArrayList<>();

        sorted.sort((a, b) -> NaturalOrderComparator.compare(true, a.toString(), b.toString()));
        rows.add(new FolderRow(DataPath.EMPTY.copy(), 0, false, true, false));

        for (DataPath folder : sorted)
        {
            if (!this.isVisible(folder))
            {
                continue;
            }

            rows.add(new FolderRow(folder, folder.strings.size() - 1, this.hasChildren(folder, sorted), false, false));
        }

        return rows;
    }

    private boolean isVisible(DataPath folder)
    {
        for (int i = 1; i < folder.strings.size(); i++)
        {
            DataPath parent = folder.getTo(i);

            parent.folder = true;

            if (!this.expanded.contains(parent.toString()))
            {
                return false;
            }
        }

        return true;
    }

    private boolean hasChildren(DataPath folder, List<DataPath> folders)
    {
        for (DataPath candidate : folders)
        {
            if (candidate.startsWith(folder, 1))
            {
                return true;
            }
        }

        return false;
    }

    private static class FolderRow
    {
        private final DataPath path;
        private final int depth;
        private final boolean hasChildren;
        private final boolean root;
        private final boolean background;

        private FolderRow(DataPath path, int depth, boolean hasChildren, boolean root, boolean background)
        {
            this.path = path;
            this.depth = depth;
            this.hasChildren = hasChildren;
            this.root = root;
            this.background = background;
        }
    }

    /**
     * 文件夹树向外请求实际仓库操作的回调。
     */
    public interface Actions
    {
        /** 在指定文件夹中创建影片。 */
        public void createFilm(DataPath folder);

        /** 在指定文件夹中创建子文件夹。 */
        public void createFolder(DataPath folder);

        /** 重命名指定文件夹。 */
        public void renameFolder(DataPath folder);

        /** 删除指定文件夹。 */
        public void deleteFolder(DataPath folder);

        /** 在系统资源管理器中打开指定文件夹。 */
        public void openFolder(DataPath folder);

        /** 重新请求影片列表。 */
        public void refresh();
    }
}
