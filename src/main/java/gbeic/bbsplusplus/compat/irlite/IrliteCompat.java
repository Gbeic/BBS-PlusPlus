package gbeic.bbsplusplus.compat.irlite;

import net.fabricmc.loader.api.FabricLoader;

/**
 * IRLite 模组的存在性检测。
 * <p>
 * BBS++ 需要把新版 IRLite（mod_id {@code irlite}）把光影参数迁进 BBS 设置后丢失的参数
 * 重新接入光影曲线系统。没装 IRLite 时相关桥接必须整体跳过，界面入口也不该显示出来。
 * 检测结果缓存：模组列表在运行期不会变化，而这个判断会被频繁调用。
 * </p>
 */
public final class IrliteCompat
{
    public static final String IRLITE_MOD_ID = "irlite";

    private static Boolean loaded;

    private IrliteCompat()
    {}

    /**
     * 是否装了 IRLite。
     */
    public static boolean isLoaded()
    {
        if (loaded == null)
        {
            loaded = FabricLoader.getInstance().isModLoaded(IRLITE_MOD_ID);
        }

        return loaded;
    }
}
