package gbeic.bbsplusplus.keyframes;

import mchorse.bbs_mod.film.replays.ReplayKeyframes;
import mchorse.bbs_mod.forms.entities.IEntity;
import mchorse.bbs_mod.utils.keyframes.KeyframeChannel;
import mchorse.bbs_mod.utils.pose.Transform;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

import java.util.EnumMap;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * 保存当前渲染帧内每个实体的装备槽位变换采样结果。
 *
 * <p>解决的问题：模型渲染器只能拿到当前 {@link IEntity}，不知道它来自哪个 Replay
 * 和当前 tick。实现思路是在回放关键帧应用/渲染帧准备阶段采样六个装备槽位变换，
 * 再由渲染器按实体与槽位读取。</p>
 */
public class EquipmentTransformRuntime
{
    private static final Map<IEntity, EnumMap<EquipmentSlot, Transform>> TRANSFORMS = new WeakHashMap<>();

    public static void apply(ReplayKeyframes keyframes, float tick, IEntity entity)
    {
        if (keyframes == null || entity == null)
        {
            return;
        }

        EnumMap<EquipmentSlot, Transform> transforms = TRANSFORMS.computeIfAbsent(entity, (k) -> new EnumMap<>(EquipmentSlot.class));

        put(transforms, EquipmentSlot.MAINHAND, keyframes.mainHand, tick);
        put(transforms, EquipmentSlot.OFFHAND, keyframes.offHand, tick);
        put(transforms, EquipmentSlot.HEAD, keyframes.armorHead, tick);
        put(transforms, EquipmentSlot.CHEST, keyframes.armorChest, tick);
        put(transforms, EquipmentSlot.LEGS, keyframes.armorLegs, tick);
        put(transforms, EquipmentSlot.FEET, keyframes.armorFeet, tick);
    }

    public static Transform get(IEntity entity, EquipmentSlot slot)
    {
        EnumMap<EquipmentSlot, Transform> transforms = TRANSFORMS.get(entity);

        return transforms == null ? null : transforms.get(slot);
    }

    private static void put(EnumMap<EquipmentSlot, Transform> transforms, EquipmentSlot slot, KeyframeChannel<ItemStack> channel, float tick)
    {
        Transform transform = EquipmentKeyframeTransforms.interpolate(channel, tick);

        if (transform == null || transform.isDefault())
        {
            transforms.remove(slot);
        }
        else
        {
            transforms.put(slot, transform.copy());
        }
    }
}
