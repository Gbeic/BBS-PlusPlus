package gbeic.bbsplusplus.mixin;

import gbeic.bbsplusplus.client.ui.film.IFilmLibrarySearchBox;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.input.text.UITextbox;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 为影片库搜索框补充 Esc 清空搜索的行为。
 * <p>
 * 原版文本框在聚焦时按 Esc 会直接失焦；影片库中用户更常见的意图是先清空搜索条件。
 * 该 Mixin 通过实例标记启用，只影响影片库搜索框，不改变其它文本框行为。
 * </p>
 */
@Mixin(UITextbox.class)
public abstract class UITextboxFilmLibrarySearchMixin implements IFilmLibrarySearchBox
{
    @Unique private boolean bbspp$filmLibrarySearchBox;

    @Override
    public void bbspp$setFilmLibrarySearchBox(boolean enabled)
    {
        this.bbspp$filmLibrarySearchBox = enabled;
    }

    /**
     * 注入目标：{@link UITextbox#subKeyPressed(UIContext)} 开始处。
     * 注入原因：原版 Esc 只会失焦，无法在搜索框内快速清空过滤条件。
     * 修改后的行为：影片库搜索框聚焦且有内容时，Esc 先清空搜索并消费按键。
     */
    @Inject(method = "subKeyPressed", at = @At("HEAD"), cancellable = true, remap = false)
    private void bbspp$clearFilmLibrarySearchOnEscape(UIContext context, CallbackInfoReturnable<Boolean> cir)
    {
        UITextbox self = (UITextbox) (Object) this;

        if (this.bbspp$filmLibrarySearchBox && self.isFocused() && context.isPressed(GLFW.GLFW_KEY_ESCAPE) && !self.getText().isEmpty())
        {
            self.setText("");

            if (self.callback != null)
            {
                self.callback.accept("");
            }

            cir.setReturnValue(true);
        }
    }
}
