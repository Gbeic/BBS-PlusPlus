package gbeic.bbsplusplus.client.ui.pose;

/**
 * 为关键帧时间轴保存可跨编辑器重建继续使用的姿势参数刷状态。
 *
 * <p>BBS 在用户改选关键帧时会重新创建右侧参数编辑器，因此不能把快照只放在某个
 * 姿势编辑器实例中。实现该接口的时间轴会话作为稳定宿主，让同一 Pose 轨道里的新编辑器
 * 能继续读取原来的复制源，并在成功粘贴后统一清理。</p>
 */
public interface IPoseParameterBrushHost
{
    /** 获取当前时间轴会话唯一的姿势参数刷状态。 */
    PoseParameterBrushState bbspp$getPoseParameterBrushState();
}
