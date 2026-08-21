package gbeic.bbsplusplus.mixin;

import mchorse.bbs_mod.graphics.texture.Texture;
import mchorse.bbs_mod.graphics.texture.TextureManager;
import mchorse.bbs_mod.resources.AssetProvider;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.utils.resources.Pixels;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.io.File;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * TextureManager 的稳定性修复。
 *
 * 1. 在 TextureManager 的 getTexture 方法头部添加一个注入，如果传入的 Link 为 null，则直接返回错误纹理。这可以防止在某些情况下由于 Link 解析失败而导致的 NullPointerException。
 * 2. 在 TextureManager 的 getPixels 方法头部添加一个注入，如果传入的 Link 为 null，则直接返回 null。这同样可以防止由于 Link 解析失败而导致的异常。
 * 3. 通过这种方式，用户在使用 BBS++ 的新版本时，无需担心由于资源链接问题导致的崩溃，即使在旧版本中创建了包含无效链接的录像文件，在新版本中也会自动处理这些情况。
 */

@Mixin(TextureManager.class)
public abstract class TextureManagerMixin
{
    @Shadow
    public abstract Texture getError();

    @Shadow
    @Final
    public Map<Link, Texture> textures;

    @Shadow
    public AssetProvider provider;

    /** 每个 PBR 伴随贴图上次做存在性检查的时间戳，用于给磁盘访问节流。 */
    @Unique
    private static final Map<Link, Long> bbspp$pbrRetryStamps = new HashMap<>();

    /** PBR 伴随贴图的重试间隔。贴图是外部写入的，秒级延迟足够，不需要每帧探测。 */
    @Unique
    private static final long bbspp$PBR_RETRY_INTERVAL_MS = 3000L;

    /**
     * 注入目标：TextureManager#getTexture(Link, int, boolean)。
     * 原版在静默加载 Iris PBR 贴图时，如果 *_n.png 等伴随贴图暂时不存在或正在写入，会把错误纹理缓存起来。
     * 这里在文件后来已经存在时清掉旧错误缓存，让法线/高光贴图可以自动重试加载。
     *
     * <p>
     * <b>必须节流</b>：开启光影后 Iris 会为每个用到的纹理请求 {@code _n}/{@code _s} 等伴随贴图，
     * 而模型皮肤通常并不带这些文件，于是它们会被长期缓存为错误纹理。此时若每次请求都执行
     * {@code getFile} + {@code exists}，就等于在渲染循环里做磁盘 IO——主渲染与阴影通道各来一遍，
     * 每帧累积几十次系统调用。因此这里按 {@link #bbspp$PBR_RETRY_INTERVAL_MS} 间隔限制探测频率。
     * </p>
     */
    @Inject(method = "getTexture(Lmchorse/bbs_mod/resources/Link;IZ)Lmchorse/bbs_mod/graphics/texture/Texture;", at = @At("HEAD"), cancellable = true, remap = false)
    private void onGetTexture(Link link, int filter, boolean silent, CallbackInfoReturnable<Texture> cir)
    {
        if (link == null)
        {
            cir.setReturnValue(this.getError());

            return;
        }

        if (silent && bbspp$isPBRSidecar(link) && this.textures.get(link) == this.getError())
        {
            long now = System.currentTimeMillis();
            Long last = bbspp$pbrRetryStamps.get(link);

            /* 距上次探测不足间隔时直接跳过，避免每帧访问磁盘。 */
            if (last != null && now - last < bbspp$PBR_RETRY_INTERVAL_MS)
            {
                return;
            }

            bbspp$pbrRetryStamps.put(link, now);

            File file = this.provider.getFile(link);

            if (file != null && file.exists())
            {
                this.textures.remove(link);
                bbspp$pbrRetryStamps.remove(link);
            }
        }
    }

    /**
     * 注入目标：TextureManager#getPixels(Link)。
     * 一些旧工程或异常资源会传入空 Link；原版会继续访问字段并崩溃，这里直接返回 null，
     * 交给上层使用错误纹理兜底。
     */
    @Inject(method = "getPixels", at = @At("HEAD"), cancellable = true, remap = false)
    private void onGetPixels(Link link, CallbackInfoReturnable<Pixels> cir)
    {
        if (link == null)
        {
            cir.setReturnValue(null);
        }
    }

    /**
     * 拦截原版 TextureManager 在加载纹理时疯狂输出的 "Texture ... was loaded!" 日志，防止刷屏。
     */
    @org.spongepowered.asm.mixin.injection.Redirect(
        method = "getTexture(Lmchorse/bbs_mod/resources/Link;IZ)Lmchorse/bbs_mod/graphics/texture/Texture;",
        at = @At(value = "INVOKE", target = "Ljava/io/PrintStream;println(Ljava/lang/String;)V"),
        remap = false
    )
    private void suppressTextureLoadLog(java.io.PrintStream instance, String x)
    {
        // 留空，吞掉日志，避免被刷屏
    }

    private static boolean bbspp$isPBRSidecar(Link link)
    {
        String path = link.path.toLowerCase(Locale.ROOT);

        return path.endsWith("_n.png")
            || path.endsWith("_s.png")
            || path.endsWith("_e.png")
            || path.endsWith("_normal.png")
            || path.endsWith("_specular.png")
            || path.endsWith("_emission.png");
    }
}
