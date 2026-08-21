package gbeic.bbsplusplus.mixin;

import gbeic.bbsplusplus.ui.list.UIAudioTreeList;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;

import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.audio.SoundLikeManager;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.ui.framework.elements.input.list.UILikeableStringList;
import mchorse.bbs_mod.ui.framework.elements.overlay.UISoundOverlayPanel;

/**
 * Mixin — 将 {@link UISoundOverlayPanel} 中的普通列表替换为 {@link UIAudioTreeList} 树形列表。
 */
@Mixin(UISoundOverlayPanel.class)
public abstract class UISoundOverlayPanelMixin
{
    @Shadow(remap = false)
    private String selectedSound;

    @Shadow(remap = false)
    private void refreshLikedList()
    {
        throw new AssertionError("Mixin not applied");
    }

    /* ======================
     *   构造器：替换列表类型
     * ====================== */

    @Redirect(
        method = "<init>(Ljava/util/function/Consumer;Lmchorse/bbs_mod/ui/framework/UIContext;)V",
        at = @At(
            value = "NEW",
            target = "Lmchorse/bbs_mod/ui/framework/elements/input/list/UILikeableStringList;"
        ),
        remap = false
    )
    private UILikeableStringList onCreateLikeableList(
        Consumer<java.util.List<String>> callback,
        SoundLikeManager likeManager
    )
    {
        return new UIAudioTreeList(callback, likeManager);
    }

    /* ======================
     *   refreshSoundList：使用 setAudioPaths
     * ====================== */

    @Inject(
        method = "refreshSoundList",
        at = @At("HEAD"),
        cancellable = true,
        remap = false
    )
    private void onRefreshSoundList(CallbackInfo ci)
    {
        UISoundOverlayPanel self = (UISoundOverlayPanel) (Object) this;
        Set<String> soundEvents = getSoundEvents();

        UIAudioTreeList list = (UIAudioTreeList) self.strings.list;
        list.setAudioPaths(soundEvents, true);

        String filter = self.strings.search.getText();
        self.strings.filter(filter, true);
        self.strings.resize();
        this.refreshLikedList();

        ci.cancel();
    }

    /* ======================
     *   updateListSelections：展开选中路径
     * ====================== */

    @Inject(
        method = "updateListSelections",
        at = @At("HEAD"),
        remap = false
    )
    private void onUpdateListSelections(CallbackInfo ci)
    {
        UISoundOverlayPanel self = (UISoundOverlayPanel) (Object) this;

        if (this.selectedSound != null && self.strings.list instanceof UIAudioTreeList treeList)
        {
            treeList.expandToShow(this.selectedSound);
        }
    }

    /* ======================
     *   工具方法
     * ====================== */

    @Unique
    private static Set<String> getSoundEvents()
    {
        Set<String> locations = new HashSet<>();

        for (Link link : BBSMod.getProvider().getLinksFromPath(Link.assets("audio")))
        {
            String pathLower = link.path.toLowerCase();
            boolean supported = pathLower.endsWith(".wav") || pathLower.endsWith(".ogg");

            if (supported)
            {
                locations.add(link.toString());
            }
        }

        return locations;
    }
}
