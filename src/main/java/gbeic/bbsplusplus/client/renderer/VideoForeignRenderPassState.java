package gbeic.bbsplusplus.client.renderer;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;

/**
 * 识别会重复渲染 BBS 形态的第三方离屏通道。
 *
 * VFXLights 和 IR Lights 会为了阴影、遮罩等效果在同一游戏帧内再次渲染影片演员。
 * 这里通过可选反射读取它们公开的重入标记，避免让视频后端在这些通道中重复寻帧，
 * 同时不把任一插件变成 BBS++ 的运行时必需依赖。
 */
public final class VideoForeignRenderPassState
{
    private static final String VFX_PASS_CLASS = "com.bbsvfx.bbsvfx.client.BbsVfxForeignPass";
    private static final String IRL_SHADOW_CLASS = "org.qualet.irl.light.shadow.ShadowBakeState";

    private static boolean resolved;
    private static MethodHandle vfxPassActive;
    private static MethodHandle irlShadowBaking;

    private VideoForeignRenderPassState()
    {
    }

    public static boolean isActive()
    {
        resolve();

        return invoke(vfxPassActive) || invoke(irlShadowBaking);
    }

    private static void resolve()
    {
        if (resolved)
        {
            return;
        }

        resolved = true;
        vfxPassActive = findStaticBoolean(VFX_PASS_CLASS, "isActive");
        irlShadowBaking = findStaticBoolean(IRL_SHADOW_CLASS, "isBaking");
    }

    private static MethodHandle findStaticBoolean(String className, String methodName)
    {
        try
        {
            Class<?> type = Class.forName(className, false, VideoForeignRenderPassState.class.getClassLoader());

            return MethodHandles.publicLookup().unreflect(type.getMethod(methodName));
        }
        catch (Throwable ignored)
        {
            return null;
        }
    }

    private static boolean invoke(MethodHandle handle)
    {
        if (handle == null)
        {
            return false;
        }

        try
        {
            return (boolean) handle.invokeExact();
        }
        catch (Throwable ignored)
        {
            return false;
        }
    }
}
