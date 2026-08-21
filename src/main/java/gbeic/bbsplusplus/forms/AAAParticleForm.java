package gbeic.bbsplusplus.forms;

import mchorse.bbs_mod.settings.values.numeric.ValueInt;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.settings.values.core.ValueLink;
import mchorse.bbs_mod.settings.values.numeric.ValueBoolean;
import mchorse.bbs_mod.settings.values.numeric.ValueFloat;


/**
 * AAA 粒子表单。
 *
 * 使用 AAA Particles Mod 渲染 Effekseer 粒子效果。
 * 支持循环播放、速度倍率、粒子缩放等功能。
 */
public class AAAParticleForm extends Form
{
    private static final String L10N_NONE = "bbspp.ui.forms.editors.aaa_particle.none";

    /* 特效路径 —— 相对于 assets/effeks/ 的 .efkefc 文件路径 */
    public final ValueAAAParticleLink effect = new ValueAAAParticleLink("effect", null);

    /* 预览图 —— 表单列表中显示的图标 */
    public final ValueLink preview = new ValueLink("preview", null);

    /* 播放控制 */
    public final ValueBoolean paused = new ValueBoolean("paused", false);
    public final ValueBoolean restart = new ValueBoolean("restart", false);
    public final ValueBoolean loop = new ValueBoolean("loop", true);
    public final ValueInt loopStart = new ValueInt("loopStart", 0);
    public final ValueInt loopEnd = new ValueInt("loopEnd", 0);
    public final mchorse.bbs_mod.settings.values.base.BaseValueBasic<Boolean> forceFreeze = new mchorse.bbs_mod.settings.values.base.BaseValueBasic<Boolean>("forceFreeze", false) {
        @Override
        public mchorse.bbs_mod.data.types.BaseType toData() {
            return new mchorse.bbs_mod.data.types.ByteType(this.get());
        }
        @Override
        public void fromData(mchorse.bbs_mod.data.types.BaseType data) {
            if (data instanceof mchorse.bbs_mod.data.types.NumericType) {
                this.set(((mchorse.bbs_mod.data.types.NumericType) data).boolValue());
            }
        }
    };

    /* 速度倍率 */
    public final ValueFloat speed = new ValueFloat("speed", 1F);

    /* 粒子缩放（独立于变换缩放） */
    public final ValueFloat particleScale = new ValueFloat("particleScale", 1F);

    /* 动态输入参数 0-3（Effekseer DynamicInput） */
    public final ValueFloat dynamicInput0 = new ValueFloat("dynamicInput0", 0F);
    public final ValueFloat dynamicInput1 = new ValueFloat("dynamicInput1", 0F);
    public final ValueFloat dynamicInput2 = new ValueFloat("dynamicInput2", 0F);
    public final ValueFloat dynamicInput3 = new ValueFloat("dynamicInput3", 0F);

    /* 触发器 0-3（Effekseer Trigger） */
    public final ValueBoolean trigger0 = new ValueBoolean("trigger0", false);
    public final ValueBoolean trigger1 = new ValueBoolean("trigger1", false);
    public final ValueBoolean trigger2 = new ValueBoolean("trigger2", false);
    public final ValueBoolean trigger3 = new ValueBoolean("trigger3", false);

    /* 透视渲染 */
    public final ValueBoolean ignoreDepth = new ValueBoolean("ignoreDepth", false);

    /**
     * UI 手动触发脉冲队列。
     * 不参与属性系统和关键帧，专门用于 UI 按钮的一次性脉冲触发。
     * 渲染器每帧消费后自动清零。
     */
    public final boolean[] manualTriggerPulse = new boolean[4];

    public AAAParticleForm()
    {
        super();

        this.preview.invisible();

        this.add(this.effect);
        this.add(this.preview);
        this.add(this.paused);
        this.add(this.restart);
        this.add(this.loop);
        this.add(this.loopStart);
        this.add(this.loopEnd);
        this.add(this.ignoreDepth);
        this.add(this.forceFreeze);
        this.add(this.speed);
        this.add(this.particleScale);
        this.add(this.dynamicInput0);
        this.add(this.dynamicInput1);
        this.add(this.dynamicInput2);
        this.add(this.dynamicInput3);
        this.add(this.trigger0);
        this.add(this.trigger1);
        this.add(this.trigger2);
        this.add(this.trigger3);
    }

    /** 本地化的"无"文字 */
    private static String localizedNone()
    {
        try
        {
            String text = mchorse.bbs_mod.l10n.L10n.lang(L10N_NONE).get();
            return text != null && !text.equals(L10N_NONE) ? text : "none";
        }
        catch (Exception e)
        {
            return "none";
        }
    }

    @Override
    public String getDefaultDisplayName()
    {
        Link effectLink = this.effect.get();

        if (effectLink == null)
        {
            return localizedNone();
        }

        String path = effectLink.path;

        if (path == null || path.isEmpty())
        {
            return localizedNone();
        }

        // 提取文件名（不含扩展名）
        int lastSlash = path.lastIndexOf('/');
        String name = lastSlash >= 0 ? path.substring(lastSlash + 1) : path;

        if (name.endsWith(".efkefc"))
        {
            name = name.substring(0, name.length() - 7);
        }

        return name;
    }
}