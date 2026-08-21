package gbeic.bbsplusplus.mixin;

import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.settings.values.ui.ValueMotionPath;
import mchorse.bbs_mod.ui.film.controller.MotionPath;
import mchorse.bbs_mod.ui.film.controller.UIFilmController;
import mchorse.bbs_mod.utils.Pair;
import mchorse.bbs_mod.utils.keyframes.KeyframeChannel;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Arrays;

/**
 * 修正运动路径在长影片骨骼编辑时的采样范围。
 * <p>
 * bbs-fs 的运动路径绘制虽然提供“仅当前帧附近”开关，但骨骼路径的缓存重建仍会先按整段
 * pose 动画逐 tick 采样，再在绘制阶段裁剪显示范围。长影片中拖动骨骼 xyz 数值会连续改变
 * pose 通道签名，导致每次变更都重算整段骨骼世界坐标。
 * </p>
 * <p>
 * 此 Mixin 在不复制运动路径渲染器的前提下，只把骨骼轨迹的计算范围改为当前帧窗口，并把窗口
 * 纳入缓存签名。用户关闭“仅当前帧附近”时仍保留原版完整轨迹。
 * </p>
 */
@Mixin(MotionPath.class)
public class MotionPathMixin
{
    @Unique
    private static ValueMotionPath bbsplusplus$currentMotionPathConfig;

    @Unique
    private static float bbsplusplus$currentMotionPathTick;

    /**
     * 注入目标：{@link MotionPath#render(WorldRenderContext, ValueMotionPath, UIFilmController, Replay, Pair, float)} 开始。
     * 注入原因：骨骼轨迹计算方法拿不到运动路径配置，需要在本次渲染调用期间暂存当前配置和时间。
     * 修改行为：只保存当前线程同步渲染所需的配置引用，不改变原渲染流程。
     */
    @Inject(method = "render", at = @At("HEAD"), remap = false)
    private static void bbsplusplus$captureMotionPathConfig(WorldRenderContext context, ValueMotionPath config, UIFilmController controller, Replay replay, Pair<String, Boolean> bone, float currentTick, CallbackInfo ci)
    {
        bbsplusplus$currentMotionPathConfig = config;
        bbsplusplus$currentMotionPathTick = currentTick;
    }

    /**
     * 注入目标：{@link MotionPath#render(WorldRenderContext, ValueMotionPath, UIFilmController, Replay, Pair, float)} 结束。
     * 注入原因：暂存配置只属于本次运动路径渲染，渲染结束后清空可避免影响之后的异常调用路径。
     * 修改行为：清理本次调用上下文。
     */
    @Inject(method = "render", at = @At("RETURN"), remap = false)
    private static void bbsplusplus$clearMotionPathConfig(WorldRenderContext context, ValueMotionPath config, UIFilmController controller, Replay replay, Pair<String, Boolean> bone, float currentTick, CallbackInfo ci)
    {
        bbsplusplus$currentMotionPathConfig = null;
        bbsplusplus$currentMotionPathTick = 0F;
    }

    /**
     * 注入目标：骨骼轨迹缓存签名生成。
     * 注入原因：启用“仅当前帧附近”后，采样窗口会随当前帧变化；如果签名不包含窗口，缓存不会跟随播放头移动。
     * 修改行为：在原有动画内容签名之外追加当前采样窗口。
     */
    @Redirect(
        method = "boneTrajectory",
        at = @At(value = "INVOKE", target = "Lmchorse/bbs_mod/ui/film/controller/MotionPath;signature(Lmchorse/bbs_mod/film/replays/Replay;Ljava/lang/String;)Ljava/lang/String;"),
        remap = false
    )
    private static String bbsplusplus$signatureWithMotionPathWindow(Replay replay, String bonePath)
    {
        String signature = bbsplusplus$signature(replay, bonePath);
        float[] window = bbsplusplus$visibleRange(replay);

        return window == null ? signature : signature + "|window=" + window[0] + ':' + window[1];
    }

    /**
     * 注入目标：骨骼轨迹重建时获取完整动画范围的位置。
     * 注入原因：“仅当前帧附近”原本只裁剪绘制范围，不裁剪骨骼逐 tick 采样范围，长 pose 轨道编辑时会极卡。
     * 修改行为：开启该选项时只采样当前帧前后配置范围内的骨骼路径；关闭时仍采样整段。
     */
    @Redirect(
        method = "computeBoneTrajectory",
        at = @At(value = "INVOKE", target = "Lmchorse/bbs_mod/ui/film/controller/MotionPath;range(Lmchorse/bbs_mod/film/replays/Replay;)[F"),
        remap = false
    )
    private static float[] bbsplusplus$limitBoneTrajectoryRange(Replay replay)
    {
        return bbsplusplus$visibleRange(replay);
    }

    @Unique
    private static float[] bbsplusplus$visibleRange(Replay replay)
    {
        float[] range = bbsplusplus$range(replay);
        ValueMotionPath config = bbsplusplus$currentMotionPathConfig;

        if (range == null || config == null || !config.aroundCurrent.get())
        {
            return range;
        }

        float first = Math.max(range[0], bbsplusplus$currentMotionPathTick - config.before.get());
        float last = Math.min(range[1], bbsplusplus$currentMotionPathTick + config.after.get());

        return first > last ? null : new float[] {first, last};
    }

    @Unique
    private static float[] bbsplusplus$range(Replay replay)
    {
        float first = Float.MAX_VALUE;
        float last = -Float.MAX_VALUE;

        for (KeyframeChannel<?> channel : Arrays.asList(replay.keyframes.x, replay.keyframes.y, replay.keyframes.z))
        {
            first = Math.min(first, bbsplusplus$firstTick(channel));
            last = Math.max(last, bbsplusplus$lastTick(channel));
        }

        for (KeyframeChannel<?> channel : replay.properties.properties.values())
        {
            first = Math.min(first, bbsplusplus$firstTick(channel));
            last = Math.max(last, bbsplusplus$lastTick(channel));
        }

        return last < first ? null : new float[] {first, last};
    }

    @Unique
    private static float bbsplusplus$firstTick(KeyframeChannel<?> channel)
    {
        return channel.isEmpty() ? Float.MAX_VALUE : channel.get(0).getTick();
    }

    @Unique
    private static float bbsplusplus$lastTick(KeyframeChannel<?> channel)
    {
        return channel.isEmpty() ? -Float.MAX_VALUE : channel.get(channel.getKeyframes().size() - 1).getTick();
    }

    @Unique
    private static String bbsplusplus$signature(Replay replay, String bonePath)
    {
        StringBuilder builder = new StringBuilder(replay.getId()).append('|').append(bonePath);

        bbsplusplus$appendSignature(builder, replay.keyframes.x);
        bbsplusplus$appendSignature(builder, replay.keyframes.y);
        bbsplusplus$appendSignature(builder, replay.keyframes.z);

        for (KeyframeChannel<?> channel : replay.properties.properties.values())
        {
            builder.append('#').append(channel.getId());
            bbsplusplus$appendSignature(builder, channel);
        }

        return builder.toString();
    }

    @Unique
    private static void bbsplusplus$appendSignature(StringBuilder builder, KeyframeChannel<?> channel)
    {
        builder.append(':').append(channel.toData().toString().hashCode());
    }
}
