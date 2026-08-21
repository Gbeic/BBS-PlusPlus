package gbeic.bbsplusplus.mixin;

import mchorse.bbs_mod.settings.ui.UIValueMap;
import mchorse.bbs_mod.settings.values.base.BaseValue;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.utils.UILabel;
import mchorse.bbs_mod.ui.utils.UI;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Collections;
import java.util.List;

@Mixin(UIValueMap.class)
public class UIValueMapMixin
{
    @Inject(method = "create", at = @At("HEAD"), cancellable = true, remap = false)
    private static void onCreate(BaseValue value, UIElement element, CallbackInfoReturnable<List<UIElement>> cir)
    {
        if (value.getId().startsWith("title_"))
        {
            // 创建一个标签元素替代原有的按钮
            UILabel label = UI.label(mchorse.bbs_mod.l10n.L10n.lang("bbs.config.bbspp." + value.getId()), 20, 0xffffffff);
            label.labelAnchor(0, 0.5F);
            // 增加背景色和一点边距，使其看起来像个分割小标题
            label.background(0x44000000).marginTop(4).marginBottom(2);
            
            cir.setReturnValue(Collections.singletonList(label));
        }
    }
}
