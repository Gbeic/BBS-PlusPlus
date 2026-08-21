package gbeic.bbsplusplus;

import java.util.HashMap;
import java.util.Map;

/**
 * BBS VFX 汉化映射表。
 * <p>
 * BBS VFX 当前构建没有公开源码，并且大量界面文字直接通过
 * {@code IKey.constant("英文")} 或运行时写入 {@code LangKey.content}。
 * 本类集中保存这些英文文本与 BBS 语言键的中文翻译，由
 * {@link gbeic.bbsplusplus.mixin.StringKeyMixin} 和
 * {@link gbeic.bbsplusplus.mixin.LangKeyMixin} 在运行时兜底替换。
 * </p>
 */
public class BBSVFXZHTranslator
{
    /** 硬编码英文字符串 -> 中文。 */
    private static final Map<String, String> STRING_MAP = new HashMap<>();

    /** BBS L10n key -> 中文。 */
    private static final Map<String, String> LANG_KEY_MAP = new HashMap<>();

    static
    {
        /* BBS VFX 设置与相机片段 */
        lang("bbs.config.personalization.xavin.title", "BBS VFX");
        lang("bbs.config.personalization.xavin.enabled", "启用");
        lang("bbs.config.personalization.xavin.enabled-comment", "BBS VFX 插件总开关。");
        lang("bbs.config.vfx.title", "视觉特效");
        lang("bbs.config.vfx.ae_tracking", "AE 跟踪导出");
        lang("bbs.config.vfx.ae_tracking-comment", "渲染视频时，同时在视频旁导出 After Effects 摄像机脚本（.jsx）。");
        lang("bbs.config.vfx.glb_export", "GLB 导出");
        lang("bbs.config.vfx.glb_export-comment", "渲染视频时，同时在视频旁为 Blender 导出二进制 glTF（.glb）动画摄像机。");
        lang("bbs.ui.camera.clips.xavin:follow_curve", "跟随曲线");
        lang("bbs.ui.camera.clips.xavin:impact", "冲击帧");

        text("BBS VFX", "BBS VFX");
        text("VFX", "视觉特效");
        text("Enabled", "启用");
        text("Master switch for the BBS VFX addon.", "BBS VFX 插件总开关。");
        text("AE tracking", "AE 跟踪导出");
        text("Also export an After Effects camera script (.jsx) next to the rendered video.", "渲染视频时，同时在视频旁导出 After Effects 摄像机脚本（.jsx）。");
        text("GLB export", "GLB 导出");
        text("Also export a binary glTF (.glb) animated camera next to the rendered video, for Blender.", "渲染视频时，同时在视频旁为 Blender 导出二进制 glTF（.glb）动画摄像机。");
        text("Follow curve", "跟随曲线");
        text("Impact frame", "冲击帧");

        /* 通用按钮、选项与混合模式 */
        text("Default", "默认");
        text("Auto", "自动");
        text("Manual", "手动");
        text("Normal", "正常");
        text("ADD", "加法");
        text("MULTIPLY", "正片叠底");
        text("Screen", "滤色");
        text("OVERLAY", "叠加");
        text("DARKEN", "变暗");
        text("Darken", "变暗");
        text("Lighten", "变亮");
        text("Difference", "差值");
        text("DIFFERENCE", "差值");
        text("Exclusion", "排除");
        text("Color Dodge", "颜色减淡");
        text("COLOR_DODGE", "颜色减淡");
        text("Soft Light", "柔光");
        text("SOFT_LIGHT", "柔光");
        text("Blend mode", "混合模式");
        text("Blend strength", "混合强度");
        text("Blend whole model", "混合整个模型");
        text("Bone blend", "骨骼混合");
        text("Whole model", "整个模型");
        text("Whole-model blend strength", "整个模型混合强度");
        text("form", "表单");

        /* 文本标签增强 */
        text("Tracking", "字距");
        text("Outline", "描边");
        text("Stroke width", "描边宽度");
        text("Stroke color", "描边颜色");
        text("Stroke only", "仅描边");
        text("Font", "字体");
        text("Font size", "字号");
        text("Open fonts folder", "打开字体文件夹");
        text("Text projection", "文字投影");
        text("Projector range", "投影范围");
        text("Projection edge fade", "投影边缘淡出");
        text("Gradient", "渐变");
        text("Gradient start color", "渐变起始颜色");
        text("Gradient end color", "渐变结束颜色");
        text("Gradient angle", "渐变角度");

        /* 曲线表单与跟随曲线片段 */
        text("3d curve", "3D 曲线");
        text("Curve", "曲线");
        text("Add point", "添加点");
        text("Remove", "移除");
        text("Points", "控制点");
        text("Point width", "点宽度");
        text("Closed", "闭合");
        text("Connect (no gaps)", "连接（无间隙）");
        text("Linear", "线性");
        text("Smooth", "平滑");
        text("Ease In", "缓入");
        text("Ease Out", "缓出");
        text("Extrude", "挤出");
        text("Extrude Block", "挤出方块");
        text("Extrude Solid", "实心挤出");
        text("Enable taper", "启用锥度");
        text("Taper", "锥度");
        text("Trim", "裁剪");
        text("Align to curve", "沿曲线对齐");
        text("Curve actor", "曲线演员");
        text("Curve actor to follow (cycles through curve actors)", "要跟随的曲线演员（在曲线演员之间循环）");
        text("Offset", "偏移");
        text("Offset along curve", "沿曲线偏移");
        text("Edit offset keyframes", "编辑偏移关键帧");
        text("(no curve)", "（无曲线）");

        /* AE / GLB 跟踪 */
        text("Tracker", "跟踪器");
        text("tracker", "跟踪器");
        text("tracker (3D null)", "跟踪器（3D 空对象）");
        text("camera tracking", "摄像机跟踪");
        text("camera import", "摄像机导入");
        text("tracker import", "跟踪器导入");
        text("1 Minecraft block = 100 px, recentered on the first frame. Assumes Motion blur = 0.\n", "1 个 Minecraft 方块 = 100 像素，并以第一帧重新居中。假定运动模糊 = 0。\n");

        /* 破坏盒 */
        text("Destruction box", "破坏盒");
        text("Destruction", "破坏");
        text("Capture Destruction Box from wand selection", "从魔杖选区捕获破坏盒");
        text("Undo the last destruction-box cut", "撤销上一次破坏盒切割");
        text("Clear", "清除");
        text("Blocks (debug)", "方块（调试）");
        text("Fill test cube", "填充测试立方体");
        text("Direction / radial", "方向 / 径向");
        text("Point destruction", "点破坏");
        text("Point", "点");
        text("Away from point", "远离点");
        text("Radius / Cone (0 = sphere, 1 = directed)", "半径 / 锥形（0 = 球形，1 = 定向）");
        text("Strength", "强度");
        text("Random", "随机");
        text("Rotation", "旋转");
        text("Far blocks first", "远处方块优先");
        text("Staged collapse", "分阶段坍塌");
        text("Explosion", "爆炸");
        text("Fracture", "破碎");
        text("Physics", "物理");
        text("Rebake", "重新烘焙");
        text("Gravity", "重力");
        text("Ground plane", "地面平面");
        text("World collision", "世界碰撞");
        text("World margin (0 = auto)", "世界边距（0 = 自动）");
        text("Max duration (s)", "最大持续时间（秒）");
        text("Release wave", "释放波");
        text("Wave time (s)", "波动时间（秒）");
        text("Cluster size (1 = off)", "簇大小（1 = 关闭）");
        text("Cluster strength (0 = unbreakable)", "簇强度（0 = 不可破坏）");
        text("Support integrity", "支撑完整性");
        text("Sub-block shatter (0..1 of radius)", "子方块碎裂（半径 0..1）");
        text("Shatter strength", "碎裂强度");
        text("Cube size", "立方体大小");

        /* 姿势拖影与动态线 */
        text("Smear", "拖影");
        text("Motion lines", "动态线");
        text("Smear copies (0 = default 4)", "拖影副本数（0 = 默认 4）");
        text("Fade toward the tail (0 = default)", "向尾端淡出（0 = 默认）");
        text("Length multiplier (0 = default)", "长度倍率（0 = 默认）");
        text("Straight smear along a manual X/Y/Z vector", "沿手动 X/Y/Z 向量生成直线拖影");
        text("Follow the bone's real movement arc (time-rewound), not a manual vector", "跟随骨骼真实运动弧线（回溯时间），而不是手动向量");
        text("Follow the bone\\'s real movement arc (time-rewound), not a manual vector", "跟随骨骼真实运动弧线（回溯时间），而不是手动向量");
        text("Arc trail length in ticks (0 = default 4)", "弧形拖尾长度（刻，0 = 默认 4）");
        text("Copy stretch along the bone length + squash (0 = off; keep 0 for a head turn)", "沿骨骼长度拉伸并压缩副本（0 = 关闭；头部转动建议保持 0）");
        text("Dissolve: noise holes eat the trailing copies (shared)", "溶解：用噪点孔洞吞噬后方副本（共享）");
        text("Overlap density: faint in-between copies merging into a continuous smear (0 = default 16)", "重叠密度：用较淡的中间副本连接成连续拖影（0 = 默认 16）");
        text("Opacity of each overlap copy (0 = default 0.14)", "每个重叠副本的不透明度（0 = 默认 0.14）");
        text("Blunt ends", "平头端点");
        text("Uniform width instead of tapering the ends to a point", "使用等宽线条，不把两端收尖");
        text("A fan of straight streaks you aim by hand (the channel rotation)", "手动控制方向的扇形直线条纹（使用通道旋转）");
        text("Auto: volume radius around the limb. Manual: fan spread. (0 = default)", "自动：肢体周围体积半径。手动：扇形扩散。（0 = 默认）");
        text("Lines follow the bone's real motion arc (volumetric, speed-driven)", "线条跟随骨骼真实运动弧线（体积化，由速度驱动）");
        text("Lines follow the bone\\'s real motion arc (volumetric, speed-driven)", "线条跟随骨骼真实运动弧线（体积化，由速度驱动）");
        text("Lines count (0 = default)", "线条数量（0 = 默认）");
        text("Lines thickness (0 = default)", "线条粗细（0 = 默认）");
        text("Reach: push the line origins down the limb onto the hand (0 = auto)", "延伸：将线条起点沿肢体推向手部（0 = 自动）");
        text("Texture color", "纹理颜色");
        text("Tint lines with the actor's texture instead of the Color picker", "使用演员纹理为线条着色，而不是颜色选择器");
        text("Tint lines with the actor\\'s texture instead of the Color picker", "使用演员纹理为线条着色，而不是颜色选择器");

        /* 冲击帧 */
        text("Impact", "冲击");
        text("Impact preset", "冲击预设");
        text("Preset…", "预设…");
        text("Apply a ready-made impact-frame preset (overwrites the values below)", "应用现成冲击帧预设（会覆盖下方数值）");
        text("Apply a colour preset to silhouette / background / ink (then fine-tune with the pickers)", "将配色预设应用到剪影 / 背景 / 墨迹（随后可用取色器微调）");
        text("Animate the impact parameters over the clip (a keyframed channel overrides its slider)", "在剪辑内为冲击参数制作动画（关键帧通道会覆盖对应滑块）");
        text("Edit keyframes", "编辑关键帧");
        text("Keyframes", "关键帧");
        text("Actor", "演员");
        text("All actors", "所有演员");
        text("Which actor's silhouette to use (cycles through the film's actors; All = every actor)", "选择用于剪影的演员（在影片演员之间循环；全部 = 所有演员）");
        text("Which actor\\'s silhouette to use (cycles through the film\\'s actors; All = every actor)", "选择用于剪影的演员（在影片演员之间循环；全部 = 所有演员）");
        text("Palette: B&W", "配色：黑白");
        text("B&W", "黑白");
        text("B&W + red", "黑白 + 红");
        text("Charcoal Slam (B&W)", "炭笔重击（黑白）");
        text("Color Swap (aura)", "换色光环");
        text("Colour-swap", "换色");
        text("Spider-Verse Pop", "蜘蛛宇宙弹跳");
        text("White Flash Hit", "白闪冲击");
        text("Manga Speed Lines", "漫画速度线");
        text("Silhouette / negative space", "剪影 / 负空间");
        text("Silhouette", "剪影");
        text("Silhouette / negative space amount (redraws the frame as flat actor silhouettes)", "剪影 / 负空间强度（将画面重绘为扁平演员剪影）");
        text("Silhouette fill colour", "剪影填充颜色");
        text("Negative-space background colour", "负空间背景颜色");
        text("Rough directional brush strokes on the silhouette (charcoal redraw)", "剪影上的粗糙定向笔触（炭笔重绘）");
        text("Stroke direction (degrees)", "笔触方向（度）");
        text("Directional streak length", "定向条纹长度");
        text("Stroke frequency (thinner/denser strokes)", "笔触频率（更细 / 更密的笔触）");
        text("Stroke roughness / gaps", "笔触粗糙度 / 间隙");
        text("Stroke angle", "笔触角度");
        text("Stroke length", "笔触长度");
        text("Stroke scale", "笔触缩放");
        text("Stroke rough", "笔触粗糙度");
        text("Strokes", "笔触");
        text("Sil strokes", "剪影笔触");
        text("Sil stroke angle", "笔触角度");
        text("Sil stroke length", "笔触长度");
        text("Sil stroke scale", "笔触缩放");
        text("Sil stroke rough", "笔触粗糙度");
        text("Ink burst", "墨爆");
        text("Rough ink burst amount (radial brush burst from the focus)", "粗糙墨爆强度（从焦点向外径向爆开）");
        text("Ink colour", "墨迹颜色");
        text("Ink reach (outer radius)", "墨迹范围（外半径）");
        text("Ink radius", "墨迹半径");
        text("Ink inner", "墨迹内径");
        text("Ink spikes", "墨迹尖刺");
        text("Ink rough", "墨迹粗糙度");
        text("Ink seed", "墨迹种子");
        text("Clear core radius (negative space at the centre)", "中心清空半径（中心负空间）");
        text("Spike count", "尖刺数量");
        text("Edge roughness / gaps between strokes", "边缘粗糙度 / 笔触间隙");
        text("Ink seed (changes the pattern)", "墨迹种子（改变图案）");
        text("Shockwave", "冲击波");
        text("Shockwave ring opacity", "冲击波环不透明度");
        text("Shockwave progress: ring radius 0..max (animate via the envelope/keyframes)", "冲击波进度：环半径 0..最大值（通过包络 / 关键帧制作动画）");
        text("Shockwave colour", "冲击波颜色");
        text("Shockwave max radius", "冲击波最大半径");
        text("Shockwave ring thickness", "冲击波环宽度");
        text("Shockwave progress", "冲击波进度");
        text("Shockwave radius", "冲击波半径");
        text("Shockwave width", "冲击波宽度");
        text("Flash star", "闪光星芒");
        text("Contact flash star amount (4-point cross-star + glow at the focus)", "接触闪光星芒强度（焦点处四角十字星 + 辉光）");
        text("Flash star ray length", "闪光星芒射线长度");
        text("Flash star ray thickness", "闪光星芒射线粗细");
        text("Flash star central glow size", "闪光星芒中心辉光大小");
        text("Flash star rotation (degrees)", "闪光星芒旋转（度）");
        text("Flash star colour", "闪光星芒颜色");
        text("Flash star size", "星芒大小");
        text("Flash star width", "星芒宽度");
        text("Flash star glow", "星芒辉光");
        text("Flash star rotation", "星芒旋转");
        text("Invert colours", "反转颜色");
        text("Invert", "反转");
        text("Inverted", "反转");
        text("White flash", "白闪");
        text("Grayscale", "灰度");
        text("Threshold", "阈值");
        text("Threshold (duotone) amount", "阈值（双色调）强度");
        text("Threshold level (luma cutoff)", "阈值等级（亮度截断）");
        text("Threshold edge softness", "阈值边缘柔和度");
        text("Threshold dark colour", "阈值暗部颜色");
        text("Threshold light colour", "阈值亮部颜色");
        text("Threshold level", "阈值等级");
        text("Threshold soft", "阈值柔和度");
        text("Chromatic aberration + focus", "色差 + 焦点");
        text("Chromatic aberration (radial from focus)", "色差（从焦点径向扩散）");
        text("Focus", "焦点");
        text("Focus X", "焦点 X");
        text("Focus Y", "焦点 Y");
        text("Focus X (0..1 screen)", "焦点 X（屏幕 0..1）");
        text("Focus Y (0..1 screen)", "焦点 Y（屏幕 0..1）");
        text("Zoom blur", "缩放模糊");
        text("Blur amount", "模糊强度");
        text("Lines (zoom / vertical / horizontal)", "线条（缩放 / 垂直 / 水平）");
        text("Zoom lines amount (concentration lines)", "缩放线强度（集中线）");
        text("Zoom lines", "缩放线");
        text("Lines count", "线条数量");
        text("Lines thickness", "线条粗细");
        text("Lines inner radius (centre kept clear)", "线条内半径（中心留空）");
        text("Lines inner", "线条内径");
        text("Lines seed (changes the pattern)", "线条种子（改变图案）");
        text("Lines seed", "线条种子");
        text("Lines colour", "线条颜色");
        text("Shapes (stars + ring circles)", "形状（星形 + 圆环）");
        text("Shapes amount", "形状强度");
        text("Shapes count", "形状数量");
        text("Shapes size", "形状大小");
        text("Shapes spread", "形状扩散");
        text("Shapes delay", "形状延迟");
        text("Shapes spread around focus", "形状围绕焦点扩散");
        text("Center star", "中心星形");
        text("Center circle", "中心圆环");
        text("Shapes colour", "形状颜色");
        text("Debris beat: delay the scattered shapes by N ticks (land after the impact)", "碎片节拍：将散落形状延迟 N 刻（在冲击后落下）");
        text("Full-screen", "全屏");
        text("Zoom", "缩放");
        text("Vertical", "垂直");
        text("Horizontal", "水平");
        text("Flash", "闪光");
        text("Chroma", "色差");
        text("Thr. level", "阈值等级");
        text("Thr. soft", "阈值柔和");
        text("Radius", "半径");
        text("Inner", "内径");
        text("Spikes", "尖刺");
        text("Rough", "粗糙度");
        text("Seed", "种子");
        text("Progress", "进度");
        text("Size", "大小");
        text("Width", "宽度");
        text("Thickness", "粗细");
        text("Count", "数量");
        text("Spread", "扩散");
        text("Delay", "延迟");
        text("Glow", "辉光");
        text("Shapes", "形状");
        text("Lines", "线条");
    }

    private static void text(String english, String chinese)
    {
        STRING_MAP.put(english, chinese);
    }

    private static void lang(String key, String chinese)
    {
        LANG_KEY_MAP.put(key, chinese);
    }

    /**
     * 根据硬编码英文文本查找中文翻译。
     *
     * @return 中文文字，或 {@code null} 表示不需要替换。
     */
    public static String getChinese(String englishText)
    {
        if (englishText == null)
        {
            return null;
        }

        String exact = STRING_MAP.get(englishText);

        if (exact != null)
        {
            return exact;
        }

        if (englishText.startsWith("Preset: "))
        {
            return "预设：" + translateTail(englishText.substring("Preset: ".length()));
        }

        if (englishText.startsWith("Palette: "))
        {
            return "配色：" + translateTail(englishText.substring("Palette: ".length()));
        }

        if (englishText.startsWith("Point "))
        {
            return "点 " + englishText.substring("Point ".length());
        }

        if (englishText.startsWith("Tracker "))
        {
            return "跟踪器 " + englishText.substring("Tracker ".length());
        }

        if (englishText.matches("\\d+ blocks"))
        {
            return englishText.substring(0, englishText.length() - " blocks".length()) + " 个方块";
        }

        return null;
    }

    /**
     * 根据 BBS L10n key 查找 BBS VFX 的中文翻译。
     *
     * @return 中文文字，或 {@code null} 表示不需要替换。
     */
    public static String getChineseForKey(String langKey)
    {
        return LANG_KEY_MAP.get(langKey);
    }

    private static String translateTail(String tail)
    {
        String translated = STRING_MAP.get(tail);

        return translated != null ? translated : tail;
    }
}
