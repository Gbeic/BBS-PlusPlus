package gbeic.bbsplusplus.client.ui.pose;

/**
 * 为姿势关键帧编辑器提供一次性骨骼参数刷状态。
 *
 * <p>单选时保存主选骨骼并允许列表或模型视图自由指定目标；多选时分别保存每根骨骼，
 * 仅允许在兼容目标姿势帧中按名称整组粘贴。两种模式成功后都自动退出。</p>
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

    /** 当前是否为禁止自由骨骼映射的多骨骼批量模式。 */
    boolean bbspp$isParameterBrushBatch();

    /** 指定骨骼是否为当前参数刷的复制源。 */
    boolean bbspp$isParameterBrushSource(String bone);

    /** 指定骨骼是否会在当前目标帧接受多骨骼同名粘贴。 */
    boolean bbspp$isParameterBrushTarget(String bone);

    /** 指定骨骼在当前姿势关键帧中是否存在非默认参数。 */
    boolean bbspp$isPoseBoneModified(String bone);
}
