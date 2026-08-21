package gbeic.bbsplusplus.mixin.shadercurves;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import net.irisshaders.iris.uniforms.custom.CustomUniforms;
import net.irisshaders.iris.uniforms.custom.cached.CachedUniform;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;

/**
 * 暴露 Iris 自定义 uniform 与渲染程序之间的位置映射。
 * <p>
 * setup compute 在首帧渲染状态建立前执行，不能调用 Iris 的全量 uniform 更新；
 * 此访问器用于只初始化并上传 BBS 光影曲线生成的 {@code bbs_} uniform。
 * </p>
 */
@Mixin(value = CustomUniforms.class, remap = false)
public interface CustomUniformsSetupAccessor
{
    /**
     * 获取各渲染程序对应的 uniform 位置，只应读取并用于精确上传 BBS 曲线参数。
     */
    @Accessor(value = "locationMap", remap = false)
    Map<Object, Object2IntMap<CachedUniform>> bbspp$getLocationMap();
}
