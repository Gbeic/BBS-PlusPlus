package gbeic.bbsplusplus.ui.morphing;

import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.UIScrollView;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlayPanel;
import mchorse.bbs_mod.ui.utils.icons.Icon;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.colors.Colors;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;

/**
 * 展示 BBS 已注册图标的网格选择弹层。
 * <p>
 * 图标直接来自 {@link Icons#ICONS}，因此会随 BBS 的注册表自动覆盖全部可用图标。
 * 选择任意图标后立即回调并关闭弹层，当前图标会用主题色边框标识。
 * </p>
 */
public class UIBBSPPIconPickerOverlayPanel extends UIOverlayPanel
{
    private static final int CELL_SIZE = 32;
    private static final int PADDING = 6;

    private final UIScrollView scroll;
    private final List<IconCell> cells = new ArrayList<>();
    private final Icon selected;
    private final Consumer<Icon> callback;

    public UIBBSPPIconPickerOverlayPanel(IKey title, Icon selected, Consumer<Icon> callback)
    {
        super(title);

        this.selected = selected;
        this.callback = callback;
        this.scroll = new UIScrollView();
        this.scroll.scroll.scrollSpeed = CELL_SIZE;
        this.scroll.full(this.content);

        List<Icon> icons = new ArrayList<>(Icons.ICONS.values());

        // 按图集中的位置排序，让选择面板与 BBS 图标表的视觉顺序一致。
        icons.sort(Comparator.comparingInt((Icon icon) -> icon.y)
            .thenComparingInt((Icon icon) -> icon.x)
            .thenComparing((Icon icon) -> icon.id));

        for (Icon icon : icons)
        {
            IconCell cell = new IconCell(icon);

            this.cells.add(cell);
            this.scroll.add(cell);
        }

        this.content.add(this.scroll);
    }

    @Override
    public void resize()
    {
        super.resize();

        int availableWidth = Math.max(CELL_SIZE, this.scroll.area.w - PADDING * 2 - this.scroll.scroll.getScrollbarWidth());
        int columns = Math.max(1, availableWidth / (CELL_SIZE + PADDING));

        for (int i = 0; i < this.cells.size(); i++)
        {
            int column = i % columns;
            int row = i / columns;
            int x = this.scroll.area.x + PADDING + column * (CELL_SIZE + PADDING);
            int y = this.scroll.area.y + PADDING + row * (CELL_SIZE + PADDING);

            this.cells.get(i).area.set(x, y, CELL_SIZE, CELL_SIZE);
        }

        int rows = (this.cells.size() + columns - 1) / columns;

        this.scroll.scroll.scrollSize = PADDING + rows * (CELL_SIZE + PADDING);
        this.scroll.scroll.clamp();
    }

    /**
     * 绘制并处理单个图标选项，同时标出悬停状态和当前分类正在使用的图标。
     */
    private class IconCell extends UIElement
    {
        private final Icon icon;

        private IconCell(Icon icon)
        {
            this.icon = icon;
            this.tooltip(IKey.raw(icon.id));
        }

        @Override
        public boolean subMouseClicked(UIContext context)
        {
            if (this.area.isInside(context) && context.mouseButton == 0)
            {
                if (UIBBSPPIconPickerOverlayPanel.this.callback != null)
                {
                    UIBBSPPIconPickerOverlayPanel.this.callback.accept(this.icon);
                }

                UIBBSPPIconPickerOverlayPanel.this.close();

                return true;
            }

            return super.subMouseClicked(context);
        }

        @Override
        public void render(UIContext context)
        {
            boolean hovered = this.area.isInside(context);
            boolean active = this.icon == UIBBSPPIconPickerOverlayPanel.this.selected;

            if (hovered || active)
            {
                int background = hovered ? Colors.A50 : Colors.A25;

                context.batcher.box(this.area.x, this.area.y, this.area.ex(), this.area.ey(), background | BBSSettings.primaryColor.get());
            }

            if (active)
            {
                context.batcher.outline(this.area.x, this.area.y, this.area.ex(), this.area.ey(), Colors.A100 | BBSSettings.primaryColor.get());
            }

            context.batcher.icon(this.icon, Colors.WHITE, this.area.mx(), this.area.my(), 0.5F, 0.5F);
            super.render(context);
        }
    }
}
