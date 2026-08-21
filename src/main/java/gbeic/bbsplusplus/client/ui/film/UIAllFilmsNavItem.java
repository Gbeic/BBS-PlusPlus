package gbeic.bbsplusplus.client.ui.film;

import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.l10n.L10n;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.dashboard.list.UIDataPathList;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.input.text.UITextbox;
import mchorse.bbs_mod.ui.framework.elements.utils.FontRenderer;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.colors.Colors;

/**
 * 影片库左侧“全部影片”入口。
 * <p>
 * 这个控件只负责切换右侧主列表到全部影片视图，并清空搜索状态。它被拆出 Mixin，
 * 是为了避免 Mixin 内部类嵌套导致运行期转换失败。
 * </p>
 */
public class UIAllFilmsNavItem extends UIElement
{
    private final IKey label;
    private final UITextbox search;
    private final UIDataPathList list;

    public UIAllFilmsNavItem(IKey label, UITextbox search, UIDataPathList list)
    {
        this.label = label;
        this.search = search;
        this.list = list;

        this.context((menu) ->
        {
            if (this.list instanceof IFilmLibraryPathList pathList)
            {
                this.search.setText("");
                pathList.bbspp$showAllFilmLibraryFilms();
            }

            if (!FilmLibraryDefaultLocation.isAllFilms())
            {
                menu.action(Icons.BOOKMARK, L10n.lang("bbspp.ui.film.library.set_default_open"), FilmLibraryDefaultLocation::setAllFilms);
            }
        });
    }

    @Override
    protected boolean subMouseClicked(UIContext context)
    {
        if (context.mouseButton == 0 && this.area.isInside(context) && this.list instanceof IFilmLibraryPathList pathList)
        {
            this.search.setText("");
            pathList.bbspp$showAllFilmLibraryFilms();

            return true;
        }

        if (context.mouseButton == 1 && this.area.isInside(context) && this.list instanceof IFilmLibraryPathList pathList)
        {
            this.search.setText("");
            pathList.bbspp$showAllFilmLibraryFilms();
        }

        return false;
    }

    @Override
    public void render(UIContext context)
    {
        boolean active = this.list instanceof IFilmLibraryPathList pathList && pathList.bbspp$isShowingAllFilmLibraryFilms();

        if (active)
        {
            context.batcher.box(this.area.x, this.area.y, this.area.ex(), this.area.ey(), Colors.A25 | BBSSettings.primaryColor.get());
        }

        FontRenderer font = context.batcher.getFont();
        int color = active ? Colors.WHITE : Colors.GRAY;
        String text = font.limitToWidth(this.label.get(), this.area.w - 28);

        context.batcher.icon(Icons.FILM, color, this.area.x + 10, this.area.my(), 0.5F, 0.5F);
        context.batcher.textShadow(text, this.area.x + 24, this.area.my(font.getHeight()), color);

        super.render(context);
    }
}
