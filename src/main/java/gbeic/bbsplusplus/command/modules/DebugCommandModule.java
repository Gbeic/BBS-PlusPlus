package gbeic.bbsplusplus.command.modules;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import gbeic.bbsplusplus.client.compat.shadercurves.ShaderCurveDebug;
import gbeic.bbsplusplus.client.debug.ItemSprayDebug;
import gbeic.bbsplusplus.client.debug.VideoDebug;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;

import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * BBS++ 调试指令模块。
 * <p>
 * 提供 {@code /bbsplusplus debug} 下的调试开关和状态查询。调试模块注册在服务端命令树中，
 * 避免客户端同名根命令与服务端命令树互相抢解析；BBS++ 是客户端环境模组，集成服务器执行该命令时
 * 可以直接修改客户端调试状态。
 * </p>
 */
public class DebugCommandModule implements BBSPlusPlusCommandModule
{
    private static final List<DebugModule> MODULES = List.of(
        new DebugModule("shader_curves", "光影曲线", ShaderCurveDebug::isShaderCurvePatches, ShaderCurveDebug::setShaderCurvePatches),
        new DebugModule("item_spray", "物品喷射", ItemSprayDebug::isEnabled, ItemSprayDebug::setEnabled),
        new DebugModule("video", "视频伪装", VideoDebug::isEnabled, VideoDebug::setEnabled)
    );

    @Override
    public LiteralArgumentBuilder<ServerCommandSource> create()
    {
        LiteralArgumentBuilder<ServerCommandSource> debug = CommandManager.literal("debug")
            .then(CommandManager.literal("all")
                .then(CommandManager.literal("on").executes((context) -> setAll(context, true)))
                .then(CommandManager.literal("off").executes((context) -> setAll(context, false)))
                .then(CommandManager.literal("status").executes(DebugCommandModule::showAllStatus))
            );

        for (DebugModule module : MODULES)
        {
            debug.then(CommandManager.literal(module.id())
                .then(CommandManager.literal("on").executes((context) -> setModule(context, module, true)))
                .then(CommandManager.literal("off").executes((context) -> setModule(context, module, false)))
                .then(CommandManager.literal("status").executes((context) -> showModuleStatus(context, module)))
            );
        }

        return debug;
    }

    private static int setModule(CommandContext<ServerCommandSource> context, DebugModule module, boolean enabled)
    {
        module.setEnabled(enabled);
        context.getSource().sendFeedback(() -> Text.literal("[BBS++] 调试模块 " + module.label() + " 已" + formatEnabled(enabled)), false);

        return 1;
    }

    private static int setAll(CommandContext<ServerCommandSource> context, boolean enabled)
    {
        for (DebugModule module : MODULES)
        {
            module.setEnabled(enabled);
        }

        context.getSource().sendFeedback(() -> Text.literal("[BBS++] 全部调试模块已" + formatEnabled(enabled)), false);

        return MODULES.size();
    }

    private static int showModuleStatus(CommandContext<ServerCommandSource> context, DebugModule module)
    {
        context.getSource().sendFeedback(() -> Text.literal("[BBS++] " + module.label() + "：" + formatStatus(module.enabled())), false);

        return module.enabled() ? 1 : 0;
    }

    private static int showAllStatus(CommandContext<ServerCommandSource> context)
    {
        StringBuilder builder = new StringBuilder("[BBS++] 调试状态");

        for (DebugModule module : MODULES)
        {
            builder.append("\n- ")
                .append(module.label())
                .append("：")
                .append(formatStatus(module.enabled()));
        }

        context.getSource().sendFeedback(() -> Text.literal(builder.toString()), false);

        return MODULES.size();
    }

    private static String formatEnabled(boolean enabled)
    {
        return enabled ? "开启" : "关闭";
    }

    private static String formatStatus(boolean enabled)
    {
        return enabled ? "开启" : "关闭";
    }

    private record DebugModule(String id, String name, BooleanSupplier getter, Consumer<Boolean> setter)
    {
        private boolean enabled()
        {
            return this.getter.getAsBoolean();
        }

        private void setEnabled(boolean enabled)
        {
            this.setter.accept(enabled);
        }

        private String label()
        {
            return this.id + "（" + this.name + "）";
        }
    }
}
