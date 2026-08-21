package gbeic.bbsplusplus.client;

import gbeic.bbsplusplus.BBSAddonsSettings;
import mchorse.bbs_mod.camera.clips.CameraClipContext;
import mchorse.bbs_mod.camera.clips.misc.CurveClip;
import mchorse.bbs_mod.camera.data.Position;
import mchorse.bbs_mod.film.WorldFilmController;
import mchorse.bbs_mod.utils.clips.Clip;
import mchorse.bbs_mod.utils.iris.ShaderCurves;

import java.util.Map;

/**
 * 保存世界内影片播放时由曲线剪辑采样出的光影参数。
 * <p>
 * BBS 原版只让 {@code CameraWorkCameraController} 的上下文参与光影曲线读取，
 * 而右 Ctrl 世界播放第一人称影片时通常只存在 {@link WorldFilmController}。
 * 这个类在每帧渲染开始前主动采样影片相机剪辑，并把结果临时提供给 BBSRendering 的读取点。
 * </p>
 */
public class WorldFilmShaderCurveState
{
    private static final CameraClipContext CONTEXT = new CameraClipContext();
    private static final Position POSITION = new Position();
    private static boolean active;

    public static void clear()
    {
        active = false;
        CONTEXT.clipData.clear();
        CONTEXT.entities.clear();
        CONTEXT.clips = null;
    }

    public static void sample(WorldFilmController controller, float transition)
    {
        if (!isEnabled())
        {
            clear();
            return;
        }

        int tick = Math.max(controller.getTick(), 0);
        CONTEXT.clips = controller.film.camera;
        CONTEXT.entities.clear();
        CONTEXT.entities.putAll(controller.getEntities());
        CONTEXT.clipData.clear();
        CONTEXT.setup(tick, transition);

        POSITION.copy(Position.ZERO);

        for (Clip clip : CONTEXT.clips.getClips(tick))
        {
            CONTEXT.apply(clip, POSITION);
        }

        CONTEXT.currentLayer = 0;
        active = true;
        applyShaderVariables();
    }

    public static Double getValue(String id)
    {
        if (!active)
        {
            return null;
        }

        return CurveClip.getValues(CONTEXT).get(id);
    }

    public static Integer getColorValue(String id)
    {
        if (!active)
        {
            return null;
        }

        return CurveClip.getColorValues(CONTEXT).get(id);
    }

    private static boolean isEnabled()
    {
        return BBSAddonsSettings.worldFilmShaderCurves != null
            && BBSAddonsSettings.worldFilmShaderCurves.get();
    }

    private static void applyShaderVariables()
    {
        for (Map.Entry<String, Double> entry : CurveClip.getValues(CONTEXT).entrySet())
        {
            String id = entry.getKey();

            if (!id.startsWith(CurveClip.SHADER_CURVES_PREFIX))
            {
                continue;
            }

            ShaderCurves.ShaderVariable variable = ShaderCurves.variableMap.get(id.substring(CurveClip.SHADER_CURVES_PREFIX.length()));

            if (variable != null)
            {
                variable.value = entry.getValue().floatValue();
            }
        }
    }
}
