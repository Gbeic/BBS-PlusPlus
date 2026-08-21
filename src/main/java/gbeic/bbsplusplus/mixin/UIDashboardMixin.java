package gbeic.bbsplusplus.mixin;

import mchorse.bbs_mod.ui.dashboard.UIDashboard;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = UIDashboard.class, remap = false)
public class UIDashboardMixin {

    /**
     * 当关闭 BBS 总控制面板时，统一清理所有缩略图缓存，释放内存
     */
    @Inject(method = "closeMenu", at = @At("HEAD"))
    private void onCloseMenu(CallbackInfo ci) {
        clearTextureThumbnailsSafely();
    }

    private static void clearTextureThumbnailsSafely() {
        try {
            Class<?> manager = Class.forName("gbeic.bbsplusplus.utils.TextureThumbnailManager");

            manager.getMethod("clear").invoke(null);
        } catch (ClassNotFoundException ignored) {
            // 兼容旧运行环境：缺少缩略图管理器时跳过清理，不能阻断 ESC 关闭界面。
        } catch (Throwable throwable) {
            throwable.printStackTrace();
        }
    }
}
