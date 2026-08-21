package gbeic.bbsplusplus.mixin;

import gbeic.bbsplusplus.compat.irlite.IrliteShaderCurveBridge;
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

    /**
     * 注入目标：{@code GameRenderer#render(float, long, boolean)} 入口。
     * 注入原因：IRLite 迁到 BBS 设置的光影参数由每帧渲染的曲线桥同步回其 BBS Value 字段。
     * 修改行为：在整帧渲染最外层把当前帧曲线写入的值同步给 IRLite 参数；未装 IRLite 时该方法为空操作。
     */
    @Inject(method = "render", at = @At("HEAD"))
    public void bbspp$applyIrLiteShaderCurves(float tickDelta, long startTime, boolean tick, CallbackInfo ci)
    {
        IrliteShaderCurveBridge.apply();
    }
}
