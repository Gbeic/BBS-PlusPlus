package gbeic.bbsplusplus.mixin;

import gbeic.bbsplusplus.BBSAddonsSettings;
import gbeic.bbsplusplus.client.renderer.VideoTimelineState;
import gbeic.bbsplusplus.util.ClipOverlapFixer;
import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.graphics.window.Window;
import mchorse.bbs_mod.ui.film.IUIClipsDelegate;
import mchorse.bbs_mod.ui.film.UIClips;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.utils.Area;
import mchorse.bbs_mod.utils.clips.Clip;
import mchorse.bbs_mod.utils.clips.Clips;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.joml.Vector3i;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 修复摄像机编辑器（UIClips）中时间轴标尺区域的穿透问题。
 *
 * 问题：时间轴顶部的标尺（约 21px 高）在视觉上会遮挡其下方的剪辑片段，
 * 但鼠标事件仍然可以穿透标尺，点击/框选/拖拽到被遮挡的剪辑。
 *
 * 解决方案采用三管齐下的精准拦截策略：
 * 1. @ModifyVariable 钳制 fromLayerY 入参 → 解决拖拽时层级计算飞天的问题
 * 2. @Redirect 拦截 handleLeftClick 中的 getClipAt → 阻止点击选中被遮挡的剪辑
 * 3. @Inject 钳制 captureSelection 的区域 → 阻止框选选中被遮挡的剪辑
 */
@Mixin(value = UIClips.class, remap = false)
public class UIClipsMixin
{
    /** 标尺的像素高度，与 TimelineRulerRenderer.RULER_BLOCK_HEIGHT (21) 保持一致 */
    private static final int RULER_HEIGHT = 21;

    /** 底部功能栏占用的高度，与 UIClips 的 MARGIN 保持一致。 */
    private static final int BOTTOM_MARGIN = 10;

    /** 拖拽剪辑靠近轨道上下边缘时触发自动滚动的区域高度。 */
    private static final int AUTO_SCROLL_ZONE = 24;

    /** 边缘自动滚动每帧允许移动的最大像素数。 */
    private static final double AUTO_SCROLL_MAX_SPEED = 6D;

    /** 开始拖拽时鼠标命中的固定层级，滚动视口后仍作为垂直位移基准。 */
    @Unique
    private int bbspp$initialDragLayer = -1;

    /** 当前帧为提高拖拽剪辑绘制层级而生成的临时渲染顺序。 */
    @Unique
    private List<Clip> bbspp$dragRenderOrder;

    /** 当前粘贴剪辑是否因为可见轨道已满而需要跳过。 */
    @Unique
    private boolean bbspp$skipCurrentPastedClip;

    @Shadow
    private Clips clips;

    @Shadow
    private IUIClipsDelegate delegate;

    @Shadow
    private boolean scrolling;

    @Shadow
    private boolean scrubbing;

    @Shadow
    private int grabMode;

    @Shadow
    private boolean grabbing;

    @Shadow
    private List<Clip> grabbedClips;

    @Shadow
    private List<Clip> otherClips;

    @Shadow
    private List<Vector3i> grabbedData;

    @Shadow
    private boolean isSelecting()
    {
        return false;
    }

    /**
     * 注入目标：{@code UIClips.renderCameraWork} 获取待绘制剪辑列表的位置。
     * 注入原因：原版按列表顺序绘制剪辑，排在后面的静止剪辑可能覆盖正在拖动的剪辑。
     * 修改行为：仅在拖拽期间生成临时绘制顺序，把所有拖拽剪辑放到列表末尾，使其始终最后绘制；不修改真实剪辑列表和轨道层级。
     */
    @Redirect(
        method = "renderCameraWork",
        at = @At(value = "INVOKE", target = "Lmchorse/bbs_mod/utils/clips/Clips;get()Ljava/util/List;")
    )
    private List<Clip> bbspp$renderDraggedClipsAboveOthers(Clips clips)
    {
        List<Clip> originalOrder = clips.get();

        this.bbspp$dragRenderOrder = null;

        if (!this.grabbing || this.grabbedClips == null || this.grabbedClips.isEmpty())
        {
            return originalOrder;
        }

        List<Clip> renderOrder = new ArrayList<>(originalOrder.size());

        for (Clip clip : originalOrder)
        {
            if (!this.grabbedClips.contains(clip))
            {
                renderOrder.add(clip);
            }
        }

        renderOrder.addAll(this.grabbedClips);
        this.bbspp$dragRenderOrder = renderOrder;

        return renderOrder;
    }

    /**
     * 注入目标：{@code UIClips.renderCameraWork} 查询剪辑选中状态的位置。
     * 注入原因：拖拽期间临时调整了绘制顺序，而原版选中状态仍以真实列表索引保存。
     * 修改行为：把临时绘制索引映射回真实剪辑索引，避免高亮和拖拽手柄显示到错误的剪辑上。
     */
    @Redirect(
        method = "renderCameraWork",
        at = @At(value = "INVOKE", target = "Lmchorse/bbs_mod/ui/film/UIClips;hasSelected(I)Z")
    )
    private boolean bbspp$preserveSelectionForDragRenderOrder(UIClips self, int renderIndex)
    {
        if (this.bbspp$dragRenderOrder != null
            && renderIndex >= 0
            && renderIndex < this.bbspp$dragRenderOrder.size())
        {
            int originalIndex = this.clips.get().indexOf(this.bbspp$dragRenderOrder.get(renderIndex));

            return originalIndex >= 0 && self.hasSelected(originalIndex);
        }

        return self.hasSelected(renderIndex);
    }

    /**
     * 注入目标：{@code UIClips.pasteClips(MapType, int)} 为新剪辑查找空轨道的位置。
     * 注入原因：原版 {@code Clips.findFreeLayer} 遇到重叠只会不断增加轨道编号，从顶部粘贴时会把剪辑推到屏幕外。
     * 修改行为：只在当前可见轨道范围内，从剪辑原轨道开始寻找最近空位；顶部无空间时自动向下寻找。
     */
    @Redirect(
        method = "pasteClips(Lmchorse/bbs_mod/data/types/MapType;I)V",
        at = @At(value = "INVOKE", target = "Lmchorse/bbs_mod/utils/clips/Clips;findFreeLayer(Lmchorse/bbs_mod/utils/clips/Clip;)I")
    )
    private int bbspp$findVisibleLayerForPastedClip(Clips clips, Clip clip)
    {
        int[] visibleRange = this.bbspp$getVisibleLayerRange();

        this.bbspp$skipCurrentPastedClip = false;

        if (visibleRange == null)
        {
            this.bbspp$skipCurrentPastedClip = true;

            return Math.max(0, clip.layer.get());
        }

        int minimumLayer = visibleRange[0];
        int maximumLayer = visibleRange[1];
        int centerLayer = Math.max(minimumLayer, Math.min(clip.layer.get(), maximumLayer));
        int maximumDistance = maximumLayer - minimumLayer;

        for (int distance = 0; distance <= maximumDistance; distance++)
        {
            int higherLayer = centerLayer + distance;

            if (higherLayer <= maximumLayer && this.bbspp$isLayerFreeForClip(clips, clip, higherLayer))
            {
                return higherLayer;
            }

            int lowerLayer = centerLayer - distance;

            if (distance > 0
                && lowerLayer >= minimumLayer
                && this.bbspp$isLayerFreeForClip(clips, clip, lowerLayer))
            {
                return lowerLayer;
            }
        }

        this.bbspp$skipCurrentPastedClip = true;

        return centerLayer;
    }

    /**
     * 注入目标：{@code UIClips.pasteClips(MapType, int)} 把粘贴剪辑加入时间线的位置。
     * 注入原因：当前可见轨道全部被占用时，不能继续生成重叠或位于屏幕外且无法点击的剪辑。
     * 修改行为：仅跳过没有合法可见落点的当前剪辑，其余能够合法落位的粘贴剪辑照常加入时间线。
     */
    @Redirect(
        method = "pasteClips(Lmchorse/bbs_mod/data/types/MapType;I)V",
        at = @At(value = "INVOKE", target = "Lmchorse/bbs_mod/utils/clips/Clips;addClip(Lmchorse/bbs_mod/utils/clips/Clip;)V")
    )
    private void bbspp$skipInvisiblePastedClip(Clips clips, Clip clip)
    {
        if (!this.bbspp$skipCurrentPastedClip)
        {
            clips.addClip(clip);
        }

        this.bbspp$skipCurrentPastedClip = false;
    }

    /**
     * 注入目标：{@link UIClips#subMouseScrolled(UIContext)} 开始处。
     * 注入原因：摄影机编辑器和动作编辑器的剪辑轨道使用 UIClips，自带 Alt+滚轮调整轨道高度逻辑，
     * 不经过关键帧摄影表的滚轮处理。
     * 修改行为：未选中剪辑时，根据 BBS++ 的 Alt 滚轮时间线行为设置禁用轨道高度调整，或改为左右滚动时间线；
     * 已选中剪辑时保留 Alt+滚轮移动剪辑行为，并跟随 BBS++ 的反转时间线滚轮方向设置调整移动方向。
     */
    @Inject(method = "subMouseScrolled", at = @At("HEAD"), cancellable = true)
    private void bbspp$applyAltWheelTimelineMode(UIContext context, CallbackInfoReturnable<Boolean> cir)
    {
        UIClips self = (UIClips) (Object) this;

        if (!self.area.isInside(context)
            || this.scrolling
            || self.hasEmbeddedView()
            || !Window.isAltPressed()
            || context.mouseWheel == 0D
            || this.isSelecting())
        {
            return;
        }

        BBSAddonsSettings.AltWheelTimelineMode mode = BBSAddonsSettings.getFilmAltWheelTimelineMode();

        if (mode == BBSAddonsSettings.AltWheelTimelineMode.DISABLED)
        {
            cir.setReturnValue(true);
        }
        else if (mode == BBSAddonsSettings.AltWheelTimelineMode.HORIZONTAL_SCROLL)
        {
            double offsetX = this.bbspp$getAltWheelHorizontalOffset(self, context);

            self.scale.setShift(self.scale.getShift() + offsetX);
            cir.setReturnValue(true);
        }
    }

    @Unique
    private double bbspp$getAltWheelHorizontalOffset(UIClips self, UIContext context)
    {
        double offsetX = (25F * BBSSettings.scrollingSensitivityHorizontal.get() * context.mouseWheel) / self.scale.getZoom();

        return BBSAddonsSettings.reverseTimelineScroll != null && BBSAddonsSettings.reverseTimelineScroll.get()
            ? -offsetX
            : offsetX;
    }

    /**
     * 重定向目标：{@link UIClips#subMouseScrolled(UIContext)} 中选中剪辑时用于计算移动方向的
     * {@link Math#copySign(double, double)} 调用。
     * 重定向原因：选中剪辑的 Alt+滚轮移动不经过影片编辑器全局 Ctrl+滚轮入口，
     * 因此原本不会应用 BBS++ 的反转时间线滚轮方向设置。
     * 修改行为：启用反转设置时翻转滚轮符号，使选中剪辑移动方向与时间线滚动方向一致。
     */
    @Redirect(
        method = "subMouseScrolled",
        at = @At(value = "INVOKE", target = "Ljava/lang/Math;copySign(DD)D", ordinal = 0)
    )
    private double bbspp$reverseSelectedClipAltWheel(double magnitude, double sign)
    {
        return this.bbspp$shouldReverseTimelineScroll()
            ? Math.copySign(magnitude, -sign)
            : Math.copySign(magnitude, sign);
    }

    /**
     * 钳制 fromLayerY 的入参 mouseY，使其永远不低于标尺的底部边缘。
     *
     * 这是最核心也最精妙的一步：fromLayerY 被拖拽 (dragClips)、点击 (handleLeftClick)、
     * 框选 (captureSelection) 等逻辑共用来将像素 Y 坐标转换为层级索引。
     * 当鼠标移入标尺区域时，原始 fromLayerY 会计算出一个极大的层级号（因为它从底部向上计算），
     * 导致拖拽中的剪辑瞬间飞到天上消失、或点击时误选被遮挡的高层剪辑。
     *
     * 默认通过钳制入参，标尺区域内的 mouseY 会被"钉住"在标尺底边，
     * 层级计算永远落在可见范围内——拖拽时剪辑最多移到标尺下方第一层，不会消失；
     * 点击和框选也不会拿到一个"不可能"的层级号。
     * 当“允许拖动扩展剪辑轨道”开启且正在拖动整段剪辑时，跳过顶部钳制以恢复原版创建更高轨道的行为；
     * 点击、框选和调整剪辑边缘仍保留钳制，避免标尺区域重新发生鼠标穿透。
     */
    @ModifyVariable(method = "fromLayerY", at = @At("HEAD"), argsOnly = true)
    private int bbspp$clampMouseY(int mouseY)
    {
        UIClips self = (UIClips) (Object) this;

        /* 扩展开关只放行整段剪辑拖动，不能连点击和框选也一起放进标尺区域。 */
        if (!this.bbspp$isClipTrackExpansionEnabled() || !this.grabbing || this.grabMode != 0)
        {
            /* 加 1 像素可避免边界整除后被换算成标尺背后的下一条隐藏轨道。 */
            mouseY = Math.max(mouseY, self.area.y + RULER_HEIGHT + 1);
        }

        // 底部钳制：防止跑到功能标签栏导致层级计算为 -1
        mouseY = Math.min(mouseY, self.area.ey() - BOTTOM_MARGIN);

        /* 顶部安全空间只修正坐标，不再增加滚动内容高度，避免底部凭空多出一条轨道。 */
        return mouseY - this.bbspp$getRulerTrackOffset();
    }

    /**
     * 注入目标：{@link UIClips#toLayerY(int)} 返回处。
     * 注入原因：最高轨道原本会绘制在标尺背后，但直接增加滚动内容高度会让同一段空间也出现在时间线底部。
     * 修改行为：仅当视口靠近最顶部时，把轨道绘制坐标向下偏移到标尺下方；不修改轨道数量、滚动高度或底部边界。
     */
    @Inject(method = "toLayerY", at = @At("RETURN"), cancellable = true)
    private void bbspp$offsetTopTrackBelowRuler(int layer, CallbackInfoReturnable<Integer> cir)
    {
        cir.setReturnValue(cir.getReturnValue() + this.bbspp$getRulerTrackOffset());
    }

    /**
     * 在处理左键点击时，拦截剪辑的选择判定。
     * 当点击发生在标尺区域内时，强制让 getClipAt 返回 null。
     *
     * 配合上面的 fromLayerY 钳制，这一步确保即使钳制后的层级碰巧有剪辑，
     * 也不会被选中。底层代码发现 clip == null 后会自然走入游标拖拽 (Scrubbing) 分支，
     * 完美保留了在标尺上拖拽游标的原生操作。
     */
    @Redirect(method = "handleLeftClick", at = @At(value = "INVOKE", target = "Lmchorse/bbs_mod/utils/clips/Clips;getClipAt(II)Lmchorse/bbs_mod/utils/clips/Clip;"))
    private Clip bbspp$blockClipSelectionInRuler(mchorse.bbs_mod.utils.clips.Clips clips, int tick, int layerIndex)
    {
        UIClips self = (UIClips) (Object) this;

        if (self.getContext().mouseY < self.area.y + RULER_HEIGHT)
        {
            this.bbspp$resetDragState();

            return null;
        }

        Clip clip = clips.getClipAt(tick, layerIndex);

        /* 原版每帧都会用当前滚动位置重新换算起点层级，导致视口滚动量在相减时被抵消。
         * 这里记录真实按下时的层级，让后续自动滚动能转化为剪辑的层级移动。 */
        if (clip == null)
        {
            this.bbspp$resetDragState();
        }
        else
        {
            this.bbspp$initialDragLayer = layerIndex;
        }

        return clip;
    }

    /**
     * 注入目标：{@code UIClips#handleLeftClick} 返回处。
     * 注入原因：原版播放头拖动会连续修改影片 cursor，视频若同步寻帧会阻塞编辑器。
     * 修改行为：确认进入 scrubbing 后通知视频渲染器保持当前画面，直到鼠标释放。
     */
    @Inject(method = "handleLeftClick", at = @At("RETURN"))
    private void bbspp$beginVideoScrubbing(UIContext context, int mouseX, int mouseY, boolean ctrl, boolean shift, boolean alt, CallbackInfoReturnable<Boolean> cir)
    {
        if (this.scrubbing)
        {
            VideoTimelineState.beginScrubbing(this);
        }
    }

    /**
     * 注入目标：{@code UIClips#dragClips(int, int)} 开始处。
     * 注入原因：标尺穿透修复会把鼠标坐标限制在轨道视口内，拖到上下边缘后无法继续访问隐藏轨道。
     * 修改行为：移动整个剪辑且鼠标进入边缘区域时，按靠近边缘的程度平滑滚动纵向轨道；
     * 调整剪辑左右边缘时不滚动，避免只改时长却意外移动轨道视口。
     */
    @Inject(method = "dragClips", at = @At("HEAD"))
    private void bbspp$autoScrollTracksWhileDragging(int mouseX, int mouseY, CallbackInfo ci)
    {
        UIClips self = (UIClips) (Object) this;

        if (this.grabMode != 0 || !self.vertical.hasScrollbar())
        {
            return;
        }

        int top = self.area.y + RULER_HEIGHT;
        int bottom = self.area.ey() - BOTTOM_MARGIN;
        double scroll = self.vertical.getScroll();
        double nextScroll = scroll;

        if (mouseY < top + AUTO_SCROLL_ZONE && scroll > 0D)
        {
            double strength = Math.min(1D, Math.max(0D, (top + AUTO_SCROLL_ZONE - mouseY) / (double) AUTO_SCROLL_ZONE));

            nextScroll -= 1D + (AUTO_SCROLL_MAX_SPEED - 1D) * strength;
        }
        else
        {
            double maxScroll = Math.max(0D, self.vertical.scrollSize - self.vertical.area.h);

            if (mouseY > bottom - AUTO_SCROLL_ZONE && scroll < maxScroll)
            {
                double strength = Math.min(1D, Math.max(0D, (mouseY - (bottom - AUTO_SCROLL_ZONE)) / (double) AUTO_SCROLL_ZONE));

                nextScroll += 1D + (AUTO_SCROLL_MAX_SPEED - 1D) * strength;
            }
        }

        if (nextScroll != scroll)
        {
            /* setScroll 会同时更新当前值与目标值，确保本帧的层级换算立即使用新视口。 */
            self.vertical.setScroll(nextScroll);
        }
    }

    /**
     * 重定向目标：{@code UIClips#dragClips(int, int)} 中第二次调用 {@code fromLayerY} 的起点层级换算。
     * 重定向原因：视口滚动会同时改变当前点和起点的换算结果，使两者相减后滚动量完全抵消。
     * 修改行为：拖拽期间始终返回按下剪辑时记录的层级，让滚动后显示的新轨道成为有效落点。
     */
    @Redirect(
        method = "dragClips",
        at = @At(
            value = "INVOKE",
            target = "Lmchorse/bbs_mod/ui/film/UIClips;fromLayerY(I)I",
            ordinal = 1
        )
    )
    private int bbspp$keepInitialLayerWhileScrolling(UIClips instance, int mouseY)
    {
        return this.bbspp$initialDragLayer >= 0 ? this.bbspp$initialDragLayer : instance.fromLayerY(mouseY);
    }

    /**
     * 注入目标：{@link UIClips#subMouseReleased(UIContext)} 开始处。
     * 注入原因：整段剪辑拖动期间允许临时重叠，必须在原版清空拖拽快照前确定最终落点。
     * 修改行为：若松开位置发生重叠，则保持当前时间位置，把整组剪辑移动到距离目标最近的合法轨道；
     * 开启轨道扩展时还会把新增的高层轨道滚入标尺下方，确保剪辑松开后仍可见、可点击。
     */
    @Inject(method = "subMouseReleased", at = @At("HEAD"))
    private void bbspp$correctOverlappingDrop(UIContext context, CallbackInfoReturnable<Boolean> cir)
    {
        if (!this.grabbing
            || this.grabMode != 0
            || this.grabbedClips == null
            || this.otherClips == null
            || this.grabbedData == null
            || this.grabbedClips.isEmpty()
            || this.grabbedData.size() != this.grabbedClips.size())
        {
            return;
        }

        Clip reference = this.grabbedClips.get(0);
        Vector3i original = this.grabbedData.get(0);
        int dx = reference.tick.get() - original.x();
        int dy = reference.layer.get() - original.y();
        int[] bounded = this.bbspp$clampToTimelineBounds(this.grabbedData, dx, dy);

        dx = bounded[0];
        dy = bounded[1];

        if (this.collisionExists(this.otherClips, this.grabbedData, dx, dy))
        {
            int[] corrected = this.bbspp$isClipTrackExpansionEnabled()
                ? this.bbspp$findNearestFreeLayer(this.otherClips, this.grabbedData, dx, dy)
                : this.bbspp$findNearestFreeVisibleLayer(this.otherClips, this.grabbedData, dx, dy);

            if (corrected != null)
            {
                dx = corrected[0];
                dy = corrected[1];
            }
            else
            {
                /* 当前可见轨道全部被占用时取消本次移动，不能把剪辑塞到玩家看不见的轨道。 */
                dx = 0;
                dy = 0;
            }
        }

        for (int i = 0; i < this.grabbedClips.size(); i++)
        {
            Clip clip = this.grabbedClips.get(i);
            Vector3i data = this.grabbedData.get(i);

            clip.tick.set(data.x() + dx);
            clip.layer.set(data.y() + dy);
            clip.duration.set(data.z());
        }

        if (this.delegate != null)
        {
            this.delegate.fillData();
        }

        if (this.bbspp$isClipTrackExpansionEnabled())
        {
            this.bbspp$scrollDraggedClipsIntoView();
        }
    }

    /**
     * 注入目标：{@link UIClips#subMouseReleased(UIContext)} 返回后。
     * 注入原因：固定起始层级只属于当前一次拖拽，不能泄漏到下次交互。
     * 修改行为：鼠标释放后仅清除拖拽基准；不再运行会二次改写落点的全局整理。
     */
    @Inject(method = "subMouseReleased", at = @At("TAIL"))
    private void bbspp$clearInitialDragLayer(UIContext context, CallbackInfoReturnable<Boolean> cir)
    {
        VideoTimelineState.endScrubbing(this);
        this.bbspp$resetDragState();
    }

    /**
     * 钳制框选区域，使其不会越过标尺底部进入标尺内。
     * 防止 Shift+左键框选时选中被标尺遮挡的剪辑片段。
     *
     * 即使框选起点在标尺内（由于 fromLayerY 钳制，实际 initialY 可能在标尺底边），
     * 这一步也能确保最终的选择矩形不会包含标尺下的区域。
     */
    @Inject(method = "captureSelection", at = @At("HEAD"))
    private void bbspp$clipBoxSelection(Area area, CallbackInfo ci)
    {
        UIClips self = (UIClips) (Object) this;
        int headerBottom = self.area.y + RULER_HEIGHT;

        if (area.y < headerBottom)
        {
            int diff = headerBottom - area.y;
            area.y = headerBottom;
            area.h = Math.max(0, area.h - diff);
        }
    }

    /**
     * 注入目标：{@link UIClips#setClips(Clips)} 返回后。
     * 注入原因：如果服务端或旧版本传来的影片已经包含重叠剪辑，UI 初始化时也需要兜底。
     * 修改行为：打开时间线后立即整理同层重叠数据，并刷新右侧剪辑面板。
     */
    @Inject(method = "setClips", at = @At("TAIL"))
    private void bbspp$repairAfterSetClips(Clips clips, CallbackInfo ci)
    {
        this.bbspp$repairTimeline();
    }

    @Shadow
    private boolean collisionExists(List<Clip> others, List<Vector3i> data, int dx, int dy)
    {
        return false;
    }

    /**
     * 重写 resolveCollisions 逻辑，修复拖动剪辑片段时碰撞导致的回弹问题。
     * 移动整个剪辑时允许临时重叠，只钳制负时间和负轨道；最终重叠在鼠标释放前统一校正。
     * 非拖拽路径仍使用最近空轨道和逐轴回退，避免 Alt+滚轮等操作制造无效数据。
     */
    @Overwrite
    private int[] resolveCollisions(List<Clip> others, List<Vector3i> data, int dx, int dy)
    {
        /* 移动整个剪辑时只限制时间轴硬边界，重叠在鼠标释放前统一校正。
         * 调整左右边缘和非鼠标拖动路径仍使用下方的即时碰撞保护。 */
        if (this.grabbing && this.grabMode == 0)
        {
            return this.bbspp$clampToTimelineBounds(data, dx, dy);
        }

        if (!this.collisionExists(others, data, dx, dy))
        {
            return new int[]{dx, dy};
        }

        int[] freeLayer = this.bbspp$findNearestFreeLayer(others, data, dx, dy);

        if (freeLayer != null)
        {
            return freeLayer;
        }

        /* 优先保留鼠标指向的时间位置，只将层级退回障碍前最近的合法轨道。 */
        int testDy = dy;
        while (this.collisionExists(others, data, dx, testDy) && testDy != 0)
        {
            testDy -= Integer.signum(testDy);
        }
        if (!this.collisionExists(others, data, dx, testDy))
        {
            return new int[]{dx, testDy};
        }

        int testDx = dx;
        while (this.collisionExists(others, data, testDx, dy) && testDx != 0)
        {
            testDx -= Integer.signum(testDx);
        }
        if (!this.collisionExists(others, data, testDx, dy))
        {
            return new int[]{testDx, dy};
        }

        int dir = 0;
        int guard = Math.abs(dx) + Math.abs(dy) + 2;

        while (this.collisionExists(others, data, dx, dy) && guard > 0)
        {
            boolean changed = false;

            if (dir % 2 == 0)
            {
                if (dx != 0)
                {
                    dx -= Integer.signum(dx);
                    changed = true;
                }
                else if (dy != 0)
                {
                    dy -= Integer.signum(dy);
                    changed = true;
                }
            }
            else
            {
                if (dy != 0)
                {
                    dy -= Integer.signum(dy);
                    changed = true;
                }
                else if (dx != 0)
                {
                    dx -= Integer.signum(dx);
                    changed = true;
                }
            }

            if (!changed)
            {
                break;
            }

            dir += 1;
            guard -= 1;
        }

        if (this.collisionExists(others, data, dx, dy))
        {
            return new int[]{0, 0};
        }

        return new int[]{dx, dy};
    }

    @Unique
    private int[] bbspp$clampToTimelineBounds(List<Vector3i> data, int dx, int dy)
    {
        int minimumDx = Integer.MIN_VALUE;
        int minimumDy = Integer.MIN_VALUE;

        for (Vector3i clip : data)
        {
            minimumDx = Math.max(minimumDx, -clip.x());
            minimumDy = Math.max(minimumDy, -clip.y());
        }

        return new int[]{Math.max(dx, minimumDx), Math.max(dy, minimumDy)};
    }

    @Unique
    private int[] bbspp$findNearestFreeLayer(List<Clip> others, List<Vector3i> data, int dx, int dy)
    {
        return this.bbspp$findNearestFreeLayer(others, data, dx, dy, 0, Integer.MAX_VALUE);
    }

    @Unique
    private int[] bbspp$findNearestFreeVisibleLayer(List<Clip> others, List<Vector3i> data, int dx, int dy)
    {
        int[] visibleRange = this.bbspp$getVisibleLayerRange();

        if (visibleRange == null)
        {
            return null;
        }

        return this.bbspp$findNearestFreeLayer(
            others,
            data,
            dx,
            dy,
            visibleRange[0],
            visibleRange[1]
        );
    }

    @Unique
    private int[] bbspp$getVisibleLayerRange()
    {
        UIClips self = (UIClips) (Object) this;
        int top = self.area.y + RULER_HEIGHT;
        int bottom = self.area.ey() - BOTTOM_MARGIN;

        if (bottom <= top)
        {
            return null;
        }

        /* fromLayerY 与实际鼠标操作共用坐标换算，结果就是玩家当前能够指向的轨道范围。 */
        int firstLayer = self.fromLayerY(bottom - 1);
        int lastLayer = self.fromLayerY(top);

        /* 鼠标恰好位于标尺底边时，整数除法可能返回上一条完全藏在标尺后的轨道。 */
        while (lastLayer > firstLayer && self.toLayerY(lastLayer) < top)
        {
            lastLayer -= 1;
        }

        return new int[]{Math.min(firstLayer, lastLayer), Math.max(firstLayer, lastLayer)};
    }

    @Unique
    private boolean bbspp$isLayerFreeForClip(Clips clips, Clip candidate, int layer)
    {
        long start = candidate.tick.get();
        long end = start + candidate.duration.get();

        for (Clip existing : clips.get())
        {
            if (existing.layer.get() != layer)
            {
                continue;
            }

            long existingStart = existing.tick.get();
            long existingEnd = existingStart + existing.duration.get();

            if (this.bbspp$overlaps(start, end, existingStart, existingEnd))
            {
                return false;
            }
        }

        return true;
    }

    @Unique
    private int[] bbspp$findNearestFreeLayer(
        List<Clip> others,
        List<Vector3i> data,
        int dx,
        int dy,
        int minimumLayer,
        int maximumLayer
    )
    {
        if (!this.grabbing || data.isEmpty())
        {
            return null;
        }

        Set<Integer> blockedOffsets = new HashSet<>();
        int minimumOriginalLayer = Integer.MAX_VALUE;
        int maximumOriginalLayer = Integer.MIN_VALUE;

        /* 每个发生时间重叠的“拖动剪辑—其它剪辑”配对只会禁用一个层级偏移。 */
        for (Vector3i dragged : data)
        {
            long start = (long) dragged.x() + dx;
            long end = start + dragged.z();

            minimumOriginalLayer = Math.min(minimumOriginalLayer, dragged.y());
            maximumOriginalLayer = Math.max(maximumOriginalLayer, dragged.y());

            if (start < 0)
            {
                return null;
            }

            for (Clip other : others)
            {
                long otherStart = other.tick.get();
                long otherEnd = otherStart + Math.max(1, other.duration.get());

                if (this.bbspp$overlaps(start, end, otherStart, otherEnd))
                {
                    blockedOffsets.add(other.layer.get() - dragged.y());
                }
            }
        }

        int minimumDy = Math.max(-minimumOriginalLayer, minimumLayer - minimumOriginalLayer);
        int maximumDy = maximumLayer == Integer.MAX_VALUE
            ? Integer.MAX_VALUE
            : maximumLayer - maximumOriginalLayer;

        if (minimumDy > maximumDy)
        {
            return null;
        }

        int centerDy = Math.max(dy, minimumDy);

        if (maximumDy != Integer.MAX_VALUE)
        {
            centerDy = Math.min(centerDy, maximumDy);
        }

        if (!blockedOffsets.contains(centerDy) && !this.collisionExists(others, data, dx, centerDy))
        {
            return new int[]{dx, centerDy};
        }

        int direction = Integer.signum(dy);

        if (direction == 0)
        {
            direction = 1;
        }

        /* 若中心附近连续被占用，最多跨过禁用偏移数量再多一层就一定能找到空位。 */
        int attempts = blockedOffsets.size() + 1;

        for (int distance = 1; distance <= attempts; distance++)
        {
            int forwardDy = centerDy + direction * distance;

            if (forwardDy >= minimumDy
                && forwardDy <= maximumDy
                && !blockedOffsets.contains(forwardDy)
                && !this.collisionExists(others, data, dx, forwardDy))
            {
                return new int[]{dx, forwardDy};
            }

            int backwardDy = centerDy - direction * distance;

            if (backwardDy >= minimumDy
                && backwardDy <= maximumDy
                && !blockedOffsets.contains(backwardDy)
                && !this.collisionExists(others, data, dx, backwardDy))
            {
                return new int[]{dx, backwardDy};
            }
        }

        return null;
    }

    @Unique
    private boolean bbspp$overlaps(long startA, long endA, long startB, long endB)
    {
        return startA < endB && startB < endA;
    }

    @Unique
    private boolean bbspp$isClipTrackExpansionEnabled()
    {
        return BBSAddonsSettings.allowClipTrackExpansion != null
            && BBSAddonsSettings.allowClipTrackExpansion.get();
    }

    @Unique
    private boolean bbspp$shouldReverseTimelineScroll()
    {
        return BBSAddonsSettings.reverseTimelineScroll != null
            && BBSAddonsSettings.reverseTimelineScroll.get();
    }

    @Unique
    private int bbspp$getRulerTrackOffset()
    {
        UIClips self = (UIClips) (Object) this;

        /* 纵向滚动值达到标尺高度后，最高轨道已经自然离开标尺区域，不再需要额外偏移。 */
        return (int) Math.max(0D, Math.ceil(RULER_HEIGHT - self.vertical.getScroll()));
    }

    @Unique
    private void bbspp$scrollDraggedClipsIntoView()
    {
        if (this.grabbedClips == null || this.grabbedClips.isEmpty())
        {
            return;
        }

        UIClips self = (UIClips) (Object) this;
        int highestLayer = 0;

        for (Clip clip : this.grabbedClips)
        {
            highestLayer = Math.max(highestLayer, clip.layer.get());
        }

        /* 立即刷新滚动尺寸，避免必须等到下一帧才知道刚创建出的最高轨道。 */
        self.updateScrollSize();

        int top = self.area.y + RULER_HEIGHT;

        if (self.toLayerY(highestLayer) >= top)
        {
            return;
        }

        int layerHeight = Math.max(1, self.toLayerY(0) - self.toLayerY(1));
        int contentY = self.vertical.scrollSize - (highestLayer + 1) * layerHeight;

        self.vertical.scrollIntoView(contentY, layerHeight, RULER_HEIGHT);
        self.vertical.updateTarget();
    }


    @Unique
    private void bbspp$resetDragState()
    {
        this.bbspp$initialDragLayer = -1;
    }

    @Unique
    private void bbspp$repairTimeline()
    {
        if (ClipOverlapFixer.repair(this.clips) > 0 && this.delegate != null)
        {
            this.delegate.fillData();
        }
    }
}
