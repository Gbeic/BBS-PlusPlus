package gbeic.bbsplusplus.util;

/**
 * 游戏模式切换消息抑制器 — 普通工具类，非 Mixin。
 * <p>
 * 由 {@link gbeic.bbsplusplus.mixin.UIFilmPanelGameModeMixin} 等在发命令前调用标记，
 * {@link gbeic.bbsplusplus.mixin.ClientPlayNetworkHandlerMixin} 在收到系统消息时检查标记。
 * 使用引用计数：每次 {@link #suppressNext()} +1，每次拦截成功 {@link #clear()} -1，
 * 支持短时间内连续多次模式切换（如快速开/关编辑器）不会丢失拦截。
 * 标记带 1 秒自动过期，不会影响手动的 {@code /gamemode} 命令。
 * </p>
 */
public class GameModeMessageSuppressor
{
    private static long suppressUntil = 0;
    private static int suppressCount = 0;

    /** 标记下一轮游戏模式切换消息需要过滤（1 秒内有效） */
    public static void suppressNext()
    {
        suppressCount++;
        long target = System.currentTimeMillis() + 1000;

        if (target > suppressUntil)
        {
            suppressUntil = target;
        }
    }

    /** 检查当前是否处于抑制窗口内 */
    public static boolean isActive()
    {
        return suppressCount > 0 && System.currentTimeMillis() < suppressUntil;
    }

    /** 消费一次抑制标记 */
    public static void clear()
    {
        if (suppressCount > 0)
        {
            suppressCount--;
        }

        if (suppressCount <= 0)
        {
            suppressUntil = 0;
        }
    }
}
