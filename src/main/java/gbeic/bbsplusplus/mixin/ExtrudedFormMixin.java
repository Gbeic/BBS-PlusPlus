package gbeic.bbsplusplus.mixin;

import gbeic.bbsplusplus.api.ExtrudedFormUVTransform;
import mchorse.bbs_mod.forms.forms.ExtrudedForm;
import mchorse.bbs_mod.settings.values.core.ValueColor;
import mchorse.bbs_mod.settings.values.misc.ValueVector4f;
import mchorse.bbs_mod.settings.values.numeric.ValueBoolean;
import mchorse.bbs_mod.settings.values.numeric.ValueFloat;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 为挤出形态补充表面纹理的偏移、缩放和旋转属性。
 * <p>
 * 偏移与缩放合并为一个向量属性，旋转沿用广告牌的角度语义；
 * 两项注册在纹理之后，使时间轴中的纹理相关轨道保持相邻。
 * </p>
 */
@Mixin(value = ExtrudedForm.class, remap = false)
public class ExtrudedFormMixin implements ExtrudedFormUVTransform
{
    @Shadow @Final public ValueColor color;
    @Shadow @Final public ValueBoolean billboard;
    @Shadow @Final public ValueBoolean shading;

    @Unique private ValueVector4f bbspp$uvTransform;
    @Unique private ValueFloat bbspp$uvRotation;
    @Unique private boolean bbspp$uvValuesAdded;

    /**
     * 注入目标：{@code ExtrudedForm} 构造结束。
     * 注入原因：原生挤出形态没有任何 UV 变换属性。
     * 修改行为：注册偏移缩放向量与旋转角度，并放到纹理轨道之后。
     */
    @Inject(method = "<init>", at = @At("TAIL"))
    private void bbspp$addUvTransform(CallbackInfo ci)
    {
        this.bbspp$ensureUvValues();
    }

    @Override
    public ValueVector4f bbspp$getUvTransform()
    {
        this.bbspp$ensureUvValues();

        return this.bbspp$uvTransform;
    }

    @Override
    public ValueFloat bbspp$getUvRotation()
    {
        this.bbspp$ensureUvValues();

        return this.bbspp$uvRotation;
    }

    @Unique
    private void bbspp$ensureUvValues()
    {
        if (this.bbspp$uvTransform == null)
        {
            this.bbspp$uvTransform = new ValueVector4f("bbspp_uv_transform", new Vector4f(0F, 0F, 1F, 1F));
            this.bbspp$uvRotation = new ValueFloat("bbspp_uv_rotation", 0F);
        }

        if (!this.bbspp$uvValuesAdded)
        {
            ExtrudedForm self = (ExtrudedForm) (Object) this;

            self.remove(this.color);
            self.remove(this.billboard);
            self.remove(this.shading);
            self.add(this.bbspp$uvTransform);
            self.add(this.bbspp$uvRotation);
            self.add(this.color);
            self.add(this.billboard);
            self.add(this.shading);

            this.bbspp$uvValuesAdded = true;
        }
    }
}
