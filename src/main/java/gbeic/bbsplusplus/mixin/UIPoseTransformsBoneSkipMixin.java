package gbeic.bbsplusplus.mixin;

import gbeic.bbsplusplus.client.ui.pose.PoseBoneSkipData;
import mchorse.bbs_mod.ui.film.replays.UIReplaysEditorUtils;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframes;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.factories.UIPoseKeyframeFactory;
import mchorse.bbs_mod.utils.keyframes.Keyframe;
import mchorse.bbs_mod.utils.pose.Pose;
import mchorse.bbs_mod.utils.pose.PoseTransform;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * 让变换面板和 Gizmo 对骨骼的实际编辑自动恢复该骨骼参与当前 Pose 帧。
 *
 * <p>通过重定向原版批量写入入口，把“清除跳过标记”和参数变更放进同一轮关键帧通知，
 * 避免产生两个撤销步骤；普通编辑、重置及录制期间写入均使用相同规则。</p>
 */
@Mixin(value = UIPoseKeyframeFactory.UIPoseTransforms.class, remap = false)
public class UIPoseTransformsBoneSkipMixin
{
    /**
     * 注入目标：{@code UIPoseTransforms#applyToSelection} 的多骨骼写入调用。
     * 注入原因：数字输入与 Gizmo 最终都从这里修改选中骨骼。
     * 修改后的行为：每根目标骨骼先恢复参与当前帧，再在同一撤销块中应用变换。
     */
    @Redirect(
        method = "applyToSelection",
        at = @At(
            value = "INVOKE",
            target = "Lmchorse/bbs_mod/ui/framework/elements/input/keyframes/factories/UIPoseKeyframeFactory$UIPoseFactoryEditor;applyBones(Lmchorse/bbs_mod/ui/framework/elements/input/keyframes/UIKeyframes;Lmchorse/bbs_mod/utils/keyframes/Keyframe;Ljava/util/List;Ljava/util/function/BiConsumer;)V"
        )
    )
    private void bbspp$unskipBeforeTransformEdit(UIKeyframes editor, Keyframe<?> keyframe, List<String> bones,
                                                 BiConsumer<String, PoseTransform> consumer)
    {
        UIPoseKeyframeFactory.UIPoseFactoryEditor.apply(editor, keyframe, (pose) ->
        {
            for (String bone : bones)
            {
                PoseBoneSkipData.setSkipped(pose, bone, false);
                consumer.accept(bone, pose.get(bone));
            }
        });
    }

    /**
     * 注入目标：{@code UIPoseTransforms#reset} 的多骨骼重置调用。
     * 注入原因：重置也是一次明确编辑，应立即让结果在动画中生效。
     * 修改后的行为：同一通知中恢复参与并重置每根选中骨骼。
     */
    @Redirect(
        method = "reset",
        at = @At(
            value = "INVOKE",
            target = "Lmchorse/bbs_mod/ui/framework/elements/input/keyframes/factories/UIPoseKeyframeFactory$UIPoseFactoryEditor;apply(Lmchorse/bbs_mod/ui/framework/elements/input/keyframes/UIKeyframes;Lmchorse/bbs_mod/utils/keyframes/Keyframe;Ljava/util/List;Ljava/util/function/Consumer;)V"
        )
    )
    private void bbspp$unskipBeforeTransformReset(UIKeyframes editor, Keyframe<?> keyframe, List<String> bones,
                                                  Consumer<PoseTransform> consumer)
    {
        UIPoseKeyframeFactory.UIPoseFactoryEditor.apply(editor, keyframe, (pose) ->
        {
            for (String bone : bones)
            {
                PoseBoneSkipData.setSkipped(pose, bone, false);
                consumer.accept(pose.get(bone));
            }
        });
    }

    /**
     * 注入目标：{@code UIPoseTransforms#applyDuringRecording} 的录制关键帧写入调用。
     * 注入原因：录制期间可能新建或覆盖 Pose 帧，跳过标记不能吞掉刚录制的动作。
     * 修改后的行为：对每个录制目标帧只通知一次，并在写参数前恢复对应骨骼参与。
     */
    @Redirect(
        method = "applyDuringRecording",
        at = @At(
            value = "INVOKE",
            target = "Lmchorse/bbs_mod/ui/framework/elements/input/keyframes/factories/UIPoseKeyframeFactory$UIPoseTransforms;applyRecordingBones(Lmchorse/bbs_mod/ui/framework/elements/input/keyframes/UIKeyframes;Lmchorse/bbs_mod/utils/keyframes/Keyframe;ILjava/util/List;Ljava/util/function/BiConsumer;)V"
        )
    )
    private void bbspp$unskipBeforeRecordedTransformEdit(UIKeyframes editor, Keyframe<?> keyframe, int tick,
                                                         List<String> bones,
                                                         BiConsumer<String, PoseTransform> consumer)
    {
        UIReplaysEditorUtils.forEachRecordedKeyframe(editor, keyframe, tick, (recorded) ->
        {
            Pose pose = (Pose) recorded.getValue();

            recorded.preNotify();

            for (String bone : bones)
            {
                PoseBoneSkipData.setSkipped(pose, bone, false);
                consumer.accept(bone, pose.get(bone));
            }

            recorded.postNotify();
        });
    }
}
