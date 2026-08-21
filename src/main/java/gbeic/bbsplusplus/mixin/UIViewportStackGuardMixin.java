package gbeic.bbsplusplus.mixin;

import mchorse.bbs_mod.ui.framework.elements.utils.UIViewportStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Deque;

import mchorse.bbs_mod.ui.utils.Area;

/**
 * 防止空视口栈出栈时抛异常拖垮整个 BBS 界面。
 *
 * <p>
 * {@code UIContext.setMouse()} 每次都会 {@code viewportStack.reset()} 清空栈，而
 * {@code UIBaseMenu.mouseClicked()} 的结构是「setMouse（清空）→ pushViewport → 分发 →
 * popViewport」，并且不像同类的 {@code mouseCanceled()} 那样用 try-finally 保护。因此只要
 * 事件分发过程中再次触发任何一次 {@code setMouse}（即输入事件重入），栈就会被提前清空，
 * 随后外层那次 {@code popViewport()} 落在空栈上，{@code ArrayDeque.pop()} 抛出
 * {@link java.util.NoSuchElementException}。
 * </p>
 * <p>
 * 实测触发路径：BBS++ 的光影控制按钮打开 Iris 的 ShaderPackScreen，按 Esc 返回 BBS 界面后
 * 点击任意位置即崩溃。FSR 新增的 UI 镜像与输入邮箱机制（{@code dispatchRemoteMouseClicked}、
 * {@code BBSUiInputDispatcher}）会重放输入事件，正是这里的重入来源——1.20.1 的 BBS 没有这套
 * 机制，所以旧版不会出现。
 * </p>
 * <p>
 * 修改行为：出栈前先判断栈是否为空，空栈直接取消本次出栈。空栈本来就没有可恢复的视口，
 * 抛异常只会中断事件分发或渲染、并把整个界面带崩；跳过它可以让界面在状态短暂失衡后继续工作。
 * 这是对上游缺陷的防御性兜底，BBS 侧的正解是给 {@code mouseClicked} 等方法补 try-finally。
 * </p>
 */
@Mixin(value = UIViewportStack.class, remap = false)
public class UIViewportStackGuardMixin
{
    @Shadow
    private Deque<Area> viewportStack;

    /**
     * 注入目标：{@link UIViewportStack#popViewport()} 开头。
     * 注入原因：空栈出栈会抛 NoSuchElementException，导致 BBS 界面崩溃退出。
     * 修改行为：栈为空时直接返回，不执行原版的 {@code pop()}。
     */
    @Inject(method = "popViewport", at = @At("HEAD"), cancellable = true)
    private void bbspp$skipPopOnEmptyStack(CallbackInfo ci)
    {
        if (this.viewportStack == null || this.viewportStack.isEmpty())
        {
            ci.cancel();
        }
    }
}
