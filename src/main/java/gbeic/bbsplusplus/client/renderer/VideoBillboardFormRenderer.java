package gbeic.bbsplusplus.client.renderer;

import com.mojang.blaze3d.systems.RenderSystem;
import gbeic.bbsplusplus.forms.VideoBillboardForm;
import mchorse.bbs_mod.forms.ITickable;
import mchorse.bbs_mod.forms.entities.IEntity;
import mchorse.bbs_mod.forms.renderers.FormRenderType;
import mchorse.bbs_mod.forms.renderers.FormRenderingContext;
import mchorse.bbs_mod.forms.renderers.FormRenderer;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.colors.Colors;
import mchorse.bbs_mod.utils.joml.Vectors;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.math.MatrixStack;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * 视频广告牌形态渲染器。
 *
 * 它把 BBS 影片 tick 换算成秒数，交给 MediaPlayer-BBS 按时间轴寻帧，
 * 再使用返回的 OpenGL 纹理绘制双面平面。渲染器自身只管理时间、尺寸和资源生命周期。
 */
public class VideoBillboardFormRenderer extends FormRenderer<VideoBillboardForm> implements ITickable
{
    private VideoBackendBridge.DecoderHandle decoder;
    private String currentPath;
    private int currentTick;
    private boolean lastRestart;
    private long lastPreviewTime;
    private double lastPreviewSeconds;

    public VideoBillboardFormRenderer(VideoBillboardForm form)
    {
        super(form);
    }

    @Override
    public void renderInUI(UIContext context, int x1, int y1, int x2, int y2)
    {
        int cx = (x1 + x2) / 2;
        int cy = (y1 + y2) / 2;

        context.batcher.icon(Icons.FILM, Colors.WHITE, cx, cy, 1F, 1F);
    }

    @Override
    public void render3D(FormRenderingContext context)
    {
        if (context.type == FormRenderType.ITEM_INVENTORY)
        {
            return;
        }

        if (!this.ensureDecoder())
        {
            return;
        }

        double seconds = this.computeSeconds(context);

        if (Double.isNaN(seconds))
        {
            return;
        }

        try
        {
            this.decoder.renderTime(seconds);
        }
        catch (Exception e)
        {
            this.closeDecoder();
            return;
        }

        int textureId = this.decoder.getTextureId();

        if (textureId <= 0)
        {
            return;
        }

        this.renderPlane(context.stack, textureId);
    }

    private boolean ensureDecoder()
    {
        Link link = this.form.video.get();
        String path = link == null ? null : link.path;
        boolean restart = this.form.restart.get();

        if (restart && !this.lastRestart)
        {
            this.closeDecoder();
            this.form.restart.set(false);
        }

        this.lastRestart = restart;

        if (path == null || path.isEmpty())
        {
            this.closeDecoder();
            return false;
        }

        if (this.decoder != null && path.equals(this.currentPath))
        {
            return true;
        }

        this.closeDecoder();

        if (!VideoBackendBridge.isAvailable())
        {
            return false;
        }

        try
        {
            this.decoder = VideoBackendBridge.openAssetVideo(path);
            this.currentPath = path;
            this.applyMetadataSize();

            return true;
        }
        catch (Exception e)
        {
            this.closeDecoder();
            return false;
        }
    }

    private void applyMetadataSize()
    {
        if (this.decoder == null || !this.form.keepAspectRatio.get())
        {
            return;
        }

        int w = this.decoder.getWidth();
        int h = this.decoder.getHeight();

        if (w > 0 && h > 0)
        {
            float height = this.form.height.get();

            if (height <= 0F)
            {
                height = 1F;
                this.form.height.set(height);
            }

            this.form.width.set(height * w / (float) h);
        }
    }

    private double computeSeconds(FormRenderingContext context)
    {
        double base;

        if (context.type == FormRenderType.PREVIEW)
        {
            if (this.form.paused.get())
            {
                return this.lastPreviewSeconds;
            }

            long now = System.currentTimeMillis();
            long delta = this.lastPreviewTime <= 0 ? 0 : now - this.lastPreviewTime;

            this.lastPreviewTime = now;
            this.lastPreviewSeconds += delta / 1000D * Math.max(0D, this.form.speed.get());
            base = this.lastPreviewSeconds;
        }
        else
        {
            base = this.form.offsetSeconds.get() + this.currentTick / 20D * this.form.speed.get();
        }

        return this.applyRange(base);
    }

    private double applyRange(double seconds)
    {
        double duration = this.decoder == null ? 0D : this.decoder.getDurationSeconds();
        double loopStart = Math.max(0D, this.form.loopStart.get());
        double loopEnd = Math.max(loopStart, this.form.loopEnd.get());
        double end = loopEnd > loopStart ? loopEnd : duration;

        if (this.form.loop.get() && end > loopStart)
        {
            if (seconds < loopStart)
            {
                return loopStart;
            }

            return loopStart + ((seconds - loopStart) % (end - loopStart));
        }

        if (duration > 0D && seconds > duration)
        {
            if (this.form.outOfRange.get() == VideoBillboardForm.OUT_OF_RANGE_HIDE)
            {
                return Double.NaN;
            }

            if (this.form.outOfRange.get() == VideoBillboardForm.OUT_OF_RANGE_LOOP)
            {
                return seconds % duration;
            }

            return duration;
        }

        return Math.max(0D, seconds);
    }

    private void renderPlane(MatrixStack matrices, int textureId)
    {
        float width = Math.max(0.001F, this.form.width.get());
        float height = Math.max(0.001F, this.form.height.get());
        float halfWidth = width / 2F;
        float halfHeight = height / 2F;

        if (this.form.billboard.get())
        {
            Matrix4f modelMatrix = matrices.peek().getPositionMatrix();
            Vector3f scale = Vectors.TEMP_3F;

            modelMatrix.getScale(scale);
            modelMatrix.m00(1).m01(0).m02(0);
            modelMatrix.m10(0).m11(1).m12(0);
            modelMatrix.m20(0).m21(0).m22(1);
            modelMatrix.scale(scale);
            matrices.peek().getNormalMatrix().identity();
        }

        RenderSystem.setShader(GameRenderer::getPositionTexProgram);
        RenderSystem.setShaderTexture(0, textureId);
        RenderSystem.defaultBlendFunc();
        RenderSystem.enableBlend();
        RenderSystem.disableCull();

        BufferBuilder builder = Tessellator.getInstance().getBuffer();
        Matrix4f matrix = matrices.peek().getPositionMatrix();

        builder.begin(VertexFormat.DrawMode.TRIANGLES, VertexFormats.POSITION_TEXTURE);
        fill(builder, matrix, -halfWidth, -halfHeight, 0F, 1F);
        fill(builder, matrix, halfWidth, halfHeight, 1F, 0F);
        fill(builder, matrix, -halfWidth, halfHeight, 0F, 0F);

        fill(builder, matrix, -halfWidth, -halfHeight, 0F, 1F);
        fill(builder, matrix, halfWidth, -halfHeight, 1F, 1F);
        fill(builder, matrix, halfWidth, halfHeight, 1F, 0F);

        fill(builder, matrix, -halfWidth, halfHeight, 0F, 0F);
        fill(builder, matrix, halfWidth, halfHeight, 1F, 0F);
        fill(builder, matrix, -halfWidth, -halfHeight, 0F, 1F);

        fill(builder, matrix, halfWidth, halfHeight, 1F, 0F);
        fill(builder, matrix, halfWidth, -halfHeight, 1F, 1F);
        fill(builder, matrix, -halfWidth, -halfHeight, 0F, 1F);
        BufferRenderer.drawWithGlobalProgram(builder.end());

        RenderSystem.enableCull();
    }

    private static void fill(BufferBuilder builder, Matrix4f matrix, float x, float y, float u, float v)
    {
        builder.vertex(matrix, x, y, 0F).texture(u, v).next();
    }

    private void closeDecoder()
    {
        if (this.decoder != null)
        {
            this.decoder.close();
            this.decoder = null;
        }

        this.currentPath = null;
    }

    @Override
    public void tick(IEntity entity)
    {
        if (entity != null)
        {
            this.currentTick = entity.getAge();
        }

        if (MinecraftClient.getInstance().isPaused() || this.form.paused.get())
        {
            this.lastPreviewTime = 0L;
        }
    }
}
