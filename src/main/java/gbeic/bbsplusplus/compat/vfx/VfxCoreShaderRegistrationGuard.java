package gbeic.bbsplusplus.compat.vfx;

import net.fabricmc.fabric.api.client.rendering.v1.CoreShaderRegistrationCallback;

/**
 * 限制 VFX 核心着色器回调在每次资源重载中只执行一次。
 * 当前 Fabric/BBS 渲染加载链可能重复触发同一个注册事件，导致相同着色器程序被构造数十轮；
 * 这里用重载代次包装第三方回调，既保留后续资源重载能力，也跳过同一轮中的重复调用。
 */
public final class VfxCoreShaderRegistrationGuard
{
    private static int generation;

    private VfxCoreShaderRegistrationGuard()
    {}

    public static void beginReload()
    {
        generation++;
    }

    public static CoreShaderRegistrationCallback wrap(CoreShaderRegistrationCallback callback)
    {
        return new CoreShaderRegistrationCallback()
        {
            private int lastGeneration = Integer.MIN_VALUE;

            @Override
            public void registerShaders(RegistrationContext context) throws java.io.IOException
            {
                if (this.lastGeneration == generation)
                {
                    return;
                }

                this.lastGeneration = generation;
                callback.registerShaders(context);
            }
        };
    }
}
