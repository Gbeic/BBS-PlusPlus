package gbeic.bbsplusplus.mixin;

import gbeic.bbsplusplus.BBSAddonsSettings;
import gbeic.bbsplusplus.util.GizmoModeController;
import mchorse.bbs_mod.ui.framework.elements.input.UIPropTransform;
import mchorse.bbs_mod.ui.utils.Gizmo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Blockbench 风格的 Gizmo 热键切换（G/S/R）。
 * <p>
 * 当 {@link BBSAddonsSettings#gizmoBlockbenchMode} 开启时：
 * <ul>
 *   <li><b>G/S/R</b>：第 1 次→切换显示模式；第 2 次→恢复或放行原版</li>
 *   <li><b>T（COMBINED）</b>：由 {@code GizmoBlockbenchMixin} 处理（{@code toggleCombined}），此处不干预</li>
 * </ul>
 * </p>
 */
@Mixin(UIPropTransform.class)
public class UIPropTransformBlockbenchMixin
{
    @Inject(method = "enableMode(I)V", at = @At("HEAD"), cancellable = true, remap = false)
    private void onEnableMode(int modeOrdinal, CallbackInfo ci)
    {
        if (BBSAddonsSettings.gizmoBlockbenchMode == null
            || !BBSAddonsSettings.gizmoBlockbenchMode.get())
        {
            return;
        }

        Gizmo.Mode requested = Gizmo.Mode.values()[modeOrdinal];

        /* COMBINED 由 GizmoBlockbenchMixin 处理（toggleCombined），此处不干预 */
        if (requested == Gizmo.Mode.COMBINED)
        {
            return;
        }

        /* G/S/R：第 1 次切显示，第 2 次恢复或放行原版 */
        Gizmo.Mode current = Gizmo.INSTANCE.getMode();
        if (current == requested)
        {
            if (BBSAddonsSettings.gizmoKeepOriginal != null
                && BBSAddonsSettings.gizmoKeepOriginal.get())
            {
                return; // 不取消，让原版 enableMode(int) 继续执行
            }
            GizmoModeController.restorePreviousMode();
        }
        else
        {
            Gizmo.INSTANCE.setMode(requested);
        }
        ci.cancel();
    }
}
