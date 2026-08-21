package gbeic.bbsplusplus.mixin;

import gbeic.bbsplusplus.client.render.ModelUVTransformRuntime;
import gbeic.bbsplusplus.keyframes.EquipmentTransformRuntime;
import mchorse.bbs_mod.cubic.ModelInstance;
import mchorse.bbs_mod.cubic.model.ArmorSlot;
import mchorse.bbs_mod.cubic.model.ArmorType;
import mchorse.bbs_mod.forms.entities.IEntity;
import mchorse.bbs_mod.forms.forms.ModelForm;
import mchorse.bbs_mod.forms.renderers.ModelFormRenderer;
import mchorse.bbs_mod.obj.shapes.ShapeKeys;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.ui.framework.elements.utils.StencilMap;
import mchorse.bbs_mod.utils.MatrixStackUtils;
import mchorse.bbs_mod.utils.colors.Color;
import mchorse.bbs_mod.utils.pose.Transform;
import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.EquipmentSlot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * 把装备/手持物品关键帧里的附加变换应用到模型渲染。
 */
@Mixin(value = ModelFormRenderer.class, remap = false)
public class ModelFormRendererMixin
{
    /**
     * 注入目标：{@code ModelFormRenderer#renderModel} 调用 {@code ModelInstance#render} 的位置。
     * 注入原因：底层模型渲染器拿不到当前表单实例，但 UV 变换参数保存在表单上。
     * 修改行为：在一次模型绘制期间把当前表单的 UV 参数放入线程本地上下文，绘制结束后恢复。
     */
    @Redirect(
        method = "renderModel",
        at = @At(
            value = "INVOKE",
            target = "Lmchorse/bbs_mod/cubic/ModelInstance;render(Lnet/minecraft/client/util/math/MatrixStack;Ljava/util/function/Supplier;Lmchorse/bbs_mod/utils/colors/Color;IILmchorse/bbs_mod/ui/framework/elements/utils/StencilMap;Lmchorse/bbs_mod/obj/shapes/ShapeKeys;Ljava/util/function/Function;)V"
        )
    )
    private void bbspp$renderModelWithUvTransform(ModelInstance model, MatrixStack stack, Supplier<ShaderProgram> program, Color color, int light, int overlay, StencilMap stencilMap, ShapeKeys keys, Function<String, Link> textureResolver)
    {
        ModelForm form = ((ModelFormRenderer) (Object) this).getForm();

        ModelUVTransformRuntime.push(form, model);

        try
        {
            model.render(stack, program, color, light, overlay, stencilMap, keys, textureResolver);
        }
        finally
        {
            ModelUVTransformRuntime.pop();
        }
    }

    /**
     * 注入目标：{@code ModelFormRenderer#renderArmor} 中模型槽位变换应用之后。
     * 注入原因：盔甲关键帧现在可以在属性栏内保存额外坐标、缩放和旋转。
     * 修改行为：在模型配置的盔甲挂点变换后，继续叠加当前关键帧采样出的槽位变换。
     */
    @Inject(
        method = "renderArmor",
        at = @At(
            value = "INVOKE",
            target = "Lmchorse/bbs_mod/utils/MatrixStackUtils;applyTransform(Lnet/minecraft/client/util/math/MatrixStack;Lmchorse/bbs_mod/utils/pose/Transform;)V",
            shift = At.Shift.AFTER
        )
    )
    private void bbspp$applyArmorKeyframeTransform(IEntity target, MatrixStack stack, ArmorType type, ArmorSlot armorSlot, Color color, int overlay, int light, CallbackInfo ci)
    {
        this.bbspp$applyEquipmentTransform(target, stack, type.slot);
    }

    /**
     * 注入目标：{@code ModelFormRenderer#renderItems} 中模型槽位变换应用之后。
     * 注入原因：主手和副手关键帧现在可以在属性栏内保存额外坐标、缩放和旋转。
     * 修改行为：在模型配置的物品挂点变换后，继续叠加当前关键帧采样出的槽位变换。
     */
    @Inject(
        method = "renderItems",
        at = @At(
            value = "INVOKE",
            target = "Lmchorse/bbs_mod/utils/MatrixStackUtils;applyTransform(Lnet/minecraft/client/util/math/MatrixStack;Lmchorse/bbs_mod/utils/pose/Transform;)V",
            shift = At.Shift.AFTER
        )
    )
    private void bbspp$applyItemKeyframeTransform(IEntity target, ModelInstance model, MatrixStack stack, EquipmentSlot slot, ModelTransformationMode mode, List<ArmorSlot> items, Color color, int overlay, int light, CallbackInfo ci)
    {
        this.bbspp$applyEquipmentTransform(target, stack, slot);
    }

    private void bbspp$applyEquipmentTransform(IEntity target, MatrixStack stack, EquipmentSlot slot)
    {
        Transform transform = EquipmentTransformRuntime.get(target, slot);

        if (transform != null && !transform.isDefault())
        {
            MatrixStackUtils.applyTransform(stack, transform);
        }
    }
}
