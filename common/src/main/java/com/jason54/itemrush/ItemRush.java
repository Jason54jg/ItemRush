package com.jason54.itemrush;

import com.jason54.itemrush.config.ItemRushConfig;
import com.jason54.itemrush.platform.Services;

/**
 * Common bootstrap shared by Fabric and NeoForge.
 */
public final class ItemRush {

    private ItemRush() {
    }

    public static void init() {
        Constants.LOG.info("Initializing {} on {}", Constants.MOD_NAME, Services.PLATFORM.getPlatformName());
        ItemRushConfig.get();
    }
}
