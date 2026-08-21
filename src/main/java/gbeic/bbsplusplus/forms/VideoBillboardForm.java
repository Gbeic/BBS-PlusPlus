package gbeic.bbsplusplus.forms;

import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.l10n.L10n;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.settings.values.core.ValueLink;
import mchorse.bbs_mod.settings.values.numeric.ValueBoolean;
import mchorse.bbs_mod.settings.values.numeric.ValueFloat;
import mchorse.bbs_mod.settings.values.numeric.ValueInt;

/**
 * 视频广告牌形态。
 *
 * 用于把 BBS 资产目录中的视频文件作为可被影片时间轴驱动的平面纹理渲染。
 * 表单只保存 `video/xxx.mp4` 这样的相对路径，真正的解码和 OpenGL 纹理由可选依赖
 * MediaPlayer-BBS 提供，避免 BBS++ 直接持有 native 解码实现。
 */
public class VideoBillboardForm extends Form
{
    public static final int OUT_OF_RANGE_HOLD = 0;
    public static final int OUT_OF_RANGE_LOOP = 1;
    public static final int OUT_OF_RANGE_HIDE = 2;
    private static final String L10N_NAME = "bbs.ui.forms.video_billboard";

    public final ValueLink video = new ValueLink("video", null);
    public final ValueFloat width = new ValueFloat("width", 1F, 0F, Float.POSITIVE_INFINITY);
    public final ValueFloat height = new ValueFloat("height", 1F, 0F, Float.POSITIVE_INFINITY);
    public final ValueFloat offsetSeconds = new ValueFloat("offsetSeconds", 0F);
    public final ValueFloat speed = new ValueFloat("speed", 1F);
    public final ValueBoolean paused = new ValueBoolean("paused", false);
    public final ValueBoolean restart = new ValueBoolean("restart", false);
    public final ValueBoolean loop = new ValueBoolean("loop", false);
    public final ValueFloat loopStart = new ValueFloat("loopStart", 0F, 0F, Float.POSITIVE_INFINITY);
    public final ValueFloat loopEnd = new ValueFloat("loopEnd", 0F, 0F, Float.POSITIVE_INFINITY);
    public final ValueInt outOfRange = new ValueInt("outOfRange", OUT_OF_RANGE_HOLD, OUT_OF_RANGE_HOLD, OUT_OF_RANGE_HIDE);
    public final ValueBoolean keepAspectRatio = new ValueBoolean("keepAspectRatio", true);
    public final ValueBoolean billboard = new ValueBoolean("billboard", false);
    public final ValueBoolean shaded = new ValueBoolean("shaded", false);

    public VideoBillboardForm()
    {
        super();

        this.add(this.video);
        this.add(this.width);
        this.add(this.height);
        this.add(this.offsetSeconds);
        this.add(this.speed);
        this.add(this.paused);
        this.add(this.restart);
        this.add(this.loop);
        this.add(this.loopStart);
        this.add(this.loopEnd);
        this.add(this.outOfRange);
        this.add(this.keepAspectRatio);
        this.add(this.billboard);

        // shaded 只是画面显示开关，不属于动画属性，隐藏其关键帧轨道。
        this.shaded.invisible();
        this.add(this.shaded);
    }

    @Override
    public String getDefaultDisplayName()
    {
        Link link = this.video.get();

        if (link == null || link.path == null || link.path.isEmpty())
        {
            return localizedName();
        }

        int slash = link.path.lastIndexOf('/');
        String name = slash >= 0 ? link.path.substring(slash + 1) : link.path;
        int dot = name.lastIndexOf('.');

        return dot > 0 ? name.substring(0, dot) : name;
    }

    private static String localizedName()
    {
        try
        {
            String text = L10n.lang(L10N_NAME).get();

            return text != null && !text.equals(L10N_NAME) ? text : "Video Billboard";
        }
        catch (Exception e)
        {
            return "Video Billboard";
        }
    }
}
