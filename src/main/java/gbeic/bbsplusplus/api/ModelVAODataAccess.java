package gbeic.bbsplusplus.api;

import mchorse.bbs_mod.cubic.render.vao.ModelVAOData;

/**
 * 暴露 {@code ModelVAO} 上传时使用的原始网格数组。
 * <p>
 * 挤出形态的 UV 已烘焙进 GPU 缓冲；保留原始数组后，启用动态 UV 变换时可以走 CPU 顶点路径，
 * 无需每帧重建 VAO，也不会修改同一纹理下其它挤出形态共享的缓存。
 * </p>
 */
public interface ModelVAODataAccess
{
    ModelVAOData bbspp$getModelVaoData();
}
