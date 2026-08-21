package gbeic.bbsplusplus.mixin;

import gbeic.bbsplusplus.BBSAddonsSettings;
import gbeic.bbsplusplus.client.ui.film.FilmLibraryDefaultLocation;
import gbeic.bbsplusplus.client.ui.film.IFilmLibraryLayoutToggle;
import gbeic.bbsplusplus.client.ui.film.IFilmLibraryPathList;
import gbeic.bbsplusplus.client.ui.film.IFilmLibrarySearchBox;
import gbeic.bbsplusplus.client.ui.film.UIAllFilmsNavItem;
import gbeic.bbsplusplus.client.ui.film.UIFilmLibraryFolderTree;
import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.graphics.texture.Texture;
import mchorse.bbs_mod.graphics.window.Window;
import mchorse.bbs_mod.l10n.L10n;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.dashboard.list.UIDataPathList;
import mchorse.bbs_mod.ui.dashboard.panels.UIDataDashboardPanel;
import mchorse.bbs_mod.ui.dashboard.panels.UISelectionScreen;
import mchorse.bbs_mod.ui.film.UIFilmSelectionPanel;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIButton;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIIcon;
import mchorse.bbs_mod.ui.framework.elements.input.list.UISearchList;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIConfirmOverlayPanel;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlay;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIPromptOverlayPanel;
import mchorse.bbs_mod.ui.framework.elements.utils.UILabel;
import mchorse.bbs_mod.ui.framework.elements.utils.UIRenderable;
import mchorse.bbs_mod.ui.utils.Area;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.ui.utils.UIUtils;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.ui.utils.keys.Keybind;
import mchorse.bbs_mod.ui.utils.keys.KeyCombo;
import mchorse.bbs_mod.utils.DataPath;
import mchorse.bbs_mod.utils.colors.Colors;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.io.File;
import java.util.List;

/**
 * 将影片选择器从居中小卡片调整为影片库工作区。
 * <p>
 * 第一阶段只重排现有选择器：保留原本的新建、复制、重命名、删除、搜索、双击打开和键盘行为，
 * 额外提供左侧导航占位和主列表大区域，避免过早改动影片数据格式。
 * </p>
 */
@Mixin(UISelectionScreen.class)
public abstract class UIFilmSelectionPanelLibraryLayoutMixin implements IFilmLibraryLayoutToggle
{
    @Unique private static final int bbspp$PRIMARY_BUTTON_WIDTH = 112;
    @Unique private static final int bbspp$ORIGINAL_CARD_W = 420;
    @Unique private static final int bbspp$ORIGINAL_CARD_H = 420;
    @Unique private static final int bbspp$ORIGINAL_BANNER_H = 150;
    @Unique private static final int bbspp$ORIGINAL_HEADER_H = 16;
    @Unique private static final int bbspp$ORIGINAL_PADDING = 10;
    @Unique private static final int bbspp$ORIGINAL_HEADER_MARGIN = 6;

    @Shadow @Final protected UIDataDashboardPanel<?> panel;
    @Shadow @Final protected UIElement card;
    @Shadow @Final protected UIElement banner;
    @Shadow @Final protected UIElement header;
    @Shadow @Final protected UIElement listWrap;
    @Shadow @Final protected UIIcon add;
    @Shadow @Final protected UIIcon dupe;
    @Shadow @Final protected UIIcon rename;
    @Shadow @Final protected UIIcon remove;
    @Shadow @Final protected UISearchList<DataPath> names;
    @Shadow @Final protected UIDataPathList namesList;

    @Unique private UIButton bbspp$sortButton;
    @Unique private UIButton bbspp$newFilmButton;
    @Unique private boolean bbspp$filmLibraryLayoutApplied;
    @Unique private Keybind bbspp$filmLibrarySearchKeybind;

    @Invoker("addNewFolder")
    protected abstract void bbspp$invokeAddNewFolder();

    @Invoker("dupeSelected")
    protected abstract void bbspp$invokeDupeSelected();

    @Invoker("copy")
    protected abstract void bbspp$invokeCopy();

    @Invoker("paste")
    protected abstract void bbspp$invokePaste(MapType data);

    @Invoker("renameSelected")
    protected abstract void bbspp$invokeRenameSelected();

    @Invoker("removeSelected")
    protected abstract void bbspp$invokeRemoveSelected();

    @Shadow
    protected abstract Link getBannerTexture();

    /**
     * 注入目标：{@link UISelectionScreen} 构造方法结束处。
     * 注入原因：原版影片选择界面是固定尺寸 CRUD 卡片，屏幕利用率和选中反馈都不足。
     * 修改后的行为：仅当实际实例为 {@link UIFilmSelectionPanel} 时，改成左侧导航加主列表的影片库布局。
     */
    @Inject(method = "<init>", at = @At("RETURN"), remap = false)
    private void bbspp$setupFilmLibraryLayout(UIDataDashboardPanel<?> panel, CallbackInfo ci)
    {
        this.bbspp$syncFilmLibraryLayout();
    }

    /**
     * 注入目标：{@link UISelectionScreen#setVisible(boolean)} 开始处。
     * 注入原因：影片选择界面实例可能早于用户打开设置开关就已创建，只在构造期判断会导致开关不能即时生效。
     * 修改后的行为：每次显示影片选择界面前补查一次开关，开启后立即套用新版影片库布局。
     */
    @Inject(method = "setVisible", at = @At("HEAD"), remap = false)
    private void bbspp$setupFilmLibraryLayoutWhenShown(boolean visible, CallbackInfo ci)
    {
        if (visible)
        {
            this.bbspp$syncFilmLibraryLayout();
        }
    }

    @Override
    public void bbspp$syncFilmLibraryLayout()
    {
        if (!((Object) this instanceof UIFilmSelectionPanel))
        {
            return;
        }

        boolean enabled = bbspp$isNewFilmLibraryUiEnabled();

        if (enabled)
        {
            this.bbspp$applyFilmLibraryLayoutIfNeeded();
        }
        else if (this.bbspp$filmLibraryLayoutApplied)
        {
            this.bbspp$restoreOriginalFilmLibraryLayout();
        }
    }

    @Unique
    private void bbspp$applyFilmLibraryLayoutIfNeeded()
    {
        if (this.bbspp$filmLibraryLayoutApplied)
        {
            return;
        }

        this.bbspp$filmLibraryLayoutApplied = true;
        this.bbspp$layoutExistingElements();
        this.bbspp$addLibraryPanels();
    }

    @Unique
    private static boolean bbspp$isNewFilmLibraryUiEnabled()
    {
        return BBSAddonsSettings.newFilmLibraryUi != null && BBSAddonsSettings.newFilmLibraryUi.get();
    }

    @Unique
    private void bbspp$layoutExistingElements()
    {
        this.card.relative((UIElement) (Object) this).xy(0, 0).wh(1F, 1F).minW(0).minH(0).maxW(0).maxH(0).anchor(0F, 0F);
        this.banner.setVisible(false);

        this.namesList.scroll.scrollItemSize = 20;

        this.add.tooltip(L10n.lang("bbspp.ui.film.library.new_film"));
        this.dupe.tooltip(L10n.lang("bbspp.ui.film.library.duplicate"));
        this.rename.tooltip(L10n.lang("bbspp.ui.film.library.rename"));
        this.remove.tooltip(L10n.lang("bbspp.ui.film.library.remove"));
        this.dupe.wh(0, 20);
        this.rename.wh(0, 20);
        this.remove.wh(0, 20);
        this.dupe.setVisible(false);
        this.rename.setVisible(false);
        this.remove.setVisible(false);

        if (this.namesList instanceof IFilmLibraryPathList pathList)
        {
            pathList.bbspp$setFilmLibraryMode(true);
            pathList.bbspp$setFilmLibrarySortDescending(this.bbspp$isSortDescending());
            this.bbspp$rememberPhysicalFilmLibraryFolders(pathList);
            this.names.search.callback = pathList::bbspp$filterFilmLibrary;
            this.names.label(L10n.lang("bbspp.ui.film.library.search"));
            this.names.search.border().background(true);
        }

        if (this.names.search instanceof IFilmLibrarySearchBox searchBox)
        {
            searchBox.bbspp$setFilmLibrarySearchBox(true);
        }

        this.bbspp$setupFilmLibraryActions();

        // 移除原版卡片背景渲染，避免隐藏 banner 后仍残留横向高光条。
        this.card.removeAll();
        this.listWrap.removeAll();
        this.names.remove(this.names.search);
        this.names.remove(this.namesList);
    }

    @Unique
    private void bbspp$setupFilmLibraryActions()
    {
        this.namesList.resetContext();
        this.namesList.context((menu) ->
        {
            DataPath selected = this.namesList.getCurrentFirst();
            List<DataPath> selectedItems = this.namesList.getCurrent();
            boolean single = selectedItems.size() == 1;

            menu.action(Icons.ADD, UIKeys.GENERAL_ADD, this::bbspp$createFilmAtCurrentLocation);

            try
            {
                MapType data = Window.getClipboardMap("_ContentType_" + this.panel.getType().getId());

                if (data != null)
                {
                    menu.action(Icons.PASTE, UIKeys.PANELS_CONTEXT_PASTE, () -> this.bbspp$invokePaste(data));
                }
            }
            catch (Exception e)
            {}

            if (selected != null && !selected.folder)
            {
                if (single)
                {
                    menu.action(Icons.EDIT, UIKeys.GENERAL_RENAME, this::bbspp$invokeRenameSelected);
                }

                menu.action(Icons.DUPE, UIKeys.GENERAL_DUPE, this::bbspp$invokeDupeSelected);

                if (single)
                {
                    menu.action(Icons.COPY, UIKeys.PANELS_CONTEXT_COPY, this::bbspp$invokeCopy);
                }

                menu.action(Icons.REMOVE, UIKeys.GENERAL_REMOVE, this::bbspp$invokeRemoveSelected);
            }

            File folder = this.panel.getType().getRepository().getFolder();

            if (folder != null)
            {
                menu.action(Icons.FOLDER, UIKeys.PANELS_CONTEXT_OPEN, () ->
                {
                    DataPath target = selected != null && !selected.folder ? selected.getParent() : this.namesList.getPath();

                    UIUtils.openFolder(new File(folder, target.toString()));
                });
            }
        });

        if (this.bbspp$filmLibrarySearchKeybind == null)
        {
            this.bbspp$filmLibrarySearchKeybind = ((UIElement) (Object) this).keys().register(new KeyCombo(UIKeys.GENERAL_SEARCH, GLFW.GLFW_KEY_F, GLFW.GLFW_KEY_LEFT_CONTROL), () ->
            {
                UIContext context = this.names.search.getContext();

                if (context != null)
                {
                    context.focus(this.names.search);
                }
            }).active(() -> this.bbspp$filmLibraryLayoutApplied);
        }
    }

    @Unique
    private void bbspp$addLibraryPanels()
    {
        UIElement workspace = new UIElement();
        workspace.relative(this.card).xy(10, 10).w(1F, -20).h(1F, -20).row(10);

        UIElement sidebar = new UIElement();
        sidebar.w(190).h(1F);
        sidebar.add(new UIRenderable((ctx) -> this.bbspp$renderPanel(ctx, sidebar.area)));

        UILabel browse = this.bbspp$section("bbspp.ui.film.library.browse");
        UILabel folders = this.bbspp$section("bbspp.ui.film.library.folders");

        browse.relative(sidebar).xy(10, 10).w(1F, -20).h(14);
        folders.relative(sidebar).xy(10, 58).w(1F, -20).h(14);

        UIAllFilmsNavItem all = new UIAllFilmsNavItem(L10n.lang("bbspp.ui.film.library.all_films"), this.names.search, this.namesList);
        UIFilmLibraryFolderTree folderTree = new UIFilmLibraryFolderTree(this.names.search, this.namesList, new UIFilmLibraryFolderTree.Actions()
        {
            @Override
            public void createFilm(DataPath folder)
            {
                bbspp$switchToFolder(folder);
                bbspp$createFilmAtCurrentLocation();
            }

            @Override
            public void createFolder(DataPath folder)
            {
                bbspp$switchToFolder(folder);
                bbspp$invokeAddNewFolder();
            }

            @Override
            public void renameFolder(DataPath folder)
            {
                bbspp$renameFolder(folder);
            }

            @Override
            public void deleteFolder(DataPath folder)
            {
                bbspp$deleteFolder(folder);
            }

            @Override
            public void openFolder(DataPath folder)
            {
                bbspp$openSystemFolder(folder);
            }

            @Override
            public void refresh()
            {
                if (namesList instanceof IFilmLibraryPathList pathList)
                {
                    bbspp$rememberPhysicalFilmLibraryFolders(pathList);
                }

                panel.requestNames();
            }
        });

        all.relative(sidebar).xy(8, 30).w(1F, -16).h(20);
        folderTree.relative(sidebar).xy(8, 78).w(1F, -16).h(1F, -86);

        sidebar.add(browse, all, folders, folderTree);

        UIElement content = new UIElement();
        content.h(1F);
        content.add(new UIRenderable((ctx) -> this.bbspp$renderPanel(ctx, content.area)));

        this.bbspp$sortButton = new UIButton(this.bbspp$getSortLabel(), (b) -> this.bbspp$toggleSortMode());
        this.bbspp$newFilmButton = new UIButton(L10n.lang("bbspp.ui.film.library.new_film_button"), (b) -> this.bbspp$createFilmAtCurrentLocation());

        this.bbspp$newFilmButton.relative(content).x(1F, -bbspp$PRIMARY_BUTTON_WIDTH).y(0).w(bbspp$PRIMARY_BUTTON_WIDTH).h(20);
        this.bbspp$sortButton.relative(content).x(1F, -(bbspp$PRIMARY_BUTTON_WIDTH * 2 + 10)).y(0).w(bbspp$PRIMARY_BUTTON_WIDTH).h(20);
        this.names.search.relative(content).xy(0, 0).w(1F, -(bbspp$PRIMARY_BUTTON_WIDTH * 2 + 20)).h(20);
        this.listWrap.relative(content).xy(0, 22).w(1F).h(1F, -22);
        this.namesList.relative(this.listWrap).xy(0, 0).wh(1F, 1F);
        this.listWrap.add(this.namesList);
        content.add(this.names.search, this.bbspp$sortButton, this.bbspp$newFilmButton, this.listWrap);

        workspace.add(sidebar, content);
        this.card.add(workspace);

        this.bbspp$openDefaultFilmLibraryLocation();
        this.panel.requestNames();
        ((UIElement) (Object) this).resize();
    }

    @Unique
    private void bbspp$restoreOriginalFilmLibraryLayout()
    {
        this.bbspp$filmLibraryLayoutApplied = false;

        if (this.namesList instanceof IFilmLibraryPathList pathList)
        {
            pathList.bbspp$setFilmLibraryMode(false);
        }

        if (this.names.search instanceof IFilmLibrarySearchBox searchBox)
        {
            searchBox.bbspp$setFilmLibrarySearchBox(false);
        }

        this.names.search.callback = (str) -> this.names.filter(str, false);
        this.names.label(UIKeys.GENERAL_SEARCH);
        this.names.search.border().background(false);

        this.banner.setVisible(true);
        this.add.wh(20, 20);
        this.dupe.wh(20, 20);
        this.rename.wh(20, 20);
        this.remove.wh(20, 20);
        this.add.setVisible(true);
        this.dupe.setVisible(true);
        this.rename.setVisible(true);
        this.remove.setVisible(true);

        this.bbspp$setupOriginalContextMenu();

        this.card.removeAll();
        this.listWrap.removeAll();
        this.names.removeAll();

        this.card.relative((UIElement) (Object) this).xy(0.5F, 0.5F).wh(bbspp$ORIGINAL_CARD_W, bbspp$ORIGINAL_CARD_H).minW(0).minH(0).maxW(0).maxH(0).anchor(0.5F);
        this.banner.relative(this.card).xy(0, 0).w(1F).h(bbspp$ORIGINAL_BANNER_H);
        this.header.relative(this.card).xy(bbspp$ORIGINAL_PADDING, bbspp$ORIGINAL_BANNER_H + bbspp$ORIGINAL_HEADER_MARGIN).w(1F, -bbspp$ORIGINAL_PADDING * 2).h(bbspp$ORIGINAL_HEADER_H);
        this.listWrap.relative(this.card)
            .xy(bbspp$ORIGINAL_PADDING, bbspp$ORIGINAL_BANNER_H + bbspp$ORIGINAL_HEADER_H + bbspp$ORIGINAL_HEADER_MARGIN * 2)
            .w(1F, -bbspp$ORIGINAL_PADDING * 2)
            .h(1F, -(bbspp$ORIGINAL_BANNER_H + bbspp$ORIGINAL_HEADER_H + bbspp$ORIGINAL_HEADER_MARGIN * 2 + bbspp$ORIGINAL_PADDING));

        this.names.full(this.listWrap);
        this.names.search.relative(this.names).set(0, 0, 0, 20).w(1, 0);
        this.namesList.relative(this.names).set(0, 20, 0, 0).w(1, 0).h(1, -20);
        this.namesList.scroll.scrollItemSize = 16;

        this.names.add(this.names.search, this.namesList);
        this.listWrap.add(new UIRenderable((ctx) -> this.bbspp$renderOriginalListBackground(ctx, this.listWrap.area)), this.names);
        this.banner.add(new UIRenderable((ctx) -> this.bbspp$renderOriginalBanner(ctx, this.banner.area)));
        this.card.add(new UIRenderable((ctx) -> this.bbspp$renderOriginalCard(ctx, this.card.area)), this.banner, this.header, this.listWrap);

        this.names.filter("", true);
        this.namesList.deselect();
        this.panel.requestNames();
        ((UIElement) (Object) this).resize();
    }

    @Unique
    private void bbspp$setupOriginalContextMenu()
    {
        this.namesList.resetContext();
        this.namesList.context((menu) ->
        {
            try
            {
                MapType data = Window.getClipboardMap("_ContentType_" + this.panel.getType().getId());

                if (data != null)
                {
                    menu.action(Icons.PASTE, UIKeys.PANELS_CONTEXT_PASTE, () -> this.bbspp$invokePaste(data));
                }
            }
            catch (Exception e)
            {}

            menu.action(Icons.ADD, UIKeys.GENERAL_ADD, this.add::clickItself);
            menu.action(Icons.FOLDER, UIKeys.PANELS_MODALS_ADD_FOLDER_TITLE, this::bbspp$invokeAddNewFolder);
            menu.action(Icons.EDIT, UIKeys.GENERAL_RENAME, this::bbspp$invokeRenameSelected);
            menu.action(Icons.DUPE, UIKeys.GENERAL_DUPE, this::bbspp$invokeDupeSelected);
            menu.action(Icons.REMOVE, UIKeys.GENERAL_REMOVE, this::bbspp$invokeRemoveSelected);

            DataPath selected = this.namesList.getCurrentFirst();

            if (selected != null && !selected.folder && this.namesList.getCurrent().size() == 1)
            {
                menu.action(Icons.COPY, UIKeys.PANELS_CONTEXT_COPY, this::bbspp$invokeCopy);
            }

            File folder = this.panel.getType().getRepository().getFolder();

            if (folder != null)
            {
                menu.action(Icons.FOLDER, UIKeys.PANELS_CONTEXT_OPEN, () -> UIUtils.openFolder(new File(folder, this.namesList.getPath().toString())));
            }
        });
    }

    @Unique
    private void bbspp$createFilmAtCurrentLocation()
    {
        this.names.search.setText("");
        this.add.clickItself();
    }

    /**
     * 注入目标：{@link UISelectionScreen#addNewData(String, MapType)} 开始处。
     * 注入原因：新版影片库创建后应回到创建位置并选中新影片，避免用户在搜索/全部影片视图中丢失反馈。
     * 修改后的行为：记录即将创建的影片 ID，待仓库刷新后由影片库列表自动选中。
     */
    @Inject(method = "addNewData(Ljava/lang/String;Lmchorse/bbs_mod/data/types/MapType;)V", at = @At("HEAD"), cancellable = true, remap = false)
    private void bbspp$selectCreatedFilmAfterRefresh(String name, MapType mapType, CallbackInfo ci)
    {
        if (!(((Object) this instanceof UIFilmSelectionPanel)
            && this.namesList instanceof IFilmLibraryPathList pathList
            && pathList.bbspp$isFilmLibraryMode()))
        {
            return;
        }

        String current = this.namesList.getPath().toString();

        if (name == null || name.trim().isEmpty() || (!current.isEmpty() && current.equals(name)))
        {
            ((UIElement) (Object) this).getContext().notifyError(UIKeys.PANELS_MODALS_EMPTY);
            ci.cancel();

            return;
        }

        if (!this.namesList.hasInHierarchy(name))
        {
            pathList.bbspp$selectFilmLibraryAfterRefresh(name);
        }
    }

    /**
     * 注入目标：{@code UISelectionScreen#renameData(String, String)} 开始处。
     * 注入原因：新版“全部影片”视图的列表路径是根目录，直接复用原版重命名会把子文件夹中的影片移到根目录。
     * 修改后的行为：仅在全部影片视图重命名子文件夹影片时拦截，保留原父目录，只替换文件名。
     */
    @Inject(method = "renameData(Ljava/lang/String;Ljava/lang/String;)V", at = @At("HEAD"), cancellable = true, remap = false)
    private void bbspp$renameFilmInsideOriginalFolder(String from, String to, CallbackInfo ci)
    {
        if (!(((Object) this instanceof UIFilmSelectionPanel)
            && this.namesList instanceof IFilmLibraryPathList pathList
            && pathList.bbspp$isFilmLibraryMode()
            && pathList.bbspp$isShowingAllFilmLibraryFilms()))
        {
            return;
        }

        DataPath source = new DataPath(from);
        DataPath parent = source.getParent();

        if (parent.strings.isEmpty())
        {
            return;
        }

        if (to.trim().isEmpty())
        {
            ((UIElement) (Object) this).getContext().notifyError(UIKeys.PANELS_MODALS_EMPTY);
            ci.cancel();

            return;
        }

        String name = new DataPath(to).getLast();
        String target = parent + "/" + name;

        if (this.namesList.hasInHierarchy(target))
        {
            ci.cancel();

            return;
        }

        this.panel.getType().getRepository().rename(from, target);

        if (this.panel.getData() != null && from.equals(this.panel.getData().getId()))
        {
            this.panel.getData().setId(target);
        }

        this.panel.onDataRenamed(from, target);
        this.panel.requestNames();
        ci.cancel();
    }

    /**
     * 注入目标：{@code UISelectionScreen#dupeSelected(String, String)} 开始处。
     * 注入原因：在“全部影片”中复制子文件夹影片时，原版目标路径会从根目录计算，导致副本跑到根目录。
     * 修改后的行为：仅新版影片库的全部影片视图中拦截子文件夹影片复制，保留原父目录。
     */
    @Inject(method = "dupeSelected(Ljava/lang/String;Ljava/lang/String;)V", at = @At("HEAD"), cancellable = true, remap = false)
    private void bbspp$dupeFilmInsideOriginalFolder(String from, String to, CallbackInfo ci)
    {
        if (!(((Object) this instanceof UIFilmSelectionPanel)
            && this.namesList instanceof IFilmLibraryPathList pathList
            && pathList.bbspp$isFilmLibraryMode()
            && pathList.bbspp$isShowingAllFilmLibraryFilms()))
        {
            return;
        }

        DataPath source = new DataPath(from);
        DataPath parent = source.getParent();

        if (parent.strings.isEmpty())
        {
            return;
        }

        if (to.trim().isEmpty())
        {
            ((UIElement) (Object) this).getContext().notifyError(UIKeys.PANELS_MODALS_EMPTY);
            ci.cancel();

            return;
        }

        String name = new DataPath(to).getLast();
        String target = parent + "/" + name;

        if (this.namesList.hasInHierarchy(target))
        {
            ci.cancel();

            return;
        }

        this.panel.getType().getRepository().load(from, (data) ->
        {
            if (data != null)
            {
                this.panel.getType().getRepository().save(target, data.toData().asMap());
            }

            this.panel.requestNames();
        });

        ci.cancel();
    }

    @Unique
    private void bbspp$switchToFolder(DataPath folder)
    {
        if (this.namesList instanceof IFilmLibraryPathList pathList)
        {
            this.names.search.setText("");
            pathList.bbspp$showFilmLibraryFolder(folder == null ? DataPath.EMPTY : folder);
        }
    }

    @Unique
    private void bbspp$openDefaultFilmLibraryLocation()
    {
        if (!(this.namesList instanceof IFilmLibraryPathList pathList))
        {
            return;
        }

        this.names.search.setText("");

        if (FilmLibraryDefaultLocation.isAllFilms())
        {
            pathList.bbspp$showAllFilmLibraryFilms();

            return;
        }

        DataPath folder = FilmLibraryDefaultLocation.getFolder();

        if (folder == null)
        {
            pathList.bbspp$showAllFilmLibraryFilms();

            return;
        }

        if (!folder.strings.isEmpty() && !pathList.bbspp$getFilmLibraryFolders().contains(folder))
        {
            // 默认文件夹暂时找不到（例如资源尚未加载完）时仅本次回退到全部影片，
            // 不覆盖持久化的默认打开设置，避免用户设置被悄悄抹掉。
            pathList.bbspp$showAllFilmLibraryFilms();

            return;
        }

        pathList.bbspp$showFilmLibraryFolder(folder);
    }

    @Unique
    private void bbspp$renameFolder(DataPath folder)
    {
        if (folder == null || folder.strings.isEmpty())
        {
            return;
        }

        UIPromptOverlayPanel overlay = new UIPromptOverlayPanel(
            UIKeys.PANELS_MODALS_RENAME_FOLDER_TITLE,
            UIKeys.PANELS_MODALS_RENAME_FOLDER,
            (str) -> this.bbspp$renameFolder(folder, str)
        );

        overlay.text.setText(folder.getLast());
        overlay.text.filename();

        UIOverlay.addOverlay(((UIElement) (Object) this).getContext(), overlay);
    }

    @Unique
    private void bbspp$renameFolder(DataPath folder, String name)
    {
        if (name.trim().isEmpty())
        {
            ((UIElement) (Object) this).getContext().notifyError(UIKeys.PANELS_MODALS_EMPTY);

            return;
        }

        String from = folder.toString();

        this.panel.getType().getRepository().renameFolder(from, name, (bool) ->
        {
            if (!bool)
            {
                return;
            }

            if (this.panel.getData() != null)
            {
                String id = this.panel.getData().getId();

                if (id != null && id.startsWith(from + "/"))
                {
                    this.panel.getData().setId(this.bbspp$getRenamedFolderPath(folder, name) + "/" + id.substring((from + "/").length()));
                }
            }

            this.panel.onDataFolderRenamed(from, name);

            if (this.namesList instanceof IFilmLibraryPathList pathList)
            {
                pathList.bbspp$renameKnownFilmLibraryFolder(folder, name);

                DataPath current = pathList.bbspp$getFilmLibraryFolder();

                if (!pathList.bbspp$isShowingAllFilmLibraryFilms() && current.startsWith(folder))
                {
                    pathList.bbspp$showFilmLibraryFolder(new DataPath(this.bbspp$getPathAfterFolderRename(current, folder, name)));
                }
            }

            this.panel.requestNames();
        });
    }

    @Unique
    private String bbspp$getRenamedFolderPath(DataPath folder, String name)
    {
        DataPath parent = folder.getParent();

        return parent.strings.isEmpty() ? name : parent + "/" + name;
    }

    @Unique
    private String bbspp$getPathAfterFolderRename(DataPath path, DataPath folder, String name)
    {
        String from = folder.toString();
        String renamed = this.bbspp$getRenamedFolderPath(folder, name);
        String current = path.toString();

        if (current.equals(from))
        {
            return renamed;
        }

        return renamed + "/" + current.substring((from + "/").length());
    }

    @Unique
    private void bbspp$deleteFolder(DataPath folder)
    {
        if (folder == null || folder.strings.isEmpty())
        {
            return;
        }

        UIConfirmOverlayPanel overlay = new UIConfirmOverlayPanel(
            UIKeys.PANELS_MODALS_REMOVE_FOLDER_TITLE,
            UIKeys.PANELS_MODALS_REMOVE_FOLDER,
            (confirm) ->
            {
                if (confirm)
                {
                    this.bbspp$deleteFolderNow(folder);
                }
            }
        );

        UIOverlay.addOverlay(((UIElement) (Object) this).getContext(), overlay);
    }

    @Unique
    private void bbspp$deleteFolderNow(DataPath folder)
    {
        String path = folder.toString();

        this.panel.getType().getRepository().deleteFolder(path, (bool) ->
        {
            if (!bool)
            {
                ((UIElement) (Object) this).getContext().notifyError(L10n.lang("bbspp.ui.film.library.folder_remove_failed_hidden"));

                return;
            }

            this.panel.onDataFolderRemoved(path);

            if (this.namesList instanceof IFilmLibraryPathList pathList)
            {
                pathList.bbspp$forgetFilmLibraryFolder(folder);

                DataPath current = pathList.bbspp$getFilmLibraryFolder();

                if (!pathList.bbspp$isShowingAllFilmLibraryFilms() && current.startsWith(folder))
                {
                    pathList.bbspp$showFilmLibraryFolder(folder.getParent());
                }
            }

            this.panel.requestNames();
        });
    }

    @Unique
    private void bbspp$openSystemFolder(DataPath path)
    {
        File folder = this.panel.getType().getRepository().getFolder();

        if (folder != null)
        {
            UIUtils.openFolder(new File(folder, path == null ? "" : path.toString()));
        }
    }

    @Unique
    private void bbspp$rememberPhysicalFilmLibraryFolders(IFilmLibraryPathList pathList)
    {
        File folder = this.panel.getType().getRepository().getFolder();

        if (folder != null && folder.isDirectory())
        {
            this.bbspp$rememberPhysicalFilmLibraryFolders(pathList, folder, DataPath.EMPTY);
        }
    }

    @Unique
    private void bbspp$rememberPhysicalFilmLibraryFolders(IFilmLibraryPathList pathList, File folder, DataPath path)
    {
        File[] files = folder.listFiles();

        if (files == null)
        {
            return;
        }

        for (File file : files)
        {
            if (!file.isDirectory() || file.getName().startsWith("_"))
            {
                continue;
            }

            DataPath child = path.getChild(file.getName());

            child.folder = true;
            pathList.bbspp$rememberFilmLibraryFolder(child);
            this.bbspp$rememberPhysicalFilmLibraryFolders(pathList, file, child);
        }
    }

    @Unique
    private UILabel bbspp$section(String key)
    {
        return this.bbspp$label(key, Colors.LIGHTER_GRAY);
    }

    @Unique
    private UILabel bbspp$label(String key, int color)
    {
        UILabel label = UI.label(L10n.lang(key), 12, color);

        label.labelAnchor(0F, 0.5F);

        return label;
    }

    @Unique
    private void bbspp$renderPanel(UIContext context, Area area)
    {
        int bg = BBSSettings.baseSurface();
        int border = BBSSettings.color(BBSSettings.dividerColor(), Colors.A25);

        context.batcher.box(area.x, area.y, area.ex(), area.ey(), bg);
        context.batcher.outline(area.x, area.y, area.ex(), area.ey(), border);
    }

    @Unique
    private void bbspp$renderOriginalCard(UIContext context, Area area)
    {
        int bg = BBSSettings.raisedSurface();
        int border = BBSSettings.color(BBSSettings.dividerColor(), Colors.A12);
        int accent = BBSSettings.primaryColor.get();

        context.batcher.dropShadow(area.x, area.y, area.ex(), area.ey(), 14, Colors.A50, 0);
        context.batcher.box(area.x, area.y, area.ex(), area.ey(), bg);
        context.batcher.outline(area.x, area.y, area.ex(), area.ey(), border);

        int sepY = area.y + bbspp$ORIGINAL_BANNER_H;
        int mid = area.mx();

        context.batcher.gradientHBox(area.x, sepY, mid, sepY + 2, Colors.setA(accent, 0F), Colors.A100 | accent);
        context.batcher.gradientHBox(mid, sepY, area.ex(), sepY + 2, Colors.A100 | accent, Colors.setA(accent, 0F));
        context.batcher.gradientVBox(area.x, sepY + 2, area.ex(), sepY + 48, Colors.A25 | accent, 0);
        context.batcher.gradientVBox(area.x, sepY + 2, area.ex(), sepY + 18, Colors.A50 | accent, 0);
    }

    @Unique
    private void bbspp$renderOriginalListBackground(UIContext context, Area area)
    {
        context.batcher.box(area.x, area.y, area.ex(), area.ey(), BBSSettings.baseSurface());
    }

    @Unique
    private void bbspp$renderOriginalBanner(UIContext context, Area area)
    {
        Link bannerLink = this.getBannerTexture();

        if (bannerLink == null)
        {
            return;
        }

        Texture texture = BBSModClient.getTextures().getTexture(bannerLink);

        if (texture == null)
        {
            return;
        }

        float texW = texture.width;
        float texH = texture.height;
        float areaW = area.w;
        float areaH = area.h;
        float texAspect = texW / texH;
        float areaAspect = areaW / areaH;
        float u1;
        float u2;
        float v1;
        float v2;

        if (areaAspect > texAspect)
        {
            float cropH = texW / areaAspect;

            u1 = 0;
            u2 = texW;
            v1 = (texH - cropH) * 0.5F;
            v2 = v1 + cropH;
        }
        else
        {
            float cropW = texH * areaAspect;

            u1 = (texW - cropW) * 0.5F;
            u2 = u1 + cropW;
            v1 = 0;
            v2 = texH;
        }

        context.batcher.texturedBox(texture, Colors.WHITE, area.x, area.y, area.w, area.h, u1, v1, u2, v2, texture.width, texture.height);
    }

    @Unique
    private IKey bbspp$getSortLabel()
    {
        return this.bbspp$isSortDescending()
            ? L10n.lang("bbspp.ui.film.library.sort_name_desc")
            : L10n.lang("bbspp.ui.film.library.sort_name_asc");
    }

    @Unique
    private boolean bbspp$isSortDescending()
    {
        return BBSAddonsSettings.filmLibrarySortMode != null && BBSAddonsSettings.filmLibrarySortMode.get() == 1;
    }

    @Unique
    private void bbspp$toggleSortMode()
    {
        boolean descending = !this.bbspp$isSortDescending();

        if (BBSAddonsSettings.filmLibrarySortMode != null)
        {
            BBSAddonsSettings.filmLibrarySortMode.set(descending ? 1 : 0);
        }

        if (this.namesList instanceof IFilmLibraryPathList pathList)
        {
            pathList.bbspp$setFilmLibrarySortDescending(descending);
        }

        this.bbspp$sortButton.label = this.bbspp$getSortLabel();
    }

}
