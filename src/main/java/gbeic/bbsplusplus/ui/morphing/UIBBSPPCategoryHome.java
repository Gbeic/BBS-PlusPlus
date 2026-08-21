package gbeic.bbsplusplus.ui.morphing;

import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.UIScrollView;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlay;
import mchorse.bbs_mod.ui.utils.icons.Icon;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.colors.Colors;
import mchorse.bbs_mod.forms.categories.FormCategory;
import mchorse.bbs_mod.ui.forms.categories.UIFormCategory;
import mchorse.bbs_mod.l10n.L10n;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * 在伪装选择器首页以卡片网格展示全部伪装分类。
 */
public class UIBBSPPCategoryHome extends UIScrollView
{
    private Consumer<FormCategory> callback;
    private List<CategoryCard> cards = new ArrayList<>();
    
    public UIBBSPPCategoryHome(Consumer<FormCategory> callback)
    {
        this.callback = callback;
        this.scroll.scrollSpeed = 30;
    }
    
    public void setup(List<UIFormCategory> categories)
    {
        this.cards.clear();
        this.removeAll();
        
        for (UIFormCategory cat : categories)
        {
            CategoryCard card = new CategoryCard(cat.category);
            this.cards.add(card);
            this.add(card);
        }
        
        this.resize();
    }
    
    @Override
    public void resize()
    {
        if (this.flex != null)
        {
            this.flex.apply(this.area);
        }

        // 在网格中布局卡片
        int cardW = 80;
        int cardH = 90;
        int padding = 10;
        
        int w = this.area.w;
        int cols = Math.max(1, (w - padding) / (cardW + padding));
        
        int startX = padding;
        int startY = padding;
        
        for (int i = 0; i < this.cards.size(); i++)
        {
            CategoryCard card = this.cards.get(i);
            int c = i % cols;
            int r = i / cols;
            
            int cx = this.area.x + startX + c * (cardW + padding);
            int cy = this.area.y + startY + r * (cardH + padding);
            
            // 绕过 flex 以保证精确坐标
            if (card.getFlex() != null) card.resetFlex();
            card.resizer(null);
            
            card.area.set(cx, cy, cardW, cardH);
        }
        
        int totalRows = (int) Math.ceil((double) this.cards.size() / cols);
        this.scroll.scrollSize = totalRows * (cardH + padding) + padding;
        
        super.resize();
    }
    
    public class CategoryCard extends UIElement
    {
        public FormCategory category;
        private Icon icon;
        
        public CategoryCard(FormCategory category)
        {
            this.category = category;
            this.icon = MorphCategoryIconStorage.get(category.visible.getId());

            this.context((menu) -> menu.action(Icons.EDIT, L10n.lang("bbspp.ui.morph.change_category_icon"), () ->
            {
                UIBBSPPIconPickerOverlayPanel panel = new UIBBSPPIconPickerOverlayPanel(
                    L10n.lang("bbspp.ui.morph.select_category_icon"),
                    this.icon,
                    (icon) ->
                    {
                        this.icon = icon;
                        MorphCategoryIconStorage.set(this.category.visible.getId(), icon);
                    }
                );

                UIOverlay.addOverlay(this.getContext(), panel, 430, 320);
            }));
        }
        
        @Override
        public boolean subMouseClicked(UIContext context)
        {
            if (this.area.isInside(context) && context.mouseButton == 0)
            {
                if (UIBBSPPCategoryHome.this.callback != null)
                {
                    UIBBSPPCategoryHome.this.callback.accept(this.category);
                }
                return true;
            }
            return super.subMouseClicked(context);
        }
        
        @Override
        public void render(UIContext context)
        {
            boolean isHovered = this.area.isInside(context);
            
            int bgColor = isHovered ? (Colors.A50 | BBSSettings.primaryColor.get()) : Colors.A25;
            context.batcher.box(this.area.x, this.area.y, this.area.ex(), this.area.ey(), bgColor);
            if (isHovered) {
                context.batcher.outline(this.area.x, this.area.y, this.area.ex(), this.area.ey(), Colors.A75 | BBSSettings.primaryColor.get(), 2);
            }
            
            // 在上半部分居中绘制文件夹图标
            int iconX = this.area.x + this.area.w / 2;
            int iconY = this.area.y + 36; // 垂直居中于文本上方的区域
            
            context.batcher.getContext().getMatrices().push();
            context.batcher.getContext().getMatrices().translate(iconX, iconY, 0);
            context.batcher.getContext().getMatrices().scale(2F, 2F, 1F);
            context.batcher.icon(this.icon, 0, 0, 0.5f, 0.5f);
            context.batcher.getContext().getMatrices().pop();
            
            // 在底部绘制标题文本
            String text = this.category.getProcessedTitle();
            int maxTextW = this.area.w - 8;
            int textW = context.batcher.getFont().getWidth(text);
            if (textW > maxTextW)
            {
                text = context.batcher.getFont().limitToWidth(text, maxTextW);
                this.tooltip(mchorse.bbs_mod.l10n.keys.IKey.raw(this.category.getProcessedTitle()));
            }
            else
            {
                this.tooltip((mchorse.bbs_mod.ui.framework.tooltips.ITooltip) null);
            }
            
            int textX = this.area.x + (this.area.w - context.batcher.getFont().getWidth(text)) / 2;
            int textY = this.area.ey() - 15;
            
            context.batcher.text(text, textX, textY, Colors.WHITE);
            
            super.render(context);
        }
    }
    @Override
    public void render(UIContext context)
    {
        UIElement lastTooltip = context.tooltip.element;
        this.scroll.drag(context.mouseX, context.mouseY);
        context.batcher.clip(this.area, context);
        
        this.apply(context);
        
        int scrollY = (int) this.scroll.getScroll();
        for (CategoryCard card : this.cards)
        {
            if (card.isVisible())
            {
                int cardScreenY = card.area.y - scrollY;
                if (cardScreenY + card.area.h > this.area.y && cardScreenY < this.area.ey())
                {
                    card.render(context);
                }
            }
        }
        
        this.unapply(context);
        this.scroll.renderScrollbar(context.batcher);
        context.batcher.unclip(context);
        
        if (!this.area.isInside(context) && context.tooltip.element != lastTooltip)
        {
            context.tooltip.set(context, null);
        }
    }
}
