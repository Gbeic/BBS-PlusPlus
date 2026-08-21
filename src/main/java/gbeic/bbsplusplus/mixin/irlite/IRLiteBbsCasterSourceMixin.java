package gbeic.bbsplusplus.mixin.irlite;

import gbeic.bbsplusplus.client.renderer.ItemSprayFormRenderer;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 给 IR Lights 的 BBS 阴影投射源追加物品喷射兼容。
 *
 * IR Lights 没有公开注册额外 {@code ShadowCasterSource} 的入口，而 BBS++ 也不能把 IRL 设为硬依赖。
 * 因此这里使用软目标 Mixin：当 IRL 存在时，把物品喷射快照追加到它的 occluder 收集结果里，并在绘制阶段拦截
 * BBS++ 自己的 caster；当 IRL 不存在时，这个 Mixin 配置会被安全跳过。
 */
@Pseudo
@Mixin(targets = "qualet.irlite.client.light.shadow.IRLiteBbsCasterSource", remap = false)
public abstract class IRLiteBbsCasterSourceMixin
{
    /**
     * 注入目标：IRLiteBbsCasterSource.collect(...) 尾部。
     *
     * IRL 原本只收集实体、模型方块和回放；物品喷射由 BBS++ 的全局渲染钩子独立绘制，不属于这些来源。
     * 在原收集流程结束后追加物品喷射快照，可以让 IRL 阴影烘焙看到它们，同时不改变主世界渲染 pass。
     */
    @Inject(method = "collect", at = @At("TAIL"), remap = false, require = 0)
    private void bbspp$collectItemSprayCasters(ClientLevel world, Vec3 camPos, float tickDelta, @Coerce Object sink, CallbackInfo ci)
    {
        ItemSprayFormRenderer.collectIRLiteShadowCasters(world, camPos, tickDelta, sink);
    }

    /**
     * 注入目标：IRLiteBbsCasterSource.emitOccluder(...) 头部。
     *
     * IRL 的原方法只认识自己的三种 caster 类型；BBS++ 追加的物品喷射 caster 必须在这里拦截并自行绘制，
     * 否则原方法会把它按实体/模型方块强转。非 BBS++ caster 会继续走 IRL 原逻辑。
     */
    @Inject(method = "emitOccluder", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void bbspp$emitItemSprayCaster(Object caster, int type, float tickDelta, @Coerce Object batch, CallbackInfo ci)
    {
        if (ItemSprayFormRenderer.renderIRLiteShadowCaster(caster, tickDelta, batch))
        {
            ci.cancel();
        }
    }
}
