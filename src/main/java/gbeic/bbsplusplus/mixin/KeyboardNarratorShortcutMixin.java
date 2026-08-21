package gbeic.bbsplusplus.mixin;

import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.OptionInstance;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 屏蔽原版 {@code Ctrl+B} 复述功能快捷键。
 *
 * <p>原版在 {@link KeyboardHandler#keyPress(long, int, int, int, int)} 中直接检测 {@code Ctrl+B}，
 * 并通过修改复述选项来循环切换复述模式。这个快捷键很容易误触，且不属于可正常重绑的
 * BBS 按键体系，因此只拦截这一次选项写入，让按键事件继续交给后续界面逻辑处理。</p>
 */
@Mixin(KeyboardHandler.class)
public class KeyboardNarratorShortcutMixin
{
    /**
     * 注入目标：{@link KeyboardHandler#keyPress(long, int, int, int, int)} 中原版复述快捷键调用
     * {@link OptionInstance#set(Object)} 的位置。
     *
     * <p>为什么要注入：原版 {@code Ctrl+B} 会直接切换 Narrator/复述模式，用户误触后会被
     * 突然弹出的复述提示打断。修改后的行为是吞掉这次复述选项写入，但不取消整个按键事件，
     * 这样其它界面仍然可以正常响应自己的 {@code Ctrl+B}。</p>
     *
     * <p>1.21.1 中 {@code keyPress} 里共有两处 {@code OptionInstance.set}：
     * ordinal 0 是 F11 全屏开关的写回，ordinal 1 才是 {@code Ctrl+B} 的复述模式切换。</p>
     */
    @Redirect(
        method = "keyPress",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/OptionInstance;set(Ljava/lang/Object;)V",
            ordinal = 1
        )
    )
    private void bbspp$disableNarratorShortcut(OptionInstance<?> option, Object value)
    {
        // 原版第二次 set 是 Ctrl+B 修改复述模式；这里故意不写入，等同于禁用该快捷键。
    }
}
