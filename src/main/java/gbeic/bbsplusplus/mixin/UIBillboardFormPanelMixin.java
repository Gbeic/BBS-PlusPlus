package gbeic.bbsplusplus.mixin;

import gbeic.bbsplusplus.api.BillboardFormUVScale;
import gbeic.bbsplusplus.client.ui.UVScaleEditor;
import mchorse.bbs_mod.forms.forms.BillboardForm;
import mchorse.bbs_mod.l10n.L10n;
import mchorse.bbs_mod.ui.forms.editors.forms.UIForm;
import mchorse.bbs_mod.ui.forms.editors.panels.UIBillboardFormPanel;
import mchorse.bbs_mod.ui.forms.editors.panels.UIFormPanel;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.ui.utils.UIConstants;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 在广告牌表单面板中补充 U/V 缩放与统一缩放控件。
 * <p>
 * 原生偏移和旋转控件保持不变，新增缩放行紧随纹理变换区域显示。
 * </p>
 */
@Mixin(value = UIBillboardFormPanel.class, remap = false)
public abstract class UIBillboardFormPanelMixin extends UIFormPanel<BillboardForm>
{
    @Unique private UVScaleEditor bbspp$uvScaleEditor;

    private UIBillboardFormPanelMixin(UIForm<BillboardForm> editor)
    {
        super(editor);
    }

    /**
     * 注入目标：{@code UIBillboardFormPanel} 构造结束。
     * 注入原因：原生纹理变换区域缺少 U/V 缩放。
     * 修改行为：在选项栏追加带统一缩放切换的缩放行。
     */
    @Inject(method = "<init>", at = @At("TAIL"))
    private void bbspp$addUvScaleControls(UIForm<BillboardForm> editor, CallbackInfo ci)
    {
        this.bbspp$uvScaleEditor = new UVScaleEditor((value) ->
        {
            if (this.form instanceof BillboardFormUVScale uvScale)
            {
                uvScale.bbspp$getUvScale().set(new Vector4f(value));
            }
        });

        this.options.add(
            UI.label(L10n.lang("bbs.ui.forms.editor.billboard.uv_scale")).marginTop(UIConstants.SECTION_GAP),
            this.bbspp$uvScaleEditor
        );
    }

    /**
     * 注入目标：{@code UIBillboardFormPanel#startEdit} 结束。
     * 注入原因：切换广告牌形态后需要刷新缩放输入值。
     * 修改行为：把当前 U/V 缩放同步到新增控件。
     */
    @Inject(method = "startEdit", at = @At("TAIL"))
    private void bbspp$syncUvScaleControls(BillboardForm form, CallbackInfo ci)
    {
        if (form instanceof BillboardFormUVScale uvScale)
        {
            this.bbspp$uvScaleEditor.setValue(uvScale.bbspp$getUvScaleValue());
        }
    }
}
