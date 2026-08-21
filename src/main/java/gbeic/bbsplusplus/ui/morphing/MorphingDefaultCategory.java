package gbeic.bbsplusplus.ui.morphing;

import gbeic.bbsplusplus.BBSAddonsSettings;

/**
 * 管理新版伪装界面的默认打开分类。
 * <p>
 * 新版伪装界面有“首页”和具体分类两种入口。这个类把默认入口保存到隐藏设置里，
 * 并把读写逻辑集中起来，避免侧边栏和列表初始化代码各自处理空值与默认值。
 * </p>
 */
public final class MorphingDefaultCategory
{
    public static final String HOME = "home";

    private MorphingDefaultCategory()
    {}

    /** 获取默认打开的入口 ID。 */
    public static String get()
    {
        if (BBSAddonsSettings.morphingDefaultCategory == null)
        {
            return HOME;
        }

        String value = BBSAddonsSettings.morphingDefaultCategory.get();

        return value == null || value.isEmpty() ? HOME : value;
    }

    /** 判断指定入口是否已经是默认打开入口。 */
    public static boolean isDefault(String id)
    {
        return get().equals(id == null || id.isEmpty() ? HOME : id);
    }

    /** 设置默认打开入口。 */
    public static void set(String id)
    {
        if (BBSAddonsSettings.morphingDefaultCategory != null)
        {
            BBSAddonsSettings.morphingDefaultCategory.set(id == null || id.isEmpty() ? HOME : id);
        }
    }
}
