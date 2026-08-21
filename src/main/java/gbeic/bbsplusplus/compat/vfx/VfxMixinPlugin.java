package gbeic.bbsplusplus.compat.vfx;

import net.fabricmc.loader.api.FabricLoader;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

/**
 * 管理 BBSVFX 与 VFXLIGHTS 的可选兼容 Mixin。
 * 两组注入直接以可选模组类为目标，因此在目标模组未安装时必须于类解析前跳过。
 */
public class VfxMixinPlugin implements IMixinConfigPlugin
{
    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName)
    {
        if (mixinClassName.endsWith(".BbsVfxClientMixin"))
        {
            return FabricLoader.getInstance().isModLoaded("bbsvfx");
        }

        if (mixinClassName.endsWith(".VfxLightsClientMixin"))
        {
            return FabricLoader.getInstance().isModLoaded("vfxlights");
        }

        return false;
    }

    @Override
    public void onLoad(String mixinPackage)
    {}

    @Override
    public String getRefMapperConfig()
    {
        return null;
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
