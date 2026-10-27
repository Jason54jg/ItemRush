package com.jason54.itemrush.client;

/**
 * Entry used by loader clients to render the HUD without depending on a specific GuiGraphics type.
 */
public final class HudBridge {

    private HudBridge() {
    }

    public static void render(Object guiGraphics, float partialTick) {
        ItemRushHud.render(ReflectiveHudGraphics.wrap(guiGraphics), partialTick);
    }
}
