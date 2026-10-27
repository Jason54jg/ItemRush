package com.jason54.itemrush.network;

import com.jason54.itemrush.Constants;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server → client: remaining seconds during PLAYING phase.
 */
public record TimerSyncPayload(int secondsLeft) implements CustomPacketPayload {

    public static final Type<TimerSyncPayload> TYPE = new Type<>(Constants.id("timer_sync"));

    public static final StreamCodec<RegistryFriendlyByteBuf, TimerSyncPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, TimerSyncPayload::secondsLeft,
            TimerSyncPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
