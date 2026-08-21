package gbeic.bbsplusplus.mixin;

import gbeic.bbsplusplus.client.ui.pose.PoseBoneSkipInterpolator;
import mchorse.bbs_mod.utils.keyframes.Keyframe;
import mchorse.bbs_mod.utils.keyframes.KeyframeSegment;
import mchorse.bbs_mod.utils.pose.Pose;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 在通用关键帧段生成 Pose 结果后接入逐骨骼跳帧插值。
 *
 * <p>选择通用段出口而不是替换 Pose 工厂，可以获得当前实际时间与完整所属轨道；仅当
 * 值类型为 Pose 且轨道确有跳过标记时才重算受影响骨骼，其余关键帧类型完全不变。</p>
 */
@Mixin(value = KeyframeSegment.class, remap = false)
public class KeyframeSegmentPoseBoneSkipMixin<T>
{
    @Shadow
    public Keyframe<T> a;

    @Shadow
    public float offset;

    /**
     * 注入目标：{@link KeyframeSegment#createInterpolated()} 返回最终复制值之前。
     * 注入原因：逐骨骼跳过需要访问所属轨道并越过中间关键帧寻找有效邻点。
     * 修改后的行为：Pose 结果仅覆盖受跳过标记影响的骨骼，其它数据保留原版插值结果。
     */
    @Inject(method = "createInterpolated", at = @At("RETURN"), cancellable = true)
    private void bbspp$interpolateSkippedPoseBones(CallbackInfoReturnable<T> cir)
    {
        T value = cir.getReturnValue();

        if (value instanceof Pose pose && this.a != null && this.a.getValue() instanceof Pose)
        {
            PoseBoneSkipInterpolator.apply(this.a, this.a.getTick() + this.offset, pose);
        }
    }
}
