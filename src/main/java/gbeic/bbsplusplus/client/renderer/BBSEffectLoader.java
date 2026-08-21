package gbeic.bbsplusplus.client.renderer;

import mchorse.bbs_mod.BBSMod;
import gbeic.bbsplusplus.BBSPlusPlusMod;
import gbeic.bbsplusplus.utils.EnvLogger;
import mod.chloeprime.aaaparticles.api.client.EffectDefinition;
import mod.chloeprime.aaaparticles.api.client.EffectHolder;
import mod.chloeprime.aaaparticles.api.client.EffectMetadata;
import mod.chloeprime.aaaparticles.api.client.effekseer.EffekseerEffect;
import mod.chloeprime.aaaparticles.api.client.effekseer.TextureType;
import mod.chloeprime.aaaparticles.client.loader.EffekAssetLoader;
import mod.chloeprime.aaaparticles.client.render.RenderUtil;
import net.minecraft.resources.ResourceLocation;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * BBS 特效加载器。
 * <p>
 * 从 BBS 外部资源文件夹（config/bbs/assets/）加载 Effekseer 特效文件，
 * 并通过反射注入到 AAA Particles 的 EffekAssetLoader 中，
 * 使特效能够被 AAA Particles 的渲染管线自动处理。
 * </p>
 */
public class BBSEffectLoader
{
    /* 跟踪已加载的 BBS 特效 ID，用于重载时清理 */
    private static final Set<ResourceLocation> bbsLoadedEffects = new HashSet<>();

    /* EffectDefinition 缓存池，防止同一特效重复加载 */
    private static final Map<ResourceLocation, EffectDefinition> definitionCache = new java.util.HashMap<>();

    /* AAA Particles 中 loadedEffects 字段的反射缓存 */
    private static Field loadedEffectsField = null;

    /* 加载锁，防止多线程同时调用 getOrLoad 导致 native 层竞态 */
    private static final Object LOAD_LOCK = new Object();

    /* 记录最近加载失败的时间，短暂抑制每帧重试，同时允许用户修复文件后自动恢复 */
    private static final Map<ResourceLocation, Long> failedEffects = new java.util.HashMap<>();
    private static final long FAILED_RETRY_INTERVAL_MS = 2000L;

    private static volatile boolean reloadRequested;
    private static volatile boolean reloading;

    public static void requestReload()
    {
        reloadRequested = true;
    }

    public static boolean beginReload()
    {
        if (!reloadRequested)
        {
            return false;
        }

        reloadRequested = false;
        reloading = true;
        return true;
    }

    public static void endReload()
    {
        reloading = false;
    }

    public static boolean isReloading()
    {
        return reloading;
    }

    public static boolean canLoadExternalEffects()
    {
        return !reloading;
    }

    /**
     * 获取或加载一个来自 BBS 外部资源的特效。
     * 特效会被注入到 AAA Particles 的 EffekAssetLoader 中。
     */
    public static EffectDefinition getOrLoad(ResourceLocation id)
    {
        if (!canLoadExternalEffects())
        {
            return null;
        }

        synchronized (LOAD_LOCK)
        {
            Long failedAt = failedEffects.get(id);
            if (failedAt != null && System.currentTimeMillis() - failedAt < FAILED_RETRY_INTERVAL_MS)
            {
                return null;
            }
            failedEffects.remove(id);

            // 优先从缓存池返回，避免重复加载同一特效
            EffectDefinition cachedDef = definitionCache.get(id);
            if (cachedDef != null)
            {
                return cachedDef;
            }

            // 检查 AAA Particles 是否已加载此特效
            EffekAssetLoader loader = EffekAssetLoader.get();

            if (loader != null)
            {
                EffectHolder holder = loader.get(id);

                if (holder != null)
                {
                    EffectDefinition existing = holder.lazyGet()
                            .orElseGet(() -> holder.load().join().orElse(null));

                    if (existing != null)
                    {
                        definitionCache.put(id, existing);
                        return existing;
                    }
                }
            }
            else
            {
                EnvLogger.warn(BBSPlusPlusMod.LOGGER, "EffekAssetLoader.get() 返回 null！", "EffekAssetLoader.get() returned null!");
            }

            EnvLogger.info(BBSPlusPlusMod.LOGGER, "尝试从 BBS 资源加载特效：{}", "Attempting to load BBS asset effect: {}", id);

            // 从外部资源目录加载
            File assetsFolder = BBSMod.getAssetsFolder();
            String path = id.getPath();

            if (!path.startsWith("effeks/"))
            {
                path = "effeks/" + path;
            }

            if (!path.endsWith(".efkefc"))
            {
                path = path + ".efkefc";
            }

            File effectFile = new File(assetsFolder, path);

            if (!effectFile.exists())
            {
                EnvLogger.debug(BBSPlusPlusMod.LOGGER, "特效文件未找到：{}", "Effect file not found: {}", effectFile.getAbsolutePath());
                failedEffects.put(id, System.currentTimeMillis());
                return null;
            }

            EffectDefinition definition = loadEffect(effectFile, id);

            if (definition != null)
            {
                definitionCache.put(id, definition);
                injectIntoAAAParticles(id, definition);
                bbsLoadedEffects.add(id);
            }
            else
            {
                failedEffects.put(id, System.currentTimeMillis());
            }

            return definition;
        } // synchronized(LOAD_LOCK)
    }

    /**
     * 通过反射将特效注入到 AAA Particles 的 EffekAssetLoader。
     */
    private static void injectIntoAAAParticles(ResourceLocation id, EffectDefinition definition)
    {
        try
        {
            EffekAssetLoader loader = EffekAssetLoader.get();

            if (loader == null)
            {
                EnvLogger.warn(BBSPlusPlusMod.LOGGER, "EffekAssetLoader 未初始化，无法注入特效 {}", "EffekAssetLoader not initialized, cannot inject effect {}", id);
                return;
            }

            if (loadedEffectsField == null)
            {
                loadedEffectsField = EffekAssetLoader.class.getDeclaredField("loadedEffects");
                loadedEffectsField.setAccessible(true);
            }

            @SuppressWarnings("unchecked")
            Map<Object, EffectHolder> loadedEffects = (Map<Object, EffectHolder>) loadedEffectsField.get(loader);

            // 将定义包装到 holder 中，使注册管线可用
            EffectHolder holder = new EffectHolder(EffectMetadata.DEFAULT, () -> definition);
            holder.load().join();
            loadedEffects.put(id, holder);

            EnvLogger.info(BBSPlusPlusMod.LOGGER, "已注入 BBS 特效到 AAA Particles：{}", "Injected BBS effect to AAA Particles: {}", id);
        }
        catch (ReflectiveOperationException | ClassCastException e)
        {
            EnvLogger.error(BBSPlusPlusMod.LOGGER, "注入特效到 AAA Particles 失败：{}", "Failed to inject effect to AAA Particles: {}", id, e);
        }
    }

    /**
     * 从文件加载特效
     */
    private static EffectDefinition loadEffect(File effectFile, ResourceLocation id)
    {
        try (FileInputStream input = new FileInputStream(effectFile))
        {
            EffekseerEffect effect = new EffekseerEffect();
            boolean success = effect.load(input, 1);

            if (!success)
            {
                EnvLogger.error(BBSPlusPlusMod.LOGGER, "加载特效失败（文件可能损坏或版本过新）：{}", "Failed to load effect (file might be corrupted or version too new): {}", effectFile.getAbsolutePath());
                return null;
            }

            File parentDir = effectFile.getParentFile();

            // 在 GL 状态保护下加载所有 GPU 资源
            RenderUtil.runEffekLoadCodeHealthily(() ->
            {
                // 加载纹理
                for (TextureType texType : TextureType.values())
                {
                    int count = effect.textureCount(texType);
                    for (int i = 0; i < count; i++)
                    {
                        final int index = i;
                        String texturePath = effect.getTexturePath(i, texType);
                        if (texturePath != null && !texturePath.isEmpty())
                        {
                            loadAsset(parentDir, texturePath, (data, len) ->
                                effect.loadTexture(data, len, index, texType));
                        }
                    }
                }

                // 加载模型
                int modelCount = effect.modelCount();
                for (int i = 0; i < modelCount; i++)
                {
                    final int index = i;
                    String modelPath = effect.getModelPath(i);
                    if (modelPath != null && !modelPath.isEmpty())
                    {
                        loadAsset(parentDir, modelPath, (data, len) ->
                            effect.loadModel(data, len, index));
                    }
                }

                // 加载曲线
                int curveCount = effect.curveCount();
                for (int i = 0; i < curveCount; i++)
                {
                    final int index = i;
                    String curvePath = effect.getCurvePath(i);
                    if (curvePath != null && !curvePath.isEmpty())
                    {
                        loadAsset(parentDir, curvePath, (data, len) ->
                            effect.loadCurve(data, len, index));
                    }
                }

                // 加载材质
                int materialCount = effect.materialCount();
                for (int i = 0; i < materialCount; i++)
                {
                    final int index = i;
                    String materialPath = effect.getMaterialPath(i);
                    if (materialPath != null && !materialPath.isEmpty())
                    {
                        loadAsset(parentDir, materialPath, (data, len) ->
                            effect.loadMaterial(data, len, index));
                    }
                }
            });

            EffectDefinition definition = new EffectDefinition(EffectMetadata.DEFAULT);
            definition.setEffect(effect);

            EnvLogger.info(BBSPlusPlusMod.LOGGER, "已加载 BBS 特效：{} 来自 {}", "Loaded BBS effect: {} from {}", id, effectFile.getAbsolutePath());
            return definition;
        }
        catch (IOException e)
        {
            EnvLogger.error(BBSPlusPlusMod.LOGGER, "读取特效文件失败：{}", "Failed to read effect file: {}", effectFile.getAbsolutePath(), e);
            return null;
        }
    }

    /**
     * 加载特效引用的资源文件
     */
    private static void loadAsset(File parentDir, String assetPath, AssetLoader loader)
    {
        assetPath = assetPath.replace("\\", "/");
        File assetFile = new File(parentDir, assetPath);

        if (!assetFile.exists())
        {
            EnvLogger.warn(BBSPlusPlusMod.LOGGER, "资源文件未找到：{}", "Asset file not found: {}", assetFile.getAbsolutePath());
            return;
        }

        try (FileInputStream input = new FileInputStream(assetFile))
        {
            byte[] data = input.readAllBytes();
            loader.load(data, data.length);
        }
        catch (IOException e)
        {
            EnvLogger.error(BBSPlusPlusMod.LOGGER, "加载资源失败：{}", "Failed to load asset: {}", assetFile.getAbsolutePath(), e);
        }
    }

    /**
     * 预加载所有外部特效
     */
    public static int preloadExternalEffects()
    {
        if (!canLoadExternalEffects())
        {
            return 0;
        }

        File assetsFolder = BBSMod.getAssetsFolder();
        File effeksFolder = new File(assetsFolder, "effeks");

        if (!effeksFolder.exists() || !effeksFolder.isDirectory())
        {
            return 0;
        }

        List<File> files = new ArrayList<>();
        collectEfks(files, effeksFolder, new HashSet<>());

        int loaded = 0;

        for (File file : files)
        {
            String relative = effeksFolder.toPath().relativize(file.toPath()).toString().replace("\\", "/");

            if (!relative.endsWith(".efkefc"))
            {
                continue;
            }

            relative = relative.substring(0, relative.length() - 7);

            ResourceLocation id;
            try
            {
                id = ResourceLocation.fromNamespaceAndPath("bbs", relative);
            }
            catch (Exception e)
            {
                continue;
            }

            if (bbsLoadedEffects.contains(id))
            {
                continue;
            }

            EffectDefinition definition = getOrLoad(id);

            if (definition != null)
            {
                loaded++;
            }
        }

        return loaded;
    }

    private static void collectEfks(List<File> list, File folder, Set<String> visited)
    {
        try
        {
            String path = folder.getCanonicalPath();
            if (!visited.add(path)) return;
        }
        catch (Exception e)
        {
            return;
        }

        File[] files = folder.listFiles();

        if (files == null)
        {
            return;
        }

        for (File file : files)
        {
            if (file.isDirectory())
            {
                collectEfks(list, file, visited);
            }
            else if (file.getName().endsWith(".efkefc"))
            {
                list.add(file);
            }
        }
    }

    /**
     * 标记缓存为脏，下次选择特效时重新从磁盘加载。
     * 不调用 holder.close() 避免触发 native DLL 释放正在渲染的资源。
     */
    public static void markCacheDirty()
    {
        synchronized (LOAD_LOCK)
        {
            definitionCache.clear();
            bbsLoadedEffects.clear();
            failedEffects.clear();

            try
            {
                EffekAssetLoader loader = EffekAssetLoader.get();

                if (loader != null && loadedEffectsField != null)
                {
                    @SuppressWarnings("unchecked")
                    Map<Object, EffectHolder> loadedEffects = (Map<Object, EffectHolder>) loadedEffectsField.get(loader);

                    List<ResourceLocation> ids = new ArrayList<>();
                    for (Object k : loadedEffects.keySet())
                    {
                        if (k instanceof ResourceLocation id)
                        {
                            if ("bbs".equals(id.getNamespace()))
                            {
                                ids.add(id);
                            }
                        }
                    }

                    for (ResourceLocation id : ids)
                    {
                        // 强制停止所有正在使用该特效的粒子，防止 native 崩溃
                        for (AAAParticleFormRenderer renderer : AAAParticleFormRenderer.activeRenderers)
                        {
                            ResourceLocation currentId = renderer.getLastEffectId();
                            if (id.equals(currentId))
                            {
                                renderer.forceStop();
                            }
                        }

                        EffectHolder removed = loadedEffects.remove(id);
                        if (removed != null)
                        {
                            removed.close();
                        }
                    }
                }
            }
            catch (Throwable e)
            {
                EnvLogger.error(BBSPlusPlusMod.LOGGER, "标记特效缓存脏失败", "Failed to mark effect cache as dirty", e);
            }

            EnvLogger.info(BBSPlusPlusMod.LOGGER, "已标记所有 BBS 特效缓存为脏，下次选择时重新加载", "Marked all BBS effect caches as dirty, will reload on next selection");
        }
    }

    /**
     * 卸载指定特效，释放其 native 资源。
     * 当渲染器切换到新特效时调用，防止累积已加载的 EffectDefinition 导致 native 内存泄漏。
     */
    public static void unloadEffect(ResourceLocation id)
    {
        if (id == null) return;

        // 检查是否还有其他活跃粒子正在使用此特效
        boolean inUse = false;
        for (AAAParticleFormRenderer renderer : AAAParticleFormRenderer.activeRenderers)
        {
                ResourceLocation currentId = renderer.getLastEffectId();
                if (id.equals(currentId))
                {
                    inUse = true;
                    break;
                }
        }

        if (inUse)
        {
            return; // 仍有其他渲染器在使用，不可卸载
        }

        synchronized (LOAD_LOCK)
        {
            EffekAssetLoader loader = EffekAssetLoader.get();

            if (loader != null && loadedEffectsField != null)
            {
                try
                {
                    @SuppressWarnings("unchecked")
                    Map<Object, EffectHolder> loadedEffects = (Map<Object, EffectHolder>) loadedEffectsField.get(loader);
                    EffectHolder removed = loadedEffects.remove(id);

                    if (removed != null)
                    {
                        removed.close();
                        EnvLogger.debug(BBSPlusPlusMod.LOGGER, "已卸载特效：{}", "Unloaded effect: {}", id);
                    }
                }
                catch (IllegalAccessException e)
                {
                    EnvLogger.error(BBSPlusPlusMod.LOGGER, "卸载特效失败：{}", "Failed to unload effect: {}", id, e);
                }
            }

            definitionCache.remove(id);
            bbsLoadedEffects.remove(id);
        }
    }

    /**
     * 检查特效是否已加载
     */
    public static boolean isLoaded(ResourceLocation id)
    {
        return bbsLoadedEffects.contains(id);
    }

    /**
     * 检查是否有任何 BBS 特效已加载
     */
    public static boolean hasLoadedEffects()
    {
        return !bbsLoadedEffects.isEmpty();
    }

    @FunctionalInterface
    private interface AssetLoader
    {
        boolean load(byte[] data, int length);
    }
}
