package gbeic.bbsplusplus.client.renderer;

import gbeic.bbsplusplus.BBSAddonsSettings;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3d;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 物品喷射和 IR Lights 的兼容桥接层。
 *
 * 这个类只负责 IRL 阴影烘焙相关的可选插件逻辑：通过反射把物品喷射粒子作为投影物交给 IRL，
 * 并在 IRL 的阴影批次中重绘这些物品。拆出来后，主渲染器可以专注在发射、运动和普通世界渲染上。
 */
final class ItemSprayIRLiteBridge
{
    private static final int IRLITE_ITEM_SHADOW_CASTER_TYPE = 11047;
    private static final int IRLITE_MAX_ITEM_SHADOW_CASTERS = 4;
    private static final int DEFAULT_IRLITE_MAX_ITEM_SHADOW_ITEMS = 1024;
    private static final int IRLITE_ITEMS_PER_SHADOW_CASTER = 256;
    private static final double IRLITE_SINGLE_CASTER_MAX_RADIUS = 18D;
    private static final double IRLITE_SHADOW_BUCKET_SIZE = 6D;
    private static final double IRLITE_COLLECT_DIST = 72D;
    private static final int IRLITE_SHADOW_LIGHT = LightmapTextureManager.pack(15, 15);

    private static Class<?> irliteSinkClass;
    private static Method irliteSinkEmit;
    private static Class<?> irliteBatchClass;
    private static Method irliteBatchImmediate;
    private static Method irliteBatchMatrices;
    private static boolean irliteShadowOriginResolved;
    private static Method irliteShadowOriginX;
    private static Method irliteShadowOriginY;
    private static Method irliteShadowOriginZ;

    private ItemSprayIRLiteBridge()
    {}

    public static void collectShadowCasters(World world, Vec3d cameraPos, float tickDelta, Object sink, List<ItemSprayFormRenderer.SprayedItem> globalItems, List<ItemSprayFormRenderer.SprayedItem> deterministicWorldItems)
    {
        if (world == null || cameraPos == null || sink == null)
        {
            return;
        }

        int total = globalItems.size() + deterministicWorldItems.size();

        if (total <= 0)
        {
            return;
        }

        double maxDistanceSq = IRLITE_COLLECT_DIST * IRLITE_COLLECT_DIST;
        double itemRenderDistanceSq = ItemSprayFormRenderer.getMaxRenderDistanceSquared();

        if (itemRenderDistanceSq > 0D)
        {
            maxDistanceSq = Math.min(maxDistanceSq, itemRenderDistanceSq);
        }

        int maxShadowItems = getMaxItemShadowItems();

        if (maxShadowItems <= 0)
        {
            return;
        }

        List<IRLiteShadowItem> shadowItems = new ArrayList<>(Math.min(total, maxShadowItems));

        collectShadowItems(shadowItems, globalItems, world, cameraPos, tickDelta, maxDistanceSq);
        collectShadowItems(shadowItems, deterministicWorldItems, world, cameraPos, tickDelta, maxDistanceSq);

        if (shadowItems.isEmpty())
        {
            return;
        }

        shadowItems = limitShadowItems(shadowItems);
        List<IRLiteShadowCaster> casters = createShadowCasterBatches(shadowItems);

        for (IRLiteShadowCaster caster : casters)
        {
            emitShadowCaster(sink, caster);
        }
    }

    public static boolean renderShadowCaster(Object caster, float tickDelta, Object batch)
    {
        if (!(caster instanceof IRLiteShadowCaster shadowCaster))
        {
            return false;
        }

        VertexConsumerProvider.Immediate immediate = getBatchImmediate(batch);
        net.minecraft.client.util.math.MatrixStack matrices = getBatchMatrices(batch);

        if (immediate == null || matrices == null)
        {
            return true;
        }

        try
        {
            renderShadowCaster(shadowCaster, matrices, immediate);
        }
        catch (Throwable throwable)
        {
            throwable.printStackTrace();
        }

        return true;
    }

    private static void collectShadowItems(List<IRLiteShadowItem> shadowItems, List<ItemSprayFormRenderer.SprayedItem> items, World world, Vec3d cameraPos, float tickDelta, double maxDistanceSq)
    {
        for (ItemSprayFormRenderer.SprayedItem item : items)
        {
            if (!canCastShadow(item, world))
            {
                continue;
            }

            double x = MathHelper.lerp(tickDelta, (float) item.prevPos.x, (float) item.pos.x);
            double y = MathHelper.lerp(tickDelta, (float) item.prevPos.y, (float) item.pos.y);
            double z = MathHelper.lerp(tickDelta, (float) item.prevPos.z, (float) item.pos.z);
            double distanceSq = getDistanceSquared(x, y, z, cameraPos);

            if (!isWithinRenderDistance(distanceSq, maxDistanceSq))
            {
                continue;
            }

            float rx = MathHelper.lerp(tickDelta, item.prevRotation.x, item.rotation.x);
            float ry = MathHelper.lerp(tickDelta, item.prevRotation.y, item.rotation.y);
            float rz = MathHelper.lerp(tickDelta, item.prevRotation.z, item.rotation.z);
            float scale = item.getRenderScale(tickDelta);

            if (scale <= 0F)
            {
                continue;
            }

            shadowItems.add(new IRLiteShadowItem(item, x, y, z, rx, ry, rz, scale));
        }
    }

    private static List<IRLiteShadowItem> limitShadowItems(List<IRLiteShadowItem> shadowItems)
    {
        int hardLimit = getMaxItemShadowItems();
        int renderLimit = ItemSprayFormRenderer.getMaxRenderedItems();

        if (renderLimit > 0)
        {
            hardLimit = Math.min(hardLimit, renderLimit);
        }

        if (shadowItems.size() <= hardLimit)
        {
            return shadowItems;
        }

        List<IRLiteShadowItem> limited = new ArrayList<>(hardLimit);

        /* 不按相机距离重排，避免玩家转动/移动视角时阴影集合频繁换人。
         * 用原列表顺序做均匀抽样，能稳定覆盖整团喷射物品。 */
        for (int i = 0; i < hardLimit; i++)
        {
            int index = (int) ((long) i * shadowItems.size() / hardLimit);

            limited.add(shadowItems.get(index));
        }

        return limited;
    }

    private static List<IRLiteShadowCaster> createShadowCasterBatches(List<IRLiteShadowItem> shadowItems)
    {
        IRLiteShadowCaster merged = createShadowCaster(new ArrayList<>(shadowItems));

        // 紧凑喷射团用一个大包围球即可，实际阴影几何仍然逐个物品绘制，不会损失细节。
        if (shadowItems.size() <= IRLITE_ITEMS_PER_SHADOW_CASTER || merged.radius <= IRLITE_SINGLE_CASTER_MAX_RADIUS)
        {
            List<IRLiteShadowCaster> casters = new ArrayList<>(1);

            casters.add(merged);

            return casters;
        }

        shadowItems.sort(Comparator
            .comparingLong((IRLiteShadowItem item) -> item.bucketX)
            .thenComparingLong((item) -> item.bucketY)
            .thenComparingLong((item) -> item.bucketZ));

        int itemsPerCaster = Math.max(
            IRLITE_ITEMS_PER_SHADOW_CASTER,
            (shadowItems.size() + IRLITE_MAX_ITEM_SHADOW_CASTERS - 1) / IRLITE_MAX_ITEM_SHADOW_CASTERS
        );
        List<IRLiteShadowCaster> casters = new ArrayList<>(IRLITE_MAX_ITEM_SHADOW_CASTERS);

        for (int start = 0; start < shadowItems.size() && casters.size() < IRLITE_MAX_ITEM_SHADOW_CASTERS; start += itemsPerCaster)
        {
            int end = Math.min(shadowItems.size(), start + itemsPerCaster);
            List<IRLiteShadowItem> batch = new ArrayList<>(shadowItems.subList(start, end));

            casters.add(createShadowCaster(batch));
        }

        return casters;
    }

    private static IRLiteShadowCaster createShadowCaster(List<IRLiteShadowItem> items)
    {
        double cx = 0D;
        double cy = 0D;
        double cz = 0D;

        for (IRLiteShadowItem item : items)
        {
            cx += item.x;
            cy += item.y;
            cz += item.z;
        }

        double inv = 1D / items.size();
        cx *= inv;
        cy *= inv;
        cz *= inv;

        double radiusSq = 0D;

        for (IRLiteShadowItem item : items)
        {
            double dx = item.x - cx;
            double dy = item.y - cy;
            double dz = item.z - cz;
            double radius = Math.sqrt(dx * dx + dy * dy + dz * dz) + item.radius;

            radiusSq = Math.max(radiusSq, radius * radius);
        }

        return new IRLiteShadowCaster(items, cx, cy, cz, (float) Math.sqrt(radiusSq));
    }

    private static boolean canCastShadow(ItemSprayFormRenderer.SprayedItem item, World world)
    {
        return item != null
            && item.scale > 0F
            && item.stack != null
            && !item.stack.isEmpty()
            && isFinite(item.pos)
            && isFinite(item.prevPos)
            && (item.color == null || item.color.a > 0.01F)
            && (item.source == null || item.source.isAlive(world));
    }

    private static void emitShadowCaster(Object sink, IRLiteShadowCaster caster)
    {
        Method method = getSinkEmit(sink);

        if (method == null)
        {
            return;
        }

        try
        {
            method.invoke(
                sink,
                caster,
                IRLITE_ITEM_SHADOW_CASTER_TYPE,
                false,
                (float) caster.x,
                (float) caster.y,
                (float) caster.z,
                caster.radius,
                0L
            );
        }
        catch (Throwable ignored)
        {
            // IRL 版本变动或反射失败时只丢失物品喷射阴影，不能影响正常渲染。
        }
    }

    private static Method getSinkEmit(Object sink)
    {
        Class<?> sinkClass = sink.getClass();

        if (irliteSinkEmit == null || irliteSinkClass != sinkClass)
        {
            irliteSinkClass = sinkClass;
            irliteSinkEmit = null;

            try
            {
                irliteSinkEmit = sinkClass.getDeclaredMethod(
                    "emit",
                    Object.class,
                    int.class,
                    boolean.class,
                    float.class,
                    float.class,
                    float.class,
                    float.class,
                    long.class
                );
                irliteSinkEmit.setAccessible(true);
            }
            catch (Throwable ignored)
            {
                // 没有匹配的 IRL OccluderSink 时保持空方法。
            }
        }

        return irliteSinkEmit;
    }

    private static VertexConsumerProvider.Immediate getBatchImmediate(Object batch)
    {
        Method method = getBatchMethod(batch, true);

        if (method == null)
        {
            return null;
        }

        try
        {
            Object value = method.invoke(batch);

            return value instanceof VertexConsumerProvider.Immediate immediate ? immediate : null;
        }
        catch (Throwable ignored)
        {
            return null;
        }
    }

    private static net.minecraft.client.util.math.MatrixStack getBatchMatrices(Object batch)
    {
        Method method = getBatchMethod(batch, false);

        if (method == null)
        {
            return null;
        }

        try
        {
            Object value = method.invoke(batch);

            return value instanceof net.minecraft.client.util.math.MatrixStack matrices ? matrices : null;
        }
        catch (Throwable ignored)
        {
            return null;
        }
    }

    private static Method getBatchMethod(Object batch, boolean immediate)
    {
        if (batch == null)
        {
            return null;
        }

        Class<?> batchClass = batch.getClass();

        if (irliteBatchClass != batchClass)
        {
            irliteBatchClass = batchClass;
            irliteBatchImmediate = null;
            irliteBatchMatrices = null;

            try
            {
                irliteBatchImmediate = batchClass.getDeclaredMethod("immediate");
                irliteBatchImmediate.setAccessible(true);
            }
            catch (Throwable ignored)
            {
                // 兼容层会在调用处降级为空。
            }

            try
            {
                irliteBatchMatrices = batchClass.getDeclaredMethod("matrices");
                irliteBatchMatrices.setAccessible(true);
            }
            catch (Throwable ignored)
            {
                // 兼容层会在调用处降级为空。
            }
        }

        return immediate ? irliteBatchImmediate : irliteBatchMatrices;
    }

    private static void renderShadowCaster(IRLiteShadowCaster caster, net.minecraft.client.util.math.MatrixStack matrices, VertexConsumerProvider.Immediate immediate)
    {
        for (IRLiteShadowItem item : caster.items)
        {
            renderShadowItem(item, matrices, immediate);
        }
    }

    private static void renderShadowItem(IRLiteShadowItem item, net.minecraft.client.util.math.MatrixStack matrices, VertexConsumerProvider.Immediate immediate)
    {
        matrices.push();
        try
        {
            matrices.translate(
                item.x - getShadowOriginX(),
                item.y - getShadowOriginY(),
                item.z - getShadowOriginZ()
            );

            if (item.renderRotation != null)
            {
                matrices.peek().getPositionMatrix().mul(new Matrix4f().set(item.renderRotation));
                matrices.peek().getNormalMatrix().mul(item.renderRotation);
            }

            if (item.billboard)
            {
                applyShadowBillboard(matrices);
                matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(item.rz));
            }
            else
            {
                matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(item.rx));
                matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(item.ry));
                matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(item.rz));
            }

            float scale = 0.5F * item.scale;
            matrices.scale(scale, scale, scale);

            MinecraftClient client = MinecraftClient.getInstance();

            client.getItemRenderer().renderItem(
                item.stack,
                ModelTransformationMode.GROUND,
                IRLITE_SHADOW_LIGHT,
                OverlayTexture.DEFAULT_UV,
                matrices,
                immediate,
                client.world,
                0
            );
        }
        finally
        {
            matrices.pop();
        }
    }

    private static double getShadowOriginX()
    {
        return invokeShadowOrigin(0);
    }

    private static double getShadowOriginY()
    {
        return invokeShadowOrigin(1);
    }

    private static double getShadowOriginZ()
    {
        return invokeShadowOrigin(2);
    }

    private static double invokeShadowOrigin(int axis)
    {
        resolveShadowOriginMethods();

        Method method = axis == 0 ? irliteShadowOriginX : (axis == 1 ? irliteShadowOriginY : irliteShadowOriginZ);

        if (method == null)
        {
            return 0D;
        }

        try
        {
            Object value = method.invoke(null);

            return value instanceof Number number ? number.doubleValue() : 0D;
        }
        catch (Throwable ignored)
        {
            return 0D;
        }
    }

    private static void resolveShadowOriginMethods()
    {
        if (irliteShadowOriginResolved)
        {
            return;
        }

        irliteShadowOriginResolved = true;

        Class<?> renderer = findIRLiteShadowRenderer();

        if (renderer == null)
        {
            return;
        }

        try
        {
            irliteShadowOriginX = renderer.getDeclaredMethod("currentOriginX");
            irliteShadowOriginY = renderer.getDeclaredMethod("currentOriginY");
            irliteShadowOriginZ = renderer.getDeclaredMethod("currentOriginZ");
            irliteShadowOriginX.setAccessible(true);
            irliteShadowOriginY.setAccessible(true);
            irliteShadowOriginZ.setAccessible(true);
        }
        catch (Throwable ignored)
        {
            irliteShadowOriginX = null;
            irliteShadowOriginY = null;
            irliteShadowOriginZ = null;
        }
    }

    private static Class<?> findIRLiteShadowRenderer()
    {
        try
        {
            return Class.forName("org.qualet.irl.light.shadow.ShadowRenderer");
        }
        catch (Throwable ignored)
        {}

        try
        {
            return Class.forName("qualet.irlite.client.light.shadow.ShadowRenderer");
        }
        catch (Throwable ignored)
        {
            return null;
        }
    }

    private static void applyShadowBillboard(net.minecraft.client.util.math.MatrixStack matrices)
    {
        net.minecraft.client.render.Camera camera = MinecraftClient.getInstance().gameRenderer.getCamera();

        if (camera != null)
        {
            matrices.multiply(camera.getRotation());
        }
    }

    private static int getMaxItemShadowItems()
    {
        if (BBSAddonsSettings.itemSprayIRLiteShadowMaxItems == null)
        {
            return DEFAULT_IRLITE_MAX_ITEM_SHADOW_ITEMS;
        }

        return Math.max(0, BBSAddonsSettings.itemSprayIRLiteShadowMaxItems.get());
    }

    private static double getDistanceSquared(double x, double y, double z, Vec3d cameraPos)
    {
        double dx = x - cameraPos.x;
        double dy = y - cameraPos.y;
        double dz = z - cameraPos.z;

        return dx * dx + dy * dy + dz * dz;
    }

    private static boolean isWithinRenderDistance(double distanceSq, double maxDistanceSq)
    {
        if (maxDistanceSq <= 0D)
        {
            return true;
        }

        return distanceSq <= maxDistanceSq;
    }

    private static boolean isFinite(Vector3d vector)
    {
        return Double.isFinite(vector.x) && Double.isFinite(vector.y) && Double.isFinite(vector.z);
    }

    /**
     * IR Lights 阴影烘焙用的物品喷射批次。
     * <p>
     * IRL 的投影物槽位很少，不能让每个喷射物品都单独占一个槽。这里把同一批空间上相近的物品塞进一个
     * caster，让 IRL 用一个包围球做灯光裁剪，真正绘制时再一次性画出批次里的所有物品。
     */
    private record IRLiteShadowCaster(List<IRLiteShadowItem> items, double x, double y, double z, float radius)
    {
    }

    /**
     * IR Lights 阴影批次中的单个物品快照。
     *
     * 快照只保存绘制阴影需要的数据，避免阴影烘焙期间读取还在变化的物理粒子对象。
     */
    private static final class IRLiteShadowItem
    {
        public final ItemStack stack;
        public final double x;
        public final double y;
        public final double z;
        public final long bucketX;
        public final long bucketY;
        public final long bucketZ;
        public final float rx;
        public final float ry;
        public final float rz;
        public final boolean billboard;
        public final float scale;
        public final float radius;
        public final Matrix3f renderRotation;

        private IRLiteShadowItem(ItemSprayFormRenderer.SprayedItem item, double x, double y, double z, float rx, float ry, float rz, float scale)
        {
            this.stack = item.stack.copy();
            this.x = x;
            this.y = y;
            this.z = z;
            this.bucketX = (long) Math.floor(x / IRLITE_SHADOW_BUCKET_SIZE);
            this.bucketY = (long) Math.floor(y / IRLITE_SHADOW_BUCKET_SIZE);
            this.bucketZ = (long) Math.floor(z / IRLITE_SHADOW_BUCKET_SIZE);
            this.rx = rx;
            this.ry = ry;
            this.rz = rz;
            this.billboard = item.billboard;
            this.scale = scale;
            this.radius = Math.max(0.1F, scale) * 0.75F + 0.5F;
            this.renderRotation = item.renderRotation == null ? null : new Matrix3f(item.renderRotation);
        }
    }
}
