package gbeic.bbsplusplus.mixin.shadercurves;

import gbeic.bbsplusplus.client.compat.iris.ShaderCurveState;
import net.irisshaders.iris.pathways.CenterDepthSampler;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

/**
 * 让景深的对焦距离可以由曲线直接指定。
 * <p>
 * 移植自 BBSTools 4.1。Iris 原本采样屏幕正中央的实际深度当作对焦点，
 * 拍摄时镜头一动焦点就乱跑，也没法做「拉焦」这种运镜。
 * 接上曲线后就能像真实摄影机一样手动控制焦点。
 * </p>
 */
@Mixin(value = CenterDepthSampler.class, remap = false)
public class CenterDepthSamplerMixin
{
    @Shadow(remap = false)
    @Final
    private int altTexture;

    /**
     * 覆盖目标：{@code CenterDepthSampler#getCenterDepthTexture()}。
     * 覆盖原因：原方法固定返回 Iris 自己采样出来的深度纹理，没有插入点可以替换。
     * 修改行为：曲线里启用了焦点时返回按曲线值生成的深度纹理；没启用时回退到 Iris 原本的纹理，行为不变。
     *
     * @author Gbeic
     * @reason 让景深焦点可以被曲线剪辑动画化
     */
    @Overwrite
    public int getCenterDepthTexture()
    {
        return ShaderCurveState.getCenterDepthTexture().orElse(this.altTexture);
    }
}
