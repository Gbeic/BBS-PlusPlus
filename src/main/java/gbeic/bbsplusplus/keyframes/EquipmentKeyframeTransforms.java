package gbeic.bbsplusplus.keyframes;

import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.utils.interps.IInterp;
import mchorse.bbs_mod.utils.keyframes.Keyframe;
import mchorse.bbs_mod.utils.keyframes.KeyframeChannel;
import mchorse.bbs_mod.utils.keyframes.KeyframeSegment;
import mchorse.bbs_mod.utils.pose.Transform;
import net.minecraft.world.item.ItemStack;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * 统一管理六个装备/手持关键帧的附加变换数据。
 *
 * <p>解决的问题：这些轨道的数据类型仍然必须是原版 {@link ItemStack}，否则会破坏
 * 旧工程和换物品逻辑。实现思路是把变换数据存到关键帧顶层，并在 UI 与渲染阶段
 * 单独读写和插值。</p>
 */
public class EquipmentKeyframeTransforms
{
    public static final String DATA_KEY = "bbspp_item_transform";

    private static final Transform DEFAULT = new Transform();
    private static final Set<String> EQUIPMENT_CHANNELS = new HashSet<>(Arrays.asList(
        "item_main_hand",
        "item_off_hand",
        "item_head",
        "item_chest",
        "item_legs",
        "item_feet"
    ));

    public static boolean isEquipmentChannel(String id)
    {
        return EQUIPMENT_CHANNELS.contains(id);
    }

    public static boolean isEquipmentKeyframe(Keyframe<?> keyframe)
    {
        if (keyframe == null || !(keyframe.getParent() instanceof KeyframeChannel<?> channel))
        {
            return false;
        }

        return isEquipmentChannel(channel.getId());
    }

    public static Transform get(Keyframe<?> keyframe)
    {
        return keyframe instanceof EquipmentKeyframeTransformHolder holder ? holder.bbspp$getEquipmentTransform() : null;
    }

    public static Transform getOrCreate(Keyframe<?> keyframe)
    {
        if (!(keyframe instanceof EquipmentKeyframeTransformHolder holder))
        {
            return new Transform();
        }

        Transform transform = holder.bbspp$getEquipmentTransform();

        if (transform == null)
        {
            transform = new Transform();
            holder.bbspp$setEquipmentTransform(transform);
        }

        return transform;
    }

    public static void copy(Keyframe<?> from, Keyframe<?> to)
    {
        if (!(to instanceof EquipmentKeyframeTransformHolder holder))
        {
            return;
        }

        Transform source = get(from);
        holder.bbspp$setEquipmentTransform(source == null ? null : source.copy());
    }

    public static void write(MapType data, Keyframe<?> keyframe)
    {
        Transform transform = get(keyframe);

        if (transform != null && !transform.isDefault())
        {
            data.put(DATA_KEY, transform.toData());
        }
    }

    public static void read(MapType data, Keyframe<?> keyframe)
    {
        if (!(keyframe instanceof EquipmentKeyframeTransformHolder holder))
        {
            return;
        }

        if (!data.has(DATA_KEY))
        {
            holder.bbspp$setEquipmentTransform(null);
            return;
        }

        Transform transform = new Transform();
        transform.fromData(data.getMap(DATA_KEY));
        holder.bbspp$setEquipmentTransform(transform);
    }

    public static boolean equals(Keyframe<?> a, Keyframe<?> b)
    {
        return Objects.equals(defaulted(get(a)), defaulted(get(b)));
    }

    public static Transform interpolate(KeyframeChannel<?> channel, float tick)
    {
        KeyframeSegment<?> segment = channel.find(tick);

        if (segment == null)
        {
            return DEFAULT;
        }

        Transform a = defaulted(get(segment.a));
        Transform b = defaulted(get(segment.b));

        if (segment.isSame())
        {
            return a;
        }

        Transform preA = defaulted(get(segment.preA));
        Transform postB = defaulted(get(segment.postB));
        IInterp interpolation = segment.a.getInterpolation().getInterp();
        Transform result = new Transform();

        result.lerp(preA, a, b, postB, interpolation, segment.x);

        return result;
    }

    private static Transform defaulted(Transform transform)
    {
        return transform == null ? DEFAULT : transform;
    }
}
