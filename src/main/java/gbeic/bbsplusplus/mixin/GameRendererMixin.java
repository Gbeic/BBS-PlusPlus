package gbeic.bbsplusplus.mixin;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.util.Mth;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

 /**
  * 混入 GameRenderer 类，用于修改 FOV 计算逻辑。
  */

 @Mixin(GameRenderer.class)
public class GameRendererMixin {

    @Inject(method = "getFov", at = @At("RETURN"), cancellable = true)
    public void onGetFov(Camera camera, float tickDelta, boolean changingFov, CallbackInfoReturnable<Double> cir) {
        if (changingFov) {
            // 进行帧间插值，获得极其平滑的 FOV 乘数
            float mult = Mth.lerp(tickDelta, gbeic.bbsplusplus.BBSPlusPlusState.prevFovMultiplier, gbeic.bbsplusplus.BBSPlusPlusState.smoothedFovMultiplier);

            if (mult > 1.001F) {
                // 尊重玩家在游戏设置里的“FOV 效果缩放”选项 (0% ~ 100%)
                float scale = Minecraft.getInstance().options.fovEffectScale().get().floatValue();
                float finalMult = 1.0F + (mult - 1.0F) * scale;
                
                cir.setReturnValue(cir.getReturnValue() * finalMult);
            }
        }
    }
}
