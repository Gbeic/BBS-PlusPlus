package gbeic.bbsplusplus.client.compat.shadercurves;

import gbeic.bbsplusplus.client.debug.BBSPlusPlusDebugLogs;

/**
 * 光影曲线排查日志的动态开关。
 * <p>
 * 该开关原来放在 {@code ShaderCurvesMixin} 里作为编译期常量。改成游戏内指令动态切换后，
 * 必须挪到 Mixin 之外的普通类持有：Mixin 的 {@code @Unique} 字段会被注入到目标类，
 * 普通代码直接引用 Mixin 类本身拿到的是另一套静态字段，指令开关会失效。
 * </p>
 */
public final class ShaderCurveDebug
{
    private static final String MODULE = "shader_curves";

    /** 仅在需要分析新光影包兼容性时通过游戏内指令临时开启 */
    private static volatile boolean shaderCurvePatches = false;

    private ShaderCurveDebug()
    {
    }

    public static boolean isShaderCurvePatches()
    {
        return shaderCurvePatches;
    }

    public static void setShaderCurvePatches(boolean value)
    {
        shaderCurvePatches = value;
    }

    public static void log(String message)
    {
        BBSPlusPlusDebugLogs.write(MODULE, message);
    }
}
