package gbeic.bbsplusplus.api;

import mchorse.bbs_mod.settings.values.misc.ValueVector4f;
import mchorse.bbs_mod.settings.values.numeric.ValueFloat;
import org.joml.Vector4f;

/**
 * 暴露挤出形态的完整表面 UV 变换属性。
 * <p>
 * 四维向量依次保存 U/V 像素偏移和 U/V 缩放，旋转使用独立浮点属性；
 * 该变换只作用于挤出网格表面的纹理坐标，不改变由原贴图 Alpha 生成的轮廓。
 * </p>
 */
public interface ExtrudedFormUVTransform
{
    ValueVector4f bbspp$getUvTransform();

    ValueFloat bbspp$getUvRotation();

    default Vector4f bbspp$getUvTransformValue()
    {
        ValueVector4f value = this.bbspp$getUvTransform();

        return value == null || value.get() == null ? new Vector4f(0F, 0F, 1F, 1F) : value.get();
    }
}
