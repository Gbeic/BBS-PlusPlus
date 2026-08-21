package gbeic.bbsplusplus.mixin;

import mchorse.bbs_mod.graphics.window.Window;
import mchorse.bbs_mod.ui.film.UIFilmPanel;
import mchorse.bbs_mod.ui.film.replays.UIReplaysEditor;
import mchorse.bbs_mod.ui.film.utils.keyframes.UIFilmKeyframes;
import mchorse.bbs_mod.ui.framework.elements.IUIElement;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.UIContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

import java.util.List;
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

    /** 层级重排任务已提交但尚未生效，避免每帧重复提交。 */
    @Unique
    private boolean bbspp$blockerReorderPending;

    @Shadow
    public UIElement iconBar;

    /**
     * 注入目标：{@link UIReplaysEditor} 构造方法末尾。
     * 注入原因：关键帧时间轴的标尺只是绘制层，原版命中测试仍会选中其下方被遮挡的关键帧。
     * 修改行为：在关键帧模式下拦截标尺区域的关键帧操作，但将无修饰键左键转换为播放头拖动；
     * 剪辑模式继续交给 {@code UIClips} 自身处理，避免透明遮挡层吞掉它的原版播放头事件。
     */
    @Inject(method = "<init>(Lmchorse/bbs_mod/ui/film/UIFilmPanel;)V", at = @At("TAIL"))
    private void bbspp$initBlocker(UIFilmPanel filmPanel, CallbackInfo ci)
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
     * 注入目标：{@link UIReplaysEditor#resize()} 结束处。
     * 注入原因：时间轴重建或模式切换会改变子元素顺序，遮挡层必须持续位于时间轴之上、
     * 分类栏之下——在时间轴之上才能拦到标尺点击，在分类栏之下才不会挡住分类按钮。
     *
     * <p>
     * 1.20.1 主线的 BBS 2.4 把这段层级重排抽成了私有方法 {@code bringBarToFront}，注入它的
     * HEAD 即可；FSR 所基于的版本把同样的逻辑内联在 {@code replaceKeyframeEditor} 内部，
     * 而且那段代码位于一个交给 {@code runAfterHierarchyMutation} 延迟执行的 lambda 里，
     * Mixin 无法稳定注入（lambda 会编译成名字随编译顺序变化的 synthetic 方法）。
     * 因此本分支改注入 {@code resize()}：该 lambda 内部一定会调用它，是层级变更后的可靠时机。
     * </p>
     * <p>
     * 修改行为：仅当遮挡层的层级不正确时，才把它重新插到分类栏之前。
     * </p>
     */
    @Inject(method = "resize", at = @At("TAIL"))
    private void bbspp$keepBlockerAboveTimeline(CallbackInfo ci)
    {
        /* 构造期间 resize 可能早于遮挡层创建。 */
        if (this.bbspp$clickBlocker == null)
        {
            return;
        }

        UIReplaysEditor self = (UIReplaysEditor) (Object) this;
        List<IUIElement> children = self.getChildren();
        int blocker = children.indexOf(this.bbspp$clickBlocker);
        int bar = children.indexOf(this.iconBar);
        int editor = self.keyframeEditor == null ? -1 : children.indexOf(self.keyframeEditor);

        /* 事件按列表顺序自后向前分发，因此「更靠后」即「更上层」。
         * 遮挡层要排在时间轴之后，同时排在分类栏之前。 */
        boolean correct = blocker >= 0
            && (editor < 0 || blocker > editor)
            && (bar < 0 || blocker < bar);

        if (correct)
        {
            this.bbspp$blockerReorderPending = false;

            return;
        }

        /* add/addBefore 经由 runAfterHierarchyMutation 延迟到本帧的层级变更之后才真正生效，
         * 所以紧接着的几次 resize 里顺序仍是旧的。用标记避免反复提交同一个重排任务。 */
        if (this.bbspp$blockerReorderPending)
        {
            return;
        }

        this.bbspp$blockerReorderPending = true;

        if (this.bbspp$clickBlocker.getParent() != null)
        {
            this.bbspp$clickBlocker.removeFromParent();
        }

        if (bar >= 0)
        {
            self.addBefore(this.iconBar, this.bbspp$clickBlocker);
        }
        else
        {
            self.add(this.bbspp$clickBlocker);
        }
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
