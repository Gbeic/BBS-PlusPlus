package gbeic.bbsplusplus.client.ui.curves;

import gbeic.bbsplusplus.client.compat.iris.SafeShaderLanguageMap;
import gbeic.bbsplusplus.client.compat.iris.ShaderCurveState;
import gbeic.bbsplusplus.compat.IrisCompat;
import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.camera.clips.misc.CurveClip;
import mchorse.bbs_mod.l10n.L10n;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.input.list.UIList;
import mchorse.bbs_mod.ui.framework.elements.input.text.UITextbox;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlayPanel;
import mchorse.bbs_mod.ui.framework.elements.utils.FontRenderer;
import mchorse.bbs_mod.ui.framework.elements.utils.UILabel;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.NaturalOrderComparator;
import mchorse.bbs_mod.utils.colors.Colors;
import mchorse.bbs_mod.utils.iris.ShaderCurves;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 光影曲线选择面板。
 * <p>
 * 原版 BBS 会把所有可动画光影参数平铺到一个搜索列表里，参数多时很难看出它们来自光影包的哪一页。
 * 这个面板复用 BBS 已经从 Iris 菜单中整理出的本地化路径，把当前可添加的曲线参数还原成“分类页 > 参数”的层级结构；
 * 搜索时仍然在全部参数里查找，避免层级导航牺牲原有的快速定位能力。
 * </p>
 */
public class UIShaderCurvePickerOverlayPanel extends UIOverlayPanel
{
    private static final String OPTION_PREFIX = "option.";
    private static final String PATH_SEPARATOR = " > ";

    private static final IKey BACK = L10n.lang("bbspp.ui.shader_curve_picker.back");
    private static final IKey BUILT_IN = L10n.lang("bbspp.ui.shader_curve_picker.built_in");
    private static final IKey UNCATEGORIZED = L10n.lang("bbspp.ui.shader_curve_picker.uncategorized");
    private static final IKey SEARCH = L10n.lang("bbspp.ui.shader_curve_picker.search");
    private static final IKey SUN_PATH_ROTATION = L10n.lang("bbs.ui.camera.panels.curves.sun_path_rotation");
    private static final IKey CENTER_DEPTH = L10n.lang("bbs.ui.camera.panels.curves.center_depth");

    private final TreeNode root;
    private final List<CurveEntry> allOptions;
    private final Set<String> selected;
    private final ToggleCallback callback;

    private TreeNode current;
    private UILabel path;
    private UITextbox search;
    private UIShaderCurveEntryList entries;

    public UIShaderCurvePickerOverlayPanel(Collection<String> existing, ToggleCallback callback)
    {
        super(UIKeys.CAMERA_PANELS_PICK_KEY);

        this.callback = callback;
        this.root = buildTree();
        this.allOptions = this.root.collectOptions();
        this.selected = existing == null ? new HashSet<>() : new HashSet<>(existing);
        this.current = this.root;

        this.path = UI.label(IKey.EMPTY, 14, Colors.GRAY);
        this.path.relative(this.content).xy(6, 2).w(1F, -12).h(14);

        this.search = new UITextbox(100, this::filter);
        this.search.placeholder(UIKeys.GENERAL_SEARCH);
        this.search.relative(this.content).xy(6, 18).w(1F, -12).h(16);

        this.entries = new UIShaderCurveEntryList((list) -> this.accept(list.get(0)));
        this.entries.background();
        this.entries.relative(this.content).xy(6, 38).w(1F, -12).h(1F, -38);

        this.content.add(this.path, this.search, this.entries);
        this.updateEntries();
    }

    public interface ToggleCallback
    {
        void accept(String channelId, boolean selected);
    }

    private static TreeNode buildTree()
    {
        TreeNode root = new TreeNode(null, "", UIKeys.CAMERA_PANELS_PICK_KEY.get());
        Map<String, String> languageMap = SafeShaderLanguageMap.collect();

        for (ShaderCurves.ShaderVariable variable : ShaderCurves.variableMap.values())
        {
            String channelId = CurveClip.SHADER_CURVES_PREFIX + variable.name;

            String path = languageMap.get(OPTION_PREFIX + variable.name);
            List<String> groups = new ArrayList<>();
            String title = variable.name;

            if (path != null && !path.isBlank())
            {
                List<String> parts = splitPath(path);

                if (!parts.isEmpty())
                {
                    title = parts.remove(parts.size() - 1);
                    groups.addAll(parts);
                }
            }

            if (groups.isEmpty())
            {
                groups.add(UNCATEGORIZED.get());
            }

            addOption(root, groups, title, channelId, variable.name);
        }

        List<String> builtIn = Collections.singletonList(BUILT_IN.get());

        addBuiltIn(root, builtIn, UIKeys.CAMERA_PANELS_CURVES_BRIGHTNESS, ShaderCurves.BRIGHTNESS);
        addBuiltIn(root, builtIn, UIKeys.CAMERA_PANELS_CURVES_SUN_ROTATION, ShaderCurves.SUN_ROTATION);
        addBuiltIn(root, builtIn, UIKeys.CAMERA_PANELS_CURVES_WEATHER, ShaderCurves.WEATHER);
        addBuiltIn(root, builtIn, UIKeys.CAMERA_PANELS_CURVES_CHROMA_SKY_COLOR, CurveClip.CHROMA_SKY_COLOR);

        /* 日月偏角与焦点直接注入 Iris，没装 Iris 时不该出现在列表里 */
        boolean hasIrisCurves = IrisCompat.isLoaded();

        if (hasIrisCurves && BBSSettings.shaderCurvesEnabled.get())
        {
            addBuiltIn(root, builtIn, SUN_PATH_ROTATION, ShaderCurveState.SUN_PATH_ROTATION);
        }

        if (hasIrisCurves)
        {
            addBuiltIn(root, builtIn, CENTER_DEPTH, ShaderCurveState.CENTER_DEPTH);
        }

        return root;
    }

    private static void addBuiltIn(TreeNode root, List<String> groups, IKey title, String channelId)
    {
        addOption(root, groups, title.get(), channelId, channelId);
    }

    private static void addOption(TreeNode root, List<String> groups, String title, String channelId, String optionId)
    {
        TreeNode node = root;

        for (String group : groups)
        {
            node = node.child(group);
        }

        node.options.add(new CurveEntry(title, channelId, optionId, node));
    }

    private static List<String> splitPath(String path)
    {
        List<String> parts = new ArrayList<>();
        int start = 0;
        int index;

        while ((index = path.indexOf(PATH_SEPARATOR, start)) != -1)
        {
            addPathPart(parts, path.substring(start, index));
            start = index + PATH_SEPARATOR.length();
        }

        addPathPart(parts, path.substring(start));

        return parts;
    }

    private static void addPathPart(List<String> parts, String part)
    {
        String trimmed = part.trim();

        if (!trimmed.isEmpty())
        {
            parts.add(trimmed);
        }
    }

    private void accept(CurveEntry entry)
    {
        if (entry.isBack())
        {
            this.goBack();

            return;
        }

        if (entry.node != null)
        {
            this.current = entry.node;
            this.search.setText("");
            this.updateEntries();

            return;
        }

        if (this.callback != null)
        {
            boolean selected = !this.isSelected(entry);

            this.callback.accept(entry.channelId, selected);

            if (selected)
            {
                this.selected.add(entry.channelId);
                this.selected.add(entry.optionId);
            }
            else
            {
                this.selected.remove(entry.channelId);
                this.selected.remove(entry.optionId);
            }
        }

        this.refreshAfterToggle();
    }

    private void refreshAfterToggle()
    {
        String query = this.search.textbox.getText();

        if (query == null || query.isBlank())
        {
            this.updateEntries();
        }
        else
        {
            this.filter(query);
        }
    }

    private void goBack()
    {
        if (!this.search.textbox.getText().isEmpty())
        {
            this.search.setText("");
            this.updateEntries();

            return;
        }

        if (this.current.parent != null)
        {
            this.current = this.current.compactParent();
            this.updateEntries();
        }
    }

    private void filter(String query)
    {
        if (query == null || query.isBlank())
        {
            this.updateEntries();

            return;
        }

        String lower = query.toLowerCase(Locale.ROOT);
        List<CurveEntry> results = new ArrayList<>();

        for (CurveEntry entry : this.allOptions)
        {
            if (entry.searchTextLower.contains(lower))
            {
                results.add(entry);
            }
        }

        results.sort(CurveEntry.COMPARATOR);
        results.add(0, CurveEntry.back());
        this.path.label = SEARCH.format(query);
        this.entries.setEntries(results);
    }

    private void updateEntries()
    {
        List<CurveEntry> entries = this.current.entries();

        if (this.current.parent != null)
        {
            entries.add(0, CurveEntry.back());
        }

        this.path.label = IKey.constant(this.current.path());
        this.entries.setEntries(entries);
    }

    private boolean isSelected(CurveEntry entry)
    {
        return !entry.isBack() && !entry.isNode() && (this.selected.contains(entry.channelId) || this.selected.contains(entry.optionId));
    }

    private static class TreeNode
    {
        private final TreeNode parent;
        private final String title;
        private final String path;
        private final Map<String, TreeNode> children = new LinkedHashMap<>();
        private final List<CurveEntry> options = new ArrayList<>();

        private TreeNode(TreeNode parent, String title, String path)
        {
            this.parent = parent;
            this.title = title;
            this.path = path;
        }

        private TreeNode child(String title)
        {
            return this.children.computeIfAbsent(title, (key) ->
            {
                String path = this.parent == null ? key : this.path + PATH_SEPARATOR + key;

                return new TreeNode(this, key, path);
            });
        }

        private List<CurveEntry> entries()
        {
            List<CurveEntry> entries = new ArrayList<>();

            for (TreeNode child : this.children.values())
            {
                entries.add(child.compactEntry());
            }

            entries.addAll(this.options);
            entries.sort(CurveEntry.COMPARATOR);

            return entries;
        }

        private CurveEntry compactEntry()
        {
            TreeNode target = this;
            List<String> titles = new ArrayList<>();

            titles.add(target.title);

            while (target.options.isEmpty() && target.children.size() == 1)
            {
                target = target.children.values().iterator().next();
                titles.add(target.title);
            }

            return CurveEntry.node(target, String.join(PATH_SEPARATOR, titles));
        }

        private TreeNode compactParent()
        {
            TreeNode target = this.parent;

            while (target != null && target.parent != null && target.options.isEmpty() && target.children.size() == 1)
            {
                target = target.parent;
            }

            return target;
        }

        private List<CurveEntry> collectOptions()
        {
            List<CurveEntry> entries = new ArrayList<>();

            this.collectOptions(entries);
            entries.sort(CurveEntry.COMPARATOR);

            return entries;
        }

        private void collectOptions(List<CurveEntry> entries)
        {
            entries.addAll(this.options);

            for (TreeNode child : this.children.values())
            {
                child.collectOptions(entries);
            }
        }

        private String path()
        {
            return this.path;
        }
    }

    private static class CurveEntry
    {
        private static final Comparator<CurveEntry> COMPARATOR = (a, b) ->
        {
            if (a.isNode() != b.isNode())
            {
                return a.isNode() ? -1 : 1;
            }

            return NaturalOrderComparator.compare(true, a.title, b.title);
        };

        private final String title;
        private final String channelId;
        private final String optionId;
        private final String fullPath;
        private final String searchText;
        private final String searchTextLower;
        private final TreeNode node;
        private final boolean back;

        private CurveEntry(String title, String channelId, String optionId, TreeNode parent)
        {
            this.title = title;
            this.channelId = channelId;
            this.optionId = optionId;
            this.fullPath = parent.path() + PATH_SEPARATOR + title;
            this.searchText = this.fullPath + " " + this.channelId + " " + this.optionId;
            this.searchTextLower = this.searchText.toLowerCase(Locale.ROOT);
            this.node = null;
            this.back = false;
        }

        private CurveEntry(TreeNode node, String title)
        {
            this.title = title;
            this.channelId = "";
            this.optionId = "";
            this.fullPath = node.path();
            this.searchText = this.fullPath;
            this.searchTextLower = this.searchText.toLowerCase(Locale.ROOT);
            this.node = node;
            this.back = false;
        }

        private CurveEntry()
        {
            this.title = BACK.get();
            this.channelId = "";
            this.optionId = "";
            this.fullPath = this.title;
            this.searchText = this.fullPath;
            this.searchTextLower = this.searchText.toLowerCase(Locale.ROOT);
            this.node = null;
            this.back = true;
        }

        private static CurveEntry node(TreeNode node, String title)
        {
            return new CurveEntry(node, title);
        }

        private static CurveEntry back()
        {
            return new CurveEntry();
        }

        private boolean isBack()
        {
            return this.back;
        }

        private boolean isNode()
        {
            return this.node != null;
        }

        private String searchText()
        {
            return this.searchText;
        }
    }

    private class UIShaderCurveEntryList extends UIList<CurveEntry>
    {
        private UIShaderCurveEntryList(java.util.function.Consumer<List<CurveEntry>> callback)
        {
            super(callback);

            this.scroll.scrollItemSize = 18;
        }

        private void setEntries(List<CurveEntry> entries)
        {
            this.current.clear();
            this.list = entries;
            this.update();
            this.scroll.setScroll(0);
            this.scroll.updateTarget();
        }

        @Override
        public void renderListElement(UIContext context, CurveEntry element, int i, int x, int y, boolean hover, boolean selected)
        {
            boolean curveSelected = UIShaderCurvePickerOverlayPanel.this.isSelected(element);

            if (curveSelected)
            {
                context.batcher.box(x, y, x + this.area.w, y + this.scroll.scrollItemSize, Colors.A50 | BBSSettings.primaryColor.get());
            }

            this.renderElementPart(context, element, i, x, y, hover, selected || curveSelected);
        }

        @Override
        protected String elementToString(UIContext context, int i, CurveEntry element)
        {
            return element.searchText();
        }

        @Override
        protected void renderElementPart(UIContext context, CurveEntry element, int i, int x, int y, boolean hover, boolean selected)
        {
            FontRenderer font = context.batcher.getFont();
            int color = hover ? Colors.HIGHLIGHT : Colors.WHITE;

            if (selected && !element.isBack() && !element.isNode())
            {
                color = Colors.HIGHLIGHT;
            }

            int textY = y + (this.scroll.scrollItemSize - font.getHeight()) / 2;
            int iconColor = Colors.WHITE;
            int iconX = x + 10;
            int textX = x + 22;

            context.batcher.icon(element.isBack() ? Icons.ARROW_LEFT : (element.isNode() ? Icons.FOLDER : Icons.CURVES), iconColor, iconX, y + this.scroll.scrollItemSize / 2F, 0.5F, 0.5F);

            if (element.isBack() || element.isNode())
            {
                context.batcher.textShadow(font.limitToWidth(element.title, this.area.w - 44), textX, textY, color);

                if (element.isNode())
                {
                    context.batcher.icon(Icons.ARROW_RIGHT, iconColor, x + this.area.w - 10, y + this.scroll.scrollItemSize / 2F, 0.5F, 0.5F);
                }

                return;
            }

            int idWidth = Math.min(220, Math.max(60, this.area.w / 3));
            String id = font.limitToWidth(element.optionId, idWidth);
            int idX = x + this.area.w - font.getWidth(id) - 6;
            int titleWidth = Math.max(30, idX - textX - 8);

            context.batcher.textShadow(font.limitToWidth(element.title, titleWidth), textX, textY, color);
            context.batcher.textShadow(id, idX, textY, Colors.GRAY);
        }
    }
}
