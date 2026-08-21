package gbeic.bbsplusplus.client.renderer;

import gbeic.bbsplusplus.forms.ItemSprayForm;
import mchorse.bbs_mod.utils.colors.Color;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.MathHelper;
import org.joml.Matrix3f;
import org.joml.Vector3d;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * 物品喷射的发射与采样器。
 *
 * 负责按频率与数量生成粒子：计算发射形状采样点、速度/位置散布、初始朝向、旋转速度、
 * 缩放散布与确定性种子混合。实时模式与本地预览共用同一套发射逻辑，保证两种模式手感一致。
 */
class ItemSprayEmitter
{
    private final ItemSprayFormRenderer owner;
    private final ItemSprayForm form;
    private final Random random;

    ItemSprayEmitter(ItemSprayFormRenderer owner, ItemSprayForm form, Random random)
    {
        this.owner = owner;
        this.form = form;
        this.random = random;
    }

    /**
     * 按发射频率推进冷却并决定本 tick 是否生成粒子。
     *
     * @param currentCooldown 传入当前冷却计数，返回更新后的冷却计数
     */
    int emit(int currentCooldown, List<SprayedItem> target, Vector3d origin, Matrix3f rotation, ItemSpraySource source)
    {
        int frequency = this.form.frequency.get();
        boolean spawn = false;

        if (frequency <= 1)
        {
            spawn = true;
        }
        else
        {
            if (currentCooldown <= 0)
            {
                spawn = true;
                currentCooldown = frequency;
            }
            else
            {
                currentCooldown--;
            }
        }

        if (spawn)
        {
            this.spawnItems(target, origin, rotation, source);
        }

        return currentCooldown;
    }

    private void spawnItems(List<SprayedItem> target, Vector3d origin, Matrix3f rotation, ItemSpraySource source)
    {
        List<ItemStack> items = new ArrayList<>();
        for (mchorse.bbs_mod.settings.values.mc.ValueItemStack vis : this.form.items.getList())
        {
            if (vis.get() != null && !vis.get().isEmpty()) items.add(vis.get());
        }

        if (items.isEmpty())
        {
            return;
        }

        int amount = this.form.amount.get();
        float speed = this.form.speed.get();
        float range = this.form.range.get();
        boolean useGravity = this.form.gravity.get();
        boolean useCollision = this.form.collision.get();
        Color formColor = this.form.color.get();

        for (int i = 0; i < amount; i++)
        {
            ItemStack stack = items.get(this.random.nextInt(items.size())).copy();
            EmissionSample sample = this.createEmissionSample(this.random);

            // 速度散布
            float currentSpeed = speed;
            float speedOffset = this.form.speedOffset.get();
            if (speedOffset > 0)
            {
                currentSpeed += (this.random.nextFloat() * 2 - 1) * speedOffset;

                if (speed >= 0 && currentSpeed < 0)
                {
                    currentSpeed = 0;
                }
                else if (speed < 0 && currentSpeed > 0)
                {
                    currentSpeed = 0;
                }
            }

            // 局部速度向量
            Vector3f localVel = new Vector3f(sample.direction).normalize().mul(currentSpeed);
            rotation.transform(localVel);
            Vector3d velocity = new Vector3d(localVel.x(), localVel.y(), localVel.z());

            Vector3d pos = new Vector3d(origin);
            Vector3d centerStopTarget = this.shouldStopAtCenter(currentSpeed) ? new Vector3d(origin) : null;
            Vector3f localOffset = new Vector3f(sample.offset);

            rotation.transform(localOffset);
            pos.add(localOffset.x(), localOffset.y(), localOffset.z());

            // 位置散布在发射形状基础上继续叠加，保留旧工程的随机扩散手感。
            float scatter = this.form.scatter.get();
            if (scatter > 0)
            {
                Vector3f localScatter = new Vector3f(
                    (this.random.nextFloat() * 2 - 1) * scatter,
                    (this.random.nextFloat() * 2 - 1) * scatter,
                    (this.random.nextFloat() * 2 - 1) * scatter
                );
                rotation.transform(localScatter);
                pos.add(localScatter.x(), localScatter.y(), localScatter.z());

                if (centerStopTarget != null)
                {
                    centerStopTarget.add(localScatter.x(), localScatter.y(), localScatter.z());
                }
            }

            // 存活时间。负速度表示反向发射，也应该按速度绝对值计算射程对应的寿命。
            int maxAge = this.form.lifetime.get();
            if (maxAge <= 0)
            {
                float speedAbs = Math.abs(speed);
                maxAge = speedAbs > 0.0001F ? Math.max(1, (int) (range / speedAbs)) : 20;
            }

            Vector3f initialRotation = this.getInitialRotation();
            Vector3f rotationSpeed = this.createRotationSpeed(this.random);
            float itemScale = this.createItemScale(this.random);
            int scaleInTime = Math.max(0, this.form.scaleInTime.get());

            SprayedItem item = new SprayedItem(this.owner, stack, pos, velocity, initialRotation, rotationSpeed, maxAge, useGravity, this.form.gravitySpeed.get(), useCollision, this.form.billboard.get(), itemScale, scaleInTime, formColor, source);

            ItemSprayPhysics.configureCenterStop(item, centerStopTarget);
            target.add(item);
        }
    }

    /**
     * 单次发射采样结果。
     *
     * offset 是粒子生成点相对发射器原点的局部偏移，direction 是粒子初速度方向。
     * 实时模式和确定性模式都通过该结构生成粒子，避免两套采样逻辑产生不同形态。
     */
    static class EmissionSample
    {
        public final Vector3f offset = new Vector3f();
        public final Vector3f direction = new Vector3f(0F, 0F, 1F);
    }

    EmissionSample createEmissionSample(Random random)
    {
        EmissionSample sample = new EmissionSample();
        int shape = this.getEmissionShape();

        switch (shape)
        {
            case ItemSprayForm.SHAPE_PLANE:
                sample.offset.set(
                    (random.nextFloat() * 2F - 1F) * this.form.spawnWidth.get() * 0.5F,
                    (random.nextFloat() * 2F - 1F) * this.form.spawnHeight.get() * 0.5F,
                    0F
                );
                sample.direction.set(0F, 0F, 1F);
                break;
            case ItemSprayForm.SHAPE_SPHERE_OUT:
                sample.direction.set(this.randomUnitVector(random));
                sample.offset.set(sample.direction).mul(this.form.spawnOffset.get());
                break;
            case ItemSprayForm.SHAPE_SPHERE_IN:
                Vector3f outward = this.randomUnitVector(random);
                float inwardStart = Math.max(this.form.range.get() - this.form.spawnOffset.get(), 0F);

                sample.offset.set(outward).mul(inwardStart);
                sample.direction.set(outward).negate();
                break;
            default:
                sample.direction.set(this.randomConeDirection(random));
                break;
        }

        return sample;
    }

    int getEmissionShape()
    {
        return Math.max(ItemSprayForm.SHAPE_CONE, Math.min(ItemSprayForm.SHAPE_COUNT - 1, this.form.emissionShape.get()));
    }

    boolean shouldStopAtCenter(float currentSpeed)
    {
        return this.form.stopAtCenter.get()
            && this.getEmissionShape() == ItemSprayForm.SHAPE_SPHERE_IN
            && currentSpeed > 0.0001F;
    }

    private Vector3f randomConeDirection(Random random)
    {
        float radius = this.form.radius.get();
        float halfAngle = Math.min(radius, 180F) * 0.5F;
        float phi = (float) Math.acos(1 - random.nextFloat() * (1 - Math.cos(Math.toRadians(halfAngle))));
        float theta = random.nextFloat() * (float) Math.PI * 2F;

        /* BBS 表单和原版粒子形态都把局部 +Z 当作正向，因此正速度应当朝 +Z 发射。
         * 这里保留旧版随机数顺序，确保默认锥形模式的确定性结果不乱跳。 */
        return new Vector3f(
            MathHelper.sin(theta) * MathHelper.sin(phi),
            MathHelper.cos(theta) * MathHelper.sin(phi),
            MathHelper.cos(phi)
        );
    }

    private Vector3f randomUnitVector(Random random)
    {
        float z = random.nextFloat() * 2F - 1F;
        float theta = random.nextFloat() * (float) Math.PI * 2F;
        float radius = (float) Math.sqrt(Math.max(0F, 1F - z * z));

        return new Vector3f(
            MathHelper.cos(theta) * radius,
            MathHelper.sin(theta) * radius,
            z
        );
    }

    /**
     * 用表单种子、发射刻和序号混合出确定性随机种子，保证同一时间与种子得到完全一致的喷射结果。
     */
    long mixSeed(int spawnTick, int index)
    {
        long value = this.form.seed.get();

        value = value * 31L + spawnTick;
        value = value * 31L + index;
        value ^= (value >>> 33);
        value *= 0xff51afd7ed558ccdL;
        value ^= (value >>> 33);
        value *= 0xc4ceb9fe1a85ec53L;
        value ^= (value >>> 33);

        return value;
    }

    int randomIndex(int spawnTick, int index, int salt, int bound)
    {
        if (bound <= 1)
        {
            return 0;
        }

        return new Random(this.mixSeed(spawnTick, index + salt * 100000)).nextInt(bound);
    }

    private double randomSigned(Random random)
    {
        return random.nextDouble() * 2D - 1D;
    }

    Vector3f getInitialRotation()
    {
        return new Vector3f(
            this.form.itemPitch.get(),
            this.form.itemYaw.get(),
            this.form.itemRoll.get()
        );
    }

    Vector3f createRotationSpeed(Random random)
    {
        float randomSpeed = Math.max(0F, this.form.rotationRandomSpeed.get());

        return new Vector3f(
            this.form.rotationSpeedX.get() + (float) (this.randomSigned(random) * randomSpeed),
            this.form.rotationSpeedY.get() + (float) (this.randomSigned(random) * randomSpeed),
            this.form.rotationSpeedZ.get() + (float) (this.randomSigned(random) * randomSpeed)
        );
    }

    float createItemScale(Random random)
    {
        float scale = Math.max(0F, this.form.itemScale.get());
        float scatter = Math.max(0F, this.form.scaleScatter.get());

        if (scatter > 0F)
        {
            scale += (float) (this.randomSigned(random) * scatter);
        }

        return Math.max(0F, scale);
    }
}
