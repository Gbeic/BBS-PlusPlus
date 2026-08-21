package gbeic.bbsplusplus.util;

import mchorse.bbs_mod.ui.utils.Gizmo;

/**
 * Blockbench 风格 Gizmo 模式切换的状态管理。
 * <p>
 * 在 {@link Gizmo} 的 {@code previousMode} 字段无法从外部直接访问，
 * 因此使用此类独立追踪模式栈，实现 G/S/R 热键的"恢复上一步"功能。
 * </p>
 */
public class GizmoModeController
{
    private static Gizmo.Mode previousMode = Gizmo.Mode.TRANSLATE;

    /**
     * 保存上一个模式（应在 {@code Gizmo.setMode()} 切换前调用）。
     */
    public static void savePreviousMode()
    {
        previousMode = Gizmo.INSTANCE.getMode();
    }

    /**
     * 将 Gizmo 恢复到上一个模式。
     */
    public static void restorePreviousMode()
    {
        Gizmo.Mode target = previousMode;

        /* 如果上一个模式就是当前模式，或者属于"已退出"的情况，
         * 则默认回到 TRANSLATE */
        if (target == Gizmo.INSTANCE.getMode() || target == null)
        {
            target = Gizmo.Mode.TRANSLATE;
        }

        Gizmo.INSTANCE.setMode(target);
    }

    /**
     * 获取保存的上一个模式。
     */
    public static Gizmo.Mode getPreviousMode()
    {
        return previousMode;
    }
}
