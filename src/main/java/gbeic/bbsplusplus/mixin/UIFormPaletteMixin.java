package gbeic.bbsplusplus.mixin;

import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.ui.forms.UIFormPalette;
import gbeic.bbsplusplus.BBSAddonsSettings;
import gbeic.bbsplusplus.ui.morphing.UIBBSPPFormList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.Consumer;

@Mixin(UIFormPalette.class)
public class UIFormPaletteMixin
{
    /**
     * 注入新版 FS 的表单调色板构造器，在原版列表与编辑器创建完成后按设置切换为 BBS++ 的新版伪装列表。
     */
    @Inject(method = "<init>(Ljava/util/function/Consumer;)V", at = @At("TAIL"), remap = false)
    private void onInitTail(Consumer<Form> callback, CallbackInfo ci)
    {
        this.bbspp$checkLayout();
    }

    /**
     * 注入渲染入口，让运行时切换“新版伪装面板”设置后可以立即重建列表布局。
     */
    @Inject(method = "render", at = @At("HEAD"), remap = false)
    private void onRender(mchorse.bbs_mod.ui.framework.UIContext context, CallbackInfo ci)
    {
        this.bbspp$checkLayout();
    }

    @Unique
    private void bbspp$checkLayout()
    {
        UIFormPalette self = (UIFormPalette) (Object) this;
        boolean isNewLayout = BBSAddonsSettings.newMorphingPanel != null && BBSAddonsSettings.newMorphingPanel.get();
        boolean currentlyNewLayout = self.list instanceof UIBBSPPFormList;

        if (isNewLayout != currentlyNewLayout)
        {
            // 提取额外添加的控件（例如 UIMorphingPanel 里的 demorph 等按钮）
            java.util.List<mchorse.bbs_mod.ui.framework.elements.IUIElement> extraElements = new java.util.ArrayList<>();
            boolean hadClose = true;

            if (self.list != null && self.list.bar != null)
            {
                for (mchorse.bbs_mod.ui.framework.elements.IUIElement child : self.list.bar.getChildren())
                {
                    if (child != self.list.search && child != self.list.edit && child != self.list.close && child != self.list.categoryFilter)
                    {
                        if (self.list instanceof UIBBSPPFormList && child == ((UIBBSPPFormList) self.list).openModelsBtn)
                        {
                            continue; // 不提取新版界面专属的模型文件夹按钮
                        }
                        extraElements.add(child);
                    }
                }
            }

            if (self.list != null && self.list.close != null)
            {
                hadClose = self.list.close.getParent() != null;
            }

            Form selected = self.list == null ? null : self.list.getSelected();

            if (self.list != null)
            {
                self.list.removeFromParent();
            }

            if (isNewLayout)
            {
                self.list = new UIBBSPPFormList(self);
            }
            else
            {
                self.list = new mchorse.bbs_mod.ui.forms.UIFormList(self);
            }

            self.list.full(self);

            // 保持 close 按钮的状态（如果原版移除了，新版也得移除，以适配 cantExit 逻辑）
            if (!hadClose && self.list.close != null)
            {
                self.list.close.removeFromParent();
            }

            // 重新添加额外的控件
            if (self.list.bar != null)
            {
                for (mchorse.bbs_mod.ui.framework.elements.IUIElement child : extraElements)
                {
                    self.list.bar.add(child);
                }
            }
            
            mchorse.bbs_mod.forms.FormCategories formCategories = mchorse.bbs_mod.BBSModClient.getFormCategories();
            if (formCategories != null && !formCategories.getAllCategories().isEmpty())
            {
                self.list.setupForms(formCategories);
            }

            if (selected != null)
            {
                self.list.setSelected(selected);
            }

            self.removeAll();
            self.add(self.list, self.editor);

            if (self.editor != null)
            {
                self.list.setVisible(!self.editor.isEditing());
            }
            
            self.resize();
        }
    }
}
