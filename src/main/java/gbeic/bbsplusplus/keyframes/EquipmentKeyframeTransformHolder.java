package gbeic.bbsplusplus.keyframes;

import mchorse.bbs_mod.utils.pose.Transform;

/**
 * 为原版关键帧挂载 BBS++ 的装备槽位变换数据。
 *
 * <p>解决的问题：主手、副手和四个装备槽位的关键帧原本只保存 {@code ItemStack}，
 * 用户必须通过骨骼姿势间接控制物品位置。实现思路是在关键帧实例上保存一份独立
 * {@link Transform}，让物品选择和变换参数仍属于同一个可见轨道。</p>
 */
public interface EquipmentKeyframeTransformHolder
{
    Transform bbspp$getEquipmentTransform();

    void bbspp$setEquipmentTransform(Transform transform);
}
