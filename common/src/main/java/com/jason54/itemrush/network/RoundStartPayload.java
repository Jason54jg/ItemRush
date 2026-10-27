package com.jason54.itemrush.network;

import com.jason54.itemrush.Constants;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Server → client: start a round (roulette + timer).
 */
public record RoundStartPayload(
        Identifier targetItem,
        int durationSeconds,
        int rouletteTicks,
        float rouletteSpeed
) implements CustomPacketPayload {

    public static final Type<RoundStartPayload> TYPE = new Type<>(Constants.id("round_start"));

    public static final StreamCodec<RegistryFriendlyByteBuf, RoundStartPayload> STREAM_CODEC = StreamCodec.composite(
            Identifier.STREAM_CODEC, RoundStartPayload::targetItem,
            ByteBufCodecs.VAR_INT, RoundStartPayload::durationSeconds,
            ByteBufCodecs.VAR_INT, RoundStartPayload::rouletteTicks,
            ByteBufCodecs.FLOAT, RoundStartPayload::rouletteSpeed,
            RoundStartPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
