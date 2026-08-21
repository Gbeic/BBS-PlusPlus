package gbeic.bbsplusplus.mixin.vfx;

import com.bbsvfx.bbsvfx.client.BbsVfxClient;
import gbeic.bbsplusplus.compat.vfx.VfxCoreShaderRegistrationGuard;
import gbeic.bbsplusplus.client.structure.VFXDestructionWandSelection;
import net.fabricmc.fabric.api.client.rendering.v1.CoreShaderRegistrationCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 修正 BBSVFX 核心着色器回调重复执行，并接管新版破坏魔杖的选区边框显示条件。
 * 着色器回调通过包装 Fabric 事件监听器实现单轮去重，魔杖边框则只在玩家手持时显示。
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

    /**
     * 注入目标：新版 {@code BbsVfxClient#renderSelection(WorldRenderContext)} 入口。
     * 注入原因：BBSVFX 原实现不检查玩家是否还手持破坏魔杖，选区完整时会一直显示预览边框。
     * 修改行为：未手持破坏魔杖时取消 VFX 自带边框渲染，选区数据仍保留给捕获功能使用。
     */
    @Inject(method = "renderSelection", at = @At("HEAD"), cancellable = true)
    private void bbspp$hideSelectionWithoutWand(WorldRenderContext context, CallbackInfo ci)
    {
        if (!VFXDestructionWandSelection.shouldRenderSelection(MinecraftClient.getInstance()))
        {
            ci.cancel();
        }
    }
}
