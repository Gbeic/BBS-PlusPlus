package gbeic.bbsplusplus.mixin;

import gbeic.bbsplusplus.utils.SettingsFileSanitizer;
import mchorse.bbs_mod.settings.Settings;
import mchorse.bbs_mod.settings.SettingsManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.io.File;

/**
 * BBS 设置加载前的异常颜色配置修复补丁。
 * <p>
 * BBSFS 2.4 的颜色列表读取逻辑仍会在设置重载时把文件里的颜色追加到旧列表后面，
 * 导致 {@code recent_colors} 和 {@code favorite_colors} 被重复写入。这个补丁在设置解析前
 * 先用流式方式瘦身主设置文件，避免巨大配置文件拖慢或卡死客户端。
 * </p>
 */
@Mixin(SettingsManager.class)
public abstract class SettingsManagerMixin
{
    /**
     * 注入目标：{@code SettingsManager#load(Settings, File)} 入口。
     * 注入原因：异常巨大的颜色数组必须在 {@code DataToString.read(file)} 完整解析之前处理。
     * 修改行为：仅对 BBS 主设置文件执行最近颜色和收藏颜色瘦身，其它设置文件保持原样。
     */
    @Inject(method = "load", at = @At("HEAD"), remap = false)
    private void bbspp$sanitizeHugeColorLists(Settings settings, File file, CallbackInfoReturnable<Boolean> cir)
    {
        if (settings != null && "bbs".equals(settings.getId()))
        {
            SettingsFileSanitizer.sanitizeBbsSettings(file);
        }
    }
}
