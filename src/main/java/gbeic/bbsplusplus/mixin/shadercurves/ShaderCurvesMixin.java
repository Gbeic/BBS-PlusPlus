package gbeic.bbsplusplus.mixin.shadercurves;

import gbeic.bbsplusplus.client.compat.shadercurves.ShaderCurveDebug;
import mchorse.bbs_mod.client.BBSRendering;
import mchorse.bbs_mod.utils.iris.ShaderCurves;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 修复并增强 BBS 本体的光影曲线源码处理器 {@code ShaderCurves}。
 * <p>
 * 这部分逻辑移植自 BBSTools 4.1，并合并了后续针对它的性能与正确性修复。相对 BBS 原版解决了四个问题：
 * </p>
 * <ol>
 *   <li><b>误处理非 GLSL 文本</b>：原版对光影包里的任何文本都跑一遍宏解析，属性文件之类的内容会被当着色器改写；</li>
 *   <li><b>宏识别过窄</b>：原版规则要求 {@code #define 名字 数值} 之间只能是空格，且数值不能带负号或
 *       {@code e}/{@code f} 后缀，导致大量光影包的可调参数扫不出来；</li>
 *   <li><b>变量名子串误杀</b>：原版用 {@code contains} 判断宏里是否出现某个变量名，
 *       {@code FOG} 会误命中 {@code FOG_DENSITY}，把本可以调的参数错误剔除；</li>
 *   <li><b>解析崩溃与性能问题</b>：{@code const} 声明里没有等号时原版会下标越界；
 *       同时每个变量对每一行都要重新编译一次正则，光影包越大越卡到进不去游戏。</li>
 * </ol>
 * <p>
 * 修复思路是反过来做匹配：先从宏行里一次性抽出所有标识符，再拿去哈希集合里查，
 * 既保证按完整单词匹配，又把复杂度从「变量数 × 行数」降到一次线性扫描。
 * </p>
 */
@Mixin(value = ShaderCurves.class, remap = false)
public class ShaderCurvesMixin
{
    @Shadow(remap = false)
    private static Set<String> prohibitedVariables;

    @Shadow(remap = false)
    private static Set<String> prohibitedConstIdentifiers;

    @Shadow(remap = false)
    public static Map<String, ShaderCurves.ShaderVariable> variableMap;

    /** 避免同一段 shader 源码在一次运行中重复刷屏 */
    @Unique
    private static final Set<Integer> BBSPP$LOGGED_SOURCE_HASHES = new HashSet<>();

    /** 记录上次输出的全局变量表，只有变化时才再次输出 */
    @Unique
    private static Set<String> BBSPP$LAST_LOGGED_VARIABLE_MAP = new HashSet<>();

    /** 提取标识符用的正则，只编译一次 */
    @Unique
    private static final Pattern BBSPP$IDENTIFIER_PATTERN = Pattern.compile("[a-zA-Z0-9_$]+");

    /**
     * 放宽后的宏识别正则：允许任意空白分隔、允许负数、允许 {@code 1e-3} / {@code 0.5f} 这类写法。
     */
    @Unique
    private static final Pattern BBSPP$DEFINE_PATTERN =
        Pattern.compile("^\\s*(?!//)\\s*#define\\s+([\\w_]+)\\s+(-?[\\d.ef]+)\\s*//\\s*(\\[|OptionAnnotatedSource)");

    /**
     * 覆盖目标：{@code ShaderCurves#parseVariables(String)}。
     * 覆盖原因：原版的识别规则太严，很多光影包的可调参数因此扫不出来，在曲线参数列表里就找不到它们。
     * 修改行为：流程与原版完全一致，只换用放宽后的识别规则；
     * 另外把匹配到的内容解析成数字时做了兜底，避免放宽规则后碰上不合法的数值直接抛异常中断光影加载。
     *
     * @author Gbeic
     * @reason 放宽宏识别规则并补上数值解析保护
     */
    @Overwrite
    private static Map<String, ShaderCurves.ShaderVariable> parseVariables(String source)
    {
        Map<String, ShaderCurves.ShaderVariable> variables = new HashMap<>();
        int index = 0;

        while ((index = source.indexOf("#define", index)) != -1)
        {
            int newLine = source.indexOf("\n", index);

            if (newLine == -1)
            {
                newLine = source.length();
            }

            int lastNewLine = source.lastIndexOf('\n', index);
            String define = source.substring(lastNewLine != -1 ? lastNewLine : index, newLine).trim();
            Matcher matcher = BBSPP$DEFINE_PATTERN.matcher(define);

            if (matcher.find())
            {
                String name = matcher.group(1);
                String defaultValue = matcher.group(2);
                // 放宽后的规则会放进 1e-3、0.5f 这类写法，它们同样是浮点数
                boolean integer = defaultValue.indexOf('.') == -1
                    && defaultValue.indexOf('e') == -1
                    && defaultValue.indexOf('f') == -1;

                try
                {
                    variables.putIfAbsent(name, new ShaderCurves.ShaderVariable(name, defaultValue, integer));
                }
                catch (NumberFormatException ignored)
                {
                    // 放宽规则后可能匹配到 ".." 之类不是合法数字的内容，跳过即可
                }
            }

            index = newLine;
        }

        return variables;
    }

    /**
     * 注入目标：{@code ShaderCurves#processSource(String)} 入口。
     * 注入原因：光影包里除了着色器还有语言文件、属性表等纯文本，原版不加判断就整段做宏替换，
     * 既浪费性能又可能破坏内容。
     * 修改行为：跳过开头注释后如果不是以 {@code #version} 起始，说明不是 GLSL 源码，原样返回不做任何处理。
     */
    @Inject(method = "processSource", at = @At("HEAD"), cancellable = true, remap = false)
    private static void bbspp$skipNonGlslSource(String source, CallbackInfoReturnable<String> cir)
    {
        if (!bbspp$startsWithVersion(source))
        {
            cir.setReturnValue(source);
        }
    }

    /**
     * 注入目标：{@code ShaderCurves#reset()} 末尾。
     * 注入原因：切换或重载光影包时，需要重新记录新光影包的源码与全局变量表。
     * 修改行为：只清理排查日志的去重状态，不修改光影曲线处理结果。
     */
    @Inject(method = "reset", at = @At("TAIL"), remap = false)
    private static void bbspp$resetDebugState(CallbackInfo ci)
    {
        BBSPP$LOGGED_SOURCE_HASHES.clear();
        BBSPP$LAST_LOGGED_VARIABLE_MAP.clear();
    }

    /**
     * 注入目标：{@code ShaderCurves#processSource(String)} 返回点。
     * 注入原因：临时排查 Revoxelation 像永夜一样没有太阳光的问题，需要知道本次加载后有哪些光影参数
     * 已经被 BBS 注册为可运行时改写的曲线变量。
     * 修改行为：仅输出日志，不修改源码处理结果。
     */
    @Inject(method = "processSource", at = @At("RETURN"), remap = false)
    private static void bbspp$logShaderCurveVariableMap(String source, CallbackInfoReturnable<String> cir)
    {
        if (!ShaderCurveDebug.isShaderCurvePatches() || variableMap == null)
        {
            return;
        }

        Set<String> names = new HashSet<>(variableMap.keySet());

        if (!names.equals(BBSPP$LAST_LOGGED_VARIABLE_MAP))
        {
            BBSPP$LAST_LOGGED_VARIABLE_MAP = names;
            ShaderCurveDebug.log("当前已注册、会被 BBS 曲线系统改写为 uniform 的光影参数 "
                + names.size()
                + " 个："
                + bbspp$formatNames(names));
        }
    }

    /**
     * 覆盖目标：{@code ShaderCurves#removeIrrelevantVariables}。
     * 覆盖原因：原版用子串包含判断变量名，会把名字互为前缀的参数一起误删；
     * 且对每个变量都要重新扫描整段源码，光影包大时明显卡顿。
     * 修改行为：先按滑条选项与黑名单筛出有效变量存进哈希集合，
     * 再一次线性扫描源码，从每条 {@code #if}/{@code #elif}/{@code #define} 里抽出完整标识符去查表，
     * 命中的说明该参数参与了条件编译或宏展开，不能作为可动画曲线暴露出来。
     *
     * @author Gbeic
     * @reason 修正变量名子串误杀，并消除按变量数重复扫描源码的性能开销
     */
    @Overwrite
    private static void removeIrrelevantVariables(String source, Map<String, ShaderCurves.ShaderVariable> variables)
    {
        List<String> filter = BBSRendering.getShadersSliderOptions();
        Set<String> parsedNames = ShaderCurveDebug.isShaderCurvePatches() ? new HashSet<>(variables.keySet()) : Collections.emptySet();
        Set<String> validNames = new HashSet<>();

        // 只保留光影设置里真的存在滑条、且不在黑名单中的参数
        variables.values().removeIf((v) ->
        {
            if (v.name == null || !filter.contains(v.name))
            {
                return true;
            }

            if (prohibitedVariables != null && prohibitedVariables.contains(v.name))
            {
                return true;
            }

            validNames.add(v.name);

            return false;
        });

        if (validNames.isEmpty())
        {
            bbspp$logSourceSelection(source, parsedNames, validNames, Collections.emptySet(), variables.keySet());

            return;
        }

        Set<String> toRemove = new HashSet<>();
        int index = 0;

        while ((index = source.indexOf("#", index + 1)) != -1)
        {
            int newLine = source.indexOf('\n', index);

            if (newLine < 0)
            {
                continue;
            }

            String substr = source.substring(index, newLine);

            if (substr.startsWith("#if") || substr.startsWith("#elif"))
            {
                bbspp$collectIdentifiers(substr, validNames, toRemove);

                continue;
            }

            if (!substr.startsWith("#define"))
            {
                continue;
            }

            // 跳过 "#define" 与紧随其后的宏名，只检查宏体
            int len = substr.length();
            int nameEnd = 7;

            while (nameEnd < len && Character.isWhitespace(substr.charAt(nameEnd)))
            {
                nameEnd++;
            }

            while (nameEnd < len && !Character.isWhitespace(substr.charAt(nameEnd)))
            {
                nameEnd++;
            }

            if (nameEnd >= len)
            {
                continue;
            }

            bbspp$collectIdentifiers(substr.substring(nameEnd), validNames, toRemove);
        }

        variables.keySet().removeAll(toRemove);

        bbspp$logSourceSelection(source, parsedNames, validNames, toRemove, variables.keySet());
    }

    /**
     * 查找参与顶层 {@code const} 派生的滑条变量，仅供排查日志使用。
     *
     * <p>该扫描不会删除候选变量、写入黑名单或改变 shader 源码，只记录 BBS 即将改写的参数中，
     * 哪些被顶层常量引用，便于继续定位光影兼容问题。</p>
     *
     * @param source 当前 shader 源码。
     * @param validNames 已通过滑条白名单筛选的变量名。
     */
    @Unique
    private static Set<String> bbspp$findConstBackedVariables(String source, Set<String> validNames)
    {
        Set<String> constBacked = new HashSet<>();

        if (validNames.isEmpty())
        {
            return constBacked;
        }

        int depth = 0;
        int length = source.length();

        for (int i = 0; i < length;)
        {
            char c = source.charAt(i);

            if (c == '/' && i + 1 < length)
            {
                char next = source.charAt(i + 1);

                if (next == '/')
                {
                    int newline = source.indexOf('\n', i + 2);

                    i = newline == -1 ? length : newline + 1;
                    continue;
                }
                else if (next == '*')
                {
                    int end = source.indexOf("*/", i + 2);

                    i = end == -1 ? length : end + 2;
                    continue;
                }
            }

            if (c == '{')
            {
                depth++;
                i++;
                continue;
            }
            else if (c == '}')
            {
                depth = Math.max(0, depth - 1);
                i++;
                continue;
            }

            if (depth == 0 && bbspp$startsWithKeyword(source, i, "const"))
            {
                int semicolon = source.indexOf(';', i);

                if (semicolon == -1)
                {
                    break;
                }

                int brace = source.indexOf('{', i);

                if (brace == -1 || brace > semicolon)
                {
                    bbspp$collectIdentifiers(source.substring(i, semicolon), validNames, constBacked);
                }

                i = semicolon + 1;
                continue;
            }

            i++;
        }

        return constBacked;
    }

    /**
     * 覆盖目标：{@code ShaderCurves#removeConstFromRelevantVariables}。
     * 覆盖原因：原版在遇到没有等号的 {@code const} 声明时会取到 -1 下标直接抛异常，
     * 并且每一轮传播都用子串匹配，同样存在误命中与重复扫描的问题。
     * 修改行为：改为按完整标识符匹配并做下标保护，去 const 的传播链保持原版语义（先找含 {@code bbs_} 的声明，
     * 再顺着被去 const 的名字一层层往下传播），黑名单从本体字段读取而不是硬编码。
     *
     * @author Gbeic
     * @reason 修复无等号 const 声明导致的越界崩溃，并改用标识符精确匹配
     */
    @Overwrite
    private static String removeConstFromRelevantVariables(String source)
    {
        Set<String> deconst = new HashSet<>();

        source = bbspp$removeConst(source, deconst, null);

        if (prohibitedConstIdentifiers != null)
        {
            deconst.addAll(prohibitedConstIdentifiers);
        }

        while (!deconst.isEmpty())
        {
            Set<String> next = new HashSet<>();

            source = bbspp$removeConst(source, next, deconst);
            deconst = next;
        }

        return source;
    }

    /**
     * 扫描源码里的 {@code const} 声明，把命中的声明去掉 {@code const} 关键字，并记录被去 const 的变量名。
     *
     * @param collected 输出参数，收集本轮被去 const 的变量名，用于下一轮传播
     * @param matchers  需要命中的标识符集合，传 {@code null} 表示按含有 {@code bbs_} 前缀判断
     */
    @Unique
    private static String bbspp$removeConst(String source, Set<String> collected, Set<String> matchers)
    {
        StringBuilder builder = new StringBuilder();
        int index = 0;
        int lastIndex = 0;

        while ((index = source.indexOf("const ", index + 1)) != -1)
        {
            int semicolon = source.indexOf(';', index);

            if (semicolon < 0)
            {
                continue;
            }

            String substr = source.substring(index, semicolon);

            if (substr.indexOf('{') == -1 && bbspp$matches(substr, matchers))
            {
                builder.append(source, lastIndex, index);
                builder.append(source, index + 6, semicolon);

                int equals = substr.indexOf('=');

                // 原版这里不做判断，遇到没有等号的 const 声明会直接崩溃
                if (equals >= 0)
                {
                    String sub = substr.substring(0, equals).trim();
                    int space = sub.lastIndexOf(' ');

                    if (space >= 0)
                    {
                        collected.add(sub.substring(space).trim());
                    }
                }
            }
            else
            {
                builder.append(source, lastIndex, semicolon);
            }

            lastIndex = semicolon;
        }

        builder.append(source, lastIndex, source.length());

        return builder.toString();
    }

    /** 判断一段 const 声明是否命中目标标识符集合 */
    @Unique
    private static boolean bbspp$matches(String substr, Set<String> matchers)
    {
        if (matchers == null)
        {
            return substr.contains(ShaderCurves.UNIFORM_IDENTIFIER);
        }

        Matcher matcher = BBSPP$IDENTIFIER_PATTERN.matcher(substr);

        while (matcher.find())
        {
            if (matchers.contains(matcher.group()))
            {
                return true;
            }
        }

        return false;
    }

    /** 从一段文本里抽出所有标识符，命中候选集合的记入结果集合 */
    @Unique
    private static void bbspp$collectIdentifiers(String text, Set<String> candidates, Set<String> result)
    {
        Matcher matcher = BBSPP$IDENTIFIER_PATTERN.matcher(text);

        while (matcher.find())
        {
            String identifier = matcher.group();

            if (candidates.contains(identifier))
            {
                result.add(identifier);
            }
        }
    }

    /** 输出单段 shader 源码里光影曲线参数的筛选过程 */
    @Unique
    private static void bbspp$logSourceSelection(
        String source,
        Set<String> parsedNames,
        Set<String> sliderNames,
        Set<String> preprocessorBacked,
        Collection<String> finalNames
    )
    {
        if (!ShaderCurveDebug.isShaderCurvePatches() || parsedNames.isEmpty())
        {
            return;
        }

        int hash = source.hashCode();

        if (!BBSPP$LOGGED_SOURCE_HASHES.add(hash))
        {
            return;
        }

        Set<String> constBacked = bbspp$findConstBackedVariables(source, sliderNames);
        Set<String> notSlider = new HashSet<>(parsedNames);

        notSlider.removeAll(sliderNames);
        notSlider.removeAll(constBacked);
        notSlider.removeAll(preprocessorBacked);
        notSlider.removeAll(finalNames);

        ShaderCurveDebug.log("源码片段 hash=" + hash
            + "：候选=" + parsedNames.size()
            + " 个，最终改写=" + finalNames.size()
            + " 个，非滑条跳过=" + notSlider.size()
            + " 个，顶层 const 关联且仍改写=" + constBacked.size()
            + " 个，预处理跳过=" + preprocessorBacked.size()
            + " 个");

        ShaderCurveDebug.log("hash=" + hash + " 最终改写：" + bbspp$formatNames(finalNames));

        if (!constBacked.isEmpty())
        {
            ShaderCurveDebug.log("hash=" + hash + " 顶层 const 关联且仍改写：" + bbspp$formatNames(constBacked));
        }

        if (!preprocessorBacked.isEmpty())
        {
            ShaderCurveDebug.log("hash=" + hash + " 预处理跳过：" + bbspp$formatNames(preprocessorBacked));
        }
    }

    /** 排序并压缩变量名列表，避免日志行过长到难以阅读 */
    @Unique
    private static String bbspp$formatNames(Collection<String> names)
    {
        if (names == null || names.isEmpty())
        {
            return "[]";
        }

        List<String> sorted = new ArrayList<>(names);

        Collections.sort(sorted);

        int limit = 120;

        if (sorted.size() <= limit)
        {
            return sorted.toString();
        }

        List<String> head = new ArrayList<>(sorted.subList(0, limit));

        head.add("... 另有 " + (sorted.size() - limit) + " 个");

        return head.toString();
    }

    /** 判断指定位置是否以完整关键字起始 */
    @Unique
    private static boolean bbspp$startsWithKeyword(String source, int index, String keyword)
    {
        int end = index + keyword.length();

        return end <= source.length()
            && source.startsWith(keyword, index)
            && (index == 0 || !bbspp$isIdentifierPart(source.charAt(index - 1)))
            && (end == source.length() || !bbspp$isIdentifierPart(source.charAt(end)));
    }

    /** 判断字符是否属于 GLSL 标识符 */
    @Unique
    private static boolean bbspp$isIdentifierPart(char c)
    {
        return Character.isLetterOrDigit(c) || c == '_';
    }

    /** 跳过开头的空白与注释后，判断是否为以 {@code #version} 起始的 GLSL 源码 */
    @Unique
    private static boolean bbspp$startsWithVersion(String str)
    {
        if (str == null)
        {
            return false;
        }

        int i = 0;
        int n = str.length();

        while (i < n)
        {
            char c = str.charAt(i);

            if (Character.isWhitespace(c))
            {
                i++;

                continue;
            }

            // 块注释
            if (i + 1 < n && c == '/' && str.charAt(i + 1) == '*')
            {
                int end = str.indexOf("*/", i + 2);

                i = end == -1 ? n : end + 2;

                continue;
            }

            // 行注释
            if (i + 1 < n && c == '/' && str.charAt(i + 1) == '/')
            {
                int end = str.indexOf('\n', i + 2);

                i = end == -1 ? n : end + 1;

                continue;
            }

            break;
        }

        return i + 8 <= n && str.startsWith("#version", i);
    }
}
