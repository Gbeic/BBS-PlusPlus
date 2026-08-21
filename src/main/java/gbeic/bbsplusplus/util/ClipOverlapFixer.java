package gbeic.bbsplusplus.util;

import mchorse.bbs_mod.utils.clips.Clip;
import mchorse.bbs_mod.utils.clips.Clips;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;

/**
 * 修复影片剪辑时间线中的同层重叠数据。
 * <p>
 * BBS 原版在少数拖拽路径下可能把两个剪辑写到同一层且时间区间相交，
 * 之后打开影片或回放时会因为同一 tick 命中多个互斥剪辑而触发崩溃。
 * 本工具只移动冲突剪辑的层级，不修改 tick、duration 和列表顺序，
 * 尽量保留坏影片原本的剪辑节奏。
 * </p>
 */
public final class ClipOverlapFixer
{
    private ClipOverlapFixer()
    {}

    /**
     * 修复指定剪辑容器中的同层重叠。
     *
     * @param clips 剪辑容器
     * @return 被修正的字段数量
     */
    public static int repair(Clips clips)
    {
        return clips == null ? 0 : repair(clips.get());
    }

    /**
     * 修复指定剪辑集合中的同层重叠。
     *
     * @param clips 剪辑集合
     * @return 被修正的字段数量
     */
    public static int repair(Collection<Clip> clips)
    {
        if (clips == null || clips.isEmpty())
        {
            return 0;
        }

        List<Entry> ordered = new ArrayList<>();
        int index = 0;
        int changes = 0;

        for (Clip clip : clips)
        {
            if (clip == null)
            {
                index += 1;

                continue;
            }

            changes += normalize(clip);
            ordered.add(new Entry(clip, index));
            index += 1;
        }

        if (ordered.size() <= 1)
        {
            return changes;
        }

        ordered.sort(Comparator
            .comparingInt((Entry entry) -> entry.layer)
            .thenComparingInt((Entry entry) -> entry.tick)
            .thenComparingInt((Entry entry) -> entry.index));

        List<Clip> accepted = new ArrayList<>(ordered.size());

        for (Entry entry : ordered)
        {
            Clip clip = entry.clip;

            if (overlapsAny(accepted, clip, clip.layer.get()))
            {
                int layer = findFreeLayer(clips, clip, clip.layer.get() + 1);

                if (layer != clip.layer.get())
                {
                    clip.layer.set(layer);
                    changes += 1;
                }
            }

            accepted.add(clip);
        }

        return changes;
    }

    private static int normalize(Clip clip)
    {
        int changes = 0;

        if (clip.tick.get() < 0)
        {
            clip.tick.set(0);
            changes += 1;
        }

        if (clip.layer.get() < 0)
        {
            clip.layer.set(0);
            changes += 1;
        }

        if (clip.duration.get() < 1)
        {
            clip.duration.set(1);
            changes += 1;
        }

        return changes;
    }

    private static int findFreeLayer(Collection<Clip> clips, Clip target, int startLayer)
    {
        int layer = Math.max(0, startLayer);

        while (layer < Integer.MAX_VALUE && overlapsAny(clips, target, layer))
        {
            layer += 1;
        }

        return layer;
    }

    private static boolean overlapsAny(Collection<Clip> clips, Clip target, int layer)
    {
        for (Clip clip : clips)
        {
            if (clip == null || clip == target || clip.layer.get() != layer)
            {
                continue;
            }

            if (overlaps(target, clip))
            {
                return true;
            }
        }

        return false;
    }

    private static boolean overlaps(Clip a, Clip b)
    {
        long aStart = a.tick.get();
        long aEnd = aStart + Math.max(a.duration.get(), 1);
        long bStart = b.tick.get();
        long bEnd = bStart + Math.max(b.duration.get(), 1);

        return aStart < bEnd && bStart < aEnd;
    }

    private static final class Entry
    {
        private final Clip clip;
        private final int index;
        private final int layer;
        private final int tick;

        private Entry(Clip clip, int index)
        {
            this.clip = clip;
            this.index = index;
            this.layer = Math.max(clip.layer.get(), 0);
            this.tick = Math.max(clip.tick.get(), 0);
        }
    }
}
