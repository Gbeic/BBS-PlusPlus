package gbeic.bbsplusplus.compat;

import net.fabricmc.loader.api.FabricLoader;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

/**
 * {@code bbsplusplus-xavin.mixins.json} 的 Mixin 配置插件。
 * <p>
 * 破坏魔杖来自可选的 BBS VFX 插件，因此所有直接指向 {@code org.xavin.xavin}
 * 类的注入都必须等确认 VFX 已加载后才应用，避免没有安装 VFX 时解析目标类失败。
 * </p>
 */
public class XavinMixinPlugin implements IMixinConfigPlugin
{
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
        return FabricLoader.getInstance().isModLoaded("xavin");
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
