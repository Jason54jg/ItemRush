package com.jason54.itemrush.client;

import net.minecraft.client.gui.Font;
import net.minecraft.world.item.ItemStack;

/**
 * Thin drawing facade so the same HUD code works on 1.21.11 ({@code GuiGraphics})
 * and 26.x ({@code GuiGraphicsExtractor}).
 */
public interface HudGraphics {

    void fill(int x1, int y1, int x2, int y2, int argb);

    void text(Font font, String value, int x, int y, int argb, boolean shadow);

    void centeredText(Font font, String value, int x, int y, int argb);

    void item(ItemStack stack, int x, int y);
}
