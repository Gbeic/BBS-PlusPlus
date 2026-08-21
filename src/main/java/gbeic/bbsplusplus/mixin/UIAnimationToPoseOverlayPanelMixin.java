package gbeic.bbsplusplus.mixin;

import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.film.replays.overlays.UIAnimationToPoseOverlayPanel;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.UIScrollView;
import mchorse.bbs_mod.ui.framework.elements.input.list.UISearchList;
import mchorse.bbs_mod.ui.framework.elements.input.list.UIStringList;
import mchorse.bbs_mod.ui.utils.UI;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 为 {@link UIAnimationToPoseOverlayPanel}（动画转姿势关键帧窗口）的
 * 动画列表添加搜索过滤功能。
 * <p>
 * BBS 2.2 原版使用 {@link UIStringList} 直接列出所有动画，当动画较多时
 * 难以查找。此 Mixin 将其替换为 {@link UISearchList}，在列表上方添加
 * 搜索文本框，支持输入过滤。
 * </p>
 */
@Mixin(UIAnimationToPoseOverlayPanel.class)
public class UIAnimationToPoseOverlayPanelMixin
{
    @Unique
    private UISearchList<String> bbspp_searchList;

    /**
     * 当构造器中调用 {@link UI#scrollView(int, int, UIElement...)} 时，
     * 拦截该方法调用，将原本传入的 {@link UIStringList} 替换为
     * {@link UISearchList} 包装后的版本。
     */
    @Redirect(
        method = "<init>",
        at = @At(
            value = "INVOKE",
            target = "Lmchorse/bbs_mod/ui/utils/UI;scrollView(II[Lmchorse/bbs_mod/ui/framework/elements/UIElement;)Lmchorse/bbs_mod/ui/framework/elements/UIScrollView;"
        ),
        remap = false
    )
    private UIScrollView wrapListWithSearchList(int marginX, int marginY, UIElement[] elements)
    {
        UIAnimationToPoseOverlayPanel self = (UIAnimationToPoseOverlayPanel) (Object) this;

        for (int i = 0; i < elements.length; i++)
        {
            if (elements[i] == self.list)
            {
                /* 用 UISearchList 包裹 UIStringList，添加搜索过滤 */
                this.bbspp_searchList = new UISearchList<>(self.list);
                this.bbspp_searchList.label(UIKeys.GENERAL_SEARCH);
                this.bbspp_searchList.h(UIStringList.DEFAULT_HEIGHT * 6 + 20);

                elements[i] = this.bbspp_searchList;
                break;
            }
        }

        return UI.scrollView(marginX, marginY, elements);
    }
}
