package gbeic.bbsplusplus.client.renderer;

import mchorse.bbs_mod.blocks.entities.ModelBlockEntity;
import net.minecraft.registry.RegistryKey;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * 物品喷射的发射源记录。
 *
 * 记录模型方块源的世界、方块坐标和实体身份码，用来判断喷射粒子所属的源方块是否还存在。
 * blockEntityIdentity 只保存模型方块实例身份码，避免反向强引用实体导致 WeakHashMap 无法释放旧条目。
 */
public record ItemSpraySource(RegistryKey<World> worldKey, BlockPos pos, int blockEntityIdentity)
{
    public ItemSpraySource(World world, BlockPos pos, ModelBlockEntity modelBlock)
    {
        this(world.getRegistryKey(), pos.toImmutable(), System.identityHashCode(modelBlock));
    }

    public boolean isAlive(World world)
    {
        ModelBlockEntity modelBlock = this.getModelBlock(world);

        if (modelBlock == null)
        {
            return false;
        }

        try
        {
            return modelBlock.getProperties() != null && modelBlock.getProperties().isEnabled();
        }
        catch (Throwable ignored)
        {
            return false;
        }
    }

    public ModelBlockEntity getModelBlock(World world)
    {
        if (world == null || !world.getRegistryKey().equals(this.worldKey))
        {
            return null;
        }

        if (world.getBlockEntity(this.pos) instanceof ModelBlockEntity modelBlock
            && System.identityHashCode(modelBlock) == this.blockEntityIdentity)
        {
            return modelBlock;
        }

        return null;
    }
}
