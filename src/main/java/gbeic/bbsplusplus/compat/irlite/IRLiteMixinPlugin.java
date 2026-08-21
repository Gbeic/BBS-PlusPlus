package gbeic.bbsplusplus.compat.irlite;

import net.fabricmc.loader.api.FabricLoader;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

/**
 * 按已安装 IRLite 的实际包结构选择物品喷射阴影兼容 Mixin。
 * <p>
 * IRLite 1.1 将 BBS 投影源迁移到了 {@code org.qualet.irl.light.shadow}，旧版则位于
 * {@code qualet.irlite.client.light.shadow}。在解析 Mixin 目标前检查模组包内类路径，
 * 只启用存在的版本，避免兼容另一版本时产生无意义的目标类缺失警告。
 * </p>
 */
public class IRLiteMixinPlugin implements IMixinConfigPlugin
{
    private static final String CURRENT_CASTER_SOURCE = "org.qualet.irl.light.shadow.IRLiteBbsCasterSource";
    private static final String LEGACY_CASTER_SOURCE = "qualet.irlite.client.light.shadow.IRLiteBbsCasterSource";

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
        if (mixinClassName.endsWith(".IRLiteBbsCasterSourceMixin"))
        {
            return containsIRLiteClass(CURRENT_CASTER_SOURCE);
        }

        if (mixinClassName.endsWith(".LegacyIRLiteBbsCasterSourceMixin"))
        {
            return containsIRLiteClass(LEGACY_CASTER_SOURCE);
        }

        return false;
    }

    private static boolean containsIRLiteClass(String className)
    {
        String classPath = className.replace('.', '/') + ".class";

        return FabricLoader.getInstance()
            .getModContainer("irlite")
            .flatMap(container -> container.findPath(classPath))
            .isPresent();
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
