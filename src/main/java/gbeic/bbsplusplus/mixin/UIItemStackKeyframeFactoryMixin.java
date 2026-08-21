package gbeic.bbsplusplus.mixin;

import gbeic.bbsplusplus.client.keyframes.UIEquipmentKeyframeTransform;
import gbeic.bbsplusplus.keyframes.EquipmentKeyframeTransforms;
import mchorse.bbs_mod.l10n.L10n;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframeSheet;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframes;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.factories.UIItemStackKeyframeFactory;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.ui.utils.UIConstants;
import mchorse.bbs_mod.utils.keyframes.Keyframe;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 给装备/手持物品关键帧的属性栏追加坐标、缩放和旋转编辑器。
 */
@Mixin(value = UIItemStackKeyframeFactory.class, remap = false)
public class UIItemStackKeyframeFactoryMixin
{
    /**
     * 注入目标：{@link UIItemStackKeyframeFactory} 构造完成后。
     * 注入原因：原版 ItemStack 关键帧属性栏只有物品选择器，不能直接编辑槽位渲染变换。
     * 修改行为：仅当轨道是主手、副手或四个装备槽位时，在同一属性栏追加原生风格 Transform 控件。
     */
    @Inject(method = "<init>", at = @At("RETURN"))
    private void bbspp$addEquipmentTransform(Keyframe<ItemStack> keyframe, UIKeyframes editor, CallbackInfo ci)
    {
        UIKeyframeSheet sheet = editor.getGraph().getSheet(keyframe);

        if (sheet == null || !EquipmentKeyframeTransforms.isEquipmentChannel(sheet.channel.getId()))
        {
            return;
        }

        UIItemStackKeyframeFactory self = (UIItemStackKeyframeFactory) (Object) this;
        UIEquipmentKeyframeTransform transform = new UIEquipmentKeyframeTransform(editor, keyframe);

        self.scroll.add(
            UI.label(L10n.lang("bbspp.ui.keyframes.item_transform")).marginTop(UIConstants.SECTION_GAP),
            transform.marginTop(4)
        );
    }
}
