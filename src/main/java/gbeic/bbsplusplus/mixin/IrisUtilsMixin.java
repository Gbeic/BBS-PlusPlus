package gbeic.bbsplusplus.mixin;

import gbeic.bbsplusplus.client.compat.iris.SafeShaderLanguageMap;
import gbeic.bbsplusplus.compat.irlite.IrliteCompat;
import gbeic.bbsplusplus.compat.irlite.IrliteShaderCurveBridge;
import mchorse.bbs_mod.utils.iris.ShaderCurves;
import net.irisshaders.iris.gl.uniform.UniformUpdateFrequency;
import net.irisshaders.iris.uniforms.custom.cached.CachedUniform;
import net.irisshaders.iris.uniforms.custom.cached.FloatCachedUniform;
import net.irisshaders.iris.uniforms.custom.cached.IntCachedUniform;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;
import java.util.Map;

/**
 * 修复 BBS 原版 Iris 光影设置路径收集在循环菜单中无限递归的问题。
 * <p>
 * 另：把桥接注入的 IRLite 参数从 Iris 的 uniform 注册中过滤掉——这些参数由 IRLite 自己的
 * UBO 驱动，shader 里没有对应的 {@code bbs_*} uniform。Iris 的 {@code CustomUniforms.update()}
 * 每帧会遍历全部已注册 CachedUniform 并调用供给函数排空 {@code ShaderVariable.value}，
 * 不过滤的话曲线桥每帧都读到 null，曲线会完全失效。
 * </p>
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

    /**
     * 注入目标：{@code IrisUtils#addUniforms(List, Map)} 入口。
     * 注入原因：让桥接注入的 IRLite 参数不注册成 Iris uniform，避免 Iris 每帧消费并清空曲线值。
     * 修改行为：装了 IRLite 时重新实现原循环、跳过 IRLite 变量；未装 IRLite 走原逻辑。
     */
    @Inject(method = "addUniforms", at = @At("HEAD"), cancellable = true, remap = false)
    private static void bbspp$skipIrLiteUniforms(
        List<CachedUniform> list,
        Map<String, ShaderCurves.ShaderVariable> variableMap,
        CallbackInfo ci
    )
    {
        if (!IrliteCompat.isLoaded())
        {
            return;
        }

        for (ShaderCurves.ShaderVariable value : variableMap.values())
        {
            if (IrliteShaderCurveBridge.isIrLiteVariable(value.name))
            {
                continue;
            }

            if (value.integer)
            {
                list.add(new IntCachedUniform(value.uniformName, UniformUpdateFrequency.PER_FRAME, () -> (int) value.getValue()));
            }
            else
            {
                list.add(new FloatCachedUniform(value.uniformName, UniformUpdateFrequency.PER_FRAME, value::getValue));
            }
        }

        ci.cancel();
    }
}
