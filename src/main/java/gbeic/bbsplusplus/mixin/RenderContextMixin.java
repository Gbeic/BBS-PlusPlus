package gbeic.bbsplusplus.mixin;

import mod.chloeprime.aaaparticles.client.internal.RenderContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = RenderContext.class, remap = false)
public class RenderContextMixin {
    
    /**
     * BBS++ 深度修复：
     * 强行阻止 AAA Particles 将世界粒子的渲染推迟到手臂（Hand）渲染之后或之后的不同阶段。
     * 强制在 LevelRenderer.renderLevel 的末尾立刻渲染，这使得：
     * 1. 粒子在渲染时，手部尚未渲染，因此手部总是能在粒子渲染之后正常绘制并产生正确的深度遮挡。
     * 2. 在 Iris 光影下，粒子也会在 Iris 的地形 composite 之前渲染，使得 X-Ray 粒子能被 Iris 的独立手部渲染正确遮挡。
     */
    @Inject(method = "renderLevelAfterHand", at = @At("HEAD"), cancellable = true)
    private static void bbspp$disableDeferredRenderingForSodium(CallbackInfoReturnable<Boolean> cir) {
        cir.setReturnValue(false);
    }

    @Inject(method = "renderLevelDeferred", at = @At("HEAD"), cancellable = true)
    private static void bbspp$disableDeferredLevelRendering(CallbackInfoReturnable<Boolean> cir) {
        cir.setReturnValue(false);
    }
}
