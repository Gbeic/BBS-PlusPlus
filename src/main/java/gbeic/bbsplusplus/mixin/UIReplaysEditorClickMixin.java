package gbeic.bbsplusplus.mixin;

import mchorse.bbs_mod.ui.film.replays.UIReplaysEditor;
import mchorse.bbs_mod.ui.utils.UIUtils;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin — 为 {@link UIReplaysEditor} 的快捷键切换标签页补充点击音效。
 * <p>
 * 鼠标点击标签按钮时 {@link mchorse.bbs_mod.ui.framework.elements.buttons.UIIcon}
 * 自带音效，但快捷键 1/2/3 触发的 {@code setCategory} 没有。
 * 通过检查按键物理状态，仅在快捷键触发时播放音效，不干扰鼠标点击的原有行为。
 * </p>
 */
@Mixin(UIReplaysEditor.class)
public abstract class UIReplaysEditorClickMixin
{
    @Inject(
        method = "setCategory",
        at = @At("TAIL"),
        remap = false
    )
    private void onSetCategory(CallbackInfo ci)
    {
        long window = Minecraft.getInstance().getWindow().getWindow();

        /* 仅在快捷键 1/2/3 触发时播放音效，鼠标点击不自定播放（UIIcon 自带了） */
        if (GLFW.glfwGetKey(window, GLFW.GLFW_KEY_1) == GLFW.GLFW_PRESS
            || GLFW.glfwGetKey(window, GLFW.GLFW_KEY_2) == GLFW.GLFW_PRESS
            || GLFW.glfwGetKey(window, GLFW.GLFW_KEY_3) == GLFW.GLFW_PRESS)
        {
            UIUtils.playClick();
        }
    }
}
