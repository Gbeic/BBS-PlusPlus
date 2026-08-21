package gbeic.bbsplusplus.mixin;

import gbeic.bbsplusplus.client.render.ModelUVTransformRuntime;
import mchorse.bbs_mod.cubic.render.vao.ModelVAORenderer;
import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 在 VAO 渲染路径的 shader uniform 设置阶段同步 UV 变换。
 * <p>
 * VAO 顶点数据是预烘焙的，不能每帧直接改数组；因此 BBS++ 给模型 shader 增加
 * {@code UVTransform} uniform，并在这里把当前表单参数传进去。
 * </p>
 */
@Mixin(value = ModelVAORenderer.class, remap = false)
public class ModelVAORendererMixin
{
    /**
     * 注入目标：{@code ModelVAORenderer#setupUniforms} 结束处。
     * 注入原因：原版只设置模型、投影、光照等 uniform，没有 BBS++ 的 UV 变换。
     * 修改行为：当当前 shader 声明了 {@code UVTransform} 时写入偏移和缩放参数。
     */
    @Inject(method = "setupUniforms", at = @At("TAIL"))
    private static void bbspp$setupUvTransformUniform(MatrixStack stack, ShaderProgram shader, CallbackInfo ci)
    {
        ModelUVTransformRuntime.applyToShader(shader);
    }

    /**
     * 注入目标：{@code ModelVAORenderer#render} 解绑 shader 前。
     * 注入原因：混合渲染模型可能先画 VAO 骨骼，再画 CPU 顶点骨骼。
     * 修改行为：VAO 绘制结束后把 uniform 恢复默认值，避免后续 CPU 顶点路径重复应用 UV 变换。
     */
    @Inject(
        method = "render",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/gl/ShaderProgram;unbind()V",
            shift = At.Shift.BEFORE
        )
    )
    private static void bbspp$clearUvTransformUniform(ShaderProgram shader, mchorse.bbs_mod.cubic.render.vao.IModelVAO modelVAO, MatrixStack stack, float r, float g, float b, float a, int light, int overlay, CallbackInfo ci)
    {
        ModelUVTransformRuntime.clearShader(shader);
    }
}
