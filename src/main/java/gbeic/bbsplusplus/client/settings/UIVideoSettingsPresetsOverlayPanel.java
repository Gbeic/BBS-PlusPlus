package gbeic.bbsplusplus.client.settings;

import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.l10n.L10n;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIIcon;
import mchorse.bbs_mod.ui.framework.elements.input.list.UIStringList;
import mchorse.bbs_mod.ui.framework.elements.input.list.UISearchList;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIConfirmOverlayPanel;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIListOverlayPanel;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlay;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIPromptOverlayPanel;
import mchorse.bbs_mod.ui.utils.UIUtils;
import mchorse.bbs_mod.ui.utils.context.ContextMenuManager;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.ui.utils.presets.UICopyPasteController;
import mchorse.bbs_mod.utils.Direction;
import mchorse.bbs_mod.utils.presets.PresetManager;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * BBS++ 视频设置专用预设界面。
 * <p>
 * 在复用 BBS 预设保存、选择布局的基础上增加刷新按钮，并为预设文件提供
 * 右键重命名和删除操作；该界面只由 BBS++ 视频预设按钮打开，不影响 BBS
 * 其它类型的预设列表。
 * </p>
 */
public class UIVideoSettingsPresetsOverlayPanel extends UIListOverlayPanel
{
    private String cwd = "";
    private final UICopyPasteController controller;
    private final int mouseX;
    private final int mouseY;

    public UIVideoSettingsPresetsOverlayPanel(UICopyPasteController controller, int mouseX, int mouseY)
    {
        super(UIKeys.PRESETS_TITLE, null);

        this.controller = controller;
        this.mouseX = mouseX;
        this.mouseY = mouseY;

        this.list.removeFromParent();
        this.list = new UISearchList<>(new VideoPresetList(this::select, () -> this.cwd, this::renamePreset, this::deletePreset));
        this.list.relative(this.content).xy(6, 6).w(1F, -12).h(1F, -6);
        this.content.add(this.list);

        this.refreshList();

        UIIcon refresh = new UIIcon(Icons.REFRESH, (b) -> this.refreshList());
        UIIcon save = new UIIcon(Icons.SAVED, (b) -> this.savePreset());
        UIIcon folder = new UIIcon(Icons.FOLDER, (b) -> UIUtils.openFolder(this.controller.manager.getFolder()));

        refresh.tooltip(L10n.lang("bbspp.ui.video_settings.presets.refresh"), Direction.LEFT);
        save.tooltip(UIKeys.PRESETS_SAVE, Direction.LEFT);
        folder.tooltip(UIKeys.PRESETS_OPEN, Direction.LEFT);
        save.setEnabled(controller.canCopy());
        this.icons.add(save, folder, refresh);
    }

    private void select(List<String> values)
    {
        if (values == null || values.isEmpty())
        {
            return;
        }

        String pick = values.get(0);

        if ("..".equals(pick))
        {
            this.goUp();
            this.refreshList();

            return;
        }

        if (pick.endsWith("/"))
        {
            this.cwd = PresetManager.joinRelative(this.cwd, pick.substring(0, pick.length() - 1));
            this.refreshList();

            return;
        }

        String id = PresetManager.joinRelative(this.cwd, pick);
        MapType load = this.controller.manager.load(id);

        if (load != null)
        {
            this.controller.getConsumer().paste(load, this.mouseX, this.mouseY);
            this.close();
        }
    }

    private void savePreset()
    {
        MapType type = this.controller.getSupplier().get();

        if (type == null)
        {
            return;
        }

        UIPromptOverlayPanel pane = new UIPromptOverlayPanel(
            UIKeys.PRESETS_SAVE_TITLE,
            UIKeys.PRESETS_SAVE_DESCRIPTION,
            (name) ->
            {
                String normalized = VideoSettingsPresets.normalizeName(name);

                if (!normalized.isEmpty())
                {
                    this.controller.manager.save(PresetManager.joinRelative(this.cwd, normalized), type);
                    this.refreshList();
                }
            }
        );

        pane.text.filename();
        UIOverlay.addOverlay(this.getContext(), pane);
    }

    private void renamePreset(String id, String currentName)
    {
        UIPromptOverlayPanel pane = new UIPromptOverlayPanel(
            L10n.lang("bbspp.ui.video_settings.presets.rename"),
            L10n.lang("bbspp.ui.video_settings.presets.rename_description"),
            (name) ->
            {
                String normalized = VideoSettingsPresets.normalizeName(name);

                if (!normalized.isEmpty())
                {
                    String target = PresetManager.joinRelative(this.cwd, normalized);

                    VideoSettingsPresets.rename(id, target);
                    this.refreshList();
                }
            }
        );

        pane.text.setText(currentName);
        pane.text.filename();
        UIOverlay.addOverlay(this.getContext(), pane);
    }

    private void deletePreset(String id, String currentName)
    {
        UIConfirmOverlayPanel pane = new UIConfirmOverlayPanel(
            L10n.lang("bbspp.ui.video_settings.presets.delete"),
            L10n.lang("bbspp.ui.video_settings.presets.delete_description").format(currentName),
            (confirmed) ->
            {
                if (confirmed)
                {
                    VideoSettingsPresets.delete(id);
                    this.refreshList();
                }
            }
        );

        UIOverlay.addOverlay(this.getContext(), pane);
    }

    private void goUp()
    {
        if (this.cwd.isEmpty())
        {
            return;
        }

        int i = this.cwd.lastIndexOf('/');
        this.cwd = i < 0 ? "" : this.cwd.substring(0, i);
    }

    private void refreshList()
    {
        String filter = this.list.search.getText();
        this.list.filter("", false);
        this.list.list.getList().clear();
        this.list.list.add(this.controller.manager.listDirectory(this.cwd));
        this.list.filter(filter, false);
    }

    private static class VideoPresetList extends UIStringList
    {
        private final Supplier<String> cwd;
        private final Consumer<NamedPresetAction> rename;
        private final Consumer<NamedPresetAction> delete;

        private VideoPresetList(
            Consumer<List<String>> callback,
            Supplier<String> cwd,
            BiConsumerWithName rename,
            BiConsumerWithName delete
        )
        {
            super(callback);
            this.cwd = cwd;
            this.rename = action -> rename.accept(action.id, action.name);
            this.delete = action -> delete.accept(action.id, action.name);
        }

        @Override
        public mchorse.bbs_mod.ui.framework.elements.context.UIContextMenu createContextMenu(UIContext context)
        {
            int index = this.getIndexAtCursor(context);

            if (!this.exists(index))
            {
                return null;
            }

            String name = this.getList().get(index);

            if (name == null || name.endsWith("/") || "..".equals(name))
            {
                return null;
            }

            String id = PresetManager.joinRelative(this.cwd.get(), name);
            ContextMenuManager menu = new ContextMenuManager();

            menu.action(Icons.EDIT, L10n.lang("bbspp.ui.video_settings.presets.rename"),
                () -> this.rename.accept(new NamedPresetAction(id, name)));
            menu.action(Icons.REMOVE, L10n.lang("bbspp.ui.video_settings.presets.delete"),
                () -> this.delete.accept(new NamedPresetAction(id, name)));

            return menu.create();
        }
    }

    private record NamedPresetAction(String id, String name)
    {}

    @FunctionalInterface
    private interface BiConsumerWithName
    {
        void accept(String id, String name);
    }
}
