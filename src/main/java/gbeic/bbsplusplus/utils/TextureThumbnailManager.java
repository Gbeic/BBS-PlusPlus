package gbeic.bbsplusplus.utils;

import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.graphics.texture.Texture;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.utils.resources.Pixels;
import org.lwjgl.opengl.GL11;

import java.io.InputStream;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 异步纹理缩略图加载器，专为网格模式排版优化。
 * 在后台线程加载和缩放高清图片，避免主线程卡顿，同时使用 256x256 以下的低分辨率以节省显存。
 */
public class TextureThumbnailManager {
    private static final int MAX_SIZE = 256;
    private static final Map<Link, Texture> thumbnails = new ConcurrentHashMap<>();
    private static final Set<Link> loadingLinks = ConcurrentHashMap.newKeySet();
    private static final ConcurrentLinkedQueue<UploadTask> uploadQueue = new ConcurrentLinkedQueue<>();
    private static final ExecutorService executor = Executors.newFixedThreadPool(
            Math.max(2, Runtime.getRuntime().availableProcessors() / 2),
            r -> {
                Thread t = new Thread(r, "BBS++ Thumbnail Loader");
                t.setDaemon(true);
                return t;
            }
    );

    private static class UploadTask {
        Link link;
        Pixels pixels;

        UploadTask(Link link, Pixels pixels) {
            this.link = link;
            this.pixels = pixels;
        }
    }

    /**
     * 获取指定 Link 的缩略图。
     * 如果未加载，则触发异步加载，并返回 null。
     */
    public static Texture getThumbnail(Link link) {
        Texture tex = thumbnails.get(link);
        if (tex != null) {
            return tex;
        }

        Texture globalTex = mchorse.bbs_mod.BBSModClient.getTextures().textures.get(link);
        if (globalTex != null) {
            return globalTex;
        }

        // 针对小文件或游戏内置资源（Jar包内）进行同步加载优化
        java.io.File file = mchorse.bbs_mod.BBSMod.getProvider().getFile(link);
        boolean isLargeFile = file != null && file.exists() && file.length() >= 500 * 1024;
        
        if (!isLargeFile) {
            // 如果文件不存在（如内置 assets）、或者存在但小于 500KB，则直接同步加载
            return mchorse.bbs_mod.BBSModClient.getTextures().getTexture(link);
        }

        if (loadingLinks.add(link)) {
            // 开始异步加载
            executor.submit(() -> loadAndScale(link));
        }

        return null; // 返回 null，调用方应使用占位图
    }

    private static void loadAndScale(Link link) {
        boolean success = false;
        Pixels pixels = null;
        try {
            // 获取输入流
            try (InputStream stream = BBSMod.getProvider().getAsset(link)) {
                if (stream != null) {
                    pixels = Pixels.fromPNGStream(stream);
                }
            }

            if (pixels != null) {
                // 如果图片尺寸大于 MAX_SIZE，进行缩放
                if (pixels.width > MAX_SIZE || pixels.height > MAX_SIZE) {
                    float ratio = (float) pixels.width / pixels.height;
                    int newW = MAX_SIZE;
                    int newH = MAX_SIZE;
                    if (ratio > 1) {
                        newH = (int) (MAX_SIZE / ratio);
                    } else {
                        newW = (int) (MAX_SIZE * ratio);
                    }

                    Pixels scaled = Pixels.fromSize(newW, newH);
                    scaled.drawPixels(pixels, 0, 0, newW, newH, 0, 0, pixels.width, pixels.height);
                    pixels.delete(); // 释放原图内存
                    pixels = scaled;
                }

                // 关键修复：由于 drawPixels 等操作会改变 ByteBuffer 的 position 到末尾，
                // 在传递给 OpenGL 之前必须将其倒带到 0，否则会导致越界读取引发 EXCEPTION_ACCESS_VIOLATION 闪退！
                pixels.rewindBuffer();

                // 漏洞修复：如果在处理期间玩家关闭了 UI 并触发了 clear()，这里必须立即拦截
                // 否则 pixels 会一直卡在未执行 update 的 uploadQueue 中，导致 Native 内存泄漏
                if (!loadingLinks.contains(link)) {
                    pixels.delete();
                    return;
                }

                uploadQueue.add(new UploadTask(link, pixels));
                success = true;
            }
        } catch (Throwable e) {
            // 使用 Throwable 防止抛出 Error 导致线程死亡而未处理
            e.printStackTrace();
        } finally {
            if (!success) {
                loadingLinks.remove(link);
                // 如果加载失败或中途被拦截，确保 Native 内存被释放
                if (pixels != null) {
                    try { pixels.delete(); } catch (Throwable ignored) {}
                }
            }
        }
    }

    /**
     * 应在每帧或渲染列表中调用，处理后台加载完毕的缩略图上传到显存。
     */
    public static void update() {
        UploadTask task;
        // 限制每帧最多上传几个纹理，避免突然拥堵主线程
        int uploadsThisFrame = 0;
        while (uploadsThisFrame < 5 && (task = uploadQueue.poll()) != null) {
            try {
                // 如果在队列等待期间被 clear() 清空了该记录，则跳过并释放
                if (!loadingLinks.contains(task.link)) {
                    task.pixels.delete();
                    continue;
                }

                Texture texture = Texture.textureFromPixels(task.pixels, GL11.GL_NEAREST);
                thumbnails.put(task.link, texture);
                loadingLinks.remove(task.link);
                uploadsThisFrame++;
            } catch (Exception e) {
                e.printStackTrace();
                loadingLinks.remove(task.link);
                if (task.pixels != null) {
                    task.pixels.delete();
                }
            }
        }
    }

    /**
     * 清理所有缓存和正在排队的任务，释放显存。
     * 通常在离开当前文件夹或关闭界面时调用。
     */
    public static void clear() {
        uploadQueue.clear();
        loadingLinks.clear();
        for (Texture texture : thumbnails.values()) {
            texture.delete();
        }
        thumbnails.clear();
    }
}
