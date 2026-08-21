package gbeic.bbsplusplus.utils;

/**
 * 这是一个简单的接口，用于标记粒子发射器是否启用穿透方块（X-Ray）功能。
 * <p>
 * 由于 BBS 的粒子系统设计没有直接支持穿透方块的选项，我们通过此接口在运行时动态修改渲染层级来实现该功能。
 * </p>
 */

public interface IIgnoreDepth {
    boolean bbspp$getIgnoreDepth();
    void bbspp$setIgnoreDepth(boolean ignoreDepth);
    boolean bbspp$getOriginalVisibility();
}
