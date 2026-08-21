package gbeic.bbsplusplus;

import java.lang.reflect.Method;

/**
 * IMBlocker 输入法管理器兼容层
 *
 * 通过反射安全调用 IMBlocker 的 IMManager.setState() 方法，
 * 使 BBS FS 的自定义文本框在获得/失去焦点时能正确通知 IMBlocker
 * 启用或禁用输入法。
 *
 * 如果 IMBlocker 未安装，所有调用都会被静默忽略。
 */
public final class IMBlockerCompat
{
    private static final Method SET_STATE;
    private static boolean textInputFocused;
    private static int pendingRestoreTicks;

    static
    {
        Method m = null;

        try
        {
            Class<?> clazz = Class.forName("io.github.reserveword.imblocker.common.IMManager");
            m = clazz.getMethod("setState", boolean.class);
        }
        catch (Throwable ignored)
        {
            // IMBlocker 未安装，忽略
        }

        SET_STATE = m;
    }

    private IMBlockerCompat() {}

    /**
     * 检测 IMBlocker 是否已加载
     */
    public static boolean isAvailable()
    {
        return SET_STATE != null;
    }

    /**
     * 记录 BBS 文本输入控件当前是否处于焦点状态，并立即同步给 IMBlocker。
     *
     * <p>窗口切到其它程序再切回来时，BBS 的文本框不会重新触发 focus()，
     * 因此需要保留这份状态，供窗口重新激活时恢复输入法。</p>
     */
    public static void setTextInputFocused(boolean focused)
    {
        textInputFocused = focused;
        pendingRestoreTicks = 0;

        if (focused)
        {
            enableIM();
        }
        else
        {
            disableIM();
        }
    }

    /**
     * Minecraft 窗口重新获得焦点时，如果 BBS 文本输入控件仍保持焦点，
     * 就再次通知 IMBlocker 允许输入法，修复 Alt+Tab 回来后被锁英文的问题。
     */
    public static void restoreTextInputAfterWindowFocus(boolean windowFocused)
    {
        if (windowFocused && textInputFocused)
        {
            pendingRestoreTicks = 3;

            enableIM();
        }
        else if (!windowFocused)
        {
            pendingRestoreTicks = 0;
        }
    }

    /**
     * 在窗口刚恢复焦点后的几帧内补发输入法启用状态。
     *
     * <p>IMBlocker 或系统窗口焦点事件可能在同一瞬间重置输入法状态，
     * 延迟到客户端 tick 再同步一次可以避免被后续回调覆盖。</p>
     */
    public static void tickPendingRestore()
    {
        if (pendingRestoreTicks <= 0 || !textInputFocused)
        {
            return;
        }

        pendingRestoreTicks -= 1;
        enableIM();
    }

    /**
     * 通知 IMBlocker 启用输入法（文本框获得焦点时调用）
     */
    public static void enableIM()
    {
        if (SET_STATE != null)
        {
            try
            {
                SET_STATE.invoke(null, true);
            }
            catch (Throwable ignored) {}
        }
    }

    /**
     * 通知 IMBlocker 禁用输入法（文本框失去焦点时调用）
     */
    public static void disableIM()
    {
        if (SET_STATE != null)
        {
            try
            {
                SET_STATE.invoke(null, false);
            }
            catch (Throwable ignored) {}
        }
    }
}
