package gbeic.bbsplusplus.client.renderer;

import net.fabricmc.loader.api.FabricLoader;

import java.lang.reflect.Method;

/**
 * MediaPlayer-BBS 的反射桥。
 *
 * BBS++ 只把视频后端视为可选依赖，因此这里集中处理类查找、方法调用和失败原因。
 * 这样主工程无需在编译期依赖 MediaPlayer-BBS，也能在未安装后端时正常启动。
 */
public final class VideoBackendBridge
{
    private static boolean resolved;
    private static boolean available;
    private static String unavailableReason = "未检测到 MediaPlayer-BBS";
    private static Class<?> backendClass;
    private static Method isAvailableMethod;
    private static Method getUnavailableReasonMethod;
    private static Method openAssetVideoMethod;

    private VideoBackendBridge()
    {
    }

    public static boolean isAvailable()
    {
        resolve();

        return available;
    }

    public static String getUnavailableReason()
    {
        resolve();

        return unavailableReason;
    }

    public static DecoderHandle openAssetVideo(String relativePath)
    {
        resolve();

        if (!available)
        {
            throw new IllegalStateException(unavailableReason);
        }

        try
        {
            return new DecoderHandle(openAssetVideoMethod.invoke(null, relativePath));
        }
        catch (Exception e)
        {
            throw new RuntimeException("打开视频后端失败: " + e.getMessage(), e);
        }
    }

    private static void resolve()
    {
        if (resolved)
        {
            return;
        }

        resolved = true;

        if (!FabricLoader.getInstance().isModLoaded("mediaplayer"))
        {
            available = false;
            unavailableReason = "未检测到 MediaPlayer-BBS";
            return;
        }

        try
        {
            backendClass = Class.forName("net.hacker.mediaplayer.BBSVideoBackend");
            isAvailableMethod = backendClass.getMethod("isAvailable");
            getUnavailableReasonMethod = backendClass.getMethod("getUnavailableReason");
            openAssetVideoMethod = backendClass.getMethod("openAssetVideo", String.class);

            available = Boolean.TRUE.equals(isAvailableMethod.invoke(null));
            Object reason = getUnavailableReasonMethod.invoke(null);
            unavailableReason = reason instanceof String ? (String) reason : "";
        }
        catch (Exception e)
        {
            available = false;
            unavailableReason = "MediaPlayer-BBS API 不可用: " + e.getMessage();
        }
    }

    public static final class DecoderHandle implements AutoCloseable
    {
        private final Object decoder;
        private final Method renderTime;
        private final Method getTextureId;
        private final Method getMetadata;
        private final Method close;
        private final Object metadata;
        private final Method width;
        private final Method height;
        private final Method durationSeconds;

        private DecoderHandle(Object decoder) throws Exception
        {
            this.decoder = decoder;
            Class<?> decoderClass = decoder.getClass();

            this.renderTime = decoderClass.getMethod("renderTime", double.class);
            this.getTextureId = decoderClass.getMethod("getTextureId");
            this.getMetadata = decoderClass.getMethod("getMetadata");
            this.close = decoderClass.getMethod("close");
            this.metadata = this.getMetadata.invoke(decoder);

            Class<?> metadataClass = this.metadata.getClass();
            this.width = metadataClass.getMethod("width");
            this.height = metadataClass.getMethod("height");
            this.durationSeconds = metadataClass.getMethod("durationSeconds");
        }

        public void renderTime(double seconds)
        {
            try
            {
                this.renderTime.invoke(this.decoder, seconds);
            }
            catch (Exception e)
            {
                throw new RuntimeException("视频寻帧失败: " + e.getMessage(), e);
            }
        }

        public int getTextureId()
        {
            try
            {
                return ((Number) this.getTextureId.invoke(this.decoder)).intValue();
            }
            catch (Exception e)
            {
                return 0;
            }
        }

        public int getWidth()
        {
            return this.getInt(this.width);
        }

        public int getHeight()
        {
            return this.getInt(this.height);
        }

        public double getDurationSeconds()
        {
            try
            {
                return ((Number) this.durationSeconds.invoke(this.metadata)).doubleValue();
            }
            catch (Exception e)
            {
                return 0D;
            }
        }

        private int getInt(Method method)
        {
            try
            {
                return ((Number) method.invoke(this.metadata)).intValue();
            }
            catch (Exception e)
            {
                return 0;
            }
        }

        @Override
        public void close()
        {
            try
            {
                this.close.invoke(this.decoder);
            }
            catch (Exception ignored)
            {
            }
        }
    }
}
