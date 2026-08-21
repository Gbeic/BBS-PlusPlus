package gbeic.bbsplusplus.mixin;

import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIIcon;
import mchorse.bbs_mod.ui.framework.elements.input.UITexturePicker;
import mchorse.bbs_mod.ui.framework.elements.input.list.UIFileLinkList;
import gbeic.bbsplusplus.BBSAddonsSettings;
import gbeic.bbsplusplus.ui.framework.elements.input.list.UIGridFileLinkList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.Consumer;

/**
 * 对 {@link UITexturePicker} 进行修改的 Mixin。
 * <p>
 * 主要是替换其内部的 {@link UIFileLinkList}，将其更换为我们自定义支持网格布局的 {@link UIGridFileLinkList}。
 * 并在顶部工具栏中注入一个用于切换排版模式的按钮。
 * </p>
 */
@Mixin(value = UITexturePicker.class, remap = false)
public abstract class UITexturePickerMixin extends UIElement {

    @Shadow public UIElement right;
    @Shadow public UIFileLinkList picker;
    @Shadow protected abstract void selectCurrent(Link link);
    @Shadow public abstract void updateFolderButton();

    /**
     * 在构造函数结尾处注入，替换列表组件并添加切换按钮
     */
    @Inject(method = "<init>(Ljava/util/function/Consumer;)V", at = @At("TAIL"))
    private void onInit(Consumer<Link> callback, CallbackInfo ci) {
        UIFileLinkList oldPicker = this.picker;
        
        // 替换为自定义的网格列表
        this.picker = new UIGridFileLinkList(this::selectCurrent) {
            @Override
            public void setPath(Link folder, boolean fastForward) {
                super.setPath(folder, fastForward);
                UITexturePickerMixin.this.updateFolderButton();
            }
        };
        
        this.picker.filter((l) -> l.path.endsWith("/") || l.path.endsWith(".png")).cancelScrollEdge();
        this.picker.relative(this.right).set(10, 30, 0, 0).w(1, -10).h(1, -30);
        
        // 获取当前布局对应的图标
        int currentLayout = BBSAddonsSettings.textureManagerLayout.get();
        mchorse.bbs_mod.ui.utils.icons.Icon initialIcon = mchorse.bbs_mod.ui.utils.icons.Icons.LIST;
        if (currentLayout == 1) initialIcon = mchorse.bbs_mod.ui.utils.icons.Icons.STOP;
        else if (currentLayout == 2) initialIcon = mchorse.bbs_mod.ui.utils.icons.Icons.STOP;
        else if (currentLayout == 3) initialIcon = mchorse.bbs_mod.ui.utils.icons.Icons.STOP;

        UIIcon layoutToggle = new UIIcon(initialIcon, null);
        layoutToggle.callback = (b) -> {
            this.picker.getContext().replaceContextMenu((menu) -> {
                menu.action(mchorse.bbs_mod.ui.utils.icons.Icons.LIST, mchorse.bbs_mod.l10n.L10n.lang("bbs.ui.texture_manager_layout.list"), () -> {
                    BBSAddonsSettings.textureManagerLayout.set(0);
                    this.picker.resize();
                    this.picker.update();
                    layoutToggle.both(mchorse.bbs_mod.ui.utils.icons.Icons.LIST);
                });
                menu.action(mchorse.bbs_mod.ui.utils.icons.Icons.STOP, mchorse.bbs_mod.l10n.L10n.lang("bbs.ui.texture_manager_layout.small"), () -> {
                    BBSAddonsSettings.textureManagerLayout.set(1);
                    this.picker.resize();
                    this.picker.update();
                    layoutToggle.both(mchorse.bbs_mod.ui.utils.icons.Icons.STOP);
                });
                menu.action(mchorse.bbs_mod.ui.utils.icons.Icons.STOP, mchorse.bbs_mod.l10n.L10n.lang("bbs.ui.texture_manager_layout.medium"), () -> {
                    BBSAddonsSettings.textureManagerLayout.set(2);
                    this.picker.resize();
                    this.picker.update();
                    layoutToggle.both(mchorse.bbs_mod.ui.utils.icons.Icons.STOP);
                });
                menu.action(mchorse.bbs_mod.ui.utils.icons.Icons.STOP, mchorse.bbs_mod.l10n.L10n.lang("bbs.ui.texture_manager_layout.large"), () -> {
                    BBSAddonsSettings.textureManagerLayout.set(3);
                    this.picker.resize();
                    this.picker.update();
                    layoutToggle.both(mchorse.bbs_mod.ui.utils.icons.Icons.STOP);
                });
            });
        };
        layoutToggle.tooltip(mchorse.bbs_mod.l10n.L10n.lang("bbs.ui.texture_manager_layout_tooltip"));
        
        // 查找 icons 行组件，并添加新按钮
        UIElement icons = (UIElement) this.right.getChildren().get(0);
        
        // 将按钮插入到关闭按钮的前面（或者直接加在 icons 中）
        if (icons.getChildren().size() > 0) {
            icons.addBefore(icons.getChildren().get(icons.getChildren().size() - 1), layoutToggle);
            icons.w(icons.getChildren().size() * 20); // 动态更新宽度，防止按钮被挤压隐藏
        } else {
            icons.add(layoutToggle);
        }
        
        // 在右侧面板中替换旧的 picker
        this.right.addBefore(oldPicker, this.picker);
        oldPicker.removeFromParent();
    }

    @Inject(method = "cantBeClosed", at = @At("TAIL"), remap = false)
    private void onCantBeClosed(org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<mchorse.bbs_mod.ui.framework.elements.input.UITexturePicker> cir) {
        UIElement icons = (UIElement) this.right.getChildren().get(0);
        icons.w(icons.getChildren().size() * 20); // 重新调整宽度，消除因关闭按钮被移除导致的空隙
    }

    /**
     * 当独立的纹理选择器关闭时，清理异步加载的缩略图缓存
     */
    @Inject(method = "close", at = @At("HEAD"))
    private void onClose(CallbackInfo ci) {
        clearTextureThumbnailsSafely();
    }

    private static void clearTextureThumbnailsSafely() {
        try {
            Class<?> manager = Class.forName("gbeic.bbsplusplus.utils.TextureThumbnailManager");

            manager.getMethod("clear").invoke(null);
        } catch (ClassNotFoundException ignored) {
            // 兼容旧运行环境：缺少缩略图管理器时跳过清理，不能阻断界面关闭。
        } catch (Throwable throwable) {
            throwable.printStackTrace();
        }
    }
}
