package gbeic.bbsplusplus.ui.morphing;

import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.forms.FormCategories;
import mchorse.bbs_mod.forms.categories.UserFormCategory;
import mchorse.bbs_mod.forms.sections.UserFormSection;
import mchorse.bbs_mod.l10n.L10n;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.forms.categories.UIFormCategory;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIConfirmOverlayPanel;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlay;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIPromptOverlayPanel;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.UIScrollView;
import mchorse.bbs_mod.ui.framework.elements.utils.UIDraggable;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.utils.colors.Colors;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class UIBBSPPCategorySidebar extends UIElement
{
    public UIScrollView scroll;
    private UIDraggable resizer;

    private int width = 120;
    private int minWidth = 80;
    private int maxWidth = 300;

    private List<CategoryItem> items = new ArrayList<>();
    public CategoryItem selected;
    private Consumer<CategoryItem> callback;

    public UIBBSPPCategorySidebar(Consumer<CategoryItem> callback)
    {
        this.callback = callback;
        
        this.scroll = UI.scrollView(0, 0);
        this.scroll.scroll.scrollSpeed = 20;
        this.scroll.full(this);
        this.scroll.w(1F, -4); // 留出4px给拖动条

        this.resizer = new UIDraggable((context) -> 
        {
            this.width = Math.max(this.minWidth, Math.min(this.maxWidth, context.mouseX - this.area.x));
            this.w(this.width);
            if (this.getParent() != null) 
            {
                this.getParent().resize();
            }
        });
        
        this.add(this.scroll, this.resizer);

        this.context((menu) ->
        {
            FormCategories formCategories = BBSModClient.getFormCategories();
            UserFormSection userForms = formCategories.getUserForms();

            menu.action(Icons.ADD, UIKeys.FORMS_CATEGORIES_CONTEXT_ADD_CATEGORY, () ->
            {
                UIOverlay.addOverlay(this.getContext(), new UIPromptOverlayPanel(
                    UIKeys.FORMS_CATEGORIES_ADD_CATEGORY_TITLE,
                    UIKeys.FORMS_CATEGORIES_ADD_CATEGORY_DESCRIPTION,
                    (str) ->
                    {
                        userForms.addUserCategory(new UserFormCategory(IKey.constant(str), formCategories.visibility.get(java.util.UUID.randomUUID().toString()), userForms));
                        if (this.getParent() instanceof UIBBSPPFormList)
                        {
                            ((UIBBSPPFormList) this.getParent()).setupForms(formCategories);
                        }
                    }
                ));
            });
        });
    }

    public void setWidth(int w)
    {
        this.width = Math.max(this.minWidth, Math.min(this.maxWidth, w));
        this.w(this.width);
    }

    public int getSidebarWidth()
    {
        return this.width;
    }

    public void clear()
    {
        this.items.clear();
        this.scroll.removeAll();
        this.selected = null;
    }

    public void clearSelection()
    {
        this.selected = null;
    }

    public void addItem(UIFormCategory category, String id, String label, boolean isHome)
    {
        CategoryItem item = new CategoryItem(category, id, label, isHome);
        this.items.add(item);
        this.scroll.add(item);
    }

    public void select(String id)
    {
        for (CategoryItem item : this.items)
        {
            if (item.id.equals(id))
            {
                this.selected = item;
                if (this.callback != null)
                {
                    this.callback.accept(item);
                }
                return;
            }
        }
    }

    @Override
    public void resize()
    {
        super.resize();
        this.resizer.area.set(this.area.ex() - 4, this.area.y, 4, this.area.h);
    }

    @Override
    public void render(UIContext context)
    {
        // 绘制背景
        context.batcher.box(this.area.x, this.area.y, this.area.ex(), this.area.ey(), Colors.A50);
        
        // 绘制拖动条
        int resizerColor = this.resizer.isDragging() || this.resizer.area.isInside(context) ? BBSSettings.primaryColor.get() : Colors.A50;
        context.batcher.box(this.area.ex() - 1, this.area.y, this.area.ex(), this.area.ey(), resizerColor);

        super.render(context);
    }

    public class CategoryItem extends UIElement
    {
        public UIFormCategory uiCategory;
        public String id;
        public String label;
        public boolean isHome;

        public CategoryItem(UIFormCategory category, String id, String label, boolean isHome)
        {
            this.uiCategory = category;
            this.id = id;
            this.label = label;
            this.isHome = isHome;
            this.h(20);
            
            // 调整布局作为 scroll 的子元素
            this.w(1F).h(20).marginBottom(this.isHome ? 4 : 0);

            this.context((menu) ->
            {
                UIBBSPPCategorySidebar.this.select(this.id);

                FormCategories formCategories = BBSModClient.getFormCategories();
                UserFormSection userForms = formCategories.getUserForms();

                if (!MorphingDefaultCategory.isDefault(this.id))
                {
                    menu.action(Icons.BOOKMARK, L10n.lang("bbspp.ui.morph.set_default_open"), () -> MorphingDefaultCategory.set(this.id));
                }

                menu.action(Icons.ADD, UIKeys.FORMS_CATEGORIES_CONTEXT_ADD_CATEGORY, () ->
                {
                    UIOverlay.addOverlay(this.getContext(), new UIPromptOverlayPanel(
                        UIKeys.FORMS_CATEGORIES_ADD_CATEGORY_TITLE,
                        UIKeys.FORMS_CATEGORIES_ADD_CATEGORY_DESCRIPTION,
                        (str) ->
                        {
                            userForms.addUserCategory(new UserFormCategory(IKey.constant(str), formCategories.visibility.get(java.util.UUID.randomUUID().toString()), userForms));
                            if (UIBBSPPCategorySidebar.this.getParent() instanceof UIBBSPPFormList)
                            {
                                ((UIBBSPPFormList) UIBBSPPCategorySidebar.this.getParent()).setupForms(formCategories);
                            }
                        }
                    ));
                });

                if (this.uiCategory != null && this.uiCategory.category instanceof UserFormCategory)
                {
                    menu.action(Icons.EDIT, UIKeys.FORMS_CATEGORIES_CONTEXT_RENAME_CATEGORY, () ->
                    {
                        UIPromptOverlayPanel panel = new UIPromptOverlayPanel(
                            UIKeys.FORMS_CATEGORIES_RENAME_CATEGORY_TITLE,
                            UIKeys.FORMS_CATEGORIES_RENAME_CATEGORY_DESCRIPTION,
                            (str) ->
                            {
                                this.uiCategory.category.title = IKey.constant(str);
                                userForms.writeUserCategories();
                                if (UIBBSPPCategorySidebar.this.getParent() instanceof UIBBSPPFormList)
                                {
                                    ((UIBBSPPFormList) UIBBSPPCategorySidebar.this.getParent()).setupForms(formCategories);
                                }
                            }
                        );
                        panel.text.setText(this.uiCategory.category.title.get());
                        UIOverlay.addOverlay(this.getContext(), panel);
                    });

                    menu.action(Icons.TRASH, UIKeys.FORMS_CATEGORIES_CONTEXT_REMOVE_CATEGORY, () ->
                    {
                        UIConfirmOverlayPanel panel = new UIConfirmOverlayPanel(
                            UIKeys.FORMS_CATEGORIES_REMOVE_CATEGORY_TITLE.format(this.uiCategory.category.getProcessedTitle()),
                            UIKeys.FORMS_CATEGORIES_REMOVE_CATEGORY_DESCRIPTION,
                            (confirm) ->
                            {
                                if (confirm)
                                {
                                    userForms.removeUserCategory((UserFormCategory) this.uiCategory.category);
                                    if (UIBBSPPCategorySidebar.this.getParent() instanceof UIBBSPPFormList)
                                    {
                                        ((UIBBSPPFormList) UIBBSPPCategorySidebar.this.getParent()).setupForms(formCategories);
                                    }
                                }
                            }
                        );
                        UIOverlay.addOverlay(this.getContext(), panel);
                    });
                }
            });
        }

        @Override
        public boolean subMouseClicked(UIContext context)
        {
            if (this.area.isInside(context) && context.mouseButton == 0)
            {
                UIBBSPPCategorySidebar.this.select(this.id);
                return true;
            }

            if (this.area.isInside(context) && context.mouseButton == 1)
            {
                UIBBSPPCategorySidebar.this.select(this.id);
            }

            return super.subMouseClicked(context);
        }

        @Override
        public void render(UIContext context)
        {
            boolean isSelected = UIBBSPPCategorySidebar.this.selected == this;
            boolean isHovered = this.area.isInside(context);

            if (isSelected)
            {
                context.batcher.box(this.area.x, this.area.y, this.area.ex(), this.area.ey(), Colors.A25 | BBSSettings.primaryColor.get());
                context.batcher.box(this.area.x, this.area.y, this.area.x + 2, this.area.ey(), 0xff000000 | BBSSettings.primaryColor.get());
            }
            else if (isHovered)
            {
                context.batcher.box(this.area.x, this.area.y, this.area.ex(), this.area.ey(), Colors.A25);
            }

            int x = this.area.x + 8;
            int y = this.area.y + 6;
            
            String text = this.label;
            
            int maxTextW = this.area.w - 12;

            // 如果需要则截断文本
            int textW = context.batcher.getFont().getWidth(text);
            if (textW > maxTextW)
            {
                text = context.batcher.getFont().limitToWidth(text, maxTextW);
                this.tooltip(mchorse.bbs_mod.l10n.keys.IKey.raw(this.label));
            }
            else
            {
                this.tooltip((mchorse.bbs_mod.ui.framework.tooltips.ITooltip) null);
            }

            context.batcher.text(text, x, y, isSelected ? Colors.WHITE : Colors.LIGHTEST_GRAY);
            
            if (this.isHome)
            {
                context.batcher.box(this.area.x, this.area.ey() - 1, this.area.ex(), this.area.ey(), Colors.A25);
            }

            super.render(context);
        }
    }
}
