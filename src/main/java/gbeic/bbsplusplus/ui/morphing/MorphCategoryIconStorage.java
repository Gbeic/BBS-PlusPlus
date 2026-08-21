package gbeic.bbsplusplus.ui.morphing;

import gbeic.bbsplusplus.BBSPlusPlusMod;
import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.data.DataToString;
import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.ui.utils.icons.Icon;
import mchorse.bbs_mod.ui.utils.icons.Icons;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * 保存伪装首页各分类所使用的图标。
 * <p>
 * 分类的可见性 ID 在 BBS 工程中保持稳定，因此以它作为键，将图标 ID 写入独立配置文件。
 * 读取时会通过 BBS 的图标注册表反查图标；图标失效或尚未设置时自动回退到文件夹图标。
 * </p>
 */
public final class MorphCategoryIconStorage
{
    private static final File FILE = BBSMod.getSettingsPath("bbsplusplus/morph_category_icons.json");
    private static final Map<String, String> ICON_IDS = new HashMap<>();

    private static boolean loaded;

    private MorphCategoryIconStorage()
    {}

    /**
     * 获取指定分类当前使用的图标。
     */
    public static Icon get(String categoryId)
    {
        load();

        Icon icon = Icons.ICONS.get(ICON_IDS.get(categoryId));

        return icon == null ? Icons.FOLDER : icon;
    }

    /**
     * 更新分类图标并立即写入配置，确保重新进入游戏后仍能恢复选择。
     */
    public static void set(String categoryId, Icon icon)
    {
        load();

        if (categoryId == null || icon == null || !Icons.ICONS.containsKey(icon.id))
        {
            return;
        }

        ICON_IDS.put(categoryId, icon.id);
        save();
    }

    private static void load()
    {
        if (loaded)
        {
            return;
        }

        loaded = true;

        if (!FILE.isFile())
        {
            return;
        }

        try
        {
            BaseType data = DataToString.read(FILE);

            if (data.isMap())
            {
                MapType map = data.asMap();

                for (String categoryId : map.keys())
                {
                    String iconId = map.getString(categoryId);

                    if (Icons.ICONS.containsKey(iconId))
                    {
                        ICON_IDS.put(categoryId, iconId);
                    }
                }
            }
        }
        catch (IOException | RuntimeException e)
        {
            BBSPlusPlusMod.LOGGER.warn("读取伪装分类图标配置失败，将使用默认文件夹图标：{}", FILE.getAbsolutePath(), e);
        }
    }

    private static void save()
    {
        File parent = FILE.getParentFile();

        if (parent != null && !parent.isDirectory() && !parent.mkdirs())
        {
            BBSPlusPlusMod.LOGGER.warn("无法创建伪装分类图标配置目录：{}", parent.getAbsolutePath());

            return;
        }

        MapType data = new MapType(false);

        for (Map.Entry<String, String> entry : ICON_IDS.entrySet())
        {
            data.putString(entry.getKey(), entry.getValue());
        }

        if (!DataToString.writeSilently(FILE, data, true))
        {
            BBSPlusPlusMod.LOGGER.warn("保存伪装分类图标配置失败：{}", FILE.getAbsolutePath());
        }
    }
}
