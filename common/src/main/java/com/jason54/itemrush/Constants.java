package com.jason54.itemrush;

import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Shared mod constants.
 */
public final class Constants {

    public static final String MOD_ID = "itemrush";
    public static final String MOD_NAME = "ItemRush";
    public static final Logger LOG = LoggerFactory.getLogger(MOD_NAME);

    private Constants() {
    }

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MOD_ID, path);
    }
}
