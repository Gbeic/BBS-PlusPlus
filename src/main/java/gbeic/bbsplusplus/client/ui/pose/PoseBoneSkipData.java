package gbeic.bbsplusplus.client.ui.pose;

import mchorse.bbs_mod.utils.pose.Pose;

import java.util.Collections;
import java.util.Set;

/**
 * 统一访问 Pose 的逐骨骼关键帧跳过状态。
 *
 * <p>把 Mixin 接口转换集中在这里，让插值器和界面无需依赖具体注入字段；如果遇到
 * 未被 Mixin 扩展的 Pose，也会安全退化为空状态。</p>
 */
public class PoseBoneSkipData
{
    /** 返回 Pose 中被跳过的骨骼名称。 */
    public static Set<String> getSkippedBones(Pose pose)
    {
        return pose instanceof IPoseBoneSkip skip ? skip.bbspp$getSkippedBones() : Collections.emptySet();
    }

    /** 查询骨骼是否在这个 Pose 关键帧中被跳过。 */
    public static boolean isSkipped(Pose pose, String bone)
    {
        return pose instanceof IPoseBoneSkip skip && skip.bbspp$isBoneSkipped(bone);
    }

    /** 设置骨骼是否在这个 Pose 关键帧中被跳过。 */
    public static void setSkipped(Pose pose, String bone, boolean skipped)
    {
        if (pose instanceof IPoseBoneSkip skip)
        {
            skip.bbspp$setBoneSkipped(bone, skipped);
        }
    }

    /** 清空 Pose 的全部跳过标记。 */
    public static void clear(Pose pose)
    {
        if (pose instanceof IPoseBoneSkip skip)
        {
            skip.bbspp$clearSkippedBones();
        }
    }

    private PoseBoneSkipData()
    {}
}
