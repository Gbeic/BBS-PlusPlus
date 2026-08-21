package gbeic.bbsplusplus.client.renderer;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import mchorse.bbs_mod.client.BBSRendering;
import mchorse.bbs_mod.forms.CustomVertexConsumerProvider;
import mchorse.bbs_mod.forms.FormUtilsClient;
import mchorse.bbs_mod.forms.renderers.utils.FormColorBlend;
import mchorse.bbs_mod.utils.colors.Color;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;

/**
 * 物品喷射粒子的普通世界渲染器。
 *
 * 该类负责把已经生成的喷射物品转换为本帧可渲染候选，应用距离/视锥/数量限制，
 * 然后按原来的物品模型渲染路径绘制。它不参与发射、碰撞或生命周期结算。
 */
final class ItemSprayWorldItemRenderer
{
    private static final double ITEM_RENDER_BOX_SIZE = 1D;

    private ItemSprayWorldItemRenderer()
    {}

    static void renderItems(PoseStack stack, List<ItemSprayFormRenderer.SprayedItem> items, float tickDelta, Level world, int fallbackLight, int overlay, Vec3 cameraPos)
    {
        if (items.isEmpty())
        {
            return;
        }

        List<RenderCandidate> candidates = new ArrayList<>(items.size());

        collectRenderCandidates(candidates, items, tickDelta, cameraPos, null, -1D, 0L);
        renderCollectedItems(stack, candidates, tickDelta, world, fallbackLight, overlay, cameraPos);
    }

    static void renderWorldItems(PoseStack stack, float tickDelta, Level world, int fallbackLight, int overlay, Vec3 cameraPos, Frustum frustum, int maxRenderedItems, double maxDistanceSq, List<ItemSprayFormRenderer.SprayedItem> globalItems, List<ItemSprayFormRenderer.SprayedItem> deterministicWorldItems)
    {
        int total = globalItems.size() + deterministicWorldItems.size();

        if (total <= 0)
        {
            return;
        }

        List<RenderCandidate> candidates = new ArrayList<>(total);
        long order = collectRenderCandidates(candidates, globalItems, tickDelta, cameraPos, frustum, maxDistanceSq, 0L);

        collectRenderCandidates(candidates, deterministicWorldItems, tickDelta, cameraPos, frustum, maxDistanceSq, order);

        if (candidates.isEmpty())
        {
            return;
        }

        if (maxRenderedItems > 0 && candidates.size() > maxRenderedItems)
        {
            // 超出渲染预算时，先保留离镜头最近的粒子，再恢复原始顺序绘制，避免随机闪烁和排序抖动。
            candidates.sort((a, b) -> Double.compare(a.distanceSq, b.distanceSq));
            candidates = new ArrayList<>(candidates.subList(0, maxRenderedItems));
            candidates.sort((a, b) -> Long.compare(a.order, b.order));
        }

        renderCollectedItems(stack, candidates, tickDelta, world, fallbackLight, overlay, cameraPos);
    }

    private static long collectRenderCandidates(List<RenderCandidate> candidates, List<ItemSprayFormRenderer.SprayedItem> items, float tickDelta, Vec3 cameraPos, Frustum frustum, double maxDistanceSq, long startOrder)
    {
        long order = startOrder;

        for (ItemSprayFormRenderer.SprayedItem item : items)
        {
            float scale = item.getRenderScale(tickDelta);

            if (scale <= 0F)
            {
                order++;

                continue;
            }

            // 插值平滑移动
            double x = Mth.lerp(tickDelta, (float) item.prevPos.x, (float) item.pos.x);
            double y = Mth.lerp(tickDelta, (float) item.prevPos.y, (float) item.pos.y);
            double z = Mth.lerp(tickDelta, (float) item.prevPos.z, (float) item.pos.z);
            double distanceSq = getDistanceSquared(x, y, z, cameraPos);

            if (!isWithinRenderDistance(distanceSq, maxDistanceSq) || !isVisibleInFrustum(x, y, z, scale, frustum))
            {
                order++;

                continue;
            }

            candidates.add(new RenderCandidate(item, x, y, z, scale, distanceSq, order));
            order++;
        }

        return order;
    }

    private static void renderCollectedItems(PoseStack stack, List<RenderCandidate> candidates, float tickDelta, Level world, int fallbackLight, int overlay, Vec3 cameraPos)
    {
        if (candidates.isEmpty())
        {
            return;
        }

        CustomVertexConsumerProvider consumers = FormUtilsClient.getProvider();
        CustomVertexConsumerProvider.hijackVertexFormat((l) -> RenderSystem.enableBlend());

        try
        {
            for (RenderCandidate candidate : candidates)
            {
                ItemSprayFormRenderer.SprayedItem item = candidate.item;
                double x = candidate.x;
                double y = candidate.y;
                double z = candidate.z;

                // 因为每个形态颜色可能不同，所以需要在渲染时单独给每个粒子应用它的专属颜色。
                Color rc = new Color(1, 1, 1, 1);
                FormColorBlend.blend(rc, item.color, false);
                consumers.setSubstitute(BBSRendering.getColorConsumer(rc));

                stack.pushPose();
                try
                {
                    if (cameraPos == null)
                    {
                        stack.translate(x, y, z);
                    }
                    else
                    {
                        stack.translate(x - cameraPos.x, y - cameraPos.y, z - cameraPos.z);
                    }

                    if (item.renderRotation != null)
                    {
                        stack.last().pose().mul(new Matrix4f().set(item.renderRotation));
                        stack.last().normal().mul(item.renderRotation);
                    }

                    // 插值旋转
                    float rx = Mth.lerp(tickDelta, item.prevRotation.x, item.rotation.x);
                    float ry = Mth.lerp(tickDelta, item.prevRotation.y, item.rotation.y);
                    float rz = Mth.lerp(tickDelta, item.prevRotation.z, item.rotation.z);

                    if (item.billboard)
                    {
                        ItemSprayFormRenderer.applyBillboard(stack);

                        // 广告牌模式需要稳定面向镜头，因此只保留屏幕平面内的横滚旋转。
                        stack.mulPose(Axis.ZP.rotationDegrees(rz));
                    }
                    else
                    {
                        stack.mulPose(Axis.XP.rotationDegrees(rx));
                        stack.mulPose(Axis.YP.rotationDegrees(ry));
                        stack.mulPose(Axis.ZP.rotationDegrees(rz));
                    }

                    float scale = 0.5F * candidate.scale;
                    stack.scale(scale, scale, scale);

                    int light = fallbackLight;
                    if (world != null && cameraPos != null)
                    {
                        // 根据物品世界坐标采样光照
                        BlockPos bp = new BlockPos((int) Math.floor(x), (int) Math.floor(y), (int) Math.floor(z));
                        light = LevelRenderer.getLightColor(world, bp);
                    }

                    // 1.21.1 的物品渲染入口改名为 renderStatic，参数顺序不变。
                    Minecraft.getInstance().getItemRenderer().renderStatic(
                        item.stack, ItemDisplayContext.GROUND,
                        light, overlay, stack, consumers, world, 0
                    );
                }
                catch (Throwable throwable)
                {
                    throwable.printStackTrace();
                }
                finally
                {
                    stack.popPose();
                }
            }

            consumers.draw();
        }
        finally
        {
            consumers.setSubstitute(null);
            CustomVertexConsumerProvider.clearRunnables();
            RenderSystem.enableDepthTest();
        }
    }

    private static double getDistanceSquared(double x, double y, double z, Vec3 cameraPos)
    {
        if (cameraPos == null)
        {
            return 0D;
        }

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

    private static boolean isVisibleInFrustum(double x, double y, double z, float scale, Frustum frustum)
    {
        if (frustum == null)
        {
            return true;
        }

        double half = ITEM_RENDER_BOX_SIZE * Math.max(0.05F, scale) * 0.5D;

        return frustum.isVisible(new AABB(x - half, y - half, z - half, x + half, y + half, z + half));
    }

    private static class RenderCandidate
    {
        public final ItemSprayFormRenderer.SprayedItem item;
        public final double x;
        public final double y;
        public final double z;
        public final float scale;
        public final double distanceSq;
        public final long order;

        public RenderCandidate(ItemSprayFormRenderer.SprayedItem item, double x, double y, double z, float scale, double distanceSq, long order)
        {
            this.item = item;
            this.x = x;
            this.y = y;
            this.z = z;
            this.scale = scale;
            this.distanceSq = distanceSq;
            this.order = order;
        }
    }
}
