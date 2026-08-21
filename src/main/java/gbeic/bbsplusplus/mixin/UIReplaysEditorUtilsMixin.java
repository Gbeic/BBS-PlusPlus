package gbeic.bbsplusplus.mixin;

import gbeic.bbsplusplus.client.ui.pose.IPoseParameterBrush;
import mchorse.bbs_mod.forms.FormUtils;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.forms.forms.ModelForm;
import mchorse.bbs_mod.forms.renderers.ModelFormRenderer;
import mchorse.bbs_mod.graphics.window.Window;
import mchorse.bbs_mod.cubic.ModelInstance;
import mchorse.bbs_mod.ui.film.ICursor;
import mchorse.bbs_mod.ui.film.replays.UIReplaysEditorUtils;
import mchorse.bbs_mod.ui.framework.UIContext;
import gbeic.bbsplusplus.BBSAddonsSettings;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframeEditor;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframeSheet;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.factories.UIPoseKeyframeFactory;
import mchorse.bbs_mod.utils.Pair;
import mchorse.bbs_mod.utils.keyframes.Keyframe;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

@Mixin(UIReplaysEditorUtils.class)
public class UIReplaysEditorUtilsMixin
{
    /**
     * 注入目标：模型视图拾取骨骼并准备切换对应关键帧之前。
     * 注入原因：原版视图点选可能重建姿势关键帧工厂，若等选中目标后再处理，参数刷的复制源快照已经随旧编辑器丢失。
     * 修改后的行为：沿用 BBS 原生的形态路径与 Pose 轨道匹配规则，先尝试粘贴到画面中实际点中的骨骼；命中复制源则保持等待，命中目标则让原逻辑继续切换到目标。
     */
    @Inject(
        method = "pickForm(Lmchorse/bbs_mod/ui/framework/elements/input/keyframes/UIKeyframeEditor;Lmchorse/bbs_mod/ui/film/ICursor;Lmchorse/bbs_mod/forms/forms/Form;Ljava/lang/String;Z)V",
        at = @At("HEAD"),
        cancellable = true,
        remap = false
    )
    private static void bbspp$applyPoseParameterBrushFromViewport(UIKeyframeEditor keyframeEditor, ICursor cursor,
                                                                   Form form, String bone, boolean insert,
                                                                   CallbackInfo ci)
    {
        if (insert || keyframeEditor == null || form == null
            || !(keyframeEditor.editor instanceof UIPoseKeyframeFactory poseFactory)
            || !(poseFactory.poseEditor instanceof IPoseParameterBrush brush))
        {
            return;
        }

        Keyframe<?> selected = keyframeEditor.view.getGraph().getSelected();
        UIKeyframeSheet currentSheet = selected == null ? null : keyframeEditor.view.getGraph().getSheet(selected);
        String formPath = FormUtils.getPath(form);
        String posePrefix = formPath.isEmpty() ? "" : formPath + FormUtils.PATH_SEPARATOR;
        boolean matchingPoseSheet = currentSheet != null && currentSheet.id != null
            && (currentSheet.id.equals(posePrefix + "pose") || currentSheet.id.startsWith(posePrefix + "pose_overlay"));

        if (!matchingPoseSheet)
        {
            return;
        }

        if (brush.bbspp$applyParameterBrush(bone) == IPoseParameterBrush.Result.SOURCE)
        {
            ci.cancel();
        }
    }

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
