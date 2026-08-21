package gbeic.bbsplusplus.mixin;

import gbeic.bbsplusplus.keyframes.EquipmentTransformRuntime;
import mchorse.bbs_mod.film.replays.ReplayKeyframes;
import mchorse.bbs_mod.forms.entities.IEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/**
 * 在回放关键帧应用后缓存六个装备槽位的附加变换。
 */
@Mixin(value = ReplayKeyframes.class, remap = false)
public class ReplayKeyframesMixin
{
    /**
     * 注入目标：{@link ReplayKeyframes#apply(int, IEntity, List)} 结束处。
     * 注入原因：应用装备物品后，需要让渲染器能拿到同一 tick 的装备槽位变换。
     * 修改行为：把六个装备槽位的 BBS++ 附加变换采样到运行时缓存。
     */
    @Inject(method = "apply(ILmchorse/bbs_mod/forms/entities/IEntity;Ljava/util/List;)V", at = @At("RETURN"))
    private void bbspp$cacheEquipmentTransforms(int tick, IEntity entity, List<String> groups, CallbackInfo ci)
    {
        EquipmentTransformRuntime.apply((ReplayKeyframes) (Object) this, tick, entity);
    }
}
