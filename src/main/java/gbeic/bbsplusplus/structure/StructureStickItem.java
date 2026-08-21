package gbeic.bbsplusplus.structure;

import net.minecraft.item.Item;

/**
 * 结构棒物品本体。
 * <p>
 * 移植自 BBSTools 4.1。物品本身没有任何服务端行为，所有交互都在客户端完成
 * （见 {@code gbeic.bbsplusplus.client.structure.StructureStickSelection}），
 * 这里只是提供一个可以拿在手里的载体，因此限制为不可堆叠。
 * </p>
 */
public class StructureStickItem extends Item
{
    public StructureStickItem(Item.Settings settings)
    {
        super(settings.maxCount(1));
    }
}
