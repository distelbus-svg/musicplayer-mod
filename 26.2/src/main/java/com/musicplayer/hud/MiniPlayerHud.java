package com.musicplayer.hud;

import com.musicplayer.AlbumArtManager;
import com.musicplayer.MusicPlayerManager;
import com.musicplayer.VanillaMusicPlayer;
import com.musicplayer.VanillaTrack;
import com.musicplayer.config.MusicPlayerConfig;
import com.musicplayer.config.MusicPlayerConfig.MiniPlayerStyle;
import com.musicplayer.theme.ColorTheme;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;

import static org.lwjgl.glfw.GLFW.*;

public class MiniPlayerHud {

    private static boolean registered;
    private static boolean dragging;
    private static int dragOffX, dragOffY;
    private static boolean resizing;
    private static boolean mouseWasDown;

    // Slide animation state (notification mode)
    private static boolean slideActive;
    private static float slideOffset;
    private static long slideStartMs;
    private static boolean slidingIn;
    private static long slideStayUntilMs;

    // Volume indicator state
    private static boolean volIndicatorActive;
    private static float volIndicatorValue;
    private static long volIndicatorStartMs;
    private static boolean volIndicatorSlidingIn;
    private static float volIndicatorOffset;
    private static long volIndicatorStayUntilMs;

    private static final int SLIDE_PANEL_W = 180;
    private static final int SLIDE_PANEL_H = 60;
    private static final int SLIDE_MARGIN = 8;
    private static final long SLIDE_DURATION_MS = 250;
    private static final long SLIDE_STAY_MS = 4000;

    private static final int VOL_INDICATOR_W = 120;
    private static final int VOL_INDICATOR_H = 40;

    public static void init() {
        if (!registered) {
            registered = true;
            HudElementRegistry.addLast(
                Identifier.fromNamespaceAndPath("musicplayer", "mini_player"),
                MiniPlayerHud::render
            );
        }
    }

    /** Monotonic clock — immune to system-time jumps (NTP, etc.). */
    private static long monotonicMs() {
        return System.nanoTime() / 1_000_000L;
    }

    public static void triggerSlideIn() {
        long now = monotonicMs();
        if (slideActive && slidingIn) {
            slideStayUntilMs = now + SLIDE_STAY_MS;
            return;
        }
        slideActive = true;
        slidingIn = true;
        slideOffset = SLIDE_PANEL_W + SLIDE_MARGIN;
        slideStartMs = now;
        slideStayUntilMs = now + SLIDE_STAY_MS;
    }

    public static void showVolumeIndicator(float volume) {
        long now = monotonicMs();
        volIndicatorValue = volume;
        volIndicatorSlidingIn = true;
        volIndicatorOffset = VOL_INDICATOR_W + SLIDE_MARGIN;
        volIndicatorStartMs = now;
        volIndicatorStayUntilMs = now + 1500;
        volIndicatorActive = true;
    }

    private static void render(GuiGraphicsExtractor graphics, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        MusicPlayerConfig cfg = MusicPlayerConfig.getInstance();

        // ALWAYS advance the animation clock so it doesn't freeze while a screen is
        // open (root cause of the "stuck / snap-away" bug). We only skip DRAWING
        // over an open screen — timing continues in the background.
        if (slideActive) {
            updateSlideAnimation();
        }
        if (volIndicatorActive) {
            updateVolumeIndicatorAnimation();
        }

        // Volume indicator renders even when a screen is open
        if (volIndicatorActive) {
            int screenW = mc.getWindow().getGuiScaledWidth();
            int x = screenW - SLIDE_MARGIN - VOL_INDICATOR_W + (int) volIndicatorOffset;
            int y = SLIDE_MARGIN;
            ColorTheme th = ColorTheme.get(cfg.getThemeId());
            renderVolumeIndicator(graphics, mc, th, x, y);
            return;
        }

        if (mc.gui.screen() != null) return;

        MusicPlayerManager mgr = MusicPlayerManager.getInstance();
        boolean isMc = mgr.getCurrentSource() == MusicPlayerManager.SongSource.MINECRAFT;
        boolean playing, paused;
        if (isMc) {
            playing = VanillaMusicPlayer.getInstance().isPlaying();
            paused = VanillaMusicPlayer.getInstance().isPaused();
        } else {
            playing = mgr.getEngine().isActive() && !mgr.getEngine().isPaused();
            paused = mgr.getEngine().isPaused();
        }

        // Notification slide mode (replaces the old widget)
        if (slideActive) {
            if (!slideActive) return;
            int screenW = mc.getWindow().getGuiScaledWidth();
            int x = screenW - SLIDE_MARGIN - SLIDE_PANEL_W + (int) slideOffset;
            int y = SLIDE_MARGIN;
            renderSlidePanel(graphics, mc, mgr, cfg, isMc, x, y, playing, paused);
            return;
        }

        // Persistent mode (manual toggle in settings)
        if (!cfg.isMiniPlayerEnabled()) return;
        if (!playing && !paused) return;

        boolean compact = cfg.getMiniPlayerStyle() == MiniPlayerStyle.COMPACT;

        long windowHandle = mc.getWindow().handle();
        var win = mc.getWindow();
        double mouseX = mc.mouseHandler.xpos() * win.getGuiScaledWidth() / win.getScreenWidth();
        double mouseY = mc.mouseHandler.ypos() * win.getGuiScaledHeight() / win.getScreenHeight();
        boolean mouseDown = glfwGetMouseButton(windowHandle, GLFW_MOUSE_BUTTON_LEFT) == GLFW_PRESS;

        int x = cfg.getMiniPlayerX();
        int y = cfg.getMiniPlayerY();
        int w = compact ? 180 : cfg.getMiniPlayerWidth();
        int totalH = compact ? 60 : cfg.getMiniPlayerHeight();

        if (mouseDown) {
            if (!mouseWasDown) {
                if (mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + 20) {
                    dragging = true;
                    dragOffX = (int) mouseX - x;
                    dragOffY = (int) mouseY - y;
                }
                if (!compact && mouseX >= x + w - 10 && mouseX <= x + w && mouseY >= y + totalH - 10 && mouseY <= y + totalH) {
                    resizing = true;
                }
            }
        } else {
            dragging = false;
            resizing = false;
        }
        mouseWasDown = mouseDown;

        if (dragging) {
            x = Math.max(0, (int) mouseX - dragOffX);
            y = Math.max(0, (int) mouseY - dragOffY);
            cfg.setMiniPlayerX(x);
            cfg.setMiniPlayerY(y);
        }

        if (!compact && resizing) {
            int nw = Math.max(100, (int) mouseX - x);
            int nh = Math.max(120, (int) mouseY - y);
            cfg.setMiniPlayerWidth(nw);
            cfg.setMiniPlayerHeight(nh);
            w = nw;
            totalH = nh;
        }

        ColorTheme th = ColorTheme.get(cfg.getThemeId());
        int screenW = mc.getWindow().getGuiScaledWidth();
        int screenH = mc.getWindow().getGuiScaledHeight();

        x = Math.min(x, screenW - w);
        y = Math.min(y, screenH - totalH);

        graphics.fill(x, y, x + w, y + totalH, 0xFF1A1A1A);
        graphics.fill(x - 1, y - 1, x + w + 1, y, th.panelBorder());
        graphics.fill(x - 1, y - 1, x, y + totalH + 1, th.panelBorder());
        graphics.fill(x + w, y, x + w + 1, y + totalH + 1, th.panelBorder());
        graphics.fill(x, y + totalH, x + w + 1, y + totalH + 1, th.panelBorder());

        if (compact) {
            renderCompact(graphics, mc, mgr, cfg, th, isMc, x, y, w);
        } else {
            renderBig(graphics, mc, mgr, cfg, th, isMc, x, y, w, totalH);
        }
    }

    private static void updateSlideAnimation() {
        long now = monotonicMs();
        if (slidingIn) {
            long elapsed = now - slideStartMs;
            float t = Math.min(1f, (float) elapsed / SLIDE_DURATION_MS);
            slideOffset = (SLIDE_PANEL_W + SLIDE_MARGIN) * (1 - easeOutCubic(t));
            if (t >= 1f) {
                slideOffset = 0;
                slidingIn = false;
            }
        } else if (now >= slideStayUntilMs) {
            long elapsed = now - slideStayUntilMs;
            float t = Math.min(1f, (float) elapsed / SLIDE_DURATION_MS);
            slideOffset = (SLIDE_PANEL_W + SLIDE_MARGIN) * easeInCubic(t);
            if (t >= 1f) {
                slideActive = false;
                slideOffset = SLIDE_PANEL_W + SLIDE_MARGIN;
            }
        }
    }

    private static float easeOutCubic(float t) {
        return 1 - (float) Math.pow(1 - t, 3);
    }

    private static float easeInCubic(float t) {
        return t * t * t;
    }

    private static void updateVolumeIndicatorAnimation() {
        long now = monotonicMs();
        if (volIndicatorSlidingIn) {
            long elapsed = now - volIndicatorStartMs;
            float t = Math.min(1f, (float) elapsed / SLIDE_DURATION_MS);
            volIndicatorOffset = (VOL_INDICATOR_W + SLIDE_MARGIN) * (1 - easeOutCubic(t));
            if (t >= 1f) {
                volIndicatorOffset = 0;
                volIndicatorSlidingIn = false;
            }
        } else if (now >= volIndicatorStayUntilMs) {
            long elapsed = now - volIndicatorStayUntilMs;
            float t = Math.min(1f, (float) elapsed / SLIDE_DURATION_MS);
            volIndicatorOffset = (VOL_INDICATOR_W + SLIDE_MARGIN) * easeInCubic(t);
            if (t >= 1f) {
                volIndicatorActive = false;
                volIndicatorOffset = VOL_INDICATOR_W + SLIDE_MARGIN;
            }
        }
    }

    private static void renderVolumeIndicator(GuiGraphicsExtractor graphics, Minecraft mc, ColorTheme th, int x, int y) {
        int w = VOL_INDICATOR_W;
        int h = VOL_INDICATOR_H;

        graphics.fill(x, y, x + w, y + h, 0xFF1A1A1A);
        graphics.fill(x - 1, y - 1, x + w + 1, y, th.panelBorder());
        graphics.fill(x - 1, y - 1, x, y + h + 1, th.panelBorder());
        graphics.fill(x + w, y, x + w + 1, y + h + 1, th.panelBorder());
        graphics.fill(x, y + h, x + w + 1, y + h + 1, th.panelBorder());

        graphics.centeredText(mc.font, "Volume", x + w / 2, y + 4, th.dimText());

        int barX = x + 10;
        int barY = y + 20;
        int barW = w - 20;
        int barH = 8;
        int fillW = Math.round(volIndicatorValue * barW);

        graphics.fill(barX, barY, barX + barW, barY + barH, 0xFF444444);
        graphics.fill(barX, barY, barX + fillW, barY + barH, th.accent());

        String pct = Math.round(volIndicatorValue * 100) + "%";
        graphics.centeredText(mc.font, pct, x + w / 2, barY + barH + 3, th.text());
    }

    private static void renderSlidePanel(GuiGraphicsExtractor graphics, Minecraft mc, MusicPlayerManager mgr,
                                          MusicPlayerConfig cfg, boolean isMc,
                                          int x, int y, boolean playing, boolean paused) {
        ColorTheme th = ColorTheme.get(cfg.getThemeId());
        int w = SLIDE_PANEL_W;
        int totalH = SLIDE_PANEL_H;

        graphics.fill(x, y, x + w, y + totalH, 0xFF1A1A1A);
        graphics.fill(x - 1, y - 1, x + w + 1, y, th.panelBorder());
        graphics.fill(x - 1, y - 1, x, y + totalH + 1, th.panelBorder());
        graphics.fill(x + w, y, x + w + 1, y + totalH + 1, th.panelBorder());
        graphics.fill(x, y + totalH, x + w + 1, y + totalH + 1, th.panelBorder());

        int artSize = 40;
        int artX = x + 4;
        int artY = y + (totalH - artSize) / 2;

        String coverKey = null;
        if (isMc) {
            VanillaTrack vt = VanillaMusicPlayer.getInstance().getCurrentTrack();
            if (vt != null) coverKey = vt.coverKey();
        } else {
            var song = mgr.getCurrentSong();
            if (song != null) coverKey = "user_" + song.getTitle();
        }

        Identifier artId = coverKey != null ? AlbumArtManager.getBuiltInArt(coverKey) : null;
        if (artId == null && AlbumArtManager.getInstance().hasArt()) {
            artId = AlbumArtManager.ALBUM_ART_ID;
        }
        if (artId == null) {
            artId = AlbumArtManager.DEFAULT_ALBUM_ART;
        }
        if (artId != null) {
            graphics.blit(artId, artX, artY, artX + artSize, artY + artSize,
                0.0f, 1.0f, 0.0f, 1.0f);
        } else {
            graphics.fill(artX, artY, artX + artSize, artY + artSize, th.panelBg());
            graphics.centeredText(mc.font, "\u266B", artX + artSize / 2, artY + artSize / 2 - 4, th.dimText());
        }

        int textX = artX + artSize + 6;
        int textRelX = textX - x;
        int maxTextW = w - textRelX - 6;
        String title = "Unknown";
        String artist = "";
        if (isMc) {
            VanillaTrack vt = VanillaMusicPlayer.getInstance().getCurrentTrack();
            if (vt != null) { title = vt.title(); artist = vt.artist(); }
        } else {
            var song = mgr.getCurrentSong();
            if (song != null) { title = song.getTitle(); artist = song.getArtist(); }
        }
        if (mc.font.width(title) > maxTextW) title = mc.font.plainSubstrByWidth(title, maxTextW - 4) + "...";
        graphics.text(mc.font, title, textX, artY, th.text(), true);
        if (artist != null && !artist.isEmpty()) {
            if (mc.font.width(artist) > maxTextW) artist = mc.font.plainSubstrByWidth(artist, maxTextW - 4) + "...";
            graphics.text(mc.font, artist, textX, artY + 10, th.dimText(), true);
        }

        int barY = y + 32;
        int barX = textX;
        int barW = w - textRelX - 4;
        int barH = 2;

        graphics.fill(barX, barY, barX + barW, barY + barH, 0xFF444444);
        float progress = mgr.getProgress();
        int fillW = Math.round(progress * barW);
        if (fillW > 0) {
            graphics.fill(barX, barY, barX + fillW, barY + barH, th.accent());
        }

        long posMs = isMc ? VanillaMusicPlayer.getInstance().getElapsedMs() : mgr.getEngine().getPositionMs();
        long durMs = isMc ? (VanillaMusicPlayer.getInstance().getCurrentTrack() != null
            ? VanillaMusicPlayer.getInstance().getCurrentTrack().durationSeconds() * 1000L : 0)
            : mgr.getEngine().getDurationMs();
        String posStr = MusicPlayerManager.formatTime(posMs);
        String durStr = MusicPlayerManager.formatTime(durMs);
        graphics.text(mc.font, posStr, barX, barY + barH + 3, th.dimText(), true);
        graphics.text(mc.font, durStr, barX + barW - mc.font.width(durStr), barY + barH + 3, th.dimText(), true);
    }

    private static void renderCompact(GuiGraphicsExtractor graphics, Minecraft mc, MusicPlayerManager mgr,
                                       MusicPlayerConfig cfg, ColorTheme th, boolean isMc,
                                       int x, int y, int w) {
        int artSize = 40;
        int artX = x + 4;
        int artY = y + (60 - artSize) / 2;

        String coverKey = null;
        if (isMc) {
            VanillaTrack vt = VanillaMusicPlayer.getInstance().getCurrentTrack();
            if (vt != null) coverKey = vt.coverKey();
        } else {
            var song = mgr.getCurrentSong();
            if (song != null) coverKey = "user_" + song.getTitle();
        }

        Identifier artId = coverKey != null ? AlbumArtManager.getBuiltInArt(coverKey) : null;
        if (artId == null && AlbumArtManager.getInstance().hasArt()) {
            artId = AlbumArtManager.ALBUM_ART_ID;
        }
        if (artId == null) {
            artId = AlbumArtManager.DEFAULT_ALBUM_ART;
        }
        if (artId != null) {
            graphics.blit(artId, artX, artY, artX + artSize, artY + artSize,
                0.0f, 1.0f, 0.0f, 1.0f);
        } else {
            graphics.fill(artX, artY, artX + artSize, artY + artSize, th.panelBg());
            graphics.centeredText(mc.font, "\u266B", artX + artSize / 2, artY + artSize / 2 - 4, th.dimText());
        }

        int textX = artX + artSize + 6;
        int textRelX = textX - x;
        int maxTextW = w - textRelX - 6;
        String title = "Unknown";
        String artist = "";
        if (isMc) {
            VanillaTrack vt = VanillaMusicPlayer.getInstance().getCurrentTrack();
            if (vt != null) { title = vt.title(); artist = vt.artist(); }
        } else {
            var song = mgr.getCurrentSong();
            if (song != null) { title = song.getTitle(); artist = song.getArtist(); }
        }
        if (mc.font.width(title) > maxTextW) title = mc.font.plainSubstrByWidth(title, maxTextW - 4) + "...";
        graphics.text(mc.font, title, textX, artY, th.text(), true);
        if (artist != null && !artist.isEmpty()) {
            if (mc.font.width(artist) > maxTextW) artist = mc.font.plainSubstrByWidth(artist, maxTextW - 4) + "...";
            graphics.text(mc.font, artist, textX, artY + 10, th.dimText(), true);
        }

        int barY = y + 32;
        int barX = textX;
        int barW = w - textRelX - 4;
        int barH = 2;

        graphics.fill(barX, barY, barX + barW, barY + barH, 0xFF444444);
        float progress = mgr.getProgress();
        int fillW = Math.round(progress * barW);
        if (fillW > 0) {
            graphics.fill(barX, barY, barX + fillW, barY + barH, th.accent());
        }

        long posMs = isMc ? VanillaMusicPlayer.getInstance().getElapsedMs() : mgr.getEngine().getPositionMs();
        long durMs = isMc ? (VanillaMusicPlayer.getInstance().getCurrentTrack() != null
            ? VanillaMusicPlayer.getInstance().getCurrentTrack().durationSeconds() * 1000L : 0)
            : mgr.getEngine().getDurationMs();
        String posStr = MusicPlayerManager.formatTime(posMs);
        String durStr = MusicPlayerManager.formatTime(durMs);
        graphics.text(mc.font, posStr, barX, barY + barH + 3, th.dimText(), true);
        graphics.text(mc.font, durStr, barX + barW - mc.font.width(durStr), barY + barH + 3, th.dimText(), true);
    }

    private static void renderBig(GuiGraphicsExtractor graphics, Minecraft mc, MusicPlayerManager mgr,
                                   MusicPlayerConfig cfg, ColorTheme th, boolean isMc,
                                   int x, int y, int w, int totalH) {
        int artSize = w;
        String coverKey = null;
        if (isMc) {
            VanillaTrack vt = VanillaMusicPlayer.getInstance().getCurrentTrack();
            if (vt != null) coverKey = vt.coverKey();
        } else {
            var song = mgr.getCurrentSong();
            if (song != null) coverKey = "user_" + song.getTitle();
        }

        Identifier artId = coverKey != null ? AlbumArtManager.getBuiltInArt(coverKey) : null;
        if (artId == null && AlbumArtManager.getInstance().hasArt()) {
            artId = AlbumArtManager.ALBUM_ART_ID;
        }
        if (artId == null) {
            artId = AlbumArtManager.DEFAULT_ALBUM_ART;
        }
        if (artId != null) {
            graphics.blit(artId, x, y, x + artSize, y + artSize,
                0.0f, 1.0f, 0.0f, 1.0f);
        } else {
            graphics.fill(x, y, x + artSize, y + artSize, th.panelBg());
            graphics.centeredText(mc.font, "\u266B", x + artSize / 2, y + artSize / 2 - 4, th.dimText());
        }

        int barY = y + artSize;
        int barH = 4;
        graphics.fill(x, barY, x + w, barY + barH, 0xFF444444);
        float progress = mgr.getProgress();
        int fillW = Math.round(progress * w);
        if (fillW > 0) {
            graphics.fill(x, barY, x + fillW, barY + barH, th.accent());
        }

        long posMs = isMc ? VanillaMusicPlayer.getInstance().getElapsedMs() : mgr.getEngine().getPositionMs();
        long durMs = isMc ? (VanillaMusicPlayer.getInstance().getCurrentTrack() != null
            ? VanillaMusicPlayer.getInstance().getCurrentTrack().durationSeconds() * 1000L : 0)
            : mgr.getEngine().getDurationMs();
        String posStr = MusicPlayerManager.formatTime(posMs);
        String durStr = MusicPlayerManager.formatTime(durMs);
        graphics.text(mc.font, posStr, x + 2, barY + barH + 1, th.dimText(), true);
        graphics.text(mc.font, durStr, x + w - mc.font.width(durStr) - 2, barY + barH + 1, th.dimText(), true);

        int ctrlY = barY + barH + 12;
        int ctrlH = totalH - artSize - barH - 12;
        if (ctrlH < 20) ctrlH = 20;

        int btnSize = Math.min(20, ctrlH);
        int totalBtnW = btnSize * 3 + 4;
        int btnStartX = x + (w - totalBtnW) / 2;
        int btnCenterY = ctrlY + (ctrlH - btnSize) / 2;

        graphics.fill(btnStartX, btnCenterY, btnStartX + btnSize, btnCenterY + btnSize, th.panelBorder());
        graphics.centeredText(mc.font, "\u23EE", btnStartX + btnSize / 2, btnCenterY + 4, th.text());

        boolean paused = isMc ? VanillaMusicPlayer.getInstance().isPaused() : mgr.getEngine().isPaused();
        String ppIcon = paused ? "\u25B6" : "\u23F8";
        int ppX = btnStartX + btnSize + 2;
        graphics.fill(ppX, btnCenterY, ppX + btnSize, btnCenterY + btnSize, th.accent());
        graphics.centeredText(mc.font, ppIcon, ppX + btnSize / 2, btnCenterY + 4, 0xFFFFFFFF);

        int nextX = ppX + btnSize + 2;
        graphics.fill(nextX, btnCenterY, nextX + btnSize, btnCenterY + btnSize, th.panelBorder());
        graphics.centeredText(mc.font, "\u23ED", nextX + btnSize / 2, btnCenterY + 4, th.text());
    }
}
