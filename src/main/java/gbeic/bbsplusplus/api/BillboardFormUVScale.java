package gbeic.bbsplusplus.api;

import mchorse.bbs_mod.settings.values.misc.ValueVector4f;
import org.joml.Vector4f;

/**
 * 暴露广告牌形态缺失的 U/V 缩放属性。
 * <p>
 * 原生广告牌已经提供 UV 偏移和旋转，因此这里只用一个向量属性补充 U/V 缩放，
 * 让两个缩放分量共用一条关键帧轨道。
 * </p>
 */
public interface BillboardFormUVScale
{
    ValueVector4f bbspp$getUvScale();

    default Vector4f bbspp$getUvScaleValue()
    {
        ValueVector4f value = this.bbspp$getUvScale();

        return value == null || value.get() == null ? new Vector4f(1F) : value.get();
    }
}
