package gbeic.bbsplusplus.client.debug;

import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;

import java.util.Locale;

/**
 * 物品喷射调试统计与 HUD 面板。
 * <p>
 * 通过 {@code /bbsplusplus debug item_spray on|off} 动态开关。开启后屏幕左上角实时显示
 * 粒子数量、物理/渲染耗时、剔除数量、发射量与 IRL 阴影投影数，用于定位性能瓶颈。
 * 所有统计字段都是覆盖式快照，仅在开关开启时写入，不开启时只有一次布尔判断的开销。
 * </p>
 */
public final class ItemSprayDebug
{
    /** 调试面板开关 */
    private static boolean enabled;

    // ==================== 统计字段（客户端主线程单线程写入，无需同步） ====================
    /** 实时模式下全局活跃粒子数（每刻更新） */
    private static int activeGlobalItems;
    /** 世界确定性快照粒子数（每刻更新） */
    private static int deterministicWorldItems;
    /** 编辑器确定性预览采样数（每帧采样后更新） */
    private static int previewSampleCount;
    /** 最近一次发射批次的粒子数 */
    private static int emittedLastBatch;
    /** 最近一次物理更新耗时（纳秒） */
    private static long physicsNanos;
    /** 本帧裁剪前渲染候选数 */
    private static int rawCandidates;
    /** 本帧实际绘制的物品数 */
    private static int renderedCandidates;
    /** 最近一帧世界渲染耗时（纳秒） */
    private static long renderNanos;
    /** 本帧距离剔除累计数 */
    private static int culledByDistance;
    /** 本帧视锥剔除累计数 */
    private static int culledByFrustum;
    /** 最近一次 IRL 阴影投影物品数 */
    private static int irliteShadowItems;
    /** 每帧最大渲染数量预算 */
    private static int maxRenderedBudget;
    /** 最大渲染距离（米） */
    private static int maxRenderDistance;

    static
    {
        // 注册 HUD 渲染回调，开关开启时在左上角绘制数据面板
        HudRenderCallback.EVENT.register((context, tickDelta) ->
        {
            if (enabled)
            {
                renderHud(context);
            }
        });
    }

    private ItemSprayDebug()
    {
    }

    public static boolean isEnabled()
    {
        return enabled;
    }

    public static void setEnabled(boolean value)
    {
        enabled = value;
    }

    // ==================== 统计上报入口 ====================
    public static void recordGlobalItems(int active, int deterministic)
    {
        if (enabled)
        {
            activeGlobalItems = active;
            deterministicWorldItems = deterministic;
        }
    }

    public static void recordPreview(int count)
    {
        if (enabled)
        {
            previewSampleCount = count;
        }
    }

    public static void recordPhysicsNanos(long nanos)
    {
        if (enabled)
        {
            physicsNanos = nanos;
        }
    }

    public static void recordEmitted(int count)
    {
        if (enabled)
        {
            emittedLastBatch = count;
        }
    }

    public static void recordCandidates(int raw, int rendered)
    {
        if (enabled)
        {
            rawCandidates = raw;
            renderedCandidates = rendered;
        }
    }

    public static void recordRenderNanos(long nanos)
    {
        if (enabled)
        {
            renderNanos = nanos;
        }
    }

    public static void recordCulledByDistance()
    {
        if (enabled)
        {
            culledByDistance++;
        }
    }

    public static void recordCulledByFrustum()
    {
        if (enabled)
        {
            culledByFrustum++;
        }
    }

    /** 每帧渲染开始时清零本帧的剔除计数，保证 HUD 显示的是当前帧数量而非累计值 */
    public static void resetFrameCounters()
    {
        if (enabled)
        {
            culledByDistance = 0;
            culledByFrustum = 0;
        }
    }

    public static void recordIRLiteShadowItems(int count)
    {
        if (enabled)
        {
            irliteShadowItems = count;
        }
    }

    public static void recordRenderBudget(int maxRendered, double maxDistanceSq)
    {
        if (enabled)
        {
            maxRenderedBudget = maxRendered;
            maxRenderDistance = maxDistanceSq > 0D ? (int) Math.round(Math.sqrt(maxDistanceSq)) : 0;
        }
    }

    // ==================== HUD 绘制 ====================
    private static void renderHud(DrawContext context)
    {
        MinecraftClient client = MinecraftClient.getInstance();
        TextRenderer textRenderer = client.textRenderer;

        if (textRenderer == null)
        {
            return;
        }

        int x = 10;
        int y = 10;
        int lineHeight = 10;

        context.drawTextWithShadow(textRenderer, "物品喷射 Debug", x, y, 0xffffff00);
        y += lineHeight;
        context.drawTextWithShadow(textRenderer, String.format(Locale.ROOT, "实时: %d | 快照: %d | 预览: %d", activeGlobalItems, deterministicWorldItems, previewSampleCount), x, y, 0xffffffff);
        y += lineHeight;
        context.drawTextWithShadow(textRenderer, String.format(Locale.ROOT, "物理: %.2f ms | 发射: %d/批", millis(physicsNanos), emittedLastBatch), x, y, 0xffffffff);
        y += lineHeight;
        context.drawTextWithShadow(textRenderer, String.format(Locale.ROOT, "渲染: %d/%d | %.2f ms", renderedCandidates, maxRenderedBudget, millis(renderNanos)), x, y, renderedCandidates >= maxRenderedBudget && maxRenderedBudget > 0 ? 0xffff5555 : 0xffffffff);
        y += lineHeight;
        context.drawTextWithShadow(textRenderer, String.format(Locale.ROOT, "剔除: 距离 %d | 视锥 %d | 候选 %d", culledByDistance, culledByFrustum, rawCandidates), x, y, 0xffffffff);
        y += lineHeight;
        context.drawTextWithShadow(textRenderer, String.format(Locale.ROOT, "IRL阴影: %d | 距离上限: %d m", irliteShadowItems, maxRenderDistance), x, y, 0xffffffff);
    }

    private static double millis(long nanos)
    {
        return nanos / 1_000_000.0D;
    }
}
