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

    public FlatButton(int x, int y, int width, int height, Component message, Runnable onClick) {
        super(x, y, width, height, message);
        this.onClick = onClick;
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

    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        ColorTheme th = theme();
        boolean hovered = mouseX >= getX() && mouseX < getX() + getWidth()
                && mouseY >= getY() && mouseY < getY() + getHeight();
        if (active) {
            graphics.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(), th.accent());
            graphics.fill(getX() + BORDER, getY() + BORDER, getX() + getWidth() - BORDER, getY() + getHeight() - BORDER, th.panelBg());
            graphics.centeredText(Minecraft.getInstance().font, getMessage(), getX() + getWidth() / 2, getY() + (getHeight() - 8) / 2, th.accent());
        } else {
            int border = hovered ? th.accent() : th.dimText();
            graphics.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(), border);
            graphics.fill(getX() + BORDER, getY() + BORDER, getX() + getWidth() - BORDER, getY() + getHeight() - BORDER, th.panelBg());
            graphics.centeredText(Minecraft.getInstance().font, getMessage(), getX() + getWidth() / 2, getY() + (getHeight() - 8) / 2, th.text());
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean isFocused) {
        if (event.button() == 0
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
