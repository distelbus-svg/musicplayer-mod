package com.musicplayer.hud;

import com.musicplayer.port.*;
import com.musicplayer.ui.*;

import com.musicplayer.MusicPlayerManager;
import com.musicplayer.SongInfo;

public class NowPlayingHud {
    private static NowPlayingHud instance;

    private static final long DISPLAY_MS = 4000;
    private static final long FADE_IN_MS = 200;
    private static final long FADE_OUT_MS = 500;

    private String currentTitle = "";
    private String currentSubtitle = "";
    private long showStartedMs = 0;
    private boolean active = false;

    private NowPlayingHud() {
        UiHost.get().registerHud("now_playing_hud", this::render);
    }

    public static void init() {
        if (instance == null) {
            instance = new NowPlayingHud();
        }
    }

    public static void show(SongInfo song) {
        show(song.getTitle() != null ? song.getTitle() : "Unknown",
             song.getArtist() != null ? song.getArtist() : "");
    }

    public static void show(String title, String subtitle) {
        if (instance == null) return;
        instance.currentTitle = title != null ? title : "Unknown";
        instance.currentSubtitle = subtitle != null ? subtitle : "";
        instance.showStartedMs = System.currentTimeMillis();
        instance.active = true;
    }

    private void render(Gfx graphics, Float delta) {
        if (!active) return;

        if (UiHost.get().classicScreenOpen()) return;

        long elapsed = System.currentTimeMillis() - showStartedMs;
        if (elapsed > DISPLAY_MS) {
            active = false;
            return;
        }

        float alpha = 1.0f;
        if (elapsed < FADE_IN_MS) {
            alpha = (float) elapsed / FADE_IN_MS;
        } else if (elapsed > DISPLAY_MS - FADE_OUT_MS) {
            alpha = (float) (DISPLAY_MS - elapsed) / FADE_OUT_MS;
        }

        MusicPlayerManager manager = MusicPlayerManager.getInstance();
        boolean isMc = manager.getCurrentSource() == MusicPlayerManager.SongSource.MINECRAFT;
        long pos;
        long dur;
        if (isMc) {
            VanillaPort vp = Ports.vanilla();
            pos = vp.getElapsedMs();
            com.musicplayer.VanillaTrack vt = vp.getCurrentTrack();
            dur = vt != null ? vt.durationSeconds() * 1000L : 0;
        } else {
            pos = manager.getEngine().getPositionMs();
            dur = manager.getEngine().getDurationMs();
        }
        String posStr = formatTime(pos);
        String durStr = dur > 0 ? formatTime(dur) : "?:??";

        String title = currentTitle;
        int maxTextWidth = 320;
        String text = title + " \u2014 " + posStr + " / " + durStr;
        while (graphics.textWidth(text, false) > maxTextWidth && title.length() > 2) {
            title = title.substring(0, title.length() - 2) + "..";
            text = title + " \u2014 " + posStr + " / " + durStr;
        }

        int screenWidth = graphics.guiWidth();
        int screenHeight = graphics.guiHeight();
        int x = screenWidth / 2;
        int y = screenHeight - 60;

        int color = (int) (alpha * 255) << 24 | 0x00FFFFFF;
        graphics.text(text, false, x - graphics.textWidth(text, false) / 2, y, color, true);
        if (!currentSubtitle.isEmpty()) {
            int subColor = (int) (alpha * 180) << 24 | 0x00AAAAAA;
            graphics.text(currentSubtitle, false, x - graphics.textWidth(currentSubtitle, false) / 2, y + 10, subColor, true);
        }
    }

    private static String formatTime(long ms) {
        if (ms < 0) ms = 0;
        long totalSeconds = ms / 1000;
        long minutes = totalSeconds / 60;
        long seconds = totalSeconds % 60;
        return String.format("%d:%02d", minutes, seconds);
    }
}
