package gbeic.bbsplusplus.forms;

import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.settings.values.base.BaseKeyframeFactoryValue;
import gbeic.bbsplusplus.keyframes.BBSPlusPlusKeyframeFactories;

public class ValueAAAParticleLink extends BaseKeyframeFactoryValue<Link>
{
    public ValueAAAParticleLink(String id, Link defaultValue)
    {
        super(id, BBSPlusPlusKeyframeFactories.AAA_EFFECT, defaultValue);
    }

    @Override
    public String toString()
    {
        return this.value == null ? "" : this.value.toString();
    }
}
