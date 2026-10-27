package com.jason54.itemrush.client;

import com.jason54.itemrush.network.RoundEndPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.world.item.ItemStack;

/**
 * Shared HUD drawing for roulette, timer, and winner banner.
 */
public final class ItemRushHud {

    private ItemRushHud() {
    }

    public static void render(HudGraphics graphics, float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || ClientRoundState.phase() == ClientRoundState.Phase.IDLE) {
            return;
        }

        int screenW = mc.getWindow().getGuiScaledWidth();
        int screenH = mc.getWindow().getGuiScaledHeight();
        Font font = mc.font;

        if (ClientRoundState.phase() == ClientRoundState.Phase.ROULETTE
                || ClientRoundState.phase() == ClientRoundState.Phase.PLAYING) {
            renderTimer(graphics, font, screenW);
            renderRoulette(graphics, font, screenW);
        }

        if (ClientRoundState.phase() == ClientRoundState.Phase.RESULT) {
            renderResult(graphics, font, screenW, screenH);
        }
    }

    private static void renderTimer(HudGraphics graphics, Font font, int screenW) {
        int seconds = ClientRoundState.secondsLeft();
        String text = formatTime(seconds);
        int color = seconds <= 10 ? argb(255, 255, 80, 80) : argb(255, 255, 255, 255);
        int x = (screenW - font.width(text)) / 2;
        graphics.fill(x - 8, 6, x + font.width(text) + 8, 22, argb(140, 0, 0, 0));
        graphics.text(font, text, x, 10, color, true);
    }

    private static void renderRoulette(HudGraphics graphics, Font font, int screenW) {
        int box = 72;
        int x = (screenW - box) / 2;
        int y = 36;

        graphics.fill(x - 4, y - 4, x + box + 4, y + box + 28, argb(160, 0, 0, 0));
        graphics.fill(x, y, x + box, y + box, argb(200, 30, 30, 40));

        ItemStack stack = ClientRoundState.displayStack();
        if (!stack.isEmpty()) {
            graphics.item(stack, x + (box - 16) / 2, y + (box - 16) / 2);
        }

        String label;
        int labelColor;
        if (ClientRoundState.phase() == ClientRoundState.Phase.ROULETTE) {
            label = "Spinning...";
            labelColor = argb(255, 255, 220, 100);
        } else {
            label = stack.getHoverName().getString();
            labelColor = argb(255, 120, 255, 140);
            graphics.centeredText(font, "Find this item!", screenW / 2, y + box + 14, argb(255, 200, 200, 200));
        }
        graphics.centeredText(font, label, screenW / 2, y + box + 2, labelColor);
    }

    private static void renderResult(HudGraphics graphics, Font font, int screenW, int screenH) {
        int reason = ClientRoundState.resultReason();
        String title;
        String subtitle;
        int titleColor;

        if (reason == RoundEndPayload.REASON_WINNER) {
            title = "Winner!";
            subtitle = ClientRoundState.resultPlayer() + " - " + ClientRoundState.resultReward();
            titleColor = argb(255, 255, 215, 0);
        } else if (reason == RoundEndPayload.REASON_TIME_UP) {
            title = "Time's up!";
            subtitle = "Nobody found the item";
            titleColor = argb(255, 255, 80, 80);
        } else {
            title = "Round stopped";
            subtitle = "";
            titleColor = argb(255, 200, 200, 200);
        }

        int cy = screenH / 2 - 20;
        graphics.fill(screenW / 2 - 140, cy - 16, screenW / 2 + 140, cy + 36, argb(170, 0, 0, 0));
        graphics.centeredText(font, title, screenW / 2, cy, titleColor);
        if (!subtitle.isEmpty()) {
            graphics.centeredText(font, subtitle, screenW / 2, cy + 16, argb(255, 230, 230, 230));
        }
    }

    private static String formatTime(int totalSeconds) {
        int m = Math.max(0, totalSeconds) / 60;
        int s = Math.max(0, totalSeconds) % 60;
        return String.format("%d:%02d", m, s);
    }

    private static int argb(int a, int r, int g, int b) {
        return (a & 0xFF) << 24 | (r & 0xFF) << 16 | (g & 0xFF) << 8 | (b & 0xFF);
    }
}
