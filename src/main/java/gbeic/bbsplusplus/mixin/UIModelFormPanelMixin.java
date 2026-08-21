package gbeic.bbsplusplus.mixin;

import gbeic.bbsplusplus.client.ui.UVTransformEditor;
import gbeic.bbsplusplus.api.ModelFormUVTransform;
import mchorse.bbs_mod.forms.forms.ModelForm;
import mchorse.bbs_mod.l10n.L10n;
import mchorse.bbs_mod.ui.forms.editors.forms.UIForm;
import mchorse.bbs_mod.ui.forms.editors.panels.UIFormPanel;
import mchorse.bbs_mod.ui.forms.editors.panels.UIModelFormPanel;
import mchorse.bbs_mod.ui.framework.elements.UISection;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 在模型形态编辑面板中加入贴图 UV 变换控件。
 * <p>
 * 原版面板只有模型、纹理和颜色选择。这里在同一个选项栏追加“纹理变换”小节，
 * 让用户可以直接编辑 BBS++ 注入到 {@link ModelForm} 上的 UV 偏移与缩放属性。
 * </p>
 */
@Mixin(value = UIModelFormPanel.class, remap = false)
public abstract class UIModelFormPanelMixin extends UIFormPanel<ModelForm>
{
    @Unique private UISection bbspp$uvSection;
    @Unique private UVTransformEditor bbspp$uvEditor;

    private UIModelFormPanelMixin(UIForm<ModelForm> editor)
    {
        super(editor);
    }

    /**
     * 注入目标：{@code UIModelFormPanel} 构造结束。
     * 注入原因：原版模型面板没有 UV 变换输入项。
     * 修改行为：创建偏移、缩放和统一缩放输入控件，并追加到右侧模型选项栏。
     */
    @Inject(method = "<init>", at = @At("TAIL"))
    private void bbspp$addUvControls(UIForm<ModelForm> editor, CallbackInfo ci)
    {
        this.bbspp$uvEditor = new UVTransformEditor((value) ->
        {
            if (this.form instanceof ModelFormUVTransform uv)
            {
                uv.bbspp$getUvTransform().set(new Vector4f(value));
            }
        });

        this.bbspp$uvSection = new UISection(L10n.lang("bbs.ui.forms.editor.model.uv_transform"));
        this.bbspp$uvSection.fields.add(this.bbspp$uvEditor);
        this.bbspp$uvSection.setExpanded(false);

        this.options.add(this.bbspp$uvSection);
    }

    /**
     * 注入目标：{@code UIModelFormPanel#startEdit} 结束。
     * 注入原因：切换表单后，输入框需要同步当前表单已保存或关键帧运行时采样出的值。
     * 修改行为：把当前模型形态的 UV 参数刷新到四个输入控件。
     */
    @Inject(method = "startEdit", at = @At("TAIL"))
    private void bbspp$syncUvControls(ModelForm form, CallbackInfo ci)
    {
        if (!(form instanceof ModelFormUVTransform uv))
        {
            return;
        }

        this.bbspp$uvEditor.setValue(uv.bbspp$getUvTransformValue());
    }
}
