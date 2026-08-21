package gbeic.bbsplusplus.client.ui.pose;

import com.mojang.blaze3d.systems.RenderSystem;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.shapes.IKeyframeShapeRenderer;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.shapes.KeyframeShapeRenderers;
import mchorse.bbs_mod.utils.colors.Colors;
import mchorse.bbs_mod.utils.keyframes.KeyframeShape;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import org.joml.Matrix4f;

/**
 * 使用 BBS 关键帧形状渲染器绘制骨骼列表中的修改标记。
 *
 * <p>外层采用轨道菱形的 3 单位尺寸，内层采用 2 单位尺寸并填充黑色，与关键帧编辑器中
 * 未选中菱形的双层画法一致；因此不再依赖尺寸偏大的图标贴图。</p>
 */
public class PoseBoneMarkerRenderer
{
    /** 绘制与关键帧外观一致的双色菱形。 */
    public static void renderModifiedDiamond(UIContext context, int x, int y, int color)
    {
        IKeyframeShapeRenderer diamond = KeyframeShapeRenderers.SHAPES.get(KeyframeShape.DIAMOND);

        if (diamond == null)
        {
            return;
        }

        Matrix4f matrix = context.batcher.getContext().getMatrices().peek().getPositionMatrix();
        BufferBuilder builder = Tessellator.getInstance().getBuffer();

        builder.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
        diamond.renderKeyframe(context, builder, matrix, x, y, 3, color);
        diamond.renderKeyframe(context, builder, matrix, x, y, 2, Colors.A100);

        RenderSystem.enableBlend();
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);
        BufferRenderer.drawWithGlobalProgram(builder.end());
    }

    private PoseBoneMarkerRenderer()
    {}
}
