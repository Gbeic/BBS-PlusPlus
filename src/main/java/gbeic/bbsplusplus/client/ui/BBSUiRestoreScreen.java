package gbeic.bbsplusplus.client.ui;

import mchorse.bbs_mod.ui.framework.UIBaseMenu;
import mchorse.bbs_mod.ui.framework.UIScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * 用于从外部界面（如 Iris 光影设置）安全返回 BBS 界面的中转界面。
 *
 * <p>
 * <b>为什么需要它</b>：BBS 的 {@link UIScreen} 是一次性的。它的 {@code removed()} 会把
 * {@code removed} 标记置为 true 且<b>永不复位</b>，同时执行一整套拆解——
 * {@code BBSUiInputDispatcher.detach}、{@code menu.onClose(null)}、
 * {@code invalidateInputState}、{@code BBSUiFrameRecorder.closeSession} 等。而它所有的输入
 * 分发入口都带 {@code if (removing || removed) return true} 守卫。
 * </p>
 * <p>
 * 因此一旦把当前 UIScreen 实例当作外部界面的 parent，MC 在切换时会先 {@code removed()} 掉它，
 * 之后按 Esc 返回的就是这个已经被拆解的实例：输入全部被吞掉、菜单也已关闭，表现为黑屏且
 * 完全无响应，只能强制结束进程。
 * </p>
 * <p>
 * <b>做法</b>：把本界面作为 parent，它在显示时立刻通过 BBS 的正规入口
 * {@link UIScreen#open(UIBaseMenu)} 重新打开——该方法每次都会新建 UIScreen 实例并复用同一个
 * menu，正是 BBS 自己反复打开面板时用的方式（见 {@code BBSModClient} 中按键打开 dashboard 的
 * 逻辑），所以复用被 {@code onClose} 过的 menu 是安全的。
 * </p>
 */
public class BBSUiRestoreScreen extends Screen
{
    /** 待恢复的 BBS 菜单，来自打开外部界面之前的 {@link UIScreen#getCurrentMenu()}。 */
    private final UIBaseMenu menu;

    /** 防止重复触发：init 在窗口缩放等情况下会被多次调用。 */
    private boolean restoring;

    public BBSUiRestoreScreen(UIBaseMenu menu)
    {
        super(Component.empty());

        this.menu = menu;
    }

    @Override
    protected void init()
    {
        super.init();

        if (this.restoring || this.menu == null)
        {
            return;
        }

        this.restoring = true;

        /* 延迟一帧再切换：此刻仍处在 Minecraft.setScreen() 的调用栈内，
         * 直接再次 setScreen 会造成嵌套切换，外层流程会继续操作已被替换掉的界面。 */
        Minecraft.getInstance().execute(() -> UIScreen.open(this.menu));
    }

    /**
     * 不绘制原版的半透明黑色遮罩。本界面只存在一帧，画背景只会造成一次可见的闪黑。
     */
    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick)
    {}

    /**
     * 始终允许 Esc 关闭。
     * <p>
     * 正常情况下恢复任务会在下一帧把界面换成新的 UIScreen，Esc 根本没机会作用到本界面；
     * 但万一那个任务没能执行，保留 Esc 至少能让玩家退回游戏，而不是卡在一个空白界面里。
     * </p>
     */
    @Override
    public boolean shouldCloseOnEsc()
    {
        return true;
    }
}
