package com.jason54.itemrush.game;

import com.jason54.itemrush.Constants;
import com.jason54.itemrush.config.ItemRushConfig;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Resolves the configured target item pool and picks one at random.
 */
public final class TargetPool {

    private TargetPool() {
    }

    public static Item pickTarget() {
        List<Item> pool = resolvePool();
        if (pool.isEmpty()) {
            Constants.LOG.warn("Target pool empty — falling back to diamond");
            return Items.DIAMOND;
        }
        return pool.get(ThreadLocalRandom.current().nextInt(pool.size()));
    }

    public static List<Item> resolvePool() {
        ItemRushConfig config = ItemRushConfig.get();
        List<Item> pool = new ArrayList<>();

        if (config.useAllItems) {
            for (Item item : BuiltInRegistries.ITEM) {
                if (item == Items.AIR) {
                    continue;
                }
                pool.add(item);
            }
            return pool;
        }

        for (String raw : config.targetItems) {
            try {
                Identifier id = Identifier.parse(raw);
                Item item = BuiltInRegistries.ITEM.getValue(id);
                if (item != null && item != Items.AIR) {
                    pool.add(item);
                } else {
                    Constants.LOG.warn("Unknown target item in config: {}", raw);
                }
            } catch (Exception e) {
                Constants.LOG.warn("Invalid target item id: {}", raw);
            }
        }
        return pool;
    }
}
