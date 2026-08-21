package gbeic.bbsplusplus.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * 暴露 {@code GameRenderer#bobView(PoseStack, float)}，用于复刻 Iris 移到 model-view 矩阵里的视野摇晃。
 */
@Mixin(GameRenderer.class)
public interface GameRendererAccessor
{
    /**
     * 在临时矩阵栈上调用原版视野摇晃逻辑，便于从模型方块渲染矩阵中剥离同一份扰动。
     */
    @Invoker("bobView")
    void bbspp$invokeBobView(PoseStack matrices, float tickDelta);
}
