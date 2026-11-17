package com.musicplayer.hud;

import com.musicplayer.port.*;
import com.musicplayer.ui.*;

import com.musicplayer.AudioEngine;
import com.musicplayer.MusicPlayerManager;
import com.musicplayer.config.MusicPlayerConfig;
import com.musicplayer.theme.ColorTheme;

public class AudioVisualizerHud {

    private static boolean registered;
    private static final float[] BARS = new float[32];

    public static void init() {
        if (!registered) {
            registered = true;
            UiHost.get().registerHud("visualizer", AudioVisualizerHud::render);
        }
    }

    private static void render(Gfx graphics, Float delta) {
        MusicPlayerConfig cfg = MusicPlayerConfig.getInstance();
        if (!cfg.isVisualizerEnabled()) return;
        if (graphics.screenOpen()) return;

        MusicPlayerManager mgr = MusicPlayerManager.getInstance();
        boolean isMc = mgr.getCurrentSource() == MusicPlayerManager.SongSource.MINECRAFT;
        boolean playing = isMc
            ? Ports.vanilla().isPlaying()
            : mgr.getEngine().isActive() && !mgr.getEngine().isPaused();
        if (!playing) return;

        float[] bars = BARS;
        boolean hasData = false;

        if (isMc) {
            SoundtrackPort ssm = Ports.soundtrack();
            hasData = ssm.getVisualizerBars(bars, 32);
        } else {
            AudioEngine engine = mgr.getEngine();
            engine.getVisualizerBars(bars, 32);
            for (float b : bars) if (b > 0.01f) { hasData = true; break; }
        }
        if (!hasData) return;

        int screenWidth = graphics.guiWidth();
        int screenHeight = graphics.guiHeight();

        int barW = 3;
        int barGap = 2;
        int totalW = bars.length * (barW + barGap) - barGap;
        int x = (screenWidth - totalW) / 2;
        int y = screenHeight - 50;
        int maxH = 25;

        ColorTheme th = ColorTheme.get(cfg.getThemeId());
        int color = th.accent();

        for (int i = 0; i < bars.length; i++) {
            int h = Math.round(bars[i] * maxH);
            if (h > 0) {
                graphics.fill(x + i * (barW + barGap), y - h, x + i * (barW + barGap) + barW, y, color);
            }
        }
    }
}
