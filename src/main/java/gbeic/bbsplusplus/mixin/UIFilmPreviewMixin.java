package gbeic.bbsplusplus.mixin;

import mchorse.bbs_mod.l10n.L10n;
import mchorse.bbs_mod.ui.dashboard.panels.UIDashboardPanels;
import mchorse.bbs_mod.ui.film.UIFilmPanel;
import mchorse.bbs_mod.ui.film.UIFilmPreview;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIIcon;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.Direction;
import gbeic.bbsplusplus.BBSAddonsSettings;
import gbeic.bbsplusplus.util.IrisHelper;
import net.fabricmc.loader.api.FabricLoader;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(UIFilmPreview.class)
public abstract class UIFilmPreviewMixin {

    @Shadow @Final public UIElement icons;

    @Inject(method = "<init>", at = @At("RETURN"), remap = false)
    private void onInit(UIFilmPanel panel, CallbackInfo ci) {
        if (FabricLoader.getInstance().isModLoaded("iris")) {
            UIIcon irisButton = new UIIcon(Icons.SUN, null) {
                @Override
                protected boolean isAllowed(int mouseButton) {
                    if (BBSAddonsSettings.enableIrisButton != null && !BBSAddonsSettings.enableIrisButton.get()) return false;
                    return mouseButton == 0 || mouseButton == 1;
                }

                @Override
                protected void click(int mouseButton) {
                    if (mouseButton == 0) {
                        IrisHelper.toggleShaders();
                    } else if (mouseButton == 1) {
                        IrisHelper.openShaderScreen();
                    }
                    super.click(mouseButton);
                }

                /**
                 * 上一次应用过的开关状态，null 表示尚未初始化。
                 * <p>
                 * 不能用 {@code area.w} 反推当前状态：本按钮位于 {@code UI.row} 布局中，而
                 * {@code RowResizer} 把「宽度约束为 0」视作「未指定宽度」，转而分配平均宽度
                 * （{@code cw = cw > 0 ? cw : w;}）。于是 {@code w(0)} 之后 {@code area.w}
                 * 依然不为 0，判断恒成立，导致每帧都调用一次 {@code getParent().resize()}。
                 * </p>
                 */
                private Boolean bbspp$appliedEnabled;

                @Override
                public void render(UIContext context) {
                    boolean enabled = BBSAddonsSettings.enableIrisButton != null && BBSAddonsSettings.enableIrisButton.get();

                    /* 只在开关真正发生变化时调整宽度并重排一次，避免每帧重排。 */
                    if (this.bbspp$appliedEnabled == null || this.bbspp$appliedEnabled != enabled) {
                        this.bbspp$appliedEnabled = enabled;
                        this.w(enabled ? 20 : 0);

                        if (this.getParent() != null) this.getParent().resize();
                    }

                    if (!enabled) {
                        return;
                    }

                    if (IrisHelper.isShadersEnabled()) {
                        UIDashboardPanels.renderHighlight(context.batcher, this.area, Direction.BOTTOM);
                    }
                    super.render(context);
                }
            };
            irisButton.tooltip(L10n.lang("bbs.config.bbspp.iris_button_tooltip"));
            this.icons.add(irisButton);
        }
    }
}
