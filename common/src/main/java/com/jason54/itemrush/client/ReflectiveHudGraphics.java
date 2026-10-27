package com.jason54.itemrush.client;

import net.minecraft.client.gui.Font;
import net.minecraft.world.item.ItemStack;

import java.lang.reflect.Method;

/**
 * Reflective bridge over GuiGraphics (1.21.x) and GuiGraphicsExtractor (26.x).
 */
public final class ReflectiveHudGraphics implements HudGraphics {

    private final Object graphics;

    private ReflectiveHudGraphics(Object graphics) {
        this.graphics = graphics;
    }

    public static HudGraphics wrap(Object graphics) {
        return new ReflectiveHudGraphics(graphics);
    }

    @Override
    public void fill(int x1, int y1, int x2, int y2, int argb) {
        invoke("fill", new Class<?>[]{int.class, int.class, int.class, int.class, int.class}, x1, y1, x2, y2, argb);
    }

    @Override
    public void text(Font font, String value, int x, int y, int argb, boolean shadow) {
        if (tryInvoke("text", new Class<?>[]{Font.class, String.class, int.class, int.class, int.class, boolean.class},
                font, value, x, y, argb, shadow)) {
            return;
        }
        // 1.21.x GuiGraphics#drawString
        if (tryInvoke("drawString", new Class<?>[]{Font.class, String.class, int.class, int.class, int.class, boolean.class},
                font, value, x, y, argb, shadow)) {
            return;
        }
        invoke("drawString", new Class<?>[]{Font.class, String.class, int.class, int.class, int.class},
                font, value, x, y, argb);
    }

    @Override
    public void centeredText(Font font, String value, int x, int y, int argb) {
        if (tryInvoke("centeredText", new Class<?>[]{Font.class, String.class, int.class, int.class, int.class},
                font, value, x, y, argb)) {
            return;
        }
        invoke("drawCenteredString", new Class<?>[]{Font.class, String.class, int.class, int.class, int.class},
                font, value, x, y, argb);
    }

    @Override
    public void item(ItemStack stack, int x, int y) {
        if (tryInvoke("item", new Class<?>[]{ItemStack.class, int.class, int.class}, stack, x, y)) {
            return;
        }
        invoke("renderItem", new Class<?>[]{ItemStack.class, int.class, int.class}, stack, x, y);
    }

    private boolean tryInvoke(String name, Class<?>[] types, Object... args) {
        try {
            Method method = graphics.getClass().getMethod(name, types);
            method.invoke(graphics, args);
            return true;
        } catch (ReflectiveOperationException e) {
            return false;
        }
    }

    private void invoke(String name, Class<?>[] types, Object... args) {
        if (!tryInvoke(name, types, args)) {
            throw new IllegalStateException("Missing HUD method: " + name);
        }
    }
}
