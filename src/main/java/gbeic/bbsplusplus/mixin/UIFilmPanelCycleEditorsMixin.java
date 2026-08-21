package gbeic.bbsplusplus.mixin;

import gbeic.bbsplusplus.BBSAddonsSettings;
import mchorse.bbs_mod.ui.Keys;
import mchorse.bbs_mod.ui.dashboard.UIDashboard;
import mchorse.bbs_mod.ui.film.UIFilmPanel;
import mchorse.bbs_mod.ui.utils.UIUtils;
import mchorse.bbs_mod.ui.utils.keys.Keybind;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.Supplier;

/**
 * 让波浪号（{@code ~}）循环切换编辑器时跳过动作剪辑时间轴。
 * <p>
 * 原版 {@code UIFilmPanel} 把三个面板放进同一个列表按顺序轮换：相机编辑器、回放编辑器、
 * 动作剪辑时间轴。对于只用前两者的工作流，每次循环都要多按一次才能回到相机编辑器。
 * BBS 早期版本的动作时间轴在别处，这个问题曾一度消失，FSR 又把它放回了右上角，所以这里恢复该功能。
 * </p>
 * <p>
 * <b>实现方式</b>：不直接拦截 {@code showPanel}——那个方法也服务于点击图标切换面板，
 * 拦截它会让用户无法再进入动作剪辑时间轴。这里改为在构造完成后接管快捷键本身：
 * 给原版那条 keybind 挂上「本功能关闭时才生效」的条件，再注册一条只在开启时生效的替代实现。
 * 之所以要禁用原版而非仅仅追加，是因为 {@code KeybindManager} 在同分数的候选里取先注册的那条
 * （见其 {@code checkKeybinds} 中 {@code keybindScore > score} 的严格比较），后注册无法覆盖。
 * </p>
 */
@Mixin(value = UIFilmPanel.class, remap = false)
public abstract class UIFilmPanelCycleEditorsMixin
{
    /** panels 列表中相机编辑器与回放编辑器的下标，与原版添加顺序一致。 */
    @Unique
    private static final int bbspp$CAMERA_PANEL = 0;

    @Unique
    private static final int bbspp$REPLAY_PANEL = 1;

    /**
     * 注入目标：{@link UIFilmPanel} 构造方法末尾。
     * 注入原因：此时原版已经注册完所有快捷键，可以安全地调整那条循环切换的 keybind。
     * 修改行为：按开关状态在原版实现与跳过动作时间轴的实现之间二选一。
     */
    @Inject(method = "<init>(Lmchorse/bbs_mod/ui/dashboard/UIDashboard;)V", at = @At("RETURN"))
    private void bbspp$replaceCycleEditorsKeybind(UIDashboard dashboard, CallbackInfo ci)
    {
        UIFilmPanel self = (UIFilmPanel) (Object) this;
        Keybind original = null;

        for (Keybind keybind : self.keys().keybinds)
        {
            if (Keys.FILM_CONTROLLER_CYCLE_EDITORS.getKeyCombo().equals(keybind.getKeyCombo()))
            {
                original = keybind;

                break;
            }
        }

        if (original == null)
        {
            /* 原版改了快捷键的注册方式时保持原状，不影响其它功能。 */
            return;
        }

        Supplier<Boolean> previous = original.active;

        original.active(() -> !bbspp$isEnabled() && (previous == null || Boolean.TRUE.equals(previous.get())));

        self.keys().register(Keys.FILM_CONTROLLER_CYCLE_EDITORS, () ->
        {
            /* 停在动作剪辑时间轴上时（例如刚用图标切进去）按下快捷键，回到相机编辑器。 */
            int next = self.getPanelIndex() == bbspp$CAMERA_PANEL ? bbspp$REPLAY_PANEL : bbspp$CAMERA_PANEL;

            self.showPanel(next);
            UIUtils.playClick();
        }).active(UIFilmPanelCycleEditorsMixin::bbspp$isEnabled).category(original.getCategory());
    }

    @Unique
    private static boolean bbspp$isEnabled()
    {
        return BBSAddonsSettings.cycleCompact != null
            && BBSAddonsSettings.cycleCompact.get();
    }
}
