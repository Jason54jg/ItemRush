package com.jason54.itemrush.client;

import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Client-only mirror of the active round for HUD / roulette.
 */
public final class ClientRoundState {

    public enum Phase {
        IDLE,
        ROULETTE,
        PLAYING,
        RESULT
    }

    private static Phase phase = Phase.IDLE;
    private static Identifier targetId;
    private static ItemStack targetStack = ItemStack.EMPTY;
    private static int secondsLeft;
    /** True when the round was started with duration &lt;= 0. */
    private static boolean unlimited;
    private static int rouletteTicksTotal;
    private static int rouletteTicksLeft;
    private static float rouletteSpeed = 1.0f;
    private static long rouletteStartMillis;
    private static ItemStack displayStack = ItemStack.EMPTY;

    private static int resultReason = -1;
    private static String resultPlayer = "";
    private static String resultReward = "";
    private static long resultUntilMillis;

    private ClientRoundState() {
    }

    public static void beginRound(Identifier target, Item item, int durationSeconds, int rouletteTicks, float speed) {
        phase = Phase.ROULETTE;
        targetId = target;
        targetStack = new ItemStack(item);
        displayStack = targetStack.copy();
        unlimited = durationSeconds <= 0;
        secondsLeft = unlimited ? -1 : durationSeconds;
        rouletteTicksTotal = Math.max(1, rouletteTicks);
        rouletteTicksLeft = rouletteTicksTotal;
        rouletteSpeed = Math.max(0.1f, speed);
        rouletteStartMillis = System.currentTimeMillis();
        resultReason = -1;
        resultPlayer = "";
        resultReward = "";
    }

    public static void syncTimer(int seconds) {
        secondsLeft = seconds;
        if (phase == Phase.ROULETTE && seconds >= 0) {
            // Server entered PLAYING; keep client in sync if roulette animation finished early/late.
        }
        if (phase == Phase.ROULETTE && rouletteTicksLeft <= 0) {
            phase = Phase.PLAYING;
        }
    }

    public static void markPlaying() {
        if (phase == Phase.ROULETTE) {
            phase = Phase.PLAYING;
            displayStack = targetStack.copy();
        }
    }

    public static void endRound(int reason, String playerName, String reward) {
        phase = Phase.RESULT;
        resultReason = reason;
        resultPlayer = playerName == null ? "" : playerName;
        resultReward = reward == null ? "" : reward;
        resultUntilMillis = System.currentTimeMillis() + 4500L;
    }

    public static void clientTick() {
        if (phase == Phase.ROULETTE) {
            rouletteTicksLeft--;
            if (rouletteTicksLeft <= 0) {
                phase = Phase.PLAYING;
                displayStack = targetStack.copy();
            }
        } else if (phase == Phase.RESULT) {
            if (System.currentTimeMillis() >= resultUntilMillis) {
                clear();
            }
        }
    }

    public static void clear() {
        phase = Phase.IDLE;
        targetId = null;
        targetStack = ItemStack.EMPTY;
        displayStack = ItemStack.EMPTY;
        secondsLeft = 0;
        unlimited = false;
        rouletteTicksLeft = 0;
        resultReason = -1;
    }

    public static Phase phase() {
        return phase;
    }

    public static ItemStack targetStack() {
        return targetStack;
    }

    public static ItemStack displayStack() {
        return displayStack;
    }

    public static void setDisplayStack(ItemStack stack) {
        displayStack = stack;
    }

    public static int secondsLeft() {
        return secondsLeft;
    }

    /** {@code true} when the server sent duration &lt;= 0 (no round timer). */
    public static boolean isUnlimited() {
        return unlimited;
    }

    public static int rouletteTicksLeft() {
        return rouletteTicksLeft;
    }

    public static int rouletteTicksTotal() {
        return rouletteTicksTotal;
    }

    public static float rouletteSpeed() {
        return rouletteSpeed;
    }

    public static long rouletteStartMillis() {
        return rouletteStartMillis;
    }

    public static int resultReason() {
        return resultReason;
    }

    public static String resultPlayer() {
        return resultPlayer;
    }

    public static String resultReward() {
        return resultReward;
    }

    public static Identifier targetId() {
        return targetId;
    }
}
