package gbeic.bbsplusplus.client.keyframes;

import gbeic.bbsplusplus.keyframes.EquipmentKeyframeTransforms;
import mchorse.bbs_mod.ui.film.replays.UIReplaysEditorUtils;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframeSheet;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframes;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.factories.UIKeyframePropTransform;
import mchorse.bbs_mod.utils.keyframes.Keyframe;
import mchorse.bbs_mod.utils.pose.Transform;
import net.minecraft.world.item.ItemStack;

import java.util.function.Consumer;

/**
 * 六个装备槽位 {@code ItemStack} 关键帧的内嵌变换编辑器。
 *
 * <p>解决的问题：轨道仍然只显示“主手/副手/头盔...”等六条，但右侧属性栏需要像
 * 原生 Transform 关键帧一样编辑坐标、缩放和旋转。实现思路是复用原生关键帧变换
 * 控件，把写入目标改为关键帧上的 BBS++ 附加 {@link Transform}。</p>
 */
public class UIEquipmentKeyframeTransform extends UIKeyframePropTransform
{
    private final UIKeyframes editor;
    private final Keyframe<?> keyframe;

    public UIEquipmentKeyframeTransform(UIKeyframes editor, Keyframe<ItemStack> keyframe)
    {
        this.editor = editor;
        this.keyframe = keyframe;

        this.enableHotkeys();
        this.setTransform(EquipmentKeyframeTransforms.getOrCreate(keyframe));
    }

    @Override
    protected void applyToSelection(Consumer<Transform> consumer)
    {
        forEachSelected((selected) ->
        {
            Transform transform = EquipmentKeyframeTransforms.getOrCreate(selected);

            selected.preNotify();
            consumer.accept(transform);
            selected.postNotify();
        });
    }

    @Override
    protected void applyDuringRecording(int tick, Consumer<Transform> consumer)
    {
        if (this.editor == null || this.keyframe == null)
        {
            return;
        }

        for (UIKeyframeSheet sheet : this.editor.getGraph().getSheets())
        {
            if (!EquipmentKeyframeTransforms.isEquipmentChannel(sheet.channel.getId()) || sheet.selection.getSelected().isEmpty())
            {
                continue;
            }

            Keyframe<?> recorded = this.ensureEquipmentKeyframe(sheet, tick);

            if (recorded != null)
            {
                Transform transform = EquipmentKeyframeTransforms.getOrCreate(recorded);

                recorded.preNotify();
                consumer.accept(transform);
                recorded.postNotify();
            }
        }
    }

    @Override
    protected Transform getRecordedTransform(int tick)
    {
        UIKeyframeSheet sheet = this.editor.getGraph().getSheet(this.keyframe);
        Keyframe<?> recorded = this.ensureEquipmentKeyframe(sheet, tick);

        return recorded == null ? null : EquipmentKeyframeTransforms.getOrCreate(recorded);
    }

    private void forEachSelected(Consumer<Keyframe<?>> consumer)
    {
        if (this.editor == null || this.keyframe == null)
        {
            return;
        }

        for (UIKeyframeSheet sheet : this.editor.getGraph().getSheets())
        {
            if (!EquipmentKeyframeTransforms.isEquipmentChannel(sheet.channel.getId()))
            {
                continue;
            }

            for (Object selected : sheet.selection.getSelected())
            {
                if (selected instanceof Keyframe<?> keyframe)
                {
                    consumer.accept(keyframe);
                }
            }
        }
    }

    private Keyframe<?> ensureEquipmentKeyframe(UIKeyframeSheet sheet, int tick)
    {
        if (sheet == null || !EquipmentKeyframeTransforms.isEquipmentChannel(sheet.channel.getId()))
        {
            return null;
        }

        for (Object candidate : sheet.channel.getKeyframes())
        {
            if (candidate instanceof Keyframe<?> keyframe && keyframe.getTick() == tick)
            {
                return keyframe;
            }
        }

        Transform interpolated = EquipmentKeyframeTransforms.interpolate(sheet.channel, tick).copy();
        Keyframe<?> keyframe = UIReplaysEditorUtils.ensureKeyframe(sheet, tick);

        if (keyframe != null)
        {
            EquipmentKeyframeTransforms.getOrCreate(keyframe).copy(interpolated);
        }

        return keyframe;
    }
}
