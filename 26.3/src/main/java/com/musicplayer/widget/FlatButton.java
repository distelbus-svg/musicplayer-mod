package com.musicplayer.widget;

import com.musicplayer.MusicPlayerManager;
import com.musicplayer.theme.ColorTheme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

public class FlatButton extends AbstractWidget {
    private static final int BORDER = 1;
    private final Runnable onClick;
    private boolean active = false;
    private int labelDy = 0;

    public FlatButton(int x, int y, int width, int height, Component message, Runnable onClick) {
        super(x, y, width, height, message);
        this.onClick = onClick;
    }

    /** Nudges the label vertically; glyphs from the icon font have different top edges, so some need a pixel. */
    public void setLabelOffsetY(int dy) {
        this.labelDy = dy;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public boolean isActive() {
        return active;
    }

    private ColorTheme theme() {
        return ColorTheme.get(MusicPlayerManager.getInstance().getConfig().getThemeId());
    }

    /**
     * Centres the label on its visible width. Font.width() includes a 1px trailing gap after the last glyph,
     * which pushed labels (especially single-glyph icons) half a pixel to the left.
     */
    private void drawLabel(GuiGraphicsExtractor graphics, int color) {
        var font = Minecraft.getInstance().font;
        int visibleW = Math.max(0, font.width(getMessage()) - 1);
        int x = getX() + (getWidth() - visibleW) / 2;
        int y = getY() + (getHeight() - 8) / 2 + labelDy;
        graphics.text(font, getMessage(), x, y, color, true);
    }

    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        ColorTheme th = theme();
        boolean hovered = mouseX >= getX() && mouseX < getX() + getWidth()
                && mouseY >= getY() && mouseY < getY() + getHeight();
        if (active) {
            graphics.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(), th.accent());
            graphics.fill(getX() + BORDER, getY() + BORDER, getX() + getWidth() - BORDER, getY() + getHeight() - BORDER, th.panelBg());
            drawLabel(graphics, th.accent());
        } else {
            int border = hovered ? th.accent() : th.dimText();
            graphics.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(), border);
            graphics.fill(getX() + BORDER, getY() + BORDER, getX() + getWidth() - BORDER, getY() + getHeight() - BORDER, th.panelBg());
            drawLabel(graphics, th.text());
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean isFocused) {
        if (event.button() == com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT
                && event.x() >= getX() && event.x() < getX() + getWidth()
                && event.y() >= getY() && event.y() < getY() + getHeight()) {
            onClick.run();
            return true;
        }
        return false;
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
    }
}
