package gbeic.bbsplusplus.client.ui;

import mchorse.bbs_mod.l10n.L10n;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIIcon;
import mchorse.bbs_mod.ui.framework.elements.input.UITrackpad;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.ui.utils.UIConstants;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.colors.Colors;
import org.joml.Vector4f;

import java.util.function.Consumer;

/**
 * 广告牌 U/V 缩放的紧凑编辑控件。
 * <p>
 * 布局与原生 Transform 的缩放行一致，缩放图标可切换独立缩放和统一缩放；
 * 图标本身不切换激活态，避免 BBS 图标皮肤在重排后丢失。
 * </p>
 */
public class UVScaleEditor extends UIElement
{
    private final Consumer<Vector4f> callback;
    private final UITrackpad scaleU;
    private final UITrackpad scaleV;
    private final UIIcon scaleIcon;
    private final UIElement scaleRow;

    private boolean uniformScale;
    private boolean syncing;

    public UVScaleEditor(Consumer<Vector4f> callback)
    {
        this.callback = callback;
        this.scaleV = this.trackpad(L10n.lang("bbs.ui.forms.editor.model.uv_scale_v"), Colors.GREEN, (value) -> this.submit(null, value));
        this.scaleU = this.trackpad(L10n.lang("bbs.ui.forms.editor.model.uv_scale_u"), Colors.RED, (value) ->
        {
            if (this.uniformScale)
            {
                this.submit(value, value);
                this.syncing = true;
                this.scaleV.setValue(value);
                this.syncing = false;
            }
            else
            {
                this.submit(value, null);
            }
        });

        this.scaleIcon = new UIIcon(Icons.SCALE, (button) -> this.toggleUniformScale());
        this.scaleIcon.tooltip(L10n.lang("bbs.ui.forms.editor.model.toggle_uv_scale_uniform"));
        this.scaleIcon.wh(UIConstants.CONTROL_HEIGHT, UIConstants.CONTROL_HEIGHT);
        this.scaleIcon.hoverColor = Colors.WHITE;

        this.w(190).h(UIConstants.CONTROL_HEIGHT).column(0).stretch().vertical();
        this.add(this.scaleRow = UI.row(2, 0, UIConstants.CONTROL_HEIGHT, this.scaleIcon, this.scaleU, this.scaleV));
    }

    public void setValue(Vector4f value)
    {
        Vector4f safe = value == null ? new Vector4f(1F) : value;

        this.syncing = true;
        this.scaleU.setValue(safe.x);
        this.scaleV.setValue(safe.y);
        this.syncing = false;
    }

    private UITrackpad trackpad(IKey tooltip, int color, Consumer<Float> callback)
    {
        UITrackpad trackpad = new UITrackpad((value) ->
        {
            if (!this.syncing)
            {
                callback.accept(value.floatValue());
            }
        }).increment(0.01D).values(0.01D, 0.001D, 0.1D).onlyNumbers();

        trackpad.tooltip(tooltip);
        trackpad.textbox.setColor(color);

        return trackpad;
    }

    private void toggleUniformScale()
    {
        this.uniformScale = !this.uniformScale;
        this.scaleRow.removeAll();

        if (this.uniformScale)
        {
            this.scaleRow.add(this.scaleIcon, this.scaleU);
        }
        else
        {
            this.scaleRow.add(this.scaleIcon, this.scaleU, this.scaleV);
        }

        UIElement parentContainer = this.getParentContainer();

        if (parentContainer != null)
        {
            parentContainer.resize();
        }
        else if (this.getParent() != null)
        {
            this.getParent().resize();
        }
    }

    private void submit(Float scaleU, Float scaleV)
    {
        Vector4f value = new Vector4f(
            scaleU == null ? (float) this.scaleU.getValue() : scaleU,
            scaleV == null ? (float) this.scaleV.getValue() : scaleV,
            1F,
            1F
        );

        if (this.callback != null)
        {
            this.callback.accept(value);
        }
    }
}
