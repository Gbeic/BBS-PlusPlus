package gbeic.bbsplusplus.ui.list;

import mchorse.bbs_mod.audio.SoundLikeManager;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.input.list.UILikeableStringList;
import mchorse.bbs_mod.ui.utils.icons.Icon;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.colors.Colors;

import gbeic.bbsplusplus.mixin.UILikeableStringListAccessor;
import gbeic.bbsplusplus.utils.AlphanumComparator;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/**
 * 树形音效列表 — 将音效文件按文件夹结构组织，支持展开/折叠。
 */
public class UIAudioTreeList extends UILikeableStringList
{
    private static final String PATH_PREFIX = "assets:audio/";

    private Map<String, TreeEntry> treeData = new HashMap<>();
    private Set<String> expandedFolders = new HashSet<>();
    private List<String> audioPaths = new ArrayList<>();
    private boolean includeNone;

    private UILikeableStringListAccessor acc;

    private UILikeableStringListAccessor acc()
    {
        if (acc == null) acc = (UILikeableStringListAccessor) this;
        return acc;
    }

    public UIAudioTreeList(Consumer<List<String>> callback, SoundLikeManager likeManager)
    {
        super(callback, likeManager);
    }

    /* ====================== 公开 API ====================== */

    public void setAudioPaths(Collection<String> paths, boolean includeNone)
    {
        this.audioPaths.clear();
        this.audioPaths.addAll(paths);
        this.includeNone = includeNone;
        rebuildTree();
    }

    public void toggleFolder(String folderPath)
    {
        TreeEntry entry = treeData.get(folderPath);
        if (entry != null && entry.folder)
        {
            if (entry.expanded)
            {
                expandedFolders.remove(folderPath);
                entry.expanded = false;
            }
            else
            {
                expandedFolders.add(folderPath);
                entry.expanded = true;
            }
            rebuildTree();
        }
    }

    public void expandToShow(String path)
    {
        if (path == null || !path.startsWith(PATH_PREFIX)) return;

        String relativePath = stripPrefix(path);
        String[] parts = relativePath.split("/");

        StringBuilder currentPath = new StringBuilder(PATH_PREFIX);
        for (int i = 0; i < parts.length - 1; i++)
        {
            currentPath.append(parts[i]).append("/");
            String folderPath = currentPath.toString();
            expandedFolders.add(folderPath);
            TreeEntry entry = treeData.get(folderPath);
            if (entry != null) entry.expanded = true;
        }
        rebuildTree();
    }

    public String getPathAt(int visibleIndex)
    {
        if (visibleIndex < 0 || visibleIndex >= this.list.size()) return null;
        return this.list.get(visibleIndex);
    }

    /* ====================== 树构建 ====================== */

    private void rebuildTree()
    {
        List<TreeNode> rootNodes = buildTree(audioPaths);
        this.list.clear();
        this.treeData.clear();

        if (includeNone)
        {
            String noneStr = UIKeys.GENERAL_NONE.get();
            this.list.add(noneStr);
            treeData.put(noneStr, new TreeEntry(noneStr, 0, false, false));
        }

        for (TreeNode node : rootNodes) flattenNode(node, 0);
        this.update();
    }

    private List<TreeNode> buildTree(Collection<String> paths)
    {
        Map<String, TreeNode> nodeMap = new HashMap<>();
        List<TreeNode> rootNodes = new ArrayList<>();

        for (String path : paths)
        {
            String relativePath = stripPrefix(path);
            if (relativePath == null) continue;

            String[] parts = relativePath.split("/");
            StringBuilder currentPath = new StringBuilder(PATH_PREFIX);
            TreeNode parent = null;

            for (int i = 0; i < parts.length; i++)
            {
                boolean isLast = (i == parts.length - 1);
                String part = parts[i];
                currentPath.append(part);

                if (!isLast)
                {
                    String dirPath = currentPath.toString() + "/";
                    TreeNode dirNode = nodeMap.get(dirPath);
                    if (dirNode == null)
                    {
                        dirNode = new TreeNode(dirPath, part, true);
                        nodeMap.put(dirPath, dirNode);
                        if (parent != null) parent.children.add(dirNode);
                        else rootNodes.add(dirNode);
                    }
                    parent = dirNode;
                    currentPath.append("/");
                }
                else
                {
                    TreeNode fileNode = new TreeNode(path, part, false);
                    if (parent != null) parent.children.add(fileNode);
                    else rootNodes.add(fileNode);
                }
            }
        }

        sortTreeNodes(rootNodes);
        return rootNodes;
    }

    private static final AlphanumComparator ALPHANUM_COMPARATOR = new AlphanumComparator();

    private void sortTreeNodes(List<TreeNode> nodes)
    {
        Collections.sort(nodes, (a, b) -> {
            if (a.folder != b.folder) return a.folder ? -1 : 1;
            return ALPHANUM_COMPARATOR.compare(a.name, b.name);
        });
        for (TreeNode node : nodes)
        {
            if (node.folder) sortTreeNodes(node.children);
        }
    }

    private void flattenNode(TreeNode node, int depth)
    {
        if (node.folder)
        {
            String folderPath = node.path;
            boolean expanded = expandedFolders.contains(folderPath);
            treeData.put(folderPath, new TreeEntry(node.name, depth, true, expanded));
            this.list.add(folderPath);

            if (expanded)
            {
                for (TreeNode child : node.children) flattenNode(child, depth + 1);
            }
        }
        else
        {
            treeData.put(node.path, new TreeEntry(node.name, depth, false, false));
            this.list.add(node.path);
        }
    }

    private String stripPrefix(String path)
    {
        if (path.startsWith(PATH_PREFIX)) return path.substring(PATH_PREFIX.length());
        return path;
    }

    /* ====================== 渲染 ====================== */

    @Override
    protected void renderElementPart(UIContext context, String element, int i, int x, int y, boolean hover, boolean selected)
    {
        if (element.equals(UIKeys.GENERAL_NONE.get()))
        {
            super.renderElementPart(context, element, i, x, y, hover, selected);
            return;
        }

        TreeEntry entry = treeData.get(element);
        if (entry == null)
        {
            super.renderElementPart(context, element, i, x, y, hover, selected);
            return;
        }

        int indent = entry.depth * 12;
        int textX = x + 4 + indent;

        if (entry.folder)
        {
            int iconY = y + (this.scroll.scrollItemSize - 16) / 2;
            int folderIconX = textX - 2;
            context.batcher.icon(Icons.FOLDER, Colors.setA(Colors.WHITE, 0.6F), folderIconX, iconY);

            String displayText = entry.displayName + "/";
            int folderTextX = folderIconX + 16;
            int textWidth = context.batcher.getFont().getWidth(displayText);
            int maxWidth = this.area.w - 8 - 36 - (folderTextX - x);

            if (textWidth > maxWidth)
            {
                displayText = context.batcher.getFont().limitToWidth(displayText, maxWidth);
                textWidth = context.batcher.getFont().getWidth(displayText);
            }

            int textColor = expandedFolders.contains(element) ? Colors.HIGHLIGHT : (hover ? Colors.HIGHLIGHT : Colors.WHITE);
            context.batcher.textShadow(displayText, folderTextX,
                y + (this.scroll.scrollItemSize - context.batcher.getFont().getHeight()) / 2, textColor);

            int arrowX = folderTextX + textWidth;
            Icon arrowIcon = entry.expanded ? Icons.ARROW_DOWN : Icons.ARROW_RIGHT;
            context.batcher.icon(arrowIcon, Colors.setA(Colors.WHITE, 0.6F), arrowX, iconY);
        }
        else
        {
            boolean isNoneOption = element.equals(UIKeys.GENERAL_NONE.get());
            String displayText = entry.displayName;
            int textWidth = context.batcher.getFont().getWidth(displayText);
            int buttonSpace = isNoneOption ? 0 : (acc().getShowEditRemoveButtons() ? 60 : 20);
            int maxWidth = this.area.w - 8 - buttonSpace - (textX - x);

            if (textWidth > maxWidth)
                displayText = context.batcher.getFont().limitToWidth(displayText, maxWidth);

            context.batcher.textShadow(displayText, textX,
                y + (this.scroll.scrollItemSize - context.batcher.getFont().getHeight()) / 2,
                hover ? Colors.HIGHLIGHT : Colors.WHITE);

            if (isNoneOption) return;

            int currentIconX = this.area.x + this.area.w - 20;
            int iconY = y + (this.scroll.scrollItemSize - 16) / 2;

            boolean isLiked = acc().getLikeManager().isSoundLiked(element);
            boolean isHoverOnLike = this.area.isInside(context)
                && context.mouseX >= currentIconX && context.mouseX < currentIconX + 16
                && context.mouseY >= iconY && context.mouseY < iconY + 16;

            var likeButton = acc().getLikeButton();
            likeButton.both(isLiked ? Icons.DISLIKE : Icons.LIKE);
            likeButton.iconColor(isHoverOnLike || isLiked ? Colors.WHITE : Colors.GRAY);
            likeButton.area.set(currentIconX, iconY, 16, 16);
            likeButton.render(context);

            if (acc().getShowEditRemoveButtons())
            {
                currentIconX -= 20;
                boolean isHoverOnRemove = this.area.isInside(context)
                    && context.mouseX >= currentIconX && context.mouseX < currentIconX + 16
                    && context.mouseY >= iconY && context.mouseY < iconY + 16;
                var removeButton = acc().getRemoveButton();
                removeButton.iconColor(isHoverOnRemove ? Colors.WHITE : Colors.GRAY);
                removeButton.area.set(currentIconX, iconY, 16, 16);
                removeButton.render(context);

                currentIconX -= 20;
                boolean isHoverOnEdit = this.area.isInside(context)
                    && context.mouseX >= currentIconX && context.mouseX < currentIconX + 16
                    && context.mouseY >= iconY && context.mouseY < iconY + 16;
                var editButton = acc().getEditButton();
                editButton.iconColor(isHoverOnEdit ? Colors.WHITE : Colors.GRAY);
                editButton.area.set(currentIconX, iconY, 16, 16);
                editButton.render(context);
            }
        }
    }

    /* ====================== 鼠标交互 ====================== */

    @Override
    public boolean subMouseClicked(UIContext context)
    {
        if (!this.area.isInside(context) || context.mouseButton != 0)
            return super.subMouseClicked(context);

        int scrollIndex = this.scroll.getIndex(context.mouseX, context.mouseY);
        if (!this.exists(scrollIndex))
            return super.subMouseClicked(context);

        String element = acc().getShowOnlyLiked()
            ? this.getVisibleElement(scrollIndex)
            : this.list.get(scrollIndex);

        if (element == null)
            return super.subMouseClicked(context);

        if (element.equals(UIKeys.GENERAL_NONE.get()))
            return super.subMouseClicked(context);

        TreeEntry entry = treeData.get(element);
        if (entry == null)
            return super.subMouseClicked(context);

        int y = this.area.y + scrollIndex * this.scroll.scrollItemSize - (int) this.scroll.getScroll();
        int iconY = y + (this.scroll.scrollItemSize - 16) / 2;
        int likeIconX = this.area.x + this.area.w - 20;
        int indent = entry.depth * 12;
        int textStartX = this.area.x + 4 + indent;

        if (entry.folder)
        {
            if (context.mouseX >= textStartX && context.mouseX < likeIconX)
            {
                this.toggleFolder(element);
                return true;
            }

            if (context.mouseX >= likeIconX)
                return true;

            return true;
        }
        else
        {
            if (context.mouseX >= likeIconX && context.mouseX < likeIconX + 16
                && context.mouseY >= iconY && context.mouseY < iconY + 16)
            {
                acc().getLikeManager().toggleSoundLiked(element);
                Runnable cb = acc().getRefreshCallback();
                if (cb != null) cb.run();
                return true;
            }

            if (acc().getShowEditRemoveButtons())
            {
                int removeIconX = likeIconX - 20;
                if (context.mouseX >= removeIconX && context.mouseX < removeIconX + 16
                    && context.mouseY >= iconY && context.mouseY < iconY + 16)
                {
                    Consumer<String> removeCb = acc().getRemoveCallback();
                    if (removeCb != null) removeCb.accept(element);
                    return true;
                }

                int editIconX = removeIconX - 20;
                if (context.mouseX >= editIconX && context.mouseX < editIconX + 16
                    && context.mouseY >= iconY && context.mouseY < iconY + 16)
                {
                    Consumer<String> editCb = acc().getEditCallback();
                    if (editCb != null) editCb.accept(element);
                    return true;
                }
            }

            if (!acc().getShowOnlyLiked())
                return super.subMouseClicked(context);

            int actualIndex = this.list.indexOf(element);
            if (actualIndex < 0) return false;

            int buttonAreaStartX = this.area.x + this.area.w - 20;
            if (context.mouseX >= buttonAreaStartX && context.mouseX < this.area.x + this.area.w
                && context.mouseY >= iconY && context.mouseY < iconY + 16)
                return false;

            this.current.clear();
            this.current.add(actualIndex);

            if (this.callback != null)
            {
                List<String> selected = new ArrayList<>();
                selected.add(element);
                this.callback.accept(selected);
            }
            return true;
        }
    }

    @Override
    public int renderElement(UIContext context, String element, int i, int index, boolean postDraw)
    {
        boolean isNoneOption = element.equals(UIKeys.GENERAL_NONE.get());
        if (acc().getShowOnlyLiked() && !acc().getLikeManager().isSoundLiked(element) && !isNoneOption)
            return i;
        return super.renderElement(context, element, i, index, postDraw);
    }

    @Override
    public void renderList(UIContext context)
    {
        if (!acc().getShowOnlyLiked())
        {
            super.renderList(context);
            return;
        }

        int visibleIndex = 0;
        for (int actualIndex = 0; actualIndex < this.list.size(); actualIndex++)
        {
            String element = this.list.get(actualIndex);
            boolean isNoneOption = element.equals(UIKeys.GENERAL_NONE.get());
            if (acc().getLikeManager().isSoundLiked(element) || isNoneOption)
            {
                int next = this.renderElement(context, element, visibleIndex, actualIndex, false);
                if (next == -1) break;
                visibleIndex = next;
            }
        }
    }

    @Override
    protected String elementToString(UIContext context, int i, String element)
    {
        TreeEntry entry = treeData.get(element);
        return entry != null ? entry.displayName : element;
    }

    /* ====================== 内部数据结构 ====================== */

    private static class TreeNode
    {
        String path;
        String name;
        boolean folder;
        List<TreeNode> children = new ArrayList<>();
        TreeNode(String path, String name, boolean folder)
        {
            this.path = path; this.name = name; this.folder = folder;
        }
    }

    private static class TreeEntry
    {
        String displayName;
        int depth;
        boolean folder;
        boolean expanded;
        TreeEntry(String displayName, int depth, boolean folder, boolean expanded)
        {
            this.displayName = displayName; this.depth = depth;
            this.folder = folder; this.expanded = expanded;
        }
    }
}
