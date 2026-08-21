package gbeic.bbsplusplus.client.renderer;

import net.minecraft.client.MinecraftClient;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;
import org.joml.Matrix4f;
import org.joml.Vector3d;
import org.joml.Vector3f;

import java.util.List;

/**
 * 物品喷射的物理与碰撞结算工具。
 *
 * 纯静态工具类，负责粒子的重力、速度步进、中心停止、世界碰撞追踪与二分安全点查找，
 * 以及全局粒子列表的生命周期更新。不持有任何实例状态，渲染器与模拟器直接调用。
 */
final class ItemSprayPhysics
{
    private static final double CENTER_STOP_EPSILON = 0.000001D;
    private static final Matrix4f IDENTITY_TRANSFORM = new Matrix4f();

    private ItemSprayPhysics()
    {}

    static void configureCenterStop(SprayedItem item, Vector3d target)
    {
        if (target == null || item.velocity.lengthSquared() <= CENTER_STOP_EPSILON)
        {
            return;
        }

        item.stopAtCenter = true;
        item.centerStopTarget = new Vector3d(target);
        item.centerStopDirection = new Vector3d(item.velocity).normalize();

        if (getCenterRemainingDistance(item, item.pos) <= CENTER_STOP_EPSILON)
        {
            stopItemAt(item, item.centerStopTarget);
        }
    }

    static void stopItemAt(SprayedItem item, Vector3d position)
    {
        item.stopped = true;
        item.pos.set(position);
        item.prevPos.set(position);
        item.velocity.set(0D, 0D, 0D);
        item.rotationSpeed.set(0F, 0F, 0F);
    }

    /**
     * 按指定年龄重放确定性粒子的整段运动历史。
     * 整段按整刻步进，尾部用余数补齐，保证与实时模式的逐刻结算结果一致。
     */
    static void replayDeterministicMotion(SprayedItem item, World world, Matrix4f previewTransform, Vector3d motionWorldOffset, Vector3f gravityLocal, float age)
    {
        int wholeTicks = Math.max(0, (int) Math.floor(age));
        float remainder = age - wholeTicks;

        for (int i = 0; i < wholeTicks; i++)
        {
            applyMotionStep(item, world, previewTransform, motionWorldOffset, gravityLocal, 1F);

            if (item.stopped)
            {
                return;
            }
        }

        if (remainder > 0F)
        {
            applyMotionStep(item, world, previewTransform, motionWorldOffset, gravityLocal, remainder);
        }
    }

    /**
     * 无碰撞时的确定性运动闭式解：位置和旋转直接用初速度与旋转速度乘以年龄推算。
     */
    static void applyDeterministicMotion(SprayedItem item, Vector3f gravityLocal, float age)
    {
        double gravityFactor = 0.5D * age * (age + 1D);

        item.pos.x += item.velocity.x * age + gravityLocal.x * gravityFactor;
        item.pos.y += item.velocity.y * age + gravityLocal.y * gravityFactor;
        item.pos.z += item.velocity.z * age + gravityLocal.z * gravityFactor;
        item.rotation.set(item.initialRotation).add(
            item.rotationSpeed.x * age,
            item.rotationSpeed.y * age,
            item.rotationSpeed.z * age
        );
    }

    static void applyMotionStep(SprayedItem item, World world, Matrix4f previewTransform, Vector3f gravityLocal, float dt)
    {
        applyMotionStep(item, world, previewTransform, null, gravityLocal, dt);
    }

    /**
     * 对单个粒子推进一个时间步：先加重力，再位移，再依次判断中心停止与方块碰撞。
     * 任一停止条件命中时立即停住并清空速度与旋转速度。
     */
    static void applyMotionStep(SprayedItem item, World world, Matrix4f previewTransform, Vector3d motionWorldOffset, Vector3f gravityLocal, float dt)
    {
        if (dt <= 0F)
        {
            return;
        }

        item.prevPos.set(item.pos);
        item.prevRotation.set(item.rotation);

        if (!item.stopped)
        {
            Vector3d currentPos = new Vector3d(item.pos);
            Vector3d nextPos = new Vector3d(item.pos);
            Vector3d nextVelocity = new Vector3d(item.velocity);
            Vector3f nextRotationSpeed = new Vector3f(item.rotationSpeed);

            if (item.useGravity)
            {
                nextVelocity.x += gravityLocal.x * dt;
                nextVelocity.y += gravityLocal.y * dt;
                nextVelocity.z += gravityLocal.z * dt;
            }

            nextPos.x += nextVelocity.x * dt;
            nextPos.y += nextVelocity.y * dt;
            nextPos.z += nextVelocity.z * dt;

            Vector3d centerStopPos = traceCenterStop(item, currentPos, nextPos);
            Vector3d collisionPos = null;

            if (item.useCollision && world != null)
            {
                collisionPos = traceCollision(world, previewTransform, motionWorldOffset, currentPos, nextPos);
            }

            Vector3d stopPos = pickNearestStop(currentPos, centerStopPos, collisionPos);

            if (stopPos != null)
            {
                item.stopped = true;
                nextVelocity.set(0, 0, 0);
                nextRotationSpeed.set(0, 0, 0);
                nextPos.set(stopPos);
            }

            item.velocity.set(nextVelocity);
            item.rotationSpeed.set(nextRotationSpeed);
            item.pos.set(nextPos);
            item.rotation.add(
                item.rotationSpeed.x * dt,
                item.rotationSpeed.y * dt,
                item.rotationSpeed.z * dt
            );
        }
    }

    private static Vector3d traceCenterStop(SprayedItem item, Vector3d start, Vector3d end)
    {
        if (!item.stopAtCenter || item.centerStopTarget == null || item.centerStopDirection == null)
        {
            return null;
        }

        double startDistance = getCenterRemainingDistance(item, start);
        double endDistance = getCenterRemainingDistance(item, end);

        if (startDistance <= CENTER_STOP_EPSILON || (startDistance > CENTER_STOP_EPSILON && endDistance <= CENTER_STOP_EPSILON))
        {
            return new Vector3d(item.centerStopTarget);
        }

        return null;
    }

    private static double getCenterRemainingDistance(SprayedItem item, Vector3d position)
    {
        return (item.centerStopTarget.x - position.x) * item.centerStopDirection.x
            + (item.centerStopTarget.y - position.y) * item.centerStopDirection.y
            + (item.centerStopTarget.z - position.z) * item.centerStopDirection.z;
    }

    private static Vector3d pickNearestStop(Vector3d start, Vector3d a, Vector3d b)
    {
        if (a == null)
        {
            return b;
        }

        if (b == null)
        {
            return a;
        }

        return distanceSquared(start, a) <= distanceSquared(start, b) ? a : b;
    }

    private static double distanceSquared(Vector3d a, Vector3d b)
    {
        double dx = a.x - b.x;
        double dy = a.y - b.y;
        double dz = a.z - b.z;

        return dx * dx + dy * dy + dz * dz;
    }

    /**
     * 追踪粒子本时间步在世界空间中的碰撞。
     * 先做世界空间光线检测，命中则把命中点反算回局部坐标作为停止位置；
     * 终点本身卡进方块时再退化为二分查找最后安全点。
     */
    private static Vector3d traceCollision(World world, Matrix4f localToWorld, Vector3d worldOffset, Vector3d startLocal, Vector3d endLocal)
    {
        Vector3d startWorld = transformPosition(localToWorld, worldOffset, startLocal);
        Vector3d endWorld = transformPosition(localToWorld, worldOffset, endLocal);

        if (!isFinite(startWorld) || !isFinite(endWorld))
        {
            return null;
        }

        net.minecraft.entity.Entity cameraEntity = MinecraftClient.getInstance().cameraEntity;

        if (cameraEntity == null)
        {
            return hasCollisionAt(world, endWorld) ? findLastSafeCollisionLocal(world, localToWorld, worldOffset, startLocal, endLocal) : null;
        }

        Vec3d start = new Vec3d(startWorld.x, startWorld.y, startWorld.z);
        Vec3d end = new Vec3d(endWorld.x, endWorld.y, endWorld.z);
        BlockHitResult hit = world.raycast(new RaycastContext(
            start,
            end,
            RaycastContext.ShapeType.COLLIDER,
            RaycastContext.FluidHandling.NONE,
            cameraEntity
        ));

        if (hit.getType() != HitResult.Type.MISS)
        {
            Vector3d hitLocal = inverseTransformPosition(localToWorld, worldOffset, hit.getPos());
            Vector3d motion = new Vector3d(endLocal).sub(startLocal);

            if (motion.lengthSquared() > 0.000001D)
            {
                hitLocal.sub(motion.normalize().mul(0.001D));
            }

            return hitLocal;
        }

        if (hasCollisionAt(world, endWorld))
        {
            return findLastSafeCollisionLocal(world, localToWorld, worldOffset, startLocal, endLocal);
        }

        return null;
    }

    /**
     * 二分查找从起点到终点之间最后一个不卡进方块的局部坐标点，防止粒子嵌入墙体。
     */
    private static Vector3d findLastSafeCollisionLocal(World world, Matrix4f localToWorld, Vector3d worldOffset, Vector3d startLocal, Vector3d endLocal)
    {
        Vector3d startWorld = transformPosition(localToWorld, worldOffset, startLocal);

        if (hasCollisionAt(world, startWorld))
        {
            return new Vector3d(startLocal);
        }

        Vector3d safe = new Vector3d(startLocal);
        Vector3d blocked = new Vector3d(endLocal);

        for (int i = 0; i < 12; i++)
        {
            Vector3d mid = new Vector3d(
                (safe.x + blocked.x) * 0.5D,
                (safe.y + blocked.y) * 0.5D,
                (safe.z + blocked.z) * 0.5D
            );
            Vector3d midWorld = transformPosition(localToWorld, worldOffset, mid);

            if (hasCollisionAt(world, midWorld))
            {
                blocked.set(mid);
            }
            else
            {
                safe.set(mid);
            }
        }

        return safe;
    }

    private static boolean isFinite(Vector3d vector)
    {
        return Double.isFinite(vector.x) && Double.isFinite(vector.y) && Double.isFinite(vector.z);
    }

    /**
     * 把局部坐标通过矩阵变换到世界坐标，可选叠加双精度世界偏移。
     */
    static Vector3d transformPosition(Matrix4f matrix, Vector3d worldOffset, Vector3d position)
    {
        Vector3f transformed = new Vector3f((float) position.x, (float) position.y, (float) position.z);

        matrix.transformPosition(transformed);

        if (worldOffset == null)
        {
            return new Vector3d(transformed.x(), transformed.y(), transformed.z());
        }

        return new Vector3d(transformed.x() + worldOffset.x, transformed.y() + worldOffset.y, transformed.z() + worldOffset.z);
    }

    /**
     * 把世界坐标反算回局部坐标（transformPosition 的逆运算）。
     */
    static Vector3d inverseTransformPosition(Matrix4f matrix, Vector3d worldOffset, Vec3d position)
    {
        Matrix4f inverse = new Matrix4f(matrix).invert();
        double x = position.x;
        double y = position.y;
        double z = position.z;

        if (worldOffset != null)
        {
            x -= worldOffset.x;
            y -= worldOffset.y;
            z -= worldOffset.z;
        }

        Vector3f transformed = new Vector3f((float) x, (float) y, (float) z);

        inverse.transformPosition(transformed);

        return new Vector3d(transformed.x(), transformed.y(), transformed.z());
    }

    private static boolean hasCollisionAt(World world, Vector3d position)
    {
        BlockPos blockPos = new BlockPos(
            (int) Math.floor(position.x),
            (int) Math.floor(position.y),
            (int) Math.floor(position.z)
        );
        net.minecraft.block.BlockState state = world.getBlockState(blockPos);
        VoxelShape shape = state == null ? null : state.getCollisionShape(world, blockPos);

        if (state == null || state.isAir() || shape == null || shape.isEmpty())
        {
            return false;
        }

        double x = position.x - blockPos.getX();
        double y = position.y - blockPos.getY();
        double z = position.z - blockPos.getZ();
        double epsilon = 0.000001D;

        for (Box box : shape.getBoundingBoxes())
        {
            if (x >= box.minX - epsilon && x <= box.maxX + epsilon
                && y >= box.minY - epsilon && y <= box.maxY + epsilon
                && z >= box.minZ - epsilon && z <= box.maxZ + epsilon)
            {
                return true;
            }
        }

        return false;
    }

    /**
     * 推进一整组粒子的一个 tick：按生命周期移除、记录上一刻状态并执行一步物理。
     * 只有来自模型方块的世界粒子才跟随源方块生命周期清理；其他来源仍按自身 lifetime 结束。
     */
    static void updateItems(List<SprayedItem> items, World world)
    {
        for (int i = items.size() - 1; i >= 0; i--)
        {
            SprayedItem item = items.get(i);

            if (world != null && item.source != null && !item.source.isAlive(world))
            {
                items.remove(i);
                continue;
            }

            item.age++;
            if (item.age >= item.maxAge)
            {
                items.remove(i);
                continue;
            }

            item.prevPos.set(item.pos);
            item.prevRotation.set(item.rotation);

            applyMotionStep(item, world, IDENTITY_TRANSFORM, new Vector3f(0F, -item.gravitySpeed, 0F), 1F);
        }
    }
}
