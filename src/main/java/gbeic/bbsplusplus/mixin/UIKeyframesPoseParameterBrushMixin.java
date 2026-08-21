package gbeic.bbsplusplus.mixin;

import gbeic.bbsplusplus.client.ui.pose.IPoseParameterBrushHost;
import gbeic.bbsplusplus.client.ui.pose.PoseParameterBrushState;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframeSheet;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframes;
import mchorse.bbs_mod.utils.keyframes.Keyframe;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 把姿势参数刷状态挂到生命周期稳定的关键帧时间轴上。
 *
 * <p>关键帧切换只会替换参数编辑器，不会替换 {@link UIKeyframes}，因此同一 Form 的主姿势
 * 与叠加姿势轨道之间可以安全地保留快照。切换到其它模型、非 Pose 轨道或整体更换时间轴
 * 内容时会立即清空，避免旧数据在不相关的属性中重新出现。</p>
 */
@Mixin(UIKeyframes.class)
public class UIKeyframesPoseParameterBrushMixin implements IPoseParameterBrushHost
{
    @Unique
    private final PoseParameterBrushState bbspp$poseParameterBrushState = new PoseParameterBrushState();

    @Override
    public PoseParameterBrushState bbspp$getPoseParameterBrushState()
    {
        return this.bbspp$poseParameterBrushState;
    }

    /**
     * 注入目标：时间轴把新选中的关键帧交给参数面板之前。
     * 注入原因：参数刷只允许在复制源所属 Form 的主姿势与叠加姿势轨道中使用。
     * 修改后的行为：选中其它模型或非 Pose 轨道时清空快照；兼容 Pose 轨道间改选以及短暂的空选择不会打断格式刷。
     */
    @Inject(method = "pickKeyframe", at = @At("HEAD"), remap = false)
    private void bbspp$clearPoseParameterBrushOnIncompatibleKeyframe(Keyframe<?> keyframe, CallbackInfo ci)
    {
        UIKeyframes self = (UIKeyframes) (Object) this;
        UIKeyframeSheet sheet = keyframe == null ? null : self.getGraph().getSheet(keyframe);

        if (keyframe != null && this.bbspp$poseParameterBrushState.isArmed()
            && !this.bbspp$poseParameterBrushState.isCompatible(keyframe, sheet))
        {
            this.bbspp$poseParameterBrushState.clear();
        }
    }

    /**
     * 注入目标：时间轴移除全部关键帧轨道之前。
     * 注入原因：切换影片、回放对象或重新装载轨道时，旧快照不能跟随稳定的时间轴控件残留。
     * 修改后的行为：在轨道内容被替换前结束本次格式刷。
     */
    @Inject(method = "removeAllSheets", at = @At("HEAD"), remap = false)
    private void bbspp$clearPoseParameterBrushOnSheetReset(CallbackInfo ci)
    {
        this.bbspp$poseParameterBrushState.clear();
    }
}
