package com.musicplayer.widget;

import com.musicplayer.MusicPlayerManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/**
 * A horizontal volume slider widget with visual fill and percentage display.
 */
public class VolumeSliderWidget extends AbstractWidget {

    private static final int BG_COLOR = 0xFF333333;
    private static final int FILL_COLOR = 0xFFAAAAAA;
    private static final int BORDER_COLOR = 0xFF555555;
    private static final int KNOB_COLOR = 0xFFFFFFFF;

    private final MusicPlayerManager manager;
    private boolean dragging = false;

    public VolumeSliderWidget(int x, int y, int width, int height) {
        super(x, y, width, height, Component.literal("Volume"));
        this.manager = MusicPlayerManager.getInstance();
    }

    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        float volume = manager.getEngine().getVolume();

        // Border
        graphics.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(), BORDER_COLOR);
        // Background
        graphics.fill(getX() + 1, getY() + 1, getX() + getWidth() - 1, getY() + getHeight() - 1, BG_COLOR);

        // Fill
        int fillWidth = (int) ((getWidth() - 2) * volume);
        if (fillWidth > 0) {
            graphics.fill(
                    getX() + 1, getY() + 1,
                    getX() + 1 + fillWidth, getY() + getHeight() - 1,
                    FILL_COLOR
            );
        }

        // Knob
        int knobX = getX() + 1 + fillWidth;
        graphics.fill(
                Math.max(getX() + 1, knobX - 1), getY(),
                Math.min(getX() + getWidth() - 1, knobX + 2), getY() + getHeight(),
                KNOB_COLOR
        );

        // Volume icon and percentage
        int percent = Math.round(volume * 100);
        String icon;
        if (volume <= 0) {
            icon = "\uD83D\uDD07"; // Muted
        } else if (volume < 0.5f) {
            icon = "\uD83D\uDD09"; // Low
        } else {
            icon = "\uD83D\uDD0A"; // High
        }
        // Draw percentage above the slider
        String text = percent + "%";
        int textWidth = Minecraft.getInstance().font.width(text);
        graphics.text(
                Minecraft.getInstance().font,
                text,
                getX() + (getWidth() - textWidth) / 2,
                getY() - 10,
                0xFFAAAAAA, true
        );
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean isFocused) {
        if (event.buttonInfo().button() == com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT && this.isHovered()) {
            dragging = true;
            setVolumeFromMouse(event.x());
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        if (dragging && event.buttonInfo().button() == com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT) {
            setVolumeFromMouse(event.x());
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (event.buttonInfo().button() == com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT && dragging) {
            dragging = false;
            return true;
        }
        return false;
    }

    private void setVolumeFromMouse(double mouseX) {
        float vol = (float) (mouseX - getX()) / getWidth();
        vol = Math.max(0f, Math.min(1f, vol));
        manager.setVolume(vol);
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        // Narration handled by default
    }
}
