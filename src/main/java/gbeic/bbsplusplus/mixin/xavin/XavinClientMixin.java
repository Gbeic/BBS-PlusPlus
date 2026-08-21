package gbeic.bbsplusplus.mixin.xavin;

import gbeic.bbsplusplus.client.structure.VFXDestructionWandSelection;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 接管 BBS VFX 破坏魔杖的原版选区边框显示条件。
 * <p>
 * VFX 自己会在 {@code XavinClient#renderSelection} 中只要选区完整就绘制红色破坏盒，
 * 导致玩家切走魔杖后仍能看到预览边框。这里只在渲染入口做取消处理，
 * 保留 {@code DestructionSelection} 里的选区数据，避免影响“从魔杖选区捕获破坏盒”。
 * </p>
 */
@Mixin(targets = "org.xavin.xavin.client.XavinClient", remap = false)
public class XavinClientMixin
{
    /**
     * 注入目标：{@code XavinClient#renderSelection(WorldRenderContext)} 入口。
     * 注入原因：VFX 原实现不检查玩家是否还手持破坏魔杖，选区完整时会一直显示预览边框。
     * 修改行为：未手持破坏魔杖时取消 VFX 自带边框渲染，选区本身仍保留给后续捕获使用。
     */
    @Inject(method = "renderSelection", at = @At("HEAD"), cancellable = true)
    private void bbspp$hideSelectionWithoutWand(WorldRenderContext context, CallbackInfo ci)
    {
        if (!VFXDestructionWandSelection.shouldRenderSelection(MinecraftClient.getInstance()))
        {
            ci.cancel();
        }
    }
}
