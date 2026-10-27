package com.jason54.itemrush.platform.services;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.nio.file.Path;

/**
 * Loader-specific helpers (Fabric / NeoForge).
 */
public interface IPlatformHelper {

    String getPlatformName();

    boolean isModLoaded(String modId);

    boolean isDevelopmentEnvironment();

    /** Config folder for this game instance (e.g. .minecraft/config). */
    Path getConfigDirectory();

    void sendToPlayer(ServerPlayer player, CustomPacketPayload payload);

    void sendToAllPlayers(MinecraftServer server, CustomPacketPayload payload);
}
