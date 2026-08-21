package gbeic.bbsplusplus.mixin;

import gbeic.bbsplusplus.client.ui.film.IFilmLibraryLayoutToggle;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.forms.categories.UIFormCategory;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.context.UIContextMenu;
import mchorse.bbs_mod.ui.framework.elements.context.UISimpleContextMenu;
import mchorse.bbs_mod.ui.utils.context.ContextAction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

@Mixin(UIElement.class)
public class UIElementMixin
{
    /**
     * 注入目标：{@link UIElement#render(UIContext)} 开始处。
     * 注入原因：影片库开关可能在影片选择器已经显示时被修改，只靠打开界面时检查无法做到即时开关。
     * 修改后的行为：仅对实现了 {@link IFilmLibraryLayoutToggle} 的元素同步布局状态，其它 UI 不受影响。
     */
    @Inject(method = "render", at = @At("HEAD"), remap = false)
    private void bbspp$syncFilmLibraryLayoutOnRender(UIContext context, CallbackInfo ci)
    {
        if ((Object) this instanceof IFilmLibraryLayoutToggle toggle)
        {
            toggle.bbspp$syncFilmLibraryLayout();
        }
    }

    /**
     * 注入目标：{@link UIElement#createContextMenu(UIContext)} 返回后。
     * 注入原因：新版伪装界面使用自己的分类管理入口，原版分类右键菜单中的管理项会造成重复。
     * 修改后的行为：仅当全新伪装界面开启时，移除表单分类的新增、重命名和删除分类菜单项。
     */
    @Inject(method = "createContextMenu", at = @At("RETURN"), cancellable = true, remap = false)
    private void onContextMenu(UIContext context, CallbackInfoReturnable<UIContextMenu> cir)
    {
        UIContextMenu menu = cir.getReturnValue();
        
        // 拦截 UIFormCategory（包括 UIUserFormCategory）的右键菜单
        if (menu instanceof UISimpleContextMenu && (Object) this instanceof UIFormCategory)
        {
            if (!gbeic.bbsplusplus.BBSAddonsSettings.newMorphingPanel.get())
            {
                return;
            }
            UISimpleContextMenu simpleMenu = (UISimpleContextMenu) menu;
            List<ContextAction> actions = simpleMenu.actions.getList();
            
            // 移除原本用于“添加类别”、“重命名类别”、“移除类别”的三个选项
            boolean removed = actions.removeIf(a -> 
                a.label == UIKeys.FORMS_CATEGORIES_CONTEXT_ADD_CATEGORY ||
                a.label == UIKeys.FORMS_CATEGORIES_CONTEXT_RENAME_CATEGORY ||
                a.label == UIKeys.FORMS_CATEGORIES_CONTEXT_REMOVE_CATEGORY
            );
            
            if (removed)
            {
                simpleMenu.actions.update();
            }
        }
    }
}
