package gbeic.bbsplusplus.client.renderer;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import gbeic.bbsplusplus.forms.ItemSprayForm;
import mchorse.bbs_mod.graphics.Draw;
import mchorse.bbs_mod.utils.Axis;
import mchorse.bbs_mod.utils.colors.Color;
import mchorse.bbs_mod.utils.colors.Colors;
import net.minecraft.client.renderer.GameRenderer;
import org.joml.Matrix4f;

import java.util.function.Consumer;

public final class SprayGuideRenderer
{
    private static final int CIRCLE_SEGMENTS = 48;
    private static final float WIRE_ALPHA = 0.92F;
    private static final float AXIS_ALPHA = 0.75F;
    private static final Color SPHERE_SPAWN_OFFSET_COLOR = Color.rgb(Colors.BLUE);

    private SprayGuideRenderer() {}

    public static void renderSprayGuide(PoseStack stack, Color color, float range, float outerAngle)
    {
        renderSprayGuide(stack, color, ItemSprayForm.SHAPE_CONE, range, outerAngle, 0F, 0F, 0F);
    }

    public static void renderSprayGuide(PoseStack stack, Color color, int shape, float range, float outerAngle, float width, float height, float spawnOffset)
    {
        if (shape == ItemSprayForm.SHAPE_PLANE)
        {
            renderPlaneGuide(stack, color, range, width, height);
        }
        else if (shape == ItemSprayForm.SHAPE_SPHERE_OUT || shape == ItemSprayForm.SHAPE_SPHERE_IN)
        {
            renderSphereGuide(stack, color, shape, range, spawnOffset);
        }
        else
        {
            renderConeGuide(stack, color, range, outerAngle);
        }
    }

    private static void renderConeGuide(PoseStack stack, Color color, float range, float outerAngle)
    {
        float r = Math.max(range, 0.05F);
        float outer = Math.max(outerAngle, 1F);
        float outerR = coneRadius(r, outer);
        float t = clamp(r * 0.0020F, 0.002F, 0.009F);

        renderTriangles((builder) ->
        {
            // 辅助线需要跟模型方块编辑器里的可视正面一致，因此绘制在局部 -Z 一侧。
            line(builder, stack, 0, 0, 0, 0, 0, -r, t, color, AXIS_ALPHA);
            coneWire(builder, stack, -r, outerR, t, color, WIRE_ALPHA);
            ringAtZ(builder, stack, r, outerR, t, color, WIRE_ALPHA);
        });
    }

    private static void renderPlaneGuide(PoseStack stack, Color color, float range, float width, float height)
    {
        float w = Math.max(width, 0.05F) * 0.5F;
        float h = Math.max(height, 0.05F) * 0.5F;
        float r = Math.max(range, 0.05F);
        float t = clamp(Math.max(width, height) * 0.0025F, 0.002F, 0.009F);

        renderTriangles((builder) ->
        {
            line(builder, stack, -w, -h, 0F,  w, -h, 0F, t, color, WIRE_ALPHA);
            line(builder, stack,  w, -h, 0F,  w,  h, 0F, t, color, WIRE_ALPHA);
            line(builder, stack,  w,  h, 0F, -w,  h, 0F, t, color, WIRE_ALPHA);
            line(builder, stack, -w,  h, 0F, -w, -h, 0F, t, color, WIRE_ALPHA);
            line(builder, stack, 0F, 0F, 0F, 0F, 0F, -r, t, color, AXIS_ALPHA);
        });
    }

    private static void renderSphereGuide(PoseStack stack, Color color, int shape, float range, float spawnOffset)
    {
        float travel = Math.max(range, 0F);
        float offset = Math.max(spawnOffset, 0F);
        float spawn = shape == ItemSprayForm.SHAPE_SPHERE_IN ? Math.max(travel - offset, 0F) : offset;
        float spawnVisualRadius = Math.max(spawn, 0.05F);
        float spawnT = clamp(spawn * 0.003F, 0.002F, 0.009F);

        renderTriangles((builder) ->
        {
            if (travel > 0.0001F)
            {
                float rangeRadius = Math.max(travel, 0.05F);
                float rangeT = clamp(rangeRadius * 0.003F, 0.002F, 0.009F);

                sphereRangeLines(builder, stack, 0F, rangeRadius, rangeT, color);
                renderCircle(builder, stack, Axis.X, rangeRadius, rangeT, color, AXIS_ALPHA);
                renderCircle(builder, stack, Axis.Y, rangeRadius, rangeT, color, AXIS_ALPHA);
                renderCircle(builder, stack, Axis.Z, rangeRadius, rangeT, color, AXIS_ALPHA);
            }

            // 蓝色球面表示粒子的实际生成起点；白色球面表示射程，两者互不影响。
            renderCircle(builder, stack, Axis.X, spawnVisualRadius, spawnT, SPHERE_SPAWN_OFFSET_COLOR, WIRE_ALPHA);
            renderCircle(builder, stack, Axis.Y, spawnVisualRadius, spawnT, SPHERE_SPAWN_OFFSET_COLOR, WIRE_ALPHA);
            renderCircle(builder, stack, Axis.Z, spawnVisualRadius, spawnT, SPHERE_SPAWN_OFFSET_COLOR, WIRE_ALPHA);
        });
    }

    private static void renderTriangles(Consumer<BufferBuilder> consumer)
    {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.depthMask(false);
        RenderSystem.disableDepthTest();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);

        /* 1.21.1 起 Tesselator 不再提供可复用的 getBuffer()，begin() 直接返回一次性的 BufferBuilder，
         * 因此必须先 begin 拿到 builder 再填顶点，最后用 buildOrThrow() 产出的 MeshData 一次性提交。 */
        BufferBuilder builder = Tesselator.getInstance().begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);

        consumer.accept(builder);

        BufferUploader.drawWithShader(builder.buildOrThrow());

        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
    }

    private static void coneWire(BufferBuilder builder, PoseStack stack, float capZ, float radius, float t, Color color, float alpha)
    {
        line(builder, stack, 0, 0, 0,  radius, 0, capZ, t, color, alpha);
        line(builder, stack, 0, 0, 0, -radius, 0, capZ, t, color, alpha);
        line(builder, stack, 0, 0, 0, 0,  radius, capZ, t, color, alpha);
        line(builder, stack, 0, 0, 0, 0, -radius, capZ, t, color, alpha);
    }

    private static void ringAtZ(BufferBuilder builder, PoseStack stack, float z, float radius, float t, Color color, float alpha)
    {
        stack.pushPose();
        stack.translate(0, 0, z);
        renderCircle(builder, stack, Axis.Z, radius, t, color, alpha);
        stack.popPose();
    }

    private static void sphereRangeLines(BufferBuilder builder, PoseStack stack, float start, float end, float t, Color color)
    {
        line(builder, stack,  start, 0F, 0F,  end, 0F, 0F, t, color, AXIS_ALPHA);
        line(builder, stack, -start, 0F, 0F, -end, 0F, 0F, t, color, AXIS_ALPHA);
        line(builder, stack, 0F,  start, 0F, 0F,  end, 0F, t, color, AXIS_ALPHA);
        line(builder, stack, 0F, -start, 0F, 0F, -end, 0F, t, color, AXIS_ALPHA);
        line(builder, stack, 0F, 0F,  start, 0F, 0F,  end, t, color, AXIS_ALPHA);
        line(builder, stack, 0F, 0F, -start, 0F, 0F, -end, t, color, AXIS_ALPHA);
    }

    private static void renderCircle(BufferBuilder builder, PoseStack stack, Axis axis, float radius, float thickness, Color color, float alpha)
    {
        if (radius <= 0.0001F)
        {
            return;
        }

        Matrix4f m = stack.last().pose();
        float r = color.r, g = color.g, b = color.b;
        float halfT = thickness * 0.5F;
        float rIn = Math.max(radius - halfT, 0F);
        float rOut = radius + halfT;

        for (int i = 0; i < CIRCLE_SEGMENTS; i++)
        {
            double a1 = Math.PI * 2D * i / CIRCLE_SEGMENTS;
            double a2 = Math.PI * 2D * (i + 1) / CIRCLE_SEGMENTS;
            float c1 = (float) Math.cos(a1);
            float s1 = (float) Math.sin(a1);
            float c2 = (float) Math.cos(a2);
            float s2 = (float) Math.sin(a2);

            float ix1, iy1, iz1, ox1, oy1, oz1;
            float ix2, iy2, iz2, ox2, oy2, oz2;

            if (axis == Axis.X)
            {
                ix1 = 0; iy1 = s1 * rIn;  iz1 = c1 * rIn;
                ox1 = 0; oy1 = s1 * rOut; oz1 = c1 * rOut;
                ix2 = 0; iy2 = s2 * rIn;  iz2 = c2 * rIn;
                ox2 = 0; oy2 = s2 * rOut; oz2 = c2 * rOut;
            }
            else if (axis == Axis.Y)
            {
                ix1 = s1 * rIn;  iy1 = 0; iz1 = c1 * rIn;
                ox1 = s1 * rOut; oy1 = 0; oz1 = c1 * rOut;
                ix2 = s2 * rIn;  iy2 = 0; iz2 = c2 * rIn;
                ox2 = s2 * rOut; oy2 = 0; oz2 = c2 * rOut;
            }
            else
            {
                ix1 = s1 * rIn;  iy1 = c1 * rIn;  iz1 = 0;
                ox1 = s1 * rOut; oy1 = c1 * rOut; oz1 = 0;
                ix2 = s2 * rIn;  iy2 = c2 * rIn;  iz2 = 0;
                ox2 = s2 * rOut; oy2 = c2 * rOut; oz2 = 0;
            }

            vertex(builder, m, ix1, iy1, iz1, r, g, b, alpha);
            vertex(builder, m, ox1, oy1, oz1, r, g, b, alpha);
            vertex(builder, m, ox2, oy2, oz2, r, g, b, alpha);

            vertex(builder, m, ix1, iy1, iz1, r, g, b, alpha);
            vertex(builder, m, ox2, oy2, oz2, r, g, b, alpha);
            vertex(builder, m, ix2, iy2, iz2, r, g, b, alpha);
        }
    }

    private static void vertex(BufferBuilder builder, Matrix4f m, float x, float y, float z, float r, float g, float b, float a)
    {
        // 1.21.1 删除了 .next()，链式调用结束即代表一个顶点写入完成。
        builder.addVertex(m, x, y, z).setColor(r, g, b, a);
    }

    private static void line(BufferBuilder builder, PoseStack stack, float x1, float y1, float z1, float x2, float y2, float z2, float t, Color color, float alpha)
    {
        Draw.fillBoxTo(builder, stack, x1, y1, z1, x2, y2, z2, t, color.r, color.g, color.b, alpha);
    }

    private static float coneRadius(float range, float angle)
    {
        return (float) (Math.tan(Math.toRadians(angle * 0.5F)) * range);
    }

    private static float clamp(float value, float min, float max)
    {
        return Math.max(min, Math.min(max, value));
    }
}
