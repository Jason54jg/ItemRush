package com.jason54.itemrush.network;

import com.jason54.itemrush.Constants;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server → client: round finished (win, timeout, or manual stop).
 *
 * @param reason 0 = winner, 1 = time up, 2 = stopped
 */
public record RoundEndPayload(
        int reason,
        String playerName,
        String rewardDisplay
) implements CustomPacketPayload {

    public static final int REASON_WINNER = 0;
    public static final int REASON_TIME_UP = 1;
    public static final int REASON_STOPPED = 2;

    public static final Type<RoundEndPayload> TYPE = new Type<>(Constants.id("round_end"));

    public static final StreamCodec<RegistryFriendlyByteBuf, RoundEndPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, RoundEndPayload::reason,
            ByteBufCodecs.STRING_UTF8, RoundEndPayload::playerName,
            ByteBufCodecs.STRING_UTF8, RoundEndPayload::rewardDisplay,
            RoundEndPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
