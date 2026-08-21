package gbeic.bbsplusplus.mixin;

/**
 * 混入 EffectDefinition 类，捕获粒子系统的当前位置并在渲染时根据该位置动态设置渲染层级，以实现穿透方块（X-Ray）功能。
 * <p>
 * 由于 BBS 的粒子系统设计没有直接支持穿透方块的选项，我们通过在 EffectDefinition 的 draw 方法中捕获当前粒子系统的位置，
 * 并在调用 EffekseerManager.draw() 前设置一个线程局部变量，供 ParticleEmitterMixin 在重定向 draw 调用时使用，从而动态修改渲染层级。
 * </p>
 */

import org.spongepowered.asm.mixin.Mixin;
@Mixin(value = mod.chloeprime.aaaparticles.api.client.EffectDefinition.class, remap = false)
public class EffectDefinitionMixin {

    @org.spongepowered.asm.mixin.Shadow
    private static java.util.List<mod.chloeprime.aaaparticles.api.client.effekseer.ParticleEmitter> EMITTERS_BUFFER;

    @org.spongepowered.asm.mixin.injection.Inject(method = "draw", at = @org.spongepowered.asm.mixin.injection.At("TAIL"), remap = false)
    private static void bbspp_afterDraw(
            mod.chloeprime.aaaparticles.api.client.effekseer.ParticleEmitter.Type type,
            org.joml.Vector3f front,
            org.joml.Vector3f pos,
            int w, int h,
            float[] camera,
            float[] projection,
            float deltaFrames,
            float partialTicks,
            net.minecraft.client.gl.Framebuffer background,
            org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) {
        
        if (type == mod.chloeprime.aaaparticles.api.client.effekseer.ParticleEmitter.Type.WORLD) {
            mod.chloeprime.aaaparticles.api.client.effekseer.EffekseerManager xrayManager = gbeic.bbsplusplus.utils.XRayManager.get();
            if (xrayManager != null) {
                // 同步所有必需的渲染状态
                xrayManager.setViewport(w, h);
                xrayManager.setCameraMatrix(camera);
                xrayManager.setProjectionMatrix(projection);
                xrayManager.setCameraParameter(front.x, front.y, front.z, pos.x, pos.y, pos.z);
                
                xrayManager.update(deltaFrames);
                
                // 深度测试覆盖：由于 Effekseer C++ 底层会强制重设 GL_DEPTH_TEST 和 GL_DEPTH_FUNC，
                // 我们使用 glDepthRange(0.0, 0.0) 将深度强制压缩到 0.0（最近处），使得 GL_LEQUAL 测试必然通过！
                // 同时，因为 C++ 底层可能会强行开启 glDepthMask(true)，导致这个 0.0 被写死在深度缓冲中，
                // 进而导致随后的第一人称手臂在做深度测试时被这个 0.0 错误地遮挡（即发生粒子穿透右手的 Bug）。
                // 为了彻底解决这个问题，我们在粒子渲染前先备份当前的深度缓冲，然后在渲染后原样恢复！
                mod.chloeprime.aaaparticles.client.render.RenderUtil.copyCurrentDepthTo(mod.chloeprime.aaaparticles.client.internal.RenderStateCapture.CAPTURED_WORLD_DEPTH_BUFFER);
                
                com.mojang.blaze3d.systems.RenderSystem.disableDepthTest();
                org.lwjgl.opengl.GL11.glDepthRange(0.0, 0.0);
                org.lwjgl.opengl.GL11.glDepthMask(false);
                
                xrayManager.drawBack();
                xrayManager.drawFront();
                
                // 强行恢复深度缓冲（消除 C++ 底层的任何污染）
                mod.chloeprime.aaaparticles.client.render.RenderUtil.pasteToCurrentDepthFrom(mod.chloeprime.aaaparticles.client.internal.RenderStateCapture.CAPTURED_WORLD_DEPTH_BUFFER);
                
                // 【BBS++ 核心修复】
                // Effekseer 的 C++ 底层不仅更改了深度测试，还会直接调用 OpenGL 原生方法绑定纹理（glActiveTexture 和 glBindTexture）。
                // 这种绕过 Minecraft RenderSystem 的行为，会导致 RenderSystem 的内部缓存（如当前激活的纹理单元、绑定的贴图 ID）与 OpenGL 实际状态严重脱节！
                // 因为我们在 RenderContextMixin 中把粒子渲染强行提前到了“画手”和“画BBS伪装”之前，如果在这里不把状态拨乱反正，
                // 接下来画手的时候，RenderSystem 以为自己早就绑定了玩家皮肤贴图而跳过绑定，最终导致手臂和 BBS 伪装使用错误的（或空的）贴图！
                for (int i = 0; i < 8; i++) {
                    com.mojang.blaze3d.systems.RenderSystem.activeTexture(org.lwjgl.opengl.GL13.GL_TEXTURE0 + i);
                    com.mojang.blaze3d.systems.RenderSystem.bindTexture(0);
                }
                com.mojang.blaze3d.systems.RenderSystem.activeTexture(org.lwjgl.opengl.GL13.GL_TEXTURE0);
                
                // 恢复原生状态
                org.lwjgl.opengl.GL11.glDepthMask(true);
                org.lwjgl.opengl.GL11.glDepthRange(0.0, 1.0);
                org.lwjgl.opengl.GL11.glDepthFunc(org.lwjgl.opengl.GL11.GL_LEQUAL);
                com.mojang.blaze3d.systems.RenderSystem.depthFunc(org.lwjgl.opengl.GL11.GL_LEQUAL);
                com.mojang.blaze3d.systems.RenderSystem.enableDepthTest();
            }
        }
    }
}
