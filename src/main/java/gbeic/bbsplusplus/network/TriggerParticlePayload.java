package gbeic.bbsplusplus.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * 手动触发粒子的数据包（服务端 -> 客户端）。
 * <p>
 * 1.21 起原版网络层不再允许直接往通道里塞裸 {@code PacketByteBuf}，每个自定义包都必须是
 * 一个带类型标识和 {@link StreamCodec} 的 {@link CustomPacketPayload}。这里承载的内容与
 * 1.20.1 版本一致：目标模型方块的坐标，以及被触发的触发器序号。
 * </p>
 */
public record TriggerParticlePayload(BlockPos pos, int triggerIndex) implements CustomPacketPayload
{
    public static final CustomPacketPayload.Type<TriggerParticlePayload> TYPE =
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("bbsplusplus", "trigger_particle"));

    public static final StreamCodec<ByteBuf, TriggerParticlePayload> CODEC = StreamCodec.composite(
        BlockPos.STREAM_CODEC, TriggerParticlePayload::pos,
        ByteBufCodecs.VAR_INT, TriggerParticlePayload::triggerIndex,
        TriggerParticlePayload::new
    );

    @Override
    public CustomPacketPayload.Type<TriggerParticlePayload> type()
    {
        return TYPE;
    }
}
