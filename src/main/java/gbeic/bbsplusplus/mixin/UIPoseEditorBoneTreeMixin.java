package gbeic.bbsplusplus.mixin;

import gbeic.bbsplusplus.client.ui.pose.IPoseBoneTreeList;
import mchorse.bbs_mod.cubic.IModel;
import mchorse.bbs_mod.ui.utils.pose.PoseBones;
import mchorse.bbs_mod.ui.utils.pose.UIBoneList;
import mchorse.bbs_mod.ui.utils.pose.UIPoseEditor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Collection;
import java.util.Map;

/**
 * 把姿势编辑器当前模型的骨骼父子关系交给 BBS++ 树形列表绘制层。
 *
 * <p>模型形态使用 {@link IModel} 的真实层级，普通骨骼名称集合（例如生物形态）则清除层级并
 * 继续平铺。该类不改动列表内容，所以原有排序、选择和禁用骨骼过滤规则保持不变。</p>
 */
@Mixin(UIPoseEditor.class)
public class UIPoseEditorBoneTreeMixin
{
    @Shadow
    public UIBoneList groups;

    /**
     * 注入目标：姿势编辑器按普通名称集合填充骨骼之前。
     * 注入原因：这类数据没有可用的模型父子关系，必须清除上一次模型留下的树形元数据。
     * 修改后的行为：列表继续使用原版平铺显示，不携带旧模型的缩进和连接线。
     */
    @Inject(method = "fillGroups(Ljava/util/Collection;Z)V", at = @At("HEAD"), remap = false)
    private void bbspp$clearPoseBoneHierarchy(Collection<String> groups, boolean reset, CallbackInfo ci)
    {
        ((IPoseBoneTreeList) this.groups.list).bbspp$setBoneHierarchy(null, null);
    }

    /**
     * 注入目标：姿势编辑器按模型填充骨骼之前。
     * 注入原因：正式版列表只接收排好序的名称，最新 BBS 树形外观还需要模型父子关系元数据。
     * 修改后的行为：生成与最新提交一致的可见骨骼层级；被隐藏骨骼的子节点会提升到当前层级。
     */
    @Inject(
        method = "fillGroups(Lmchorse/bbs_mod/cubic/IModel;Ljava/util/Map;ZLjava/util/Collection;)V",
        at = @At("HEAD"),
        remap = false
    )
    private void bbspp$setPoseBoneHierarchy(IModel model, Map<String, String> flippedParts, boolean reset,
                                             Collection<String> disabledBones, CallbackInfo ci)
    {
        ((IPoseBoneTreeList) this.groups.list).bbspp$setBoneHierarchy(
            model,
            (bone) -> PoseBones.isHidden(disabledBones, bone)
        );
    }
}
