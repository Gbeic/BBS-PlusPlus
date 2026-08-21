package gbeic.bbsplusplus.mixin;

import mchorse.bbs_mod.ui.film.IUIClipsDelegate;
import mchorse.bbs_mod.ui.film.UIClips;
import mchorse.bbs_mod.ui.film.UIClipsPanel;
import mchorse.bbs_mod.ui.film.clips.UIClip;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.utils.Scale;
import mchorse.bbs_mod.utils.clips.Clip;
import mchorse.bbs_mod.utils.clips.Clips;
import gbeic.bbsplusplus.util.DoubleClickHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Mixin — 在 {@link UIClips} 时间轴中添加双击剪辑检测。
 * <p>
 * 通过 {@link DoubleClickHelper} 触发剪辑编辑面板的编辑按钮，
 * 从而快速打开对应的关键帧编辑器。
 * </p>
 */
@Mixin(UIClips.class)
public abstract class UIClipsDoubleClickMixin
{
    @Shadow(remap = false)
    private IUIClipsDelegate delegate;

    @Shadow(remap = false)
    private Clips clips;

    @Shadow(remap = false)
    public Scale scale;

    @Shadow(remap = false)
    public abstract int fromLayerY(int mouseY);

    @Shadow(remap = false)
    public abstract void setSelected(Clip clip);

    @Shadow(remap = false)
    public abstract boolean hasEmbeddedView();

    /** 上一次点击的 X 坐标 */
    @Unique
    private int bbs_lastClickX;

    /** 上一次点击的 Y 坐标 */
    @Unique
    private int bbs_lastClickY;

    /** 上一次点击的时间戳（毫秒） */
    @Unique
    private long bbs_lastClickTime;

    /**
     * 在 {@code setMouse} 被调用时记录点击位置和时间。
     */
    @Inject(method = "setMouse", at = @At("TAIL"), remap = false)
    private void onSetMouse(int x, int y, CallbackInfo ci)
    {
        this.bbs_lastClickX = x;
        this.bbs_lastClickY = y;
        this.bbs_lastClickTime = System.currentTimeMillis();
    }

    /**
     * 在 {@code handleLeftClick} 开头检测双击并触发编辑。
     */
    @Inject(
        method = "handleLeftClick",
        at = @At("HEAD"),
        cancellable = true,
        remap = false
    )
    private void onHandleLeftClickHead(UIContext context, int mouseX, int mouseY, boolean ctrl, boolean shift, boolean alt, CallbackInfoReturnable<Boolean> cir)
    {
        if (shift || this.hasEmbeddedView()) return;

        /* 双击检测：同位置 + 500ms 内 */
        if (mouseX != this.bbs_lastClickX || mouseY != this.bbs_lastClickY) return;
        if (System.currentTimeMillis() - this.bbs_lastClickTime >= 500L) return;

        int tick = (int) Math.floor(this.scale.from(mouseX));
        int layerIndex = this.fromLayerY(mouseY);
        Clip clip = this.clips.getClipAt(tick, layerIndex);

        if (clip == null) return;

        /* 选中剪辑 */
        this.delegate.pickClip(clip);
        this.setSelected(clip);

        /* 通过工具类触发编辑器 */
        if (this.delegate instanceof UIClipsPanel)
        {
            UIClipsPanel clipsPanel = (UIClipsPanel) this.delegate;

            /* 通过 Shadow 访问 UIClipsPanel 的私有 panel 字段 */
            UIClip<?> clipPanel = this.bbs_getPanel(clipsPanel);

            if (clipPanel != null)
            {
                DoubleClickHelper.triggerEdit(clipPanel);
            }
        }

        cir.setReturnValue(true);
    }

    /** 通过 Shadow 读取 UIClipsPanel 的 {@code panel} 字段 */
    @Unique
    private UIClip<?> bbs_getPanel(UIClipsPanel panel)
    {
        try
        {
            java.lang.reflect.Field field = UIClipsPanel.class.getDeclaredField("panel");
            field.setAccessible(true);
            return (UIClip<?>) field.get(panel);
        }
        catch (Exception e)
        {
            e.printStackTrace();
            return null;
        }
    }
}
