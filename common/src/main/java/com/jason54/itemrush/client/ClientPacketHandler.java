package com.jason54.itemrush.client;

import com.jason54.itemrush.network.RoundEndPayload;
import com.jason54.itemrush.network.RoundStartPayload;
import com.jason54.itemrush.network.TimerSyncPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

/**
 * Handles S2C payloads on the client main thread.
 */
public final class ClientPacketHandler {

    private ClientPacketHandler() {
    }

    public static void handleRoundStart(RoundStartPayload payload) {
        Item item = BuiltInRegistries.ITEM.getValue(payload.targetItem());
        if (item == null || item == Items.AIR) {
            item = Items.DIAMOND;
        }
        RouletteAnimator.reset();
        ClientRoundState.beginRound(
                payload.targetItem(),
                item,
                payload.durationSeconds(),
                payload.rouletteTicks(),
                payload.rouletteSpeed()
        );

        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.player.playSound(SoundEvents.UI_BUTTON_CLICK.value(), 0.7f, 1.2f);
        }
    }

    public static void handleTimerSync(TimerSyncPayload payload) {
        ClientRoundState.syncTimer(payload.secondsLeft());
        // Server timer sync means PLAYING has begun (or is in progress).
        if (ClientRoundState.phase() == ClientRoundState.Phase.ROULETTE) {
            ClientRoundState.markPlaying();
        }
    }

    public static void handleRoundEnd(RoundEndPayload payload) {
        ClientRoundState.endRound(payload.reason(), payload.playerName(), payload.rewardDisplay());

        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            if (payload.reason() == RoundEndPayload.REASON_WINNER) {
                mc.player.playSound(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
            } else if (payload.reason() == RoundEndPayload.REASON_TIME_UP) {
                mc.player.playSound(SoundEvents.VILLAGER_NO, 0.8f, 0.8f);
            }
        }
    }

    public static void onClientTick() {
        if (ClientRoundState.phase() == ClientRoundState.Phase.ROULETTE) {
            boolean changed = RouletteAnimator.updateDisplay();
            Minecraft mc = Minecraft.getInstance();
            if (changed && mc.player != null) {
                mc.player.playSound(SoundEvents.UI_BUTTON_CLICK.value(), 0.25f, 1.6f);
            }
        }
        ClientRoundState.clientTick();
    }
}
