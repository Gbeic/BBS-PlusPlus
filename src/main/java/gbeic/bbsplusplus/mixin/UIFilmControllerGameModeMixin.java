package gbeic.bbsplusplus.mixin;

import mchorse.bbs_mod.ui.film.controller.UIFilmController;
import gbeic.bbsplusplus.BBSAddonsSettings;
import gbeic.bbsplusplus.util.GameModeMessageSuppressor;
import mchorse.bbs_mod.settings.values.numeric.ValueBoolean;
import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin — H 键控制/释放演员时配合自动旁观模式维护正确的游戏模式。
 * <p>
 * bbs-fs 的 {@link UIFilmController#toggleControl()} 中<b>没有任何</b>
 * 游戏模式切换逻辑（无论 {@code replacePlayer} 真假）。
 * bbs-mod-nyk 在控制时补了 {@code PlayerUtils.gamemode(CREATIVE)}，
 * 释放时补了 {@code PlayerUtils.gamemode(SPECTATOR)}。
 * </p>
 * <p>
 * 此 Mixin 在 {@code toggleControl()} 执行完毕后补充缺失的模式切换：
 * <ul>
 *   <li><b>控制演员</b>（从无到有）：切换到创造模式，防止演员穿墙；</li>
 *   <li><b>释放演员</b>（从有到无）：切回旁观模式，同时抑制系统消息。</li>
 * </ul>
 * 仅在 {@code filmAutoGameMode} 开启时生效，关闭时不干预原版行为。
 * </p>
 */
@Mixin(UIFilmController.class)
public abstract class UIFilmControllerGameModeMixin
{
    /** 记录 toggleControl() 调用前是否已在控制演员 */
    @Unique
    private boolean bbs_wasControllingBeforeToggle;

    @Shadow
    private mchorse.bbs_mod.forms.entities.IEntity controlled;

    @Inject(
        method = "toggleControl",
        at = @At("HEAD"),
        remap = false
    )
    private void onToggleControlHead(CallbackInfo ci)
    {
        this.bbs_wasControllingBeforeToggle = this.controlled != null;
    }

    @Inject(
        method = "toggleControl",
        at = @At("TAIL"),
        remap = false
    )
    private void onToggleControlTail(CallbackInfo ci)
    {
        if (BBSAddonsSettings.filmAutoGameMode != null
            && !BBSAddonsSettings.filmAutoGameMode.get())
        {
            return; /* 设置关闭，不做干预 */
        }

        MinecraftClient mc = MinecraftClient.getInstance();

        if (!this.bbs_wasControllingBeforeToggle && this.controlled != null)
        {
            /* 刚控制了一个演员（从无到有） */

            if (mc.player != null)
            {
                /*
                 * 原版代码在 replacePlayer=false 时不会切换游戏模式，
                 * 需要手动切换到创造模式，防止演员继承旁观模式而穿墙。
                 */
                GameModeMessageSuppressor.suppressNext();
                mc.player.networkHandler.sendCommand("gamemode creative");
            }
        }
        else if (this.bbs_wasControllingBeforeToggle && this.controlled == null)
        {
            /* 刚释放了演员（从有到无） */
            /*
             * bbs-fs 在释放时不会切换游戏模式，玩家仍停留在创造模式，
             * 导致玩家身体停留在伪装位置可见。需要手动切回旁观模式，
             * 匹配 bbs-mod-nyk 的 PlayerUtils.gamemode(SPECTATOR) 行为。
             */
            if (mc.player != null)
            {
                GameModeMessageSuppressor.suppressNext();
                mc.player.networkHandler.sendCommand("gamemode spectator");
            }
        }
        /* else: 既非控制也非释放（如未选中回放条目），不做任何事 */
    }

    /**
     * 注入目标：{@link UIFilmController#renderHUD(mchorse.bbs_mod.ui.framework.UIContext, mchorse.bbs_mod.ui.utils.Area)}
     * 中用于绘制原版循环状态图标的 {@code BBSSettings.editorLoop.get()} 判断。
     * 注入原因：BBS++ 已经把循环开关移动到影片顶部工具栏，预览窗口右上角的原版提示图标会重复显示。
     * 修改行为：仅在 HUD 绘制阶段隐藏原版循环图标，不影响循环播放逻辑和顶部工具栏按钮状态。
     */
    @Redirect(
        method = "renderHUD",
        at = @At(value = "INVOKE", target = "Lmchorse/bbs_mod/settings/values/numeric/ValueBoolean;get()Ljava/lang/Object;"),
        remap = false
    )
    private Object bbsplusplus$hideOriginalLoopHudIcon(ValueBoolean editorLoop)
    {
        return Boolean.FALSE;
    }
}
