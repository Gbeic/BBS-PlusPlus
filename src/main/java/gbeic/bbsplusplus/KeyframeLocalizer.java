package gbeic.bbsplusplus;

import gbeic.bbsplusplus.api.KeyframeTrackExtensionRegistry;
import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.l10n.L10n;
import mchorse.bbs_mod.l10n.keys.IKey;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * 关键帧轨道名称本地化工具。
 * <p>
 * 将硬编码的英文轨道名称映射为中文显示名。
 * 通过 {@link BBSAddonsSettings#chineseKeyframeNames} 开关控制。
 * </p>
 * <p>
 * 轨道映射按形态/插件隔离：
 * 通用轨道（BBS 原版、摄像机剪辑、IRLights、LumenCore 等）走 {@link #CN}；
 * BBS VFX 插件的轨道走 {@link #VFX_CN}（语言键前缀 bbspp.keyframe.vfx.）；
 * VFX LIGHTS 灯插件的轨道走 {@link #VFX_LIGHT_CN}（语言键前缀 bbspp.keyframe.vfxlight.）。
 * 互不干扰，避免同名属性（speed/radius/color 等）的翻译互相覆盖。
 * </p>
 */
public class KeyframeLocalizer
{
    private static final Map<String, String> CN = new HashMap<>();
    private static final Map<String, String> ITEM_SPRAY_CN = new HashMap<>();
    private static final Map<String, String> AAA_PARTICLE_CN = new HashMap<>();
    private static final Map<String, String> VIDEO_BILLBOARD_CN = new HashMap<>();
    private static final Map<String, String> VFX_CN = new HashMap<>();
    private static final Map<String, String> VFX_LIGHT_CN = new HashMap<>();

    /** BBS VFX 插件注入到任意 BBS 表单上的增强轨道（拖影、混合、文字增强、跟随偏移等）。
     *  这些轨道即使所属表单不是 VFX 表单（如 BlockForm 上的 blend 通道），也属于 VFX 插件。 */
    private static final Set<String> VFX_ENHANCEMENT_TRACKS = Set.of(
        "smear", "smear_frames", "motion_lines", "blend", "blend_mode", "follow_offset",
        "tracking", "stroke_width", "stroke_color", "stroke_only", "font", "font_size",
        "projection", "proj_range", "proj_fade", "gradient", "gradient_start", "gradient_end",
        "gradient_angle", "xavin$whole"
    );

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

        /* 文本标签 专用（BBS 原版属性） */
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

        /* 视频广告牌形态专属，避免 width/height/speed/loop 等通用属性影响其它形态。 */
        videoBillboard("video", "视频文件");
        videoBillboard("width", "宽度");
        videoBillboard("height", "高度");
        videoBillboard("offsetSeconds", "起始偏移");
        videoBillboard("speed", "速度");
        videoBillboard("paused", "暂停");
        videoBillboard("restart", "重启");
        videoBillboard("loop", "循环");
        videoBillboard("loopStart", "循环起点");
        videoBillboard("loopEnd", "循环终点");
        videoBillboard("outOfRange", "超出处理");
        videoBillboard("keepAspectRatio", "保持原始比例");
        videoBillboard("billboard", "始终面向镜头");

        /* ═══════════════════════════════════════
           BBS VFX 插件专属轨道（语言键前缀 bbspp.keyframe.vfx.）
           文本增强、曲线、拖影/动态线、混合、破坏盒、光束、穹顶、爆炸、风、冲击帧
           ═══════════════════════════════════════ */

        /* 文本标签增强（LabelFormMixin 注入） */
        vfx("tracking", "字距");
        vfx("stroke_width", "描边宽度");
        vfx("stroke_color", "描边颜色");
        vfx("stroke_only", "仅描边");
        vfx("blend_mode", "混合模式");
        vfx("font", "字体");
        vfx("font_size", "字号");
        vfx("projection", "文字投影");
        vfx("proj_range", "投影范围");
        vfx("proj_fade", "投影淡出");
        vfx("gradient", "渐变");
        vfx("gradient_start", "渐变起始");
        vfx("gradient_end", "渐变结束");
        vfx("gradient_angle", "渐变角度");

        /* 曲线表单（CurveForm） */
        vfx("3d curve", "3D 曲线");
        vfx("points", "控制点");
        vfx("point_", "点");
        vfx("resolution", "分辨率");
        vfx("closed", "闭合");
        vfx("extrude_align", "挤出对齐");
        vfx("extrude_block", "挤出方块");
        vfx("extrude_blocks", "挤出方块");
        vfx("extrude_connect", "挤出连接");
        vfx("extrude_scale", "挤出缩放");
        vfx("extrude_solid", "实心挤出");
        vfx("extrude_spacing", "挤出间距");
        vfx("taper_enabled", "启用锥度");
        vfx("taper_start", "起始锥度");
        vfx("taper_end", "结束锥度");
        vfx("taper_curve", "锥度曲线");
        vfx("trim_start", "裁剪起点");
        vfx("trim_end", "裁剪终点");
        vfx("follow_offset", "跟随偏移");

        /* 拖影与动态线（ModelFormSmearChannelsMixin / WholeFormSmearMixin / PoseTransformSmearMixin） */
        vfx("smear_frames", "涂抹帧");
        vfx("motion_lines", "动态线");
        vfx("smear", "拖影");
        vfx("xavin_smear", "拖影");
        vfx("xavin_lines", "动态线");
        vfx("xavin_smear_x", "拖影 X");
        vfx("xavin_smear_y", "拖影 Y");
        vfx("xavin_smear_z", "拖影 Z");
        vfx("xavin_smear_count", "拖影数量");
        vfx("xavin_smear_falloff", "拖影衰减");
        vfx("xavin_smear_dissolve", "拖影溶解");
        vfx("xavin_smear_manual", "手动拖影");
        vfx("xavin_smear_arc", "弧形拖影");
        vfx("xavin_smear_time", "拖影时间");
        vfx("xavin_smear_stretch", "拖影拉伸");
        vfx("xavin_smear_density", "拖影密度");
        vfx("xavin_smear_opacity", "拖影不透明度");
        vfx("xavin_smear_lines", "拖影线条");
        vfx("xavin_smear_lines_count", "线条数量");
        vfx("xavin_smear_lines_width", "线条宽度");
        vfx("xavin_smear_lines_spread", "线条扩散");
        vfx("xavin_smear_lines_ox", "线条偏移 X");
        vfx("xavin_smear_lines_oy", "线条偏移 Y");
        vfx("xavin_smear_lines_oz", "线条偏移 Z");
        vfx("xavin_smear_lines_texture", "线条纹理颜色");
        vfx("xavin_blend_factor", "混合强度");
        vfx("xavin_blend_mode", "混合模式");
        vfx("blend", "混合");
        vfx("xavin$whole", "整个模型");

        /* 跟随曲线片段与 AE 跟踪 */
        vfx("xavin_follow_curve_progress", "曲线进度");
        vfx("xavin_follow_enabled", "跟随曲线");
        vfx("xavin_follow_target", "跟随目标");
        vfx("xavin_follow_align", "沿曲线对齐");
        vfx("xavin_tracker", "AE 跟踪器");

        /* 破坏盒与爆炸（DestructionBoxForm / ExplosionForm） */
        vfx("destruction", "破坏");
        vfx("blocks", "方块");
        vfx("dir_pitch", "方向俯仰");
        vfx("dir_yaw", "方向偏航");
        vfx("dir_strength", "方向强度");
        vfx("radial_strength", "径向强度");
        vfx("point_mode", "点模式");
        vfx("point_away", "远离点");
        vfx("point_strength", "点强度");
        vfx("point_x", "点 X");
        vfx("point_y", "点 Y");
        vfx("point_z", "点 Z");
        vfx("random_amount", "随机量");
        vfx("seed", "随机种子");
        vfx("rotation_amount", "旋转量");
        vfx("stagger", "错峰");
        vfx("invert_order", "反转顺序");
        vfx("physics_mode", "物理模式");
        vfx("phys_gravity", "物理重力");
        vfx("phys_ground", "地面平面");
        vfx("phys_world", "世界碰撞");
        vfx("phys_world_margin", "世界边距");
        vfx("phys_duration", "物理持续时间");
        vfx("phys_friction", "摩擦力");
        vfx("phys_bounciness", "弹性");
        vfx("phys_explosion", "爆炸");
        vfx("phys_explosion_radius", "爆炸半径");
        vfx("phys_explosion_cone", "爆炸锥形");
        vfx("phys_wave", "释放波");
        vfx("phys_wave_time", "波动时间");
        vfx("phys_cluster_size", "簇大小");
        vfx("phys_cluster_strength", "簇强度");
        vfx("phys_support", "支撑完整性");
        vfx("phys_shatter", "子方块碎裂");
        vfx("phys_shatter_strength", "碎裂强度");
        vfx("phys_max_bodies", "最大刚体数");

        /* 爆炸专属 */
        vfx("scan_radius", "扫描半径");
        vfx("scan_hemisphere", "半球扫描");
        vfx("wind_strength", "风力强度");
        vfx("wind_front_speed", "前锋速度");
        vfx("wind_decay", "风力衰减");
        vfx("wind_hold", "风力持续");
        vfx("wind_ambient_yaw", "环境风偏航");
        vfx("wind_ambient", "环境风");
        vfx("wind_suction", "吸力");
        vfx("fire_count", "火焰数量");
        vfx("fire_duration", "火焰持续");
        vfx("fire_size", "火焰大小");
        vfx("smoke_scale", "烟雾缩放");
        vfx("dust_scale", "灰尘缩放");
        vfx("mushroom_scale", "蘑菇云缩放");
        vfx("shape", "形状");
        vfx("sphere_radius", "球体半径");
        vfx("sphere_expand", "球体扩张");
        vfx("sphere_heat", "球体热度");
        vfx("sphere_scale", "球体缩放");
        vfx("spark_count", "火花数量");
        vfx("spark_size", "火花大小");
        vfx("spark_speed", "火花速度");
        vfx("spark_gravity", "火花重力");
        vfx("spark_life", "火花寿命");
        vfx("spark_spread", "火花扩散");
        vfx("spark_color_r", "火花颜色 R");
        vfx("spark_color_g", "火花颜色 G");
        vfx("spark_color_b", "火花颜色 B");
        vfx("ground_wave", "地面冲击波");
        vfx("world_reach", "世界破坏范围");
        vfx("haze", "雾霾");
        vfx("bird_count", "鸟群数量");
        vfx("wind_streaks", "风痕");
        vfx("bend_scan_radius", "弯曲扫描半径");
        vfx("bend_radius", "弯曲半径");
        vfx("bend_amount", "弯曲程度");
        vfx("bend_fell", "树木倒下");
        vfx("bend_gusts", "阵风");
        vfx("bend_leaves", "落叶");
        vfx("foliage", "植被");

        /* 光束（BeamForm） */
        vfx("progress", "进度");
        vfx("opacity", "不透明度");
        vfx("duration", "持续时长");
        vfx("height", "高度");
        vfx("radius", "半径");
        vfx("direction", "方向");
        vfx("color", "颜色");
        vfx("rim_color", "边缘光颜色");
        vfx("strands", "光束股数");
        vfx("softness", "柔和度");
        vfx("helix", "螺旋");
        vfx("helix_count", "螺旋数量");
        vfx("helix_radius", "螺旋半径");
        vfx("helix_turns", "螺旋圈数");
        vfx("helix_thickness", "螺旋粗细");
        vfx("helix_spin", "螺旋旋转");
        vfx("rings", "光环");
        vfx("ring_max", "光环最大");
        vfx("ring_rise", "光环上升");
        vfx("ring_thickness", "光环粗细");
        vfx("dashes", "虚线");
        vfx("dash_speed", "虚线速度");
        vfx("ground_radius", "地面半径");
        vfx("destruct_radius", "破坏半径");
        vfx("destruct_depth", "破坏深度");
        vfx("impact_at", "冲击时间");

        /* 穹顶（DomeForm） */
        vfx("max_radius", "最大半径");
        vfx("expand_at", "扩张时间");
        vfx("lull", "静默期");
        vfx("beam_tail", "光束尾迹");
        vfx("beam_height", "光束高度");
        vfx("beam_radius", "光束半径");
        vfx("beam_fade", "光束淡出");
        vfx("height_scale", "高度缩放");
        vfx("rim_power", "边缘光强度");
        vfx("fill", "填充");
        vfx("turbulence", "湍流");
        vfx("swirl_speed", "漩涡速度");
        vfx("segments", "分段数");
        vfx("rings_res", "光环分辨率");
        vfx("lightning", "闪电");
        vfx("dust", "灰尘");
        vfx("flash", "闪光");
        vfx("flash_fade", "闪光淡出");
        vfx("impact_burst", "冲击爆发");
        vfx("base_ring", "底部光环");
        vfx("ring_width", "光环宽度");
        vfx("cracks", "裂纹");
        vfx("crack_color", "裂纹颜色");
        vfx("crack_reach", "裂纹延伸");
        vfx("crack_scale", "裂纹缩放");
        vfx("crack_glow", "裂纹发光");
        vfx("crack_depth", "裂纹深度");
        vfx("smoke", "烟雾");
        vfx("smoke_color", "烟雾颜色");
        vfx("smoke_height", "烟雾高度");
        vfx("smoke_density", "烟雾密度");
        vfx("smoke_rise", "烟雾上升");
        vfx("clear_span", "清空范围");

        /* 风（WindForm） */
        vfx("scanned", "已扫描");
        vfx("wind_type", "风类型");
        vfx("strength", "强度");
        vfx("streaks", "风痕");
        vfx("leaves", "落叶");
        vfx("core_radius", "核心半径");
        vfx("funnel_flare", "漏斗扩散");
        vfx("funnel_height", "漏斗高度");
        vfx("swirl", "漩涡");
        vfx("updraft", "上升气流");
        vfx("suction", "吸力");
        vfx("sway", "摇摆");
        vfx("sway_amount", "摇摆程度");

        /* 冲击帧相机片段（ImpactClip）与跟随曲线片段（FollowCurveClip） */
        vfx("silhouette", "剪影");
        vfx("silhouette_kf", "剪影");
        vfx("Silhouette", "剪影");
        vfx("silColor", "剪影颜色");
        vfx("bgColor", "背景颜色");
        vfx("target", "目标演员");
        vfx("silStrokes", "剪影笔触");
        vfx("silStrokes_kf", "剪影笔触");
        vfx("Sil strokes", "剪影笔触");
        vfx("silStrokeAngle", "笔触角度");
        vfx("silStrokeAngle_kf", "笔触角度");
        vfx("Sil stroke angle", "笔触角度");
        vfx("silStrokeLength", "笔触长度");
        vfx("silStrokeLength_kf", "笔触长度");
        vfx("Sil stroke length", "笔触长度");
        vfx("silStrokeScale", "笔触缩放");
        vfx("silStrokeScale_kf", "笔触缩放");
        vfx("Sil stroke scale", "笔触缩放");
        vfx("silStrokeRough", "笔触粗糙度");
        vfx("silStrokeRough_kf", "笔触粗糙度");
        vfx("Sil stroke rough", "笔触粗糙度");
        vfx("inkBurst", "墨爆");
        vfx("inkBurst_kf", "墨爆");
        vfx("Ink burst", "墨爆");
        vfx("inkColor", "墨迹颜色");
        vfx("inkRadius", "墨迹半径");
        vfx("inkRadius_kf", "墨迹半径");
        vfx("Ink radius", "墨迹半径");
        vfx("inkInner", "墨迹内径");
        vfx("inkInner_kf", "墨迹内径");
        vfx("Ink inner", "墨迹内径");
        vfx("inkSpikes", "墨迹尖刺");
        vfx("inkSpikes_kf", "墨迹尖刺");
        vfx("Ink spikes", "墨迹尖刺");
        vfx("inkRough", "墨迹粗糙度");
        vfx("inkRough_kf", "墨迹粗糙度");
        vfx("Ink rough", "墨迹粗糙度");
        vfx("inkSeed", "墨迹种子");
        vfx("inkSeed_kf", "墨迹种子");
        vfx("Ink seed", "墨迹种子");
        vfx("shockwave", "冲击波");
        vfx("shockwave_kf", "冲击波");
        vfx("Shockwave", "冲击波");
        vfx("shockwaveProgress", "冲击波进度");
        vfx("shockwaveProgress_kf", "冲击波进度");
        vfx("Shockwave progress", "冲击波进度");
        vfx("shockwaveColor", "冲击波颜色");
        vfx("shockwaveRadius", "冲击波半径");
        vfx("shockwaveRadius_kf", "冲击波半径");
        vfx("Shockwave radius", "冲击波半径");
        vfx("shockwaveWidth", "冲击波宽度");
        vfx("shockwaveWidth_kf", "冲击波宽度");
        vfx("Shockwave width", "冲击波宽度");
        vfx("flashStar", "闪光星芒");
        vfx("flashStar_kf", "闪光星芒");
        vfx("Flash star", "闪光星芒");
        vfx("flashStarSize", "星芒大小");
        vfx("flashStarSize_kf", "星芒大小");
        vfx("Flash star size", "星芒大小");
        vfx("flashStarWidth", "星芒宽度");
        vfx("flashStarWidth_kf", "星芒宽度");
        vfx("Flash star width", "星芒宽度");
        vfx("flashStarGlow", "星芒辉光");
        vfx("flashStarGlow_kf", "星芒辉光");
        vfx("Flash star glow", "星芒辉光");
        vfx("flashStarRotation", "星芒旋转");
        vfx("flashStarRotation_kf", "星芒旋转");
        vfx("Flash star rotation", "星芒旋转");
        vfx("flashStarColor", "星芒颜色");
        vfx("invert", "反转");
        vfx("invert_kf", "反转");
        vfx("flash_kf", "闪光");
        vfx("Flash", "闪光");
        vfx("grayscale", "灰度");
        vfx("grayscale_kf", "灰度");
        vfx("Grayscale", "灰度");
        vfx("threshold", "阈值");
        vfx("threshold_kf", "阈值");
        vfx("Threshold", "阈值");
        vfx("thresholdLevel", "阈值等级");
        vfx("thresholdLevel_kf", "阈值等级");
        vfx("Threshold level", "阈值等级");
        vfx("thresholdSoft", "阈值柔和度");
        vfx("thresholdSoft_kf", "阈值柔和度");
        vfx("Threshold soft", "阈值柔和度");
        vfx("darkColor", "暗部颜色");
        vfx("lightColor", "亮部颜色");
        vfx("chroma", "色差");
        vfx("chroma_kf", "色差");
        vfx("Chroma", "色差");
        vfx("focusX", "焦点 X");
        vfx("focusX_kf", "焦点 X");
        vfx("Focus X", "焦点 X");
        vfx("focusY", "焦点 Y");
        vfx("focusY_kf", "焦点 Y");
        vfx("Focus Y", "焦点 Y");
        vfx("zoomBlur", "缩放模糊");
        vfx("zoomBlur_kf", "缩放模糊");
        vfx("Zoom blur", "缩放模糊");
        vfx("blurMode", "模糊模式");
        vfx("zoomLines", "缩放线");
        vfx("zoomLines_kf", "缩放线");
        vfx("Zoom lines", "缩放线");
        vfx("linesCount", "线条数量");
        vfx("linesCount_kf", "线条数量");
        vfx("Lines count", "线条数量");
        vfx("linesThickness", "线条粗细");
        vfx("linesThickness_kf", "线条粗细");
        vfx("Lines thickness", "线条粗细");
        vfx("linesInner", "线条内径");
        vfx("linesInner_kf", "线条内径");
        vfx("Lines inner", "线条内径");
        vfx("linesMode", "线条模式");
        vfx("linesSeed", "线条种子");
        vfx("linesSeed_kf", "线条种子");
        vfx("Lines seed", "线条种子");
        vfx("linesColor", "线条颜色");
        vfx("shapes", "形状");
        vfx("shapes_kf", "形状");
        vfx("Shapes", "形状");
        vfx("shapesCount", "形状数量");
        vfx("shapesCount_kf", "形状数量");
        vfx("Shapes count", "形状数量");
        vfx("shapesSize", "形状大小");
        vfx("shapesSize_kf", "形状大小");
        vfx("Shapes size", "形状大小");
        vfx("shapesSpread", "形状扩散");
        vfx("shapesSpread_kf", "形状扩散");
        vfx("Shapes spread", "形状扩散");
        vfx("centerStar", "中心星形");
        vfx("centerStar_kf", "中心星形");
        vfx("Center star", "中心星形");
        vfx("centerCircle", "中心圆环");
        vfx("centerCircle_kf", "中心圆环");
        vfx("Center circle", "中心圆环");
        vfx("shapesColor", "形状颜色");
        vfx("shapesDelay", "形状延迟");
        vfx("shapesDelay_kf", "形状延迟");
        vfx("Shapes delay", "形状延迟");
        vfx("align", "对齐");

        /* ═══════════════════════════════════════
           VFX LIGHTS 灯插件专属轨道（语言键前缀 bbspp.keyframe.vfxlight.）
           点光源 / 聚光灯 / 区域光 / 环境光
           ═══════════════════════════════════════ */

        /* LightForm 基类 */
        vfxLight("color", "颜色");
        vfxLight("intensity", "强度");
        vfxLight("range", "范围");
        vfxLight("falloff_physical", "物理衰减");
        vfxLight("use_temperature", "使用色温");
        vfxLight("temperature", "色温");
        vfxLight("shadows", "阴影");
        vfxLight("shadow_softness", "阴影柔和度");
        vfxLight("air", "空气效果");
        vfxLight("style", "光照风格");
        vfxLight("toon", "卡通着色");
        vfxLight("flicker", "闪烁");
        vfxLight("flicker_speed", "闪烁速度");
        vfxLight("ies_profile", "IES 配光曲线");
        vfxLight("flare", "镜头光晕");
        vfxLight("flare_style", "光晕样式");
        vfxLight("affect_blocks", "照亮方块");
        vfxLight("affect_entities", "照亮实体");
        vfxLight("groups", "灯光组");
        vfxLight("group_filter", "组过滤");

        /* 点光源 / 聚光灯 */
        vfxLight("source_radius", "光源半径");
        vfxLight("angle", "外锥角");
        vfxLight("inner_angle", "内锥角");

        /* 区域光 */
        vfxLight("shape", "形状");
        vfxLight("width", "宽度");
        vfxLight("height", "高度");
        vfxLight("thickness", "厚度");
        vfxLight("two_sided", "双面发光");
        vfxLight("spread", "扩散");
        vfxLight("barn", "遮光板");

        /* 环境光 */
        vfxLight("mode", "模式");
        vfxLight("volume", "体积形状");
        vfxLight("size_x", "尺寸 X");
        vfxLight("size_y", "尺寸 Y");
        vfxLight("size_z", "尺寸 Z");
        vfxLight("edge_falloff", "边缘衰减");
        vfxLight("ground_color", "地面颜色");
        vfxLight("occlusion", "环境遮蔽");

        /* 分组轨道子键：air（空气效果） */
        vfxLight("beam", "光束");
        vfxLight("haze", "雾霾");
        vfxLight("dust", "灰尘");
        vfxLight("dust_size", "灰尘大小");
        vfxLight("prism", "棱镜");
        vfxLight("prism_scale", "棱镜缩放");
        vfxLight("bounce", "反弹");

        /* 分组轨道子键：style（光照风格） */
        vfxLight("rim", "边缘光");
        vfxLight("rim_width", "边缘光宽度");
        vfxLight("sheen", "光泽");
        vfxLight("translucency", "半透明");
        vfxLight("outline", "描边");
        vfxLight("outline_width", "描边宽度");
        vfxLight("outline_blur", "描边模糊");
        vfxLight("outline_inner", "内侧描边");
        vfxLight("outline_target", "描边目标");
        vfxLight("outline_blend", "描边混合");

        /* 分组轨道子键：toon（卡通着色） */
        vfxLight("enabled", "启用");
        vfxLight("softness", "柔和度");
        vfxLight("shadow_tint", "阴影色调");
        vfxLight("shadow_level", "阴影阈值");

        /* 分组轨道子键：barn（遮光板） */
        vfxLight("top", "上");
        vfxLight("bottom", "下");
        vfxLight("left", "左");
        vfxLight("right", "右");
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

    private static void videoBillboard(String key, String chinese)
    {
        VIDEO_BILLBOARD_CN.put(key, chinese);
    }

    private static void vfx(String key, String chinese)
    {
        VFX_CN.put(key, chinese);
    }

    private static void vfxLight(String key, String chinese)
    {
        VFX_LIGHT_CN.put(key, chinese);
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

    /** 返回视频广告牌形态专属轨道名称，避免通用属性名影响其它形态。 */
    public static String localizeVideoBillboard(String key)
    {
        return localizeWithMap(key, "bbspp.keyframe.video_billboard.", VIDEO_BILLBOARD_CN);
    }

    /** 返回 BBS VFX 插件专属轨道名称（语言键前缀 bbspp.keyframe.vfx.）。 */
    public static String localizeVFX(String key)
    {
        return localizeWithMap(key, "bbspp.keyframe.vfx.", VFX_CN);
    }

    /** 返回 VFX LIGHTS 灯插件专属轨道名称（语言键前缀 bbspp.keyframe.vfxlight.）。 */
    public static String localizeVfxLight(String key)
    {
        return localizeWithMap(key, "bbspp.keyframe.vfxlight.", VFX_LIGHT_CN);
    }

    /**
     * 判断轨道 id 是否为 BBS VFX 插件注入到任意 BBS 表单上的增强轨道
     * （拖影、动态线、混合、文字增强、跟随偏移等）。
     * 这些轨道即使所属表单不是 VFX 表单（例如 BlockForm 上的 blend 通道），
     * 也属于 VFX 插件，应按 VFX 专属表汉化，不能依赖通用映射。
     */
    public static boolean isVfxEnhancementTrack(String key)
    {
        if (key == null || key.isEmpty())
        {
            return false;
        }

        if (key.startsWith("xavin_"))
        {
            return true;
        }

        return VFX_ENHANCEMENT_TRACKS.contains(key);
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
}
