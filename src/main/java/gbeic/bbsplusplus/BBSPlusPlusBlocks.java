package gbeic.bbsplusplus;

import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.fabricmc.fabric.api.object.builder.v1.block.FabricBlockSettings;
import net.minecraft.block.Block;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;

/**
 * BBSPlusPlusBlocks
 *
 * 定义了 BBS++ 模组中的方块和对应的物品。主要包括 8 个发光的色彩方块（红、绿、蓝、青、品红、黄、黑、白），这些方块具有特殊的属性，如无法被破坏、不会掉落物品，并且始终发光。所有方块和物品都注册在 bbs 命名空间下，以便与 BBS 的资源系统兼容。
 */

public class BBSPlusPlusBlocks
{
    private static Block createEmissiveChromaBlock()
    {
        return new Block(FabricBlockSettings.create()
            .noBlockBreakParticles()
            .dropsNothing()
            .requiresTool()
            .strength(-1F, 3600000F)
            .emissiveLighting((state, block, world) -> true));
    }

    public static final Block EMISSIVE_CHROMA_RED_BLOCK = createEmissiveChromaBlock();
    public static final Block EMISSIVE_CHROMA_GREEN_BLOCK = createEmissiveChromaBlock();
    public static final Block EMISSIVE_CHROMA_BLUE_BLOCK = createEmissiveChromaBlock();
    public static final Block EMISSIVE_CHROMA_CYAN_BLOCK = createEmissiveChromaBlock();
    public static final Block EMISSIVE_CHROMA_MAGENTA_BLOCK = createEmissiveChromaBlock();
    public static final Block EMISSIVE_CHROMA_YELLOW_BLOCK = createEmissiveChromaBlock();
    public static final Block EMISSIVE_CHROMA_BLACK_BLOCK = createEmissiveChromaBlock();
    public static final Block EMISSIVE_CHROMA_WHITE_BLOCK = createEmissiveChromaBlock();

    public static final BlockItem EMISSIVE_CHROMA_RED_BLOCK_ITEM = new BlockItem(EMISSIVE_CHROMA_RED_BLOCK, new Item.Settings());
    public static final BlockItem EMISSIVE_CHROMA_GREEN_BLOCK_ITEM = new BlockItem(EMISSIVE_CHROMA_GREEN_BLOCK, new Item.Settings());
    public static final BlockItem EMISSIVE_CHROMA_BLUE_BLOCK_ITEM = new BlockItem(EMISSIVE_CHROMA_BLUE_BLOCK, new Item.Settings());
    public static final BlockItem EMISSIVE_CHROMA_CYAN_BLOCK_ITEM = new BlockItem(EMISSIVE_CHROMA_CYAN_BLOCK, new Item.Settings());
    public static final BlockItem EMISSIVE_CHROMA_MAGENTA_BLOCK_ITEM = new BlockItem(EMISSIVE_CHROMA_MAGENTA_BLOCK, new Item.Settings());
    public static final BlockItem EMISSIVE_CHROMA_YELLOW_BLOCK_ITEM = new BlockItem(EMISSIVE_CHROMA_YELLOW_BLOCK, new Item.Settings());
    public static final BlockItem EMISSIVE_CHROMA_BLACK_BLOCK_ITEM = new BlockItem(EMISSIVE_CHROMA_BLACK_BLOCK, new Item.Settings());
    public static final BlockItem EMISSIVE_CHROMA_WHITE_BLOCK_ITEM = new BlockItem(EMISSIVE_CHROMA_WHITE_BLOCK, new Item.Settings());

    public static void register()
    {
        // 注册到 bbs 命名空间，以便直接复用模型文件和语言键
        String ns = "bbs";
        
        Registry.register(Registries.BLOCK, new Identifier(ns, "emissive_chroma_red"), EMISSIVE_CHROMA_RED_BLOCK);
        Registry.register(Registries.BLOCK, new Identifier(ns, "emissive_chroma_green"), EMISSIVE_CHROMA_GREEN_BLOCK);
        Registry.register(Registries.BLOCK, new Identifier(ns, "emissive_chroma_blue"), EMISSIVE_CHROMA_BLUE_BLOCK);
        Registry.register(Registries.BLOCK, new Identifier(ns, "emissive_chroma_cyan"), EMISSIVE_CHROMA_CYAN_BLOCK);
        Registry.register(Registries.BLOCK, new Identifier(ns, "emissive_chroma_magenta"), EMISSIVE_CHROMA_MAGENTA_BLOCK);
        Registry.register(Registries.BLOCK, new Identifier(ns, "emissive_chroma_yellow"), EMISSIVE_CHROMA_YELLOW_BLOCK);
        Registry.register(Registries.BLOCK, new Identifier(ns, "emissive_chroma_black"), EMISSIVE_CHROMA_BLACK_BLOCK);
        Registry.register(Registries.BLOCK, new Identifier(ns, "emissive_chroma_white"), EMISSIVE_CHROMA_WHITE_BLOCK);

        Registry.register(Registries.ITEM, new Identifier(ns, "emissive_chroma_red"), EMISSIVE_CHROMA_RED_BLOCK_ITEM);
        Registry.register(Registries.ITEM, new Identifier(ns, "emissive_chroma_green"), EMISSIVE_CHROMA_GREEN_BLOCK_ITEM);
        Registry.register(Registries.ITEM, new Identifier(ns, "emissive_chroma_blue"), EMISSIVE_CHROMA_BLUE_BLOCK_ITEM);
        Registry.register(Registries.ITEM, new Identifier(ns, "emissive_chroma_cyan"), EMISSIVE_CHROMA_CYAN_BLOCK_ITEM);
        Registry.register(Registries.ITEM, new Identifier(ns, "emissive_chroma_magenta"), EMISSIVE_CHROMA_MAGENTA_BLOCK_ITEM);
        Registry.register(Registries.ITEM, new Identifier(ns, "emissive_chroma_yellow"), EMISSIVE_CHROMA_YELLOW_BLOCK_ITEM);
        Registry.register(Registries.ITEM, new Identifier(ns, "emissive_chroma_black"), EMISSIVE_CHROMA_BLACK_BLOCK_ITEM);
        Registry.register(Registries.ITEM, new Identifier(ns, "emissive_chroma_white"), EMISSIVE_CHROMA_WHITE_BLOCK_ITEM);

        // 添加到 bbs 的创造模式物品栏中
        ItemGroupEvents.modifyEntriesEvent(RegistryKey.of(RegistryKeys.ITEM_GROUP, new Identifier("bbs", "main"))).register(content -> {
            content.add(EMISSIVE_CHROMA_RED_BLOCK_ITEM);
            content.add(EMISSIVE_CHROMA_GREEN_BLOCK_ITEM);
            content.add(EMISSIVE_CHROMA_BLUE_BLOCK_ITEM);
            content.add(EMISSIVE_CHROMA_CYAN_BLOCK_ITEM);
            content.add(EMISSIVE_CHROMA_MAGENTA_BLOCK_ITEM);
            content.add(EMISSIVE_CHROMA_YELLOW_BLOCK_ITEM);
            content.add(EMISSIVE_CHROMA_BLACK_BLOCK_ITEM);
            content.add(EMISSIVE_CHROMA_WHITE_BLOCK_ITEM);
        });
    }
}
