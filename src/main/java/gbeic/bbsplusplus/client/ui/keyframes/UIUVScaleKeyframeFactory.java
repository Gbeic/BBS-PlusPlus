package gbeic.bbsplusplus.client.ui.keyframes;

import gbeic.bbsplusplus.client.ui.UVScaleEditor;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframes;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.factories.UIKeyframeFactory;
import mchorse.bbs_mod.utils.keyframes.Keyframe;
import org.joml.Vector4f;

/**
 * 广告牌 UV 缩放关键帧的属性编辑面板。
 * <p>
 * 用带 U/V 颜色提示和统一缩放按钮的单行控件替代通用四分量编辑器，
 * 只展示实际使用的两个缩放分量。
 * </p>
 */
public class UIUVScaleKeyframeFactory extends UIKeyframeFactory<Vector4f>
{
    private final UVScaleEditor scaleEditor;

    public UIUVScaleKeyframeFactory(Keyframe<Vector4f> keyframe, UIKeyframes editor)
    {
        super(keyframe, editor);

        this.scaleEditor = new UVScaleEditor((value) -> this.setValue(new Vector4f(value)));
        this.scaleEditor.setValue(keyframe.getValue());
        this.scroll.add(this.scaleEditor);
    }

    @Override
    public void update()
    {
        super.update();
        this.scaleEditor.setValue(this.keyframe.getValue());
    }
}
