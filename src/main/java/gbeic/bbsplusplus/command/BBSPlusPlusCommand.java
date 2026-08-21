package gbeic.bbsplusplus.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import mchorse.bbs_mod.blocks.entities.ModelBlockEntity;
import gbeic.bbsplusplus.forms.AAAParticleForm;
import gbeic.bbsplusplus.network.BBSPlusPlusNetwork;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.command.argument.BlockPosArgumentType;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

/**
 * BBSPlusPlusCommand
 *
 * 提供 /bbsplusplus trigger <pos> <triggerIndex> 命令，用于触发 AAA 粒子表单的触发器。
 * 该命令会向指定坐标的模型方块发送一个网络包，通知其触发对应索引的触发器。
 * 主要用于配合命令方块实现红石信号触发 AAA 粒子效果。
 */

public class BBSPlusPlusCommand
{
    public static void register(CommandDispatcher<ServerCommandSource> dispatcher, CommandRegistryAccess registryAccess, CommandManager.RegistrationEnvironment environment)
    {
        dispatcher.register(CommandManager.literal("bbsplusplus")
            .requires(source -> source.hasPermissionLevel(2))
            .then(CommandManager.literal("trigger")
                .then(CommandManager.argument("pos", BlockPosArgumentType.blockPos())
                    .then(CommandManager.argument("triggerIndex", IntegerArgumentType.integer(0, 3))
                        .executes(BBSPlusPlusCommand::executeTrigger)
                    )
                )
            )
        );
    }

    private static int executeTrigger(CommandContext<ServerCommandSource> context) throws CommandSyntaxException
    {
        ServerCommandSource source = context.getSource();
        ServerWorld world = source.getWorld();
        BlockPos pos = BlockPosArgumentType.getBlockPos(context, "pos");
        int triggerIndex = IntegerArgumentType.getInteger(context, "triggerIndex");

        BlockEntity be = world.getBlockEntity(pos);
        if (be instanceof ModelBlockEntity)
        {
            ModelBlockEntity modelBe = (ModelBlockEntity) be;
            if (modelBe.getProperties().getForm() instanceof AAAParticleForm)
            {
                // 发送网络包通知客户端触发
                BBSPlusPlusNetwork.broadcastTrigger(world, pos, triggerIndex);
                source.sendFeedback(() -> Text.literal("已成功发送 AAA 粒子触发器信号 (索引: " + triggerIndex + ") 到坐标 " + pos.toShortString()), true);
                return 1;
            }
            else
            {
                source.sendError(Text.literal("目标模型方块挂载的不是 AAA 粒子表单"));
            }
        }
        else
        {
            source.sendError(Text.literal("目标坐标没有模型方块 (ModelBlockEntity)"));
        }

        return 0;
    }
}
