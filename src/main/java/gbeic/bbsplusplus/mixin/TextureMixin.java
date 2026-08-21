package gbeic.bbsplusplus.mixin;

import mchorse.bbs_mod.graphics.texture.Texture;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 修复 BBS 纹理过滤状态偶尔无法恢复为像素边缘的问题。
 * <p>
 * 原版会把同一个过滤参数同时写入放大过滤和缩小过滤，但 OpenGL 的放大过滤不接受 mipmap 参数。
 * 这里在写入纹理状态时拆分 MAG 与 MIN：MAG 只使用最近邻或线性，MIN 才保留 mipmap 选项。
 * </p>
 */
@Mixin(value = Texture.class, remap = false)
public abstract class TextureMixin
{
    @Shadow
    private int filter;

    @Shadow
    public abstract boolean isMipmap();

    @Shadow
    public abstract void generateMipmap();

    @Shadow
    public abstract void setParameter(int param, int value);

    /**
     * 注入目标：Texture#setFilter(int)。
     * 原版把 mipmap 过滤值也写入 GL_TEXTURE_MAG_FILTER，会产生无效状态；这里改为按 OpenGL 规则分别设置，
     * 让“线性过滤”关闭时可以稳定恢复最近邻像素边缘。
     */
    @Inject(method = "setFilter", at = @At("HEAD"), cancellable = true)
    private void bbspp$setSeparatedTextureFilters(int filter, CallbackInfo ci)
    {
        this.filter = filter;

        this.setParameter(GL11.GL_TEXTURE_MAG_FILTER, bbspp$getMagnificationFilter(filter));
        this.setParameter(GL11.GL_TEXTURE_MIN_FILTER, filter);

        ci.cancel();
    }

    /**
     * 注入目标：Texture#setFilterMipmap(boolean, boolean)。
     * 原版在启用 mipmap 时仍只记录普通线性/最近邻过滤值，容易让 UI 与实际 GL 状态不同步；
     * 这里直接记录并应用完整的缩小过滤模式，然后用 MAX_LEVEL 控制 mipmap 是否真正参与采样。
     */
    @Inject(method = "setFilterMipmap", at = @At("HEAD"), cancellable = true)
    private void bbspp$setConsistentFilterMipmap(boolean linear, boolean mipmap, CallbackInfo ci)
    {
        if (!this.isMipmap())
        {
            this.generateMipmap();
        }

        int filter = bbspp$getMinificationFilter(linear, mipmap);

        this.setParameter(GL30.GL_TEXTURE_MAX_LEVEL, mipmap ? 4 : 0);
        bbspp$setSeparatedTextureFilters(filter, ci);
    }

    private static int bbspp$getMinificationFilter(boolean linear, boolean mipmap)
    {
        if (mipmap)
        {
            return linear ? GL30.GL_LINEAR_MIPMAP_NEAREST : GL30.GL_NEAREST_MIPMAP_NEAREST;
        }

        return linear ? GL11.GL_LINEAR : GL11.GL_NEAREST;
    }

    private static int bbspp$getMagnificationFilter(int filter)
    {
        return switch (filter)
        {
            case GL11.GL_LINEAR, GL30.GL_LINEAR_MIPMAP_NEAREST, GL30.GL_LINEAR_MIPMAP_LINEAR -> GL11.GL_LINEAR;
            default -> GL11.GL_NEAREST;
        };
    }
}
