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

    @Inject(method = "apply", at = @At("HEAD"))
    private void bbspp$onApplyHead(net.minecraft.entity.LivingEntity actor, Replay replay, float tick, boolean ticking, CallbackInfo ci) {
        if (actor instanceof net.minecraft.server.network.ServerPlayerEntity && replay.fp.get()) {
            bbspp$currentReplay.set(replay);
        }
    }

    @Inject(method = "apply", at = @At("TAIL"))
    private void bbspp$onApplyTail(net.minecraft.entity.LivingEntity actor, Replay replay, float tick, boolean ticking, CallbackInfo ci) {
        bbspp$currentReplay.remove();
    }

    /**
     * 拦截 ActionPlayer.apply 中的 equipStack 调用。
     * 如果是对真实的玩家（ServerPlayerEntity）应用回放，且对应的物品栏关键帧轨道是空的（用户没录制物品），
     * 就不去清空玩家的现有物品，以防止玩家在播放第一人称回放时右手消失或物品栏被清空。
     */
    @org.spongepowered.asm.mixin.injection.Redirect(
        method = "apply",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/LivingEntity;equipStack(Lnet/minecraft/entity/EquipmentSlot;Lnet/minecraft/item/ItemStack;)V")
    )
    private void bbspp$redirectEquipStack(net.minecraft.entity.LivingEntity instance, net.minecraft.entity.EquipmentSlot slot, net.minecraft.item.ItemStack stack)
    {
        Replay replay = bbspp$currentReplay.get();
        if (replay != null && instance instanceof net.minecraft.server.network.ServerPlayerEntity)
        {
            mchorse.bbs_mod.utils.keyframes.KeyframeChannel<net.minecraft.item.ItemStack> channel = null;
            if (slot == net.minecraft.entity.EquipmentSlot.MAINHAND) channel = replay.keyframes.mainHand;
            else if (slot == net.minecraft.entity.EquipmentSlot.OFFHAND) channel = replay.keyframes.offHand;
            else if (slot == net.minecraft.entity.EquipmentSlot.HEAD) channel = replay.keyframes.armorHead;
            else if (slot == net.minecraft.entity.EquipmentSlot.CHEST) channel = replay.keyframes.armorChest;
            else if (slot == net.minecraft.entity.EquipmentSlot.LEGS) channel = replay.keyframes.armorLegs;
            else if (slot == net.minecraft.entity.EquipmentSlot.FEET) channel = replay.keyframes.armorFeet;

            if (channel != null && channel.isEmpty())
            {
                // 如果没有录制任何该部位的关键帧，直接跳过，不要用空的 stack 去覆盖玩家的真实物品
                return;
            }
        }

        instance.equipStack(slot, stack);
    }
}
