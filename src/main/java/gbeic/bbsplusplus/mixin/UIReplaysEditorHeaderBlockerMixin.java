package gbeic.bbsplusplus.mixin;

import mchorse.bbs_mod.graphics.window.Window;
import mchorse.bbs_mod.ui.film.replays.UIReplaysEditor;
import mchorse.bbs_mod.ui.film.utils.keyframes.UIFilmKeyframes;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.UIContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = UIReplaysEditor.class, remap = false)
public class UIReplaysEditorHeaderBlockerMixin
{
    @Unique
    private UIElement bbspp$clickBlocker;

    @Unique
    private boolean bbspp$scrubbingRuler;

    /**
     * 注入目标：{@link UIReplaysEditor} 构造方法末尾。
     * 注入原因：关键帧时间轴的标尺只是绘制层，原版命中测试仍会选中其下方被遮挡的关键帧。
     * 修改行为：在关键帧模式下拦截标尺区域的关键帧操作，但将无修饰键左键转换为播放头拖动；
     * 剪辑模式继续交给 {@code UIClips} 自身处理，避免透明遮挡层吞掉它的原版播放头事件。
     */
    @Inject(method = "<init>", at = @At("TAIL"))
    private void bbspp$initBlocker(CallbackInfo ci)
    {
        UIReplaysEditor self = (UIReplaysEditor) (Object) this;
        this.bbspp$clickBlocker = new UIElement() {
            @Override
            public boolean subMouseClicked(UIContext context)
            {
                if (!this.area.isInside(context))
                {
                    return super.subMouseClicked(context);
                }

                /* 动作剪辑模式由 UIClipsMixin 精准屏蔽剪辑命中，这里必须放行鼠标事件，
                 * 否则 UIClips 无法进入原版 scrubbing 状态。 */
                if (self.keyframeEditor == null || !self.keyframeEditor.view.isVisible())
                {
                    return super.subMouseClicked(context);
                }

                if (context.mouseButton == 0)
                {
                    /* 只有时间图表一侧的普通左键用于拖动播放头；标签栏和带修饰键的点击仍被吞掉，
                     * 从而避免折叠轨道、框选、添加或复制被标尺遮住的关键帧。 */
                    if (self.keyframeEditor.view.graphArea.isInside(context)
                        && !Window.isCtrlPressed()
                        && !Window.isShiftPressed()
                        && !Window.isAltPressed())
                    {
                        UIReplaysEditorHeaderBlockerMixin.this.bbspp$scrubbingRuler = true;
                        UIReplaysEditorHeaderBlockerMixin.this.bbspp$updateRulerCursor(self, context);
                    }

                    return true;
                }
                else if (context.mouseButton == 1)
                {
                    return true;
                }

                return super.subMouseClicked(context);
            }

            @Override
            public boolean subMouseReleased(UIContext context)
            {
                if (UIReplaysEditorHeaderBlockerMixin.this.bbspp$scrubbingRuler)
                {
                    UIReplaysEditorHeaderBlockerMixin.this.bbspp$scrubbingRuler = false;

                    return true;
                }

                return super.subMouseReleased(context);
            }

            @Override
            public void render(UIContext context)
            {
                if (UIReplaysEditorHeaderBlockerMixin.this.bbspp$scrubbingRuler)
                {
                    UIReplaysEditorHeaderBlockerMixin.this.bbspp$updateRulerCursor(self, context);
                }

                super.render(context);
            }
        };
        // 顶部时间轴和图标栏占据 20 像素的高度
        this.bbspp$clickBlocker.relative(self).y(0).w(1F).h(20);
    }

    /**
     * 注入目标：{@link UIReplaysEditor} 将分类栏重新置顶之前。
     * 注入原因：时间轴重建或模式切换会改变子元素顺序，遮挡层必须持续位于时间轴之上。
     * 修改行为：先把遮挡层移到前景，随后原方法会再把可交互的分类按钮放到它上面。
     */
    @Inject(method = "bringBarToFront", at = @At("HEAD"))
    private void bbspp$bringBlockerToFront(CallbackInfo ci)
    {
        UIReplaysEditor self = (UIReplaysEditor) (Object) this;

        /* 构造期间原类可能在遮挡层尚未创建时提前调用此方法。 */
        if (this.bbspp$clickBlocker == null)
        {
            return;
        }

        if (this.bbspp$clickBlocker.getParent() != null)
        {
            this.bbspp$clickBlocker.removeFromParent();
        }
        
        self.add(this.bbspp$clickBlocker);
    }

    @Unique
    private void bbspp$updateRulerCursor(UIReplaysEditor self, UIContext context)
    {
        if (self.keyframeEditor == null || !(self.keyframeEditor.view instanceof UIFilmKeyframes keyframes) || keyframes.editor == null)
        {
            this.bbspp$scrubbingRuler = false;

            return;
        }

        long offset = keyframes.getClipOffset();
        int tick = Math.max(0, (int) (Math.round(keyframes.fromGraphX(context.mouseX)) + offset));

        keyframes.editor.setCursor(tick);
    }
}
