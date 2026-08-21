package gbeic.bbsplusplus.client;

import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.data.types.ListType;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.l10n.L10n;
import mchorse.bbs_mod.utils.DataPath;
import mchorse.bbs_mod.utils.undo.CompoundUndo;
import mchorse.bbs_mod.utils.undo.IUndo;
import mchorse.bbs_mod.ui.film.utils.undo.ValueChangeUndo;

import java.util.ArrayList;
import java.util.List;

/**
 * 将撤销历史里晦涩的数据路径转换成更容易识别的人话摘要。
 * <p>
 * 新版 BBS FS 已经提供可点击跳转的历史面板，但列表文本仍主要来自
 * {@link DataPath#toString()}，用户只能看到类似 {@code camera/9/fov/2}
 * 的内部路径。本类只在显示层重写文案，不改动撤销数据和历史跳转逻辑；
 * 对无法识别的路径保留原始片段，避免为了好看而丢失定位信息。
 * </p>
 */
public class UndoHistoryLabeler
{
    private static final String LANG_PREFIX = "bbspp.undo_history.";

    /**
     * 把单条撤销记录格式化成历史面板使用的标题。
     *
     * @param undo 撤销记录
     * @return 人话摘要
     */
    public static String label(IUndo<?> undo)
    {
        if (undo instanceof ValueChangeUndo valueUndo)
        {
            return labelValueUndo(valueUndo);
        }

        if (undo instanceof CompoundUndo<?> compoundUndo)
        {
            return labelCompoundUndo(compoundUndo);
        }

        return String.valueOf(undo);
    }

    private static String labelCompoundUndo(CompoundUndo<?> compoundUndo)
    {
        List<ValueChangeUndo> changes = collectValueUndos(compoundUndo);

        if (changes.isEmpty())
        {
            return String.valueOf(compoundUndo);
        }

        String action = summarizeCompoundAction(changes);
        String target = labelCommonPath(changes);

        return tr("compound_format", action, target, changes.size());
    }

    private static String labelValueUndo(ValueChangeUndo undo)
    {
        String action = summarizeChangeAction(undo);
        String target = labelPath(undo.name);

        return action.isEmpty() ? target : tr("entry_format", action, target);
    }

    private static String summarizeCompoundAction(List<ValueChangeUndo> changes)
    {
        boolean allAdded = true;
        boolean allRemoved = true;
        boolean anyKeyframeCount = false;

        for (ValueChangeUndo change : changes)
        {
            ChangeKind kind = getChangeKind(change);

            allAdded = allAdded && kind == ChangeKind.ADDED;
            allRemoved = allRemoved && kind == ChangeKind.REMOVED;
            anyKeyframeCount = anyKeyframeCount || changesKeyframeCount(change);
        }

        if (allAdded)
        {
            return tr("action.add");
        }

        if (allRemoved)
        {
            return tr("action.remove");
        }

        if (anyKeyframeCount)
        {
            return tr("action.edit_keyframes");
        }

        return tr("action.change");
    }

    private static String summarizeChangeAction(ValueChangeUndo undo)
    {
        int oldKeyframes = getKeyframeCount(undo.oldValue);
        int newKeyframes = getKeyframeCount(undo.newValue);

        if (oldKeyframes >= 0 && newKeyframes >= 0)
        {
            if (newKeyframes > oldKeyframes)
            {
                return tr("action.add_keyframe");
            }

            if (newKeyframes < oldKeyframes)
            {
                return tr("action.remove_keyframe");
            }
        }

        ChangeKind kind = getChangeKind(undo);

        if (kind == ChangeKind.ADDED)
        {
            return tr("action.add");
        }

        if (kind == ChangeKind.REMOVED)
        {
            return tr("action.remove");
        }

        if (changesKeyframeCount(undo))
        {
            return tr("action.edit_keyframes");
        }

        return tr("action.change");
    }

    private static ChangeKind getChangeKind(ValueChangeUndo undo)
    {
        boolean oldEmpty = isEmptyContainer(undo.oldValue);
        boolean newEmpty = isEmptyContainer(undo.newValue);

        if (oldEmpty && !newEmpty)
        {
            return ChangeKind.ADDED;
        }

        if (!oldEmpty && newEmpty)
        {
            return ChangeKind.REMOVED;
        }

        return ChangeKind.CHANGED;
    }

    private static boolean isEmptyContainer(BaseType data)
    {
        if (data instanceof ListType list)
        {
            return list.isEmpty();
        }

        if (data instanceof MapType map)
        {
            if (map.isEmpty())
            {
                return true;
            }

            if (map.has("keyframes", BaseType.TYPE_LIST))
            {
                return map.getList("keyframes").isEmpty();
            }
        }

        return false;
    }

    private static boolean changesKeyframeCount(ValueChangeUndo undo)
    {
        int oldCount = getKeyframeCount(undo.oldValue);
        int newCount = getKeyframeCount(undo.newValue);

        return oldCount >= 0 && newCount >= 0 && oldCount != newCount;
    }

    /**
     * 判断一条同轨道修改是否改动了关键帧数量。
     *
     * @param undo 撤销记录
     * @return 改动了关键帧数量时返回 true
     */
    public static boolean changesKeyframeCount(IUndo<?> undo)
    {
        if (undo instanceof ValueChangeUndo valueUndo)
        {
            return changesKeyframeCount(valueUndo);
        }

        if (undo instanceof CompoundUndo<?> compoundUndo)
        {
            for (IUndo<?> child : compoundUndo.getUndos())
            {
                if (changesKeyframeCount(child))
                {
                    return true;
                }
            }
        }

        return false;
    }

    private static int getKeyframeCount(BaseType data)
    {
        if (data instanceof MapType map && map.has("keyframes", BaseType.TYPE_LIST))
        {
            return map.getList("keyframes").size();
        }

        if (data instanceof ListType list)
        {
            boolean keyframeLike = list.isEmpty();

            for (BaseType entry : list)
            {
                keyframeLike = entry instanceof MapType map && map.has("tick") && map.has("value");

                if (!keyframeLike)
                {
                    break;
                }
            }

            if (keyframeLike)
            {
                return list.size();
            }
        }

        return -1;
    }

    private static String labelCommonPath(List<ValueChangeUndo> changes)
    {
        List<String> segments = commonSegments(changes);
        DataPath path = new DataPath(false);

        path.strings.addAll(segments);

        return labelPath(path);
    }

    private static List<String> commonSegments(List<ValueChangeUndo> changes)
    {
        if (changes.isEmpty())
        {
            return new ArrayList<>();
        }

        List<String> common = new ArrayList<>(changes.get(0).name.strings);

        for (int i = 1; i < changes.size(); i++)
        {
            List<String> other = changes.get(i).name.strings;
            int index = 0;
            int max = Math.min(common.size(), other.size());

            while (index < max && common.get(index).equals(other.get(index)))
            {
                index += 1;
            }

            while (common.size() > index)
            {
                common.remove(common.size() - 1);
            }
        }

        return common.isEmpty() ? new ArrayList<>(changes.get(0).name.strings) : common;
    }

    private static List<ValueChangeUndo> collectValueUndos(IUndo<?> undo)
    {
        List<ValueChangeUndo> changes = new ArrayList<>();

        collectValueUndos(undo, changes);

        return changes;
    }

    private static void collectValueUndos(IUndo<?> undo, List<ValueChangeUndo> changes)
    {
        if (undo instanceof ValueChangeUndo valueUndo)
        {
            changes.add(valueUndo);
        }
        else if (undo instanceof CompoundUndo<?> compoundUndo)
        {
            for (IUndo<?> child : compoundUndo.getUndos())
            {
                collectValueUndos(child, changes);
            }
        }
    }

    private static String labelPath(DataPath path)
    {
        List<String> parts = new ArrayList<>();
        List<String> segments = path.strings;
        int start = hasFilmRootPrefix(segments) ? 1 : 0;

        for (int i = start; i < segments.size(); i++)
        {
            String segment = segments.get(i);

            if (segment.equals("camera"))
            {
                parts.add(tr("root.camera"));
            }
            else if (segment.equals("replays"))
            {
                parts.add(tr("root.replays"));
            }
            else if (segment.equals("actions"))
            {
                parts.add(tr("root.actions"));
            }
            else if (segment.equals("properties"))
            {
                parts.add(tr("root.properties"));
            }
            else if (segment.equals("keyframes"))
            {
                parts.add(tr("root.keyframes"));
            }
            else if (isNumeric(segment))
            {
                parts.add(labelNumericSegment(segments, i, segment));
            }
            else
            {
                parts.add(labelProperty(segment));
            }
        }

        return String.join(tr("path_separator"), parts);
    }

    private static boolean hasFilmRootPrefix(List<String> segments)
    {
        if (segments.size() < 2)
        {
            return false;
        }

        String first = segments.get(0);
        String second = segments.get(1);

        return !isKnownRoot(first) && isKnownRoot(second);
    }

    private static boolean isKnownRoot(String segment)
    {
        return segment.equals("camera")
            || segment.equals("replays")
            || segment.equals("actions")
            || segment.equals("properties")
            || segment.equals("keyframes")
            || segment.equals("inventory")
            || segment.equals("hp")
            || segment.equals("hunger")
            || segment.equals("xp_level")
            || segment.equals("xp_progress");
    }

    private static String labelNumericSegment(List<String> segments, int index, String segment)
    {
        String previous = index > 0 ? segments.get(index - 1) : "";

        if (previous.equals("camera"))
        {
            return tr("numeric.clip", segment);
        }

        if (previous.equals("replays"))
        {
            return tr("numeric.replay", segment);
        }

        if (previous.equals("actions"))
        {
            return tr("numeric.action", segment);
        }

        if (isKeyframeTrack(previous))
        {
            return tr("numeric.keyframe", segment);
        }

        return segment;
    }

    private static boolean isKeyframeTrack(String segment)
    {
        return segment.equals("x")
            || segment.equals("y")
            || segment.equals("z")
            || segment.equals("yaw")
            || segment.equals("pitch")
            || segment.equals("roll")
            || segment.equals("fov")
            || segment.equals("distance")
            || segment.equals("pose")
            || segment.equals("transform")
            || segment.equals("item_main_hand")
            || segment.equals("item_off_hand")
            || segment.equals("item_head")
            || segment.equals("item_chest")
            || segment.equals("item_legs")
            || segment.equals("item_feet");
    }

    private static String labelProperty(String segment)
    {
        String key = L10n.lang("bbspp.keyframe." + segment).get();

        if (!key.equals("bbspp.keyframe." + segment))
        {
            return key;
        }

        return switch (segment)
        {
            case "enabled" -> tr("property.enabled");
            case "title" -> tr("property.title");
            case "layer" -> tr("property.layer");
            case "tick" -> tr("property.tick");
            case "duration" -> tr("property.duration");
            case "envelope" -> tr("property.envelope");
            case "position" -> tr("property.position");
            case "point" -> tr("property.point");
            case "angle" -> tr("property.angle");
            case "form" -> tr("property.form");
            case "actor" -> tr("property.actor");
            case "category" -> tr("property.category");
            case "label" -> tr("property.label");
            case "shadow" -> tr("property.shadow");
            case "shadow_size" -> tr("property.shadow_size");
            case "relative" -> tr("property.relative");
            case "relativeOffset" -> tr("property.relativeOffset");
            case "inventory" -> tr("property.inventory");
            case "hp" -> tr("property.hp");
            case "hunger" -> tr("property.hunger");
            case "xp_level" -> tr("property.xp_level");
            case "xp_progress" -> tr("property.xp_progress");
            default -> segment;
        };
    }

    private static String tr(String key, Object... args)
    {
        return L10n.lang(LANG_PREFIX + key).format(args).get();
    }

    private static boolean isNumeric(String value)
    {
        for (int i = 0; i < value.length(); i++)
        {
            if (!Character.isDigit(value.charAt(i)))
            {
                return false;
            }
        }

        return !value.isEmpty();
    }

    private enum ChangeKind
    {
        ADDED,
        REMOVED,
        CHANGED
    }
}
