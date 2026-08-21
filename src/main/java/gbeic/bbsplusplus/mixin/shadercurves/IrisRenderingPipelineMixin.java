package gbeic.bbsplusplus.mixin.shadercurves;

import gbeic.bbsplusplus.client.compat.iris.ShaderCurveState;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import kroppeb.stareval.function.FunctionReturn;
import kroppeb.stareval.function.Type;
import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.utils.iris.ShaderCurves;
import net.irisshaders.iris.gl.program.ComputeProgram;
import net.irisshaders.iris.pipeline.IrisRenderingPipeline;
import net.irisshaders.iris.uniforms.custom.CustomUniforms;
import net.irisshaders.iris.uniforms.custom.cached.CachedUniform;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 修复 Iris 渲染管线与 BBS 光影曲线之间的兼容问题。
 * <p>
 * 一方面让渲染管线对外汇报的日月偏角跟随曲线；另一方面在光影包的一次性 setup compute
 * 执行前主动刷新并上传 BBS 生成的参数 uniform，避免大气 LUT 等初始化资源读到全零参数。
 * </p>
 */
@Mixin(value = IrisRenderingPipeline.class, remap = false)
public class IrisRenderingPipelineMixin
{
    @Shadow(remap = false)
    @Final
    private float sunPathRotation;

    @Shadow(remap = false)
    @Final
    private CustomUniforms customUniforms;

    /** 记录已经为 setup compute 初始化过的 BBS 曲线 uniform，避免同一曲线值被重复消费 */
    @Unique
    private final Set<CachedUniform> bbspp$initializedSetupUniforms = new HashSet<>();

    /** Revoxelation 中负责生成大气透射率与多重散射 LUT 的 setup 程序，按原执行顺序保存 */
    @Unique
    private final List<ComputeProgram> bbspp$atmosphereLutPrograms = new ArrayList<>();

    /** 记录上次生成大气 LUT 时使用的 BBS uniform 值，用于避免无变化时重复计算 */
    @Unique
    private final Map<CachedUniform, Integer> bbspp$atmosphereLutValues = new HashMap<>();

    /** 复用读取 uniform 缓存值的容器，避免每帧检查大气参数时产生临时对象 */
    @Unique
    private final FunctionReturn bbspp$uniformValue = new FunctionReturn();

    /**
     * 注入目标：{@code IrisRenderingPipeline} 构造阶段对 setup compute 的一次性派发。
     * 注入原因：Iris 会在首个渲染帧之前执行这些程序，但原路径既没有刷新也没有上传自定义 uniform；
     * BBS 把光影滑条宏改写成 uniform 后，它们此时仍是 OpenGL 默认值 0。Revoxelation 会用这些全零参数
     * 生成大气透射 LUT，随后即使首帧把 uniform 恢复正常也不会重新生成，表现为太阳光永久消失。
     * 修改行为：只刷新并上传当前 setup compute 实际使用的 {@code bbs_} uniform，
     * 不触碰此时尚不具备渲染矩阵等输入条件的 Iris 内置 uniform。
     */
    @Redirect(
        method = "<init>",
        at = @At(
            value = "INVOKE",
            target = "Lnet/irisshaders/iris/gl/program/ComputeProgram;dispatch(FF)V"
        ),
        require = 0,
        remap = false
    )
    private void bbspp$pushSetupComputeUniforms(ComputeProgram program, float width, float height)
    {
        if (BBSSettings.shaderCurvesEnabled.get())
        {
            this.bbspp$pushCurveUniforms(program);
            this.bbspp$trackAtmosphereLutProgram(program);
        }

        program.dispatch(width, height);
    }

    /**
     * 注入目标：{@code IrisRenderingPipeline#beginLevelRendering()} 中每帧更新自定义 uniform 之后。
     * 注入原因：Revoxelation 的大气 LUT 默认只在光影加载和窗口尺寸变化时生成，曲线后续改变大气参数时
     * LUT 不会同步，导致逐帧参数与查找表内容不一致。
     * 修改行为：仅当相关 {@code bbs_} uniform 的缓存值发生变化时，按原顺序重新执行大气透射率与
     * 多重散射两个 setup 程序；不重跑图像清理、体素缓存等其他 setup 程序。
     */
    @Inject(
        method = "beginLevelRendering",
        at = @At(
            value = "INVOKE",
            target = "Lnet/irisshaders/iris/uniforms/custom/CustomUniforms;update()V",
            shift = At.Shift.AFTER
        ),
        remap = false
    )
    private void bbspp$refreshAtmosphereLutsWhenCurveChanges(CallbackInfo ci)
    {
        if (!BBSSettings.shaderCurvesEnabled.get()
            || this.bbspp$atmosphereLutPrograms.isEmpty()
            || !this.bbspp$haveAtmosphereLutValuesChanged())
        {
            return;
        }

        for (ComputeProgram program : this.bbspp$atmosphereLutPrograms)
        {
            program.use();
            this.bbspp$pushCurveUniforms(program);
            program.dispatch(1F, 1F);
        }

        ComputeProgram.unbind();
    }

    /** 仅更新并上传当前程序实际声明的 BBS 曲线 uniform */
    @Unique
    private void bbspp$pushCurveUniforms(ComputeProgram program)
    {
        Map<Object, Object2IntMap<CachedUniform>> locationMap =
            ((CustomUniformsSetupAccessor) this.customUniforms).bbspp$getLocationMap();
        Object2IntMap<CachedUniform> locations = locationMap.get(program);

        if (locations == null)
        {
            return;
        }

        for (Object2IntMap.Entry<CachedUniform> entry : locations.object2IntEntrySet())
        {
            CachedUniform uniform = entry.getKey();

            if (!uniform.getName().startsWith(ShaderCurves.UNIFORM_IDENTIFIER))
            {
                continue;
            }

            if (this.bbspp$initializedSetupUniforms.add(uniform))
            {
                uniform.update();
            }

            uniform.pushIfChanged(entry.getIntValue());
        }
    }

    /** 识别并记录 Revoxelation 的大气 LUT 程序及其初始 uniform 快照 */
    @Unique
    private void bbspp$trackAtmosphereLutProgram(ComputeProgram program)
    {
        Object2IntMap<CachedUniform> locations =
            ((CustomUniformsSetupAccessor) this.customUniforms).bbspp$getLocationMap().get(program);

        if (locations == null || !bbspp$isAtmosphereLutProgram(locations))
        {
            return;
        }

        this.bbspp$atmosphereLutPrograms.add(program);

        for (CachedUniform uniform : locations.keySet())
        {
            if (uniform.getName().startsWith(ShaderCurves.UNIFORM_IDENTIFIER))
            {
                this.bbspp$atmosphereLutValues.put(uniform, this.bbspp$getUniformValueBits(uniform));
            }
        }
    }

    /** 检查所有会影响大气 LUT 的 BBS uniform 是否发生变化，并同步保存新的快照 */
    @Unique
    private boolean bbspp$haveAtmosphereLutValuesChanged()
    {
        boolean changed = false;

        for (Map.Entry<CachedUniform, Integer> entry : this.bbspp$atmosphereLutValues.entrySet())
        {
            int value = this.bbspp$getUniformValueBits(entry.getKey());

            if (entry.getValue() != value)
            {
                entry.setValue(value);
                changed = true;
            }
        }

        return changed;
    }

    /** 通过两个采样数 uniform 精确识别 Revoxelation 的大气透射率与多重散射 setup 程序 */
    @Unique
    private static boolean bbspp$isAtmosphereLutProgram(Object2IntMap<CachedUniform> locations)
    {
        for (CachedUniform uniform : locations.keySet())
        {
            String name = uniform.getName();

            if (name.equals(ShaderCurves.UNIFORM_IDENTIFIER + "ATMOSPHERE_TLUT_SAMPLES")
                || name.equals(ShaderCurves.UNIFORM_IDENTIFIER + "ATMOSPHERE_MSLUT_SAMPLES"))
            {
                return true;
            }
        }

        return false;
    }

    /** 把 BBS 生成的整型或浮点 uniform 当前缓存值转换为可直接比较的位表示 */
    @Unique
    private int bbspp$getUniformValueBits(CachedUniform uniform)
    {
        uniform.writeTo(this.bbspp$uniformValue);

        return uniform.getType().equals(Type.Int)
            ? this.bbspp$uniformValue.intReturn
            : Float.floatToIntBits(this.bbspp$uniformValue.floatReturn);
    }

    /**
     * 覆盖目标：{@code IrisRenderingPipeline#getSunPathRotation()}。
     * 覆盖原因：原方法只是直接返回构造时从光影包配置读到的固定值，没有插入点可以改。
     * 修改行为：光影曲线总开关打开时返回曲线当前值，否则返回原值。
     *
     * @author Gbeic
     * @reason 让日月偏角可以被曲线剪辑动画化
     */
    @Overwrite
    public float getSunPathRotation()
    {
        if (!BBSSettings.shaderCurvesEnabled.get())
        {
            return this.sunPathRotation;
        }

        return ShaderCurveState.getSunPathRotation(this.sunPathRotation);
    }
}
