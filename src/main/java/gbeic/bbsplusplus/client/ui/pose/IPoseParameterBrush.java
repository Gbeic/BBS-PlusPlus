package gbeic.bbsplusplus.client.ui.pose;

/**
 * 为姿势关键帧编辑器提供一次性骨骼参数刷状态。
 *
 * <p>实现会在启用时保存主选骨骼的完整参数快照，并把骨骼列表点选与模型视图点选
 * 汇入同一个目标处理入口。命中有效目标后只粘贴一次并自动退出，避免切换选择时丢失复制源。</p>
 */
public interface IPoseParameterBrush
{
    /**
     * 参数刷处理一次骨骼点选后的结果。
     */
    enum Result
    {
        /** 参数刷未启用，或点中的不是有效骨骼。 */
        IGNORED,
        /** 点中了复制源本身，保持参数刷等待状态。 */
        SOURCE,
        /** 已把快照粘贴到目标骨骼并自动退出。 */
        APPLIED
    }

    /** 尝试把当前参数刷快照应用到指定骨骼。 */
    Result bbspp$applyParameterBrush(String bone);

    /** 当前是否正在等待下一根目标骨骼。 */
    boolean bbspp$isParameterBrushArmed();

    /** 指定骨骼是否为当前参数刷的复制源。 */
    boolean bbspp$isParameterBrushSource(String bone);

    /** 指定骨骼在当前姿势关键帧中是否存在非默认参数。 */
    boolean bbspp$isPoseBoneModified(String bone);
}
