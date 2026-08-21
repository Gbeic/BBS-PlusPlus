package gbeic.bbsplusplus.mixin;

import mchorse.bbs_mod.cubic.model.loaders.IModelLoader;
import mchorse.bbs_mod.resources.Link;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Mixin(value = IModelLoader.class, remap = false)
public interface IModelLoaderMixin
{
    /**
     * BBS++ 修复：
     * 拦截原有的 PNG 模糊查找逻辑。底层会用硬编码的 model.png 作为主贴图请求，
     * 但用户素材经常是模型文件或文件夹同名贴图。这里改为优先匹配模型文件同名、
     * 再匹配文件夹同名，并过滤掉常见的 PBR/发光贴图，避免它们被错误地选为主贴图。
     */
    @Inject(method = "getLink(Lmchorse/bbs_mod/resources/Link;Ljava/util/Collection;Ljava/lang/String;)Lmchorse/bbs_mod/resources/Link;", at = @At("HEAD"), cancellable = true)
    private static void bbspp$getLinkWithSuffix(Link link, Collection<Link> links, String suffix, CallbackInfoReturnable<Link> cir)
    {
        if (".png".equals(suffix))
        {
            if (links.contains(link) && !bbspp$fileName(link.path).equalsIgnoreCase("model.png"))
            {
                cir.setReturnValue(link);

                return;
            }

            cir.setReturnValue(bbspp$findDiffuseTexture(link, links));
        }
    }

    /**
     * BBS++ 修复：
     * 拦截 OBJ/BOBJ 材质默认贴图查找。原版只找第一个 .png，而资源集合来自 HashSet，
     * 同目录存在 *_n.png 等 PBR 贴图时可能随机把法线贴图当成漫反射贴图。
     * 这里改为只选择普通颜色贴图，并优先使用材质文件夹同名贴图，避免同一模型多次加载结果不一致。
     */
    @Inject(method = "findMaterialTexture(Ljava/util/Collection;Lmchorse/bbs_mod/resources/Link;Ljava/lang/String;)Lmchorse/bbs_mod/resources/Link;", at = @At("HEAD"), cancellable = true)
    private static void bbspp$findMaterialTexture(Collection<Link> links, Link model, String material, CallbackInfoReturnable<Link> cir)
    {
        String prefix = model.toString();
        String folder = "/" + material + "/";
        List<Link> candidates = new ArrayList<>();
        String materialName = material.toLowerCase(Locale.ROOT);

        for (Link link : links)
        {
            String string = link.toString();

            if (string.startsWith(prefix) && string.contains(folder) && bbspp$isDiffusePng(link))
            {
                candidates.add(link);
            }
        }

        candidates.sort(
            Comparator.comparingInt((Link l) -> bbspp$getNamedTexturePriority(materialName, l))
                .thenComparing((Link l) -> l.toString(), String.CASE_INSENSITIVE_ORDER)
        );
        cir.setReturnValue(candidates.isEmpty() ? null : candidates.get(0));
    }

    private static Link bbspp$findDiffuseTexture(Link requested, Collection<Link> links)
    {
        List<Link> candidates = new ArrayList<>();

        for (Link link : links)
        {
            if (bbspp$isDiffusePng(link))
            {
                candidates.add(link);
            }
        }

        if (candidates.isEmpty())
        {
            return requested;
        }

        String requestedParent = bbspp$parentPath(requested.path);
        Map<String, Integer> preferredNames = bbspp$getPreferredTexturePriorities(requestedParent, links);

        candidates.sort(
            Comparator.comparingInt((Link l) -> bbspp$getDiffuseTexturePriority(requestedParent, preferredNames, l))
                .thenComparing((Link l) -> l.toString(), String.CASE_INSENSITIVE_ORDER)
        );

        return candidates.get(0);
    }

    private static int bbspp$getDiffuseTexturePriority(String requestedParent, Map<String, Integer> preferredNames, Link link)
    {
        String parent = bbspp$parentPath(link.path);
        String name = bbspp$baseName(bbspp$fileName(link.path)).toLowerCase(Locale.ROOT);
        int priority = parent.equals(requestedParent) ? 0 : 1000 + bbspp$countSlashes(parent);
        Integer preferred = preferredNames.get(name);

        if (preferred != null)
        {
            return priority + preferred;
        }

        return priority + bbspp$getCommonTexturePriority(name);
    }

    private static int bbspp$getNamedTexturePriority(String preferredName, Link link)
    {
        String name = bbspp$baseName(bbspp$fileName(link.path)).toLowerCase(Locale.ROOT);

        if (name.equals(preferredName))
        {
            return 0;
        }

        return bbspp$getCommonTexturePriority(name);
    }

    private static int bbspp$getCommonTexturePriority(String name)
    {
        if ("default".equals(name))
        {
            return 10;
        }
        if ("texture".equals(name))
        {
            return 20;
        }
        if ("model".equals(name))
        {
            return 30;
        }

        return 40;
    }

    private static Map<String, Integer> bbspp$getPreferredTexturePriorities(String requestedParent, Collection<Link> links)
    {
        Map<String, Integer> names = new HashMap<>();
        String folderName = bbspp$fileName(requestedParent).toLowerCase(Locale.ROOT);

        if (!folderName.isEmpty())
        {
            names.put(folderName, 5);
        }

        for (Link link : links)
        {
            if (!bbspp$parentPath(link.path).equals(requestedParent))
            {
                continue;
            }

            String modelName = bbspp$getModelFileBaseName(bbspp$fileName(link.path));

            if (!modelName.isEmpty())
            {
                names.put(modelName.toLowerCase(Locale.ROOT), 0);
            }
        }

        return names;
    }

    private static String bbspp$getModelFileBaseName(String fileName)
    {
        String lower = fileName.toLowerCase(Locale.ROOT);

        if (lower.endsWith(".geo.json"))
        {
            return fileName.substring(0, fileName.length() - ".geo.json".length());
        }
        if (lower.endsWith(".bbs.json"))
        {
            return fileName.substring(0, fileName.length() - ".bbs.json".length());
        }
        if (lower.endsWith(".bobj") || lower.endsWith(".obj") || lower.endsWith(".vox"))
        {
            return bbspp$baseName(fileName);
        }

        return "";
    }

    private static boolean bbspp$isDiffusePng(Link link)
    {
        String path = link.path.toLowerCase(Locale.ROOT);

        return path.endsWith(".png") && !bbspp$isPBRSidecar(path);
    }

    private static boolean bbspp$isPBRSidecar(String path)
    {
        return path.endsWith("_n.png")
            || path.endsWith("_s.png")
            || path.endsWith("_e.png")
            || path.endsWith("_normal.png")
            || path.endsWith("_specular.png")
            || path.endsWith("_emission.png");
    }

    private static String bbspp$parentPath(String path)
    {
        int index = path.lastIndexOf('/');

        return index < 0 ? "" : path.substring(0, index);
    }

    private static String bbspp$fileName(String path)
    {
        int index = path.lastIndexOf('/');

        return index < 0 ? path : path.substring(index + 1);
    }

    private static String bbspp$baseName(String fileName)
    {
        int index = fileName.lastIndexOf('.');

        return index < 0 ? fileName : fileName.substring(0, index);
    }

    private static int bbspp$countSlashes(String path)
    {
        int count = 0;

        for (int i = 0; i < path.length(); i++)
        {
            if (path.charAt(i) == '/')
            {
                count += 1;
            }
        }

        return count;
    }
}
