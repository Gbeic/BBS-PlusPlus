package gbeic.bbsplusplus.mixin;

import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.forms.forms.ModelForm;
import mchorse.bbs_mod.forms.renderers.ModelFormRenderer;
import mchorse.bbs_mod.graphics.window.Window;
import mchorse.bbs_mod.cubic.ModelInstance;
import mchorse.bbs_mod.ui.forms.editors.UIFormEditor;
import gbeic.bbsplusplus.BBSAddonsSettings;
import mchorse.bbs_mod.utils.Pair;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/**
 * 允许在模型表单编辑器中按住 Shift 键并点击骨骼时直接选择第一个未被禁用的祖先骨骼。
 * <p>
 * BBS 2.2 原版在模型表单编辑器中只能选择当前点击的骨骼。此 Mixin 将添加一个新的功能：当玩家按住 Shift 键并点击一个骨骼时，系统会检查该骨骼的所有上级骨骼（从最近的父级开始），并选择第一个未被禁用的祖先骨骼。这对于复杂模型来说非常有用，可以快速选择相关的父级骨骼，而不需要手动在层级树中寻找。
 * </p>
 */

@Mixin(UIFormEditor.class)
public abstract class UIFormEditorMixin
{
    @Shadow
    protected abstract void pickFormBone(Form form, String bone);

    @Shadow
    private java.util.function.Consumer<Form> callback;

    /**
     * 注入表单编辑器打开表单列表的入口，在 BBS++ 新版伪装面板启用时替换为带侧边栏的编辑器专用列表。
     */
    @Inject(method = "openFormList", at = @At("HEAD"), cancellable = true, remap = false)
    private void openBBSPPFormList(Form current, java.util.function.Consumer<Form> callback, CallbackInfo ci)
    {
        if (BBSAddonsSettings.newMorphingPanel == null || !BBSAddonsSettings.newMorphingPanel.get())
        {
            return;
        }

        gbeic.bbsplusplus.ui.morphing.UIBBSPPFormEditorList list = new gbeic.bbsplusplus.ui.morphing.UIBBSPPFormEditorList((UIFormEditor)(Object)this);

        list.setSelected(current);
        this.callback = callback;

        list.full((UIFormEditor)(Object)this);
        list.resize();
        ((UIFormEditor)(Object)this).add(list);

        // 显式初始化带有类别的侧边栏，因为在 super() 中被跳过了
        mchorse.bbs_mod.forms.FormCategories formCategories = mchorse.bbs_mod.BBSModClient.getFormCategories();
        if (!formCategories.getAllCategories().isEmpty())
        {
            list.setupForms(formCategories);
        }

        ci.cancel();
    }

    /**
     * 注入模型拾取入口，让 Shift 点击骨骼时可以改选第一个未被禁用的父级骨骼。
     */
    @Inject(method = "pickFormFromRenderer", at = @At("HEAD"), cancellable = true, remap = false)
    private void onPickFormFromRenderer(Pair<Form, String> pair, CallbackInfo ci)
    {
        if (Window.isShiftPressed() && !Window.isCtrlPressed() && BBSAddonsSettings.directParentPicking != null && BBSAddonsSettings.directParentPicking.get())
        {
            if (pair.a instanceof ModelForm modelForm)
            {
                ModelInstance model = ModelFormRenderer.getModel(modelForm);
                if (model != null)
                {
                    java.util.Collection<String> hierarchyCol = model.model.getHierarchyGroups(pair.b);
                    List<String> hierarchy = hierarchyCol instanceof List ? (List<String>) hierarchyCol : new java.util.ArrayList<>(hierarchyCol);
                    // 遍历所有上级骨骼（从索引 1 开始，跳过自身），寻找第一个未被禁用的祖先骨骼
                    for (int i = 1; i < hierarchy.size(); i++)
                    {
                        String parentBone = hierarchy.get(i);
                        if (!model.getDisabledBones().contains(parentBone))
                        {
                            this.pickFormBone(pair.a, parentBone);
                            ci.cancel();
                            return;
                        }
                    }
                }
            }
        }
    }
}
