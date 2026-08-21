package gbeic.bbsplusplus.client.compat.iris;

import com.mojang.blaze3d.platform.GlStateManager;
import gbeic.bbsplusplus.client.WorldFilmShaderCurveState;
import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.camera.clips.misc.CurveClip;
import mchorse.bbs_mod.camera.controller.CameraWorkCameraController;
import mchorse.bbs_mod.camera.controller.ICameraController;
import net.minecraft.client.MinecraftClient;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL11C;
import org.lwjgl.opengl.GL12C;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;

import java.nio.ByteBuffer;
import java.util.Map;
import java.util.Optional;

/**
 * 为 Iris 提供两条 BBS 原版没有的可动画光影参数：日月偏角与焦点。
 * <p>
 * 移植自 BBSTools 4.1。这两个值本身不是光影包里的宏，而是 Iris 自己算出来喂给着色器的量，
 * 所以没法走 ShaderCurves 那套宏替换，只能在 Iris 读取它们的位置把值换掉。
 * 本类负责从当前播放的曲线剪辑里取出这两个值，具体的替换由 {@code mixin.shadercurves} 包下的几个注入完成。
 * </p>
 * <p>
 * 相比 BBSTools 原版，这里额外接上了 BBS++ 的 {@link WorldFilmShaderCurveState}，
 * 因此右 Ctrl 在世界里播放影片时这两条曲线同样生效，而不只是在相机编辑器预览中生效。
 * </p>
 */
public final class ShaderCurveState
{
    /** 日月偏角曲线的通道 ID */
    public static final String SUN_PATH_ROTATION = "sun_path_rotation";

    /** 焦点曲线的通道 ID */
    public static final String CENTER_DEPTH = "center_depth";

    /** 焦点深度纹理的近平面，与 Iris 自身取值保持一致 */
    private static final double NEAR_PLANE = 0.05D;

    private static Float sunPathRotationValue;
    private static Integer centerDepthFBO;
    private static Integer centerDepthTexture;

    private ShaderCurveState()
    {}

    /** 外部强制指定日月偏角，传 {@code null} 表示回到跟随曲线 */
    public static void setSunPathRotationValue(Float value)
    {
        sunPathRotationValue = value;
    }

    /**
     * 取当前的日月偏角。
     *
     * @param fallback 曲线没有提供该参数时使用的原始值
     */
    public static float getSunPathRotation(float fallback)
    {
        if (sunPathRotationValue != null)
        {
            return sunPathRotationValue;
        }

        Double value = getCurveValue(SUN_PATH_ROTATION);

        return value == null ? fallback : value.floatValue();
    }

    /**
     * 按曲线里的焦点距离生成一张 1×1 的深度纹理，交给 Iris 当作「屏幕中心深度」使用。
     * <p>
     * Iris 原本是采样屏幕正中央的实际深度来做景深，画面里没有实体遮挡时对焦点会乱跳；
     * 这里改成由曲线直接指定对焦距离，把距离反算成深度缓冲里的值写进纹理。
     * </p>
     *
     * @return 曲线没有启用焦点时返回空，调用方应回退到 Iris 原本的纹理
     */
    public static Optional<Integer> getCenterDepthTexture()
    {
        MinecraftClient client = MinecraftClient.getInstance();

        if (!client.isOnThread())
        {
            return Optional.empty();
        }

        Double value = getCurveValue(CENTER_DEPTH);

        if (value == null || value == 0.0D)
        {
            return Optional.empty();
        }

        double far = client.options.getClampedViewDistance() * 16.0D;
        // 把观察空间距离反算成 [0,1] 的深度缓冲值
        double depth = ((far + NEAR_PLANE) * value - 2.0D * NEAR_PLANE * far) / value / (far - NEAR_PLANE) * 0.5D + 0.5D;

        if (centerDepthTexture == null)
        {
            centerDepthTexture = GlStateManager._genTexture();

            GL11C.glBindTexture(GL11C.GL_TEXTURE_2D, centerDepthTexture);
            GL11C.glTexParameteri(GL11C.GL_TEXTURE_2D, GL11C.GL_TEXTURE_MIN_FILTER, GL11C.GL_NEAREST);
            GL11C.glTexParameteri(GL11C.GL_TEXTURE_2D, GL11C.GL_TEXTURE_MAG_FILTER, GL11C.GL_NEAREST);
            GL11C.glTexParameteri(GL11C.GL_TEXTURE_2D, GL11C.GL_TEXTURE_WRAP_S, GL12C.GL_CLAMP_TO_EDGE);
            GL11C.glTexParameteri(GL11C.GL_TEXTURE_2D, GL11C.GL_TEXTURE_WRAP_T, GL12C.GL_CLAMP_TO_EDGE);
            GL11C.glTexImage2D(GL11C.GL_TEXTURE_2D, 0, GL30.GL_R32F, 1, 1, 0, GL11C.GL_RED, GL11C.GL_FLOAT, (ByteBuffer) null);
            GL11C.glBindTexture(GL11C.GL_TEXTURE_2D, 0);
        }

        if (centerDepthFBO == null)
        {
            centerDepthFBO = GL30.glGenFramebuffers();

            int previous = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);

            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, centerDepthFBO);
            GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL11C.GL_TEXTURE_2D, centerDepthTexture, 0);
            GL20.glDrawBuffers(GL30.GL_COLOR_ATTACHMENT0);
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, previous);
        }

        int previous = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);

        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, centerDepthFBO);
        GL11.glClearColor((float) depth, 0.0F, 0.0F, 0.0F);
        GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);
        GL11.glFinish();
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, previous);

        return Optional.of(centerDepthTexture);
    }

    /**
     * 读取当前帧某条曲线的值。
     * <p>
     * 优先取相机编辑器预览时的相机控制器上下文；没有的话再取 BBS++ 世界内播放影片时缓存的采样结果。
     * </p>
     */
    private static Double getCurveValue(String id)
    {
        ICameraController controller = BBSModClient.getCameraController().getCurrent();

        if (controller instanceof CameraWorkCameraController)
        {
            Map<String, Double> values = CurveClip.getValues(((CameraWorkCameraController) controller).getContext());
            Double value = values.get(id);

            if (value != null)
            {
                return value;
            }
        }

        return WorldFilmShaderCurveState.getValue(id);
    }
}
