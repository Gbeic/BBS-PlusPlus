package gbeic.bbsplusplus.mixin;

import gbeic.bbsplusplus.client.ui.keyframes.UIUVTransformKeyframeFactory;
import gbeic.bbsplusplus.client.ui.keyframes.UIUVScaleKeyframeFactory;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframeSheet;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframes;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.factories.UIKeyframeFactory;
import mchorse.bbs_mod.utils.StringUtils;
import mchorse.bbs_mod.utils.keyframes.Keyframe;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 为纹理变换与纹理缩放轨道替换关键帧属性面板。
 * <p>
 * 原版按 {@code Vector4f} 类型选择通用四格编辑器，但纹理变换四个分量有明确语义。
 * 因此对偏移缩放轨道使用完整 UV 编辑器，对广告牌缩放轨道使用双分量缩放编辑器，
 * 其它 {@code Vector4f} 轨道仍使用原版 UI。
 * </p>
 */
@Mixin(value = UIKeyframeFactory.class, remap = false)
public class UIKeyframeFactoryMixin
{
    /**
     * 注入目标：{@code UIKeyframeFactory#createPanel} 开头。
     * 注入原因：原版只按关键帧数据类型选择面板，无法给特定属性提供带语义的四分量编辑器。
     * 修改行为：按属性名返回与对应表单面板一致的 UV 变换或缩放编辑器。
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    @Inject(method = "createPanel", at = @At("HEAD"), cancellable = true)
    private static <T> void bbspp$createUvTransformPanel(Keyframe<T> keyframe, UIKeyframes editor, CallbackInfoReturnable<UIKeyframeFactory<T>> cir)
    {
        if (keyframe == null || editor == null || !(keyframe.getValue() instanceof Vector4f))
        {
            return;
        }

        UIKeyframeSheet sheet = editor.getGraph().getSheet(keyframe);

        if (sheet == null)
        {
            return;
        }

        String property = StringUtils.fileName(sheet.id);

        UIKeyframeFactory<T> factory;

        if ("bbspp_uv_transform".equals(property))
        {
            factory = (UIKeyframeFactory<T>) new UIUVTransformKeyframeFactory((Keyframe) keyframe, editor);
            cir.setReturnValue(factory);
        }
        else if ("bbspp_uv_scale".equals(property))
        {
            factory = (UIKeyframeFactory<T>) new UIUVScaleKeyframeFactory((Keyframe) keyframe, editor);
            cir.setReturnValue(factory);
        }
    }
}
