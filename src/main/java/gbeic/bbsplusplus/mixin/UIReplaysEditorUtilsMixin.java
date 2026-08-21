package gbeic.bbsplusplus.mixin;

import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.forms.forms.ModelForm;
import mchorse.bbs_mod.forms.renderers.ModelFormRenderer;
import mchorse.bbs_mod.graphics.window.Window;
import mchorse.bbs_mod.cubic.ModelInstance;
import mchorse.bbs_mod.ui.film.replays.UIReplaysEditorUtils;
import mchorse.bbs_mod.ui.framework.UIContext;
import gbeic.bbsplusplus.BBSAddonsSettings;
import mchorse.bbs_mod.utils.Pair;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

@Mixin(UIReplaysEditorUtils.class)
public class UIReplaysEditorUtilsMixin
{
    /**
     * 注入回放编辑器的形态拾取入口，让 Shift 点击骨骼时可以改选第一个未被禁用的父级骨骼。
     */
    @Inject(method = "pickFormWithOffers", at = @At("HEAD"), cancellable = true, remap = false)
    private static void onPickFormWithOffers(UIContext context, Pair<Form, String> pair, UIReplaysEditorUtils.FormPicker picker, CallbackInfoReturnable<Boolean> cir)
    {
        boolean select = context.mouseButton == 0 || (context.mouseButton == 2 && Window.isCtrlPressed());
        boolean insert = context.mouseButton == 1;

        if (!select && !insert)
        {
            return;
        }

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
                            picker.pick(pair.a, parentBone, insert);
                            cir.setReturnValue(true);
                            return;
                        }
                    }
                }
            }
        }
    }
}
