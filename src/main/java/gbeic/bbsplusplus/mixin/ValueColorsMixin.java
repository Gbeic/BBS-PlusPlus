package gbeic.bbsplusplus.mixin;

import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.data.types.ListType;
import mchorse.bbs_mod.settings.values.ui.ValueColors;
import mchorse.bbs_mod.utils.colors.Color;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 颜色设置读取保护补丁。
 * <p>
 * BBSFS 2.4 的 {@link ValueColors#fromData(BaseType)} 读取配置时不会清空旧列表。
 * 如果设置被重复加载，收藏颜色会持续叠加；最近颜色虽然有本体上限，也会在读取阶段先被追加。
 * 这个补丁让颜色列表每次都按文件内容重新构建，并对重复色值和异常数量做保护。
 * </p>
 */
@Mixin(ValueColors.class)
public abstract class ValueColorsMixin
{
    @Unique
    private static final int BBSPLUSPLUS_MAX_RECENT_COLORS = 33;

    @Unique
    private static final int BBSPLUSPLUS_MAX_FAVORITE_COLORS = 256;

    @Shadow
    private List<Color> colors;

    /**
     * 注入目标：{@code ValueColors#fromData(BaseType)} 入口。
     * 注入原因：原逻辑会在已有列表后继续追加颜色，导致设置重载后颜色列表重复膨胀。
     * 修改行为：读取前清空旧值，按颜色值去重，并限制最近颜色和收藏颜色的最大数量。
     */
    @Inject(method = "fromData", at = @At("HEAD"), cancellable = true, remap = false)
    private void bbspp$readBoundedColors(BaseType data, CallbackInfo ci)
    {
        this.colors.clear();

        if (!BaseType.isList(data))
        {
            ci.cancel();

            return;
        }

        ListType list = (ListType) data;

        if (this.bbspp$isRecentColors())
        {
            this.bbspp$loadRecentColors(list);
        }
        else
        {
            this.bbspp$loadFavoriteColors(list);
        }

        ci.cancel();
    }

    /**
     * 注入目标：{@code ValueColors#addColor(Color)} 结束处。
     * 注入原因：运行期添加颜色后也需要维持安全上限，避免异常旧配置继续占用界面空间。
     * 修改行为：追加后移除超出上限的旧颜色。
     */
    @Inject(method = "addColor", at = @At("TAIL"), remap = false)
    private void bbspp$trimAfterAdd(Color color, CallbackInfo ci)
    {
        int limit = this.bbspp$isRecentColors() ? BBSPLUSPLUS_MAX_RECENT_COLORS : BBSPLUSPLUS_MAX_FAVORITE_COLORS;

        while (this.colors.size() > limit)
        {
            this.colors.remove(0);
        }
    }

    @Unique
    private boolean bbspp$isRecentColors()
    {
        return "recent_colors".equals(((ValueColors) (Object) this).getId());
    }

    @Unique
    private void bbspp$loadRecentColors(ListType list)
    {
        Set<Integer> seen = new HashSet<>();
        List<Integer> values = new ArrayList<>();

        for (int i = list.size() - 1; i >= 0 && values.size() < BBSPLUSPLUS_MAX_RECENT_COLORS; i--)
        {
            BaseType color = list.get(i);

            if (color != null && color.isNumeric())
            {
                int value = color.asNumeric().intValue();

                if (seen.add(value))
                {
                    values.add(value);
                }
            }
        }

        for (int i = values.size() - 1; i >= 0; i--)
        {
            this.colors.add(new Color().set(values.get(i)));
        }
    }

    @Unique
    private void bbspp$loadFavoriteColors(ListType list)
    {
        Set<Integer> seen = new HashSet<>();

        for (BaseType color : list)
        {
            if (this.colors.size() >= BBSPLUSPLUS_MAX_FAVORITE_COLORS)
            {
                break;
            }

            if (color != null && color.isNumeric())
            {
                int value = color.asNumeric().intValue();

                if (seen.add(value))
                {
                    this.colors.add(new Color().set(value));
                }
            }
        }
    }
}
