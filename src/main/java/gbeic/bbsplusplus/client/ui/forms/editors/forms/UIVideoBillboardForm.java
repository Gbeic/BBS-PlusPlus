package gbeic.bbsplusplus.client.ui.forms.editors.forms;

import gbeic.bbsplusplus.client.renderer.VideoBackendBridge;
import gbeic.bbsplusplus.client.ui.utils.UIVideoPicker;
import gbeic.bbsplusplus.forms.VideoBillboardForm;
import mchorse.bbs_mod.l10n.L10n;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.ui.forms.editors.forms.UIForm;
import mchorse.bbs_mod.ui.forms.editors.panels.UIFormPanel;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIButton;
import mchorse.bbs_mod.ui.framework.elements.buttons.UICirculate;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIToggle;
import mchorse.bbs_mod.ui.framework.elements.input.UITrackpad;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.ui.utils.UIConstants;
import mchorse.bbs_mod.ui.utils.icons.Icons;

/**
 * 视频广告牌形态编辑器。
 *
 * 提供视频文件选择、时间轴播放参数、循环区间和显示尺寸设置。
 */
public class UIVideoBillboardForm extends UIForm<VideoBillboardForm>
{
    public UIVideoBillboardForm()
    {
        super();

        this.defaultPanel = new Panel(this);
        this.registerPanel(this.defaultPanel, L10n.lang("bbspp.ui.forms.editors.video_billboard.title"), Icons.FILM);
        this.registerDefaultPanels();
    }

    public static class Panel extends UIFormPanel<VideoBillboardForm>
    {
        public UIElement backendStatus;
        public UIButton pickVideo;
        public UIButton restart;
        public UIToggle paused;
        public UIToggle loop;
        public UITrackpad loopStart;
        public UITrackpad loopEnd;
        public UICirculate outOfRange;
        public UITrackpad offsetSeconds;
        public UITrackpad speed;
        public UITrackpad width;
        public UITrackpad height;
        public UIToggle keepAspectRatio;
        public UIToggle billboard;
        public UIToggle shaded;

        public Panel(UIForm<VideoBillboardForm> editor)
        {
            super(editor);

            this.backendStatus = UI.label(this.getBackendStatusKey()).background();
            this.pickVideo = new UIButton(label("bbspp.ui.forms.editors.video_billboard.pick_video"), (b) -> this.openPicker());
            this.restart = new UIButton(label("bbspp.ui.forms.editors.video_billboard.restart"), (b) -> this.form.restart.set(true));
            this.paused = new UIToggle(label("bbspp.ui.forms.editors.video_billboard.paused"), false, (b) -> this.form.paused.set(b.getValue()));
            this.loop = new UIToggle(label("bbspp.ui.forms.editors.video_billboard.loop"), false, (b) -> this.form.loop.set(b.getValue()));

            this.loopStart = new UITrackpad((v) -> this.form.loopStart.set(v.floatValue())).limit(0);
            this.loopEnd = new UITrackpad((v) -> this.form.loopEnd.set(v.floatValue())).limit(0);
            this.outOfRange = new UICirculate((b) -> this.form.outOfRange.set(b.getValue()));
            this.outOfRange.addLabel(label("bbspp.ui.forms.editors.video_billboard.out_of_range.hold"));
            this.outOfRange.addLabel(label("bbspp.ui.forms.editors.video_billboard.out_of_range.loop"));
            this.outOfRange.addLabel(label("bbspp.ui.forms.editors.video_billboard.out_of_range.hide"));

            this.offsetSeconds = new UITrackpad((v) -> this.form.offsetSeconds.set(v.floatValue()));
            this.speed = new UITrackpad((v) -> this.form.speed.set(v.floatValue())).limit(0);
            this.width = new UITrackpad((v) -> this.form.width.set(v.floatValue())).limit(0.001D);
            this.height = new UITrackpad((v) -> this.form.height.set(v.floatValue())).limit(0.001D);
            this.keepAspectRatio = new UIToggle(label("bbspp.ui.forms.editors.video_billboard.keep_aspect_ratio"), false, (b) -> this.form.keepAspectRatio.set(b.getValue()));
            this.billboard = new UIToggle(label("bbspp.ui.forms.editors.video_billboard.billboard"), false, (b) -> this.form.billboard.set(b.getValue()));
            this.shaded = new UIToggle(label("bbspp.ui.forms.editors.video_billboard.shaded"), false, (b) -> this.form.shaded.set(b.getValue()));

            this.options.add(this.backendStatus);
            this.options.add(section("bbspp.ui.forms.editors.video_billboard.section.file"));
            this.options.add(this.pickVideo);
            this.options.add(section("bbspp.ui.forms.editors.video_billboard.section.playback"));
            this.options.add(this.restart, this.paused, this.loop);
            this.options.add(UI.label(label("bbspp.ui.forms.editors.video_billboard.offset_seconds")), this.offsetSeconds);
            this.options.add(UI.label(label("bbspp.ui.forms.editors.video_billboard.speed")), this.speed);
            this.options.add(UI.label(label("bbspp.ui.forms.editors.video_billboard.loop_range")), this.loopStart, this.loopEnd);
            this.options.add(UI.label(label("bbspp.ui.forms.editors.video_billboard.out_of_range")), this.outOfRange);
            this.options.add(section("bbspp.ui.forms.editors.video_billboard.section.display"));
            this.options.add(UI.label(label("bbspp.ui.forms.editors.video_billboard.width")), this.width);
            this.options.add(UI.label(label("bbspp.ui.forms.editors.video_billboard.height")), this.height);
            this.options.add(this.keepAspectRatio, this.billboard, this.shaded);
        }

        @Override
        public void startEdit(VideoBillboardForm form)
        {
            super.startEdit(form);

            this.pickVideo.label = this.getPickLabel(form.video.get());
            this.paused.setValue(form.paused.get());
            this.loop.setValue(form.loop.get());
            this.loopStart.setValue(form.loopStart.get());
            this.loopEnd.setValue(form.loopEnd.get());
            this.outOfRange.setValue(form.outOfRange.get());
            this.offsetSeconds.setValue(form.offsetSeconds.get());
            this.speed.setValue(form.speed.get());
            this.width.setValue(form.width.get());
            this.height.setValue(form.height.get());
            this.keepAspectRatio.setValue(form.keepAspectRatio.get());
            this.billboard.setValue(form.billboard.get());
            this.shaded.setValue(form.shaded.get());
        }

        private void openPicker()
        {
            UIVideoPicker.open(this.getContext(), (link) ->
            {
                this.form.video.set(link);
                this.pickVideo.label = this.getPickLabel(link);
            });
        }

        private IKey getPickLabel(Link link)
        {
            if (link == null || link.path == null || link.path.isEmpty())
            {
                return label("bbspp.ui.forms.editors.video_billboard.pick_video");
            }

            return label("bbspp.ui.forms.editors.video_billboard.pick_video_selected").format(link.path);
        }

        private IKey getBackendStatusKey()
        {
            if (VideoBackendBridge.isAvailable())
            {
                return label("bbspp.ui.forms.editors.video_billboard.backend_available");
            }

            return label("bbspp.ui.forms.editors.video_billboard.backend_unavailable").format(VideoBackendBridge.getUnavailableReason());
        }

        private static IKey label(String key)
        {
            return L10n.lang(key);
        }

        private static UIElement section(String key)
        {
            return UI.label(label(key)).background().marginTop(UIConstants.SECTION_GAP);
        }
    }
}
