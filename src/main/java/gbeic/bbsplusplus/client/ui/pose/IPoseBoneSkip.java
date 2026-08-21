package gbeic.bbsplusplus.client.ui.pose;

import java.util.Set;

/**
 * 为原版 Pose 附加“当前关键帧跳过这些骨骼”的持久状态。
 *
 * <p>跳过只影响该 Pose 值作为关键帧参与插值的资格，不删除骨骼参数；因此恢复参与时
 * 原值仍然存在。具体的序列化、复制、镜像与相等性由 Pose Mixin 负责。</p>
 */
public interface IPoseBoneSkip
{
    /** 返回当前 Pose 中被跳过的骨骼名称只读视图。 */
    Set<String> bbspp$getSkippedBones();

    /** 查询指定骨骼是否在当前 Pose 关键帧中被跳过。 */
    boolean bbspp$isBoneSkipped(String bone);

    /** 设置指定骨骼在当前 Pose 关键帧中是否被跳过。 */
    void bbspp$setBoneSkipped(String bone, boolean skipped);

    /** 清空当前 Pose 的全部跳过标记。 */
    void bbspp$clearSkippedBones();
}
