package gbeic.bbsplusplus.mixin;

import gbeic.bbsplusplus.client.ui.pose.IPoseBoneSkip;
import mchorse.bbs_mod.data.types.ListType;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.utils.pose.Pose;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * 为每个 Pose 值保存逐骨骼“跳过当前关键帧”元数据。
 *
 * <p>参数仍保留在原版 transforms 中，附加集合只决定插值时该帧是否参与。Mixin 同步
 * 补齐序列化、复制、镜像、相等性和空值判断，使撤销、粘贴及工程保存行为保持一致。</p>
 */
@Mixin(value = Pose.class, remap = false)
public class PoseBoneSkipMixin implements IPoseBoneSkip
{
    @Unique
    private static final String bbspp$SKIPPED_BONES_KEY = "bbspp_skipped_bones";

    @Unique
    private final Set<String> bbspp$skippedBones = new LinkedHashSet<>();

    @Override
    public Set<String> bbspp$getSkippedBones()
    {
        return Collections.unmodifiableSet(this.bbspp$skippedBones);
    }

    @Override
    public boolean bbspp$isBoneSkipped(String bone)
    {
        return bone != null && this.bbspp$skippedBones.contains(bone);
    }

    @Override
    public void bbspp$setBoneSkipped(String bone, boolean skipped)
    {
        if (bone == null || bone.isEmpty())
        {
            return;
        }

        if (skipped)
        {
            this.bbspp$skippedBones.add(bone);
        }
        else
        {
            this.bbspp$skippedBones.remove(bone);
        }
    }

    @Override
    public void bbspp$clearSkippedBones()
    {
        this.bbspp$skippedBones.clear();
    }

    /**
     * 注入目标：{@link Pose#toData(MapType)} 完成原版骨骼变换写入之后。
     * 注入原因：跳过状态不属于原版 transforms，必须独立持久化。
     * 修改后的行为：仅在集合非空时写入 BBS++ 命名空间字段，旧工程输出保持不变。
     */
    @Inject(method = "toData", at = @At("RETURN"))
    private void bbspp$writeSkippedBones(MapType data, CallbackInfo ci)
    {
        if (this.bbspp$skippedBones.isEmpty())
        {
            return;
        }

        ListType bones = new ListType();

        for (String bone : this.bbspp$skippedBones)
        {
            bones.addString(bone);
        }

        data.put(bbspp$SKIPPED_BONES_KEY, bones);
    }

    /**
     * 注入目标：{@link Pose#fromData(MapType)} 完成原版骨骼变换读取之后。
     * 注入原因：工程载入与整帧粘贴需要恢复逐骨骼跳过状态。
     * 修改后的行为：先清空旧集合，再读取存在且非空的骨骼名称。
     */
    @Inject(method = "fromData", at = @At("RETURN"))
    private void bbspp$readSkippedBones(MapType data, CallbackInfo ci)
    {
        this.bbspp$skippedBones.clear();
        ListType bones = data.getList(bbspp$SKIPPED_BONES_KEY);

        for (int i = 0; i < bones.size(); i++)
        {
            String bone = bones.getString(i);

            if (!bone.isEmpty())
            {
                this.bbspp$skippedBones.add(bone);
            }
        }
    }

    /**
     * 注入目标：{@link Pose#copy(Pose)} 完成原版 transforms 深拷贝之后。
     * 注入原因：关键帧复制、撤销快照与插值工厂复制都必须保留跳过状态。
     * 修改后的行为：把源 Pose 的跳过集合复制到目标 Pose。
     */
    @Inject(method = "copy(Lmchorse/bbs_mod/utils/pose/Pose;)V", at = @At("RETURN"))
    private void bbspp$copySkippedBones(Pose pose, CallbackInfo ci)
    {
        this.bbspp$skippedBones.clear();

        if (pose instanceof IPoseBoneSkip skip)
        {
            this.bbspp$skippedBones.addAll(skip.bbspp$getSkippedBones());
        }
    }

    /**
     * 注入目标：{@link Pose#flip(Map)} 完成原版左右骨骼变换交换之后。
     * 注入原因：镜像整帧时，跳过状态也应跟随骨骼名称交换到对侧。
     * 修改后的行为：优先使用模型显式左右映射，否则沿用 Pose 的名称镜像规则。
     */
    @Inject(method = "flip", at = @At("RETURN"))
    private void bbspp$flipSkippedBones(Map<String, String> flippedParts, CallbackInfo ci)
    {
        if (this.bbspp$skippedBones.isEmpty())
        {
            return;
        }

        Set<String> flipped = new LinkedHashSet<>();

        for (String bone : this.bbspp$skippedBones)
        {
            String target = bbspp$getExplicitMirror(flippedParts, bone);

            flipped.add(target == null ? Pose.getMirrorName(bone) : target);
        }

        this.bbspp$skippedBones.clear();
        this.bbspp$skippedBones.addAll(flipped);
    }

    /**
     * 注入目标：{@link Pose#equals(Object)} 返回原版比较结果之后。
     * 注入原因：撤销和脏状态判断必须感知只有跳过集合发生的变化。
     * 修改后的行为：原版 Pose 相等时，再比较双方的跳过骨骼集合。
     */
    @Inject(method = "equals", at = @At("RETURN"), cancellable = true)
    private void bbspp$compareSkippedBones(Object obj, CallbackInfoReturnable<Boolean> cir)
    {
        if (cir.getReturnValue() && obj instanceof IPoseBoneSkip skip)
        {
            cir.setReturnValue(this.bbspp$skippedBones.equals(skip.bbspp$getSkippedBones()));
        }
    }

    /**
     * 注入目标：{@link Pose#isEmpty()} 返回原版 transforms 判断之后。
     * 注入原因：只包含跳过元数据的 Pose 仍然是有意义的关键帧值。
     * 修改后的行为：存在任意跳过骨骼时强制视为非空。
     */
    @Inject(method = "isEmpty", at = @At("RETURN"), cancellable = true)
    private void bbspp$includeSkippedBonesInEmptyCheck(CallbackInfoReturnable<Boolean> cir)
    {
        if (!this.bbspp$skippedBones.isEmpty())
        {
            cir.setReturnValue(false);
        }
    }

    @Unique
    private static String bbspp$getExplicitMirror(Map<String, String> flippedParts, String bone)
    {
        if (flippedParts == null || flippedParts.isEmpty())
        {
            return null;
        }

        String direct = flippedParts.get(bone);

        if (direct != null)
        {
            return direct;
        }

        for (Map.Entry<String, String> entry : flippedParts.entrySet())
        {
            if (bone.equals(entry.getValue()))
            {
                return entry.getKey();
            }
        }

        return null;
    }
}
