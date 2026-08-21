package gbeic.bbsplusplus.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import mchorse.bbs_mod.blocks.entities.ModelBlockEntity;
import gbeic.bbsplusplus.forms.AAAParticleForm;
import gbeic.bbsplusplus.network.BBSPlusPlusNetwork;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * BBSPlusPlusCommand
 *
 * 提供 /bbsplusplus trigger <pos> <triggerIndex> 命令，用于触发 AAA 粒子表单的触发器。
 * 该命令会向指定坐标的模型方块发送一个网络包，通知其触发对应索引的触发器。
 * 主要用于配合命令方块实现红石信号触发 AAA 粒子效果。
 */

public class BBSPlusPlusCommand
{
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext registryAccess, Commands.CommandSelection environment)
    {
        dispatcher.register(Commands.literal("bbsplusplus")
            .requires(source -> source.hasPermission(2))
            .then(Commands.literal("trigger")
                .then(Commands.argument("pos", BlockPosArgument.blockPos())
                    .then(Commands.argument("triggerIndex", IntegerArgumentType.integer(0, 3))
                        .executes(BBSPlusPlusCommand::executeTrigger)
                    )
                )
            )
        );
    }

    private static int executeTrigger(CommandContext<CommandSourceStack> context) throws CommandSyntaxException
    {
        CommandSourceStack source = context.getSource();
        ServerLevel world = source.getLevel();
        BlockPos pos = BlockPosArgument.getSpawnablePos(context, "pos");
        int triggerIndex = IntegerArgumentType.getInteger(context, "triggerIndex");

        BlockEntity be = world.getBlockEntity(pos);
        if (be instanceof ModelBlockEntity)
        {
            ModelBlockEntity modelBe = (ModelBlockEntity) be;
            if (modelBe.getProperties().getForm() instanceof AAAParticleForm)
            {
                // 发送网络包通知客户端触发
                BBSPlusPlusNetwork.broadcastTrigger(world, pos, triggerIndex);
                source.sendSuccess(() -> Component.literal("已成功发送 AAA 粒子触发器信号 (索引: " + triggerIndex + ") 到坐标 " + pos.toShortString()), true);
                return 1;
            }
            else
            {
                source.sendFailure(Component.literal("目标模型方块挂载的不是 AAA 粒子表单"));
            }
        }
        else
        {
            source.sendFailure(Component.literal("目标坐标没有模型方块 (ModelBlockEntity)"));
        }

        return 0;
    }
}
