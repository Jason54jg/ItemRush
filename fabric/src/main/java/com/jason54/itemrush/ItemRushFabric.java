package com.jason54.itemrush;

import com.jason54.itemrush.command.ItemRushCommands;
import com.jason54.itemrush.game.GameManager;
import com.jason54.itemrush.network.RoundEndPayload;
import com.jason54.itemrush.network.RoundStartPayload;
import com.jason54.itemrush.network.TimerSyncPayload;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

import java.lang.reflect.Method;

/**
 * Fabric common (server+client) entrypoint.
 */
public class ItemRushFabric implements ModInitializer {

    @Override
    public void onInitialize() {
        ItemRush.init();
        registerPayloads();

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                ItemRushCommands.register(dispatcher));

        ServerTickEvents.END_SERVER_TICK.register(GameManager::serverTick);
    }

    private static void registerPayloads() {
        try {
            Class<?> registryClass = Class.forName("net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry");
            Object registry = null;
            for (String methodName : new String[]{"clientboundPlay", "playS2C"}) {
                try {
                    Method method = registryClass.getMethod(methodName);
                    registry = method.invoke(null);
                    break;
                } catch (NoSuchMethodException ignored) {
                    // try next
                }
            }
            if (registry == null) {
                throw new IllegalStateException("PayloadTypeRegistry play registry not found");
            }
            Method register = null;
            for (Method method : registry.getClass().getMethods()) {
                if ("register".equals(method.getName()) && method.getParameterCount() == 2) {
                    register = method;
                    break;
                }
            }
            if (register == null) {
                throw new IllegalStateException("PayloadTypeRegistry#register not found");
            }
            register.invoke(registry, RoundStartPayload.TYPE, RoundStartPayload.STREAM_CODEC);
            register.invoke(registry, RoundEndPayload.TYPE, RoundEndPayload.STREAM_CODEC);
            register.invoke(registry, TimerSyncPayload.TYPE, TimerSyncPayload.STREAM_CODEC);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("Failed to register ItemRush payloads", e);
        }
    }
}
