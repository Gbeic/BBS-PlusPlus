package gbeic.bbsplusplus.mixin;

import gbeic.bbsplusplus.IMBlockerCompat;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/*
 * Minecraft Mixin（1.20.1 Yarn 时期名为 MinecraftClientMixin）
 *
 * 1. 在 Minecraft 的 tick 方法末尾添加一个注入，用于检查当前是否正在播放第一人称回放，并且回放是否已经结束。如果回放已经结束，则逐渐将 FOV 乘数恢复到 1.0，以实现平滑的 FOV 过渡效果。
 * 2. 通过这种方式，用户在使用 BBS++ 的新版本时，无需担心第一人称回放结束后 FOV 突然跳变的问题，即使在旧版本中创建了第一人称回放的录像文件，在新版本中也会自动实现平滑过渡。
 */

@Mixin(Minecraft.class)
public class MinecraftMixin {

    @Inject(method = "tick", at = @At("TAIL"))
    public void onTick(CallbackInfo ci) {
        IMBlockerCompat.tickPendingRestore();

        if (gbeic.bbsplusplus.BBSPlusPlusState.activeFilmId != null) {
            // 通过底层记录安全检查该回放是否已经结束
            boolean isStillPlaying = false;
            if (mchorse.bbs_mod.BBSModClient.getFilms() != null) {
                isStillPlaying = mchorse.bbs_mod.BBSModClient.getFilms().has(gbeic.bbsplusplus.BBSPlusPlusState.activeFilmId);
            }

            if (!isStillPlaying) {
                gbeic.bbsplusplus.BBSPlusPlusState.targetFovMultiplier = 1.0F;
                gbeic.bbsplusplus.BBSPlusPlusState.prevFovMultiplier = gbeic.bbsplusplus.BBSPlusPlusState.smoothedFovMultiplier;
                gbeic.bbsplusplus.BBSPlusPlusState.smoothedFovMultiplier += (1.0F - gbeic.bbsplusplus.BBSPlusPlusState.smoothedFovMultiplier) * 0.5F;

                // 完全复原后彻底清除 ID 以节约性能
                if (Math.abs(gbeic.bbsplusplus.BBSPlusPlusState.smoothedFovMultiplier - 1.0F) < 0.001F) {
                    gbeic.bbsplusplus.BBSPlusPlusState.activeFilmId = null;
                }
            }
        }
    }

    /**
     * 注入目标：{@link Minecraft#setWindowActive(boolean)} 的末尾（1.20.1 Yarn 中的 {@code onWindowFocusChanged}）。
     * <p>
     * 当玩家在 BBS 文本输入框中切到其它程序再切回 Minecraft 时，BBS 文本框仍保持焦点，
     * 但不会再次调用文本框的 focus()。这里在窗口重新激活后根据兼容层保存的文本输入焦点状态
     * 重新通知 IMBlocker，避免输入法被继续锁定为英文。
     * </p>
     */
    @Inject(method = "setWindowActive", at = @At("TAIL"))
    private void bbspp$restoreIMBlockerTextInputState(boolean focused, CallbackInfo ci) {
        IMBlockerCompat.restoreTextInputAfterWindowFocus(focused);
    }
}
