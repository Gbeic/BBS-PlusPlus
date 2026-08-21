package gbeic.bbsplusplus.client.ui.pose;

import mchorse.bbs_mod.forms.FormUtils;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframeSheet;
import mchorse.bbs_mod.utils.keyframes.Keyframe;
import mchorse.bbs_mod.utils.keyframes.factories.KeyframeFactories;
import mchorse.bbs_mod.utils.pose.PoseTransform;

/**
 * 保存一次性姿势参数刷在关键帧切换期间需要延续的数据。
 *
 * <p>状态记录完整的骨骼参数快照、来源骨骼、来源关键帧以及所属 Form。兼容性判断允许
 * 同一 Form 的主姿势与所有叠加姿势轨道互相粘贴，同时阻止快照跨到其它模型或非 Pose
 * 轨道；关键帧身份则让“来源帧的来源骨骼”和“另一帧的同名骨骼”能够被正确区分。</p>
 */
public class PoseParameterBrushState
{
    private String sourceBone;
    private Keyframe<?> sourceKeyframe;
    private Form sourceForm;
    private PoseTransform snapshot;

    /** 用当前关键帧的指定骨骼参数武装格式刷。 */
    public void arm(Keyframe<?> keyframe, UIKeyframeSheet sheet, String bone, PoseTransform transform)
    {
        Form form = bbspp$getPoseForm(sheet);

        if (keyframe == null || bone == null || bone.isEmpty() || form == null || !bbspp$isPoseSheet(sheet))
        {
            this.clear();

            return;
        }

        this.sourceKeyframe = keyframe;
        this.sourceForm = form;
        this.sourceBone = bone;
        this.snapshot = transform == null ? new PoseTransform() : (PoseTransform) transform.copy();
    }

    /** 清空复制源并结束本次一次性格式刷。 */
    public void clear()
    {
        this.sourceBone = null;
        this.sourceKeyframe = null;
        this.sourceForm = null;
        this.snapshot = null;
    }

    /** 当前是否保存了完整且可用的复制源。 */
    public boolean isArmed()
    {
        return this.sourceBone != null && this.sourceKeyframe != null
            && this.sourceForm != null && this.snapshot != null;
    }

    /** 指定关键帧是否属于复制源 Form 下的主姿势或任一叠加姿势轨道。 */
    public boolean isCompatible(Keyframe<?> keyframe, UIKeyframeSheet sheet)
    {
        return this.isArmed() && keyframe != null && bbspp$isPoseSheet(sheet)
            && bbspp$getPoseForm(sheet) == this.sourceForm;
    }

    /** 指定目标是否正是来源帧中的来源骨骼。 */
    public boolean isSource(Keyframe<?> keyframe, UIKeyframeSheet sheet, String bone)
    {
        return this.isCompatible(keyframe, sheet)
            && keyframe == this.sourceKeyframe && this.sourceBone.equals(bone);
    }

    /** 获取只用于向目标执行深复制的参数快照。 */
    public PoseTransform getSnapshot()
    {
        return this.snapshot;
    }

    private static boolean bbspp$isPoseSheet(UIKeyframeSheet sheet)
    {
        if (sheet == null || sheet.id == null || sheet.channel.getFactory() != KeyframeFactories.POSE)
        {
            return false;
        }

        int separator = sheet.id.lastIndexOf(FormUtils.PATH_SEPARATOR);
        String property = separator < 0 ? sheet.id : sheet.id.substring(separator + FormUtils.PATH_SEPARATOR.length());

        return property.equals("pose") || property.startsWith("pose_overlay");
    }

    private static Form bbspp$getPoseForm(UIKeyframeSheet sheet)
    {
        if (sheet == null)
        {
            return null;
        }

        return sheet.property == null ? sheet.form : FormUtils.getForm(sheet.property);
    }
}
