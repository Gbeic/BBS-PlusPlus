package gbeic.bbsplusplus.mixin;

import gbeic.bbsplusplus.api.ModelVAODataAccess;
import mchorse.bbs_mod.cubic.render.vao.ModelVAO;
import mchorse.bbs_mod.cubic.render.vao.ModelVAOData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 保留模型 VAO 最近一次上传的原始网格数据，供动态 UV 的 CPU 兼容路径读取。
 */
@Mixin(value = ModelVAO.class, remap = false)
public class ModelVAOMixin implements ModelVAODataAccess
{
    @Unique private ModelVAOData bbspp$modelVaoData;

    /**
     * 注入目标：{@code ModelVAO#upload} 开始处。
     * 注入原因：原类上传后只保存 OpenGL 句柄，无法在不读取 GPU 的情况下重新提交动态 UV。
     * 修改行为：保存本次上传使用的不可变记录及其数组引用，供挤出形态按需读取。
     */
    @Inject(method = "upload", at = @At("HEAD"))
    private void bbspp$captureModelVaoData(ModelVAOData data, CallbackInfo ci)
    {
        this.bbspp$modelVaoData = data;
    }

    @Override
    public ModelVAOData bbspp$getModelVaoData()
    {
        return this.bbspp$modelVaoData;
    }
}
