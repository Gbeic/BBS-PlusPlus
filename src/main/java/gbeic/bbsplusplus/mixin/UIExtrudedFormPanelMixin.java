package gbeic.bbsplusplus.mixin;

import gbeic.bbsplusplus.api.ExtrudedFormUVTransform;
import gbeic.bbsplusplus.client.ui.UVTransformEditor;
import mchorse.bbs_mod.forms.forms.ExtrudedForm;
import mchorse.bbs_mod.l10n.L10n;
import mchorse.bbs_mod.ui.forms.editors.forms.UIForm;
import mchorse.bbs_mod.ui.forms.editors.panels.UIExtrudedFormPanel;
import mchorse.bbs_mod.ui.forms.editors.panels.UIFormPanel;
import mchorse.bbs_mod.ui.framework.elements.input.UITrackpad;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.ui.utils.UIConstants;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 在挤出形态面板中加入表面 UV 偏移、缩放、统一缩放与旋转控件。
 */
@Mixin(value = UIExtrudedFormPanel.class, remap = false)
public abstract class UIExtrudedFormPanelMixin extends UIFormPanel<ExtrudedForm>
{
    @Unique private UVTransformEditor bbspp$uvEditor;
    @Unique private UITrackpad bbspp$uvRotation;

    private UIExtrudedFormPanelMixin(UIForm<ExtrudedForm> editor)
    {
        super(editor);
    }

    /**
     * 注入目标：{@code UIExtrudedFormPanel} 构造结束。
     * 注入原因：原生挤出形态没有纹理变换控件。
     * 修改行为：追加偏移缩放编辑器和旋转角度输入框。
     */
    @Inject(method = "<init>", at = @At("TAIL"))
    private void bbspp$addUvTransformControls(UIForm<ExtrudedForm> editor, CallbackInfo ci)
    {
        this.bbspp$uvEditor = new UVTransformEditor((value) ->
        {
            if (this.form instanceof ExtrudedFormUVTransform uv)
            {
                uv.bbspp$getUvTransform().set(new Vector4f(value));
            }
        });
        this.bbspp$uvRotation = new UITrackpad((value) ->
        {
            if (this.form instanceof ExtrudedFormUVTransform uv)
            {
                uv.bbspp$getUvRotation().set(value.floatValue());
            }
        }).degrees().onlyNumbers();
        this.bbspp$uvRotation.tooltip(L10n.lang("bbs.ui.forms.editor.extruded.uv_rotation"));

        this.options.add(
            UI.label(L10n.lang("bbs.ui.forms.editor.extruded.uv_transform")).marginTop(UIConstants.SECTION_GAP),
            this.bbspp$uvEditor,
            this.bbspp$uvRotation
        );
    }

    /**
     * 注入目标：{@code UIExtrudedFormPanel#startEdit} 结束。
     * 注入原因：切换挤出形态后需要刷新纹理变换输入值。
     * 修改行为：同步当前偏移、缩放和旋转角度。
     */
    @Inject(method = "startEdit", at = @At("TAIL"))
    private void bbspp$syncUvTransformControls(ExtrudedForm form, CallbackInfo ci)
    {
        if (form instanceof ExtrudedFormUVTransform uv)
        {
            this.bbspp$uvEditor.setValue(uv.bbspp$getUvTransformValue());
            this.bbspp$uvRotation.setValue(uv.bbspp$getUvRotation().get());
        }
    }
}
