package gbeic.bbsplusplus.mixin;

import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.utils.UIChalkboard;
import mchorse.bbs_mod.ui.utils.keys.KeyCombo;
import mchorse.bbs_mod.ui.utils.keys.Keybind;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 屏蔽 BBS 屏幕绘画（黑板）功能的 F10 快捷键。
 *
 * 用途：底层 UIChalkboard 在构造函数中注册了 F10 键用于切换黑板绘画状态，
 * 该功能对玩家没有实际用处，因此需要在 BBS++ 中将其彻底屏蔽。
 *
 * 实现思路：在 UIChalkboard 构造完成后（F10 绑定已注册），遍历其 KeybindManager
 * 中注册的所有快捷键，通过 Keybind.equals（仅比较键序列与 inside 标志）精确匹配
 * 到 F10 对应的 Keybind，并将其回调置为 null。由于 KeybindManager.checkKeybinds
 * 只在 callback 不为 null 时触发按键，置空后 F10 将永远无法再开启黑板功能。
 */
@Mixin(value = UIChalkboard.class, remap = false)
public class UIChalkboardMixin
{
    @Inject(method = "<init>", at = @At("TAIL"))
    private void bbspp$disableChalkboardKeybind(CallbackInfo ci)
    {
        // 构造一个与 F10 绑定等价的 Keybind，用于在列表中查找目标绑定
        Keybind f10Bind = new Keybind(new KeyCombo(IKey.EMPTY, GLFW.GLFW_KEY_F10), () -> {});

        for (Keybind keybind : ((UIElement) (Object) this).keys().keybinds)
        {
            // 匹配到 F10 的绑定后置空回调，使其在按键系统中永久失效
            if (keybind.equals(f10Bind))
            {
                keybind.callback = null;
            }
        }
    }
}
