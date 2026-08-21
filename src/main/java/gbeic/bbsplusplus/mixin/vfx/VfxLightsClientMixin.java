package gbeic.bbsplusplus.mixin.vfx;

import com.bbsvfx.vfxlights.client.VfxLightsClient;
import gbeic.bbsplusplus.compat.vfx.VfxCoreShaderRegistrationGuard;
import net.fabricmc.fabric.api.client.rendering.v1.CoreShaderRegistrationCallback;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * 修正 VFXLIGHTS 核心着色器回调在单次资源重载中被重复执行的问题。
 * 复用统一的重载代次守卫，避免附属灯的多个着色器程序被成批重复创建。
 */
@Mixin(value = VfxLightsClient.class, remap = false)
public class VfxLightsClientMixin
{
    /**
     * 注入目标：{@code VfxLightsClient#onInitializeClient()} 内注册的 Fabric 事件监听器参数。
     * 注入原因：附属灯与 BBSVFX 共用会被重复触发的核心着色器注册事件。
     * 修改行为：仅包装核心着色器回调，使其每轮执行一次；其他世界渲染事件保持原行为。
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
