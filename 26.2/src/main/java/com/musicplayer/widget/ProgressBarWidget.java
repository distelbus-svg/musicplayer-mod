package com.musicplayer.widget;

import com.musicplayer.MusicPlayerManager;
import com.musicplayer.VanillaMusicPlayer;
import com.musicplayer.VanillaTrack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/**
 * A clickable progress bar that shows playback progress and allows seeking.
 * Displays elapsed / total time overlay text.
 */
public class ProgressBarWidget extends AbstractWidget {

    private static final int BG_COLOR = 0xFF333333;
    private static final int FILL_COLOR = 0xFF00AA00;
    private static final int BORDER_COLOR = 0xFF555555;
    private static final int HANDLE_COLOR = 0xFFFFFFFF;

    private final MusicPlayerManager manager;
    private boolean dragging = false;

    public ProgressBarWidget(int x, int y, int width, int height) {
        super(x, y, width, height, Component.literal("Progress"));
        this.manager = MusicPlayerManager.getInstance();
    }

    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        // Border
        graphics.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(), BORDER_COLOR);
        // Background
        graphics.fill(getX() + 1, getY() + 1, getX() + getWidth() - 1, getY() + getHeight() - 1, BG_COLOR);

        // Fill based on progress
        float progress = manager.getProgress();
        int fillWidth = (int) ((getWidth() - 2) * progress);
        if (fillWidth > 0) {
            graphics.fill(
                    getX() + 1, getY() + 1,
                    getX() + 1 + fillWidth, getY() + getHeight() - 1,
                    FILL_COLOR
            );
        }

        // Handle (small white rectangle at current position)
        boolean isMc = manager.getCurrentSource() == MusicPlayerManager.SongSource.MINECRAFT;
        boolean active = isMc ? VanillaMusicPlayer.getInstance().isPlaying() || VanillaMusicPlayer.getInstance().isPaused()
                              : manager.getEngine().isActive();
        if (active) {
            int handleX = getX() + 1 + fillWidth - 1;
            graphics.fill(
                    Math.max(getX() + 1, handleX), getY(),
                    Math.min(getX() + getWidth() - 1, handleX + 3), getY() + getHeight(),
                    HANDLE_COLOR
            );
        }

        // Time text: "elapsed" under bar left, "total" under bar right
        long posMs;
        long durMs;
        if (isMc) {
            posMs = VanillaMusicPlayer.getInstance().getElapsedMs();
            VanillaTrack vt = VanillaMusicPlayer.getInstance().getCurrentTrack();
            durMs = vt != null ? vt.durationSeconds() * 1000L : 0;
        } else {
            posMs = manager.getEngine().getPositionMs();
            durMs = manager.getEngine().getDurationMs();
        }
        String posStr = MusicPlayerManager.formatTime(posMs);
        String durStr = MusicPlayerManager.formatTime(durMs);
        var font = Minecraft.getInstance().font;
        graphics.text(font, posStr, getX(), getY() + getHeight() + 1, 0xFFAAAAAA, true);
        graphics.text(font, durStr, getX() + getWidth() - font.width(durStr), getY() + getHeight() + 1, 0xFFAAAAAA, true);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean isFocused) {
        if (event.buttonInfo().button() == 0 && this.isHovered() && manager.getEngine().isActive()) {
            dragging = true;
            seekToMouse(event.x());
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        if (dragging && event.buttonInfo().button() == 0) {
            seekToMouse(event.x());
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (event.buttonInfo().button() == 0 && dragging) {
            dragging = false;
            return true;
        }
        return false;
    }

    private void seekToMouse(double mouseX) {
        float percent = (float) (mouseX - getX()) / getWidth();
        percent = Math.max(0f, Math.min(1f, percent));
        manager.seek(percent);
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        // Narration handled by default
    }
}
