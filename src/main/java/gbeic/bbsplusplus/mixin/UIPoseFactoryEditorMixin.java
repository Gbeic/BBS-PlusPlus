package gbeic.bbsplusplus.mixin;

import gbeic.bbsplusplus.client.ui.pose.IPoseParameterBrush;
import gbeic.bbsplusplus.client.ui.pose.IPoseParameterBrushHost;
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
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 为影片编辑器的姿势关键帧骨骼列表加入一次性参数刷。
 *
 * <p>该类在启用参数刷时把主选骨骼的完整参数交给时间轴会话保存，随后由列表点选和
 * 模型视图点选共同调用统一目标入口。关键帧编辑器即使被重建，也能从稳定宿主恢复格式刷；
 * 写入仍通过 BBS 的关键帧通知流程完成，从而保留多关键帧编辑与撤销历史。</p>
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

        this.bbspp$parameterBrushButton = new UIIcon(Icons.COPY, (button) -> this.bbspp$toggleParameterBrush());
        this.bbspp$parameterBrushButton.tooltip(L10n.lang("bbspp.ui.pose.parameter_brush_tooltip"));
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
    private void bbspp$toggleParameterBrush()
    {
        if (this.bbspp$isParameterBrushArmed())
        {
            this.bbspp$cancelParameterBrush();

            return;
        }

        UIPoseKeyframeFactory.UIPoseFactoryEditor self = (UIPoseKeyframeFactory.UIPoseFactoryEditor) (Object) this;
        String source = self.getGroup();

        if (source == null || source.isEmpty() || !self.hasBone(source))
        {
            return;
        }

        PoseParameterBrushState state = this.bbspp$getParameterBrushState();

        if (state == null)
        {
            return;
        }

        PoseTransform sourceTransform = self.getPose().transforms.get(source);

        state.arm(this.keyframe, this.bbspp$getCurrentSheet(), source, sourceTransform);
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

        if (state.isSource(this.keyframe, sheet, bone))
        {
            return Result.SOURCE;
        }

        PoseTransform snapshot = state.getSnapshot();

        UIPoseKeyframeFactory.UIPoseFactoryEditor.apply(this.editor, this.keyframe, bone, (target) -> target.copy(snapshot));
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
    public boolean bbspp$isParameterBrushSource(String bone)
    {
        PoseParameterBrushState state = this.bbspp$getParameterBrushState();

        return state != null && state.isSource(this.keyframe, this.bbspp$getCurrentSheet(), bone);
    }

    @Override
    public boolean bbspp$isPoseBoneModified(String bone)
    {
        UIPoseKeyframeFactory.UIPoseFactoryEditor self = (UIPoseKeyframeFactory.UIPoseFactoryEditor) (Object) this;
        PoseTransform transform = bone == null ? null : self.getPose().transforms.get(bone);

        return transform != null && !transform.isDefault();
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
}
