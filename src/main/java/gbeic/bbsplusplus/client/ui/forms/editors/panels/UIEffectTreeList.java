package gbeic.bbsplusplus.client.ui.forms.editors.panels;

import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.input.list.UIStringList;
import mchorse.bbs_mod.ui.utils.icons.Icon;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.colors.Colors;

import gbeic.bbsplusplus.utils.AlphanumComparator;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 树形特效列表 — 将粒子文件按文件夹结构组织，支持展开/折叠。
 */
public class UIEffectTreeList extends UIStringList
{
    /* 树结构缓存，展开/折叠时直接从中展平，不再重建 */
    private List<TreeNode> rootNodes = new ArrayList<>();

    /* 展开状态存储 */
    private final Set<String> expandedFolders = new HashSet<>();

    /* 渲染元数据：路径 → (显示名, 缩进层级, 是否为文件夹) */
    private final Map<String, TreeEntry> treeData = new HashMap<>();

    /* 搜索模式下忽略文件夹点击 */
    private boolean searchActive = false;

    public UIEffectTreeList(java.util.function.Consumer<List<String>> callback)
    {
        super(callback);
    }

    /* ====================== 公开 API ====================== */

    public void setEffectKeys(Collection<String> keys)
    {
        expandedFolders.clear();
        rootNodes = buildTree(keys);
        flattenAll();
    }

    public void toggleFolder(String folderPath)
    {
        if (expandedFolders.contains(folderPath))
        {
            expandedFolders.remove(folderPath);
        }
        else
        {
            expandedFolders.add(folderPath);
        }
        flattenAll();
    }

    /* ====================== 树构建 ====================== */

    /**
     * 将 "bbs:00_Basic/Laser01" 这样的特效键解析为树节点。
     */
    private List<TreeNode> buildTree(Collection<String> keys)
    {
        Map<String, TreeNode> nodeMap = new HashMap<>();
        List<TreeNode> roots = new ArrayList<>();

        for (String key : keys)
        {
            int colonIdx = key.indexOf(':');
            String relativePath = (colonIdx >= 0) ? key.substring(colonIdx + 1) : key;

            String[] parts = relativePath.split("/");
            StringBuilder currentPath = new StringBuilder();
            TreeNode parent = null;

            for (int i = 0; i < parts.length; i++)
            {
                boolean isLast = (i == parts.length - 1);
                String part = parts[i];

                if (currentPath.length() > 0) currentPath.append("/");
                currentPath.append(part);

                if (!isLast)
                {
                    String dirPath = currentPath.toString();
                    TreeNode dirNode = nodeMap.get(dirPath);
                    if (dirNode == null)
                    {
                        dirNode = new TreeNode(dirPath, part, true);
                        nodeMap.put(dirPath, dirNode);
                        if (parent != null) parent.children.add(dirNode);
                        else roots.add(dirNode);
                    }
                    parent = dirNode;
                }
                else
                {
                    TreeNode fileNode = new TreeNode(key, part, false);
                    if (parent != null) parent.children.add(fileNode);
                    else roots.add(fileNode);
                }
            }
        }

        sortTreeNodes(roots);
        return roots;
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

    /** 从缓存的根节点重新展平列表 */
    private void flattenAll()
    {
        this.list.clear();
        for (TreeNode node : rootNodes) flattenNode(node, 0);
        this.update();
    }

    private void flattenNode(TreeNode node, int depth)
    {
        if (node.folder)
        {
            boolean expanded = expandedFolders.contains(node.path);
            treeData.put(node.path, new TreeEntry(node.name, depth, true, expanded));
            this.list.add(node.path);
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

    @Override
    protected boolean sortElements()
    {
        // 树结构已排序，禁止再排序破坏结构
        return false;
    }

    /* ====================== 搜索 ====================== */

    @Override
    public void filter(String str)
    {
        searchActive = str != null && !str.isEmpty();

        if (!searchActive)
        {
            // 清空搜索 → 恢复完整树
            flattenAll();
        }
        else
        {
            // 搜索时只匹配文件名，不匹配文件夹
            String lower = str.toLowerCase();
            this.list.clear();
            for (TreeNode node : rootNodes)
                filterFlatten(node, 0, lower);
            this.update();
        }
    }

    /**
     * 搜索模式下展平：只显示名称匹配的文件及其父文件夹。
     * @return 是否有后代文件匹配
     */
    private boolean filterFlatten(TreeNode node, int depth, String searchLower)
    {
        if (node.folder)
        {
            // 先检查是否有后代匹配
            if (!hasMatch(node, searchLower)) return false;

            // 文件夹先加入列表（顺序在子项之前）
            treeData.put(node.path, new TreeEntry(node.name, depth, true, true));
            this.list.add(node.path);

            // 再逐个展平子项
            for (TreeNode child : node.children)
                addMatchingFiles(child, depth + 1, searchLower);

            return true;
        }
        else
        {
            if (node.name.toLowerCase().contains(searchLower))
            {
                treeData.put(node.path, new TreeEntry(node.name, depth, false, false));
                this.list.add(node.path);
                return true;
            }
            return false;
        }
    }

    /** 检查节点（或其子节点）是否有匹配搜索的文件 */
    private boolean hasMatch(TreeNode node, String searchLower)
    {
        if (!node.folder)
            return node.name.toLowerCase().contains(searchLower);
        for (TreeNode child : node.children)
        {
            if (hasMatch(child, searchLower))
                return true;
        }
        return false;
    }

    /** 添加匹配搜索的文件到列表（不处理文件夹） */
    private void addMatchingFiles(TreeNode node, int depth, String searchLower)
    {
        if (node.folder)
        {
            for (TreeNode child : node.children)
                addMatchingFiles(child, depth + 1, searchLower);
        }
        else if (node.name.toLowerCase().contains(searchLower))
        {
            treeData.put(node.path, new TreeEntry(node.name, depth, false, false));
            this.list.add(node.path);
        }
    }

    /* ====================== 渲染 ====================== */

    @Override
    protected void renderElementPart(UIContext context, String element, int i, int x, int y, boolean hover, boolean selected)
    {
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
            }

            int textColor = entry.expanded ? Colors.HIGHLIGHT : (hover ? Colors.HIGHLIGHT : Colors.WHITE);
            context.batcher.textShadow(displayText, folderTextX,
                y + (this.scroll.scrollItemSize - context.batcher.getFont().getHeight()) / 2, textColor);

            int arrowX = folderTextX + textWidth;
            Icon arrowIcon = entry.expanded ? Icons.ARROW_DOWN : Icons.ARROW_RIGHT;
            context.batcher.icon(arrowIcon, Colors.setA(Colors.WHITE, 0.6F), arrowX, iconY);
        }
        else
        {
            String displayText = entry.displayName;
            int textWidth = context.batcher.getFont().getWidth(displayText);
            int maxWidth = this.area.w - 8 - (textX - x);

            if (textWidth > maxWidth)
                displayText = context.batcher.getFont().limitToWidth(displayText, maxWidth);

            context.batcher.textShadow(displayText, textX,
                y + (this.scroll.scrollItemSize - context.batcher.getFont().getHeight()) / 2,
                hover ? Colors.HIGHLIGHT : Colors.WHITE);
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

        String element = this.list.get(scrollIndex);
        if (element == null)
            return super.subMouseClicked(context);

        TreeEntry entry = treeData.get(element);
        if (entry == null)
            return super.subMouseClicked(context);

        int indent = entry.depth * 12;
        int textStartX = this.area.x + 4 + indent;

        if (entry.folder)
        {
            // 搜索模式下文件夹仅作视觉分组，不可点击
            if (!searchActive && context.mouseX >= textStartX)
            {
                this.toggleFolder(element);
            }
            return true;
        }
        else
        {
            // 文件 — 交给默认行为（选中触发 callback）
            return super.subMouseClicked(context);
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