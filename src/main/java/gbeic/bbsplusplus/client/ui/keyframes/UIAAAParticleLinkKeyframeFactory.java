package gbeic.bbsplusplus.client.ui.keyframes;

import mchorse.bbs_mod.l10n.L10n;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIButton;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframes;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.factories.UIKeyframeFactory;
import mchorse.bbs_mod.utils.keyframes.Keyframe;
import gbeic.bbsplusplus.client.ui.utils.UIEffectPicker;

/**
 * AAA 粒子链接关键帧工厂。
 *
 * 这是一个自定义的 UIKeyframeFactory，用于创建和编辑 AAA 粒子效果的链接关键帧。它在关键帧编辑界面添加了一个按钮，点击后会打开特效选择器，允许用户选择一个 Effekseer 特效文件，并将选中的特效链接设置为关键帧的值。
 */

public class UIAAAParticleLinkKeyframeFactory extends UIKeyframeFactory<Link>
{
    public UIAAAParticleLinkKeyframeFactory(Keyframe<Link> keyframe, UIKeyframes editor)
    {
        super(keyframe, editor);

        this.scroll.add(new UIButton(L10n.lang("bbspp.ui.forms.editors.aaa_particle.pick_effect"), (b) ->
        {
            UIEffectPicker.open(this.getContext(), (link) ->
            {
                this.editor.getGraph().setValue(link, true);
            });
        }));
    }
}
