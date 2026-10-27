package com.jason54.itemrush.client;

import com.jason54.itemrush.Constants;
import com.jason54.itemrush.network.RoundEndPayload;
import com.jason54.itemrush.network.RoundStartPayload;
import com.jason54.itemrush.network.TimerSyncPayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

/**
 * Fabric client entrypoint — networking, HUD, tick animation.
 */
public class ItemRushFabricClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        ClientPlayNetworking.registerGlobalReceiver(RoundStartPayload.TYPE,
                (payload, context) -> context.client().execute(() -> ClientPacketHandler.handleRoundStart(payload)));
        ClientPlayNetworking.registerGlobalReceiver(RoundEndPayload.TYPE,
                (payload, context) -> context.client().execute(() -> ClientPacketHandler.handleRoundEnd(payload)));
        ClientPlayNetworking.registerGlobalReceiver(TimerSyncPayload.TYPE,
                (payload, context) -> context.client().execute(() -> ClientPacketHandler.handleTimerSync(payload)));

        ClientTickEvents.END_CLIENT_TICK.register(client -> ClientPacketHandler.onClientTick());

        FabricHudBootstrap.register();
    }
}
