package gbeic.bbsplusplus.api;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

import mchorse.bbs_mod.ui.utils.icons.Icon;

/**
 * 为依赖 Mod 提供关键帧轨道显示与排序扩展接口。
 *
 * <p>外部 Mod 只注册稳定属性 ID、默认英文名、中文名、样式参照轨道、直接颜色、直接图标和排序锚点；
 * BBS++ 统一负责时间轴汉化开关、颜色、图标和顺序应用，从而避免私有功能反向写入公开实现。</p>
 */
public class KeyframeTrackExtensionRegistry
{
    private static final Map<String, Extension> EXTENSIONS = new LinkedHashMap<>();

    public static void register(String id, String defaultName, String chineseName, String styleSource, Integer color, Icon icon, String before)
    {
        if (id == null || id.isEmpty())
        {
            return;
        }

        EXTENSIONS.put(id, new Extension(id, defaultName, chineseName, styleSource, color, icon, before));
    }

    public static Extension get(String id)
    {
        return EXTENSIONS.get(id);
    }

    public static Collection<Extension> getAll()
    {
        return EXTENSIONS.values();
    }

    public static String replaceTailWithDefaultName(String key)
    {
        if (key == null || key.isEmpty())
        {
            return key;
        }

        int slash = key.lastIndexOf('/');
        String prefix = slash < 0 ? "" : key.substring(0, slash + 1);
        String body = slash < 0 ? key : key.substring(slash + 1);
        Extension extension = get(body);

        return extension == null || extension.defaultName().isEmpty() ? key : prefix + extension.defaultName();
    }

    public record Extension(String id, String defaultName, String chineseName, String styleSource, Integer color, Icon icon, String before)
    {}
}
