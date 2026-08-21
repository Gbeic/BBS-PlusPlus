package gbeic.bbsplusplus.client.settings;

import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlay;
import mchorse.bbs_mod.ui.utils.presets.UICopyPasteController;
import mchorse.bbs_mod.utils.presets.PresetManager;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * 管理 BBS++ 的视频录制预设。
 * <p>
 * 该类把 BBS 视频设置分类中的全部参数保存到独立的预设目录，避免与 BBS
 * 自带的分辨率预设共用数据或互相覆盖；加载预设时只修改文件中存在的字段，
 * 这样可以对旧版本或手动编辑过的预设保持一定兼容性。
 * </p>
 */
public final class VideoSettingsPresets
{
    private static final PresetManager MANAGER = new PresetManager(
        BBSMod.getSettingsPath("bbsplusplus/video_settings")
    );

    private VideoSettingsPresets()
    {}

    /**
     * 创建连接到视频设置页面的预设控制器。
     *
     * @param panel 视频设置所在的 BBS 设置页面
     * @return 使用 BBS++ 独立目录的预设控制器
     */
    public static UICopyPasteController createController(mchorse.bbs_mod.settings.ui.UISettingsOverlayPanel panel)
    {
        return new UICopyPasteController(MANAGER, "_BBSPlusPlusVideoSettings")
            .supplier(VideoSettingsPresets::createCurrent)
            .consumer((map, mouseX, mouseY) ->
            {
                apply(map);
                panel.refresh();
            });
    }

    /**
     * 打开 BBS++ 视频预设专用管理界面。
     */
    public static void openPresets(UIContext context, UICopyPasteController controller)
    {
        UIVideoSettingsPresetsOverlayPanel panel = new UIVideoSettingsPresetsOverlayPanel(
            controller, context.mouseX, context.mouseY
        );

        UIOverlay.addOverlay(context, panel, 240, 0.5F);
    }

    /**
     * 删除指定的视频预设文件。
     */
    public static boolean delete(String id)
    {
        File file = fileForId(id);

        if (file == null || !file.isFile())
        {
            return false;
        }

        try
        {
            return Files.deleteIfExists(file.toPath());
        }
        catch (IOException e)
        {
            return false;
        }
    }

    /**
     * 将视频预设改名到同一目录中的新 ID，目标已存在时保留原文件不覆盖。
     */
    public static boolean rename(String id, String newId)
    {
        File source = fileForId(id);
        File target = fileForId(newId);

        if (source == null || target == null || !source.isFile() || target.exists())
        {
            return false;
        }

        File parent = target.getParentFile();

        if (parent != null)
        {
            parent.mkdirs();
        }

        try
        {
            Files.move(source.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE);

            return true;
        }
        catch (IOException atomicFailure)
        {
            try
            {
                Files.move(source.toPath(), target.toPath());

                return true;
            }
            catch (IOException ignored)
            {
                return false;
            }
        }
    }

    /**
     * 将输入名称转换成预设 ID 使用的文件名，避免重复添加 json 后缀。
     */
    public static String normalizeName(String name)
    {
        String normalized = name == null ? "" : name.trim();

        if (normalized.toLowerCase(java.util.Locale.ROOT).endsWith(".json"))
        {
            normalized = normalized.substring(0, normalized.length() - 5);
        }

        return normalized;
    }

    private static File fileForId(String id)
    {
        if (id == null || id.isBlank())
        {
            return null;
        }

        try
        {
            Path root = BBSMod.getSettingsPath("bbsplusplus/video_settings").getCanonicalFile().toPath();
            Path file = root.resolve(id + ".json").normalize();

            if (!file.startsWith(root))
            {
                return null;
            }

            return file.toFile();
        }
        catch (IOException e)
        {
            return null;
        }
    }

    private static MapType createCurrent()
    {
        MapType map = new MapType(false);

        map.putString("encoder_path", BBSSettings.videoEncoderPath.get());
        map.putBool("log", BBSSettings.videoEncoderLog.get());
        map.putBool("world_export_resize_window", BBSSettings.worldExportResizeWindow.get());
        map.putInt("width", BBSSettings.videoWidth.get());
        map.putInt("height", BBSSettings.videoHeight.get());
        map.putInt("frame_rate", BBSSettings.videoFrameRate.get());
        map.putBool("limit_frame_rate", BBSSettings.videoLimitFrameRate.get());
        map.putString("export_path", BBSSettings.videoExportPath.get());
        map.putString("filename_format", BBSSettings.videoExportFilenameFormat.get());
        map.putBool("audio", BBSSettings.videoExportAudio.get());
        map.putBool("minecraft_sounds", BBSSettings.videoExportMinecraftSounds.get());
        map.putBool("mute_audio_while_render", BBSSettings.videoMuteAudioWhileRender.get());
        map.putInt("motion_blur", BBSSettings.videoMotionBlur.get());
        map.putInt("held_frames", BBSSettings.videoHeldFrames.get());
        map.putFloat("delay", BBSSettings.videoDelay.get());
        map.putBool("open_folder_after_export", BBSSettings.videoOpenFolderAfterExport.get());
        map.putBool("play_sound_after_export", BBSSettings.videoPlaySoundAfterExport.get());
        map.putString("arguments", BBSSettings.videoArguments.get());
        map.putString("arguments_audio", BBSSettings.videoArgumentsAudio.get());
        map.putString("arguments_mux", BBSSettings.videoArgumentsMux.get());

        return map;
    }

    private static void apply(MapType map)
    {
        if (map == null)
        {
            return;
        }

        setString(map, "encoder_path", BBSSettings.videoEncoderPath::set);
        setBool(map, "log", BBSSettings.videoEncoderLog::set);
        setBool(map, "world_export_resize_window", BBSSettings.worldExportResizeWindow::set);
        setInt(map, "width", BBSSettings.videoWidth::set);
        setInt(map, "height", BBSSettings.videoHeight::set);
        setInt(map, "frame_rate", BBSSettings.videoFrameRate::set);
        setBool(map, "limit_frame_rate", BBSSettings.videoLimitFrameRate::set);
        setString(map, "export_path", BBSSettings.videoExportPath::set);
        setString(map, "filename_format", BBSSettings.videoExportFilenameFormat::set);
        setBool(map, "audio", BBSSettings.videoExportAudio::set);
        setBool(map, "minecraft_sounds", BBSSettings.videoExportMinecraftSounds::set);
        setBool(map, "mute_audio_while_render", BBSSettings.videoMuteAudioWhileRender::set);
        setInt(map, "motion_blur", BBSSettings.videoMotionBlur::set);
        setInt(map, "held_frames", BBSSettings.videoHeldFrames::set);
        setFloat(map, "delay", BBSSettings.videoDelay::set);
        setBool(map, "open_folder_after_export", BBSSettings.videoOpenFolderAfterExport::set);
        setBool(map, "play_sound_after_export", BBSSettings.videoPlaySoundAfterExport::set);
        setString(map, "arguments", BBSSettings.videoArguments::set);
        setString(map, "arguments_audio", BBSSettings.videoArgumentsAudio::set);
        setString(map, "arguments_mux", BBSSettings.videoArgumentsMux::set);
    }

    private static void setString(MapType map, String key, java.util.function.Consumer<String> setter)
    {
        if (map.has(key))
        {
            setter.accept(map.getString(key));
        }
    }

    private static void setBool(MapType map, String key, java.util.function.Consumer<Boolean> setter)
    {
        if (map.has(key))
        {
            setter.accept(map.getBool(key));
        }
    }

    private static void setInt(MapType map, String key, java.util.function.IntConsumer setter)
    {
        if (map.has(key))
        {
            setter.accept(map.getInt(key));
        }
    }

    private static void setFloat(MapType map, String key, java.util.function.Consumer<Float> setter)
    {
        if (map.has(key))
        {
            setter.accept(map.getFloat(key));
        }
    }
}
