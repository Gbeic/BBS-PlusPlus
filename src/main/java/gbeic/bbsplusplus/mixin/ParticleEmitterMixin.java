package gbeic.bbsplusplus.mixin;

import mod.chloeprime.aaaparticles.api.client.effekseer.ParticleEmitter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/**
 * 混入 ParticleEmitter 类，添加一个标志位用于控制是否启用穿透方块（X-Ray）功能。
 * <p>
 * 由于 BBS 的粒子系统设计没有直接支持穿透方块的选项，我们通过在 ParticleEmitter 上添加一个标志位，
 * 并在渲染时根据该标志动态修改渲染层级来实现该功能。
 * </p>
 */

@Mixin(value = ParticleEmitter.class, remap = false)
public class ParticleEmitterMixin implements gbeic.bbsplusplus.utils.IIgnoreDepth {
    @Unique
    private boolean bbspp_ignoreDepth = false;

    @Override
    public boolean bbspp$getIgnoreDepth() {
        return this.bbspp_ignoreDepth;
    }

    @Override
    public void bbspp$setIgnoreDepth(boolean ignoreDepth) {
        this.bbspp_ignoreDepth = ignoreDepth;
    }

    @org.spongepowered.asm.mixin.Shadow
    private boolean isVisible;

    @Override
    public boolean bbspp$getOriginalVisibility() {
        return this.isVisible;
    }
}
