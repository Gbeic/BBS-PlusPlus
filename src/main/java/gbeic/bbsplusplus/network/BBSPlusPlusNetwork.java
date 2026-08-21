package gbeic.bbsplusplus.network;

import io.netty.buffer.Unpooled;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

public class BBSPlusPlusNetwork
{
    public static final Identifier TRIGGER_PARTICLE = new Identifier("bbsplusplus", "trigger_particle");

    /**
     * 服务端：广播触发器数据包到附近的玩家
     */
    public static void broadcastTrigger(ServerWorld world, BlockPos pos, int triggerIndex)
    {
        PacketByteBuf buf = new PacketByteBuf(Unpooled.buffer());
        buf.writeBlockPos(pos);
        buf.writeInt(triggerIndex);

        // 广播给当前维度的所有玩家
        for (ServerPlayerEntity player : world.getPlayers())
        {
            // 如果需要，可以优化为仅广播给附近玩家，这里简单广播给同维度的所有玩家
            ServerPlayNetworking.send(player, TRIGGER_PARTICLE, buf);
        }
    }
}
