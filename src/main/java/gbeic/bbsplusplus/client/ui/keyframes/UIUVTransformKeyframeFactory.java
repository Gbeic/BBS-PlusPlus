package gbeic.bbsplusplus.client.ui.keyframes;

import gbeic.bbsplusplus.client.ui.UVTransformEditor;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframes;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.factories.UIKeyframeFactory;
import mchorse.bbs_mod.utils.keyframes.Keyframe;
import org.joml.Vector4f;

/**
 * 模型纹理变换关键帧的属性编辑面板。
 * <p>
 * BBS 默认的 {@code Vector4f} 关键帧面板只显示四个无标签数字，用户无法判断每个分量的含义。
 * 这里复用表单面板的 UV 变换控件，把四个分量按“偏移”和“缩放”分成两行展示，
 * 并保留统一缩放切换按钮，确保关键帧编辑体验和表单预览界面一致。
 * </p>
 */
public class UIUVTransformKeyframeFactory extends UIKeyframeFactory<Vector4f>
{
    private final UVTransformEditor uvEditor;

    public UIUVTransformKeyframeFactory(Keyframe<Vector4f> keyframe, UIKeyframes editor)
    {
        super(keyframe, editor);

        this.uvEditor = new UVTransformEditor((value) -> this.setValue(new Vector4f(value)));
        this.uvEditor.setValue(keyframe.getValue());

        this.scroll.add(this.uvEditor);
    }

    @Override
    public void update()
    {
        super.update();

        this.uvEditor.setValue(this.keyframe.getValue());
    }
}
