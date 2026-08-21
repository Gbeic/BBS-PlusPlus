package gbeic.bbsplusplus.client.structure;

import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/**
 * 为 BBS VFX 破坏魔杖补充 BBS++ 接管后的操作提示。
 * <p>
 * 破坏魔杖本体来自 VFX 插件，BBS++ 只在客户端增强交互，因此提示也放在客户端侧注册。
 * 按键名称直接读取玩家当前键位设置，避免用户改键后提示失真。
 * </p>
 */
public final class VFXDestructionWandTooltip
{
    private VFXDestructionWandTooltip()
    {}

    public static void register()
    {
        ItemTooltipCallback.EVENT.register((stack, context, lines) ->
        {
            if (!VFXDestructionWandSelection.isDestructionWand(stack))
            {
                return;
            }

            MinecraftClient client = MinecraftClient.getInstance();

            lines.add(singleKey(client.options.useKey, "bbsplusplus.vfx_wand.tip.drag"));
            lines.add(singleKey(client.options.useKey, "bbsplusplus.vfx_wand.tip.start"));
            lines.add(singleKey(client.options.attackKey, "bbsplusplus.vfx_wand.tip.end"));
            lines.add(singleKey(client.options.attackKey, "bbsplusplus.vfx_wand.tip.clear"));
        });
    }

    /** 「长按 / 短按 {键} 说明」形式的一行提示。 */
    private static Text singleKey(KeyBinding key, String descriptionKey)
    {
        MutableText text = Text.empty();

        text.append(boundKey(key));
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
