package gbeic.bbsplusplus.mixin;

import mchorse.bbs_mod.ui.framework.elements.input.keyframes.graphs.IUIKeyframeGraph;
import mchorse.bbs_mod.utils.keyframes.Keyframe;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin — 修复 {@link IUIKeyframeGraph#setValue(Object, boolean)} 在无选中关键帧时的 NPE。
 * <p>
 * 进入曲线剪辑后调整参数时，若关键帧图上无选中关键帧，{@code getSelected()} 返回 null，
 * 后续 {@code selected.getFactory()} 抛出 NullPointerException。
 * </p>
 */
@Mixin(IUIKeyframeGraph.class)
public interface IUIKeyframeGraphMixin
{
    @Shadow
    Keyframe<?> getSelected();

    @Inject(
        method = "setValue(Ljava/lang/Object;Z)V",
        at = @At("HEAD"),
        cancellable = true,
        remap = false
    )
    private void onSetValue(Object value, boolean unmergeable, CallbackInfo ci)
    {
        if (this.getSelected() == null)
        {
            ci.cancel();
        }
    }

    @Shadow
    java.util.List<mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframeSheet> getSheets();

    @Inject(
        method = "addKeyframe(Lmchorse/bbs_mod/ui/framework/elements/input/keyframes/UIKeyframeSheet;FLjava/lang/Object;)Lmchorse/bbs_mod/utils/keyframes/Keyframe;",
        at = @At("HEAD"),
        cancellable = true,
        remap = false
    )
    private void bbspp$preventNegativeAddKeyframe(
        mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframeSheet sheet,
        float tick,
        Object value,
        org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<Keyframe<?>> cir
    )
    {
        if (tick < 0 && gbeic.bbsplusplus.BBSAddonsSettings.preventNegativeKeyframes != null && gbeic.bbsplusplus.BBSAddonsSettings.preventNegativeKeyframes.get())
        {
            cir.setReturnValue(null);
        }
    }

    @org.spongepowered.asm.mixin.injection.ModifyVariable(
        method = "setTick(FZ)V",
        at = @At("HEAD"),
        argsOnly = true,
        ordinal = 0,
        remap = false
    )
    private float bbspp$capNegativeTickDrag(float tick)
    {
        if (gbeic.bbsplusplus.BBSAddonsSettings.preventNegativeKeyframes != null && gbeic.bbsplusplus.BBSAddonsSettings.preventNegativeKeyframes.get())
        {
            IUIKeyframeGraph self = (IUIKeyframeGraph) this;
            Keyframe<?> selected = self.getSelected();
            
            if (selected == null)
            {
                return tick;
            }

            float diff = tick - selected.getTick();
            float minTick = Float.MAX_VALUE;

            for (mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframeSheet sheet : self.getSheets())
            {
                for (Object kfObj : sheet.selection.getSelected())
                {
                    Keyframe<?> kf = (Keyframe<?>) kfObj;
                    if (kf.getTick() < minTick)
                    {
                        minTick = kf.getTick();
                    }
                }
            }

            if (minTick != Float.MAX_VALUE && minTick + diff < 0)
            {
                float clampedDiff = -minTick;
                return selected.getTick() + clampedDiff;
            }
        }
        
        return tick;
    }
}
