package gbeic.bbsplusplus.client.compat.iris;

import mchorse.bbs_mod.BBSModClient;

import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * 安全收集 Iris 光影设置项的本地化路径。
 * <p>
 * BBS 原版的 Iris 菜单路径收集会直接递归进入子菜单；如果光影包的设置菜单存在循环跳转，
 * 打开光影曲线列表时就会卡住并持续刷 {@code IrisUtils.collectPaths} 调用栈。
 * 这里通过反射读取 Iris 的可选运行时对象，并在递归时记录当前访问链，遇到循环或过深菜单就停止向下展开。
 * </p>
 */
public final class SafeShaderLanguageMap
{
    private static final String OPTION_PREFIX = "option.";
    private static final String PATH_SEPARATOR = " > ";
    private static final int MAX_SHADER_MENU_DEPTH = 64;

    /* 收集过程需要大量反射与递归，而结果只会在切换光影包或切换语言时变化，因此按光影包对象缓存 */
    private static WeakReference<Object> cachedPack;
    private static String cachedLanguage;
    private static Map<String, String> cachedResult;

    private SafeShaderLanguageMap()
    {}

    public static Map<String, String> collect()
    {
        return collect(BBSModClient.getLanguageKey());
    }

    public static Map<String, String> collect(String language)
    {
        try
        {
            return collectUnsafe(language);
        }
        catch (Throwable ignored)
        {
            return Collections.emptyMap();
        }
    }

    private static Map<String, String> collectUnsafe(String language)
        throws ReflectiveOperationException {
        Class<?> irisClass = Class.forName("net.irisshaders.iris.Iris");
        Optional<?> currentPack = (Optional<?>) irisClass.getMethod("getCurrentPack").invoke(null);

        if (currentPack.isEmpty())
        {
            invalidate();

            return Collections.emptyMap();
        }

        Object shaderPack = currentPack.get();
        Map<String, String> cached = getCached(shaderPack, language);

        if (cached != null)
        {
            return cached;
        }

        Map<String, String> map = new HashMap<>();
        Object languageMap = invoke(shaderPack, "getLanguageMap");
        Map<String, String> fallback = getTranslations(languageMap, "en_us");
        Map<String, String> target = getTranslations(languageMap, language);
        Map<String, List<String>> pathMap = new HashMap<>();
        Object container = invoke(shaderPack, "getMenuContainer");
        Object mainScreen = getField(container, "mainScreen");

        collectPaths(pathMap, container, mainScreen, Collections.emptyList(), new HashSet<>(), "<main>");
        fillInPaths(map, fallback, pathMap);
        fillInPaths(map, target, pathMap);

        cachedPack = new WeakReference<>(shaderPack);
        cachedLanguage = language;
        cachedResult = map;

        return map;
    }

    /** 取出与当前光影包、当前语言匹配的缓存结果，没有则返回 {@code null} */
    private static Map<String, String> getCached(Object shaderPack, String language)
    {
        if (cachedResult == null || cachedPack == null)
        {
            return null;
        }

        if (cachedPack.get() != shaderPack || !Objects.equals(cachedLanguage, language))
        {
            return null;
        }

        return cachedResult;
    }

    /** 清空缓存，光影包被卸载时调用 */
    private static void invalidate()
    {
        cachedPack = null;
        cachedLanguage = null;
        cachedResult = null;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, String> getTranslations(Object languageMap, String language) throws ReflectiveOperationException
    {
        return (Map<String, String>) invoke(languageMap, "getTranslations", new Class<?>[] {String.class}, language);
    }

    private static Object invoke(Object target, String method) throws ReflectiveOperationException
    {
        return invoke(target, method, new Class<?>[0]);
    }

    private static Object invoke(Object target, String method, Class<?>[] types, Object... args) throws ReflectiveOperationException
    {
        Method reflected = target.getClass().getMethod(method, types);

        return reflected.invoke(target, args);
    }

    private static Object getField(Object target, String name) throws ReflectiveOperationException
    {
        Field field = target.getClass().getField(name);

        return field.get(target);
    }

    private static void fillInPaths(Map<String, String> map, Map<String, String> language, Map<String, List<String>> pathMap)
    {
        if (language == null)
        {
            return;
        }

        for (Map.Entry<String, String> entry : language.entrySet())
        {
            if (!entry.getKey().startsWith(OPTION_PREFIX))
            {
                continue;
            }

            String optionId = entry.getKey().substring(OPTION_PREFIX.length());
            List<String> path = pathMap.get(optionId);
            String value = entry.getValue();

            if (path != null)
            {
                List<String> translations = new ArrayList<>();

                for (int i = 0, c = path.size(); i < c; i++)
                {
                    String string = path.get(i);

                    translations.add(i == c - 1 ? value : language.getOrDefault("screen." + string, string));
                }

                value = String.join(PATH_SEPARATOR, translations);
            }

            map.put(entry.getKey(), value);
        }
    }

    @SuppressWarnings("unchecked")
    private static void collectPaths(Map<String, List<String>> pathMap, Object container, Object screen, List<String> prefix, Set<String> visiting, String screenId)
        throws ReflectiveOperationException
    {
        if (screen == null || prefix.size() > MAX_SHADER_MENU_DEPTH || !visiting.add(screenId))
        {
            return;
        }

        try
        {
            Iterable<?> elements = (Iterable<?>) getField(screen, "elements");

            for (Object element : elements)
            {
                if (hasField(element, "optionId"))
                {
                    String optionId = (String) getField(element, "optionId");
                    ArrayList<String> strings = new ArrayList<>(prefix);

                    strings.add(optionId);
                    pathMap.putIfAbsent(optionId, strings);
                }
                else if (hasField(element, "targetScreenId"))
                {
                    String targetScreenId = (String) getField(element, "targetScreenId");
                    Map<String, Object> subScreens = (Map<String, Object>) getField(container, "subScreens");
                    Object subScreen = subScreens.get(targetScreenId);

                    if (subScreen != null)
                    {
                        ArrayList<String> strings = new ArrayList<>(prefix);

                        strings.add(targetScreenId);
                        collectPaths(pathMap, container, subScreen, strings, visiting, targetScreenId);
                    }
                }
            }
        }
        finally
        {
            visiting.remove(screenId);
        }
    }

    private static boolean hasField(Object target, String name)
    {
        try
        {
            target.getClass().getField(name);

            return true;
        }
        catch (NoSuchFieldException e)
        {
            return false;
        }
    }
}
