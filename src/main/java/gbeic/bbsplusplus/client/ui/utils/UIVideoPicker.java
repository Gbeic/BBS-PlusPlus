package gbeic.bbsplusplus.client.ui.utils;

import gbeic.bbsplusplus.BBSPlusPlusMod;
import gbeic.bbsplusplus.client.ui.forms.editors.panels.UIEffectTreeList;
import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.l10n.L10n;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIIcon;
import mchorse.bbs_mod.ui.framework.elements.input.text.UITextbox;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlay;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlayPanel;
import mchorse.bbs_mod.ui.utils.UIUtils;
import mchorse.bbs_mod.ui.utils.icons.Icons;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * 视频资产选择器。
 *
 * 扫描 BBS 资产目录中的 `video` 文件夹，只返回 `video/xxx` 形式的相对链接，
 * 避免表单保存玩家机器上的绝对路径。
 */
public final class UIVideoPicker
{
    private static final String[] EXTENSIONS = {".mp4", ".mov", ".mkv", ".webm", ".avi", ".m4v"};

    private UIVideoPicker()
    {
    }

    public static void open(UIContext context, Consumer<Link> callback)
    {
        List<String> videos = new ArrayList<>();
        populateVideos(videos);

        UIOverlayPanel panel = new UIOverlayPanel(L10n.lang("bbspp.ui.forms.editors.video_billboard.select_video"));
        UIEffectTreeList treeList = new UIEffectTreeList((list) ->
        {
            if (!list.isEmpty())
            {
                String key = list.get(0);

                if (key != null && !key.isEmpty())
                {
                    int colon = key.indexOf(':');
                    String relative = colon >= 0 ? key.substring(colon + 1) : key;

                    callback.accept(new Link("bbs", "video/" + relative));
                }
                else
                {
                    callback.accept(null);
                }
            }
        });

        treeList.setEffectKeys(videos);

        UITextbox search = new UITextbox(100, treeList::filter);
        search.relative(panel.content).set(6, 6, 0, 0).w(1F, -12).h(20);

        UIIcon refresh = new UIIcon(Icons.REFRESH, (b) ->
        {
            UIUtils.playClick();
            videos.clear();
            populateVideos(videos);
            treeList.setEffectKeys(videos);
            search.setText("");
            treeList.filter("");
        });
        refresh.tooltip(L10n.lang("bbspp.ui.forms.editors.video_billboard.reload_videos"));
        refresh.relative(panel).x(1F, -20).y(20).wh(20, 20);

        UIIcon folder = new UIIcon(Icons.FOLDER, (b) ->
        {
            UIUtils.playClick();
            openVideoFolder();
        });
        folder.tooltip(L10n.lang("bbspp.ui.forms.editors.video_billboard.open_folder"));
        folder.relative(panel).x(1F, -20).y(40).wh(20, 20);

        treeList.relative(panel.content).xy(6, 30).w(1F, -12).h(1F, -36);
        treeList.filter("");

        panel.add(refresh, folder);
        panel.content.add(search, treeList);

        UIOverlay.addOverlay(context, panel, 0.5F, 0.7F);
    }

    private static void populateVideos(List<String> list)
    {
        File folder = getVideoFolder();

        if (!folder.exists())
        {
            folder.mkdirs();
        }

        scan(list, folder, "");
        Collections.sort(list);
    }

    private static void scan(List<String> list, File folder, String prefix)
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
                scan(list, file, path);
            }
            else if (isVideo(file.getName()))
            {
                list.add("bbs:" + path);
            }
        }
    }

    private static boolean isVideo(String name)
    {
        String lower = name.toLowerCase(Locale.ROOT);

        for (String extension : EXTENSIONS)
        {
            if (lower.endsWith(extension))
            {
                return true;
            }
        }

        return false;
    }

    private static File getVideoFolder()
    {
        return new File(BBSMod.getAssetsFolder(), "video");
    }

    private static void openVideoFolder()
    {
        try
        {
            File folder = getVideoFolder();

            if (!folder.exists())
            {
                folder.mkdirs();
            }

            UIUtils.openFolder(folder);
        }
        catch (Exception e)
        {
            BBSPlusPlusMod.LOGGER.error("打开视频文件夹失败", e);
        }
    }
}
