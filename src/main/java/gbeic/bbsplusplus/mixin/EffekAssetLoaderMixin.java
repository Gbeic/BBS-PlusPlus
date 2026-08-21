package gbeic.bbsplusplus.mixin;

import mod.chloeprime.aaaparticles.api.client.EffectRegistry;
import mod.chloeprime.aaaparticles.client.loader.EffekAssetLoader;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import gbeic.bbsplusplus.client.renderer.BBSEffectLoader;

@Mixin(EffekAssetLoader.class)
public class EffekAssetLoaderMixin
{
    /**
     * 在重载并卸载所有旧的粒子资源前，必须强制停止并清理 C++ 渲染管线中正在播放的粒子实例。
     * 否则 C++ 原生层的 EffekseerManager 会试图继续渲染已经被 close() 并释放掉内存的粒子句柄，
     * 从而导致 EXCEPTION_ACCESS_VIOLATION (0xc0000005) 的致命原生崩溃。
     */
    @Inject(method = "unloadAll", at = @At("HEAD"), remap = false)
    private void beforeUnloadAll(CallbackInfo ci)
    {
        EffectRegistry.clearAllPlaying();
        
        // 同时必须清理 BBSPlusPlus 维护的特效定义缓存。
        // 因为 AAA Particles 在 unloadAll() 时会销毁所有被挂载的 EffectHolder。
        // 如果我们不清理 BBSEffectLoader 中的 definitionCache，那么在重载后，
        // 渲染器会拿到一个底层 C++ 内存已经被释放的空壳 EffectDefinition 进行播放，
        // 同样会导致 EXCEPTION_ACCESS_VIOLATION。
        BBSEffectLoader.markCacheDirty();
    }
}
