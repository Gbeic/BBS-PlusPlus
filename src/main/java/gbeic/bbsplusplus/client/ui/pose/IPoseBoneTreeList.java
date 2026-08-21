package gbeic.bbsplusplus.client.ui.pose;

import mchorse.bbs_mod.cubic.IModel;

import java.util.function.Predicate;

/**
 * 向正式版的姿势骨骼字符串列表补充树形层级元数据入口。
 *
 * <p>列表内容仍由 BBS 原有搜索和选择逻辑管理，本接口只保存模型父子关系以及搜索时是否
 * 临时平铺显示，因此不会改变骨骼 ID、多选、参数刷或关键帧写入流程。</p>
 */
public interface IPoseBoneTreeList
{
    /** 根据模型生成骨骼层级绘制元数据；传入空模型时清除元数据。 */
    void bbspp$setBoneHierarchy(IModel model, Predicate<String> hidden);

    /** 设置是否因搜索而临时平铺显示。 */
    void bbspp$setBoneTreeFlat(boolean flat);
}
