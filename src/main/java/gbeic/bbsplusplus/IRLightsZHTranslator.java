package gbeic.bbsplusplus;

import java.util.HashMap;
import java.util.Map;

/**
 * 双重映射表：
 * 1. 英文文本 → 中文（供 StringKeyMixin 使用，处理硬编码 IKey.constant）
 * 2. L10n key → 中文（供 LangKeyMixin 使用，处理 BBS 设置项 LangKey）
 */
public class IRLightsZHTranslator {

    /** 硬编码英文字符串 → 中文（拦截 StringKey.get()） */
    private static final Map<String, String> STRING_MAP = new HashMap<>();

    /** L10n key → 中文（拦截 LangKey.get()，仅限 irlights 相关的 key） */
    private static final Map<String, String> LANG_KEY_MAP = new HashMap<>();

    static {
        // ═══════════════════════════════════════
        // STRING_MAP：对应 IKey.constant("英文") 的硬编码文本
        // ═══════════════════════════════════════

        // —— 表单面板标签 ——
        STRING_MAP.put("Point light",               "点光源");
        STRING_MAP.put("Spotlight",                  "聚光灯");
        STRING_MAP.put("Entities only",              "仅实体");
        STRING_MAP.put("Blocks only",                "仅方块");
        STRING_MAP.put("Shadows",                    "阴影");
        STRING_MAP.put("Color",                      "颜色");
        STRING_MAP.put("Intensity",                  "强度");
        STRING_MAP.put("Range",                      "范围");
        STRING_MAP.put("Radius",                     "半径");
        STRING_MAP.put("Inner radius",               "内径");
        STRING_MAP.put("Beam strength",              "光束强度");
        STRING_MAP.put("Anisotropy",                 "各向异性");
        STRING_MAP.put("VL density",                 "体积光密度");
        STRING_MAP.put("Bulb size (shadow softness)","阴影柔和度");
        STRING_MAP.put("Cookie texture (gobo)",      "遮罩纹理");
        STRING_MAP.put("Invert gobo",                "反转遮罩");
        STRING_MAP.put("Cookie rotation",            "遮罩旋转");
        STRING_MAP.put("Cookie scale",               "遮罩缩放");
        STRING_MAP.put("Cookie / gobo (spot mask)",  "遮罩（聚光蒙版）");

        // —— Patcher UI ——
        STRING_MAP.put("Shaderpacks",                "光影包");
        STRING_MAP.put("Patches",                    "补丁");
        STRING_MAP.put("Refresh lists",              "刷新列表");
        STRING_MAP.put("Open shaderpacks folder",    "打开光影包文件夹");
        STRING_MAP.put("Open patches folder",        "打开补丁文件夹");
        STRING_MAP.put("Create new pack each time",  "生成新光影包");
        STRING_MAP.put("Validate",                   "验证");
        STRING_MAP.put("Dry-run: check every op against the selected pack, write nothing",   "模拟运行：逐项对照选定光影包检查操作，不写入任何内容");
        STRING_MAP.put("Patch",                      "应用补丁");
        STRING_MAP.put("Select a shaderpack and a patch for it.", "请选择一个光影包和对应补丁。");
        STRING_MAP.put("Couldn't read this patch.", "无法读取此补丁。");
        STRING_MAP.put("Select a shaderpack above to continue.", "请在上方选择一个光影包以继续。");
        STRING_MAP.put("Select a shaderpack from the list.", "请从列表中选择一个光影包。");
        STRING_MAP.put("Select a patch for the shaderpack.", "请为该光影包选择一个补丁。");
        STRING_MAP.put("Couldn't read the selected patch.", "无法读取所选补丁。");
        STRING_MAP.put("It fits! Press Patch to create the light version of the pack.", "验证通过！点击“应用补丁”以生成光影包的 Light 版本。");
        STRING_MAP.put("This shaderpack already has the light. Pick the original (clean) pack.", "此光影包已经包含光源补丁。请选择原始（纯净）的光影包。");
        STRING_MAP.put("Patch isn't compatible with this mod version. Update the mod or the patch.", "该补丁与当前模组版本不兼容。请更新模组或补丁。");
        STRING_MAP.put("Couldn't open the shaderpack. Make sure a valid pack is selected.", "无法打开光影包。请确保选择了一个有效的光影包。");
        STRING_MAP.put("File error. Close the pack in other programs and try again.", "文件错误。请在其他程序中关闭该光影包并重试。");
        STRING_MAP.put("This patch didn't fit the selected pack, maybe it's a different version.", "此补丁不适用于所选光影包，可能是版本不同。");

        // —— 阴影质量下拉选项 ——
        STRING_MAP.put("LOW",    "低");
        STRING_MAP.put("MEDIUM", "中");
        STRING_MAP.put("HIGH",   "高");
        STRING_MAP.put("ULTRA",  "极高");

        // ═══════════════════════════════════════
        // LANG_KEY_MAP：对应 BBS L10n 系统的 LangKey
        // （由 irlights 的 L10nMixin 通过 self.getKey(...) 注册）
        // ═══════════════════════════════════════

        // —— 设置面板侧边栏名称 ——
        LANG_KEY_MAP.put("bbs.config.irlite.title",         "IRLite");
        LANG_KEY_MAP.put("bbs.config.irlite.tooltip",       "IRLite 光源插件设置");
        LANG_KEY_MAP.put("bbs.config.irlite_patcher.title", "光影补丁");
        LANG_KEY_MAP.put("bbs.config.irlite_patcher.tooltip","将 .irlights 补丁应用到光影包");

        // —— 设置项标签 ——
        LANG_KEY_MAP.put("bbs.config.irlite.show_guides",       "在世界中绘制光源线框");
        LANG_KEY_MAP.put("bbs.config.irlite.shadow_quality",    "阴影质量");
        LANG_KEY_MAP.put("bbs.config.irlite.shadow_cache",      "缓存静态阴影");
        LANG_KEY_MAP.put("bbs.config.irlite.shadow_blocks",     "方块阴影");
        LANG_KEY_MAP.put("bbs.config.irlite.shadow_block_radius","阴影投射方块半径");
        LANG_KEY_MAP.put("bbs.config.irlite.shadow_bake_budget",   "阴影烘焙预算");

        // —— 设置项悬浮注释（-comment 键） ——
        LANG_KEY_MAP.put("bbs.config.irlite.show_guides-comment",
            "为世界中放置的点光源和聚光灯绘制线框。");
        LANG_KEY_MAP.put("bbs.config.irlite.shadow_quality-comment",
            "阴影深度贴图分辨率。越高越清晰，但占用更多显存（低～40 MB……极高～2.5 GB）。");
        LANG_KEY_MAP.put("bbs.config.irlite.shadow_cache-comment",
            "仅在灯光或遮挡物移动时重新烘焙阴影贴图。静态场景大幅提升帧率，如阴影有残影请关闭。");
        LANG_KEY_MAP.put("bbs.config.irlite.shadow_blocks-comment",
            "从世界方块投射阴影。异形方块（台阶、楼梯、栅栏）按真实形状投影，镂空方块（树叶、栏杆、玻璃门）忽略透明像素。");
        LANG_KEY_MAP.put("bbs.config.irlite.shadow_block_radius-comment",
            "灯光周围收集阴影投射方块的半径（方块数）。超出此范围的方块不投射阴影。较大值使每次重收集更耗性能，默认 24。");
        LANG_KEY_MAP.put("bbs.config.irlite.shadow_bake_budget-comment",
            "每帧允许烘焙的最大阴影贴图数量（0 = 无限制）。较低的值可避免烘焙期间的帧率骤降。默认 4。");
    }

    /**
     * 根据硬编码英文文本查找中文翻译（供 StringKeyMixin 使用）。
     * @return 中文文字，或 null（表示不需要翻译）
     */
    public static String getChinese(String englishText) {
        if (englishText == null) return null;
        String exact = STRING_MAP.get(englishText);
        if (exact != null) {
            return exact;
        }

        // 处理含有动态参数的拼接字符串
        if (englishText.startsWith("This patch is for the ") && englishText.endsWith(" shaderpack. Select it above.")) {
            String target = englishText.substring(22, englishText.length() - 29);
            return "此补丁适用于 " + target + " 光影包。请在上方选择它。";
        }
        if (englishText.startsWith("This patch is for a different shaderpack (") && englishText.endsWith(").")) {
            String target = englishText.substring(42, englishText.length() - 2);
            return "此补丁适用于其他光影包 (" + target + ")。";
        }
        if (englishText.startsWith("This patch is made for the ") && englishText.endsWith(" shaderpack.")) {
            String target = englishText.substring(27, englishText.length() - 12);
            return "此补丁专为 " + target + " 光影包制作。";
        }
        if (englishText.startsWith("Done! Pack \"") && englishText.endsWith("\" created. Select it in Iris settings.")) {
            String target = englishText.substring(12, englishText.length() - 38);
            return "完成！已生成光影包 \"" + target + "\"。请在光影设置中选择它。";
        }

        return null;
    }

    /**
     * 根据 BBS L10n key 查找中文翻译（供 LangKeyMixin 使用）。
     * 仅覆盖 irlights 相关的 key，不干涉其他模组。
     * @return 中文文字，或 null（表示不需要翻译）
     */
    public static String getChineseForKey(String langKey) {
        return LANG_KEY_MAP.get(langKey);
    }
}
