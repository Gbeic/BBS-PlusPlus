package gbeic.bbsplusplus.mixin;

import gbeic.bbsplusplus.BBSAddonsSettings;
import gbeic.bbsplusplus.client.ui.pose.IPoseBoneTreeList;
import gbeic.bbsplusplus.client.ui.pose.IPoseParameterBrush;
import gbeic.bbsplusplus.client.ui.pose.PoseBoneLabelUtils;
import gbeic.bbsplusplus.client.ui.pose.PoseBoneMarkerRenderer;
import gbeic.bbsplusplus.client.ui.pose.PoseBoneTreeMetadata;
import mchorse.bbs_mod.cubic.IModel;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.input.list.UIList;
import mchorse.bbs_mod.ui.framework.elements.utils.FontRenderer;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.ui.utils.pose.UIPoseBoneStringList;
import mchorse.bbs_mod.utils.Direction;
import mchorse.bbs_mod.utils.colors.Colors;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;
import java.util.function.Predicate;

/**
 * 让通用列表在实际对象为姿势骨骼列表时接入参数刷、树形层级和当前 Pose 帧编辑状态。
 *
 * <p>正式版的 {@link UIPoseBoneStringList} 仍继承普通字符串列表，所以注入保持在
 * {@link UIList} 声明方法的通用入口，并严格检查实际列表类型。其它列表虽然共享目标类，
 * 但不会建立层级元数据，也不会改变行为或外观。</p>
 */
@Mixin(UIList.class)
public abstract class UIListPoseParameterBrushMixin<T> implements IPoseBoneTreeList
{
    @Unique
    private static final int bbspp$BONE_TREE_INDENT = 8;

    @Unique
    private static final int bbspp$BONE_TREE_MIN_INDENT = 4;

    @Unique
    private static final int bbspp$BONE_TREE_GUIDE_COLOR = Colors.A25 | 0xFFFFFF;

    @Shadow
    protected List<T> list;

    @Unique
    private final PoseBoneTreeMetadata bbspp$boneTreeMetadata = new PoseBoneTreeMetadata();

    @Unique
    private boolean bbspp$boneTreeFlat;

    @Unique
    private int bbspp$boneTreeRenderIndent = bbspp$BONE_TREE_INDENT;

    @Unique
    private final UIElement bbspp$boneNameTooltipAnchor = new UIElement();

    @Unique
    private String bbspp$boneNameTooltipCandidate;

    @Unique
    private int bbspp$boneNameTooltipY;

    /**
     * 注入目标：通用列表开始绘制之前。
     * 注入原因：树形缩进必须以整张列表为单位统一计算，否则不同深度的行会出现连接线错位；悬停提示也需要逐帧重置候选行。
     * 修改后的行为：仅为姿势骨骼树计算本帧统一缩进，并准备收集被省略的悬停名称。
     */
    @Inject(method = "render", at = @At("HEAD"), remap = false)
    private void bbspp$prepareAdaptivePoseBoneTree(UIContext context, CallbackInfo ci)
    {
        this.bbspp$boneNameTooltipCandidate = null;

        if (!bbspp$isPoseBoneTreeEnabled() || !((Object) this instanceof UIPoseBoneStringList))
        {
            this.bbspp$boneTreeRenderIndent = bbspp$BONE_TREE_INDENT;

            return;
        }

        UIList<?> self = (UIList<?>) (Object) this;

        if (this.bbspp$boneTreeFlat || self.isFiltering())
        {
            this.bbspp$boneTreeRenderIndent = bbspp$BONE_TREE_INDENT;

            return;
        }

        FontRenderer font = context.batcher.getFont();
        int rightInset = this.bbspp$getBoneNameRightInset();
        int indent = bbspp$BONE_TREE_INDENT;
        int preferredMaximumNameWidth = Math.max(96, self.area.w * 2 / 3);

        /* 只压缩到能为每个深层名称保留合理空间；极端长名称仍交给中间省略处理。 */
        for (T element : this.list)
        {
            if (!(element instanceof String bone))
            {
                continue;
            }

            PoseBoneTreeMetadata.Meta meta = this.bbspp$boneTreeMetadata.get(bone);

            if (meta == null || meta.depth <= 0)
            {
                continue;
            }

            int preferredNameWidth = Math.min(font.getWidth(meta.label), preferredMaximumNameWidth);
            int availableForIndent = self.area.w - rightInset - 4 - preferredNameWidth;
            int candidate = availableForIndent / meta.depth;

            indent = Math.min(indent, candidate);
        }

        this.bbspp$boneTreeRenderIndent = Math.max(bbspp$BONE_TREE_MIN_INDENT, indent);
    }

    /**
     * 注入目标：通用列表绘制单行文字和附加内容之前。
     * 注入原因：正式版姿势骨骼列表只有平铺名称，而最新 BBS 提交会按模型父子关系绘制 Outliner 风格树枝。
     * 修改后的行为：仅在“姿势骨骼树形列表”设置开启且当前实例为姿势骨骼列表时，按层级缩进文字并绘制连接线；搜索结果保持平铺。
     */
    @Inject(method = "renderElementPart", at = @At("HEAD"), cancellable = true, remap = false)
    private void bbspp$renderPoseBoneTree(UIContext context, T element, int index, int x, int y,
                                          boolean hover, boolean selected, CallbackInfo ci)
    {
        if (!bbspp$isPoseBoneTreeEnabled()
            || !((Object) this instanceof UIPoseBoneStringList)
            || !(element instanceof String bone))
        {
            return;
        }

        UIList<?> self = (UIList<?>) (Object) this;
        boolean flat = this.bbspp$boneTreeFlat || self.isFiltering();
        PoseBoneTreeMetadata.Meta meta = flat ? null : this.bbspp$boneTreeMetadata.get(bone);
        int depth = meta == null ? 0 : meta.depth;
        int rowHeight = self.scroll.scrollItemSize;

        if (meta != null && depth > 0)
        {
            int middleY = y + rowHeight / 2;
            int textX = x + 4 + depth * this.bbspp$boneTreeRenderIndent;

            for (int level = 0; level < depth - 1; level++)
            {
                if ((meta.lines & (1 << level)) != 0)
                {
                    int lineX = bbspp$boneTreeColumnX(x, level, this.bbspp$boneTreeRenderIndent);

                    context.batcher.box(lineX, y, lineX + 1, y + rowHeight, bbspp$BONE_TREE_GUIDE_COLOR);
                }
            }

            int lineX = bbspp$boneTreeColumnX(x, depth - 1, this.bbspp$boneTreeRenderIndent);

            context.batcher.box(lineX, y, lineX + 1, meta.last ? middleY + 1 : y + rowHeight, bbspp$BONE_TREE_GUIDE_COLOR);
            context.batcher.box(lineX + 1, middleY, textX - 2, middleY + 1, bbspp$BONE_TREE_GUIDE_COLOR);
        }

        String label = meta == null ? bone : meta.label;
        int color = hover ? Colors.HIGHLIGHT : Colors.WHITE;
        int textX = x + 4 + depth * this.bbspp$boneTreeRenderIndent;
        int textY = y + (rowHeight - context.batcher.getFont().getHeight()) / 2;
        int availableWidth = Math.max(0, x + self.area.w - this.bbspp$getBoneNameRightInset() - textX);
        String visibleLabel = PoseBoneLabelUtils.limitMiddle(context.batcher.getFont(), label, availableWidth);

        if (hover && !visibleLabel.equals(label))
        {
            this.bbspp$boneNameTooltipCandidate = label;
            this.bbspp$boneNameTooltipY = y;
        }

        context.batcher.textShadow(visibleLabel, textX, textY, color);
        ci.cancel();
    }

    /**
     * 注入目标：通用列表完成裁剪区与子元素绘制之后。
     * 注入原因：完整骨骼名称必须交给全局提示层绘制，才能越过狭窄列表的裁剪边界且不遮挡当前行操作。
     * 修改后的行为：名称确实被省略时，鼠标放到该行便立即在左侧显示完整名称，移开后立即消失。
     */
    @Inject(method = "render", at = @At("TAIL"), remap = false)
    private void bbspp$showTruncatedPoseBoneName(UIContext context, CallbackInfo ci)
    {
        if (!bbspp$isPoseBoneTreeEnabled() || !((Object) this instanceof UIPoseBoneStringList)
            || this.bbspp$boneNameTooltipCandidate == null)
        {
            return;
        }

        UIList<?> self = (UIList<?>) (Object) this;

        this.bbspp$boneNameTooltipAnchor.area.set(self.area.x, this.bbspp$boneNameTooltipY,
            self.area.w, self.scroll.scrollItemSize);
        this.bbspp$boneNameTooltipAnchor.tooltip(IKey.constant(this.bbspp$boneNameTooltipCandidate), Direction.LEFT);
        context.tooltip.set(context, this.bbspp$boneNameTooltipAnchor);
    }

    /**
     * 注入目标：通用列表把鼠标点击转换为选择之前。
     * 注入原因：骨骼列表的回调只拿到选择结果，无法可靠知道 Shift/Ctrl 操作下这次真正点中的目标骨骼。
     * 修改后的行为：先把实际点击行交给参数刷；点中复制源时保持原选择，其它目标粘贴后继续执行原版选择切换。
     */
    @Inject(method = "applySelectionOnClick", at = @At("HEAD"), cancellable = true, remap = false)
    private void bbspp$applyPoseParameterBrushFromList(int index, CallbackInfo ci)
    {
        if (!((Object) this instanceof UIPoseBoneStringList) || index < 0 || index >= this.list.size())
        {
            return;
        }

        T element = this.list.get(index);
        IPoseParameterBrush brush = this.bbspp$findParameterBrush();

        if (!(element instanceof String bone) || brush == null)
        {
            return;
        }

        if (brush.bbspp$applyParameterBrush(bone) == IPoseParameterBrush.Result.SOURCE)
        {
            ci.cancel();
        }
    }

    /**
     * 注入目标：通用列表完成单行原版绘制之后。
     * 注入原因：原版姿势骨骼列表只显示名称，无法判断当前 Pose 关键帧究竟修改了哪些骨骼。
     * 修改后的行为：非默认骨骼在行尾显示橙色菱形；参数刷启用时，复制源额外显示绿色复制图标。
     */
    @Inject(method = "renderListElement", at = @At("TAIL"), remap = false)
    private void bbspp$renderPoseBoneState(UIContext context, T element, int index, int x, int y,
                                           boolean hover, boolean selected, CallbackInfo ci)
    {
        if (!((Object) this instanceof UIPoseBoneStringList) || !(element instanceof String bone))
        {
            return;
        }

        IPoseParameterBrush brush = this.bbspp$findParameterBrush();

        if (brush == null)
        {
            return;
        }

        UIList<?> self = (UIList<?>) (Object) this;
        int markerX = x + self.area.w - 12;
        int brushIconX = markerX - 16;
        int markerY = y + self.scroll.scrollItemSize / 2;

        boolean source = brush.bbspp$isParameterBrushSource(bone);
        boolean batch = brush.bbspp$isParameterBrushBatch();

        if (source)
        {
            context.batcher.icon(Icons.COPY, Colors.opaque(Colors.GREEN), brushIconX, markerY, 0.5F, 0.5F);
        }
        else if (batch && brush.bbspp$isParameterBrushTarget(bone))
        {
            context.batcher.icon(Icons.PASTE, Colors.opaque(Colors.GREEN), brushIconX, markerY, 0.5F, 0.5F);
        }
        else if (!batch && hover && brush.bbspp$isParameterBrushArmed())
        {
            context.batcher.icon(Icons.PASTE, Colors.opaque(Colors.GREEN), brushIconX, markerY, 0.5F, 0.5F);
        }

        if (brush.bbspp$isPoseBoneModified(bone))
        {
            PoseBoneMarkerRenderer.renderModifiedDiamond(context, markerX, markerY, Colors.opaque(Colors.ORANGE));
        }
    }

    @Unique
    private IPoseParameterBrush bbspp$findParameterBrush()
    {
        UIElement element = (UIElement) (Object) this;

        while (element != null)
        {
            if (element instanceof IPoseParameterBrush brush)
            {
                return brush;
            }

            element = element.getParent();
        }

        return null;
    }

    @Override
    public void bbspp$setBoneHierarchy(IModel model, Predicate<String> hidden)
    {
        if (!((Object) this instanceof UIPoseBoneStringList))
        {
            return;
        }

        this.bbspp$boneTreeMetadata.setHierarchy(model, hidden);
    }

    @Override
    public void bbspp$setBoneTreeFlat(boolean flat)
    {
        if ((Object) this instanceof UIPoseBoneStringList)
        {
            this.bbspp$boneTreeFlat = flat;
        }
    }

    @Unique
    private static boolean bbspp$isPoseBoneTreeEnabled()
    {
        return BBSAddonsSettings.poseBoneTreeView != null && BBSAddonsSettings.poseBoneTreeView.get();
    }

    @Unique
    private int bbspp$getBoneNameRightInset()
    {
        IPoseParameterBrush brush = this.bbspp$findParameterBrush();

        /* 格式刷开启期间为每一行同时预留粘贴图标与改动菱形，悬停时文字不会左右跳动。 */
        return brush != null && brush.bbspp$isParameterBrushArmed() ? 38 : 20;
    }

    @Unique
    private static int bbspp$boneTreeColumnX(int x, int level, int indent)
    {
        return x + 4 + level * indent + 2;
    }
}
