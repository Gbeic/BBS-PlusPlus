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
 * 模型纹理 UV 变换的紧凑编辑控件。
 * <p>
 * 实现结构刻意贴近 BBS 原生 {@code UITransform}：控件本身是固定高度的纵向布局，
 * 每一行都是 {@code UI.row(2, 0, CONTROL_HEIGHT, 图标, 输入...)}，统一缩放按钮只重排缩放行。
 * 这样可以复用原生 Transform 已验证过的折叠面板布局行为，避免图标消失和高度错位。
 * </p>
 */
public class UVTransformEditor extends UIElement
{
    private final Consumer<Vector4f> callback;

    private final UITrackpad offsetU;
    private final UITrackpad offsetV;
    private final UITrackpad scaleU;
    private final UITrackpad scaleV;
    private final UIIcon offsetIcon;
    private final UIIcon scaleIcon;
    private final UIElement scaleRow;

    private boolean uniformScale;
    private boolean syncing;

    public UVTransformEditor(Consumer<Vector4f> callback)
    {
        super();

        this.callback = callback;

        this.offsetU = this.trackpad(L10n.lang("bbs.ui.forms.editor.model.uv_offset_u"), Colors.RED, (value) -> this.submit(value, null, null, null));
        this.offsetV = this.trackpad(L10n.lang("bbs.ui.forms.editor.model.uv_offset_v"), Colors.GREEN, (value) -> this.submit(null, value, null, null));
        this.scaleV = this.trackpad(L10n.lang("bbs.ui.forms.editor.model.uv_scale_v"), Colors.GREEN, (value) -> this.submit(null, null, null, value));
        this.scaleU = this.trackpad(L10n.lang("bbs.ui.forms.editor.model.uv_scale_u"), Colors.RED, (value) ->
        {
            if (this.uniformScale)
            {
                this.submit(null, null, value, value);
                this.syncing = true;
                this.scaleV.setValue(value);
                this.syncing = false;
            }
            else
            {
                this.submit(null, null, value, null);
            }
        });

        this.offsetIcon = this.staticIcon(Icons.ALL_DIRECTIONS);
        this.scaleIcon = new UIIcon(Icons.SCALE, (b) -> this.toggleUniformScale());
        this.scaleIcon.tooltip(L10n.lang("bbs.ui.forms.editor.model.toggle_uv_scale_uniform"));
        this.scaleIcon.wh(UIConstants.CONTROL_HEIGHT, UIConstants.CONTROL_HEIGHT);
        this.scaleIcon.hoverColor = Colors.WHITE;

        this.w(190).h(UIConstants.CONTROL_HEIGHT * 2 + 2).column(2).stretch().vertical();
        this.add(UI.row(2, 0, UIConstants.CONTROL_HEIGHT, this.offsetIcon, this.offsetU, this.offsetV));
        this.add(this.scaleRow = UI.row(2, 0, UIConstants.CONTROL_HEIGHT, this.scaleIcon, this.scaleU, this.scaleV));
    }

    public void setValue(Vector4f value)
    {
        Vector4f safe = value == null ? new Vector4f(0F, 0F, 1F, 1F) : value;

        this.syncing = true;
        this.offsetU.setValue(safe.x);
        this.offsetV.setValue(safe.y);
        this.scaleU.setValue(safe.z);
        this.scaleV.setValue(safe.w);
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

    private UIIcon staticIcon(mchorse.bbs_mod.ui.utils.icons.Icon icon)
    {
        UIIcon uiIcon = new UIIcon(icon, null);

        uiIcon.wh(UIConstants.CONTROL_HEIGHT, UIConstants.CONTROL_HEIGHT);
        uiIcon.disabledColor = Colors.WHITE;
        uiIcon.hoverColor = Colors.WHITE;
        uiIcon.setEnabled(false);

        return uiIcon;
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

    private void submit(Float offsetU, Float offsetV, Float scaleU, Float scaleV)
    {
        Vector4f value = new Vector4f(
            (float) this.offsetU.getValue(),
            (float) this.offsetV.getValue(),
            (float) this.scaleU.getValue(),
            (float) this.scaleV.getValue()
        );

        if (offsetU != null)
        {
            value.x = offsetU;
        }

        if (offsetV != null)
        {
            value.y = offsetV;
        }

        if (scaleU != null)
        {
            value.z = scaleU;
        }

        if (scaleV != null)
        {
            value.w = scaleV;
        }

        if (this.callback != null)
        {
            this.callback.accept(value);
        }
    }
}
