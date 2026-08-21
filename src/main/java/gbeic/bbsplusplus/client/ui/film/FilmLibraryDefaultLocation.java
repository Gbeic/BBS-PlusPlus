package gbeic.bbsplusplus.client.ui.film;

import gbeic.bbsplusplus.BBSAddonsSettings;
import mchorse.bbs_mod.settings.Settings;
import mchorse.bbs_mod.utils.DataPath;

/**
 * 管理新版影片库的默认打开位置。
 * <p>
 * “全部影片”和“根目录”在路径表达上都可能是空值，因此不能直接用空字符串区分。
 * 这里把设置值编码为 {@code all} 或 {@code folder:路径}，让 UI 只关心语义，
 * 避免左侧导航和布局 Mixin 各自拼接魔法字符串。
 * </p>
 */
public final class FilmLibraryDefaultLocation
{
    private static final String ALL = "all";
    private static final String FOLDER_PREFIX = "folder:";

    private FilmLibraryDefaultLocation()
    {}

    /** 判断默认入口是否为“全部影片”。 */
    public static boolean isAllFilms()
    {
        return ALL.equals(get());
    }

    /** 判断指定文件夹是否为默认入口。 */
    public static boolean isFolder(DataPath folder)
    {
        return get().equals(encodeFolder(folder));
    }

    /** 把默认入口设为“全部影片”。 */
    public static void setAllFilms()
    {
        set(ALL);
    }

    /** 把默认入口设为指定文件夹。 */
    public static void setFolder(DataPath folder)
    {
        set(encodeFolder(folder));
    }

    /** 返回默认入口的文件夹路径；如果默认入口是“全部影片”，则返回 {@code null}。 */
    public static DataPath getFolder()
    {
        String value = get();

        if (!value.startsWith(FOLDER_PREFIX))
        {
            return null;
        }

        DataPath path = new DataPath(value.substring(FOLDER_PREFIX.length()));

        path.folder = true;

        return path;
    }

    private static String encodeFolder(DataPath folder)
    {
        return FOLDER_PREFIX + (folder == null ? "" : folder.toString());
    }

    private static String get()
    {
        if (BBSAddonsSettings.filmLibraryDefaultLocation == null)
        {
            return ALL;
        }

        String value = BBSAddonsSettings.filmLibraryDefaultLocation.get();

        return value == null || value.isEmpty() ? ALL : value;
    }

    private static void set(String value)
    {
        if (BBSAddonsSettings.filmLibraryDefaultLocation != null)
        {
            if (value.equals(get()))
            {
                return;
            }

            BBSAddonsSettings.filmLibraryDefaultLocation.set(value);
            saveNow();
        }
    }

    private static void saveNow()
    {
        if (BBSAddonsSettings.filmLibraryDefaultLocation.getRoot() instanceof Settings settings)
        {
            settings.save();
        }
    }
}
