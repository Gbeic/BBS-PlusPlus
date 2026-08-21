package gbeic.bbsplusplus.mixin;

import gbeic.bbsplusplus.BBSAddonsSettings;
import gbeic.bbsplusplus.client.ui.pose.IPoseBoneTreeList;
import gbeic.bbsplusplus.client.ui.pose.IPoseParameterBrush;
import gbeic.bbsplusplus.client.ui.pose.PoseBoneMarkerRenderer;
import gbeic.bbsplusplus.client.ui.pose.PoseBoneTreeMetadata;
import mchorse.bbs_mod.cubic.IModel;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.input.list.UIList;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.ui.utils.pose.UIPoseBoneStringList;
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
    private static final int bbspp$BONE_TREE_GUIDE_COLOR = Colors.A25 | 0xFFFFFF;

    @Shadow
    protected List<T> list;

    @Unique
    private final PoseBoneTreeMetadata bbspp$boneTreeMetadata = new PoseBoneTreeMetadata();

    @Unique
    private boolean bbspp$boneTreeFlat;

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
            int textX = x + 4 + depth * bbspp$BONE_TREE_INDENT;

            for (int level = 0; level < depth - 1; level++)
            {
                if ((meta.lines & (1 << level)) != 0)
                {
                    int lineX = bbspp$boneTreeColumnX(x, level);

                    context.batcher.box(lineX, y, lineX + 1, y + rowHeight, bbspp$BONE_TREE_GUIDE_COLOR);
                }
            }

            int lineX = bbspp$boneTreeColumnX(x, depth - 1);

            context.batcher.box(lineX, y, lineX + 1, meta.last ? middleY + 1 : y + rowHeight, bbspp$BONE_TREE_GUIDE_COLOR);
            context.batcher.box(lineX + 1, middleY, textX - 2, middleY + 1, bbspp$BONE_TREE_GUIDE_COLOR);
        }

        String label = meta == null ? bone : meta.label;
        int color = hover ? Colors.HIGHLIGHT : Colors.WHITE;
        int textX = x + 4 + depth * bbspp$BONE_TREE_INDENT;
        int textY = y + (rowHeight - context.batcher.getFont().getHeight()) / 2;

        context.batcher.textShadow(label, textX, textY, color);
        ci.cancel();
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

        if (source)
        {
            context.batcher.icon(Icons.COPY, Colors.opaque(Colors.GREEN), brushIconX, markerY, 0.5F, 0.5F);
        }
        else if (hover && brush.bbspp$isParameterBrushArmed())
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
    private static int bbspp$boneTreeColumnX(int x, int level)
    {
        return x + 4 + level * bbspp$BONE_TREE_INDENT + 2;
    }
}
