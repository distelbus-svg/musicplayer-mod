package com.musicplayer.ore;

import com.musicplayer.port.*;
import com.musicplayer.ui.*;

import com.musicplayer.MusicPlayerManager;
import com.musicplayer.SongInfo;
import com.musicplayer.VanillaTrack;
import com.musicplayer.config.MusicPlayerConfig;

/**
 * In-game now-playing widgets in the OreUI look (design file Widgets.dc.html). Sizes are the design's
 * pixels halved, because the HUD is drawn in GUI-scaled pixels (about 640x360 at the design's scale).
 */
public final class OreHud {

    private static final int[][] ICON_HEART = {{1, 1, 2, 1}, {5, 1, 2, 1}, {0, 2, 8, 2}, {1, 4, 6, 1}, {2, 5, 4, 1}, {3, 6, 2, 1}};

    private OreHud() { }

    public static final int COMPACT_W = 220, COMPACT_H = 68;

    /** Height of the expanded widget for a given width. */
    public static int bigHeight(int w) {
        return (w - 16) + 70;
    }

    // ------------------------------------------------------------------ data

    private record Info(String title, String artist, String path, String badge, boolean yt, String art,
                        float progress, long posMs, long durMs, boolean paused, boolean liked) { }

    private static Info info(MusicPlayerManager mgr, boolean isMc) {
        String title = "Unknown", artist = "", path = "", coverKey = null, badge = "Local file";
        boolean yt = false;
        if (isMc) {
            VanillaTrack vt = Ports.vanilla().getCurrentTrack();
            if (vt != null) { title = vt.title(); artist = vt.artist(); coverKey = vt.coverKey(); path = vt.id(); }
            badge = "Minecraft";
        } else {
            SongInfo song = mgr.getCurrentSong();
            if (song != null) {
                title = song.getTitle();
                artist = song.getArtist();
                path = song.getPath();
                coverKey = "user_" + song.getTitle();
                yt = song.getSource() == SongInfo.SourceType.YOUTUBE;
                if (yt) badge = "YouTube";
            }
        }
        String art = coverKey != null ? Ports.art().builtInArt(coverKey) : null;
        if (art == null && Ports.art().hasArt()) art = Ports.art().albumArtId();
        if (art == null) art = Ports.art().defaultArt();
        long pos = isMc ? Ports.vanilla().getElapsedMs() : mgr.getEngine().getPositionMs();
        long dur = isMc ? (Ports.vanilla().getCurrentTrack() != null
                ? Ports.vanilla().getCurrentTrack().durationSeconds() * 1000L : 0)
                : mgr.getEngine().getDurationMs();
        boolean paused = isMc ? Ports.vanilla().isPaused() : mgr.getEngine().isPaused();
        MusicPlayerConfig cfg = MusicPlayerConfig.getInstance();
        boolean liked = path != null && !path.isEmpty() && cfg.isLiked(path);
        return new Info(title, artist == null ? "" : artist, path, badge, yt, art, mgr.getProgress(), pos, dur, paused, liked);
    }

    // ------------------------------------------------------------------ widgets

    /** The compact widget: art on the left, title / source / progress on the right. */
    public static void compact(Gfx g, MusicPlayerManager mgr, boolean isMc,
                               int x, int y, int w, int h) {
        OreTheme th = OreTheme.of(MusicPlayerConfig.getInstance().getThemeId());
        Info in = info(mgr, isMc);
        frame(g, th, x, y, w, h);
        int pad = 8;
        int art = h - 2 * pad;
        art(g, th, in.art, x + pad, y + pad, art);
        int tx = x + pad + art + pad;
        int tr = x + w - pad;

        int titleMax = tr - tx - (in.paused ? 0 : 12);
        String title = fit(g, in.title, titleMax, true);
        g.text(title, true, tx, y + pad, th.text(), !th.light());
        if (!in.paused) eq(g, mgr, isMc, tx + width(g, title, true) + 4, y + pad);
        badgeRow(g, th, in, tx, y + pad + 12, tr - tx);
        bar(g, th, in, tx, y + h - pad - 9 - 3 - 6, tr - tx, false);
    }

    /** The expanded widget: big art, title with like state, progress. {@code w} is the full width. */
    public static void big(Gfx g, MusicPlayerManager mgr, boolean isMc,
                           int x, int y, int w) {
        OreTheme th = OreTheme.of(MusicPlayerConfig.getInstance().getThemeId());
        Info in = info(mgr, isMc);
        int h = bigHeight(w);
        frame(g, th, x, y, w, h);
        int pad = 8;
        int art = w - 2 * pad;
        art(g, th, in.art, x + pad, y + pad, art);
        int ry = y + pad + art + 7;
        int heartW = 16;
        int tr = x + w - pad;
        String title = fit(g, in.title, tr - (x + pad) - heartW - 6, true);
        g.text(title, true, x + pad, ry, th.text(), !th.light());
        badgeRow(g, th, in, x + pad, ry + 12, tr - (x + pad) - heartW - 6);
        heart(g, tr - heartW, ry + 3, 2, in.liked ? th.like() : th.dim());
        bar(g, th, in, x + pad, ry + 22 + 7, w - 2 * pad, true);
    }

    // ------------------------------------------------------------------ parts

    private static void frame(Gfx g, OreTheme th, int x, int y, int w, int h) {
        g.fill(x, y + 3, x + w, y + h + 3, 0x40000000); // drop shadow
        g.fill(x, y, x + w, y + h, th.edge());
        g.fill(x + 2, y + 2, x + w - 2, y + h - 2, th.card());
        g.fill(x + 2, y + 2, x + w - 2, y + 3, th.hi());
        g.fill(x + 2, y + h - 4, x + w - 2, y + h - 2, th.cardLip());
    }

    private static void art(Gfx g, OreTheme th, String id, int x, int y, int size) {
        g.fill(x - 1, y - 1, x + size + 1, y + size + 1, th.hi());
        g.fill(x, y, x + size, y + size, th.edge());
        if (id != null) {
            g.blit(id, x + 2, y + 2, x + size - 2, y + size - 2, 0f, 1f, 0f, 1f);
        } else {
            g.fill(x + 2, y + 2, x + size - 2, y + size - 2, th.inset());
        }
    }

    private static void badgeRow(Gfx g, OreTheme th, Info in, int x, int y, int maxW) {
        int bw = g.textWidth(in.badge, false) + 8;
        g.fill(x, y, x + bw, y + 11, th.edge());
        g.fill(x + 1, y + 1, x + bw - 1, y + 10, th.inset());
        g.text(in.badge, false, x + 4, y + 2, in.yt ? th.like() : th.text(), false);
        if (!in.artist.isEmpty() && maxW - bw - 6 > 16) {
            g.text(fit(g, in.artist, maxW - bw - 6, false), false, x + bw + 6, y + 2, th.dim(), !th.light());
        }
    }

    /** Progress track (6px) with the times underneath. */
    private static void bar(Gfx g, OreTheme th, Info in, int x, int y, int w, boolean handle) {
        g.fill(x, y, x + w, y + 6, th.edge());
        g.fill(x + 1, y + 1, x + w - 1, y + 5, th.inset());
        int fw = Math.round((w - 2) * Math.max(0f, Math.min(1f, in.progress)));
        if (fw > 0) {
            g.fill(x + 1, y + 1, x + 1 + fw, y + 5, OreTheme.GREEN);
            g.fill(x + 1, y + 1, x + 1 + fw, y + 2, OreTheme.GREEN_HI);
            g.fill(x + 1, y + 4, x + 1 + fw, y + 5, OreTheme.GREEN_LIP);
        }
        if (handle) {
            int hx = Math.max(x, Math.min(x + w - 6, x + 1 + fw - 3));
            g.fill(hx, y - 2, hx + 6, y + 8, th.edge());
            g.fill(hx + 1, y - 1, hx + 5, y + 7, OreTheme.LIGHT_FACE);
            g.fill(hx + 1, y - 1, hx + 5, y, OreTheme.LIGHT_HI);
            g.fill(hx + 1, y + 6, hx + 5, y + 7, OreTheme.LIGHT_LIP);
        }
        String pos = MusicPlayerManager.formatTime(in.posMs), dur = MusicPlayerManager.formatTime(in.durMs);
        g.text(pos, false, x, y + 9, th.dim(), !th.light());
        g.text(dur, false, x + w - g.textWidth(dur, false), y + 9, th.dim(), !th.light());
    }

    private static final float[] RAW = new float[32];
    private static final float[] EQ = new float[4];

    /** Four live bars driven by the audio, easing up fast and down slowly. */
    private static void eq(Gfx g, MusicPlayerManager mgr, boolean isMc, int x, int y) {
        boolean has;
        if (isMc) {
            has = Ports.soundtrack().getVisualizerBars(RAW, 32);
        } else {
            mgr.getEngine().getVisualizerBars(RAW, 32);
            has = true;
        }
        int per = RAW.length / EQ.length;
        int c = OreTheme.of(MusicPlayerConfig.getInstance().getThemeId()).accent();
        for (int i = 0; i < EQ.length; i++) {
            float v = 0;
            if (has) {
                for (int k = 0; k < per; k++) v = Math.max(v, RAW[i * per + k]);
                v = Math.min(1f, v * 1.6f);
            }
            EQ[i] += (v - EQ[i]) * (v > EQ[i] ? 0.5f : 0.15f);
            int h = Math.max(1, Math.round(EQ[i] * 9));
            g.fill(x + i * 3, y + 9 - h, x + i * 3 + 2, y + 9, c);
        }
    }

    private static void heart(Gfx g, int x, int y, int u, int color) {
        for (int[] r : ICON_HEART) g.fill(x + r[0] * u, y + r[1] * u, x + (r[0] + r[2]) * u, y + (r[1] + r[3]) * u, color);
    }

    private static int width(Gfx g, String s, boolean bold) {
        return g.textWidth(s, bold);
    }

    private static String fit(Gfx g, String s, int maxW, boolean bold) {
        if (width(g, s, bold) <= maxW) return s;
        String t = s;
        while (!t.isEmpty() && width(g, t + "...", bold) > maxW) t = t.substring(0, t.length() - 1);
        return t + "...";
    }
}
