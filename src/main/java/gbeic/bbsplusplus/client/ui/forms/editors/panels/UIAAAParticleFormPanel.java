package gbeic.bbsplusplus.client.ui.forms.editors.panels;

import gbeic.bbsplusplus.forms.AAAParticleForm;
import gbeic.bbsplusplus.client.renderer.BBSEffectLoader;
import mchorse.bbs_mod.l10n.L10n;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.ui.forms.editors.forms.UIForm;
import mchorse.bbs_mod.ui.forms.editors.panels.UIFormPanel;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIButton;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIToggle;
import mchorse.bbs_mod.ui.framework.elements.input.UITrackpad;
import mchorse.bbs_mod.ui.utils.UI;

/**
 * AAA 粒子表单编辑面板。
 *
 * 提供特效选择、播放控制等属性编辑界面。
 */
public class UIAAAParticleFormPanel extends UIFormPanel<AAAParticleForm>
{
    public UIButton pickEffect;
    public UIButton reloadEffects;
    public UIToggle paused;
    public UIButton restart;
    public UIToggle loop;
    public UIToggle forceFreeze;
    public UITrackpad loopStart;
    public UITrackpad loopEnd;
    public UITrackpad speed;
    public UITrackpad particleScale;
    public UITrackpad dynamicInput0;
    public UITrackpad dynamicInput1;
    public UITrackpad dynamicInput2;
    public UITrackpad dynamicInput3;
    public UIButton trigger0;
    public UIButton trigger1;
    public UIButton trigger2;
    public UIButton trigger3;
    public UIToggle ignoreDepth;


    @SuppressWarnings("rawtypes")
    public UIAAAParticleFormPanel(UIForm editor)
    {
        super(editor);

        this.pickEffect = new UIButton(L10n.lang("bbspp.ui.forms.editors.aaa_particle.pick_effect"), (b) -> this.openPicker());

        this.reloadEffects = new UIButton(L10n.lang("bbspp.ui.forms.editors.aaa_particle.reload_effects"), (b) ->
        {
            BBSEffectLoader.markCacheDirty();
            this.getContext().notifySuccess(L10n.lang("bbspp.ui.forms.editors.aaa_particle.reload_success"));
        });

        this.paused = new UIToggle(L10n.lang("bbspp.ui.forms.editors.aaa_particle.paused"), (b) -> this.form.paused.set(b.getValue()));
        this.restart = new UIButton(L10n.lang("bbspp.ui.forms.editors.aaa_particle.restart"), (b) -> this.form.restart.set(true));
        this.loop = new UIToggle(L10n.lang("bbspp.ui.forms.editors.aaa_particle.loop"), (b) -> this.form.loop.set(b.getValue()));
        this.loop.tooltip(L10n.lang("bbspp.ui.forms.editors.aaa_particle.loop.tooltip"));
        
        this.forceFreeze = new UIToggle(L10n.lang("bbspp.ui.forms.editors.aaa_particle.force_freeze"), (b) -> this.form.forceFreeze.set(b.getValue()));
        this.forceFreeze.tooltip(L10n.lang("bbspp.ui.forms.editors.aaa_particle.force_freeze.tooltip"));

        this.ignoreDepth = new UIToggle(L10n.lang("bbspp.ui.forms.editors.aaa_particle.ignoreDepth"), (b) -> this.form.ignoreDepth.set(b.getValue()));
        this.ignoreDepth.tooltip(L10n.lang("bbspp.ui.forms.editors.aaa_particle.ignoreDepth.tooltip"));

        this.loopStart = new UITrackpad((v) -> {
            int start = v.intValue();
            this.form.loopStart.set(start);
            int end = this.form.loopEnd.get();
            if (end > 0 && end < start) {
                this.form.loopEnd.set(start);
                this.loopEnd.setValue(start);
            }
        }).limit(0, 500).integer();
        this.loopStart.tooltip(L10n.lang("bbspp.ui.forms.editors.aaa_particle.loopStart.tooltip"));
        
        this.loopEnd = new UITrackpad((v) -> {
            int end = v.intValue();
            int start = this.form.loopStart.get();
            if (end > 0 && end < start) {
                end = start;
                this.loopEnd.setValue(end);
            }
            this.form.loopEnd.set(end);
        }).limit(0, 500).integer();
        this.loopEnd.tooltip(L10n.lang("bbspp.ui.forms.editors.aaa_particle.loopEnd.tooltip"));

        this.speed = new UITrackpad((v) -> this.form.speed.set(v.floatValue()));
        this.speed.limit(0.01D, 10D);

        this.particleScale = new UITrackpad((v) -> this.form.particleScale.set(v.floatValue()));
        this.particleScale.limit(0.01D, 100D);

        // 动态输入参数 0-3
        this.dynamicInput0 = new UITrackpad((v) -> this.form.dynamicInput0.set(v.floatValue()));
        this.dynamicInput0.limit(-1000D, 1000D);
        this.dynamicInput1 = new UITrackpad((v) -> this.form.dynamicInput1.set(v.floatValue()));
        this.dynamicInput1.limit(-1000D, 1000D);
        this.dynamicInput2 = new UITrackpad((v) -> this.form.dynamicInput2.set(v.floatValue()));
        this.dynamicInput2.limit(-1000D, 1000D);
        this.dynamicInput3 = new UITrackpad((v) -> this.form.dynamicInput3.set(v.floatValue()));
        this.dynamicInput3.limit(-1000D, 1000D);

        // 触发器 0-3（通过脉冲队列触发，不干扰关键帧系统）
        this.trigger0 = new UIButton(L10n.lang("bbspp.ui.forms.editors.aaa_particle.trigger0"), (b) -> this.form.manualTriggerPulse[0] = true);
        this.trigger1 = new UIButton(L10n.lang("bbspp.ui.forms.editors.aaa_particle.trigger1"), (b) -> this.form.manualTriggerPulse[1] = true);
        this.trigger2 = new UIButton(L10n.lang("bbspp.ui.forms.editors.aaa_particle.trigger2"), (b) -> this.form.manualTriggerPulse[2] = true);
        this.trigger3 = new UIButton(L10n.lang("bbspp.ui.forms.editors.aaa_particle.trigger3"), (b) -> this.form.manualTriggerPulse[3] = true);
        this.trigger0.tooltip(L10n.lang("bbspp.ui.forms.editors.aaa_particle.trigger.tooltip"));
        this.trigger1.tooltip(L10n.lang("bbspp.ui.forms.editors.aaa_particle.trigger.tooltip"));
        this.trigger2.tooltip(L10n.lang("bbspp.ui.forms.editors.aaa_particle.trigger.tooltip"));
        this.trigger3.tooltip(L10n.lang("bbspp.ui.forms.editors.aaa_particle.trigger.tooltip"));

        this.options.add(UI.label(L10n.lang("bbspp.ui.forms.editors.aaa_particle.effect")), this.pickEffect);
        this.options.add(this.reloadEffects);
        this.options.add(this.restart);
        this.options.add(this.paused, this.loop, this.forceFreeze, this.ignoreDepth);
        this.options.add(UI.label(L10n.lang("bbspp.ui.forms.editors.aaa_particle.loop_range")), this.loopStart, this.loopEnd);
        this.options.add(UI.label(L10n.lang("bbspp.ui.forms.editors.aaa_particle.speed")), this.speed);
        this.options.add(UI.label(L10n.lang("bbspp.ui.forms.editors.aaa_particle.scale")), this.particleScale);
        this.options.add(UI.label(L10n.lang("bbspp.ui.forms.editors.aaa_particle.dynamic_input")), this.dynamicInput0, this.dynamicInput1, this.dynamicInput2, this.dynamicInput3);
        this.options.add(UI.label(L10n.lang("bbspp.ui.forms.editors.aaa_particle.triggers")), this.trigger0, this.trigger1, this.trigger2, this.trigger3);
    }

    private void openPicker()
    {
        gbeic.bbsplusplus.client.ui.utils.UIEffectPicker.open(this.getContext(), (link) ->
        {
            if (link != null)
            {
                this.form.effect.set(link);
            }
            else
            {
                this.form.effect.set(null);
            }
        });
    }



    private String getCurrentEffectId()
    {
        Link effect = this.form.effect.get();

        if (effect == null)
        {
            return null;
        }

        String path = effect.path;

        if (path == null)
        {
            return null;
        }

        if (path.startsWith("effeks/"))
        {
            path = path.substring(7);
        }

        if (path.endsWith(".efkefc"))
        {
            path = path.substring(0, path.length() - 7);
        }

        return effect.source + ":" + path;
    }

    @Override
    public void startEdit(AAAParticleForm form)
    {
        super.startEdit(form);

        String currentEffect = this.getCurrentEffectId();

        if (currentEffect != null)
        {
            this.pickEffect.label = L10n.lang("bbspp.ui.forms.editors.aaa_particle.pick_effect").format(currentEffect);
        }
        else
        {
            this.pickEffect.label = L10n.lang("bbspp.ui.forms.editors.aaa_particle.pick_effect");
        }

        // 更新 UI 值
        this.paused.setValue(form.paused.get());
        this.loop.setValue(form.loop.get());
        this.forceFreeze.setValue(form.forceFreeze.get());
        this.loopStart.setValue(form.loopStart.get());
        this.loopEnd.setValue(form.loopEnd.get());
        this.speed.setValue(form.speed.get());
        this.particleScale.setValue(form.particleScale.get());
        this.dynamicInput0.setValue(form.dynamicInput0.get());
        this.dynamicInput1.setValue(form.dynamicInput1.get());
        this.dynamicInput2.setValue(form.dynamicInput2.get());
        this.dynamicInput3.setValue(form.dynamicInput3.get());
        this.ignoreDepth.setValue(form.ignoreDepth.get());
    }

}
