package gbeic.bbsplusplus;

/**
 * BBSPlusPlusState
 *
 * 这是一个简单的状态类，用于在 BBS++ 模组中存储一些全局的状态信息。当前包含以下字段：
 * - activeFilmId：当前正在编辑或回放的影片 ID，可以用于在不同模块之间共享当前影片的信息。
 * - targetFovMultiplier：目标 FOV 乘数，用于第一人称回放时调整视野范围。
 * - smoothedFovMultiplier：平滑过渡的 FOV 乘数，用于实现 FOV 的平滑变化效果。
 * - prevFovMultiplier：上一个 FOV 乘数，用于计算 FOV 变化的差值。
 *
 * 通过这个类，BBS++ 的不同模块可以方便地访问和修改这些全局状态，从而实现更丰富的功能和更好的用户体验。
 */

public class BBSPlusPlusState {
    public static String activeFilmId = null;
    public static float targetFovMultiplier = 1.0F;
    public static float smoothedFovMultiplier = 1.0F;
    public static float prevFovMultiplier = 1.0F;
}
