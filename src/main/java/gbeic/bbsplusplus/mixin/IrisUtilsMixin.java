package gbeic.bbsplusplus.mixin;

import gbeic.bbsplusplus.client.compat.iris.SafeShaderLanguageMap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Map;

/**
 * 修复 BBS 原版 Iris 光影设置路径收集在循环菜单中无限递归的问题。
 */
@Mixin(targets = "mchorse.bbs_mod.utils.iris.IrisUtils")
public abstract class IrisUtilsMixin
{
    /**
     * 注入目标：{@code IrisUtils#getShadersLanguageMap(String)} 入口。
     * 注入原因：部分光影包的设置菜单存在循环链接，原版递归收集路径时没有访问链保护，会卡住并持续刷调用栈。
     * 修改行为：改用 BBS++ 的安全收集器，遇到循环或过深菜单时停止下钻，保持原版返回格式。
     */
    @Inject(method = "getShadersLanguageMap", at = @At("HEAD"), cancellable = true, remap = false)
    private static void bbspp$getShadersLanguageMapSafely(String language, CallbackInfoReturnable<Map<String, String>> cir)
    {
        cir.setReturnValue(SafeShaderLanguageMap.collect(language));
    }
}
