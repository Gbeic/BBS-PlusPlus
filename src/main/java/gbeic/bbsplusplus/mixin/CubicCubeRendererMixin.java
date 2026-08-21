package gbeic.bbsplusplus.mixin;

import gbeic.bbsplusplus.client.render.ModelUVTransformRuntime;
import mchorse.bbs_mod.cubic.data.model.ModelGroup;
import mchorse.bbs_mod.ui.framework.elements.utils.StencilMap;
import mchorse.bbs_mod.utils.MathUtils;
import mchorse.bbs_mod.utils.interps.Lerps;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.LightmapTextureManager;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 在 CPU 顶点渲染路径上应用模型贴图 UV 变换。
 * <p>
 * BBS 的非 VAO 模型、部分焊接模型和禁用 VAO 的模型会逐顶点写入 {@link BufferBuilder}。
 * 这里只在当前表单存在 UV 变换时接管私有 emit 方法，其余情况完全走原版逻辑。
 * </p>
 */
@Mixin(targets = "mchorse.bbs_mod.cubic.render.CubicCubeRenderer", remap = false)
public class CubicCubeRendererMixin
{
    @Shadow protected float r;
    @Shadow protected float g;
    @Shadow protected float b;
    @Shadow protected float a;
    @Shadow protected int light;
    @Shadow protected int overlay;
    @Shadow protected StencilMap stencilMap;

    /**
     * 注入目标：{@code CubicCubeRenderer#emit} 写入单个顶点前。
     * 注入原因：CPU 路径的纹理坐标已经是普通 float，shader uniform 不一定可用。
     * 修改行为：当前模型启用 UV 变换时，按表单参数改写 U/V 后再写入顶点。
     */
    @Inject(method = "emit", at = @At("HEAD"), cancellable = true)
    private void bbspp$emitUvTransformedVertex(BufferBuilder builder, ModelGroup group, float x, float y, float z, float u, float v, Vector3f normal, CallbackInfo ci)
    {
        ModelUVTransformRuntime.Transform transform = ModelUVTransformRuntime.current();

        if (!transform.active())
        {
            return;
        }

        float transformedU = u * transform.scaleU() + transform.normalizedOffsetU();
        float transformedV = v * transform.scaleV() + transform.normalizedOffsetV();

        builder.vertex(x, y, z)
            .color(this.r * group.color.r, this.g * group.color.g, this.b * group.color.b, this.a * group.color.a)
            .texture(transformedU, transformedV)
            .overlay(this.overlay);

        if (this.stencilMap != null)
        {
            builder.light(this.stencilMap.increment ? group.index : 0, 0);
        }
        else
        {
            int lu = (int) Lerps.lerp(this.light & '\uffff', LightmapTextureManager.MAX_BLOCK_LIGHT_COORDINATE, MathUtils.clamp(group.lighting, 0F, 1F));
            int lv = this.light >> 16 & '\uffff';

            builder.light(lu, lv);
        }

        builder.normal(normal.x, normal.y, normal.z).next();
        ci.cancel();
    }
}
