package gbeic.bbsplusplus.mixin;

import gbeic.bbsplusplus.BBSAddonsSettings;
import gbeic.bbsplusplus.util.GizmoModeController;
import mchorse.bbs_mod.ui.utils.Gizmo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 改进 {@link Gizmo} 的默认模式与模式切换行为。
 * <p>
 * 当 {@link BBSAddonsSettings#gizmoBlockbenchMode} 开启时：
 * <ul>
 *   <li>默认模式改为 {@link Gizmo.Mode#TRANSLATE TRANSLATE}</li>
 *   <li>T 键 {@code toggleCombined()} 被截获为循环
 *       （3 或 4 模式，取决于 {@code gizmoTCombined}）</li>
 * </ul>
 * </p>
 */
@Mixin(Gizmo.class)
public class GizmoBlockbenchMixin
{
    @Shadow(remap = false)
    private Gizmo.Mode mode;

    @Inject(method = "<init>", at = @At("RETURN"), remap = false)
    private void onInit(CallbackInfo ci)
    {
        if (BBSAddonsSettings.gizmoBlockbenchMode != null
            && BBSAddonsSettings.gizmoBlockbenchMode.get())
        {
            this.mode = Gizmo.Mode.TRANSLATE;
        }
    }



    /**
     * 截获 {@code toggleCombined()}（T 键实际调用的方法），改为循环。
     */
    @Inject(method = "toggleCombined", at = @At("HEAD"), cancellable = true, remap = false)
    private void onToggleCombined(CallbackInfoReturnable<Boolean> cir)
    {
        if (BBSAddonsSettings.gizmoBlockbenchMode == null
            || !BBSAddonsSettings.gizmoBlockbenchMode.get())
        {
            return;
        }

        /* T 键循环 */
        Gizmo.Mode next;
        boolean combined = BBSAddonsSettings.gizmoTCombined != null
            && BBSAddonsSettings.gizmoTCombined.get();

        if (this.mode == Gizmo.Mode.TRANSLATE)
        {
            next = Gizmo.Mode.SCALE;
        }
        else if (this.mode == Gizmo.Mode.SCALE)
        {
            next = Gizmo.Mode.ROTATE;
        }
        else if (this.mode == Gizmo.Mode.ROTATE && combined)
        {
            next = Gizmo.Mode.COMBINED;
        }
        else
        {
            next = Gizmo.Mode.TRANSLATE;
        }

        boolean changed = this.mode != next;
        this.mode = next;
        GizmoModeController.savePreviousMode();
        cir.setReturnValue(changed);
    }

    /**
     * 截获 {@code setMode}：
     * <ul>
     *   <li>非 COMBINED → 保存之前模式后放行（给 G/S/R 恢复用）</li>
     *   <li>COMBINED → 不做任何循环。T 键循环由 {@code onToggleCombined} 处理，
     *       此处 {@code setMode(COMBINED)} 是 {@code restorePreviousMode} 等
     *       调用路径，需要真正设回 COMBINED 模式。</li>
     * </ul>
     */
    @Inject(method = "setMode", at = @At("HEAD"), cancellable = true, remap = false)
    private void onSetMode(Gizmo.Mode target, CallbackInfoReturnable<Boolean> cir)
    {
        if (BBSAddonsSettings.gizmoBlockbenchMode == null
            || !BBSAddonsSettings.gizmoBlockbenchMode.get())
        {
            return;
        }

        if (target == Gizmo.Mode.COMBINED)
        {
            /* 放行原版 setMode，真正设成 COMBINED */
            return;
        }

        /* 非 COMBINED → 保存前一个模式给 restore 用 */
        GizmoModeController.savePreviousMode();
    }
}
