package gbeic.bbsplusplus.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import gbeic.bbsplusplus.BBSAddonsSettings;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
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
    private static final int IRLITE_SHADOW_LIGHT = LightTexture.pack(15, 15);

    private static Class<?> irliteSinkClass;
    private static Method irliteSinkEmit;
    private static Class<?> irliteBatchClass;
    private static Method irliteBatchImmediate;
    private static Method irliteBatchMatrices;

    private ItemSprayIRLiteBridge()
    {}

    public static void collectShadowCasters(Level world, Vec3 cameraPos, float tickDelta, Object sink, List<ItemSprayFormRenderer.SprayedItem> globalItems, List<ItemSprayFormRenderer.SprayedItem> deterministicWorldItems)
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

        MultiBufferSource.BufferSource immediate = getBatchImmediate(batch);
        PoseStack matrices = getBatchMatrices(batch);

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

    private static void collectShadowItems(List<IRLiteShadowItem> shadowItems, List<ItemSprayFormRenderer.SprayedItem> items, Level world, Vec3 cameraPos, float tickDelta, double maxDistanceSq)
    {
        for (ItemSprayFormRenderer.SprayedItem item : items)
        {
            if (!canCastShadow(item, world))
            {
                continue;
            }

            double x = Mth.lerp(tickDelta, (float) item.prevPos.x, (float) item.pos.x);
            double y = Mth.lerp(tickDelta, (float) item.prevPos.y, (float) item.pos.y);
            double z = Mth.lerp(tickDelta, (float) item.prevPos.z, (float) item.pos.z);
            double distanceSq = getDistanceSquared(x, y, z, cameraPos);

            if (!isWithinRenderDistance(distanceSq, maxDistanceSq))
            {
                continue;
            }

            float rx = Mth.lerp(tickDelta, item.prevRotation.x, item.rotation.x);
            float ry = Mth.lerp(tickDelta, item.prevRotation.y, item.rotation.y);
            float rz = Mth.lerp(tickDelta, item.prevRotation.z, item.rotation.z);
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

    private static boolean canCastShadow(ItemSprayFormRenderer.SprayedItem item, Level world)
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

    private static MultiBufferSource.BufferSource getBatchImmediate(Object batch)
    {
        Method method = getBatchMethod(batch, true);

        if (method == null)
        {
            return null;
        }

        try
        {
            Object value = method.invoke(batch);

            return value instanceof MultiBufferSource.BufferSource immediate ? immediate : null;
        }
        catch (Throwable ignored)
        {
            return null;
        }
    }

    private static PoseStack getBatchMatrices(Object batch)
    {
        Method method = getBatchMethod(batch, false);

        if (method == null)
        {
            return null;
        }

        try
        {
            Object value = method.invoke(batch);

            return value instanceof PoseStack matrices ? matrices : null;
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

    private static void renderShadowCaster(IRLiteShadowCaster caster, PoseStack matrices, MultiBufferSource.BufferSource immediate)
    {
        for (IRLiteShadowItem item : caster.items)
        {
            renderShadowItem(item, matrices, immediate);
        }
    }

    private static void renderShadowItem(IRLiteShadowItem item, PoseStack matrices, MultiBufferSource.BufferSource immediate)
    {
        matrices.pushPose();
        try
        {
            matrices.translate(item.x, item.y, item.z);

            if (item.renderRotation != null)
            {
                matrices.last().pose().mul(new Matrix4f().set(item.renderRotation));
                matrices.last().normal().mul(item.renderRotation);
            }

            if (item.billboard)
            {
                applyShadowBillboard(matrices);
                matrices.mulPose(Axis.ZP.rotationDegrees(item.rz));
            }
            else
            {
                matrices.mulPose(Axis.XP.rotationDegrees(item.rx));
                matrices.mulPose(Axis.YP.rotationDegrees(item.ry));
                matrices.mulPose(Axis.ZP.rotationDegrees(item.rz));
            }

            float scale = 0.5F * item.scale;
            matrices.scale(scale, scale, scale);

            Minecraft client = Minecraft.getInstance();

            // 1.21.1 的物品渲染入口改名为 renderStatic，DEFAULT_UV 改名为 NO_OVERLAY。
            client.getItemRenderer().renderStatic(
                item.stack,
                ItemDisplayContext.GROUND,
                IRLITE_SHADOW_LIGHT,
                OverlayTexture.NO_OVERLAY,
                matrices,
                immediate,
                client.level,
                0
            );
        }
        finally
        {
            matrices.popPose();
        }
    }

    private static void applyShadowBillboard(PoseStack matrices)
    {
        net.minecraft.client.Camera camera = Minecraft.getInstance().gameRenderer.getMainCamera();

        if (camera != null)
        {
            matrices.mulPose(camera.rotation());
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

    private static double getDistanceSquared(double x, double y, double z, Vec3 cameraPos)
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
     *
     * IRL 的投影物槽位很少，不能让每个喷射物品都单独占一个槽。这里把同一批空间上相近的物品塞进一个
     * caster，让 IRL 用一个包围球做灯光裁剪，真正绘制时再一次性画出批次里的所有物品。
     */
    private static final class IRLiteShadowCaster
    {
        public final List<IRLiteShadowItem> items;
        public final double x;
        public final double y;
        public final double z;
        public final float radius;

        private IRLiteShadowCaster(List<IRLiteShadowItem> items, double x, double y, double z, float radius)
        {
            this.items = items;
            this.x = x;
            this.y = y;
            this.z = z;
            this.radius = radius;
        }
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
