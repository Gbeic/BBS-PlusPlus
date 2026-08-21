package gbeic.bbsplusplus.mixin;

import gbeic.bbsplusplus.client.settings.VideoSettingsPresets;
import mchorse.bbs_mod.l10n.L10n;
import mchorse.bbs_mod.settings.ui.UISettingsOverlayPanel;
import mchorse.bbs_mod.settings.values.core.ValueGroup;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIIcon;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.Direction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 向 BBS 视频设置页的标题栏追加 BBS++ 独立预设按钮。
 * <p>
 * BBS 自带按钮继续负责内置分辨率预设，新增按钮只打开 BBS++ 自己的完整
 * 视频设置预设目录，因此两套预设的保存范围和文件位置彼此独立。
 * </p>
 */
@Mixin(targets = "mchorse.bbs_mod.settings.ui.UISettingsOverlayPanel$UISectionHeader")
public class UISettingsVideoPresetMixin
{
    /**
     * 注入目标：BBS 视频设置标题栏构造完成之后。
     * 注入原因：在“对调宽度和高度”右侧增加 BBS++ 的完整视频设置预设入口，
     * 同时保留 BBS 原有的内置分辨率预设按钮。
     * 修改行为：扩展标题栏按钮行，并插入只使用 BBS++ 预设目录的按钮。
     */
    @Inject(method = "<init>", at = @At("TAIL"))
    private void bbsplusplus$addVideoSettingsPresets(UISettingsOverlayPanel panel, ValueGroup category, CallbackInfo ci)
    {
        if (!"video".equals(category.getId()))
        {
            return;
        }

        UIElement header = (UIElement) (Object) this;

        if (header.getChildren().isEmpty() || !(header.getChildren().get(0) instanceof UIElement row))
        {
            return;
        }

        var controller = VideoSettingsPresets.createController(panel);
        UIIcon presets = new UIIcon(Icons.FILE, (button) ->
        {
            UIContext context = button.getContext();
            VideoSettingsPresets.openPresets(context, controller);
        });

        presets.tooltip(L10n.lang("bbspp.ui.video_settings.presets"), Direction.LEFT);
        presets.wh(16, 16);

        if (!row.getChildren().isEmpty())
        {
            row.addAfter(row.getChildren().get(0), presets);
        }
        else
        {
            row.add(presets);
        }

        row.w(48);
    }
}
