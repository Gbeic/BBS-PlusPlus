package gbeic.bbsplusplus.client.debug;

import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * BBS++ 调试模块的独立日志写入器。
 * <p>
 * 调试日志只在模块真正输出内容时创建，按模块写入 {@code logs/bbsplusplus/<模块>.log}，避免污染玩家的
 * {@code latest.log}，也避免没开启调试时在游戏目录里留下空文件。
 * </p>
 */
public final class BBSPlusPlusDebugLogs
{
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS", Locale.ROOT);

    private BBSPlusPlusDebugLogs()
    {
    }

    public static synchronized void write(String module, String message)
    {
        try
        {
            Path path = getLogPath(module);

            Files.createDirectories(path.getParent());
            Files.writeString(path, "[" + TIME_FORMAT.format(LocalDateTime.now()) + "] " + message + System.lineSeparator(),
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.APPEND
            );
        }
        catch (IOException ignored)
        {
            // 调试日志写入失败不能影响光影加载或游戏运行。
        }
    }

    private static Path getLogPath(String module)
    {
        return FabricLoader.getInstance().getGameDir()
            .resolve("logs")
            .resolve("bbsplusplus")
            .resolve(module + ".log");
    }
}
