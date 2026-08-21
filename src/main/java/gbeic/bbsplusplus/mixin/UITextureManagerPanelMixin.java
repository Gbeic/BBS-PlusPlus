package gbeic.bbsplusplus.mixin;

import gbeic.bbsplusplus.ui.framework.elements.input.list.UIGridFileLinkList;
import mchorse.bbs_mod.ui.dashboard.UIDashboard;
import mchorse.bbs_mod.ui.dashboard.textures.UITextureManagerPanel;
import mchorse.bbs_mod.ui.framework.elements.input.UITexturePicker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 主纹理管理器面板的行为补丁。
 * <p>
 * BBS++ 会全局替换 {@link UITexturePicker} 的内部列表以支持缩略图和网格排版，但 ESC 返回上一级目录
 * 只适合按 0 打开的仪表盘底部“纹理管理器”。这里在主面板构造完成后单独打开该能力，
 * 让其它位置弹出的纹理选择器继续沿用原版 ESC 关闭/退出逻辑。
 * </p>
 */
@Mixin(value = UITextureManagerPanel.class, remap = false)
public class UITextureManagerPanelMixin {
    @Shadow
    public UITexturePicker picker;

    /**
     * 注入目标：UITextureManagerPanel 构造函数尾部。
     * 主纹理管理器使用不可关闭的 {@link UITexturePicker}，原版 ESC 不会关闭它；
     * 因此只在这里开启列表层的 ESC 返回上一级目录，避免影响从表单、关键帧等入口打开的纹理选择器。
     */
    @Inject(method = "<init>(Lmchorse/bbs_mod/ui/dashboard/UIDashboard;)V", at = @At("TAIL"))
    private void bbspp$enableEscapeGoBack(UIDashboard dashboard, CallbackInfo ci) {
        if (this.picker.picker instanceof UIGridFileLinkList gridFileLinkList) {
            gridFileLinkList.escapeGoBackEnabled(true);
        }
    }
}
