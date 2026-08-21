package gbeic.bbsplusplus.client.audio;

import gbeic.bbsplusplus.BBSPlusPlusMod;
import mchorse.bbs_mod.BBSModClient;
import net.minecraft.client.MinecraftClient;
import org.lwjgl.openal.ALC10;
import org.lwjgl.openal.ALC11;
import org.lwjgl.openal.ALUtil;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 音频输出设备切换监听器。
 * <p>
 * BBS 的 {@code SoundManager} 与 Minecraft 共用同一个 OpenAL 上下文，它不会监听
 * 系统播放设备的插拔与切换。切换播放设备（例如从外放切到耳机）后，已创建的
 * OpenAL buffer/source 仍绑定在旧设备上，source 状态卡在"播放中"却不再出声，
 * 只有手动重选一次 clip 音频（触发全量 deleteSounds）才能恢复。
 * 本类周期性比对 ALC 报告的默认实际输出设备与当前上下文设备，检测到变化后全量
 * 重建 BBS 音频缓存，让 {@code AudioClientClip.manageSounds} 自动于新设备上重建声音。
 * </p>
 */
public class AudioOutputDeviceWatcher
{
    /** 检测周期：每 20 tick（约 1 秒）比对一次设备信息 */
    private static final int CHECK_INTERVAL = 20;

    /** 设备指纹连续稳定两次后才执行刷新，避免蓝牙设备切换时的瞬态状态触发多次刷新 */
    private static final int REQUIRED_STABLE_CHECKS = 2;

    private static int tickCounter;
    private static String confirmedDeviceFingerprint;
    private static String candidateDeviceFingerprint;
    private static int candidateStableChecks;

    /**
     * 每个客户端 tick 调用一次，周期性比对音频输出设备信息。
     * <p>
     * 设备指纹优先使用 OpenAL 的默认实际输出设备，同时加入当前上下文绑定的设备。
     * 如果运行环境不支持扩展默认设备查询，则回退到普通默认设备和规范化后的设备列表。
     * 指纹连续稳定后才调用 {@code SoundManager#deleteSounds()} 清除全部旧句柄，
     * 由 BBS 自身的管理循环重建 buffer/source，声音自动恢复。
     * </p>
     */
    public static void tick(MinecraftClient client)
    {
        if (client == null || ++tickCounter % CHECK_INTERVAL != 0)
        {
            return;
        }

        String fingerprint = queryDeviceFingerprint();

        if (fingerprint == null)
        {
            return;
        }

        if (confirmedDeviceFingerprint == null)
        {
            // 首次检测只记录基线，不触发刷新
            confirmedDeviceFingerprint = fingerprint;

            return;
        }

        if (fingerprint.equals(confirmedDeviceFingerprint))
        {
            candidateDeviceFingerprint = null;
            candidateStableChecks = 0;

            return;
        }

        if (!fingerprint.equals(candidateDeviceFingerprint))
        {
            candidateDeviceFingerprint = fingerprint;
            candidateStableChecks = 1;

            return;
        }

        if (++candidateStableChecks < REQUIRED_STABLE_CHECKS)
        {
            return;
        }

        confirmedDeviceFingerprint = fingerprint;
        candidateDeviceFingerprint = null;
        candidateStableChecks = 0;

        if (BBSModClient.getSounds() != null)
        {
            BBSModClient.getSounds().deleteSounds();
            BBSPlusPlusMod.LOGGER.info("检测到音频输出设备变更，已刷新 BBS 音频缓存，声音将在下一帧自动重建。");
        }
    }

    /**
     * 查询当前输出设备指纹。
     * <p>
     * {@code ALC_DEFAULT_ALL_DEVICES_SPECIFIER} 能区分 Windows 下的实际输出端点，
     * 比普通的逻辑默认设备名更适合判断耳机和扬声器切换。部分 OpenAL 实现不提供该
     * 扩展，因此需要回退到标准查询；回退时使用 {@link ALUtil#getStringList(long, int)}
     * 正确解析 NUL 分隔的设备列表，并排序后避免列表顺序变化导致误判。
     * </p>
     */
    private static String queryDeviceFingerprint()
    {
        try
        {
            String extendedDefaultDevice = getString(0, ALC11.ALC_DEFAULT_ALL_DEVICES_SPECIFIER);
            String defaultDevice = extendedDefaultDevice;
            String allDevices = "";

            if (isEmpty(defaultDevice))
            {
                defaultDevice = getString(0, ALC11.ALC_DEFAULT_DEVICE_SPECIFIER);
                allDevices = queryAllDevices();
            }

            String contextDevice = queryContextDevice();

            if (isEmpty(defaultDevice) && isEmpty(contextDevice) && isEmpty(allDevices))
            {
                // OpenAL 尚未初始化，或当前没有可用的输出设备
                return null;
            }

            return normalize(defaultDevice) + "\u001f" + normalize(contextDevice) + "\u001f" + allDevices;
        }
        catch (RuntimeException | LinkageError ignored)
        {
            // OpenAL 尚未初始化或查询失败时跳过本次检测，避免影响游戏主线程
            return null;
        }
    }

    /** 查询当前 OpenAL 上下文实际绑定的输出设备。 */
    private static String queryContextDevice()
    {
        long context = ALC10.alcGetCurrentContext();

        if (context == 0)
        {
            return "";
        }

        long device = ALC10.alcGetContextsDevice(context);

        return device == 0 ? "" : normalize(getString(device, ALC10.ALC_DEVICE_SPECIFIER));
    }

    /** 在回退路径中读取并规范化完整输出设备列表。 */
    private static String queryAllDevices()
    {
        try
        {
            List<String> devices = ALUtil.getStringList(0, ALC11.ALC_ALL_DEVICES_SPECIFIER);

            return devices.stream()
                .map(AudioOutputDeviceWatcher::normalize)
                .filter(device -> !device.isEmpty())
                .sorted()
                .collect(Collectors.joining("\u001e"));
        }
        catch (RuntimeException | LinkageError ignored)
        {
            return "";
        }
    }

    /** 安全读取 ALC 字符串；设备句柄为 0 时表示系统级 NULL 设备。 */
    private static String getString(long device, int token)
    {
        try
        {
            return normalize(ALC10.alcGetString(device, token));
        }
        catch (RuntimeException | LinkageError ignored)
        {
            // 某些 OpenAL 实现不支持扩展 token，交给调用方继续尝试标准查询
            return "";
        }
    }

    /** 统一处理 OpenAL 返回的空字符串和设备名首尾空白。 */
    private static String normalize(String value)
    {
        return value == null ? "" : value.trim();
    }

    private static boolean isEmpty(String value)
    {
        return value == null || value.isEmpty();
    }
}
