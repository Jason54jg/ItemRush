package com.jason54.itemrush;

import com.jason54.itemrush.client.ClientPacketHandler;
import com.jason54.itemrush.client.HudBridge;
import com.jason54.itemrush.command.ItemRushCommands;
import com.jason54.itemrush.game.GameManager;
import com.jason54.itemrush.network.RoundEndPayload;
import com.jason54.itemrush.network.RoundStartPayload;
import com.jason54.itemrush.network.TimerSyncPayload;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.network.event.RegisterClientPayloadHandlersEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

@Mod(Constants.MOD_ID)
public class ItemRushNeoForge {

    public ItemRushNeoForge(IEventBus modBus) {
        ItemRush.init();
        modBus.addListener(ItemRushNeoForge::registerPayloads);
        NeoForge.EVENT_BUS.addListener(ItemRushNeoForge::registerCommands);
        NeoForge.EVENT_BUS.addListener(ItemRushNeoForge::onServerTick);

        if (FMLEnvironment.getDist() == Dist.CLIENT) {
            modBus.addListener(ItemRushNeoForge::registerClientPayloads);
            modBus.addListener(ItemRushNeoForge::registerGuiLayers);
            NeoForge.EVENT_BUS.addListener(ItemRushNeoForge::onClientTick);
        }
    }

    private static void registerPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");
        registrar.playToClient(RoundStartPayload.TYPE, RoundStartPayload.STREAM_CODEC);
        registrar.playToClient(RoundEndPayload.TYPE, RoundEndPayload.STREAM_CODEC);
        registrar.playToClient(TimerSyncPayload.TYPE, TimerSyncPayload.STREAM_CODEC);
    }

    private static void registerClientPayloads(RegisterClientPayloadHandlersEvent event) {
        event.register(RoundStartPayload.TYPE, (payload, context) -> ClientPacketHandler.handleRoundStart(payload));
        event.register(RoundEndPayload.TYPE, (payload, context) -> ClientPacketHandler.handleRoundEnd(payload));
        event.register(TimerSyncPayload.TYPE, (payload, context) -> ClientPacketHandler.handleTimerSync(payload));
    }

    private static void registerGuiLayers(RegisterGuiLayersEvent event) {
        event.registerAboveAll(Constants.id("hud"), (graphics, deltaTracker) -> {
            float partial = 0f;
            try {
                partial = deltaTracker.getGameTimeDeltaPartialTick(false);
            } catch (Throwable ignored) {
                // older DeltaTracker APIs
            }
            HudBridge.render(graphics, partial);
        });
    }

    private static void registerCommands(RegisterCommandsEvent event) {
        ItemRushCommands.register(event.getDispatcher());
    }

    private static void onServerTick(ServerTickEvent.Post event) {
        GameManager.serverTick(event.getServer());
    }

    private static void onClientTick(ClientTickEvent.Post event) {
        ClientPacketHandler.onClientTick();
    }
}
