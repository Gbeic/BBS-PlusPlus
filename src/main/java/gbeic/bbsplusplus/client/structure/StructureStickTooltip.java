package gbeic.bbsplusplus.client.structure;

import gbeic.bbsplusplus.structure.StructureStickRegistry;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/**
 * 在结构棒的物品提示里列出操作方式。
 * <p>
 * 移植自 BBSTools 4.1。按键名取自玩家当前的实际绑定，改过键位也能对得上。
 * </p>
 */
public final class StructureStickTooltip
{
    private StructureStickTooltip()
    {}

    public static void register()
    {
        ItemTooltipCallback.EVENT.register((stack, context, lines) ->
        {
            if (stack.getItem() != StructureStickRegistry.STRUCTURE_STICK)
            {
                return;
            }

            MinecraftClient client = MinecraftClient.getInstance();

            lines.add(singleKey(client.options.useKey, "bbsplusplus.structure_stick.tip.drag"));
            lines.add(comboKey(client.options.sneakKey, client.options.useKey, "bbsplusplus.structure_stick.tip.export"));
            lines.add(singleKey(client.options.attackKey, "bbsplusplus.structure_stick.tip.clear"));
        });
    }

    /** 「长按 {键} 说明」形式的一行提示 */
    private static Text singleKey(KeyBinding key, String descriptionKey)
    {
        MutableText text = Text.translatable("bbsplusplus.structure_stick.tip.hold").formatted(Formatting.GRAY);

        text.append(boundKey(key));
        text.append(Text.translatable(descriptionKey).formatted(Formatting.GRAY));

        return text;
    }

    /** 「{键}+{键} 说明」形式的一行提示 */
    private static Text comboKey(KeyBinding first, KeyBinding second, String descriptionKey)
    {
        MutableText text = Text.empty();

        text.append(boundKey(first));
        text.append(Text.literal("+").formatted(Formatting.GRAY));
        text.append(boundKey(second));
        text.append(Text.translatable(descriptionKey).formatted(Formatting.GRAY));

        return text;
    }

    private static Text boundKey(KeyBinding key)
    {
        return Text.literal("{").formatted(Formatting.GRAY)
            .append(key.getBoundKeyLocalizedText().copy().formatted(Formatting.YELLOW))
            .append(Text.literal("}").formatted(Formatting.GRAY));
    }
}
