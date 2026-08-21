package gbeic.bbsplusplus.structure;

import mchorse.bbs_mod.BBSMod;
import net.minecraft.block.Blocks;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtIo;
import net.minecraft.structure.StructureTemplate;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.io.File;
import java.io.IOException;
import java.util.Locale;

/**
 * 把世界里的一块区域写成结构文件（{@code .nbt}）。
 * <p>
 * 移植自 BBSTools 4.1 的服务端保存逻辑，抽成了两端通用的工具类：
 * 服务端装了 BBS++ 时由 {@code StructureStickNetworking} 调用，
 * 否则由客户端直接调用（见 {@code gbeic.bbsplusplus.client.structure.StructureStickSelection}）。
 * </p>
 */
public final class StructureSaver
{
    /** 单个结构最多允许的方块数，防止误框一大片地形把内存撑爆 */
    public static final int MAX_VOLUME = 262144;

    /** 结构棒导出的默认子目录 */
    public static final String DEFAULT_FOLDER = "structure_stick/";

    private StructureSaver()
    {}

    /**
     * 保存结果。
     *
     * @param success    是否保存成功
     * @param messageKey 反馈给玩家的语言键
     * @param name       成功时为消毒后的结构名，失败时为空字符串
     */
    public record SaveResult(boolean success, String messageKey, String name)
    {}

    /**
     * 把 {@code from} 与 {@code to} 围成的立方体区域写成结构文件。
     *
     * @param requestedName 玩家填的名字，会自动补上 {@code structure_stick/} 前缀并做路径消毒
     */
    public static SaveResult save(World world, String requestedName, BlockPos from, BlockPos to)
    {
        if (world == null || from == null || to == null)
        {
            return new SaveResult(false, "bbsplusplus.structure_stick.no_selection", "");
        }

        BlockPos min = new BlockPos(
            Math.min(from.getX(), to.getX()),
            Math.min(from.getY(), to.getY()),
            Math.min(from.getZ(), to.getZ())
        );
        BlockPos max = new BlockPos(
            Math.max(from.getX(), to.getX()),
            Math.max(from.getY(), to.getY()),
            Math.max(from.getZ(), to.getZ())
        );
        BlockPos size = max.subtract(min).add(1, 1, 1);
        long volume = (long) size.getX() * size.getY() * size.getZ();

        if (size.getX() <= 0 || size.getY() <= 0 || size.getZ() <= 0 || volume > MAX_VOLUME)
        {
            return new SaveResult(false, "bbsplusplus.structure_stick.too_large", "");
        }

        StructureTemplate template = new StructureTemplate();

        // 用结构空位方块当忽略目标，这样空气会被原样记录，不会在放置时留下空洞
        template.saveFromWorld(world, min, size, true, Blocks.STRUCTURE_VOID);

        String safeName = sanitizeName(requestedName);
        File file = new File(BBSMod.getAssetsPath("structures"), safeName + ".nbt");

        if (file.getParentFile() != null)
        {
            file.getParentFile().mkdirs();
        }

        try
        {
            NbtCompound nbt = template.writeNbt(new NbtCompound());

            NbtIo.writeCompressed(nbt, file);

            return new SaveResult(true, "bbsplusplus.structure_stick.saved", safeName);
        }
        catch (IOException e)
        {
            return new SaveResult(false, "bbsplusplus.structure_stick.save_failed", "");
        }
    }

    /** 补上默认子目录前缀 */
    public static String withDefaultFolder(String name)
    {
        return name.startsWith(DEFAULT_FOLDER) ? name : DEFAULT_FOLDER + name;
    }

    /**
     * 清理玩家填的名字，确保它只会落在 {@code assets/structures} 目录内部。
     * <p>
     * 统一转小写、把非法字符换成下划线、消掉 {@code ..} 与开头的斜杠，避免写到资源目录之外。
     * </p>
     */
    public static String sanitizeName(String name)
    {
        if (name == null || name.isBlank())
        {
            name = DEFAULT_FOLDER + "structure";
        }

        name = name.replace('\\', '/').toLowerCase(Locale.ROOT);
        name = name.replaceAll("[^a-z0-9_./-]", "_");

        while (name.contains(".."))
        {
            name = name.replace("..", ".");
        }

        while (name.startsWith("/"))
        {
            name = name.substring(1);
        }

        if (name.endsWith(".nbt"))
        {
            name = name.substring(0, name.length() - 4);
        }

        return name.isBlank() ? DEFAULT_FOLDER + "structure" : name;
    }
}
