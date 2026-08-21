package gbeic.bbsplusplus.mixin;

import mchorse.bbs_mod.ui.film.UIFilmPanel;
import gbeic.bbsplusplus.BBSAddonsSettings;
import gbeic.bbsplusplus.util.FilmAutoGameModeRestoreState;
import gbeic.bbsplusplus.util.GameModeMessageSuppressor;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.world.level.GameType;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin — 相机界面进入时自动切换旁观模式，退出时恢复。
 * <p>
 * 在 {@link UIFilmPanel#appear()} 时保存当前游戏模式并切换到旁观者模式，
 * 在 {@link UIFilmPanel#close()} 时恢复之前保存的游戏模式。
 * 保存状态会同步写入配置文件，用于异常退出后的下次启动兜底恢复。
 * 方便摄像师在编辑影片时自由穿墙移动视角。
 * </p>
 */
@Mixin(UIFilmPanel.class)
public abstract class UIFilmPanelGameModeMixin
{
    /** 进入相机前保存的游戏模式，退出时恢复 */
    @Unique
    private GameType bbs_savedGameMode = null;

    @Inject(
        method = "appear",
        at = @At("HEAD"),
        remap = false
    )
    private void onFilmPanelAppear(CallbackInfo ci)
    {
        if (BBSAddonsSettings.filmAutoGameMode != null
            && !BBSAddonsSettings.filmAutoGameMode.get())
        {
            return; /* 设置关闭，不自动切换 */
        }

        Minecraft mc = Minecraft.getInstance();

        if (((UIFilmPanel) (Object) this).getContext() == null)
        {
            return; /* 面板懒加载构造时不是真正打开编辑器，不能切换模式 */
        }

        if (mc.player != null && mc.getConnection() != null)
        {
            PlayerInfo entry = mc.getConnection().getPlayerInfo(mc.player.getUUID());

            if (entry != null)
            {
                GameType current = entry.getGameMode();

                if (current != GameType.SPECTATOR)
                {
                    this.bbs_savedGameMode = current;
                    FilmAutoGameModeRestoreState.markAutoSwitch(mc, current);
                    GameModeMessageSuppressor.suppressNext();
                    mc.player.connection.sendCommand("gamemode spectator");
                }
            }
        }
    }

    @Inject(
        method = "close",
        at = @At("HEAD"),
        remap = false
    )
    private void onFilmPanelClose(CallbackInfo ci)
    {
        FilmAutoGameModeRestoreState.restoreFromFilmPanel(
            Minecraft.getInstance(),
            this.bbs_savedGameMode
        );

        this.bbs_savedGameMode = null;
    }
}
