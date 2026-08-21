package gbeic.bbsplusplus;

import gbeic.bbsplusplus.api.KeyframeTrackExtensionRegistry;
import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.l10n.L10n;
import mchorse.bbs_mod.l10n.keys.IKey;

import java.util.HashMap;
import java.util.Map;

/**
 * 关键帧轨道名称本地化工具。
 * <p>
 * 将硬编码的英文轨道名称映射为中文显示名。
 * 通过 {@link BBSAddonsSettings#chineseKeyframeNames} 开关控制。
 * </p>
 */
public class KeyframeLocalizer
{
    private static final Map<String, String> CN = new HashMap<>();
    private static final Map<String, String> ITEM_SPRAY_CN = new HashMap<>();
    private static final Map<String, String> AAA_PARTICLE_CN = new HashMap<>();

    static
    {
        /* 位置与速度 */
        cn("x", "X");
        cn("y", "Y");
        cn("z", "Z");
        cn("fall", "下落距离");

        /* 摄像机关键帧剪辑专用 */
        cn("roll", "横滚");
        cn("fov", "视野");
        cn("distance", "距离");

        /* 旋转 */
        cn("yaw", "偏航");
        cn("pitch", "俯仰");
        cn("headYaw", "头偏航");
        cn("bodyYaw", "体偏航");

        /* 状态 */
        cn("sneaking", "潜行状态");
        cn("sprinting", "疾跑状态");
        cn("grounded", "落地状态");
        cn("damage", "受伤状态");

        /* 手柄摇杆 */
        cn("stick_lx", "左摇杆 X");
        cn("stick_ly", "左摇杆 Y");
        cn("stick_rx", "右摇杆 X");
        cn("stick_ry", "右摇杆 Y");

        /* 手柄扳机 */
        cn("trigger_l", "左扳机");
        cn("trigger_r", "右扳机");

        /* 附加通道 */
        cn("extra1_x", "附加 1 X");
        cn("extra1_y", "附加 1 Y");
        cn("extra2_x", "附加 2 X");
        cn("extra2_y", "附加 2 Y");

        /* 物品栏 */
        cn("item_main_hand", "主手");
        cn("item_off_hand", "副手");
        cn("item_head", "头盔");
        cn("item_chest", "胸甲");
        cn("item_legs", "护腿");
        cn("item_feet", "靴子");
        cn("selected_slot", "选中的格子");

        /* 形态公共属性 */
        cn("visible", "可见性");
        cn("texture", "纹理");
        cn("model", "模型");
        cn("pose", "姿势");
        cn("pose_overlay", "姿势叠加");
        cn("transform", "变换");
        cn("transform_overlay", "变换叠加");
        cn("anchor", "锚点");
        cn("color", "颜色");
        cn("lighting", "光照");
        cn("shape_keys", "形态键");
        cn("actions", "动作");
        cn("bbspp_uv_transform", "纹理变换");
        cn("bbspp_uv_scale", "纹理缩放");
        cn("bbspp_uv_rotation", "UV 旋转");
        cn("text", "文本");
        cn("paused", "暂停");
        cn("settings", "设置");
        cn("hp", "生命值");
        cn("movement_speed", "速度");
        cn("step_height", "上楼高度");

        /* 广告牌 / 挤压体 专用 */
        cn("billboard", "广告牌");
        cn("crop", "裁剪");
        cn("offsetX", "X 偏移");
        cn("offsetY", "Y 偏移");
        cn("rotation", "旋转");
        cn("shading", "阴影");

        /* 文本标签 专用 */
        cn("max", "最大宽度");
        cn("anchorX", "锚点 X");
        cn("anchorY", "锚点 Y");
        cn("anchorLines", "沿锚点对齐");
        cn("shadowX", "阴影 X");
        cn("shadowY", "阴影 Y");
        cn("shadowColor", "阴影颜色");
        cn("background", "背景");
        cn("offset", "偏移");

        /* 方块伪装 专用 */
        cn("block_state", "方块状态");

        /* 物品伪装 专用 */
        cn("item_stack", "物品组");
        cn("modelTransform", "变换模式");

        /* 拖尾 专用 */
        cn("length", "长度");
        cn("loop", "循环");

        /* 结构 专用 */
        cn("structure_file", "结构文件");
        cn("structure", "结构文件");
        cn("biome_id", "群系 ID");
        cn("biome", "群系");
        cn("emit_light", "发光");
        cn("light_intensity", "发光强度");
        cn("structure_light", "结构光照");
        cn("tint_block_entities", "染色方块实体");
        cn("pivot_x", "枢轴 X");
        cn("pivot_y", "枢轴 Y");
        cn("pivot_z", "枢轴 Z");

        /* 粒子属性 */
        cn("velocity", "速度");
        cn("scattering_yaw", "水平散射");
        cn("scattering_pitch", "垂直散射");
        cn("frequency", "频率");
        cn("count", "数量");
        cn("offset_x", "X 偏移");
        cn("offset_y", "Y 偏移");
        cn("offset_z", "Z 偏移");

        /* LumenCore 光源属性 */
        cn("enabled", "启用");
        cn("intensity", "强度");
        cn("range", "范围");
        cn("falloff", "衰减");
        cn("shadow", "阴影");
        cn("shadow_softness", "阴影柔和度");
        cn("volumetric", "体积光束");
        cn("translucent", "照亮半透明");
        cn("alpha_floor", "粒子接光强度");
        cn("bounce", "漫反射弹射");
        cn("gi_strength", "GI 强度");
        cn("inner_cone", "内锥角");
        cn("outer_cone", "外锥角");
        cn("noise_steps", "噪点步数");
        cn("noise_jitter", "噪点抖动");
        cn("width", "宽度");
        cn("height", "高度");
        cn("softness", "柔和度");

        /* IRLights 光源插件专用 */
        cn("radius", "半径");
        cn("inner_radius", "内径");
        cn("beam_strength", "光束强度");
        cn("anisotropy", "各向异性");
        cn("vl_density", "体积光密度");
        cn("bulb_size", "阴影柔和度");
        cn("entities_only", "仅实体");
        cn("blocks_only", "仅方块");
        cn("shadows", "阴影");
        cn("cookie", "遮罩纹理");
        cn("cookie_rotation", "遮罩旋转");
        cn("cookie_scale", "遮罩缩放");
        cn("cookie_invert", "反转遮罩");

        /* BBS VFX 插件专用：文本、曲线、拖影、破坏盒与冲击帧 */
        cn("tracking", "字距");
        cn("stroke_width", "描边宽度");
        cn("stroke_color", "描边颜色");
        cn("stroke_only", "仅描边");
        cn("blend_mode", "混合模式");
        cn("font", "字体");
        cn("font_size", "字号");
        cn("projection", "文字投影");
        cn("proj_range", "投影范围");
        cn("proj_fade", "投影淡出");
        cn("gradient", "渐变");
        cn("gradient_start", "渐变起始");
        cn("gradient_end", "渐变结束");
        cn("gradient_angle", "渐变角度");

        cn("3d curve", "3D 曲线");
        cn("points", "控制点");
        cn("point_", "点");
        cn("resolution", "分辨率");
        cn("closed", "闭合");
        cn("extrude_align", "挤出对齐");
        cn("extrude_block", "挤出方块");
        cn("extrude_blocks", "挤出方块");
        cn("extrude_connect", "挤出连接");
        cn("extrude_scale", "挤出缩放");
        cn("extrude_solid", "实心挤出");
        cn("extrude_spacing", "挤出间距");
        cn("taper_enabled", "启用锥度");
        cn("taper_start", "起始锥度");
        cn("taper_end", "结束锥度");
        cn("taper_curve", "锥度曲线");
        cn("trim_start", "裁剪起点");
        cn("trim_end", "裁剪终点");
        cn("follow_offset", "跟随偏移");
        cn("xavin_follow_curve_progress", "曲线进度");
        cn("xavin_follow_enabled", "跟随曲线");
        cn("xavin_follow_target", "跟随目标");
        cn("xavin_follow_align", "沿曲线对齐");
        cn("xavin_tracker", "AE 跟踪器");

        cn("smear_frames", "涂抹帧");
        cn("motion_lines", "动态线");
        cn("smear", "拖影");
        cn("xavin_smear", "拖影");
        cn("xavin_lines", "动态线");
        cn("xavin_smear_x", "拖影 X");
        cn("xavin_smear_y", "拖影 Y");
        cn("xavin_smear_z", "拖影 Z");
        cn("xavin_smear_count", "拖影数量");
        cn("xavin_smear_falloff", "拖影衰减");
        cn("xavin_smear_dissolve", "拖影溶解");
        cn("xavin_smear_manual", "手动拖影");
        cn("xavin_smear_arc", "弧形拖影");
        cn("xavin_smear_time", "拖影时间");
        cn("xavin_smear_stretch", "拖影拉伸");
        cn("xavin_smear_density", "拖影密度");
        cn("xavin_smear_opacity", "拖影不透明度");
        cn("xavin_smear_lines", "拖影线条");
        cn("xavin_smear_lines_count", "线条数量");
        cn("xavin_smear_lines_width", "线条宽度");
        cn("xavin_smear_lines_spread", "线条扩散");
        cn("xavin_smear_lines_ox", "线条偏移 X");
        cn("xavin_smear_lines_oy", "线条偏移 Y");
        cn("xavin_smear_lines_oz", "线条偏移 Z");
        cn("xavin_smear_lines_texture", "线条纹理颜色");
        cn("xavin_blend_factor", "混合强度");
        cn("xavin_blend_mode", "混合模式");
        cn("xavin$whole", "整个模型");
        cn("blend", "混合");

        cn("destruction", "破坏");
        cn("blocks", "方块");
        cn("dir_pitch", "方向俯仰");
        cn("dir_yaw", "方向偏航");
        cn("dir_strength", "方向强度");
        cn("radial_strength", "径向强度");
        cn("point_mode", "点模式");
        cn("point_away", "远离点");
        cn("point_strength", "点强度");
        cn("point_x", "点 X");
        cn("point_y", "点 Y");
        cn("point_z", "点 Z");
        cn("random_amount", "随机量");
        cn("seed", "随机种子");
        cn("rotation_amount", "旋转量");
        cn("stagger", "错峰");
        cn("invert_order", "反转顺序");
        cn("physics_mode", "物理模式");
        cn("phys_gravity", "物理重力");
        cn("phys_ground", "地面平面");
        cn("phys_world", "世界碰撞");
        cn("phys_world_margin", "世界边距");
        cn("phys_duration", "物理持续时间");
        cn("phys_friction", "摩擦力");
        cn("phys_bounciness", "弹性");
        cn("phys_explosion", "爆炸");
        cn("phys_explosion_radius", "爆炸半径");
        cn("phys_explosion_cone", "爆炸锥形");
        cn("phys_wave", "释放波");
        cn("phys_wave_time", "波动时间");
        cn("phys_cluster_size", "簇大小");
        cn("phys_cluster_strength", "簇强度");
        cn("phys_support", "支撑完整性");
        cn("phys_shatter", "子方块碎裂");
        cn("phys_shatter_strength", "碎裂强度");

        cn("silhouette", "剪影");
        cn("silhouette_kf", "剪影");
        cn("Silhouette", "剪影");
        cn("silColor", "剪影颜色");
        cn("bgColor", "背景颜色");
        cn("target", "目标演员");
        cn("silStrokes", "剪影笔触");
        cn("silStrokes_kf", "剪影笔触");
        cn("silStrokeAngle", "笔触角度");
        cn("silStrokeAngle_kf", "笔触角度");
        cn("silStrokeLength", "笔触长度");
        cn("silStrokeLength_kf", "笔触长度");
        cn("silStrokeScale", "笔触缩放");
        cn("silStrokeScale_kf", "笔触缩放");
        cn("silStrokeRough", "笔触粗糙度");
        cn("silStrokeRough_kf", "笔触粗糙度");
        cn("inkBurst", "墨爆");
        cn("inkBurst_kf", "墨爆");
        cn("inkColor", "墨迹颜色");
        cn("inkRadius", "墨迹半径");
        cn("inkRadius_kf", "墨迹半径");
        cn("inkInner", "墨迹内径");
        cn("inkInner_kf", "墨迹内径");
        cn("inkSpikes", "墨迹尖刺");
        cn("inkSpikes_kf", "墨迹尖刺");
        cn("inkRough", "墨迹粗糙度");
        cn("inkRough_kf", "墨迹粗糙度");
        cn("inkSeed", "墨迹种子");
        cn("inkSeed_kf", "墨迹种子");
        cn("shockwave", "冲击波");
        cn("shockwave_kf", "冲击波");
        cn("Shockwave", "冲击波");
        cn("shockwaveProgress", "冲击波进度");
        cn("shockwaveProgress_kf", "冲击波进度");
        cn("shockwaveColor", "冲击波颜色");
        cn("shockwaveRadius", "冲击波半径");
        cn("shockwaveRadius_kf", "冲击波半径");
        cn("shockwaveWidth", "冲击波宽度");
        cn("shockwaveWidth_kf", "冲击波宽度");
        cn("flashStar", "闪光星芒");
        cn("flashStar_kf", "闪光星芒");
        cn("Flash star", "闪光星芒");
        cn("flashStarSize", "星芒大小");
        cn("flashStarSize_kf", "星芒大小");
        cn("flashStarWidth", "星芒宽度");
        cn("flashStarWidth_kf", "星芒宽度");
        cn("flashStarGlow", "星芒辉光");
        cn("flashStarGlow_kf", "星芒辉光");
        cn("flashStarRotation", "星芒旋转");
        cn("flashStarRotation_kf", "星芒旋转");
        cn("flashStarColor", "星芒颜色");
        cn("invert", "反转");
        cn("invert_kf", "反转");
        cn("flash", "闪光");
        cn("flash_kf", "闪光");
        cn("Flash", "闪光");
        cn("grayscale", "灰度");
        cn("grayscale_kf", "灰度");
        cn("Grayscale", "灰度");
        cn("threshold", "阈值");
        cn("threshold_kf", "阈值");
        cn("Threshold", "阈值");
        cn("thresholdLevel", "阈值等级");
        cn("thresholdLevel_kf", "阈值等级");
        cn("Threshold level", "阈值等级");
        cn("thresholdSoft", "阈值柔和度");
        cn("thresholdSoft_kf", "阈值柔和度");
        cn("Threshold soft", "阈值柔和度");
        cn("darkColor", "暗部颜色");
        cn("lightColor", "亮部颜色");
        cn("chroma", "色差");
        cn("chroma_kf", "色差");
        cn("Chroma", "色差");
        cn("focusX", "焦点 X");
        cn("focusX_kf", "焦点 X");
        cn("Focus X", "焦点 X");
        cn("focusY", "焦点 Y");
        cn("focusY_kf", "焦点 Y");
        cn("Focus Y", "焦点 Y");
        cn("zoomBlur", "缩放模糊");
        cn("zoomBlur_kf", "缩放模糊");
        cn("Zoom blur", "缩放模糊");
        cn("blurMode", "模糊模式");
        cn("zoomLines", "缩放线");
        cn("zoomLines_kf", "缩放线");
        cn("Zoom lines", "缩放线");
        cn("linesCount", "线条数量");
        cn("linesCount_kf", "线条数量");
        cn("Lines count", "线条数量");
        cn("linesThickness", "线条粗细");
        cn("linesThickness_kf", "线条粗细");
        cn("linesInner", "线条内径");
        cn("linesInner_kf", "线条内径");
        cn("linesMode", "线条模式");
        cn("linesSeed", "线条种子");
        cn("linesSeed_kf", "线条种子");
        cn("linesColor", "线条颜色");
        cn("shapes", "形状");
        cn("shapes_kf", "形状");
        cn("Shapes", "形状");
        cn("shapesCount", "形状数量");
        cn("shapesCount_kf", "形状数量");
        cn("Shapes count", "形状数量");
        cn("shapesSize", "形状大小");
        cn("shapesSize_kf", "形状大小");
        cn("Shapes size", "形状大小");
        cn("shapesSpread", "形状扩散");
        cn("shapesSpread_kf", "形状扩散");
        cn("Shapes spread", "形状扩散");
        cn("centerStar", "中心星形");
        cn("centerStar_kf", "中心星形");
        cn("Center star", "中心星形");
        cn("centerCircle", "中心圆环");
        cn("centerCircle_kf", "中心圆环");
        cn("Center circle", "中心圆环");
        cn("shapesColor", "形状颜色");
        cn("shapesDelay", "形状延迟");
        cn("shapesDelay_kf", "形状延迟");

        /* 用户自定义通道 */
        cn("user1", "用户 1");
        cn("user2", "用户 2");
        cn("user3", "用户 3");
        cn("user4", "用户 4");
        cn("user5", "用户 5");
        cn("user6", "用户 6");
        
        /* 物品喷射形态专属。使用上下文映射，避免覆盖同名的光源、粒子、广告牌等公共轨道。 */
        itemSpray("amount", "数量");
        itemSpray("range", "射程");
        itemSpray("emissionShape", "发射形状");
        itemSpray("radius", "半径");
        itemSpray("spawnWidth", "起点宽度");
        itemSpray("spawnHeight", "起点高度");
        itemSpray("spawnOffset", "起点偏移");
        itemSpray("stopAtCenter", "到中心停止");
        itemSpray("scatter", "位置散布");
        itemSpray("speed", "速度");
        itemSpray("speedOffset", "速度散布");
        itemSpray("gravity", "重力");
        itemSpray("gravitySpeed", "重力速度");
        itemSpray("collision", "碰撞");
        itemSpray("frequency", "频率");
        itemSpray("lifetime", "存活时间");
        itemSpray("previewMode", "实时模式");
        itemSpray("simulationTime", "模拟时间");
        itemSpray("seed", "随机种子");
        itemSpray("itemPitch", "物品俯仰");
        itemSpray("itemYaw", "物品偏航");
        itemSpray("itemRoll", "物品横滚");
        itemSpray("rotationSpeedX", "X 轴旋转速度");
        itemSpray("rotationSpeedY", "Y 轴旋转速度");
        itemSpray("rotationSpeedZ", "Z 轴旋转速度");
        itemSpray("rotationRandomSpeed", "随机旋转速度");
        itemSpray("billboard", "始终面向镜头");
        itemSpray("itemScale", "缩放");
        itemSpray("scaleScatter", "缩放散布");
        itemSpray("scaleInTime", "缩放渐入时间");
        itemSpray("showGuide", "世界中显示辅助线");
        itemSpray("color", "颜色");

        /* AAA 粒子形态专属，避免 effect/speed/loop 等通用属性污染其它形态。 */
        aaaParticle("effect", "特效文件");
        aaaParticle("paused", "暂停");
        aaaParticle("restart", "重启");
        aaaParticle("loop", "循环");
        aaaParticle("loopStart", "起始帧");
        aaaParticle("loopEnd", "结束帧");
        aaaParticle("forceFreeze", "智能定格");
        aaaParticle("speed", "速度");
        aaaParticle("particleScale", "缩放");
        aaaParticle("dynamicInput0", "动态参数 0");
        aaaParticle("dynamicInput1", "动态参数 1");
        aaaParticle("dynamicInput2", "动态参数 2");
        aaaParticle("dynamicInput3", "动态参数 3");
        aaaParticle("trigger0", "触发器 0");
        aaaParticle("trigger1", "触发器 1");
        aaaParticle("trigger2", "触发器 2");
        aaaParticle("trigger3", "触发器 3");
        aaaParticle("ignoreDepth", "穿透渲染");
    }

    private static void cn(String key, String chinese)
    {
        CN.put(key, chinese);
    }

    private static void itemSpray(String key, String chinese)
    {
        ITEM_SPRAY_CN.put(key, chinese);
    }

    private static void aaaParticle(String key, String chinese)
    {
        AAA_PARTICLE_CN.put(key, chinese);
    }

    /**
     * 返回指定键的本地化名称，若开关未开启或无翻译则返回 {@code null}。
     * <p>
     * 查找优先级：用户自定义 JSON（L10n）> 硬编码映射（{@link KeyframeLocalizer}）。
     * 支持模型路径前缀：{@code "esey/pose_overlay2"} → {@code "esey/姿势叠加 2"}。
     * 支持数字后缀：{@code "pose_overlay1"} → {@code "姿势叠加 1"}。
     * </p>
     */
    public static String localize(String key)
    {
        if (key == null || key.isEmpty())
        {
            return null;
        }

        if (BBSAddonsSettings.chineseKeyframeNames != null
            && BBSAddonsSettings.chineseKeyframeNames.get())
        {
            String prefix = "";
            String body = key;
            int slash = key.lastIndexOf('/');

            if (slash >= 0)
            {
                prefix = key.substring(0, slash + 1);
                body = key.substring(slash + 1).trim();
            }

            if (body.isEmpty())
            {
                return null;
            }

            KeyframeTrackExtensionRegistry.Extension extension = KeyframeTrackExtensionRegistry.get(body);

            if (extension != null && extension.chineseName() != null && !extension.chineseName().isEmpty())
            {
                return prefix + extension.chineseName();
            }

            /* 1. 优先查用户自定义 JSON（bbs_addons_*.json 中的 bbspp.keyframe.*） */
            String l10nResult = lookupL10n(body);

            if (l10nResult != null)
            {
                return prefix + l10nResult;
            }

            /* 2. 数字后缀匹配：pose_overlay1 → 查 pose_overlay + " 1" */
            String withSuffix = matchWithSuffix(body);

            if (withSuffix != null)
            {
                return prefix + withSuffix;
            }

            /* 3. 硬编码兜底 */
            String exact = CN.get(body);

            if (exact != null)
            {
                return prefix + exact;
            }
        }

        return null;
    }

    /**
     * 返回物品喷射形态专属轨道名称，避免 {@code speed/range/radius} 等通用键污染其它形态。
     */
    public static String localizeItemSpray(String key)
    {
        return localizeWithMap(key, "bbspp.keyframe.item_spray.", ITEM_SPRAY_CN);
    }

    /** 返回 AAA 粒子形态专属轨道名称，避免通用属性名影响其它形态。 */
    public static String localizeAAAParticle(String key)
    {
        return localizeWithMap(key, "bbspp.keyframe.aaa_particle.", AAA_PARTICLE_CN);
    }

    private static String localizeWithMap(String key, String l10nPrefix, Map<String, String> fallback)
    {
        if (key == null || key.isEmpty())
        {
            return null;
        }

        if (BBSAddonsSettings.chineseKeyframeNames != null
            && BBSAddonsSettings.chineseKeyframeNames.get())
        {
            String prefix = "";
            String body = key;
            int slash = key.lastIndexOf('/');

            if (slash >= 0)
            {
                prefix = key.substring(0, slash + 1);
                body = key.substring(slash + 1).trim();
            }

            if (body.isEmpty())
            {
                return null;
            }

            String l10nResult = lookupL10n(body, l10nPrefix);

            if (l10nResult != null)
            {
                return prefix + l10nResult;
            }

            String withSuffix = matchWithSuffix(body, fallback);

            if (withSuffix != null)
            {
                return prefix + withSuffix;
            }

            String exact = fallback.get(body);

            if (exact != null)
            {
                return prefix + exact;
            }
        }

        return null;
    }

    /**
     * 通过 BBS 的 L10n 系统查询 {@code bbspp.keyframe.<body>}。
     * 用户可通过修改 JSON 文件自定义，优先级最高。
     */
    private static String lookupL10n(String body)
    {
        return lookupL10n(body, "bbspp.keyframe.");
    }

    private static String lookupL10n(String body, String l10nPrefix)
    {
        try
        {
            /* 数字后缀处理：先查 baseName 的 L10n */
            String baseName = stripNumericSuffix(body);

            if (baseName != null)
            {
                String translated = getL10nValue(baseName, l10nPrefix);

                if (translated != null)
                {
                    String number = body.substring(baseName.length());

                    return translated + " " + number;
                }
            }

            return getL10nValue(body, l10nPrefix);
        }
        catch (Exception e)
        {
            return null;
        }
    }

    private static String getL10nValue(String key, String l10nPrefix)
    {
        if (BBSModClient.getL10n() == null)
        {
            return null;
        }

        String l10nKey = l10nPrefix + key;
        IKey iKey = L10n.lang(l10nKey);
        String text = iKey.get();

        /* L10n.lang() 找不到翻译时返回 key 本身 */
        if (text != null && !text.equals(l10nKey))
        {
            return text;
        }

        return null;
    }

    /**
     * 分离数字后缀，例如 {@code "pose_overlay1"} → {@code "pose_overlay"}。
     * 若无后缀返回 {@code null}。
     */
    private static String stripNumericSuffix(String key)
    {
        int digits = 0;

        for (int i = key.length() - 1; i >= 0; i--)
        {
            if (Character.isDigit(key.charAt(i)))
            {
                digits++;
            }
            else
            {
                break;
            }
        }

        if (digits == 0)
        {
            return null;
        }

        return key.substring(0, key.length() - digits);
    }

    /**
     * 尝试匹配带数字后缀的键名，例如 {@code "pose_overlay1"} → "姿势叠加 1"。
     */
    private static String matchWithSuffix(String key)
    {
        return matchWithSuffix(key, CN);
    }

    private static String matchWithSuffix(String key, Map<String, String> map)
    {
        String base = stripNumericSuffix(key);

        if (base == null)
        {
            return null;
        }

        String number = key.substring(base.length());
        String translated = map.get(base);

        if (translated != null)
        {
            return translated + " " + number;
        }

        return null;
    }

    /**
     * 返回指定键的中文名称，若无翻译则返回原键。
     */
    public static String localizeOrOriginal(String key)
    {
        String localized = localize(key);

        return localized != null ? localized : key;
    }
}
