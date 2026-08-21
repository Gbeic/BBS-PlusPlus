package gbeic.bbsplusplus;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.ArrayList;
import java.util.List;

/**
 * BBSPlusPlusBlocks
 *
 * 定义了 BBS++ 模组中的方块和对应的物品。主要包括 8 个发光的色彩方块（红、绿、蓝、青、品红、黄、黑、白），这些方块具有特殊的属性，如无法被破坏、不会掉落物品，并且始终发光。所有方块和物品都注册在 bbs 命名空间下，以便与 BBS 的资源系统兼容。
 *
 * <p>
 * NeoForge 不允许像 Fabric 那样在任意时机直接调用 {@code Registry.register}，注册表在
 * 特定阶段就会冻结，因此这里改用 {@link DeferredRegister} 延迟注册：先声明条目，再由
 * 模组事件总线在正确的时机统一写入注册表。
 * </p>
 */
public class BBSPlusPlusBlocks
{
    /** 注册到 bbs 命名空间，以便直接复用模型文件和语言键 */
    private static final String NAMESPACE = "bbs";

    /** BBS 主创造模式物品栏，色彩方块会被追加到这里 */
    private static final ResourceKey<CreativeModeTab> BBS_MAIN_TAB =
        ResourceKey.create(Registries.CREATIVE_MODE_TAB, ResourceLocation.fromNamespaceAndPath(NAMESPACE, "main"));

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(NAMESPACE);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(NAMESPACE);

    /** 按注册顺序记录所有色彩方块物品，供创造模式物品栏填充使用 */
    private static final List<DeferredItem<BlockItem>> CHROMA_ITEMS = new ArrayList<>();

    public static final DeferredBlock<Block> EMISSIVE_CHROMA_RED_BLOCK = registerChroma("emissive_chroma_red");
    public static final DeferredBlock<Block> EMISSIVE_CHROMA_GREEN_BLOCK = registerChroma("emissive_chroma_green");
    public static final DeferredBlock<Block> EMISSIVE_CHROMA_BLUE_BLOCK = registerChroma("emissive_chroma_blue");
    public static final DeferredBlock<Block> EMISSIVE_CHROMA_CYAN_BLOCK = registerChroma("emissive_chroma_cyan");
    public static final DeferredBlock<Block> EMISSIVE_CHROMA_MAGENTA_BLOCK = registerChroma("emissive_chroma_magenta");
    public static final DeferredBlock<Block> EMISSIVE_CHROMA_YELLOW_BLOCK = registerChroma("emissive_chroma_yellow");
    public static final DeferredBlock<Block> EMISSIVE_CHROMA_BLACK_BLOCK = registerChroma("emissive_chroma_black");
    public static final DeferredBlock<Block> EMISSIVE_CHROMA_WHITE_BLOCK = registerChroma("emissive_chroma_white");

    /**
     * 声明一个发光色彩方块及其对应的方块物品。
     *
     * @param name 注册名，方块与物品共用
     */
    private static DeferredBlock<Block> registerChroma(String name)
    {
        DeferredBlock<Block> block = BLOCKS.register(name, () -> new Block(createEmissiveChromaProperties()));

        CHROMA_ITEMS.add(ITEMS.register(name, () -> new BlockItem(block.get(), new Item.Properties())));

        return block;
    }

    /**
     * 发光色彩方块的共同属性：不可破坏、无掉落、无破坏粒子，且渲染时始终自发光。
     */
    private static BlockBehaviour.Properties createEmissiveChromaProperties()
    {
        return BlockBehaviour.Properties.of()
            .noTerrainParticles()
            .noLootTable()
            .requiresCorrectToolForDrops()
            .strength(-1F, 3600000F)
            .emissiveRendering((state, level, pos) -> true);
    }

    /**
     * 把两个 DeferredRegister 挂到模组事件总线，并监听创造模式物品栏的填充事件。
     *
     * @param modBus 模组事件总线，由 {@link BBSPlusPlusMod} 的构造器传入
     */
    public static void register(IEventBus modBus)
    {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);

        modBus.addListener(BBSPlusPlusBlocks::onBuildCreativeTabContents);
    }

    /**
     * 添加到 bbs 的创造模式物品栏中。
     * <p>
     * Fabric 的 {@code ItemGroupEvents.modifyEntriesEvent} 在 NeoForge 上对应
     * {@link BuildCreativeModeTabContentsEvent}，该事件对每个标签页各触发一次，
     * 因此必须自行比对标签页的注册键。
     * </p>
     */
    private static void onBuildCreativeTabContents(BuildCreativeModeTabContentsEvent event)
    {
        if (!BBS_MAIN_TAB.equals(event.getTabKey()))
        {
            return;
        }

        for (DeferredItem<BlockItem> item : CHROMA_ITEMS)
        {
            event.accept(item.get());
        }
    }
}
