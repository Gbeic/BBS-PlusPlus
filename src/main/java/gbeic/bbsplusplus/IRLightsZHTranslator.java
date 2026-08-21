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

        // —— 描边目标下拉选项（outline_target） ——
        STRING_MAP.put("ALL",      "全部");
        STRING_MAP.put("ENTITIES", "实体");
        STRING_MAP.put("BLOCKS",   "方块");

        // —— 表单面板折叠分组标题 ——
        STRING_MAP.put("Light",           "灯光");
        STRING_MAP.put("Volumetric beam", "体积光束");
        STRING_MAP.put("Affects",         "影响");

        // —— 设置面板预设区块（UIPresetSection） ——
        STRING_MAP.put("Presets",    "预设");
        STRING_MAP.put("Quality",    "质量");
        STRING_MAP.put("Beam style", "光束风格");
        STRING_MAP.put("Performance", "性能");
        STRING_MAP.put("Balanced",    "均衡");
        STRING_MAP.put("Ultra",       "极高");
        STRING_MAP.put("Custom",      "自定义");
        STRING_MAP.put("Clean",       "纯净");
        STRING_MAP.put("Dusty",       "飘尘");
        STRING_MAP.put("Smoky",       "烟雾");
        STRING_MAP.put("Cost of the lighting: march steps, ray distance, shadow and noise tap strides, "
            + "shadow map resolution and the shader light cap. Custom means the knobs below "
            + "no longer match any preset — pick one to overwrite them. "
            + "No preset selects ULTRA shadows; that one stays a deliberate choice.",
            "光照开销：步进次数、光线距离、阴影与噪点采样步长、阴影贴图分辨率以及着色器光源上限。"
            + "选择“自定义”后，下方的旋钮将不再匹配任何预设——选一项预设即可覆盖它们。"
            + "任何预设都不会选中“极高”阴影；那始终是一个需要你自行决定的选项。");
        STRING_MAP.put("Look of the volumetric beams: noise, drift and the glow around the lamp itself. "
            + "Clean is uniform beams, Dusty is drifting puffs, Smoky is heavy morphing haze "
            + "(the priciest of the three — it is the only one that turns morph on).",
            "体积光束的外观：噪点、飘动以及灯体本身的辉光。“纯净”是均匀的光束，"
            + "“飘尘”是飘散的尘团，“烟雾”是剧烈变形的雾霭（三者中最耗性能——它是唯一开启形变的预设）。");

        // —— 调试区块（UIDebugSection，-Dirlite.debug=true 时显示） ——
        STRING_MAP.put("Debug",                  "调试");
        STRING_MAP.put("Hide performance overlay", "隐藏性能信息");
        STRING_MAP.put("Show performance overlay", "显示性能信息");
        STRING_MAP.put("Per-pass GPU milliseconds in the top-left corner: the shadow "
            + "bake segments, every Iris fullscreen pass and the VL march, plus CPU frame time and "
            + "VRAM residency. Costs a GL timer query per pass, so leave it off for recording. "
            + "Takes effect on the next frame.",
            "在左上角显示每个渲染阶段的 GPU 毫秒耗时：阴影烘焙分段、Iris 的每个全屏通道与体积光步进，"
            + "以及 CPU 帧耗时和显存占用。每个通道都会产生一次 GL 计时器查询，录制视频时请保持关闭。下一帧生效。");

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

        // ═══════════════════════════════════════
        // 新版本 addon（irlights 独立设置模块，config/bbs/settings/irlights.json）
        // 旧版把设置挂在 BBS 自己的模块下（bbs.config.irlite.*），新版注册为
        // 独立模块，键前缀改为 irlights.config.*
        // ═══════════════════════════════════════

        // —— 模块与分类名称 ——
        LANG_KEY_MAP.put("irlights.config.title",                  "IRLights");
        LANG_KEY_MAP.put("irlights.config.presets.title",          "预设");
        LANG_KEY_MAP.put("irlights.config.presets.tooltip",        "光照开销（质量）与光束外观（风格）预设。选择预设会覆盖下方对应的各项参数。");
        LANG_KEY_MAP.put("irlights.config.volumetric.title",       "体积光");
        LANG_KEY_MAP.put("irlights.config.volumetric.tooltip",     "体积光效果的步进、距离、噪点与抖动参数。");
        LANG_KEY_MAP.put("irlights.config.shadows.title",          "阴影");
        LANG_KEY_MAP.put("irlights.config.shadows.tooltip",        "阴影质量、方块阴影与实时阴影选项。");
        LANG_KEY_MAP.put("irlights.config.outline.title",          "描边");
        LANG_KEY_MAP.put("irlights.config.outline.tooltip",        "轮廓描边效果：目标、强度与发光。");
        LANG_KEY_MAP.put("irlights.config.patcher.title",          "光影补丁");
        LANG_KEY_MAP.put("irlights.config.patcher.tooltip",        "将 .irlights 补丁应用到光影包");

        // —— 预设分类设置项标签 ——
        LANG_KEY_MAP.put("irlights.config.presets.vl_intensity",       "体积光强度");
        LANG_KEY_MAP.put("irlights.config.presets.max_shader_lights",  "着色器光源上限");
        LANG_KEY_MAP.put("irlights.config.presets.show_guides",        "显示光源线框");

        // —— 预设分类设置项注释 ——
        LANG_KEY_MAP.put("irlights.config.presets.vl_intensity-comment",
            "体积光的整体亮度倍率。");
        LANG_KEY_MAP.put("irlights.config.presets.max_shader_lights-comment",
            "同时生效的最大着色器光源数量（0 = 无限制）。上限越低，性能越好。");
        LANG_KEY_MAP.put("irlights.config.presets.show_guides-comment",
            "为世界中放置的点光源和聚光灯绘制线框。");

        // —— 体积光分类设置项标签 ——
        LANG_KEY_MAP.put("irlights.config.volumetric.vl_steps",            "体积光步数");
        LANG_KEY_MAP.put("irlights.config.volumetric.vl_max_dist",         "体积光最大距离");
        LANG_KEY_MAP.put("irlights.config.volumetric.vl_shadows_live",     "体积光实时阴影");
        LANG_KEY_MAP.put("irlights.config.volumetric.vl_shadow_stride",    "体积光阴影步长");
        LANG_KEY_MAP.put("irlights.config.volumetric.vl_tip_boost",        "锥尖增强");
        LANG_KEY_MAP.put("irlights.config.volumetric.vl_tip_radius",       "锥尖半径");
        LANG_KEY_MAP.put("irlights.config.volumetric.vl_noise_live",       "体积光噪点");
        LANG_KEY_MAP.put("irlights.config.volumetric.vl_noise_amount",     "噪点强度");
        LANG_KEY_MAP.put("irlights.config.volumetric.vl_noise_scale",      "噪点缩放");
        LANG_KEY_MAP.put("irlights.config.volumetric.vl_noise_speed",      "噪点速度");
        LANG_KEY_MAP.put("irlights.config.volumetric.vl_noise_morph",      "噪点形变");
        LANG_KEY_MAP.put("irlights.config.volumetric.vl_noise_stride",     "噪点步长");
        LANG_KEY_MAP.put("irlights.config.volumetric.vl_dither_temporal",  "时间抖动");

        // —— 体积光分类设置项注释 ——
        LANG_KEY_MAP.put("irlights.config.volumetric.vl_steps-comment",
            "体积光沿视线采样的步数。越高光束越细腻，性能开销越大。");
        LANG_KEY_MAP.put("irlights.config.volumetric.vl_max_dist-comment",
            "体积光可见的最远距离（格）。");
        LANG_KEY_MAP.put("irlights.config.volumetric.vl_shadows_live-comment",
            "体积光参与阴影计算，光束穿过遮挡物时产生明暗变化。");
        LANG_KEY_MAP.put("irlights.config.volumetric.vl_shadow_stride-comment",
            "体积光阴影的采样步长。越大性能越好，但阴影细节更粗糙。");
        LANG_KEY_MAP.put("irlights.config.volumetric.vl_tip_boost-comment",
            "靠近灯体处光束亮度的增强倍率。");
        LANG_KEY_MAP.put("irlights.config.volumetric.vl_tip_radius-comment",
            "锥尖增强效果的作用半径。");
        LANG_KEY_MAP.put("irlights.config.volumetric.vl_noise_live-comment",
            "为光束叠加动态噪点，使其更自然。");
        LANG_KEY_MAP.put("irlights.config.volumetric.vl_noise_amount-comment",
            "噪点对光束的扰动程度。");
        LANG_KEY_MAP.put("irlights.config.volumetric.vl_noise_scale-comment",
            "噪点纹理的缩放（越小越细密）。");
        LANG_KEY_MAP.put("irlights.config.volumetric.vl_noise_speed-comment",
            "噪点流动的速度。");
        LANG_KEY_MAP.put("irlights.config.volumetric.vl_noise_morph-comment",
            "噪点随时间形变的程度（最耗性能的体积光选项）。");
        LANG_KEY_MAP.put("irlights.config.volumetric.vl_noise_stride-comment",
            "噪点采样的步长。越大性能越好，但细节更粗糙。");
        LANG_KEY_MAP.put("irlights.config.volumetric.vl_dither_temporal-comment",
            "在时间轴上抖动采样位置以减少条带伪影，可能产生轻微闪烁。");

        // —— 阴影分类设置项标签 ——
        LANG_KEY_MAP.put("irlights.config.shadows.shadow_quality",   "阴影质量");
        LANG_KEY_MAP.put("irlights.config.shadows.shadow_blocks",    "方块阴影");
        LANG_KEY_MAP.put("irlights.config.shadows.shadows_live",     "实时阴影");
        LANG_KEY_MAP.put("irlights.config.shadows.shadow_softness",  "阴影柔和度");

        // —— 阴影分类设置项注释 ——
        LANG_KEY_MAP.put("irlights.config.shadows.shadow_quality-comment",
            "阴影深度贴图分辨率。越高越清晰，但占用更多显存（低～40 MB……极高～2.5 GB）。");
        LANG_KEY_MAP.put("irlights.config.shadows.shadow_blocks-comment",
            "从世界方块投射阴影。异形方块（台阶、楼梯、栅栏）按真实形状投影，镂空方块（树叶、栏杆、玻璃门）忽略透明像素。");
        LANG_KEY_MAP.put("irlights.config.shadows.shadows_live-comment",
            "灯光或遮挡物移动时实时更新阴影贴图；若出现残影可关闭。");
        LANG_KEY_MAP.put("irlights.config.shadows.shadow_softness-comment",
            "阴影边缘的柔和程度（通过增大光源表面积实现）。");

        // —— 描边分类设置项标签 ——
        LANG_KEY_MAP.put("irlights.config.outline.outline",               "描边");
        LANG_KEY_MAP.put("irlights.config.outline.outline_target",        "描边目标");
        LANG_KEY_MAP.put("irlights.config.outline.outline_strength",      "描边强度");
        LANG_KEY_MAP.put("irlights.config.outline.outline_pixel_size",    "描边像素宽度");
        LANG_KEY_MAP.put("irlights.config.outline.outline_fresnel_power", "描边菲涅尔强度");
        LANG_KEY_MAP.put("irlights.config.outline.outline_back",          "背面描边");
        LANG_KEY_MAP.put("irlights.config.outline.outline_front",         "正面描边");
        LANG_KEY_MAP.put("irlights.config.outline.outline_front_strength","正面描边强度");
        LANG_KEY_MAP.put("irlights.config.outline.outline_glow",          "描边发光");
        LANG_KEY_MAP.put("irlights.config.outline.outline_glow_strength", "发光强度");

        // —— 描边分类设置项注释 ——
        LANG_KEY_MAP.put("irlights.config.outline.outline-comment",
            "为被光照的实体和方块绘制轮廓描边。");
        LANG_KEY_MAP.put("irlights.config.outline.outline_target-comment",
            "描边作用的物体类型：全部、仅实体或仅方块。");
        LANG_KEY_MAP.put("irlights.config.outline.outline_strength-comment",
            "描边线条的亮度。");
        LANG_KEY_MAP.put("irlights.config.outline.outline_pixel_size-comment",
            "描边线条的宽度（像素）。");
        LANG_KEY_MAP.put("irlights.config.outline.outline_fresnel_power-comment",
            "描边随视线角度变化的程度，值越大越集中于物体边缘。");
        LANG_KEY_MAP.put("irlights.config.outline.outline_back-comment",
            "物体背向相机一侧的描边强度（0 = 关闭背面描边）。");
        LANG_KEY_MAP.put("irlights.config.outline.outline_front-comment",
            "在物体正面（面向相机一侧）也绘制描边。");
        LANG_KEY_MAP.put("irlights.config.outline.outline_front_strength-comment",
            "正面描边的亮度。");
        LANG_KEY_MAP.put("irlights.config.outline.outline_glow-comment",
            "为描边叠加发光效果。");
        LANG_KEY_MAP.put("irlights.config.outline.outline_glow_strength-comment",
            "描边发光的强度。");
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
