package gbeic.bbsplusplus.command.modules;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.server.command.ServerCommandSource;

/**
 * BBS++ 服务端指令模块接口。
 * <p>
 * 每个模块负责创建 {@code /bbsplusplus <模块>} 这一层及其子命令。根命令只收集模块，
 * 不关心模块内部参数和执行逻辑，从而让后续新增模块时有统一落点。
 * </p>
 */
public interface BBSPlusPlusCommandModule
{
    LiteralArgumentBuilder<ServerCommandSource> create();
}
