package gbeic.bbsplusplus.keyframes;

import mchorse.bbs_mod.utils.keyframes.factories.KeyframeFactories;
import mchorse.bbs_mod.utils.keyframes.factories.LinkKeyframeFactory;

public class BBSPlusPlusKeyframeFactories
{
    // 复用原有的 LinkKeyframeFactory，因为它只需要处理序列化和反序列化
    public static final LinkKeyframeFactory AAA_EFFECT = new LinkKeyframeFactory();

    public static void register()
    {
        KeyframeFactories.FACTORIES.put("aaa_effect", AAA_EFFECT);
    }
}
