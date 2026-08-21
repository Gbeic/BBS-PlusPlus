package gbeic.bbsplusplus.mixin;

import mchorse.bbs_mod.audio.SoundLikeManager;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIIcon;
import mchorse.bbs_mod.ui.framework.elements.input.list.UILikeableStringList;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.function.Consumer;

/**
 * Accessor Mixin — 在运行时为 {@link UILikeableStringList} 的私有字段添加公开 getter 方法。
 */
@Mixin(UILikeableStringList.class)
public interface UILikeableStringListAccessor
{
    @Accessor("likeManager") SoundLikeManager getLikeManager();
    @Accessor("showOnlyLiked") boolean getShowOnlyLiked();
    @Accessor("likeButton") UIIcon getLikeButton();
    @Accessor("editButton") UIIcon getEditButton();
    @Accessor("removeButton") UIIcon getRemoveButton();
    @Accessor("refreshCallback") Runnable getRefreshCallback();
    @Accessor("editCallback") Consumer<String> getEditCallback();
    @Accessor("removeCallback") Consumer<String> getRemoveCallback();
    @Accessor("showEditRemoveButtons") boolean getShowEditRemoveButtons();
}
