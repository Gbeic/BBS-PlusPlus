package gbeic.bbsplusplus.network;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * BBS++ 的网络通道。
 * <p>
 * 相比 Fabric 的 {@code ServerPlayNetworking.send(player, id, buf)}，NeoForge 要求先在
 * {@link RegisterPayloadHandlersEvent} 中声明包类型和处理器，之后才能通过
 * {@link PacketDistributor} 发送。
 * </p>
 */
public class BBSPlusPlusNetwork
{
    /** 协议版本，双方不一致时 NeoForge 会拒绝连接 */
    private static final String NETWORK_VERSION = "1";

    /**
     * 声明数据包类型与接收逻辑。由 {@link gbeic.bbsplusplus.BBSPlusPlusMod} 挂到模组事件总线。
     */
    public static void onRegisterPayloadHandlers(RegisterPayloadHandlersEvent event)
    {
        PayloadRegistrar registrar = event.registrar(NETWORK_VERSION);

        registrar.playToClient(
            TriggerParticlePayload.TYPE,
            TriggerParticlePayload.CODEC,
            (payload, context) ->
            {
                // 服务端不存在客户端类，必须先确认当前物理端再触碰客户端逻辑
                if (FMLEnvironment.dist != Dist.CLIENT)
                {
                    return;
                }

                context.enqueueWork(() ->
                    gbeic.bbsplusplus.client.network.TriggerParticleClientHandler.handle(payload));
            }
        );
    }

    /**
     * 服务端：广播触发器数据包到附近的玩家
     */
    public static void broadcastTrigger(ServerLevel world, BlockPos pos, int triggerIndex)
    {
        TriggerParticlePayload payload = new TriggerParticlePayload(pos, triggerIndex);

        // 广播给当前维度的所有玩家
        for (ServerPlayer player : world.players())
        {
            // 如果需要，可以优化为仅广播给附近玩家，这里简单广播给同维度的所有玩家
            PacketDistributor.sendToPlayer(player, payload);
        }
    }
}
