package gbeic.bbsplusplus.client.debug;

/**
 * 视频广告牌形态的调试日志开关。
 * <p>
 * 默认关闭，通过 {@code /bbsplusplus debug video on} 动态开启。开启后，视频渲染器会把
 * 解码器打开/关闭、每帧请求的秒数、{@code renderTime} 的阻塞耗时（含 GL 纹理上传与 CUDA 同步）
 * 以及时间轴跳变写入 {@code logs/bbsplusplus/video.log}，用于定位长视频开头卡顿等性能问题。
 * 不开启时只多一次布尔判断，不影响正常渲染。
 * </p>
 */
public final class VideoDebug
{
    private static final String MODULE = "video";

    /** 调试日志开关 */
    private static volatile boolean enabled;

    private VideoDebug()
    {
    }

    public static boolean isEnabled()
    {
        return enabled;
    }

    public static void setEnabled(boolean value)
    {
        enabled = value;
    }

    public static void log(String message)
    {
        if (enabled)
        {
            BBSPlusPlusDebugLogs.write(MODULE, message);
        }
    }
}
