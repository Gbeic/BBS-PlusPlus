package gbeic.bbsplusplus.client.ui.pose;

import mchorse.bbs_mod.cubic.IModel;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/**
 * 为正式版姿势骨骼列表构建只用于绘制的父子层级元数据。
 *
 * <p>该类位于普通客户端代码包中，避免把需要在运行时实例化的辅助类型声明在 Mixin 包内。
 * 隐藏骨骼只移除自身，其可见子节点会提升到当前层级，与 BBS 最新提交的树形算法一致。</p>
 */
public class PoseBoneTreeMetadata
{
    private final Map<String, Meta> metas = new HashMap<>();

    /** 根据模型重新生成全部可见骨骼的绘制元数据。 */
    public void setHierarchy(IModel model, Predicate<String> hidden)
    {
        this.metas.clear();

        if (model == null)
        {
            return;
        }

        this.emit(build(model, model.getRootGroupKeys(), hidden), 0, 0);
    }

    /** 获取指定骨骼的缩进与连接线信息。 */
    public Meta get(String bone)
    {
        return this.metas.get(bone);
    }

    private static List<Node> build(IModel model, Collection<String> bones, Predicate<String> hidden)
    {
        List<Node> nodes = new ArrayList<>();

        for (String bone : bones)
        {
            List<Node> children = build(model, model.getDirectChildrenKeys(bone), hidden);

            if (hidden == null || !hidden.test(bone))
            {
                Node node = new Node(bone);

                node.children.addAll(children);
                nodes.add(node);
            }
            else
            {
                nodes.addAll(children);
            }
        }

        return nodes;
    }

    private void emit(List<Node> nodes, int depth, int lines)
    {
        for (int i = 0; i < nodes.size(); i++)
        {
            Node node = nodes.get(i);
            boolean last = i == nodes.size() - 1;

            this.metas.put(node.id, new Meta(depth, lines, last, node.id));

            int childLines = !last && depth > 0 ? lines | (1 << (depth - 1)) : lines;

            this.emit(node.children, depth + 1, childLines);
        }
    }

    private static class Node
    {
        public final String id;
        public final List<Node> children = new ArrayList<>();

        public Node(String id)
        {
            this.id = id;
        }
    }

    /** 单根骨骼在树形列表中的绘制信息。 */
    public static class Meta
    {
        public final int depth;
        public final int lines;
        public final boolean last;
        public final String label;

        public Meta(int depth, int lines, boolean last, String label)
        {
            this.depth = depth;
            this.lines = lines;
            this.last = last;
            this.label = label;
        }
    }
}
