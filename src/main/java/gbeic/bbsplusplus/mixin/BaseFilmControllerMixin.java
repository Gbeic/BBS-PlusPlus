package gbeic.bbsplusplus.mixin;

import gbeic.bbsplusplus.client.WorldFilmShaderCurveState;
import gbeic.bbsplusplus.client.renderer.VideoTimelineState;
import gbeic.bbsplusplus.keyframes.EquipmentTransformRuntime;
import io.netty.util.collection.IntObjectMap;
import mchorse.bbs_mod.film.BaseFilmController;
import mchorse.bbs_mod.film.Film;
import mchorse.bbs_mod.film.FirstPersonFilmController;
import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.film.WorldFilmController;
import mchorse.bbs_mod.forms.entities.IEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 *
 * 1. 修复第一人称回放时视角完全不晃动的问题（因为服务端传送导致玩家水平速度为0，从而无法触发原版的行走晃动逻辑）。
 *    通过反推玩家的真实水平速度并覆写回去，恢复了第一人称回放的行走晃动效果。
 * 2. 仅在 BBSAddonsSettings.firstPersonBobbing 开启时启用此修复，以兼容可能不喜欢晃动效果的用户。
 */

@Mixin(BaseFilmController.class)
public abstract class BaseFilmControllerMixin {

    @Shadow(remap = false)
    public Film film;

    @Shadow(remap = false)
    public IntObjectMap<IEntity> entities;

    @Shadow(remap = false)
    public abstract int getTick();

    @Shadow(remap = false)
    protected abstract float getTransition(IEntity entity, float transition);

    /**
     * 注入目标：{@code BaseFilmController#renderEntity(WorldRenderContext, Replay, IEntity)} 开始处。
     * 注入原因：实体 age 在编辑器向后跳转时仍只会递增，不能代表影片播放头时间。
     * 修改行为：在形态渲染期间暴露当前回放的真实局部 tick，让视频能够按播放头正向或反向寻帧。
     */
    @Inject(
        method = "renderEntity(Lnet/fabricmc/fabric/api/client/rendering/v1/WorldRenderContext;Lmchorse/bbs_mod/film/replays/Replay;Lmchorse/bbs_mod/forms/entities/IEntity;)V",
        at = @At("HEAD"),
        remap = false
    )
    private void bbspp$beginVideoTimelineRender(WorldRenderContext context, Replay replay, IEntity entity, CallbackInfo ci)
    {
        int tick = replay == null ? this.getTick() : replay.getTick(this.getTick());

        VideoTimelineState.beginFilmRender(tick);
    }

    /**
     * 注入目标：{@code BaseFilmController#renderEntity(WorldRenderContext, Replay, IEntity)} 返回处。
     * 注入原因：真实影片 tick 只应影响当前演员的形态渲染，不能泄漏到普通实体或表单预览。
     * 修改行为：当前演员渲染结束后立即清除影片时间上下文。
     */
    @Inject(
        method = "renderEntity(Lnet/fabricmc/fabric/api/client/rendering/v1/WorldRenderContext;Lmchorse/bbs_mod/film/replays/Replay;Lmchorse/bbs_mod/forms/entities/IEntity;)V",
        at = @At("RETURN"),
        remap = false
    )
    private void bbspp$endVideoTimelineRender(WorldRenderContext context, Replay replay, IEntity entity, CallbackInfo ci)
    {
        VideoTimelineState.endFilmRender();
    }

    /**
     * 注入目标：{@code BaseFilmController#startRenderFrame(float)} 结束处。
     * 注入原因：右 Ctrl 世界播放影片时，光影曲线不会经过相机控制器上下文，导致 BBSRendering 读不到曲线数据。
     * 修改行为：在世界影片控制器每帧完成演员属性准备后，额外采样当前影片相机剪辑里的光影曲线。
     */
    @Inject(method = "startRenderFrame", at = @At("TAIL"), remap = false)
    private void bbspp$sampleWorldFilmShaderCurves(float transition, CallbackInfo ci)
    {
        if ((Object) this instanceof WorldFilmController controller)
        {
            WorldFilmShaderCurveState.sample(controller, transition);
        }

        this.bbspp$sampleEquipmentTransforms(transition);
    }

    private void bbspp$sampleEquipmentTransforms(float transition)
    {
        if (this.film == null || this.entities == null)
        {
            return;
        }

        for (IntObjectMap.PrimitiveEntry<IEntity> entry : this.entities.entries())
        {
            int i = entry.key();
            IEntity entity = entry.value();

            if (i < 0 || i >= this.film.replays.getList().size())
            {
                continue;
            }

            Replay replay = this.film.replays.getList().get(i);

            if (replay == null || !replay.enabled.get())
            {
                continue;
            }

            float delta = this.getTransition(entity, transition);
            float tick = replay.getTick(this.getTick()) + delta;

            EquipmentTransformRuntime.apply(replay.keyframes, tick, entity);
        }
    }

    @Inject(method = "updateEndWorld", at = @At("HEAD"), remap = false)
    public void onUpdateEndWorldHead(CallbackInfo ci) {
        if ((Object) this instanceof FirstPersonFilmController) {
            gbeic.bbsplusplus.BBSPlusPlusState.activeFilmId = this.film.getId();
            gbeic.bbsplusplus.BBSPlusPlusState.prevFovMultiplier = gbeic.bbsplusplus.BBSPlusPlusState.smoothedFovMultiplier;
            
            // 默认衰减
            gbeic.bbsplusplus.BBSPlusPlusState.targetFovMultiplier = 1.0F;
        }
    }

    @Inject(method = "updateEndWorld", at = @At("TAIL"), remap = false)
    public void onUpdateEndWorld(CallbackInfo ci) {
        if (!((Object) this instanceof FirstPersonFilmController wfc)) {
            return;
        }

        ClientPlayerEntity player = MinecraftClient.getInstance().player;
        if (player == null) return;

        Replay fpReplay = null;
        for (Replay replay : this.film.replays.getList()) {
            if (replay.fp.get()) {
                fpReplay = replay;
                break;
            }
        }

        if (fpReplay != null && gbeic.bbsplusplus.BBSAddonsSettings.firstPersonBobbing.get()) {
            double x1 = fpReplay.keyframes.x.interpolate(wfc.tick - 1);
            double z1 = fpReplay.keyframes.z.interpolate(wfc.tick - 1);
            double x2 = fpReplay.keyframes.x.interpolate(wfc.tick);
            double z2 = fpReplay.keyframes.z.interpolate(wfc.tick);

            double d = x2 - x1;
            double e = z2 - z1;

            float f = (float)Math.sqrt(d * d + e * e);
            if (f > 1.0F) {
                f = 1.0F;
            }

            if (wfc.paused) {
                f = 0.0F;
            }

            // ================= 摇晃自定义设置 =================
            // 控制摇晃幅度（步幅大小）
            float bobbingAmplitudeMultiplier = 0.95F;
            
            // 控制摇晃速度（频率），用户觉得偏快，现在真正生效！
            float bobbingSpeedMultiplier = 0.82F;
            // ==================================================

            // 1. 撤销原版基于重力/错误坐标算出的错误累加
            double vanillaD = player.getX() - player.prevX;
            double vanillaE = player.getZ() - player.prevZ;
            double vanillaG = player.isOnGround() ? player.getY() - player.prevY : 0.0D;
            float vanillaF = (float)Math.sqrt(vanillaD * vanillaD + vanillaG * vanillaG + vanillaE * vanillaE);
            
            player.horizontalSpeed -= (vanillaF * 0.6F); // 撤销原版的相位累加
            player.distanceTraveled -= (vanillaF * 0.6F); // 撤销统计距离累加
            
            // 2. 只有在地面上时，才应用行走摇晃效果
            if (player.isOnGround()) {
                // 覆盖 BBS 原版 updateEndWorld 中的强制 strideDistance 设置，将其恢复为 Vanilla 的正常衰减计算值
                player.strideDistance = player.prevStrideDistance * 0.6F;
                
                // 修正摇晃幅度 (strideDistance)
                // 补上这一帧真实的移动反馈：
                player.strideDistance += f * 0.2F * bobbingAmplitudeMultiplier;
                
                // 修正摇晃频率 (horizontalSpeed)
                player.horizontalSpeed += (f * 0.6F) * bobbingSpeedMultiplier; 
                
                // 修正成就统计距离
                player.distanceTraveled += (f * 0.6F);
                
                // 动态 FOV 放大逻辑：
                // 走路的 f 约 0.21，疾跑的 f 约 0.28
                float sprintThreshold = 0.22F;
                if (f > sprintThreshold) {
                    float speedOffset = Math.min(1.0F, (f - sprintThreshold) / 0.06F);
                    gbeic.bbsplusplus.BBSPlusPlusState.targetFovMultiplier = 1.0F + speedOffset * 0.15F; // 疾跑最多放大 1.15 倍
                }
            }
            
            // 每帧平滑逼近目标 FOV 倍率（模拟原版的 MathHelper.lerp 平滑阻尼感）
            gbeic.bbsplusplus.BBSPlusPlusState.smoothedFovMultiplier += (gbeic.bbsplusplus.BBSPlusPlusState.targetFovMultiplier - gbeic.bbsplusplus.BBSPlusPlusState.smoothedFovMultiplier) * 0.5F;
        }
    }
}
