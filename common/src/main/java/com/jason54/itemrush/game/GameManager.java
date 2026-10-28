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
    /** Remaining play ticks; {@code -1} = unlimited. */
    private static int playTicksLeft = -1;
    /** 0 = unlimited. */
    private static int durationSeconds;
    private static MinecraftServer boundServer;
    private static boolean winnerLock;
    private static int restartTicksLeft = -1;

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

    public static boolean isUnlimited() {
        return playTicksLeft < 0 || durationSeconds <= 0;
    }

    public static int getSecondsLeft() {
        if (isUnlimited()) {
            return -1;
        }
        return Math.max(0, (playTicksLeft + 19) / 20);
    }

    public static boolean isAcceptingPickups() {
        return phase == RoundPhase.PLAYING && !winnerLock;
    }

    /**
     * @param durationSeconds 0 or less = unlimited time
     */
    public static Component start(MinecraftServer server, int durationSeconds) {
        ItemRushConfig config = ItemRushConfig.get();
        if (phase != RoundPhase.IDLE && phase != RoundPhase.ENDING) {
            return Component.literal(config.messages.alreadyRunning).withStyle(ChatFormatting.RED);
        }

        // Cancel a pending auto-restart if an admin starts manually.
        restartTicksLeft = -1;

        Item picked = TargetPool.pickTarget();
        Identifier id = BuiltInRegistries.ITEM.getKey(picked);

        GameManager.boundServer = server;
        GameManager.targetItem = picked;
        GameManager.targetId = id;
        GameManager.durationSeconds = Math.max(0, durationSeconds);
        GameManager.rouletteTicksLeft = Math.max(20, config.rouletteDurationTicks);
        GameManager.playTicksLeft = GameManager.durationSeconds <= 0
                ? -1
                : GameManager.durationSeconds * 20;
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
        Constants.LOG.info("ItemRush started — target={} unlimited={}", id, isUnlimited());
        return Component.literal("ItemRush started! Target will be revealed by the roulette.")
                .withStyle(ChatFormatting.GREEN);
    }

    public static Component stop(MinecraftServer server) {
        ItemRushConfig config = ItemRushConfig.get();
        boolean wasRestarting = restartTicksLeft >= 0;
        restartTicksLeft = -1;
        if (phase == RoundPhase.IDLE && !wasRestarting) {
            return Component.literal(config.messages.notRunning).withStyle(ChatFormatting.RED);
        }
        if (phase != RoundPhase.IDLE) {
            endRound(server, RoundEndPayload.REASON_STOPPED, "", "", false);
        }
        broadcast(server, Component.literal(config.messages.roundStopped).withStyle(ChatFormatting.YELLOW));
        return Component.literal(config.messages.roundStopped).withStyle(ChatFormatting.YELLOW);
    }

    public static Component status() {
        ItemRushConfig config = ItemRushConfig.get();
        if (restartTicksLeft >= 0) {
            return Component.literal("ItemRush restarting in " + ((restartTicksLeft + 19) / 20) + "s…")
                    .withStyle(ChatFormatting.AQUA);
        }
        return switch (phase) {
            case IDLE, ENDING -> Component.literal(config.messages.statusIdle);
            case ROULETTE -> Component.literal(config.messages.statusRoulette).withStyle(ChatFormatting.AQUA);
            case PLAYING -> {
                String itemName = targetItem == null ? "?" : Component.translatable(targetItem.getDescriptionId()).getString();
                String text;
                if (isUnlimited()) {
                    text = config.messages.statusRunningUnlimited.replace("%item%", itemName);
                } else {
                    text = config.messages.statusRunning
                            .replace("%item%", itemName)
                            .replace("%time%", String.valueOf(getSecondsLeft()));
                }
                yield Component.literal(text).withStyle(ChatFormatting.GREEN);
            }
        };
    }

    /**
     * Called every server tick while a round is active (or while auto-restart is pending).
     */
    public static void serverTick(MinecraftServer server) {
        if (restartTicksLeft >= 0) {
            restartTicksLeft--;
            if (restartTicksLeft < 0) {
                start(server, ItemRushConfig.get().defaultDurationSeconds);
            }
            return;
        }

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
            // Unlimited: no countdown, no time-up.
            if (playTicksLeft < 0) {
                return;
            }
            playTicksLeft--;
            if (playTicksLeft % 20 == 0) {
                Services.PLATFORM.sendToAllPlayers(server, new TimerSyncPayload(getSecondsLeft()));
            }
            if (playTicksLeft <= 0) {
                ItemRushConfig config = ItemRushConfig.get();
                broadcast(server, Component.literal(config.messages.timeUp).withStyle(ChatFormatting.RED));
                endRound(server, RoundEndPayload.REASON_TIME_UP, "", "", config.autoRestart);
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

        // Winner banner is shown via client RoundEndPayload HUD only (avoids double vanilla titles).

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

        endRound(server, RoundEndPayload.REASON_WINNER, playerName, reward.display(), config.autoRestart);
    }

    private static void endRound(MinecraftServer server, int reason, String playerName, String reward, boolean scheduleRestart) {
        phase = RoundPhase.ENDING;
        Services.PLATFORM.sendToAllPlayers(server, new RoundEndPayload(reason, playerName, reward));
        ItemRushConfig config = ItemRushConfig.get();
        int delay = Math.max(20, config.autoRestartDelayTicks);
        reset();
        if (scheduleRestart) {
            boundServer = server;
            restartTicksLeft = delay;
            Constants.LOG.info("ItemRush auto-restart in {} ticks", delay);
        }
    }

    private static void reset() {
        phase = RoundPhase.IDLE;
        targetItem = null;
        targetId = null;
        rouletteTicksLeft = 0;
        playTicksLeft = -1;
        winnerLock = false;
        boundServer = null;
    }

    private static void broadcast(MinecraftServer server, Component message) {
        server.getPlayerList().broadcastSystemMessage(message, false);
    }
}
