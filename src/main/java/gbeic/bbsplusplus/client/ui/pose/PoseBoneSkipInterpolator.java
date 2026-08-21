package gbeic.bbsplusplus.client.ui.pose;

import mchorse.bbs_mod.utils.MathUtils;
import mchorse.bbs_mod.utils.interps.IInterp;
import mchorse.bbs_mod.utils.interps.Interpolation;
import mchorse.bbs_mod.utils.interps.Interpolations;
import mchorse.bbs_mod.utils.keyframes.Keyframe;
import mchorse.bbs_mod.utils.keyframes.KeyframeChannel;
import mchorse.bbs_mod.utils.pose.Pose;
import mchorse.bbs_mod.utils.pose.PoseTransform;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 对含有逐骨骼跳过标记的 Pose 轨道执行跨有效关键帧插值。
 *
 * <p>原版 Pose 工厂会把缺失骨骼当作默认变换，因此无法通过删除 B 帧数据达到 A→C
 * 的效果。本类只重算轨道中曾被标记过的骨骼：先跳过禁用帧寻找前后有效关键帧，
 * 再沿用前一有效帧的插值类型、强制时长与自动曲线邻点。没有标记的轨道完全不介入。</p>
 */
public class PoseBoneSkipInterpolator
{
    private static final PoseTransform DEFAULT = new PoseTransform();

    /**
     * 如轨道存在跳过标记，则在原版 Pose 结果上改写受影响骨骼；否则直接返回原结果。
     */
    public static Pose apply(Keyframe<?> source, float tick, Pose result)
    {
        if (source == null || result == null || !(source.getParent() instanceof KeyframeChannel<?> rawChannel))
        {
            return result;
        }

        List<?> rawKeyframes = rawChannel.getKeyframes();
        Set<String> affected = new LinkedHashSet<>();

        for (Object value : rawKeyframes)
        {
            if (value instanceof Keyframe<?> keyframe && keyframe.getValue() instanceof Pose pose)
            {
                affected.addAll(PoseBoneSkipData.getSkippedBones(pose));
            }
        }

        if (affected.isEmpty())
        {
            return result;
        }

        @SuppressWarnings("unchecked")
        List<Keyframe<Pose>> keyframes = (List<Keyframe<Pose>>) (List<?>) rawKeyframes;

        /* 插值结果是运行时临时值，不应该继续携带某个源关键帧的编辑元数据。 */
        PoseBoneSkipData.clear(result);

        for (String bone : affected)
        {
            PoseTransform transform = interpolateBone(keyframes, bone, tick);

            if (transform == null || transform.isDefault())
            {
                result.transforms.remove(bone);
            }
            else
            {
                result.transforms.put(bone, transform);
            }
        }

        return result;
    }

    private static PoseTransform interpolateBone(List<Keyframe<Pose>> keyframes, String bone, float tick)
    {
        int right = -1;

        for (int i = 0; i < keyframes.size(); i++)
        {
            Keyframe<Pose> keyframe = keyframes.get(i);

            if (!PoseBoneSkipData.isSkipped(keyframe.getValue(), bone) && keyframe.getTick() >= tick)
            {
                right = i;
                break;
            }
        }

        int left = -1;
        int leftStart = right < 0 ? keyframes.size() - 1 : right;

        for (int i = leftStart; i >= 0; i--)
        {
            Keyframe<Pose> keyframe = keyframes.get(i);

            if (!PoseBoneSkipData.isSkipped(keyframe.getValue(), bone) && keyframe.getTick() <= tick)
            {
                left = i;
                break;
            }
        }

        if (left < 0 && right < 0)
        {
            return null;
        }

        if (left < 0)
        {
            return copyTransform(keyframes.get(right).getValue(), bone);
        }

        if (right < 0)
        {
            return copyTransform(keyframes.get(left).getValue(), bone);
        }

        Keyframe<Pose> a = keyframes.get(left);
        Keyframe<Pose> b = keyframes.get(right);

        if (a == b || a.getTick() == b.getTick())
        {
            return copyTransform(a.getValue(), bone);
        }

        Keyframe<Pose> preA = findPreviousActive(keyframes, bone, left);
        Keyframe<Pose> postB = findNextActive(keyframes, bone, right);
        float duration = a.getDuration() > 0F ? a.getDuration() : b.getTick() - a.getTick();
        float x = MathUtils.clamp(duration == 0F ? 0F : (tick - a.getTick()) / duration, 0F, 1F);
        PoseTransform output = new PoseTransform();
        Interpolation interpolation = a.getInterpolation();

        if (interpolation.has(Interpolations.AUTO) || interpolation.has(Interpolations.AUTO_CLAMPED))
        {
            output.autoLerp(
                getTransform(preA.getValue(), bone),
                getTransform(a.getValue(), bone),
                getTransform(b.getValue(), bone),
                getTransform(postB.getValue(), bone),
                preA.getTick(), a.getTick(), b.getTick(), postB.getTick(),
                interpolation.has(Interpolations.AUTO_CLAMPED), x
            );
        }
        else
        {
            output.lerp(
                getTransform(preA.getValue(), bone),
                getTransform(a.getValue(), bone),
                getTransform(b.getValue(), bone),
                getTransform(postB.getValue(), bone),
                (IInterp) interpolation, x
            );
        }

        return output;
    }

    private static Keyframe<Pose> findPreviousActive(List<Keyframe<Pose>> keyframes, String bone, int index)
    {
        for (int i = index - 1; i >= 0; i--)
        {
            Keyframe<Pose> keyframe = keyframes.get(i);

            if (!PoseBoneSkipData.isSkipped(keyframe.getValue(), bone))
            {
                return keyframe;
            }
        }

        return keyframes.get(index);
    }

    private static Keyframe<Pose> findNextActive(List<Keyframe<Pose>> keyframes, String bone, int index)
    {
        for (int i = index + 1; i < keyframes.size(); i++)
        {
            Keyframe<Pose> keyframe = keyframes.get(i);

            if (!PoseBoneSkipData.isSkipped(keyframe.getValue(), bone))
            {
                return keyframe;
            }
        }

        return keyframes.get(index);
    }

    private static PoseTransform getTransform(Pose pose, String bone)
    {
        PoseTransform transform = pose.transforms.get(bone);

        return transform == null ? DEFAULT : transform;
    }

    private static PoseTransform copyTransform(Pose pose, String bone)
    {
        return (PoseTransform) getTransform(pose, bone).copy();
    }

    private PoseBoneSkipInterpolator()
    {}
}
