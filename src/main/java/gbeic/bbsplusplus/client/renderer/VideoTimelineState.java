package gbeic.bbsplusplus.client.renderer;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

/**
 * 视频形态与 BBS 影片编辑器之间的轻量运行时状态桥。
 *
 * 它记录当前正在渲染的回放 tick（含帧间浮点插值），并汇总不同时间轴控件的播放头拖动状态。
 * 视频渲染器因此可以使用真实影片时间倒帧，并在拖动期间按固定间隔刷新预览帧而不反复寻帧。
 */
public final class VideoTimelineState
{
    private static final Set<Object> SCRUBBING_SOURCES = Collections.newSetFromMap(new IdentityHashMap<>());
    private static float renderTick;
    private static boolean renderingFilm;

    private VideoTimelineState()
    {
    }

    public static void beginFilmRender(float tick)
    {
        renderTick = tick;
        renderingFilm = true;
    }

    public static void endFilmRender()
    {
        renderingFilm = false;
    }

    public static float getFilmTick(float fallback)
    {
        return renderingFilm ? renderTick : fallback;
    }

    public static void beginScrubbing(Object source)
    {
        if (source != null)
        {
            SCRUBBING_SOURCES.add(source);
        }
    }

    public static void endScrubbing(Object source)
    {
        SCRUBBING_SOURCES.remove(source);
    }

    public static boolean isScrubbing()
    {
        return !SCRUBBING_SOURCES.isEmpty();
    }
}
