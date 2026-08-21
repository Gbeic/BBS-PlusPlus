package gbeic.bbsplusplus.mixin;

import gbeic.bbsplusplus.client.ui.pose.IPoseBoneTreeList;
import mchorse.bbs_mod.ui.framework.elements.input.text.UITextbox;
import mchorse.bbs_mod.ui.utils.pose.UIBoneList;
import mchorse.bbs_mod.ui.utils.pose.UIPoseBoneStringList;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 同步姿势骨骼搜索框与树形列表的显示模式。
 *
 * <p>搜索结果缺少未命中的父节点，继续绘制原层级连接线会制造错误的结构暗示，因此搜索期间
 * 临时平铺完整骨骼名称；清空搜索后立即恢复模型树。</p>
 */
@Mixin(UIBoneList.class)
public class UIBoneListTreeSearchMixin
{
    @Shadow
    @Final
    public UIPoseBoneStringList list;

    @Shadow
    @Final
    private UITextbox search;

    /**
     * 注入目标：骨骼列表根据搜索词重新填充可见项目之前。
     * 注入原因：原版通过重填列表实现搜索，没有调用 {@code UIList.filter}，树形绘制层无法自行判断搜索状态。
     * 修改后的行为：有搜索词时切换为平铺，清空搜索词时恢复树形层级。
     */
    @Inject(method = "filter", at = @At("HEAD"), remap = false)
    private void bbspp$syncBoneTreeSearchMode(boolean reset, CallbackInfo ci)
    {
        boolean flat = !this.search.getText().trim().isEmpty();

        ((IPoseBoneTreeList) (Object) this.list).bbspp$setBoneTreeFlat(flat);
    }
}
