package gbeic.bbsplusplus.mixin;

import gbeic.bbsplusplus.compat.vfx.VfxCoreShaderRegistrationGuard;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.util.math.MathHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

 /**
  * 混入 GameRenderer 类，用于修改 FOV 计算逻辑。
  */

 @Mixin(GameRenderer.class)
public class GameRendererMixin {

    /**
     * 注入目标：{@code GameRenderer#loadPrograms(ResourceFactory)} 入口。
     * 注入原因：当前渲染加载链会在一次资源重载中重复触发 Fabric 核心着色器注册事件。
     * 修改行为：开始新一轮注册代次，让 VFX 兼容包装器在本轮只执行一次各自的着色器回调。
     */
    @Inject(method = "loadPrograms", at = @At("HEAD"))
    private void bbspp$beginVfxCoreShaderReload(CallbackInfo ci)
    {
        VfxCoreShaderRegistrationGuard.beginReload();
    }

    @Inject(method = "getFov", at = @At("RETURN"), cancellable = true)
    public void onGetFov(Camera camera, float tickDelta, boolean changingFov, CallbackInfoReturnable<Double> cir) {
        if (changingFov) {
            // 进行帧间插值，获得极其平滑的 FOV 乘数
            float mult = MathHelper.lerp(tickDelta, gbeic.bbsplusplus.BBSPlusPlusState.prevFovMultiplier, gbeic.bbsplusplus.BBSPlusPlusState.smoothedFovMultiplier);
            if (mult > 1.001F) {
                // 尊重玩家在游戏设置里的“FOV 效果缩放”选项 (0% ~ 100%)
                float scale = MinecraftClient.getInstance().options.getFovEffectScale().getValue().floatValue();
                float finalMult = 1.0F + (mult - 1.0F) * scale;
                cir.setReturnValue(cir.getReturnValue() * finalMult);
            }
        }
    }
}
