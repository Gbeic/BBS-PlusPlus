package gbeic.bbsplusplus.mixin;

import gbeic.bbsplusplus.IMBlockerCompat;
import gbeic.bbsplusplus.client.structure.StructureStickSelection;
import gbeic.bbsplusplus.client.structure.VFXDestructionWandSelection;
import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/*
 * MinecraftClient Mixin
 *
 * 1. 在 MinecraftClient 的 tick 方法末尾添加一个注入，用于检查当前是否正在播放第一人称回放，并且回放是否已经结束。如果回放已经结束，则逐渐将 FOV 乘数恢复到 1.0，以实现平滑的 FOV 过渡效果。
 * 2. 通过这种方式，用户在使用 BBS++ 的新版本时，无需担心第一人称回放结束后 FOV 突然跳变的问题，即使在旧版本中创建了第一人称回放的录像文件，在新版本中也会自动实现平滑过渡。
 */

@Mixin(MinecraftClient.class)
public class MinecraftClientMixin {

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
     * 注入目标：{@link MinecraftClient#onWindowFocusChanged(boolean)} 的末尾。
     * <p>
     * 当玩家在 BBS 文本输入框中切到其它程序再切回 Minecraft 时，BBS 文本框仍保持焦点，
     * 但不会再次调用文本框的 focus()。这里在窗口重新激活后根据兼容层保存的文本输入焦点状态
     * 重新通知 IMBlocker，避免输入法被继续锁定为英文。
     * </p>
     */
    @Inject(method = "onWindowFocusChanged", at = @At("TAIL"))
    private void bbspp$restoreIMBlockerTextInputState(boolean focused, CallbackInfo ci) {
        IMBlockerCompat.restoreTextInputAfterWindowFocus(focused);
    }

    /**
     * 注入目标：{@link MinecraftClient#doItemUse()} 入口。
     * 注入原因：选区工具用「使用」键做框选，如果不拦住原版逻辑，手里拿着工具对着方块按右键会去触发方块交互。
     * 修改行为：手持 BBS++ 接管的选区工具且没有打开界面时取消原版的物品使用。
     */
    @Inject(method = "doItemUse", at = @At("HEAD"), cancellable = true)
    private void bbspp$blockStructureStickUse(CallbackInfo ci) {
        MinecraftClient client = (MinecraftClient) (Object) this;

        if (StructureStickSelection.shouldBlockUse(client) || VFXDestructionWandSelection.shouldBlockUse(client)) {
            ci.cancel();
        }
    }

    /**
     * 注入目标：{@link MinecraftClient#doAttack()} 入口。
     * 注入原因：选区工具用「攻击」键改终点和清除选区，不能真的去打方块或实体。
     * 修改行为：手持 BBS++ 接管的选区工具且没有打开界面时直接返回 true（表示已处理），跳过原版攻击。
     */
    @Inject(method = "doAttack", at = @At("HEAD"), cancellable = true)
    private void bbspp$blockStructureStickAttack(CallbackInfoReturnable<Boolean> cir) {
        MinecraftClient client = (MinecraftClient) (Object) this;

        if (StructureStickSelection.shouldBlockAttack(client) || VFXDestructionWandSelection.shouldBlockAttack(client)) {
            cir.setReturnValue(true);
        }
    }

    /**
     * 注入目标：{@link MinecraftClient#handleBlockBreaking(boolean)} 入口。
     * 注入原因：长按攻击键会持续触发挖掘逻辑，选区工具需要长按来清除选区，必须一并拦住。
     * 修改行为：手持 BBS++ 接管的选区工具且没有打开界面时取消挖掘。
     */
    @Inject(method = "handleBlockBreaking", at = @At("HEAD"), cancellable = true)
    private void bbspp$blockStructureStickBreaking(boolean breaking, CallbackInfo ci) {
        MinecraftClient client = (MinecraftClient) (Object) this;

        if (StructureStickSelection.shouldBlockAttack(client) || VFXDestructionWandSelection.shouldBlockAttack(client)) {
            ci.cancel();
        }
    }
}
