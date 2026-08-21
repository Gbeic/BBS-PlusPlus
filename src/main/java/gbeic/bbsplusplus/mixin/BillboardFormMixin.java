package gbeic.bbsplusplus.mixin;

import gbeic.bbsplusplus.api.BillboardFormUVScale;
import mchorse.bbs_mod.forms.forms.BillboardForm;
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
 * 为广告牌形态补充原生纹理变换缺失的 U/V 缩放。
 * <p>
 * 缩放使用一个向量属性保存，使 U/V 缩放共用一条关键帧轨道；
 * 注册时把它放在原生偏移之后、旋转之前，保持时间轴属性顺序连贯。
 * </p>
 */
@Mixin(value = BillboardForm.class, remap = false)
public class BillboardFormMixin implements BillboardFormUVScale
{
    @Shadow @Final public ValueFloat rotation;
    @Shadow @Final public ValueBoolean shading;

    @Unique private ValueVector4f bbspp$uvScale;
    @Unique private boolean bbspp$uvScaleAdded;

    /**
     * 注入目标：{@code BillboardForm} 构造结束。
     * 注入原因：原生广告牌只有 UV 偏移和旋转，没有 U/V 缩放。
     * 修改行为：注册一条包含 U/V 缩放的向量属性，并调整到原生旋转轨道之前。
     */
    @Inject(method = "<init>", at = @At("TAIL"))
    private void bbspp$addUvScale(CallbackInfo ci)
    {
        this.bbspp$ensureUvScale();
    }

    @Override
    public ValueVector4f bbspp$getUvScale()
    {
        this.bbspp$ensureUvScale();

        return this.bbspp$uvScale;
    }

    @Unique
    private void bbspp$ensureUvScale()
    {
        if (this.bbspp$uvScale == null)
        {
            this.bbspp$uvScale = new ValueVector4f("bbspp_uv_scale", new Vector4f(1F));
        }

        if (!this.bbspp$uvScaleAdded)
        {
            BillboardForm self = (BillboardForm) (Object) this;

            self.remove(this.rotation);
            self.remove(this.shading);
            self.add(this.bbspp$uvScale);
            self.add(this.rotation);
            self.add(this.shading);

            this.bbspp$uvScaleAdded = true;
        }
    }
}
