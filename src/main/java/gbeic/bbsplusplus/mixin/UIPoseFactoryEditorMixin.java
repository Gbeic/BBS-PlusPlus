package gbeic.bbsplusplus.mixin;

import gbeic.bbsplusplus.client.ui.pose.IPoseParameterBrush;
import gbeic.bbsplusplus.client.ui.pose.IPoseParameterBrushHost;
import gbeic.bbsplusplus.client.ui.pose.PoseBoneSkipData;
import gbeic.bbsplusplus.client.ui.pose.PoseParameterBrushState;
import mchorse.bbs_mod.l10n.L10n;
import mchorse.bbs_mod.ui.Keys;
import mchorse.bbs_mod.ui.framework.elements.IUIElement;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIIcon;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframeSheet;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframes;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.factories.UIPoseKeyframeFactory;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.colors.Colors;
import mchorse.bbs_mod.utils.keyframes.Keyframe;
import mchorse.bbs_mod.utils.pose.Pose;
import mchorse.bbs_mod.utils.pose.PoseTransform;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 为影片编辑器的姿势关键帧骨骼列表加入一次性参数刷。
 *
 * <p>单选骨骼时沿用列表与模型视图点选目标的一次性格式刷；多选骨骼时把每根骨骼的
 * 独立快照交给时间轴会话保存，并在兼容目标帧通过标题栏按钮按名称批量写入。关键帧
 * 编辑器即使被重建，也能从稳定宿主恢复两种格式刷状态。</p>
 */
@Mixin(UIPoseKeyframeFactory.UIPoseFactoryEditor.class)
public abstract class UIPoseFactoryEditorMixin implements IPoseParameterBrush
{
    @Shadow
    private UIKeyframes editor;

    @Shadow
    private Keyframe<Pose> keyframe;

    @Unique
    private UIIcon bbspp$parameterBrushButton;

    /**
     * 注入目标：姿势关键帧骨骼编辑器构造完成处。
     * 注入原因：原版骨骼列表只有搜索、镜像编辑和交替反转，没有可从当前骨骼取样的一次性操作入口。
     * 修改后的行为：在列表标题栏追加参数刷按钮，并注册仅在参数刷启用时生效的 Esc 取消操作。
     */
    @Inject(method = "<init>", at = @At("TAIL"), remap = false)
    private void bbspp$addParameterBrush(UIKeyframes editor, Keyframe<Pose> keyframe, CallbackInfo ci)
    {
        UIPoseKeyframeFactory.UIPoseFactoryEditor self = (UIPoseKeyframeFactory.UIPoseFactoryEditor) (Object) this;

        this.bbspp$parameterBrushButton = new UIIcon(this::bbspp$getParameterBrushIcon,
            (button) -> this.bbspp$handleParameterBrushButton());
        this.bbspp$parameterBrushButton.tooltip(this::bbspp$getParameterBrushTooltip);
        this.bbspp$parameterBrushButton.activeColor(Colors.opaque(Colors.GREEN));
        this.bbspp$parameterBrushButton.active(this.bbspp$isParameterBrushArmed());

        if (!self.groups.getChildren().isEmpty())
        {
            IUIElement first = self.groups.getChildren().get(0);

            if (first instanceof UIElement header)
            {
                header.add(this.bbspp$parameterBrushButton);
            }
        }

        self.keys().register(Keys.CLOSE, this::bbspp$cancelParameterBrush)
            .active(this::bbspp$isParameterBrushArmed)
            .label(L10n.lang("bbspp.ui.pose.parameter_brush_cancel"));
    }

    @Unique
    private void bbspp$handleParameterBrushButton()
    {
        if (this.bbspp$isParameterBrushArmed())
        {
            PoseParameterBrushState state = this.bbspp$getParameterBrushState();

            if (state != null && state.isBatchDestination(this.keyframe, this.bbspp$getCurrentSheet()))
            {
                if (this.bbspp$applyParameterBrushBatch(state))
                {
                    this.bbspp$cancelParameterBrush();
                }

                return;
            }

            this.bbspp$cancelParameterBrush();

            return;
        }

        UIPoseKeyframeFactory.UIPoseFactoryEditor self = (UIPoseKeyframeFactory.UIPoseFactoryEditor) (Object) this;
        List<String> selected = new ArrayList<>(self.groups.list.getCurrent());

        if (selected.isEmpty())
        {
            return;
        }

        PoseParameterBrushState state = this.bbspp$getParameterBrushState();

        if (state == null)
        {
            return;
        }

        Map<String, PoseTransform> snapshots = new LinkedHashMap<>();

        for (String bone : selected)
        {
            if (bone != null && !bone.isEmpty() && self.hasBone(bone))
            {
                snapshots.put(bone, self.getPose().transforms.get(bone));
            }
        }

        state.arm(this.keyframe, this.bbspp$getCurrentSheet(), snapshots);
        this.bbspp$parameterBrushButton.active(state.isArmed());
    }

    @Unique
    private void bbspp$cancelParameterBrush()
    {
        PoseParameterBrushState state = this.bbspp$getParameterBrushState();

        if (state != null)
        {
            state.clear();
        }

        if (this.bbspp$parameterBrushButton != null)
        {
            this.bbspp$parameterBrushButton.active(false);
        }
    }

    @Override
    public Result bbspp$applyParameterBrush(String bone)
    {
        if (!this.bbspp$isParameterBrushArmed() || bone == null || bone.isEmpty())
        {
            return Result.IGNORED;
        }

        UIPoseKeyframeFactory.UIPoseFactoryEditor self = (UIPoseKeyframeFactory.UIPoseFactoryEditor) (Object) this;

        if (!self.hasBone(bone))
        {
            return Result.IGNORED;
        }

        PoseParameterBrushState state = this.bbspp$getParameterBrushState();

        UIKeyframeSheet sheet = this.bbspp$getCurrentSheet();

        if (state == null || !state.isCompatible(this.keyframe, sheet))
        {
            return Result.IGNORED;
        }

        /* 多骨骼模式的目标是整张姿势帧，骨骼点选只保留原版选择行为。 */
        if (state.isBatch())
        {
            return Result.IGNORED;
        }

        if (state.isSource(this.keyframe, sheet, bone))
        {
            return Result.SOURCE;
        }

        PoseTransform snapshot = state.getSnapshot();

        UIPoseKeyframeFactory.UIPoseFactoryEditor.apply(this.editor, this.keyframe, (pose) ->
        {
            PoseBoneSkipData.setSkipped(pose, bone, false);
            pose.get(bone).copy(snapshot);
        });
        this.bbspp$cancelParameterBrush();

        return Result.APPLIED;
    }

    @Override
    public boolean bbspp$isParameterBrushArmed()
    {
        PoseParameterBrushState state = this.bbspp$getParameterBrushState();

        return state != null && state.isCompatible(this.keyframe, this.bbspp$getCurrentSheet());
    }

    @Override
    public boolean bbspp$isParameterBrushBatch()
    {
        PoseParameterBrushState state = this.bbspp$getParameterBrushState();

        return state != null && state.isBatch()
            && state.isCompatible(this.keyframe, this.bbspp$getCurrentSheet());
    }

    @Override
    public boolean bbspp$isParameterBrushSource(String bone)
    {
        PoseParameterBrushState state = this.bbspp$getParameterBrushState();

        return state != null && state.isSource(this.keyframe, this.bbspp$getCurrentSheet(), bone);
    }

    @Override
    public boolean bbspp$isParameterBrushTarget(String bone)
    {
        PoseParameterBrushState state = this.bbspp$getParameterBrushState();

        return state != null && state.isBatchTarget(this.keyframe, this.bbspp$getCurrentSheet(), bone);
    }

    @Override
    public boolean bbspp$isPoseBoneModified(String bone)
    {
        UIPoseKeyframeFactory.UIPoseFactoryEditor self = (UIPoseKeyframeFactory.UIPoseFactoryEditor) (Object) this;
        PoseTransform transform = bone == null ? null : self.getPose().transforms.get(bone);

        return transform != null && !transform.isDefault();
    }

    @Override
    public boolean bbspp$isPoseBoneSkipped(String bone)
    {
        UIPoseKeyframeFactory.UIPoseFactoryEditor self = (UIPoseKeyframeFactory.UIPoseFactoryEditor) (Object) this;

        return PoseBoneSkipData.isSkipped(self.getPose(), bone);
    }

    @Override
    public boolean bbspp$togglePoseBoneSkipped(String clickedBone)
    {
        UIPoseKeyframeFactory.UIPoseFactoryEditor self = (UIPoseKeyframeFactory.UIPoseFactoryEditor) (Object) this;

        if (!self.hasBone(clickedBone))
        {
            return false;
        }

        List<String> selected = new ArrayList<>(self.groups.list.getCurrent());

        /* 点未选中的状态菱形时先切为单选；点已选中的骨骼则作用于整个多选集合。 */
        if (!selected.contains(clickedBone))
        {
            selected.clear();
            selected.add(clickedBone);
            self.restoreSelection(selected);
        }

        selected.removeIf((bone) -> !self.hasBone(bone));

        if (selected.isEmpty())
        {
            return false;
        }

        boolean allSkipped = true;

        for (String bone : selected)
        {
            if (!PoseBoneSkipData.isSkipped(self.getPose(), bone))
            {
                allSkipped = false;
                break;
            }
        }

        boolean skipped = !allSkipped;

        UIPoseKeyframeFactory.UIPoseFactoryEditor.apply(this.editor, this.keyframe, (pose) ->
        {
            for (String bone : selected)
            {
                PoseBoneSkipData.setSkipped(pose, bone, skipped);
            }
        });

        return true;
    }

    /**
     * 注入目标：姿势编辑器的固定、颜色和光照写入调用。
     * 注入原因：编辑一个已跳过的骨骼时，若只改参数，画面仍不会响应且容易造成误解。
     * 修改后的行为：在同一次关键帧通知中先恢复该骨骼参与，再执行原参数写入。
     */
    @Redirect(
        method = {"setFix", "setColor", "setLighting"},
        at = @At(
            value = "INVOKE",
            target = "Lmchorse/bbs_mod/ui/framework/elements/input/keyframes/factories/UIPoseKeyframeFactory$UIPoseFactoryEditor;apply(Lmchorse/bbs_mod/ui/framework/elements/input/keyframes/UIKeyframes;Lmchorse/bbs_mod/utils/keyframes/Keyframe;Ljava/lang/String;Ljava/util/function/Consumer;)V"
        ),
        remap = false
    )
    private void bbspp$unskipBoneBeforeAuxiliaryEdit(UIKeyframes editor, Keyframe<?> keyframe, String bone,
                                                      java.util.function.Consumer<PoseTransform> consumer)
    {
        UIPoseKeyframeFactory.UIPoseFactoryEditor.apply(editor, keyframe, (pose) ->
        {
            PoseBoneSkipData.setSkipped(pose, bone, false);
            consumer.accept(pose.get(bone));
        });
    }

    @Unique
    private PoseParameterBrushState bbspp$getParameterBrushState()
    {
        return this.editor instanceof IPoseParameterBrushHost host
            ? host.bbspp$getPoseParameterBrushState()
            : null;
    }

    @Unique
    private UIKeyframeSheet bbspp$getCurrentSheet()
    {
        return this.editor == null || this.keyframe == null
            ? null
            : this.editor.getGraph().getSheet(this.keyframe);
    }

    @Unique
    private mchorse.bbs_mod.ui.utils.icons.Icon bbspp$getParameterBrushIcon()
    {
        PoseParameterBrushState state = this.bbspp$getParameterBrushState();

        return state != null && state.isBatchDestination(this.keyframe, this.bbspp$getCurrentSheet())
            ? Icons.PASTE
            : Icons.COPY;
    }

    @Unique
    private String bbspp$getParameterBrushTooltip()
    {
        PoseParameterBrushState state = this.bbspp$getParameterBrushState();

        if (state != null && state.isBatch())
        {
            String key = state.isBatchDestination(this.keyframe, this.bbspp$getCurrentSheet())
                ? "bbspp.ui.pose.parameter_brush_batch_paste"
                : "bbspp.ui.pose.parameter_brush_batch_source";

            return L10n.lang(key).format(state.size()).get();
        }

        return L10n.lang("bbspp.ui.pose.parameter_brush_tooltip").get();
    }

    @Unique
    private boolean bbspp$applyParameterBrushBatch(PoseParameterBrushState state)
    {
        UIPoseKeyframeFactory.UIPoseFactoryEditor self = (UIPoseKeyframeFactory.UIPoseFactoryEditor) (Object) this;
        List<Map.Entry<String, PoseTransform>> targets = new ArrayList<>();
        boolean applied = false;

        for (Map.Entry<String, PoseTransform> entry : state.getSnapshots().entrySet())
        {
            if (self.hasBone(entry.getKey()))
            {
                targets.add(entry);
            }
        }

        if (targets.isEmpty())
        {
            return false;
        }

        for (UIKeyframeSheet sheet : this.editor.getGraph().getSheets())
        {
            for (Keyframe<?> selected : sheet.selection.getSelected())
            {
                if (!state.isCompatible(selected, sheet) || state.isSourceKeyframe(selected)
                    || !(selected.getValue() instanceof Pose pose))
                {
                    continue;
                }

                /* 每个目标关键帧只通知一次，整组骨骼形成一次批量修改。 */
                selected.preNotify();

                for (Map.Entry<String, PoseTransform> target : targets)
                {
                    PoseBoneSkipData.setSkipped(pose, target.getKey(), false);
                    pose.get(target.getKey()).copy(target.getValue());
                }

                selected.postNotify();
                applied = true;
            }
        }

        if (applied)
        {
            self.restoreSelection(new ArrayList<>(self.groups.list.getCurrent()));
        }

        return applied;
    }
}
