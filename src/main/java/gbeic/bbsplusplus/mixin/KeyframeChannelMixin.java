package gbeic.bbsplusplus.mixin;

import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.utils.keyframes.KeyframeChannel;
import mchorse.bbs_mod.utils.keyframes.factories.IKeyframeFactory;
import mchorse.bbs_mod.utils.keyframes.factories.KeyframeFactories;
import gbeic.bbsplusplus.keyframes.BBSPlusPlusKeyframeFactories;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * KeyframeChannel Mixin
 *
 * 1. 在 KeyframeChannel 从数据加载时检查是否为旧版本的 "effect" 通道，并且工厂是原版的 LINK 工厂。如果是，则自动升级为 BBS++ 的 AAA_EFFECT 工厂，以兼容旧版本的录像文件。
 * 2. 通过这种方式，用户在使用 BBS++ 的新版本时，无需手动修改旧录像文件中的通道工厂设置，即可享受 AAA 粒子效果带来的增强功能。
 */

@Mixin(value = KeyframeChannel.class, remap = false)
public class KeyframeChannelMixin
{
    @Shadow
    private IKeyframeFactory<?> factory;

    @Inject(method = "fromData", at = @At(value = "INVOKE", target = "Lmchorse/bbs_mod/settings/values/core/ValueList;fromData(Lmchorse/bbs_mod/data/types/BaseType;)V", shift = At.Shift.BEFORE))
    private void bbspp$modifyFactory(BaseType data, CallbackInfo ci)
    {
        KeyframeChannel<?> channel = (KeyframeChannel<?>) (Object) this;
        // 如果是从旧版本读取的 "effect" 通道，且使用了原版的 LINK 工厂，则自动升级为 AAA_EFFECT
        if ("effect".equals(channel.getId()) && this.factory == KeyframeFactories.LINK)
        {
            this.factory = BBSPlusPlusKeyframeFactories.AAA_EFFECT;
        }
    }
}
