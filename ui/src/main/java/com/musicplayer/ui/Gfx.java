package com.musicplayer.ui;

/**
 * Everything the shared UI draws with. One implementation per Minecraft version.
 * Coordinates are GUI pixels; colors are ARGB. Texture ids are strings like "musicplayer:textures/gui/x.png".
 */
public interface Gfx {
    void fill(int x0, int y0, int x1, int y1, int argb);

    /** Text with its top-left at x,y at scale 1. */
    void text(String s, boolean bold, int x, int y, int color, boolean shadow);

    /** Text drawn at an integer scale (>=2) with its top-left at x,y. */
    void scaledText(String s, boolean bold, int x, int y, int scale, int color, boolean shadow);

    /** Plain text with shadow, centered on cx. */
    void centeredText(String s, int cx, int y, int color);

    /** Draws a whole texture (or the u/v window of it) into the rectangle. */
    void blit(String texture, int x0, int y0, int x1, int y1, float u0, float u1, float v0, float v1);

    void enableScissor(int x0, int y0, int x1, int y1);
    void disableScissor();

    /** Starts a new layer so what follows draws above everything before it. */
    void nextStratum();

    /** Requests the hand (pointing) mouse cursor for this frame. */
    void handCursor();

    int textWidth(String s, boolean bold);
    int lineHeight();
    /** Longest prefix of s that fits into w pixels. */
    String trimToWidth(String s, int w);

    int guiWidth();
    int guiHeight();
    /** True while any screen is open (HUD elements hide then). */
    boolean screenOpen();
}
