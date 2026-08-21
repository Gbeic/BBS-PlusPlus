package gbeic.bbsplusplus.client.network;

import gbeic.bbsplusplus.forms.AAAParticleForm;
import gbeic.bbsplusplus.network.TriggerParticlePayload;
import mchorse.bbs_mod.blocks.entities.ModelBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * 手动触发粒子数据包的客户端处理逻辑。
 * <p>
 * 从 {@link gbeic.bbsplusplus.network.BBSPlusPlusNetwork} 中拆出来单独成类，是为了让服务端
 * 永远不会加载到 {@link Minecraft} 等客户端专属类——NeoForge 的物理服务端上这些类并不存在。
 * </p>
 */
public final class TriggerParticleClientHandler
{
    private TriggerParticleClientHandler()
    {}

    /**
     * 把触发信号写回对应模型方块上的 AAA 粒子表单。
     * <p>
     * 调用方已经通过 {@code context.enqueueWork} 切回主线程，这里可以直接访问世界。
     * </p>
     */
    public static void handle(TriggerParticlePayload payload)
    {
        Minecraft client = Minecraft.getInstance();

        if (client.level == null)
        {
            return;
        }

        BlockEntity be = client.level.getBlockEntity(payload.pos());

        if (!(be instanceof ModelBlockEntity modelBe))
        {
            return;
        }

        if (!(modelBe.getProperties().getForm() instanceof AAAParticleForm aaaForm))
        {
            return;
        }

        int triggerIndex = payload.triggerIndex();

        if (triggerIndex >= 0 && triggerIndex < aaaForm.manualTriggerPulse.length)
        {
            aaaForm.manualTriggerPulse[triggerIndex] = true;
        }
    }
}
