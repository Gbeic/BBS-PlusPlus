package gbeic.bbsplusplus.network;

import gbeic.bbsplusplus.structure.StructureSaver;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

/**
 * 结构棒的服务端保存通道。
 * <p>
 * 移植自 BBSTools 4.1。走服务端保存能拿到完整的方块实体数据（箱子内容、告示牌文字等），
 * 所以只要服务端也装了 BBS++ 就优先走这条路；服务端没装时客户端会自行本地保存。
 * </p>
 */
public final class StructureStickNetworking
{
    public static final Identifier SAVE_STRUCTURE = new Identifier("bbsplusplus", "save_structure");

    private StructureStickNetworking()
    {}

    public static void registerServer()
    {
        ServerPlayNetworking.registerGlobalReceiver(SAVE_STRUCTURE, (server, player, handler, buf, responder) ->
        {
            String name = buf.readString(128);
            BlockPos from = buf.readBlockPos();
            BlockPos to = buf.readBlockPos();

            server.execute(() -> save(server, player, name, from, to));
        });
    }

    private static void save(MinecraftServer server, ServerPlayerEntity player, String requestedName, BlockPos from, BlockPos to)
    {
        if (player == null || player.getWorld() == null)
        {
            return;
        }

        // 保存结构会读取整片区域，限制为创造模式或管理员，避免生存服上被滥用
        if (!player.isCreative() && !player.hasPermissionLevel(2))
        {
            player.sendMessage(Text.translatable("bbsplusplus.structure_stick.no_permission"), true);

            return;
        }

        StructureSaver.SaveResult result = StructureSaver.save(player.getServerWorld(), requestedName, from, to);

        player.sendMessage(Text.translatable(result.messageKey(), result.name()), true);
    }
}
