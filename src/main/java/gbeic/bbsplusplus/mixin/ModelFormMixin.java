package gbeic.bbsplusplus.mixin;

import gbeic.bbsplusplus.api.ModelFormUVTransform;
import mchorse.bbs_mod.forms.forms.ModelForm;
import mchorse.bbs_mod.settings.values.misc.ValueVector4f;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 为原版模型形态补充贴图 UV 变换属性。
 * <p>
 * 该属性以一个 {@link Vector4f} 保存 U/V 偏移与 U/V 缩放，因此可以保存到形态数据，
 * 并由 BBS 的表单属性关键帧系统自动创建单条向量关键帧轨道。
 * </p>
 */
@Mixin(value = ModelForm.class, remap = false)
public class ModelFormMixin implements ModelFormUVTransform
{
    @Unique
    private ValueVector4f bbspp$uvTransform;

    @Unique
    private boolean bbspp$uvValuesAdded;

    /**
     * 注入目标：{@code ModelForm} 构造结束。
     * 注入原因：原版模型形态只有纹理链接，没有运行时 UV 变换参数。
     * 修改行为：把 BBS++ 的 UV 偏移和缩放属性追加到表单值列表，交给原版序列化与关键帧系统处理。
     */
    @Inject(method = "<init>", at = @At("TAIL"))
    private void bbspp$addUvTransformValues(CallbackInfo ci)
    {
        this.bbspp$ensureUvValues();
    }

    @Override
    public ValueVector4f bbspp$getUvTransform()
    {
        this.bbspp$ensureUvValues();

        return this.bbspp$uvTransform;
    }

    @Unique
    private void bbspp$ensureUvValues()
    {
        if (this.bbspp$uvTransform == null)
        {
            this.bbspp$uvTransform = new ValueVector4f("bbspp_uv_transform", new Vector4f(0F, 0F, 1F, 1F));
        }

        if (!this.bbspp$uvValuesAdded)
        {
            ModelForm self = (ModelForm) (Object) this;

            self.add(this.bbspp$uvTransform);

            this.bbspp$uvValuesAdded = true;
        }
    }
}
