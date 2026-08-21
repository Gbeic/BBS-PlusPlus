package gbeic.bbsplusplus.client.ui.pose;

import mchorse.bbs_mod.forms.FormUtils;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframeSheet;
import mchorse.bbs_mod.utils.keyframes.Keyframe;
import mchorse.bbs_mod.utils.keyframes.factories.KeyframeFactories;
import mchorse.bbs_mod.utils.pose.PoseTransform;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 保存一次性姿势参数刷在关键帧切换期间需要延续的数据。
 *
 * <p>状态按骨骼名称记录一个或多个完整参数快照，并保存来源关键帧以及所属 Form。兼容性判断允许
 * 同一 Form 的主姿势与所有叠加姿势轨道互相粘贴，同时阻止快照跨到其它模型或非 Pose
 * 轨道。单骨骼快照可以自由映射到其它骨骼，多骨骼快照只允许在目标帧按名称整组写入。</p>
 */
public class PoseParameterBrushState
{
    private Keyframe<?> sourceKeyframe;
    private Form sourceForm;
    private final Map<String, PoseTransform> snapshots = new LinkedHashMap<>();

    /** 用当前关键帧中选中的一个或多个骨骼参数武装格式刷。 */
    public void arm(Keyframe<?> keyframe, UIKeyframeSheet sheet, Map<String, PoseTransform> transforms)
    {
        Form form = bbspp$getPoseForm(sheet);

        if (keyframe == null || transforms == null || transforms.isEmpty()
            || form == null || !bbspp$isPoseSheet(sheet))
        {
            this.clear();

            return;
        }

        this.sourceKeyframe = keyframe;
        this.sourceForm = form;
        this.snapshots.clear();

        for (Map.Entry<String, PoseTransform> entry : transforms.entrySet())
        {
            String bone = entry.getKey();

            if (bone != null && !bone.isEmpty())
            {
                PoseTransform transform = entry.getValue();

                this.snapshots.put(bone, transform == null
                    ? new PoseTransform()
                    : (PoseTransform) transform.copy());
            }
        }

        if (this.snapshots.isEmpty())
        {
            this.clear();
        }
    }

    /** 清空复制源并结束本次一次性格式刷。 */
    public void clear()
    {
        this.sourceKeyframe = null;
        this.sourceForm = null;
        this.snapshots.clear();
    }

    /** 当前是否保存了完整且可用的复制源。 */
    public boolean isArmed()
    {
        return !this.snapshots.isEmpty() && this.sourceKeyframe != null && this.sourceForm != null;
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
            && keyframe == this.sourceKeyframe && this.snapshots.containsKey(bone);
    }

    /** 当前是否保存了需要按名称整组粘贴的多个骨骼。 */
    public boolean isBatch()
    {
        return this.snapshots.size() > 1;
    }

    /** 当前关键帧是否为多骨骼格式刷的兼容目标帧。 */
    public boolean isBatchDestination(Keyframe<?> keyframe, UIKeyframeSheet sheet)
    {
        return this.isBatch() && this.isCompatible(keyframe, sheet) && keyframe != this.sourceKeyframe;
    }

    /** 指定骨骼是否会在当前目标帧接受多骨骼同名粘贴。 */
    public boolean isBatchTarget(Keyframe<?> keyframe, UIKeyframeSheet sheet, String bone)
    {
        return this.isBatchDestination(keyframe, sheet) && this.snapshots.containsKey(bone);
    }

    /** 指定关键帧是否为最初取得快照的来源帧。 */
    public boolean isSourceKeyframe(Keyframe<?> keyframe)
    {
        return keyframe == this.sourceKeyframe;
    }

    /** 获取单骨骼模式中只用于向目标执行深复制的参数快照。 */
    public PoseTransform getSnapshot()
    {
        return this.snapshots.isEmpty() ? null : this.snapshots.values().iterator().next();
    }

    /** 获取多骨骼模式中按名称排列的只读参数快照。 */
    public Map<String, PoseTransform> getSnapshots()
    {
        return Collections.unmodifiableMap(this.snapshots);
    }

    /** 获取当前保存的骨骼数量。 */
    public int size()
    {
        return this.snapshots.size();
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
