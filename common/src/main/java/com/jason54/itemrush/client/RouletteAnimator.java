package com.jason54.itemrush.client;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Client-side roulette item cycling that lands on the server-chosen target.
 */
public final class RouletteAnimator {

    private static final List<Item> VISUAL_POOL = new ArrayList<>();
    private static int lastSlot = -1;
    private static long lastClickMillis;

    private RouletteAnimator() {
    }

    public static void ensurePool() {
        if (!VISUAL_POOL.isEmpty()) {
            return;
        }
        for (Item item : BuiltInRegistries.ITEM) {
            if (item != Items.AIR) {
                VISUAL_POOL.add(item);
            }
        }
        if (VISUAL_POOL.isEmpty()) {
            VISUAL_POOL.add(Items.DIAMOND);
        }
    }

    /**
     * Updates the displayed stack for the current animation frame.
     *
     * @return true if the visible item changed (useful for tick sounds)
     */
    public static boolean updateDisplay() {
        if (ClientRoundState.phase() != ClientRoundState.Phase.ROULETTE) {
            return false;
        }
        ensurePool();

        float progress = 1.0f - (ClientRoundState.rouletteTicksLeft() / (float) ClientRoundState.rouletteTicksTotal());
        progress = Mth.clamp(progress, 0.0f, 1.0f);

        // Ease-out: fast at start, slow near the end.
        float ease = 1.0f - (float) Math.pow(1.0f - progress, 2.2);
        float speed = ClientRoundState.rouletteSpeed();
        long now = System.currentTimeMillis();
        // Interval between item flips grows as we approach the end.
        long interval = (long) ((20 + ease * 180) / speed);

        if (progress >= 0.92f) {
            // Snap to the real target near the end so the stop feels intentional.
            ItemStack target = ClientRoundState.targetStack();
            boolean changed = !ItemStack.isSameItemSameComponents(ClientRoundState.displayStack(), target);
            ClientRoundState.setDisplayStack(target.copy());
            return changed;
        }

        if (now - lastClickMillis < interval) {
            return false;
        }
        lastClickMillis = now;

        int slot = ThreadLocalRandom.current().nextInt(VISUAL_POOL.size());
        if (slot == lastSlot && VISUAL_POOL.size() > 1) {
            slot = (slot + 1) % VISUAL_POOL.size();
        }
        lastSlot = slot;
        ClientRoundState.setDisplayStack(new ItemStack(VISUAL_POOL.get(slot)));
        return true;
    }

    public static void reset() {
        lastSlot = -1;
        lastClickMillis = 0;
    }
}
