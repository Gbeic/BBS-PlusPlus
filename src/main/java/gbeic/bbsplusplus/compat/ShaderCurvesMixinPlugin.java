package gbeic.bbsplusplus.compat;

import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

/**
 * {@code bbsplusplus-shadercurves.mixins.json} 的 Mixin 配置插件。
 * <p>
 * 这一组注入里有几个直接以 Iris 自身的类为目标，没装 Iris 时必须在类加载前跳过，
 * 否则 Mixin 会因为找不到目标类而报错。
 * </p>
 */
public class ShaderCurvesMixinPlugin implements IMixinConfigPlugin
{
    /** 以 Iris 自身的类为注入目标的 Mixin，没装 Iris 时不能应用 */
    private static final Set<String> IRIS_ONLY_MIXINS = Set.of(
        "CelestialUniformsMixin",
        "CustomUniformsSetupAccessor",
        "IrisRenderingPipelineMixin",
        "ShadowRendererMixin",
        "CenterDepthSamplerMixin"
    );

    @Override
    public void onLoad(String mixinPackage)
    {}

    @Override
    public String getRefMapperConfig()
    {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName)
    {
        String simpleName = mixinClassName.substring(mixinClassName.lastIndexOf('.') + 1);

        if (IRIS_ONLY_MIXINS.contains(simpleName))
        {
            return IrisCompat.isLoaded();
        }

        return true;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets)
    {}

    @Override
    public List<String> getMixins()
    {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo)
    {}

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo)
    {}
}
