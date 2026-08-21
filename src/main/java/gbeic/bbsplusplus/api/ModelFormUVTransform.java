package gbeic.bbsplusplus.api;

import mchorse.bbs_mod.settings.values.misc.ValueVector4f;
import org.joml.Vector4f;

/**
 * 暴露 BBS++ 为原版模型形态补充的贴图 UV 变换属性。
 * <p>
 * 原版 {@code ModelForm} 没有这些字段，外部代码通过该接口读取 Mixin 注入的
 * {@link ValueVector4f}，从而让表单 UI、关键帧系统和渲染路径都使用同一份数据。
 * 四个分量依次为 U 偏移、V 偏移、U 缩放、V 缩放。
 * </p>
 */
public interface ModelFormUVTransform
{
    ValueVector4f bbspp$getUvTransform();

    default Vector4f bbspp$getUvTransformValue()
    {
        ValueVector4f value = this.bbspp$getUvTransform();

        return value == null ? new Vector4f(0F, 0F, 1F, 1F) : value.get();
    }
}
