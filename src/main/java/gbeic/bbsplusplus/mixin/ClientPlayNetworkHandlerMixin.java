package gbeic.bbsplusplus.mixin;

import gbeic.bbsplusplus.util.GameModeMessageSuppressor;

import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.network.chat.Component;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin — 过滤插件自动切换游戏模式时的系统提示消息。
 * <p>
 * 拦截 {@link ChatComponent#addMessage(Component)}。
 * 时间窗口由 {@link GameModeMessageSuppressor} 控制，
 * 只有 {@link UIFilmPanelGameModeMixin} 触发后 1 秒内的模式切换提示会被过滤。
 * </p>
 */
@Mixin(ChatComponent.class)
public class ClientPlayNetworkHandlerMixin
{
    private static boolean bbs_isGameModeMessage(String text)
    {
        if (text == null) return false;

        if (text.contains("已将自己的游戏模式设置为")) return true;
        if (text.contains("Set own game mode to")) return true;
        if (text.contains("ゲームモードを")) return true;

        return false;
    }

    @Inject(
        method = "addMessage(Lnet/minecraft/network/chat/Component;)V",
        at = @At("HEAD"),
        cancellable = true
    )
    private void bbs_onAddMessage(Component text, CallbackInfo ci)
    {
        if (GameModeMessageSuppressor.isActive() && bbs_isGameModeMessage(text.getString()))
        {
            GameModeMessageSuppressor.clear();
            ci.cancel();
        }
    }
}
