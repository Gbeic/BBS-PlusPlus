package gbeic.bbsplusplus.client.ui.forms.editors.forms;

import gbeic.bbsplusplus.forms.AAAParticleForm;
import gbeic.bbsplusplus.client.ui.forms.editors.panels.UIAAAParticleFormPanel;
import mchorse.bbs_mod.l10n.L10n;
import mchorse.bbs_mod.ui.forms.editors.forms.UIForm;
import mchorse.bbs_mod.ui.utils.icons.Icons;

/**
 * AAA 粒子表单编辑器。
 *
 * 提供粒子特效选择、播放控制和变换设置的 UI 界面。
 */
public class UIAAAParticleForm extends UIForm<AAAParticleForm>
{
    public UIAAAParticleFormPanel particlePanel;

    public UIAAAParticleForm()
    {
        super();

        this.particlePanel = new UIAAAParticleFormPanel(this);

        this.registerPanel(this.particlePanel, L10n.lang("bbspp.ui.forms.editors.aaa_particle.title"), Icons.PARTICLE);
        this.registerDefaultPanels();

        this.defaultPanel = this.particlePanel;
    }
}