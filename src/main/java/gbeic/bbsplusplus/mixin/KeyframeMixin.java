package gbeic.bbsplusplus.mixin;

import gbeic.bbsplusplus.keyframes.EquipmentKeyframeTransformHolder;
import gbeic.bbsplusplus.keyframes.EquipmentKeyframeTransforms;
import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.utils.keyframes.Keyframe;
import gbeic.bbsplusplus.BBSAddonsSettings;
import mchorse.bbs_mod.utils.pose.Transform;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Keyframe Mixin
 *
 * 1. 在 Keyframe 的 setTick 方法和构造方法中添加一个变量修改器，检查是否启用了 preventNegativeKeyframes 设置，如果启用且 tick 小于 0，则将 tick 强制设置为 0。这可以防止用户在编辑关键帧时不小心将时间轴拖动到负数，从而导致回放时出现异常行为。
 * 2. 通过这种方式，用户在使用 BBS++ 的新版本时，无需担心误操作导致的负数关键帧问题，即使在旧版本中创建了负数关键帧的录像文件，在新版本中也会自动修正为 0。
 * 3. 为装备/手持物品关键帧保存附加变换数据，让同一条轨道同时编辑物品与姿态。
 */

@Mixin(value = Keyframe.class, remap = false)
public class KeyframeMixin implements EquipmentKeyframeTransformHolder
{
    @Unique
    private Transform bbspp$equipmentTransform;

    /**
     * 注入目标：{@link Keyframe#setTick(float, boolean)} 入口处的 tick 参数。
     * 注入原因：负数关键帧会让时间轴与回放逻辑出现异常位置。
     * 修改行为：开启防负数关键帧设置时，将传入的负数 tick 收束为 0。
     */
    @ModifyVariable(
        method = "setTick(FZ)V",
        at = @At("HEAD"), argsOnly = true, ordinal = 0
    )
    private float bbspp$preventNegativeTick(float tick)
    {
        return clampNegativeTick(tick);
    }

    /**
     * 注入目标：{@link Keyframe} 完整构造方法入口处的 tick 参数。
     * 注入原因：构造方法在调用 {@code this(...)} 之前不能访问实例 handler。
     * 修改行为：用静态 handler 在构造阶段提前收束负数 tick，兼容新版 Mixin 的校验。
     */
    @ModifyVariable(
        method = "<init>(Ljava/lang/String;Lmchorse/bbs_mod/utils/keyframes/factories/IKeyframeFactory;FLjava/lang/Object;)V",
        at = @At("HEAD"), argsOnly = true, ordinal = 0
    )
    private static float bbspp$preventNegativeConstructorTick(float tick)
    {
        return clampNegativeTick(tick);
    }

    private static float clampNegativeTick(float tick)
    {
        if (tick < 0 && BBSAddonsSettings.preventNegativeKeyframes != null && BBSAddonsSettings.preventNegativeKeyframes.get())
        {
            return 0F;
        }
        
        return tick;
    }

    @Override
    public Transform bbspp$getEquipmentTransform()
    {
        return this.bbspp$equipmentTransform;
    }

    @Override
    public void bbspp$setEquipmentTransform(Transform transform)
    {
        this.bbspp$equipmentTransform = transform;
    }

    /**
     * 注入目标：{@link Keyframe#toData()} 返回前。
     * 注入原因：原版关键帧只序列化 value/interp/形状等通用字段，没有装备槽位变换字段。
     * 修改行为：当关键帧附带非默认变换时，把它写入关键帧顶层数据。
     */
    @Inject(method = "toData", at = @At("RETURN"), cancellable = true)
    private void bbspp$writeEquipmentTransform(CallbackInfoReturnable<BaseType> cir)
    {
        BaseType type = cir.getReturnValue();

        if (type instanceof MapType map)
        {
            EquipmentKeyframeTransforms.write(map, (Keyframe<?>) (Object) this);
        }
    }

    /**
     * 注入目标：{@link Keyframe#fromData(BaseType)} 读取结束后。
     * 注入原因：需要从关键帧顶层恢复 BBS++ 的装备槽位变换字段。
     * 修改行为：存在字段时读取为 {@link Transform}，不存在时保持默认变换。
     */
    @Inject(method = "fromData", at = @At("RETURN"))
    private void bbspp$readEquipmentTransform(BaseType data, CallbackInfo ci)
    {
        if (data instanceof MapType map)
        {
            EquipmentKeyframeTransforms.read(map, (Keyframe<?>) (Object) this);
        }
        else
        {
            this.bbspp$equipmentTransform = null;
        }
    }

    /**
     * 注入目标：{@link Keyframe#copy(Keyframe)} 复制结束后。
     * 注入原因：原版复制只处理原生字段，拖拽复制和内部快照会遗漏 BBS++ 附加变换。
     * 修改行为：把源关键帧的装备槽位变换深拷贝到目标关键帧。
     */
    @Inject(method = "copy", at = @At("RETURN"))
    private void bbspp$copyEquipmentTransform(Keyframe<?> keyframe, CallbackInfo ci)
    {
        EquipmentKeyframeTransforms.copy(keyframe, (Keyframe<?>) (Object) this);
    }

    /**
     * 注入目标：{@link Keyframe#copyOverExtra(Keyframe)} 复制结束后。
     * 注入原因：新增关键帧会复用前一个关键帧的插值/形状等额外字段，装备变换也应该一起继承。
     * 修改行为：把源关键帧的装备槽位变换复制到新关键帧。
     */
    @Inject(method = "copyOverExtra", at = @At("RETURN"))
    private void bbspp$copyOverEquipmentTransform(Keyframe<?> keyframe, CallbackInfo ci)
    {
        EquipmentKeyframeTransforms.copy(keyframe, (Keyframe<?>) (Object) this);
    }

    /**
     * 注入目标：{@link Keyframe#equals(Object)} 原版判断为相等之后。
     * 注入原因：撤销合并和脏状态判断需要感知 BBS++ 附加变换的差异。
     * 修改行为：原版字段相同时，继续比较装备槽位变换。
     */
    @Inject(method = "equals", at = @At("RETURN"), cancellable = true)
    private void bbspp$compareEquipmentTransform(Object obj, CallbackInfoReturnable<Boolean> cir)
    {
        if (cir.getReturnValue() && obj instanceof Keyframe<?> keyframe)
        {
            cir.setReturnValue(EquipmentKeyframeTransforms.equals((Keyframe<?>) (Object) this, keyframe));
        }
    }
}
