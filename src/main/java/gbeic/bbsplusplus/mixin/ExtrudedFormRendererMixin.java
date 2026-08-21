package gbeic.bbsplusplus.mixin;

import com.mojang.blaze3d.systems.RenderSystem;
import gbeic.bbsplusplus.api.ExtrudedFormUVTransform;
import gbeic.bbsplusplus.api.ModelVAODataAccess;
import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.client.BBSShaders;
import mchorse.bbs_mod.cubic.render.vao.IModelVAO;
import mchorse.bbs_mod.cubic.render.vao.ModelVAOData;
import mchorse.bbs_mod.cubic.render.vao.ModelVAORenderer;
import mchorse.bbs_mod.forms.forms.ExtrudedForm;
import mchorse.bbs_mod.forms.renderers.ExtrudedFormRenderer;
import mchorse.bbs_mod.forms.renderers.FormRenderer;
import mchorse.bbs_mod.graphics.texture.Texture;
import mchorse.bbs_mod.resources.Link;
import net.minecraft.client.gl.GlUniform;
import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.math.MatrixStack;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 为挤出形态应用动态的表面 UV 偏移、缩放和旋转。
 * <p>
 * 默认参数仍走原生共享 VAO；只有纹理变换生效时才从保留的网格数组提交 CPU 顶点，
 * 从而兼容原生 shader 与 Iris，并避免修改同纹理实例共享的 GPU 缓冲。
 * </p>
 */
@Mixin(value = ExtrudedFormRenderer.class, remap = false)
public abstract class ExtrudedFormRendererMixin extends FormRenderer<ExtrudedForm>
{
    private ExtrudedFormRendererMixin(ExtrudedForm form)
    {
        super(form);
    }

    /**
     * 注入目标：{@code ExtrudedFormRenderer#renderModel} 调用 {@code ModelVAORenderer#render} 的位置。
     * 注入原因：挤出网格的 UV 已烘焙进 VAO，运行时参数无法直接改写。
     * 修改行为：参数为默认值时调用原渲染；参数生效时使用原始网格数组提交变换后的 UV。
     */
    @Redirect(
        method = "renderModel",
        at = @At(
            value = "INVOKE",
            target = "Lmchorse/bbs_mod/cubic/render/vao/ModelVAORenderer;render(Lnet/minecraft/client/gl/ShaderProgram;Lmchorse/bbs_mod/cubic/render/vao/IModelVAO;Lnet/minecraft/client/util/math/MatrixStack;FFFFII)V"
        )
    )
    private void bbspp$renderWithUvTransform(ShaderProgram shader, IModelVAO modelVao, MatrixStack matrices, float r, float g, float b, float a, int light, int overlay)
    {
        if (!(this.form instanceof ExtrudedFormUVTransform uv)
            || !(modelVao instanceof ModelVAODataAccess access)
            || !bbspp$isUvTransformActive(uv))
        {
            ModelVAORenderer.render(shader, modelVao, matrices, r, g, b, a, light, overlay);

            return;
        }

        ModelVAOData data = access.bbspp$getModelVaoData();

        if (data == null)
        {
            ModelVAORenderer.render(shader, modelVao, matrices, r, g, b, a, light, overlay);

            return;
        }

        this.bbspp$renderCpu(shader, data, matrices, r, g, b, a, light, overlay, uv);
    }

    private void bbspp$renderCpu(ShaderProgram shader, ModelVAOData data, MatrixStack matrices, float r, float g, float b, float a, int light, int overlay, ExtrudedFormUVTransform uv)
    {
        VertexFormat format = shader.getFormat();
        boolean simpleFormat = format == VertexFormats.POSITION_TEXTURE_LIGHT_COLOR;
        BufferBuilder builder = Tessellator.getInstance().getBuffer();
        Matrix4f position = matrices.peek().getPositionMatrix();
        Matrix3f normal = matrices.peek().getNormalMatrix();
        float[] vertices = data.vertices();
        float[] normals = data.normals();
        float[] textureCoordinates = data.texCoords();
        Vector4f transform = uv.bbspp$getUvTransformValue();
        float rotation = uv.bbspp$getUvRotation().get();
        Link link = this.form.texture.get();
        Texture texture = link == null ? null : BBSModClient.getTextures().getTexture(link);
        float width = texture == null ? 1F : Math.max(1F, texture.width);
        float height = texture == null ? 1F : Math.max(1F, texture.height);
        float radians = (float) Math.toRadians(rotation);
        float cosine = (float) Math.cos(radians);
        float sine = (float) Math.sin(radians);

        builder.begin(VertexFormat.DrawMode.TRIANGLES, format);

        for (int i = 0, count = vertices.length / 3; i < count; i++)
        {
            float localU = (textureCoordinates[i * 2] - 0.5F) * transform.z + transform.x / width;
            float localV = (textureCoordinates[i * 2 + 1] - 0.5F) * transform.w + transform.y / height;
            float u = 0.5F + cosine * localU - sine * localV;
            float v = 0.5F + sine * localU + cosine * localV;
            float x = vertices[i * 3];
            float y = vertices[i * 3 + 1];
            float z = vertices[i * 3 + 2];

            if (simpleFormat)
            {
                builder.vertex(position, x, y, z)
                    .texture(u, v)
                    .light(light)
                    .color(r, g, b, a)
                    .next();
            }
            else
            {
                builder.vertex(position, x, y, z)
                    .color(r, g, b, a)
                    .texture(u, v)
                    .overlay(overlay)
                    .light(light)
                    .normal(normal, normals[i * 3], normals[i * 3 + 1], normals[i * 3 + 2])
                    .next();
            }
        }

        RenderSystem.setShader(() -> shader);
        bbspp$prepareCpuShader(shader);
        BufferRenderer.drawWithGlobalProgram(builder.end());
    }

    private static boolean bbspp$isUvTransformActive(ExtrudedFormUVTransform uv)
    {
        Vector4f value = uv.bbspp$getUvTransformValue();

        return Math.abs(value.x) > 1.0E-6F
            || Math.abs(value.y) > 1.0E-6F
            || Math.abs(value.z - 1F) > 1.0E-6F
            || Math.abs(value.w - 1F) > 1.0E-6F
            || Math.abs(uv.bbspp$getUvRotation().get()) > 1.0E-6F;
    }

    private static void bbspp$prepareCpuShader(ShaderProgram shader)
    {
        GlUniform uvTransform = shader.getUniform("UVTransform");

        if (uvTransform != null)
        {
            uvTransform.set(0F, 0F, 1F, 1F);
        }

        if (shader == BBSShaders.getModel())
        {
            GlUniform normalMatrix = shader.getUniform("NormalMat");

            if (normalMatrix != null)
            {
                normalMatrix.set(new Matrix3f());
            }
        }
    }
}
