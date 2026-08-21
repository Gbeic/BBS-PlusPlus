package gbeic.bbsplusplus.mixin;

import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * 暴露 {@link GameRenderer#bobView(MatrixStack, float)}，用于复刻 Iris 移到 model-view 矩阵里的视野摇晃。
 */
@Mixin(GameRenderer.class)
public interface GameRendererAccessor
{
    /**
     * 在临时矩阵栈上调用原版视野摇晃逻辑，便于从模型方块渲染矩阵中剥离同一份扰动。
     */
    @Invoker("bobView")
    void bbspp$invokeBobView(MatrixStack matrices, float tickDelta);
}
