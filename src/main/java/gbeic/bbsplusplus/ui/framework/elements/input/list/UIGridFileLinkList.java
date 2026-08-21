package gbeic.bbsplusplus.ui.framework.elements.input.list;

import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.graphics.texture.Texture;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.input.list.UIFileLinkList;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.colors.Colors;
import gbeic.bbsplusplus.BBSAddonsSettings;
import org.lwjgl.glfw.GLFW;

import java.util.function.Consumer;

/**
 * 自定义的网格模式文件链接列表。
 * <p>
 * 继承自 {@link UIFileLinkList}，负责在“纹理管理器”中提供不同的排版视图（列表、小网格、中网格、大网格）。
 * 并且支持加载实际图片的缩略图，而不是使用统一的图标。
 * </p>
 */
public class UIGridFileLinkList extends UIFileLinkList {
    private boolean escapeGoBackEnabled;

    public UIGridFileLinkList(Consumer<Link> fileCallback) {
        super(fileCallback);
        
        // 重写点击事件回调，特殊处理 ".." 文件夹以防止返回时的“快进陷阱”
        this.callback = (list) -> {
            FileLink fileLink = list.get(0);

            if (fileLink.folder) {
                // 如果是点击 ".." 返回上一级，绝对不能使用 fastForward (快进)
                // 否则当上一级目录恰好只有这一个子文件夹时，系统会自作聪明地又把你快进回来，导致被困在这个文件夹里！
                boolean isGoBack = fileLink.title.equals("..");
                Link target = fileLink.link;
                if (isGoBack) {
                    target = getFastBackwardLink(target);
                }
                this.setPath(target, !isGoBack);
            } else {
                if (this.fileCallback != null) {
                    this.fileCallback.accept(fileLink.link);
                }
            }
        };
    }

    /**
     * 设置是否允许 ESC 在当前目录列表中返回上一级。
     * 该行为只应在仪表盘底部的主纹理管理器里开启，避免影响表单、关键帧等位置弹出的纹理选择器。
     */
    public UIGridFileLinkList escapeGoBackEnabled(boolean enabled) {
        this.escapeGoBackEnabled = enabled;

        return this;
    }

    /**
     * 自动向后跳过那些“只包含一个子文件夹”的冗余目录
     * （与正向点击时的 fastForward 逻辑对称，提升浏览体验）
     */
    private Link getFastBackwardLink(Link link) {
        Link current = link;
        while (current != null && (!current.path.isEmpty() || !current.source.isEmpty())) {
            java.util.Collection<Link> links = mchorse.bbs_mod.BBSMod.getProvider().getLinksFromPath(current, false);
            if (links.size() == 1) {
                // 该目录下只有 1 个条目，属于无意义的过渡目录，直接跳向上一级
                current = current.path.isEmpty()
                    ? new Link("", "")
                    : new Link(current.source, mchorse.bbs_mod.utils.StringUtils.parentPath(current.path));
            } else {
                break; // 有多个选项，停留在此
            }
        }
        return current != null ? current : new Link("", "");
    }

    /**
     * 获取当前排版模式下的网格整体尺寸
     */
    private int getGridSize() {
        int mode = BBSAddonsSettings.textureManagerLayout.get();
        if (mode == 1) return 72; // 小网格
        if (mode == 2) return 96; // 中网格
        if (mode == 3) return 120; // 大网格
        return 20; // 备选方案（不应在网格模式下使用）
    }

    /**
     * 获取当前排版模式下的缩略图/图标尺寸
     */
    private int getIconSize() {
        int mode = BBSAddonsSettings.textureManagerLayout.get();
        if (mode == 1) return 56;
        if (mode == 2) return 80;
        if (mode == 3) return 104;
        return 16;
    }

    /**
     * 判断当前是否处于网格排版模式（只要不是列表模式即可）
     */
    private boolean isGridMode() {
        return BBSAddonsSettings.textureManagerLayout.get() > 0;
    }

    /**
     * 根据当前宽度计算每行可以容纳多少列网格
     */
    private int getColumns() {
        if (!isGridMode()) return 1;
        int cols = this.area.w / getGridSize();
        return Math.max(1, cols);
    }

    /**
     * 针对网格模式的特殊高度进行更新
     */
    @Override
    public void update() {
        if (isGridMode()) {
            this.scroll.scrollItemSize = getGridSize();
            int items = this.isFiltering() ? getFilteredSize() : this.list.size();
            int columns = getColumns();
            int rows = (int) Math.ceil((double) items / columns);
            this.scroll.setSize(rows);
            this.scroll.clamp();
        } else {
            this.scroll.scrollItemSize = 20;
            super.update();
        }
    }

    /**
     * 获取经过过滤后实际需要显示的项目数量
     */
    private int getFilteredSize() {
        int count = 0;
        while (getElementAt(count) != null) {
            count++;
        }
        return count;
    }

    @Override
    public void setPath(Link link, boolean fastForward) {
        super.setPath(link, fastForward);
    }

    /**
     * 覆盖父类的绘制逻辑，将其渲染为两层循环（行和列），实现网格显示
     */
    @Override
    public void renderList(UIContext context) {
        gbeic.bbsplusplus.utils.TextureThumbnailManager.update();
        if (!isGridMode()) {
            this.scroll.scrollItemSize = 20;
            super.renderList(context);
            return;
        }

        this.scroll.scrollItemSize = getGridSize();
        int columns = getColumns();
        int s = this.scroll.scrollItemSize;
        int totalItems = this.isFiltering() ? getFilteredSize() : this.list.size();
        int rows = (int) Math.ceil((double) totalItems / columns);
        
        for (int row = 0; row < rows; row++) {
            int y = this.area.y + row * s - (int) this.scroll.getScroll();
            int low = this.area.y;
            int high = this.area.ey();

            // 超出可见范围则跳过
            if (y + s < low) continue;
            if (y >= high) break;

            for (int col = 0; col < columns; col++) {
                int index = row * columns + col;
                if (index >= totalItems) break;

                FileLink element = (FileLink) getElementAt(index);
                if (element != null) {
                    int listIndex = this.list.indexOf(element);
                    this.renderGridElement(context, element, listIndex, col, row, y);
                }
            }
        }
    }

    /**
     * 渲染网格布局中的单个文件项
     */
    private mchorse.bbs_mod.ui.framework.elements.UIElement tooltipElement = new mchorse.bbs_mod.ui.framework.elements.UIElement();

    private void renderGridElement(UIContext context, FileLink element, int index, int col, int row, int y) {
        int s = getGridSize();
        int x = this.area.x + col * s;
        
        int mouseX = context.mouseX;
        int mouseY = context.mouseY;
        boolean hover = mouseX >= x && mouseY >= y && mouseX < x + s && mouseY < y + s;
        boolean selected = this.current.contains(index);

        if (selected) {
            context.batcher.box(x, y, x + s, y + s, Colors.A50 | Colors.HIGHLIGHT);
        }

        renderElementPart(context, element, index, x, y, hover, selected);
    }

    private void drawScaledIcon(UIContext context, mchorse.bbs_mod.ui.utils.icons.Icon icon, int color, int x, int y, int size) {
        Texture texture = BBSModClient.getTextures().getTexture(icon.texture);
        if (texture != null) {
            context.batcher.texturedBox(texture, color, x, y, size, size, icon.x, icon.y, icon.x + icon.w, icon.y + icon.h, icon.textureW, icon.textureH);
        }
    }

    /**
     * 重写绘制内部逻辑。
     * 除了原本的文件夹绘制外，对于 PNG 图片尝试获取纹理并绘制真正的缩略图。
     */
    @Override
    protected void renderElementPart(UIContext context, FileLink element, int i, int x, int y, boolean hover, boolean selected) {
        if (!isGridMode()) {
            // 列表模式下的绘制
            if (element.folder) {
                mchorse.bbs_mod.ui.utils.icons.Icon folderIcon = element.title.equals("..") ? Icons.ARROW_LEFT : Icons.FOLDER;
                context.batcher.icon(folderIcon, Colors.WHITE, x + 2, y + 2);
            } else {
                Texture texture = gbeic.bbsplusplus.utils.TextureThumbnailManager.getThumbnail(element.link);
                if (texture != null) {
                    float ratio = (float) texture.width / texture.height;
                    int drawW = 16;
                    int drawH = 16;
                    if (ratio > 1) {
                        drawH = (int) (16 / ratio);
                    } else {
                        drawW = (int) (16 * ratio);
                    }
                    int drawX = x + 2 + (16 - drawW) / 2;
                    int drawY = y + 2 + (16 - drawH) / 2;
                    context.batcher.fullTexturedBox(texture, Colors.WHITE, drawX, drawY, drawW, drawH);
                } else {
                    context.batcher.icon(Icons.IMAGE, Colors.WHITE, x + 2, y + 2);
                }
            }
            context.batcher.textShadow(element.title, x + 22, y + 6, hover ? Colors.HIGHLIGHT : Colors.WHITE);
        } else {
            // 网格模式下的绘制
            int s = getGridSize();
            int iconS = getIconSize();
            int padY = 4;
            
            if (element.folder) {
                mchorse.bbs_mod.ui.utils.icons.Icon folderIcon = element.title.equals("..") ? Icons.ARROW_LEFT : Icons.FOLDER;
                drawScaledIcon(context, folderIcon, Colors.WHITE, x + (s - iconS) / 2, y + padY, iconS);
            } else {
                Texture texture = gbeic.bbsplusplus.utils.TextureThumbnailManager.getThumbnail(element.link);
                if (texture != null) {
                    // 保持缩略图宽高比例
                    float ratio = (float) texture.width / texture.height;
                    int drawW = iconS;
                    int drawH = iconS;
                    if (ratio > 1) {
                        drawH = (int) (iconS / ratio);
                    } else {
                        drawW = (int) (iconS * ratio);
                    }
                    int drawX = x + (s - drawW) / 2;
                    int drawY = y + padY + (iconS - drawH) / 2;
                    context.batcher.fullTexturedBox(texture, Colors.WHITE, drawX, drawY, drawW, drawH);
                } else {
                    drawScaledIcon(context, Icons.IMAGE, Colors.WHITE, x + (s - iconS) / 2, y + padY, iconS);
                }
            }
            
            // 绘制截断的文件名
            int textW = context.batcher.getFont().getWidth(element.title);
            int textX = x + (s - textW) / 2;
            int textY = y + padY + iconS + 2; // 文字位置放在图标下方
            
            if (textW > s - 8) {
                String truncated = context.batcher.getFont().limitToWidth(element.title, s - 8 - 12) + "...";
                textW = context.batcher.getFont().getWidth(truncated);
                textX = x + (s - textW) / 2;
                context.batcher.textShadow(truncated, textX, textY, hover ? Colors.HIGHLIGHT : Colors.WHITE);
                
                if (hover) {
                    this.tooltipElement.area.set(x, y, s, s);
                    this.tooltipElement.tooltip(mchorse.bbs_mod.l10n.keys.IKey.raw(element.title));
                    context.tooltip.set(context, this.tooltipElement);
                }
            } else {
                context.batcher.textShadow(element.title, textX, textY, hover ? Colors.HIGHLIGHT : Colors.WHITE);
            }
        }
    }

    /**
     * 重写鼠标悬停索引的计算逻辑，适应网格坐标
     */
    @Override
    public int getHoveredIndex(UIContext context) {
        if (!this.area.isInside(context)) {
            return -1;
        }
        
        if (!isGridMode()) {
            return super.getHoveredIndex(context);
        }
        
        int s = getGridSize();
        int cols = getColumns();
        
        int row = (context.mouseY - this.area.y + (int) this.scroll.getScroll()) / s;
        int col = (context.mouseX - this.area.x) / s;
        
        if (col < 0 || col >= cols) return -1;
        
        return row * cols + col;
    }

    /**
     * 支持方向键在网格上的二维移动
     */
    @Override
    public boolean subKeyPressed(UIContext context) {
        // 当处于子文件夹时，拦截 ESC 键用于返回上一级目录
        if (this.escapeGoBackEnabled && context.isPressed(GLFW.GLFW_KEY_ESCAPE)) {
            if (this.path != null && (!this.path.path.isEmpty() || !this.path.source.isEmpty())) {
                FileLink parent = (FileLink) getElementAt(0);
                if (parent != null && parent.folder && parent.title.equals("..")) {
                    // 必须传 false 禁用 fastForward，并应用“自动向后跳过”逻辑
                    Link target = getFastBackwardLink(parent.link);
                    this.setPath(target, false);
                    return true;
                }
            }
        }

        if (!isGridMode()) {
            return super.subKeyPressed(context);
        }
        
        // 网格内的二维焦点移动
        if (context.isPressed(GLFW.GLFW_KEY_LEFT)) {
            return moveCurrent(-1);
        }
        if (context.isPressed(GLFW.GLFW_KEY_RIGHT)) {
            return moveCurrent(1);
        }
        if (context.isPressed(GLFW.GLFW_KEY_UP)) {
            return moveCurrent(-getColumns());
        }
        if (context.isPressed(GLFW.GLFW_KEY_DOWN)) {
            return moveCurrent(getColumns());
        }
        
        return super.subKeyPressed(context);
    }

    /**
     * 焦点移动控制，当超出边界时自动限制，移动后确保焦点滚动到可见区域。
     */
    private boolean moveCurrent(int factor) {
        if (this.list.isEmpty()) return false;
        
        int index = this.getIndex();
        if (index == -1) index = 0;
        else index += factor;
        
        if (index < 0) index = 0;
        if (index >= this.list.size()) index = this.list.size() - 1;
        
        this.setIndex(index);
        
        // 自动滚动到对应行
        int cols = getColumns();
        int row = index / cols;
        this.scroll.scrollIntoView(row * getGridSize());
        
        return true;
    }

    /**
     * 完全接管网格模式下的鼠标点击逻辑，而不是使用 hack 修改 mouseY
     */
    @Override
    public boolean subMouseClicked(UIContext context) {
        if (!isGridMode()) {
            return super.subMouseClicked(context);
        }

        if (this.scroll.mouseClicked(context)) {
            return true;
        }

        if (this.area.isInside(context) && context.mouseButton == 0) {
            int index = this.getIndexAtCursor(context);

            if (this.exists(index)) {
                this.applySelectionOnClick(index);

                if (!this.isFiltering() && this.sorting && this.current.size() == 1) {
                    this.dragging = index;
                    this.dragTime = System.currentTimeMillis();
                }

                if (this.callback != null) {
                    this.callback.accept(this.getCurrent());
                }
            }
            // 只要在区域内左键点击，直接返回true，拦截父类的错误计算
            return true;
        }

        // 仅放行非左键点击（如右键菜单）
        return super.subMouseClicked(context);
    }

    /**
     * 同上，完全接管释放鼠标时的拖拽排序逻辑
     */
    @Override
    public boolean subMouseReleased(UIContext context) {
        if (!isGridMode()) {
            return super.subMouseReleased(context);
        }

        if (this.sorting && !this.isFiltering()) {
            if (this.isDragging()) {
                int index = this.getIndexAtCursor(context);

                if (index != -1 && index != this.dragging && this.exists(index)) {
                    this.handleSwap(this.dragging, index);
                } else if (index == -1 && this.area.isInside(context)) {
                    // 如果拖到空白处，默认放到最后
                    index = this.getList().size() - 1;
                    if (index != this.dragging && this.exists(index)) {
                        this.handleSwap(this.dragging, index);
                    }
                }
            }
            this.dragging = -1;
        }

        // 临时关闭 sorting 以绕过父类 UIList 的拖拽逻辑处理，仅调用其 scroll 释放逻辑
        boolean wasSorting = this.sorting;
        this.sorting = false;
        boolean result = super.subMouseReleased(context);
        this.sorting = wasSorting;
        
        return result;
    }
}
