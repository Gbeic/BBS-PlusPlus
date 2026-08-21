package gbeic.bbsplusplus.mixin;

import mchorse.bbs_mod.ui.forms.UIFormList;
import mchorse.bbs_mod.ui.forms.categories.UIFormCategory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import java.util.List;

@Mixin(value = UIFormList.class, remap = false)
public interface UIFormListAccessor
{
    @Accessor("categories")
    List<UIFormCategory> getCategories();
}
