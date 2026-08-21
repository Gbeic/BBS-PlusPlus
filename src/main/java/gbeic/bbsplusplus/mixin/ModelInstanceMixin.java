package gbeic.bbsplusplus.mixin;

import com.mojang.blaze3d.systems.RenderSystem;
import gbeic.bbsplusplus.client.render.ModelUVTransformRuntime;
import mchorse.bbs_mod.client.BBSRendering;
import mchorse.bbs_mod.client.BBSShaders;
import mchorse.bbs_mod.cubic.IModel;
import mchorse.bbs_mod.cubic.ModelInstance;
import mchorse.bbs_mod.cubic.data.model.Model;
import mchorse.bbs_mod.cubic.render.CubicCubeRenderer;
import mchorse.bbs_mod.cubic.render.CubicRenderer;
import mchorse.bbs_mod.cubic.weld.WeldBinding;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.forms.forms.ModelForm;
import mchorse.bbs_mod.obj.shapes.ShapeKeys;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.ui.framework.elements.utils.StencilMap;
import mchorse.bbs_mod.utils.colors.Color;
import net.minecraft.client.gl.GlUniform;
import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.math.MatrixStack;
import org.joml.Matrix3f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.List;
import java.util.function.Function;

/**
 * 为模型 UV 变换提供单纹理 cubic 模型的 CPU 顶点路径。
 * <p>
 * 外层仍按模型原本的 VAO 状态选择 shader，只在 {@link ModelInstance#render} 内部改走 CPU，
 * 既能可靠修改 UV，也不会让无光影渲染从 BBS 模型 shader 错切到实体 shader。
 * </p>
 */
@Mixin(value = ModelInstance.class, remap = false)
public class ModelInstanceMixin
{
    @Shadow public IModel model;
    @Shadow public transient Form form;
    @Shadow public List<String> materials;

    @Shadow
    private void renderHybrid(MatrixStack stack, ShaderProgram shader, Color color, int light, int overlay, StencilMap stencilMap, ShapeKeys keys, Function<String, Link> textureResolver, Model model, List<WeldBinding> bindings)
    {}

    /**
     * 注入目标：{@code ModelInstance#render} 内部对 {@code isVAORendered()} 的调用。
     * 注入原因：UV 变换需要 CPU 改写顶点，但不能修改外层用于选择 shader 的 VAO 状态。
     * 修改行为：仅在实际绘制分支中让启用 UV 变换的单纹理 cubic 模型改走 CPU。
     */
    @Redirect(
        method = "render",
        at = @At(
            value = "INVOKE",
            target = "Lmchorse/bbs_mod/cubic/ModelInstance;isVAORendered()Z"
        )
    )
    private boolean bbspp$useCpuUvPathInsideRender(ModelInstance instance)
    {
        if (this.model instanceof Model
            && this.form instanceof ModelForm modelForm
            && this.bbspp$shouldUseCpuUvFallback(modelForm))
        {
            return false;
        }

        return instance.isVAORendered();
    }

    /**
     * 注入目标：{@code ModelInstance#render} 中调用 {@code renderHybrid} 的位置。
     * 注入原因：混合路径的 VAO 部分不能可靠使用 BBS++ 的 UV uniform。
     * 修改行为：单纹理 cubic 模型启用 UV 变换时整模改走 CPU，并保留当前环境应使用的 shader。
     */
    @Redirect(
        method = "render",
        at = @At(
            value = "INVOKE",
            target = "Lmchorse/bbs_mod/cubic/ModelInstance;renderHybrid(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/gl/ShaderProgram;Lmchorse/bbs_mod/utils/colors/Color;IILmchorse/bbs_mod/ui/framework/elements/utils/StencilMap;Lmchorse/bbs_mod/obj/shapes/ShapeKeys;Ljava/util/function/Function;Lmchorse/bbs_mod/cubic/data/model/Model;Ljava/util/List;)V"
        )
    )
    private void bbspp$renderHybridWithUvFallback(ModelInstance instance, MatrixStack stack, ShaderProgram shader, Color color, int light, int overlay, StencilMap stencilMap, ShapeKeys keys, Function<String, Link> textureResolver, Model model, List<WeldBinding> bindings)
    {
        if (this.form instanceof ModelForm modelForm
            && this.bbspp$shouldUseCpuUvFallback(modelForm))
        {
            ShaderProgram drawShader = stencilMap == null
                && !(BBSRendering.isIrisShadersEnabled() && BBSRendering.isRenderingWorld())
                ? BBSShaders.getModel()
                : shader;
            CubicCubeRenderer renderProcessor = new CubicCubeRenderer(light, overlay, stencilMap, keys);

            renderProcessor.setColor(color.r, color.g, color.b, color.a);
            RenderSystem.setShader(() -> drawShader);

            BufferBuilder builder = Tessellator.getInstance().getBuffer();

            builder.begin(VertexFormat.DrawMode.TRIANGLES, VertexFormats.POSITION_COLOR_TEXTURE_OVERLAY_LIGHT_NORMAL);
            CubicRenderer.processRenderModel(renderProcessor, builder, stack, model);
            bbspp$prepareCpuShader(drawShader);
            BufferRenderer.drawWithGlobalProgram(builder.end());

            return;
        }

        this.renderHybrid(stack, shader, color, light, overlay, stencilMap, keys, textureResolver, model, bindings);
    }

    /**
     * 注入目标：{@code ModelInstance#render} 的普通 CPU 缓冲绘制调用。
     * 注入原因：CPU 路径已经变换了法线与 UV，BBS 模型 shader 不能再次应用残留矩阵或 UV uniform。
     * 修改行为：绘制前恢复单位法线矩阵与默认 UV uniform，再调用原始缓冲绘制。
     */
    @Redirect(
        method = "render",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/render/BufferRenderer;drawWithGlobalProgram(Lnet/minecraft/client/render/BufferBuilder$BuiltBuffer;)V",
            remap = true
        )
    )
    private void bbspp$drawCpuUvWithOriginalLighting(BufferBuilder.BuiltBuffer buffer)
    {
        ShaderProgram shader = RenderSystem.getShader();

        if (ModelUVTransformRuntime.current().active())
        {
            bbspp$prepareCpuShader(shader);
        }

        BufferRenderer.drawWithGlobalProgram(buffer);
    }

    @Unique
    private static void bbspp$prepareCpuShader(ShaderProgram shader)
    {
        ModelUVTransformRuntime.clearShader(shader);

        if (shader == BBSShaders.getModel())
        {
            GlUniform normalMatrix = shader.getUniform("NormalMat");

            if (normalMatrix != null)
            {
                normalMatrix.set(new Matrix3f());
            }
        }
    }

    @Unique
    private boolean bbspp$shouldUseCpuUvFallback(ModelForm modelForm)
    {
        return ModelUVTransformRuntime.hasActive(modelForm) && this.materials.size() <= 1;
    }
}
