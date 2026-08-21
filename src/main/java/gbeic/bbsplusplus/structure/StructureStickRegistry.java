package gbeic.bbsplusplus.structure;

import mchorse.bbs_mod.BBSMod;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;

/**
 * 结构棒的物品与音效注册。
 * <p>
 * 移植自 BBSTools 4.1。物品沿用 BBS++ 现有做法注册在 {@code bbs} 命名空间下，
 * 这样模型、贴图和物品名可以直接放进已有的 {@code assets/bbs} 资源目录；
 * 音效则必须留在 {@code bbsplusplus} 命名空间，否则 {@code sounds.json} 会整份顶掉 BBS 本体的音效表。
 * </p>
 */
public final class StructureStickRegistry
{
    /** BBS 的创造模式物品栏 */
    private static final RegistryKey<ItemGroup> BBS_ITEM_GROUP =
        RegistryKey.of(RegistryKeys.ITEM_GROUP, new Identifier("bbs", "main"));

    public static final StructureStickItem STRUCTURE_STICK = new StructureStickItem(new Item.Settings());

    public static final Identifier STRUCTURE_STICK_DRAG_ID = new Identifier("bbspp", "structure_stick_drag");
    public static final Identifier STRUCTURE_STICK_EXPORT_ID = new Identifier("bbspp", "structure_stick_export");

    /** 拖拽选区时的提示音，音高随选区体积变化 */
    public static final SoundEvent STRUCTURE_STICK_DRAG = SoundEvent.of(STRUCTURE_STICK_DRAG_ID);

    /** 导出结构成功时的提示音 */
    public static final SoundEvent STRUCTURE_STICK_EXPORT = SoundEvent.of(STRUCTURE_STICK_EXPORT_ID);

    private static boolean assetsFolderPrepared;

    private StructureStickRegistry()
    {}

    public static void register()
    {
        Registry.register(Registries.ITEM, new Identifier("bbs", "structure_stick"), STRUCTURE_STICK);
        Registry.register(Registries.SOUND_EVENT, STRUCTURE_STICK_DRAG_ID, STRUCTURE_STICK_DRAG);
        Registry.register(Registries.SOUND_EVENT, STRUCTURE_STICK_EXPORT_ID, STRUCTURE_STICK_EXPORT);

        ItemGroupEvents.modifyEntriesEvent(BBS_ITEM_GROUP).register((entries) -> entries.add(new ItemStack(STRUCTURE_STICK)));
    }

    /**
     * 确保 BBS 资源目录下的 {@code structures} 文件夹存在。
     * <p>
     * 需要等 BBS 初始化完资源路径之后才能调用，所以单独拆出来由表单注册时机触发。
     * </p>
     */
    public static void prepareAssetsFolder()
    {
        if (assetsFolderPrepared)
        {
            return;
        }

        assetsFolderPrepared = true;

        try
        {
            BBSMod.getAssetsPath("structures").mkdirs();
        }
        catch (Exception ignored)
        {}
    }
}
