package com.musicplayer.hud;

import com.musicplayer.AudioEngine;
import com.musicplayer.MusicPlayerManager;
import com.musicplayer.VanillaMusicPlayer;
import com.musicplayer.config.MusicPlayerConfig;
import com.musicplayer.player.SoundtrackStateManager;
import com.musicplayer.theme.ColorTheme;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;

public class AudioVisualizerHud {

    private static boolean registered;

    public static void init() {
        if (!registered) {
            registered = true;
            HudElementRegistry.addLast(
                Identifier.fromNamespaceAndPath("musicplayer", "visualizer"),
                AudioVisualizerHud::render
            );
        }
    }

    private static void render(GuiGraphicsExtractor graphics, DeltaTracker delta) {
        MusicPlayerConfig cfg = MusicPlayerConfig.getInstance();
        if (!cfg.isVisualizerEnabled()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.screen != null) return;

        MusicPlayerManager mgr = MusicPlayerManager.getInstance();
        boolean isMc = mgr.getCurrentSource() == MusicPlayerManager.SongSource.MINECRAFT;
        boolean playing = isMc
            ? VanillaMusicPlayer.getInstance().isPlaying()
            : mgr.getEngine().isActive() && !mgr.getEngine().isPaused();
        if (!playing) return;

        float[] bars = new float[32];
        boolean hasData = false;

        if (isMc) {
            SoundtrackStateManager ssm = SoundtrackStateManager.getInstance();
            hasData = ssm.getVisualizerBars(bars, 32);
        } else {
            AudioEngine engine = mgr.getEngine();
            engine.getVisualizerBars(bars, 32);
            for (float b : bars) if (b > 0.01f) { hasData = true; break; }
        }
        if (!hasData) return;

        int screenWidth = mc.getWindow().getGuiScaledWidth();
        int screenHeight = mc.getWindow().getGuiScaledHeight();

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
