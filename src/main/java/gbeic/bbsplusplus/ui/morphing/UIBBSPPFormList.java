package gbeic.bbsplusplus.ui.morphing;

import mchorse.bbs_mod.forms.FormCategories;
import mchorse.bbs_mod.ui.forms.IUIFormList;
import mchorse.bbs_mod.ui.forms.UIFormList;
import mchorse.bbs_mod.ui.forms.categories.UIFormCategory;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.forms.categories.FormCategory;
import gbeic.bbsplusplus.mixin.UIFormListAccessor;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.ui.framework.elements.utils.FontRenderer;
import mchorse.bbs_mod.utils.colors.Colors;
import mchorse.bbs_mod.l10n.L10n;

import java.util.List;

/**
 * 改进伪装表单列表, 具有侧边导航栏和首页。
 */

public class UIBBSPPFormList extends UIFormList
{
    public UIBBSPPCategorySidebar sidebar;
    public UIBBSPPCategoryHome home;
    public UIElement contentArea;
    public mchorse.bbs_mod.ui.framework.elements.buttons.UIIcon openModelsBtn;

    public UIBBSPPFormList(IUIFormList palette)
    {
        super(palette);

        this.removeAll();

        // 1. 设置顶部栏
        this.bar.resetFlex();
        this.bar.relative(this).x(0).y(0).w(1F).h(24).row(4).height(24);
        this.search.h(18);
        
        this.openModelsBtn = new mchorse.bbs_mod.ui.framework.elements.buttons.UIIcon(
            mchorse.bbs_mod.ui.utils.icons.Icons.FOLDER,
            (b) -> mchorse.bbs_mod.ui.utils.UIUtils.openFolder(mchorse.bbs_mod.BBSMod.getAssetsPath("models"))
        );
        this.openModelsBtn.tooltip(mchorse.bbs_mod.l10n.L10n.lang("studio.ui.utility.open_models_folder"));
        
        this.openModelsBtn.w(20);
        
        // 我们把新按钮和原有的按钮重新排列
        this.bar.removeAll();
        if (this.categoryFilter != null)
        {
            this.bar.add(this.categoryFilter);
        }
        this.bar.add(this.search, this.edit, this.openModelsBtn, this.close);
        
        
        // 2. 设置侧边栏
        this.sidebar = new UIBBSPPCategorySidebar(this::onSidebarSelect);
        this.sidebar.relative(this).x(0).y(24).w(120).h(1F, -24);

        // 3. 设置内容区域（右侧）
        this.contentArea = new UIElement();
        
        // 4. 设置首页
        this.home = new UIBBSPPCategoryHome(this::onHomeCategorySelect);
        this.home.full(this.contentArea);

        // 5. 设置表单（原始的 scrollview）
        this.forms.full(this.contentArea);

        this.contentArea.add(this.home, this.forms);
        this.add(this.bar, this.sidebar, this.contentArea);
    }

    @Override
    public void resize()
    {
        if (this.sidebar != null && this.contentArea != null)
        {
            int sw = this.sidebar.getSidebarWidth();
            this.sidebar.w(sw);
            this.contentArea.relative(this).x(sw).y(24).w(1F, -sw).h(1F, -24);
        }
        
        super.resize();
    }

    private void onSidebarSelect(UIBBSPPCategorySidebar.CategoryItem item)
    {
        this.search.setText(""); // 清除搜索
        
        if (item == null || item.isHome)
        {
            this.home.setVisible(true);
            this.forms.setVisible(false);
        }
        else
        {
            this.home.setVisible(false);
            this.forms.setVisible(true);
            
            this.forms.removeAll(); // 清除全部
            
            // 仅重新添加选中的类别
            List<UIFormCategory> allCats = ((UIFormListAccessor) this).getCategories();
            for (UIFormCategory cat : allCats)
            {
                boolean isSelectedCat = cat.category.visible.getId().equals(item.id);
                if (isSelectedCat)
                {
                    this.forms.add(cat);
                    cat.category.visible.set(true); // 展开它
                    cat.setVisible(true);
                }
            }
            this.forms.resize();
        }
        
        this.resize();
    }

    private void onHomeCategorySelect(FormCategory category)
    {
        this.sidebar.select(category.visible.getId());
    }

    @Override
    public void setupForms(FormCategories formsCategories)
    {
        super.setupForms(formsCategories);
        
        if (this.sidebar == null) return; // 从 super() 调用，此时我们的字段尚未初始化

        List<UIFormCategory> allCats = ((UIFormListAccessor) this).getCategories();

        String currentSelectionId = MorphingDefaultCategory.get();

        // 填充侧边栏
        this.sidebar.clear();
        this.sidebar.addItem(null, MorphingDefaultCategory.HOME, L10n.lang("bbs.ui.bbspp.morph.home").get(), true);
        
        for (UIFormCategory category : allCats)
        {
            this.sidebar.addItem(category, category.category.visible.getId(), category.category.getProcessedTitle(), false);
        }

        // 填充首页
        this.home.setup(allCats);

        this.sidebar.select(currentSelectionId);
        
        // 如果之前选中的类别被删除了，回退到首页
        if (this.sidebar.selected == null)
        {
            if (!MorphingDefaultCategory.HOME.equals(currentSelectionId))
            {
                MorphingDefaultCategory.set(MorphingDefaultCategory.HOME);
            }

            this.sidebar.select(MorphingDefaultCategory.HOME);
        }
        
        // 重新计算布局尺寸以避免新生成的 UI 元素宽高为 0
        this.resize();
    }

    @Override
    public void setSelected(Form form)
    {
        super.setSelected(form);

        this.applyDefaultCategory();
    }

    private void applyDefaultCategory()
    {
        if (this.sidebar == null)
        {
            return;
        }

        String id = MorphingDefaultCategory.get();

        this.sidebar.select(id);

        if (this.sidebar.selected == null)
        {
            MorphingDefaultCategory.set(MorphingDefaultCategory.HOME);
            this.sidebar.select(MorphingDefaultCategory.HOME);
        }
    }

    private String previousSelectionId = "home";

    public void afterSearch(String raw)
    {
        if (this.sidebar == null) return;
        
        String s = raw == null ? "" : raw.trim();
        if (s.isEmpty())
        {
            // 恢复之前的选中状态
            this.sidebar.select(this.previousSelectionId);
        }
        else
        {
            // 记住搜索前的选中状态，但仅当我们还没有在搜索时
            if (this.sidebar.selected != null)
            {
                this.previousSelectionId = this.sidebar.selected.id;
            }
            
            // 搜索时，自动切换到列表视图
            this.home.setVisible(false);
            this.forms.setVisible(true);
            
            this.forms.removeAll();
            List<UIFormCategory> allCats = ((UIFormListAccessor) this).getCategories();
            for (UIFormCategory cat : allCats)
            {
                this.forms.add(cat);
                cat.setVisible(!cat.getForms().isEmpty());
                if (cat.isVisible())
                {
                    cat.category.visible.set(true);
                }
            }
            
            this.sidebar.clearSelection();
            this.forms.resize();
            this.resize();
        }
    }

    @Override
    public void render(UIContext context)
    {
        super.render(context);

        /* 在侧边栏底部渲染表单的显示名称和ID */
        Form selected = this.getSelected();

        if (selected != null && this.sidebar != null && this.sidebar.area.w > 0)
        {
            String displayName = selected.getDisplayName();
            String id = selected.getFormId();
            FontRenderer font = context.batcher.getFont();

            int maxTextW = this.sidebar.area.w - 20; // 留出4px给拖动条
            
            // 限制文本宽度
            String drawName = font.limitToWidth(displayName, maxTextW);
            String drawId = font.limitToWidth(id, maxTextW);

            int w = this.sidebar.area.w - 4; // 减去4px的拖动条宽度
            int x = this.sidebar.area.x;
            int h = 32;
            int y = this.sidebar.area.ey() - h;

            // 深色半透明背景
            context.batcher.box(x, y, x + w, y + h, Colors.A75);
            
            // 绘制文本
            context.batcher.textShadow(drawName, x + 8, y + 6);
            context.batcher.textShadow(drawId, x + 8, y + 18, Colors.LIGHTEST_GRAY);

            // 悬浮提示
            if (context.mouseX >= x && context.mouseX <= x + w && context.mouseY >= y && context.mouseY <= y + h)
            {
                if (font.getWidth(displayName) > maxTextW || font.getWidth(id) > maxTextW)
                {
                    int tooltipW = Math.max(font.getWidth(displayName), font.getWidth(id));
                    this.tooltip(mchorse.bbs_mod.l10n.keys.IKey.raw(displayName + "\n" + id), tooltipW, mchorse.bbs_mod.utils.Direction.TOP);
                    context.tooltip.set(context, this);
                    context.tooltip.area.set(x, y, w, h);
                }
            }
            else if (context.tooltip.element == this)
            {
                this.tooltip((mchorse.bbs_mod.ui.framework.tooltips.ITooltip) null);
                context.tooltip.set(context, null);
            }
        }
    }
}
