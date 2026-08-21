package gbeic.bbsplusplus.client.ui.utils;

import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.l10n.L10n;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIIcon;
import mchorse.bbs_mod.ui.framework.elements.input.text.UITextbox;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlay;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlayPanel;
import mchorse.bbs_mod.ui.utils.UIUtils;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.colors.Colors;
import gbeic.bbsplusplus.BBSAddonsSettings;
import gbeic.bbsplusplus.BBSPlusPlusMod;
import gbeic.bbsplusplus.client.renderer.BBSEffectLoader;
import gbeic.bbsplusplus.client.ui.forms.editors.panels.UIEffectTreeList;
import net.minecraft.resources.ResourceLocation;
import org.lwjgl.glfw.GLFW;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

/**
 * 特效选择器。
 * <p>
 * 这是一个用于选择 Effekseer 特效文件的 UI 面板。它会扫描 BBS 资源文件夹下的 effeks 子文件夹，列出所有的 .efkefc 文件，并允许用户通过搜索和树形结构浏览来选择一个特效。选择后会通过回调函数返回选中的特效链接。
 * </p>
 */

public class UIEffectPicker
{
    public static void open(UIContext context, Consumer<Link> callback)
    {
        List<String> cachedEffects = new ArrayList<>();
        populateEffects(cachedEffects);

        UIResizableEffectPickerPanel panel = new UIResizableEffectPickerPanel(
            L10n.lang("bbspp.ui.forms.editors.aaa_particle.select_effect"));

        UIEffectTreeList treeList = new UIEffectTreeList((l) ->
        {
            if (!l.isEmpty())
            {
                String effectId = l.get(0);
                if (effectId != null && !effectId.isEmpty())
                {
                    ResourceLocation id = ResourceLocation.parse(effectId);
                    callback.accept(new Link(id.getNamespace(), "effeks/" + id.getPath() + ".efkefc"));
                }
                else
                {
                    callback.accept(null);
                }
            }
        });
        treeList.setEffectKeys(cachedEffects);

        // 搜索框
        UITextbox search = new UITextbox(100, (str) -> treeList.filter(str));
        search.relative(panel.content).set(6, 6, 0, 0).w(1F, -12).h(20);

        // 刷新按钮
        UIIcon refreshBtn = new UIIcon(Icons.REFRESH, (b) ->
        {
            UIUtils.playClick();
            BBSEffectLoader.markCacheDirty();
            cachedEffects.clear();
            populateEffects(cachedEffects);
            treeList.setEffectKeys(cachedEffects);
            search.setText("");
            treeList.filter("");
            context.notifySuccess(L10n.lang("bbspp.ui.forms.editors.aaa_particle.reload_success"));
        });
        refreshBtn.tooltip(L10n.lang("bbspp.ui.forms.editors.aaa_particle.reload_effects"));
        refreshBtn.relative(panel).x(1F, -20).y(20).wh(20, 20);
        panel.add(refreshBtn);

        // 打开 effeks 特效文件夹按钮
        UIIcon folderBtn = new UIIcon(Icons.FOLDER, (b) ->
        {
            UIUtils.playClick();

            try
            {
                java.io.File effeksFolder = new java.io.File(BBSMod.getAssetsFolder(), "effeks");

                if (!effeksFolder.exists())
                {
                    if (effeksFolder.mkdirs())
                    {
                        BBSPlusPlusMod.LOGGER.info("已创建 effeks 特效文件夹");
                    }
                    else
                    {
                        BBSPlusPlusMod.LOGGER.error("无法创建 effeks 特效文件夹");
                        return;
                    }
                }

                String os = System.getProperty("os.name").toLowerCase();
                String[] command;

                if (os.contains("win"))
                {
                    command = new String[]{"explorer.exe", effeksFolder.getAbsolutePath()};
                }
                else if (os.contains("mac"))
                {
                    command = new String[]{"open", effeksFolder.getAbsolutePath()};
                }
                else
                {
                    command = new String[]{"xdg-open", effeksFolder.getAbsolutePath()};
                }

                Process process = new ProcessBuilder(command).start();

                // 消费流防止进程阻塞
                new Thread(() ->
                {
                    try (java.io.InputStream is = process.getInputStream();
                         java.io.InputStream es = process.getErrorStream())
                    {
                        while (is.read() != -1 || es.read() != -1) {}
                    }
                    catch (Exception ignored) {}
                }).start();

                int exitCode = process.waitFor();

                if (exitCode != 0)
                {
                    BBSPlusPlusMod.LOGGER.error("打开 Effekseer 文件夹进程异常退出，错误码：{}", exitCode);
                }
            }
            catch (Exception e)
            {
                BBSPlusPlusMod.LOGGER.error("打开 effeks 特效文件夹时发生错误", e);
            }
        });
        folderBtn.tooltip(L10n.lang("bbspp.ui.forms.editors.aaa_particle.open_folder"));
        folderBtn.relative(panel).x(1F, -20).y(40).wh(20, 20);
        panel.add(folderBtn);

        // 树形列表
        treeList.relative(panel.content).xy(6, 30).w(1F, -12).h(1F, -36);
        treeList.filter("");

        panel.content.add(search, treeList);

        if (UIResizableEffectPickerPanel.hasSavedSize())
        {
            UIOverlay.addOverlay(context, panel, UIResizableEffectPickerPanel.getSavedWidth(), UIResizableEffectPickerPanel.getSavedHeight());
        }
        else
        {
            UIOverlay.addOverlay(context, panel, 0.5F, 0.7F);
        }
    }

    private static void populateEffects(List<String> list)
    {
        try
        {
            File assetsFolder = BBSMod.getAssetsFolder();
            File effeksFolder = new File(assetsFolder, "effeks");

            if (effeksFolder.exists() && effeksFolder.isDirectory())
            {
                scanEffeksFolder(list, effeksFolder, "bbs", "");
            }
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }

        Collections.sort(list);
    }

    private static void scanEffeksFolder(List<String> list, File folder, String namespace, String prefix)
    {
        File[] files = folder.listFiles();

        if (files == null)
        {
            return;
        }

        for (File file : files)
        {
            String path = prefix.isEmpty() ? file.getName() : prefix + "/" + file.getName();

            if (file.isDirectory())
            {
                scanEffeksFolder(list, file, namespace, path);
            }
            else if (file.getName().endsWith(".efkefc"))
            {
                String key = namespace + ":" + path.substring(0, path.length() - 7);
                list.add(key);
            }
        }
    }

    /**
     * 可缩放的 AAA 特效选择面板。
     * <p>
     * BBSFS 的通用覆盖面板只支持拖动位置，不支持拖拽改变尺寸。这里在右下角补一个专用缩放热区，
     * 将面板从比例尺寸切换为像素尺寸，并通过 BBS++ 隐藏设置持久化用户调整后的宽高。
     * </p>
     */
    private static class UIResizableEffectPickerPanel extends UIOverlayPanel
    {
        private static final int MIN_WIDTH = 300;
        private static final int MIN_HEIGHT = 220;
        private static final int SCREEN_PADDING = 16;
        private static final int HANDLE_SIZE = 18;

        private static int savedWidth;
        private static int savedHeight;

        private boolean resizing;
        private int lastResizeX;
        private int lastResizeY;

        public UIResizableEffectPickerPanel(IKey title)
        {
            super(title);
        }

        public static boolean hasSavedSize()
        {
            return getSavedWidth() > 0 && getSavedHeight() > 0;
        }

        public static int getSavedWidth()
        {
            return BBSAddonsSettings.aaaEffectPickerWidth != null
                ? BBSAddonsSettings.aaaEffectPickerWidth.get()
                : savedWidth;
        }

        public static int getSavedHeight()
        {
            return BBSAddonsSettings.aaaEffectPickerHeight != null
                ? BBSAddonsSettings.aaaEffectPickerHeight.get()
                : savedHeight;
        }

        @Override
        protected void afterResizeApplied()
        {
            super.afterResizeApplied();

            int width = this.clampWidth(this.area.w);
            int height = this.clampHeight(this.area.h);

            if (width != this.area.w || height != this.area.h)
            {
                this.applyPixelSize(width, height, true);
            }
            else
            {
                this.saveSize(width, height);
            }
        }

        @Override
        public boolean subMouseClicked(UIContext context)
        {
            if (context.mouseButton == 0 && this.isInsideResizeHandle(context))
            {
                this.resizing = true;
                this.lastResizeX = context.mouseX;
                this.lastResizeY = context.mouseY;

                return true;
            }

            return super.subMouseClicked(context);
        }

        @Override
        public boolean subMouseReleased(UIContext context)
        {
            boolean wasResizing = this.resizing;

            this.resizing = false;

            if (wasResizing)
            {
                this.persistSize();
            }

            return super.subMouseReleased(context) || wasResizing;
        }

        @Override
        public void render(UIContext context)
        {
            if (this.resizing && (context.mouseX != this.lastResizeX || context.mouseY != this.lastResizeY))
            {
                int dx = context.mouseX - this.lastResizeX;
                int dy = context.mouseY - this.lastResizeY;
                int width = this.clampWidth(this.area.w + dx);
                int height = this.clampHeight(this.area.h + dy);

                this.applyPixelSize(width, height, true);

                if (this.getParent() != null)
                {
                    this.getParent().resize();
                }

                this.lastResizeX = context.mouseX;
                this.lastResizeY = context.mouseY;
            }

            if (this.resizing || this.isInsideResizeHandle(context))
            {
                context.requestCursor(GLFW.GLFW_HRESIZE_CURSOR);
            }

            super.render(context);
        }

        @Override
        public void onClose()
        {
            this.persistSize();

            super.onClose();
        }

        @Override
        protected void renderBackground(UIContext context)
        {
            super.renderBackground(context);
            this.renderResizeHandle(context);
        }

        private void applyPixelSize(int width, int height, boolean keepTopLeft)
        {
            int oldWidth = this.area.w;
            int oldHeight = this.area.h;

            this.flex.w.set(0F, width);
            this.flex.h.set(0F, height);

            if (keepTopLeft)
            {
                this.flex.x.offset += (width - oldWidth) / 2;
                this.flex.y.offset += (height - oldHeight) / 2;
            }

            this.area.w = width;
            this.area.h = height;
            this.saveSize(width, height);
        }

        private int clampWidth(int width)
        {
            int maxWidth = this.getMaxWidth();
            int minWidth = Math.min(MIN_WIDTH, maxWidth);

            return Math.max(minWidth, Math.min(maxWidth, width));
        }

        private int clampHeight(int height)
        {
            int maxHeight = this.getMaxHeight();
            int minHeight = Math.min(MIN_HEIGHT, maxHeight);

            return Math.max(minHeight, Math.min(maxHeight, height));
        }

        private int getMaxWidth()
        {
            if (this.getParent() == null)
            {
                return Integer.MAX_VALUE;
            }

            return Math.max(160, this.getParent().area.w - SCREEN_PADDING * 2);
        }

        private int getMaxHeight()
        {
            if (this.getParent() == null)
            {
                return Integer.MAX_VALUE;
            }

            return Math.max(120, this.getParent().area.h - SCREEN_PADDING * 2);
        }

        private boolean isInsideResizeHandle(UIContext context)
        {
            return this.area.isInside(context)
                && context.mouseX >= this.area.ex() - HANDLE_SIZE
                && context.mouseY >= this.area.ey() - HANDLE_SIZE;
        }

        private void renderResizeHandle(UIContext context)
        {
            int color = this.resizing || this.isInsideResizeHandle(context)
                ? Colors.setA(BBSSettings.primaryColor.get(), 0.9F)
                : Colors.setA(Colors.WHITE, 0.45F);
            int right = this.area.ex() - 4;
            int bottom = this.area.ey() - 4;

            for (int i = 0; i < 3; i++)
            {
                int length = 5 + i * 4;
                int y = bottom - i * 4;

                context.batcher.box(right - length, y, right, y + 1, color);
            }
        }

        private void saveSize(int width, int height)
        {
            savedWidth = width;
            savedHeight = height;
        }

        private void persistSize()
        {
            if (BBSAddonsSettings.aaaEffectPickerWidth != null)
            {
                BBSAddonsSettings.aaaEffectPickerWidth.set(savedWidth);
            }

            if (BBSAddonsSettings.aaaEffectPickerHeight != null)
            {
                BBSAddonsSettings.aaaEffectPickerHeight.set(savedHeight);
            }
        }
    }
}
