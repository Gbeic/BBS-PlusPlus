package gbeic.bbsplusplus.mixin.vfx;

import com.bbsvfx.bbsvfx.client.BbsVfxClient;
import gbeic.bbsplusplus.compat.vfx.VfxCoreShaderRegistrationGuard;
import net.fabricmc.fabric.api.client.rendering.v1.CoreShaderRegistrationCallback;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * 修正 BBSVFX 核心着色器回调在单次资源重载中被重复执行的问题。
 * 通过包装其 Fabric 事件监听器实现单轮去重，不修改 BBSVFX 的渲染参数与程序回调。
 */
@Mixin(value = BbsVfxClient.class, remap = false)
public class BbsVfxClientMixin
{
    /**
     * 注入目标：{@code BbsVfxClient#onInitializeClient()} 内注册的 Fabric 事件监听器参数。
     * 注入原因：核心着色器事件在当前加载链中一轮会触发多次，BBSVFX 原回调没有去重。
     * 修改行为：仅包装核心着色器回调，使其每轮执行一次；其他世界渲染事件原样返回。
     */
    @ModifyArg(
        method = "onInitializeClient",
        at = @At(value = "INVOKE", target = "Lnet/fabricmc/fabric/api/event/Event;register(Ljava/lang/Object;)V"),
        index = 0
    )
    private Object bbspp$guardCoreShaderRegistration(Object listener)
    {
        if (listener instanceof CoreShaderRegistrationCallback callback)
        {
            return VfxCoreShaderRegistrationGuard.wrap(callback);
        }

        return listener;
    }
}
