package gbeic.bbsplusplus.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import gbeic.bbsplusplus.keyframes.EquipmentTransformRuntime;
import mchorse.bbs_mod.cubic.ModelInstance;
import mchorse.bbs_mod.cubic.model.ArmorSlot;
import mchorse.bbs_mod.cubic.model.ArmorType;
import mchorse.bbs_mod.forms.entities.IEntity;
import mchorse.bbs_mod.forms.renderers.ModelFormRenderer;
import mchorse.bbs_mod.utils.MatrixStackUtils;
import mchorse.bbs_mod.utils.colors.Color;
import mchorse.bbs_mod.utils.pose.Transform;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemDisplayContext;
import org.joml.Quaternionf;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/**
 * 把装备/手持物品关键帧里的附加变换应用到模型渲染。
 */
@Mixin(value = ModelFormRenderer.class, remap = false)
public class ModelFormRendererMixin
{
    /** FSR 0.0.11 盔甲挂点变换末尾的 180° 翻转，用于等价还原旧版叠加顺序 */
    private static final Quaternionf ROTATE_X_180 = Axis.XP.rotationDegrees(180F);

    /**
     * 注入目标：{@code ModelFormRenderer#renderArmor} 中 {@code ArmorRenderer#renderArmorSlot} 调用之前。
     * 注入原因：FSR 0.0.11 把挂点变换抽进了私有方法 {@code applyArmorTransform}，{@code renderArmor}
     * 方法体内已不再直接调用 {@link MatrixStackUtils#applyTransform}，旧的注入点扫描不到目标而崩溃；
     * 而 {@code renderArmorSlot} 调用位于全部挂点变换（含末尾 180° 翻转）之后且整个方法只有一处，
     * 用它作为新的锚点不会重复触发。
     * 修改行为：在模型配置的盔甲挂点变换后，继续叠加当前关键帧采样出的槽位变换；由于注入点在
     * 挂点变换末尾的 180° 翻转之后，叠加前先撤销该翻转、应用关键帧变换后再恢复，使叠加顺序
     * 与旧版“翻转前叠加”完全等价，保证旧工程关键帧视觉不变。
     */
    @Inject(
        method = "renderArmor",
        at = @At(
            value = "INVOKE",
            target = "Lmchorse/bbs_mod/cubic/render/vanilla/ArmorRenderer;renderArmorSlot(Lcom/mojang/blaze3d/vertex/PoseStack;Lorg/joml/Matrix4f;FFLnet/minecraft/client/renderer/MultiBufferSource;Lmchorse/bbs_mod/forms/entities/IEntity;Lnet/minecraft/world/entity/EquipmentSlot;Lmchorse/bbs_mod/cubic/model/ArmorType;I)V",
            shift = At.Shift.BEFORE
        )
    )
    private void bbspp$applyArmorKeyframeTransform(IEntity target, PoseStack stack, ArmorType type, ArmorSlot armorSlot, Color color, int overlay, int light, CallbackInfo ci)
    {
        Transform transform = EquipmentTransformRuntime.get(target, type.slot);

        if (transform != null && !transform.isDefault())
        {
            // 先撤销挂点变换末尾的 180° 翻转，叠加关键帧变换后再恢复，还原旧版叠加顺序
            stack.mulPose(ROTATE_X_180);
            MatrixStackUtils.applyTransform(stack, transform);
            stack.mulPose(ROTATE_X_180);
        }
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
            target = "Lmchorse/bbs_mod/utils/MatrixStackUtils;applyTransform(Lcom/mojang/blaze3d/vertex/PoseStack;Lmchorse/bbs_mod/utils/pose/Transform;)V",
            shift = At.Shift.AFTER
        )
    )
    private void bbspp$applyItemKeyframeTransform(IEntity target, ModelInstance model, PoseStack stack, EquipmentSlot slot, ItemDisplayContext mode, List<ArmorSlot> items, Color color, int overlay, int light, CallbackInfo ci)
    {
        this.bbspp$applyEquipmentTransform(target, stack, slot);
    }

    private void bbspp$applyEquipmentTransform(IEntity target, PoseStack stack, EquipmentSlot slot)
    {
        Transform transform = EquipmentTransformRuntime.get(target, slot);

        if (transform != null && !transform.isDefault())
        {
            MatrixStackUtils.applyTransform(stack, transform);
        }
    }
}
