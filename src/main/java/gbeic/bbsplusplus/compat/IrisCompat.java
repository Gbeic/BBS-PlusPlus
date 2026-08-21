package gbeic.bbsplusplus.compat;

import net.fabricmc.loader.api.FabricLoader;

/**
 * Iris 光影模组的存在性检测。
 * <p>
 * BBS++ 里有几处功能（日月偏角、焦点两条光影曲线）需要直接注入 Iris 自身的类，
 * 没装 Iris 时这些注入必须跳过，相关的界面入口也不该显示出来。
 * </p>
 */
public final class IrisCompat
{
    public static final String IRIS_MOD_ID = "iris";

    private static Boolean loaded;

    private IrisCompat()
    {}

    /**
     * 是否装了 Iris。
     * <p>
     * 结果会缓存，因为模组列表在运行期不会变化，而这个判断会在 Mixin 插件里被频繁调用。
     * </p>
     */
    public static boolean isLoaded()
    {
        if (loaded == null)
        {
            loaded = FabricLoader.getInstance().isModLoaded(IRIS_MOD_ID);
        }

        return loaded;
    }
}
