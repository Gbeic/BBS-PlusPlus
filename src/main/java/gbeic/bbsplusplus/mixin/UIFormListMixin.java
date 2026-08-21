package gbeic.bbsplusplus.mixin;

import mchorse.bbs_mod.ui.forms.UIFormList;
import gbeic.bbsplusplus.ui.morphing.UIBBSPPFormList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(UIFormList.class)
public class UIFormListMixin
{
    /**
     * 注入表单列表搜索过滤之后，让 BBS++ 新版伪装列表能同步切换侧边栏和搜索结果视图。
     */
    @Inject(method = "applySearchFilter", at = @At("TAIL"), remap = false)
    private void afterSearchFilter(String raw, CallbackInfo ci)
    {
        if ((Object) this instanceof UIBBSPPFormList)
        {
            ((UIBBSPPFormList) (Object) this).afterSearch(raw);
        }
    }
}
