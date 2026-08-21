package gbeic.bbsplusplus.util;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import gbeic.bbsplusplus.BBSPlusPlusMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.world.level.GameType;
import net.neoforged.fml.loading.FMLPaths;

import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.UUID;

/**
 * 影片编辑器自动旁观模式的恢复状态管理器。
 * <p>
 * 正常关闭界面时，原有逻辑可以立刻把玩家切回进入编辑器前的模式；但 Alt+F4
 * 或直接结束进程时，界面关闭钩子不会执行。此类把“BBS++ 自动切过模式”写入
 * 游戏目录配置文件，并在下次进入同一世界或服务器时补发恢复命令，避免玩家被永久
 * 留在旁观者模式。
 * </p>
 */
public class FilmAutoGameModeRestoreState
{
    private static final String FILE_NAME = "film_auto_game_mode.json";
    private static final int VERSION = 1;

    private static PendingState pending;
    private static boolean loaded;
    private static boolean activeAutoMode;
    private static GameType activeOriginalGameMode;

    /**
     * 记录本次由 BBS++ 发起的自动旁观模式切换，并尽量持久化到配置文件。
     */
    public static void markAutoSwitch(Minecraft client, GameType originalGameMode)
    {
        if (client.player == null || originalGameMode == null || originalGameMode == GameType.SPECTATOR)
        {
            return;
        }

        activeAutoMode = true;
        activeOriginalGameMode = originalGameMode;

        String worldKey = getWorldKey(client);

        if (worldKey == null)
        {
            return;
        }

        pending = new PendingState(
            client.player.getUUID(),
            worldKey,
            originalGameMode.getName(),
            System.currentTimeMillis()
        );

        savePending();
    }

    /**
     * 影片编辑器正常关闭时恢复原游戏模式，并清除异常退出补救标记。
     */
    public static void restoreFromFilmPanel(Minecraft client, GameType savedGameMode)
    {
        if (savedGameMode != null && sendGameModeCommand(client, savedGameMode.getName()))
        {
            clearPending();
        }

        activeAutoMode = false;
        activeOriginalGameMode = null;
    }

    /**
     * 客户端关闭前尽量补发恢复命令，但保留持久标记给下次启动兜底。
     */
    public static void tryRestoreBeforeShutdown(Minecraft client)
    {
        GameType gameMode = activeOriginalGameMode;

        if (gameMode == null)
        {
            PendingState state = getPending();

            if (state != null && state.matches(client))
            {
                gameMode = GameType.byName(state.originalGameMode, null);
            }
        }

        if (gameMode != null && gameMode != GameType.SPECTATOR)
        {
            sendGameModeCommand(client, gameMode.getName());
        }

        activeAutoMode = false;
        activeOriginalGameMode = null;
    }

    /**
     * 每 tick 检测上次异常退出留下的标记，并在玩家真正进入同一世界后恢复。
     */
    public static void tick(Minecraft client)
    {
        if (activeAutoMode || client.player == null || client.level == null || client.getConnection() == null)
        {
            return;
        }

        PendingState state = getPending();

        if (state == null || !state.matches(client))
        {
            return;
        }

        GameType original = GameType.byName(state.originalGameMode, null);
        GameType current = getCurrentGameMode(client);

        if (original == null || original == GameType.SPECTATOR)
        {
            clearPending();
            return;
        }

        if (current == null)
        {
            return;
        }

        if (current == GameType.SPECTATOR)
        {
            if (sendGameModeCommand(client, original.getName()))
            {
                clearPending();
            }
        }
        else
        {
            clearPending();
        }
    }

    private static boolean sendGameModeCommand(Minecraft client, String gameMode)
    {
        if (client.player == null || client.getConnection() == null || gameMode == null || gameMode.isEmpty())
        {
            return false;
        }

        GameModeMessageSuppressor.suppressNext();
        client.player.connection.sendCommand("gamemode " + gameMode);

        return true;
    }

    private static GameType getCurrentGameMode(Minecraft client)
    {
        if (client.gameMode != null)
        {
            return client.gameMode.getPlayerMode();
        }

        if (client.getConnection() != null && client.player != null)
        {
            PlayerInfo entry = client.getConnection().getPlayerInfo(client.player.getUUID());

            if (entry != null)
            {
                return entry.getGameMode();
            }
        }

        return null;
    }

    private static PendingState getPending()
    {
        if (!loaded)
        {
            loadPending();
        }

        return pending;
    }

    private static void loadPending()
    {
        loaded = true;
        Path file = getStateFile();

        if (!Files.isRegularFile(file))
        {
            pending = null;
            return;
        }

        try (Reader reader = Files.newBufferedReader(file))
        {
            JsonObject object = JsonParser.parseReader(reader).getAsJsonObject();

            pending = new PendingState(
                UUID.fromString(object.get("playerUuid").getAsString()),
                object.get("worldKey").getAsString(),
                object.get("originalGameMode").getAsString(),
                object.has("createdAt") ? object.get("createdAt").getAsLong() : 0L
            );
        }
        catch (Exception e)
        {
            pending = null;
            BBSPlusPlusMod.LOGGER.warn("读取自动旁观模式恢复状态失败，已清除残留状态。", e);
            deleteStateFile();
        }
    }

    private static void savePending()
    {
        loaded = true;

        try
        {
            Files.createDirectories(getStateFile().getParent());

            JsonObject object = new JsonObject();
            object.addProperty("version", VERSION);
            object.addProperty("playerUuid", pending.playerUuid.toString());
            object.addProperty("worldKey", pending.worldKey);
            object.addProperty("originalGameMode", pending.originalGameMode);
            object.addProperty("createdAt", pending.createdAt);

            try (Writer writer = Files.newBufferedWriter(getStateFile()))
            {
                writer.write(object.toString());
            }
        }
        catch (Exception e)
        {
            BBSPlusPlusMod.LOGGER.warn("保存自动旁观模式恢复状态失败，本次异常退出可能无法自动恢复。", e);
        }
    }

    private static void clearPending()
    {
        pending = null;
        loaded = true;
        deleteStateFile();
    }

    private static void deleteStateFile()
    {
        try
        {
            Files.deleteIfExists(getStateFile());
        }
        catch (Exception e)
        {
            BBSPlusPlusMod.LOGGER.warn("删除自动旁观模式恢复状态失败。", e);
        }
    }

    private static Path getStateFile()
    {
        return FMLPaths.GAMEDIR.get()
            .resolve("config")
            .resolve(BBSPlusPlusMod.MOD_ID)
            .resolve(FILE_NAME);
    }

    private static String getWorldKey(Minecraft client)
    {
        ServerData serverInfo = client.getCurrentServer();

        if (serverInfo != null && serverInfo.ip != null && !serverInfo.ip.isBlank())
        {
            return "server:" + serverInfo.ip.toLowerCase(Locale.ROOT);
        }

        if (client.hasSingleplayerServer() && client.getSingleplayerServer() != null)
        {
            return "singleplayer:" + client.getSingleplayerServer().getWorldData().getLevelName();
        }

        return null;
    }

    /**
     * 一次未完成的自动旁观模式恢复记录。
     * <p>
     * 记录玩家、世界/服务器和原游戏模式，用于确保下次补救只作用在同一名玩家
     * 重新进入同一个上下文时，降低误切其它存档或服务器模式的风险。
     * </p>
     */
    private static class PendingState
    {
        private final UUID playerUuid;
        private final String worldKey;
        private final String originalGameMode;
        private final long createdAt;

        private PendingState(UUID playerUuid, String worldKey, String originalGameMode, long createdAt)
        {
            this.playerUuid = playerUuid;
            this.worldKey = worldKey;
            this.originalGameMode = originalGameMode;
            this.createdAt = createdAt;
        }

        private boolean matches(Minecraft client)
        {
            return client.player != null
                && this.playerUuid.equals(client.player.getUUID())
                && this.worldKey.equals(getWorldKey(client));
        }
    }
}
