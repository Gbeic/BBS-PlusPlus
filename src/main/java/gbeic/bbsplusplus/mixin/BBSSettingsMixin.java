package gbeic.bbsplusplus.mixin;

import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.BBSModClient;
import gbeic.bbsplusplus.BBSAddonsSettings;
import mchorse.bbs_mod.l10n.L10n;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.settings.SettingsBuilder;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 向 {@link BBSSettings} 注册 BBS++ 自定义设置项。
 * <p>
 * 在 {@link BBSSettings#register(SettingsBuilder)} 执行完毕后，
 * 创建独立的 "BBS++" 设置分类页，并将所有插件设置注册在该分类下。
 * </p>
 */
@Mixin(BBSSettings.class)
public class BBSSettingsMixin
{
    /**
     * 注入目标：BBS 设置注册完成之后。
     * 注入原因：BBS++ 需要在同一个设置系统里追加插件自己的设置页。
     * 修改行为：创建 BBS++ 分类，并按功能小标题注册 BBS 增强、物品喷射和 Gizmo 改版设置。
     */
    @Inject(method = "register(Lmchorse/bbs_mod/settings/SettingsBuilder;)V", at = @At("TAIL"), remap = false)
    private static void afterRegister(SettingsBuilder builder, CallbackInfo ci)
    {
        builder.category("bbspp", Icons.DUPE);
        /** BBS 增强功能*/
        BBSAddonsSettings.titleBbsEnhancements = builder.getBoolean("title_bbs_enhancements", false);
        BBSAddonsSettings.chineseKeyframeNames = builder.getBoolean("chinese_keyframe_names", false);
        BBSAddonsSettings.filmAutoGameMode = builder.getBoolean("film_auto_game_mode", false);
        BBSAddonsSettings.preventNegativeKeyframes = builder.getBoolean("prevent_negative_keyframes", false);
        BBSAddonsSettings.reverseTimelineScroll = builder.getBoolean("reverse_timeline_scroll", false);
        BBSAddonsSettings.directParentPicking = builder.getBoolean("direct_parent_picking", false);
        BBSAddonsSettings.poseBoneTreeView = builder.getBoolean("pose_bone_tree_view", false);
        BBSAddonsSettings.firstPersonBobbing = builder.getBoolean("first_person_bobbing", false);
        BBSAddonsSettings.newMorphingPanel = builder.getBoolean("new_morphing_panel", false);
        BBSAddonsSettings.newFilmLibraryUi = builder.getBoolean("new_film_library_ui", false);
        BBSAddonsSettings.enableIrisButton = builder.getBoolean("enable_iris_button", false);
        BBSAddonsSettings.shaderCurvePicker = builder.getBoolean("shader_curve_picker", false);
        BBSAddonsSettings.enableUiKeyframesLayoutLock = builder.getBoolean("enable_ui_keyframes_layout_lock", false);
        BBSAddonsSettings.worldFilmShaderCurves = builder.getBoolean("world_film_shader_curves", false);
        BBSAddonsSettings.allowClipTrackExpansion = builder.getBoolean("allow_clip_track_expansion", false);
        BBSAddonsSettings.privateBbsClipboard = builder.getBoolean("private_bbs_clipboard", false);
        BBSAddonsSettings.filmAltWheelTimelineMode = builder.getInt("film_alt_wheel_timeline_mode", 0, 0, 2).modes(
            bbspp$lang("bbs.config.bbspp.film_alt_wheel_timeline_mode.default"),
            bbspp$lang("bbs.config.bbspp.film_alt_wheel_timeline_mode.disabled"),
            bbspp$lang("bbs.config.bbspp.film_alt_wheel_timeline_mode.horizontal_scroll")
        );

        /* 物品喷射 */
        BBSAddonsSettings.titleItemSpray = builder.getBoolean("title_item_spray", false);
        BBSAddonsSettings.itemSprayFrustumCulling = builder.getBoolean("item_spray_frustum_culling", true);
        BBSAddonsSettings.itemSprayMaxRenderDistance = builder.getInt("item_spray_max_render_distance", 0, 0, 512);
        BBSAddonsSettings.itemSprayMaxRenderedItems = builder.getInt("item_spray_max_rendered_items", 1024, 0, 8192);
        BBSAddonsSettings.itemSprayIRLiteShadowMaxItems = builder.getInt("item_spray_irlite_shadow_max_items", 1024, 0, 4096);

        /* Gizmo 修改 */
        BBSAddonsSettings.titleGizmoModifications = builder.getBoolean("title_gizmo_modifications", false);
        BBSAddonsSettings.gizmoBlockbenchMode = builder.getBoolean("gizmo_blockbench_mode", false);
        BBSAddonsSettings.gizmoTCombined = builder.getBoolean("gizmo_t_combined", false);
        BBSAddonsSettings.gizmoKeepOriginal = builder.getBoolean("gizmo_keep_original", false);

        /* 隐藏设置项 */
        BBSAddonsSettings.textureManagerLayout = (mchorse.bbs_mod.settings.values.numeric.ValueInt) builder.getInt("texture_manager_layout", 0).invisible();
        BBSAddonsSettings.aaaEffectPickerWidth = (mchorse.bbs_mod.settings.values.numeric.ValueInt) builder.getInt("aaa_effect_picker_width", 0, 0, 16384).invisible();
        BBSAddonsSettings.aaaEffectPickerHeight = (mchorse.bbs_mod.settings.values.numeric.ValueInt) builder.getInt("aaa_effect_picker_height", 0, 0, 16384).invisible();
        BBSAddonsSettings.filmLibrarySortMode = (mchorse.bbs_mod.settings.values.numeric.ValueInt) builder.getInt("film_library_sort_mode", 0, 0, 1).invisible();
        BBSAddonsSettings.filmLibraryDefaultLocation = (mchorse.bbs_mod.settings.values.core.ValueString) builder.getString("film_library_default_location", "all").invisible();
        BBSAddonsSettings.morphingDefaultCategory = (mchorse.bbs_mod.settings.values.core.ValueString) builder.getString("morphing_default_category", "home").invisible();

    }

    private static IKey bbspp$lang(String key)
    {
        return () -> BBSModClient.getL10n() == null ? key : L10n.lang(key).get();
    }
}
