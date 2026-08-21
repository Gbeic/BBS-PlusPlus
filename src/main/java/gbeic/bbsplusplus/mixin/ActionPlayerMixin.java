package gbeic.bbsplusplus.mixin;

import mchorse.bbs_mod.actions.ActionPlayer;
import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.utils.DataPath;
import mchorse.bbs_mod.settings.values.base.BaseValue;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = ActionPlayer.class, remap = false)
public class ActionPlayerMixin
{
    /**
     * 拦截 ActionPlayer.syncData，以防客户端在给一个新的属性打关键帧时，
     * 服务端还没有创建该属性对应的 KeyframeChannel 导致 Property can't be found 报错崩溃。
     */
    @Inject(method = "syncData", at = @At("HEAD"))
    private void bbspp$preSyncDataCreateChannel(DataPath key, BaseType data, CallbackInfo ci)
    {
        ActionPlayer self = (ActionPlayer) (Object) this;

        if (self.film != null && key != null && key.size() >= 4)
        {
            // 正确的 DataPath: replays / 录像ID / properties / 轨道名 / ...
            if ("replays".equals(key.strings.get(0)) && "properties".equals(key.strings.get(2)))
            {
                String replayId = key.strings.get(1);
                String propertyKey = key.strings.get(3);

                // 尝试拿到 Replay
                BaseValue replayValue = self.film.replays.get(replayId);
                
                if (replayValue instanceof Replay)
                {
                    Replay replay = (Replay) replayValue;
                    Form form = replay.form.get();
                    
                    if (form != null)
                    {
                        // 这会自动帮服务端创建好缺失的 KeyframeChannel，
                        // 然后原始方法中的 film.getRecursively(key) 就不会抛出 IllegalStateException 了！
                        replay.properties.getOrCreate(form, propertyKey);
                    }
                }
            }
        }
    }

    private static final ThreadLocal<Replay> bbspp$currentReplay = new ThreadLocal<>();

    /*
     * 以下三个注入的目标从 apply 改为 applySafely。
     *
     * FSR（BBS 2.3.2）把原先集中在 apply 里的回放应用逻辑抽到了新的私有方法 applySafely，
     * apply 只保留运行权限检查后转调。因此装备写入（setItemSlot）已不在 apply 的方法体内，
     * 继续注入 apply 会因扫描不到目标而导致服务端崩溃。applySafely 才是 1.20.1 时期 apply
     * 的等价物。
     *
     * 另外退出点用 RETURN 而非 TAIL：applySafely 带有多处提前 return false（姿态/速度/坠落
     * 距离被策略拒绝时），TAIL 只覆盖最后一条 return，那些提前退出会让 ThreadLocal 残留到
     * 下一次调用，导致后续回放误用上一次的 Replay。RETURN 覆盖全部退出路径。
     */

    /* applySafely 返回 boolean，Mixin 要求非 void 目标方法的回调参数必须是 CallbackInfoReturnable。 */

    @Inject(method = "applySafely", at = @At("HEAD"))
    private void bbspp$onApplyHead(net.minecraft.world.entity.LivingEntity actor, Replay replay, float tick, boolean ticking, CallbackInfoReturnable<Boolean> cir) {
        if (actor instanceof net.minecraft.server.level.ServerPlayer && replay.fp.get()) {
            bbspp$currentReplay.set(replay);
        }
    }

    @Inject(method = "applySafely", at = @At("RETURN"))
    private void bbspp$onApplyTail(net.minecraft.world.entity.LivingEntity actor, Replay replay, float tick, boolean ticking, CallbackInfoReturnable<Boolean> cir) {
        bbspp$currentReplay.remove();
    }

    /**
     * 拦截 ActionPlayer.applySafely 中的 setItemSlot 调用。
     * 如果是对真实的玩家（ServerPlayer）应用回放，且对应的物品栏关键帧轨道是空的（用户没录制物品），
     * 就不去清空玩家的现有物品，以防止玩家在播放第一人称回放时右手消失或物品栏被清空。
     */
    @org.spongepowered.asm.mixin.injection.Redirect(
        method = "applySafely",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;setItemSlot(Lnet/minecraft/world/entity/EquipmentSlot;Lnet/minecraft/world/item/ItemStack;)V")
    )
    private void bbspp$redirectEquipStack(net.minecraft.world.entity.LivingEntity instance, net.minecraft.world.entity.EquipmentSlot slot, net.minecraft.world.item.ItemStack stack)
    {
        Replay replay = bbspp$currentReplay.get();
        if (replay != null && instance instanceof net.minecraft.server.level.ServerPlayer)
        {
            mchorse.bbs_mod.utils.keyframes.KeyframeChannel<net.minecraft.world.item.ItemStack> channel = null;
            if (slot == net.minecraft.world.entity.EquipmentSlot.MAINHAND) channel = replay.keyframes.mainHand;
            else if (slot == net.minecraft.world.entity.EquipmentSlot.OFFHAND) channel = replay.keyframes.offHand;
            else if (slot == net.minecraft.world.entity.EquipmentSlot.HEAD) channel = replay.keyframes.armorHead;
            else if (slot == net.minecraft.world.entity.EquipmentSlot.CHEST) channel = replay.keyframes.armorChest;
            else if (slot == net.minecraft.world.entity.EquipmentSlot.LEGS) channel = replay.keyframes.armorLegs;
            else if (slot == net.minecraft.world.entity.EquipmentSlot.FEET) channel = replay.keyframes.armorFeet;

            if (channel != null && channel.isEmpty())
            {
                // 如果没有录制任何该部位的关键帧，直接跳过，不要用空的 stack 去覆盖玩家的真实物品
                return;
            }
        }

        instance.setItemSlot(slot, stack);
    }
}
