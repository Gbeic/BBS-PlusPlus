package gbeic.bbsplusplus.mixin;

import net.minecraft.client.Keyboard;
import net.minecraft.client.option.SimpleOption;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 屏蔽原版 {@code Ctrl+B} 复述功能快捷键。
 *
 * <p>原版在 {@link Keyboard#onKey(long, int, int, int, int)} 中直接检测 {@code Ctrl+B}，
 * 并通过修改复述选项来循环切换复述模式。这个快捷键很容易误触，且不属于可正常重绑的
 * BBS 按键体系，因此只拦截这一次选项写入，让按键事件继续交给后续界面逻辑处理。</p>
 */
@Mixin(Keyboard.class)
public class KeyboardNarratorShortcutMixin
{
    /**
     * 注入目标：{@link Keyboard#onKey(long, int, int, int, int)} 中原版复述快捷键调用
     * {@link SimpleOption#setValue(Object)} 的位置。
     *
     * <p>为什么要注入：原版 {@code Ctrl+B} 会直接切换 Narrator/复述模式，用户误触后会被
     * 突然弹出的复述提示打断。修改后的行为是吞掉这次复述选项写入，但不取消整个按键事件，
     * 这样其它界面仍然可以正常响应自己的 {@code Ctrl+B}。</p>
     */
    @Redirect(
        method = "onKey",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/option/SimpleOption;setValue(Ljava/lang/Object;)V",
            ordinal = 1
        )
    )
    private void bbspp$disableNarratorShortcut(SimpleOption<?> option, Object value)
    {
        // 原版第二次 setValue 是 Ctrl+B 修改复述模式；这里故意不写入，等同于禁用该快捷键。
    }
}
