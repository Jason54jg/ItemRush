package com.jason54.itemrush.game;

import com.jason54.itemrush.Constants;
import com.jason54.itemrush.config.ItemRushConfig;
import com.jason54.itemrush.network.RoundEndPayload;
import com.jason54.itemrush.network.RoundStartPayload;
import com.jason54.itemrush.network.TimerSyncPayload;
import com.jason54.itemrush.platform.Services;
import com.jason54.itemrush.util.PlayerNames;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Server-side ItemRush round state machine.
 */
public final class GameManager {

    private static RoundPhase phase = RoundPhase.IDLE;
    private static Item targetItem;
    private static Identifier targetId;
    private static int rouletteTicksLeft;
    private static int playTicksLeft;
    private static int durationSeconds;
    private static MinecraftServer boundServer;
    private static boolean winnerLock;

    private GameManager() {
    }

    public static RoundPhase getPhase() {
        return phase;
    }

    public static Item getTargetItem() {
        return targetItem;
    }

    public static Identifier getTargetId() {
        return targetId;
    }

    public static int getSecondsLeft() {
        return Math.max(0, (playTicksLeft + 19) / 20);
    }

    public static boolean isAcceptingPickups() {
        return phase == RoundPhase.PLAYING && !winnerLock;
    }

    public static Component start(MinecraftServer server, int durationSeconds) {
        ItemRushConfig config = ItemRushConfig.get();
        if (phase != RoundPhase.IDLE && phase != RoundPhase.ENDING) {
            return Component.literal(config.messages.alreadyRunning).withStyle(ChatFormatting.RED);
        }

        Item picked = TargetPool.pickTarget();
        Identifier id = BuiltInRegistries.ITEM.getKey(picked);

        GameManager.boundServer = server;
        GameManager.targetItem = picked;
        GameManager.targetId = id;
        GameManager.durationSeconds = Math.max(5, durationSeconds);
        GameManager.rouletteTicksLeft = Math.max(20, config.rouletteDurationTicks);
        GameManager.playTicksLeft = GameManager.durationSeconds * 20;
        GameManager.winnerLock = false;
        GameManager.phase = RoundPhase.ROULETTE;

        RoundStartPayload start = new RoundStartPayload(
                id,
                GameManager.durationSeconds,
                GameManager.rouletteTicksLeft,
                config.rouletteSpeed
        );
        Services.PLATFORM.sendToAllPlayers(server, start);

        String msg = config.messages.roundStarted.replace("%item%", Component.translatable(picked.getDescriptionId()).getString());
        broadcast(server, Component.literal(msg).withStyle(ChatFormatting.GOLD));
        Constants.LOG.info("ItemRush started — target={}", id);
        return Component.literal("ItemRush started! Target will be revealed by the roulette.")
                .withStyle(ChatFormatting.GREEN);
    }

    public static Component stop(MinecraftServer server) {
        ItemRushConfig config = ItemRushConfig.get();
        if (phase == RoundPhase.IDLE) {
            return Component.literal(config.messages.notRunning).withStyle(ChatFormatting.RED);
        }
        endRound(server, RoundEndPayload.REASON_STOPPED, "", "");
        broadcast(server, Component.literal(config.messages.roundStopped).withStyle(ChatFormatting.YELLOW));
        return Component.literal(config.messages.roundStopped).withStyle(ChatFormatting.YELLOW);
    }

    public static Component status() {
        ItemRushConfig config = ItemRushConfig.get();
        return switch (phase) {
            case IDLE, ENDING -> Component.literal(config.messages.statusIdle);
            case ROULETTE -> Component.literal(config.messages.statusRoulette).withStyle(ChatFormatting.AQUA);
            case PLAYING -> {
                String itemName = targetItem == null ? "?" : Component.translatable(targetItem.getDescriptionId()).getString();
                String text = config.messages.statusRunning
                        .replace("%item%", itemName)
                        .replace("%time%", String.valueOf(getSecondsLeft()));
                yield Component.literal(text).withStyle(ChatFormatting.GREEN);
            }
        };
    }

    /**
     * Called every server tick while a round is active.
     */
    public static void serverTick(MinecraftServer server) {
        if (phase == RoundPhase.IDLE || phase == RoundPhase.ENDING) {
            return;
        }

        if (phase == RoundPhase.ROULETTE) {
            rouletteTicksLeft--;
            if (rouletteTicksLeft <= 0) {
                phase = RoundPhase.PLAYING;
                Services.PLATFORM.sendToAllPlayers(server, new TimerSyncPayload(getSecondsLeft()));
            }
            return;
        }

        if (phase == RoundPhase.PLAYING) {
            playTicksLeft--;
            if (playTicksLeft % 20 == 0) {
                Services.PLATFORM.sendToAllPlayers(server, new TimerSyncPayload(getSecondsLeft()));
            }
            if (playTicksLeft <= 0) {
                ItemRushConfig config = ItemRushConfig.get();
                broadcast(server, Component.literal(config.messages.timeUp).withStyle(ChatFormatting.RED));
                sendTitles(server, Component.literal("Time's up!"), Component.literal(config.messages.timeUp));
                endRound(server, RoundEndPayload.REASON_TIME_UP, "", "");
            }
        }
    }

    /**
     * Server-side pickup detection. Must be an actual ItemEntity pickup of the exact item type.
     */
    public static void onItemPickedUp(ServerPlayer player, ItemEntity itemEntity, ItemStack stack) {
        if (!isAcceptingPickups() || player.level().isClientSide()) {
            return;
        }
        if (targetItem == null || stack.isEmpty()) {
            return;
        }

        // Exact item type match (registry identity), not display name.
        if (!stack.is(targetItem)) {
            return;
        }

        // Anti-cheat: must originate from a real world ItemEntity (mixin only fires after successful take).
        if (itemEntity == null) {
            return;
        }

        synchronized (GameManager.class) {
            if (!isAcceptingPickups()) {
                return;
            }
            winnerLock = true;
        }

        declareWinner(player);
    }

    private static void declareWinner(ServerPlayer winner) {
        MinecraftServer server = winner.level().getServer();
        if (server == null) {
            server = boundServer;
        }
        if (server == null) {
            reset();
            return;
        }

        ItemRushConfig config = ItemRushConfig.get();
        RewardSystem.GrantedReward reward = RewardSystem.grantRandom(winner)
                .orElse(new RewardSystem.GrantedReward("none", "nothing"));

        String playerName = PlayerNames.of(winner.getGameProfile());
        String chat = config.messages.winnerChat
                .replace("%player%", playerName)
                .replace("%reward%", reward.display());
        broadcast(server, Component.literal(chat).withStyle(ChatFormatting.LIGHT_PURPLE));

        String title = config.messages.winnerTitle;
        String subtitle = config.messages.winnerSubtitle
                .replace("%player%", playerName)
                .replace("%reward%", reward.display());
        sendTitles(server, Component.literal(title), Component.literal(subtitle));

        if (config.particlesEnabled && winner.level() instanceof ServerLevel level) {
            level.sendParticles(ParticleTypes.TOTEM_OF_UNDYING,
                    winner.getX(), winner.getY() + 1.0, winner.getZ(),
                    40, 0.5, 0.8, 0.5, 0.15);
            level.sendParticles(ParticleTypes.HAPPY_VILLAGER,
                    winner.getX(), winner.getY() + 1.0, winner.getZ(),
                    20, 0.4, 0.6, 0.4, 0.05);
        }
        if (config.soundsEnabled) {
            winner.level().playSound(null, winner.blockPosition(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE,
                    SoundSource.PLAYERS, 1.0f, 1.0f);
        }

        endRound(server, RoundEndPayload.REASON_WINNER, playerName, reward.display());
    }

    private static void endRound(MinecraftServer server, int reason, String playerName, String reward) {
        phase = RoundPhase.ENDING;
        Services.PLATFORM.sendToAllPlayers(server, new RoundEndPayload(reason, playerName, reward));
        reset();
    }

    private static void reset() {
        phase = RoundPhase.IDLE;
        targetItem = null;
        targetId = null;
        rouletteTicksLeft = 0;
        playTicksLeft = 0;
        winnerLock = false;
        boundServer = null;
    }

    private static void broadcast(MinecraftServer server, Component message) {
        server.getPlayerList().broadcastSystemMessage(message, false);
    }

    private static void sendTitles(MinecraftServer server, Component title, Component subtitle) {
        ClientboundSetTitlesAnimationPacket times = new ClientboundSetTitlesAnimationPacket(10, 60, 20);
        ClientboundSetTitleTextPacket titlePacket = new ClientboundSetTitleTextPacket(title);
        ClientboundSetSubtitleTextPacket subtitlePacket = new ClientboundSetSubtitleTextPacket(subtitle);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            player.connection.send(times);
            // Subtitle must be queued before title: Hud#setTitle starts the display timer,
            // while Hud#setSubtitle only stores text shown alongside an active title.
            player.connection.send(subtitlePacket);
            player.connection.send(titlePacket);
        }
    }
}
