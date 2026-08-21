package gbeic.bbsplusplus.client.renderer;

import mchorse.bbs_mod.utils.colors.Color;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.MathHelper;
import org.joml.Matrix3f;
import org.joml.Vector3d;
import org.joml.Vector3f;

/**
 * 喷射物品粒子数据结构。
 *
 * 存放单个已经发射出来的物品粒子的位置、速度、旋转、物理与渲染参数。
 * 粒子脱离发射器后依然能独立结算物理与渲染，因此所有数据都保存在自身而不是渲染器上。
 */
public class SprayedItem
{
    public ItemStack stack;
    /** 当前世界坐标位置 */
    public Vector3d pos = new Vector3d();
    /** 上一刻世界坐标位置 */
    public Vector3d prevPos = new Vector3d();
    /** 世界空间速度向量 */
    public Vector3d velocity = new Vector3d();
    public Vector3f rotation = new Vector3f();
    public Vector3f prevRotation = new Vector3f();
    public Vector3f initialRotation = new Vector3f();
    public Vector3f rotationSpeed = new Vector3f();
    public int age;
    public int maxAge;
    public boolean stopped;
    public boolean stopAtCenter;
    public Vector3d centerStopTarget;
    public Vector3d centerStopDirection;

    // 绑定属于它自己的物理与渲染参数，脱离发射器后依然能独立结算
    public boolean useGravity;
    public float gravitySpeed;
    public boolean useCollision;
    public boolean billboard;
    public float scale;
    public int scaleInTime;
    public float renderAge = -1F;
    public Matrix3f renderRotation;
    public Color color;
    /** 生成该世界粒子的渲染器，用于切换到确定性采样时清理旧的实时粒子 */
    public final ItemSprayFormRenderer owner;
    /** 如果来自模型方块，则记录源方块；源方块被破坏时粒子会在下次全局更新中移除 */
    public final ItemSpraySource source;

    public SprayedItem(ItemSprayFormRenderer owner, ItemStack stack, Vector3d pos, Vector3d velocity, Vector3f initialRotation, Vector3f rotationSpeed, int maxAge, boolean useGravity, float gravitySpeed, boolean useCollision, boolean billboard, float scale, int scaleInTime, Color color, ItemSpraySource source)
    {
        this.stack = stack;
        this.pos.set(pos);
        this.prevPos.set(pos);
        this.velocity.set(velocity);
        this.rotation.set(initialRotation);
        this.prevRotation.set(initialRotation);
        this.initialRotation.set(initialRotation);
        this.maxAge = maxAge;
        this.useGravity = useGravity;
        this.gravitySpeed = gravitySpeed;
        this.useCollision = useCollision;
        this.billboard = billboard;
        this.scale = scale;
        this.scaleInTime = scaleInTime;
        this.color = new Color().copy(color);
        this.owner = owner;
        this.source = source;
        this.rotationSpeed.set(rotationSpeed);
    }

    public float getRenderScale(float tickDelta)
    {
        float target = Math.max(0F, this.scale);

        if (target <= 0F || this.scaleInTime <= 0)
        {
            return target;
        }

        float age = this.renderAge >= 0F ? this.renderAge : this.age + Math.max(0F, tickDelta);
        float progress = MathHelper.clamp(age / this.scaleInTime, 0F, 1F);
        float smooth = progress * progress * (3F - 2F * progress);

        return target * smooth;
    }
}
