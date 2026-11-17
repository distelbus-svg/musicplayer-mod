package com.musicplayer.host;

import com.musicplayer.Ids;

import com.mojang.blaze3d.platform.cursor.CursorTypes;
import com.musicplayer.ui.Gfx;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Draws the shared UI with the 26.x GuiGraphicsExtractor. */
final class GfxImpl implements Gfx {
    private static final Style BOLD = Style.EMPTY.withBold(true);
    private static final Map<String, Identifier> IDS = new ConcurrentHashMap<>();

    private final GuiGraphicsExtractor g;
    private final Minecraft mc = Minecraft.getInstance();

    GfxImpl(GuiGraphicsExtractor g) { this.g = g; }

    GuiGraphicsExtractor raw() { return g; }

    private Font font() { return mc.font; }

    private static Component comp(String s, boolean bold) {
        return bold ? Component.literal(s).withStyle(BOLD) : Component.literal(s);
    }

    @Override public void fill(int x0, int y0, int x1, int y1, int argb) { g.fill(x0, y0, x1, y1, argb); }

    @Override public void text(String s, boolean bold, int x, int y, int color, boolean shadow) {
        g.text(font(), comp(s, bold), x, y, color, shadow);
    }

    @Override public void scaledText(String s, boolean bold, int x, int y, int scale, int color, boolean shadow) {
        g.pose().pushMatrix();
        g.pose().translate(x, y);
        g.pose().scale(scale, scale);
        g.text(font(), comp(s, bold), 0, 0, color, shadow);
        g.pose().popMatrix();
    }

    @Override public void centeredText(String s, int cx, int y, int color) { g.centeredText(font(), s, cx, y, color); }

    @Override public void blit(String texture, int x0, int y0, int x1, int y1, float u0, float u1, float v0, float v1) {
        g.blit(IDS.computeIfAbsent(texture, Identifier::parse), x0, y0, x1, y1, u0, u1, v0, v1);
    }

    @Override public void enableScissor(int x0, int y0, int x1, int y1) { g.enableScissor(x0, y0, x1, y1); }
    @Override public void disableScissor() { g.disableScissor(); }
    @Override public void nextStratum() { g.nextStratum(); }
    @Override public void handCursor() { g.requestCursor(CursorTypes.POINTING_HAND); }

    @Override public int textWidth(String s, boolean bold) { return font().width(comp(s, bold)); }
    @Override public int lineHeight() { return font().lineHeight; }
    @Override public String trimToWidth(String s, int w) { return font().plainSubstrByWidth(s, w); }

    @Override public int guiWidth() { return mc.getWindow().getGuiScaledWidth(); }
    @Override public int guiHeight() { return mc.getWindow().getGuiScaledHeight(); }
    @Override public boolean screenOpen() { return mc.gui.screen() != null; }
}
