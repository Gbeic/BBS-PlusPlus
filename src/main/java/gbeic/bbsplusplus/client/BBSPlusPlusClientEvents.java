package gbeic.bbsplusplus.client;

import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.GameShuttingDownEvent;

/**
 * BBS++ 客户端事件桥。
 * <p>
 * Fabric 的 {@code ClientModInitializer} 在 NeoForge 上没有直接对应物，这里用
 * {@link FMLClientSetupEvent} 承担入口职责，并把原先的三类 Fabric 回调转接到 NeoForge 事件：
 * </p>
 * <ul>
 *   <li>{@code ClientTickEvents.END_CLIENT_TICK} → {@link ClientTickEvent.Post}</li>
 *   <li>{@code ClientLifecycleEvents.CLIENT_STOPPING} → {@link GameShuttingDownEvent}</li>
 *   <li>{@code ClientLifecycleEvents.CLIENT_STARTED} → 无等价事件，改为在首个客户端 tick 前触发一次</li>
 * </ul>
 */
public final class BBSPlusPlusClientEvents
{
    private static boolean initialized;
    private static boolean startedOnce;

    private BBSPlusPlusClientEvents()
    {}

    public static void register(IEventBus modBus)
    {
        modBus.addListener(BBSPlusPlusClientEvents::onClientSetup);
    }

    private static void onClientSetup(FMLClientSetupEvent event)
    {
        if (initialized)
        {
            return;
        }

        initialized = true;

        event.enqueueWork(() ->
        {
            BBSPlusPlusModClient.init();

            NeoForge.EVENT_BUS.addListener(BBSPlusPlusClientEvents::onClientTickPre);
            NeoForge.EVENT_BUS.addListener(BBSPlusPlusClientEvents::onClientTickPost);
            NeoForge.EVENT_BUS.addListener(BBSPlusPlusClientEvents::onGameShuttingDown);
        });
    }

    /**
     * 首个客户端 tick 之前触发一次，等价于 Fabric 的 CLIENT_STARTED。
     * <p>
     * 之所以不能放在 {@link FMLClientSetupEvent} 里，是因为那时 BBS 本体的表单注册表等
     * 结构尚未就绪；等到第一个 tick 时整个客户端已经完全启动。
     * </p>
     */
    private static void onClientTickPre(ClientTickEvent.Pre event)
    {
        if (startedOnce)
        {
            return;
        }

        startedOnce = true;

        BBSPlusPlusModClient.onClientStarted(Minecraft.getInstance());
    }

    private static void onClientTickPost(ClientTickEvent.Post event)
    {
        BBSPlusPlusModClient.onClientTick(Minecraft.getInstance());
    }

    private static void onGameShuttingDown(GameShuttingDownEvent event)
    {
        if (FMLEnvironment.dist != Dist.CLIENT)
        {
            return;
        }

        BBSPlusPlusModClient.onClientStopping(Minecraft.getInstance());
    }
}
