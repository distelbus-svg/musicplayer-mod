package com.musicplayer.ore;

import com.musicplayer.MusicPlayerManager;
import com.musicplayer.MusicPlayerScreen;
import com.musicplayer.Playlist;
import com.musicplayer.PlaylistListScreen;
import com.musicplayer.PlaylistManager;
import com.musicplayer.SongInfo;
import com.musicplayer.UpNextScreen;
import com.musicplayer.VanillaTrack;
import com.musicplayer.config.MusicPlayerConfig;
import com.musicplayer.config.MusicPlayerConfig.PlaybackMode;
import com.musicplayer.player.SoundtrackRegistry;
import com.musicplayer.stats.ListeningStatisticsManager;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Home screen, from the design's Home.dc.html. */
public class OreHomeScreen extends OreScreen {

    private static final float[] SPEEDS = {0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f};
    // {top, right, alpha-kind} for the squares in the now-playing card corner (a=.18, b=.07, c=.13 dark)
    private static final float[][] DECO_DARK = {
            {0, 0, .18f}, {0, 64, .07f}, {0, 96, .13f}, {0, 160, .07f}, {32, 32, .13f},
            {32, 96, .07f}, {32, 128, .13f}, {64, 0, .07f}, {64, 64, .07f}};
    private static final float[][] DECO_LIGHT = {
            {0, 0, .16f}, {0, 64, .06f}, {0, 96, .11f}, {0, 160, .06f}, {32, 32, .11f},
            {32, 96, .06f}, {32, 128, .11f}, {64, 0, .06f}, {64, 64, .06f}};

    public OreHomeScreen() {
        super("Melodix");
    }

    @Override
    protected int activeTab() { return 0; }

    @Override
    protected void renderBody(GuiGraphicsExtractor g, int mx, int my) {
        MusicPlayerConfig cfg = manager.getConfig();
        Now n = now();
        int left = px(32);
        int right = this.width - px(32);

        // ---- greeting row
        int gy = px(88);
        String who = this.minecraft.getUser().getName();
        String subtitle = n.active ? "Your session is going strong. Keep the music coming."
                : "Welcome back. Your last session is ready to resume.";

        ListeningStatisticsManager st = ListeningStatisticsManager.getInstance();
        String[][] stats = {
                {"Session", hours(st.getSessionListeningTimeSeconds())},
                {"All-time", hours(st.getTotalListeningTimeSeconds())},
                {"Most played", mostPlayed(st)}};
        int sx = right;
        for (int i = stats.length - 1; i >= 0; i--) {
            int w = Math.min(px(260), Math.max(px(150), Math.max(tw(stats[i][0], false, 16), tw(stats[i][1], true, 16)) + px(28)));
            sx -= w;
            tip("Open listening stats");
            boolean sh = hit(mx, my, sx, gy, w, px(56), () -> this.minecraft.gui.setScreen(new OreStatsScreen(this)));
            inset(g, sx, gy, w, px(56));
            if (sh) g.fill(sx + px(2), gy + px(2), sx + w - px(2), gy + px(54), th.rowHover());
            text(g, stats[i][0], sx + px(14), gy + px(8), th.dim(), false, 16);
            text(g, fit(stats[i][1], w - px(28), true, 16), sx + px(14), gy + px(32), th.text(), true, 16);
            sx -= px(10);
        }
        int greetMaxW = sx - left;
        text(g, fit(greeting() + ", " + who, greetMaxW, true, 24), left, gy + px(6), th.text(), true, 24);
        text(g, fit(subtitle, greetMaxW, false, 16), left, gy + px(36), th.dim(), false, 16);

        // ---- now playing card
        int cy = px(164), ch = px(312), cw = px(760);
        card(g, left, cy, cw, ch);
        int b2 = px(2);
        hGradient(g, left + b2, cy + b2, (int) ((cw - 2 * b2) * 0.72f), ch - 2 * b2 - px(4),
                th.light() ? 0x3C8527 : 0x3C8527, th.light() ? 0.20f : 0.42f);
        deco(g, left + cw - b2, cy + b2, th.light() ? 0x1F5A12 : 0x6CC349, th.light() ? DECO_LIGHT : DECO_DARK);

        int art = px(260), ax = left + px(24), ay = cy + px(24);
        g.fill(ax - px(6), ay - px(6), ax + art + px(6), ay + art + px(6), th.hi());
        g.fill(ax - px(4), ay - px(4), ax + art + px(4), ay + art + px(4), th.edge());
        g.blit(n.art, ax, ay, ax + art, ay + art, 0f, 1f, 0f, 1f);

        int x0 = ax + art + px(24);
        int colW = left + cw - px(24) - x0;
        int titleY = ay;
        if (!n.active) {
            int tagW = tw("Continue listening", false, 16) + px(20);
            button(g, mx, my, x0, ay, tagW, px(28), GREEN, null);
            textBtn(g, "Continue listening", false, 16, x0, ay, tagW, px(28), 0xFFFFFFFF);
            titleY = ay + px(40);
        }
        String title = n.title.isEmpty() ? "Nothing playing" : n.title;
        text(g, fit(title, colW, true, 32), x0, titleY, th.text(), true, 32);
        text(g, fit(n.sub, colW, false, 16), x0, titleY + px(41), th.dim(), false, 16);

        // progress
        // The column is laid out like the design's flex space-between: the optional "Continue listening"
        // tag makes the title block taller, so the gaps below it shrink instead of overlapping.
        float tb = n.active ? 59f : 99f;
        float gapD = (264f - (tb + 42f + 52f + 32f)) / 3f;
        int py = ay + px(tb + gapD);
        sliderTrack(g, x0, py, colW, px(16), n.progress(), f -> {
            if (n.active) manager.seek((float) f);
        });
        text(g, time(n.posMs), x0, py + px(26), th.dim(), false, 16);
        textRight(g, time(n.durMs), x0 + colW, py + px(26), th.dim(), false, 16);

        // transport (centre line of the 52px play button)
        int b = px(44), gap = px(8);
        int tcy = ay + px(tb + gapD + 42f + gapD) + px(26);
        int bty = tcy - b / 2;
        boolean playing = n.active && !n.paused;
        boolean mini = cfg.isMiniPlayerEnabled();
        tip("Mini player");
        button(g, mx, my, x0, bty, b, b, mini ? GREEN : NORMAL, () -> cfg.setMiniPlayerEnabled(!cfg.isMiniPlayerEnabled()));
        iconBtn(g, ICON_MINI, 12, 11, x0, bty, b, b, px(20), mini ? 0xFFFFFFFF : th.text());

        // mini on the far left, like on the far right, play cluster centred between them without overlapping either
        int lx = x0 + colW - b;
        int midL = x0 + b + gap, midR = lx - gap;
        int maxPlayW = (midR - midL) - b * 4 - gap * 4;
        int playW = n.active ? px(72) : Math.max(px(72), Math.min(px(148), maxPlayW));
        int clusterW = b * 4 + playW + gap * 4;
        int cx = midL + (midR - midL - clusterW) / 2;
        boolean shuffle = cfg.getPlaybackMode() == PlaybackMode.SHUFFLE;
        tip("Shuffle");
        button(g, mx, my, cx, bty, b, b, shuffle ? GREEN : NORMAL, manager::toggleShuffle);
        iconBtn(g, ICON_SHUFFLE, 10, 10, cx, bty, b, b, px(22), shuffle ? 0xFFFFFFFF : th.text());
        cx += b + gap;
        tip("Previous");
        button(g, mx, my, cx, bty, b, b, NORMAL, manager::previousTrack);
        iconBtn(g, ICON_PREV, 10, 10, cx, bty, b, b, px(20), th.text());
        cx += b + gap;
        Runnable toggle = () -> {
            if (manager.getCurrentSong() != null) manager.togglePlayPause();
            else manager.playSong(0);
        };
        int pty = tcy - px(26);
        if (playing) {
            tip("Pause");
            button(g, mx, my, cx, pty, playW, px(52), LIGHT, toggle);
            iconBtn(g, ICON_PAUSE, 10, 10, cx, pty, playW, px(52), px(24), 0xFF1E1E1F);
        } else {
            tip(n.active || n.song != null ? "Resume" : "Play");
            button(g, mx, my, cx, pty, playW, px(52), GREEN, toggle);
            if (n.active) {
                iconBtn(g, ICON_PLAY, 10, 10, cx, pty, playW, px(52), px(24), 0xFFFFFFFF);
            } else {
                String label = n.song != null ? "Resume" : "Play";
                int iw = px(22), contentW = iw + px(12) + tw(label, true, 16);
                boolean withIcon = contentW <= playW - px(16);
                int tx0 = cx + (playW - (withIcon ? contentW : tw(label, true, 16))) / 2;
                if (withIcon) {
                    icon(g, ICON_PLAY, 10, 10, tx0, pty + px(26) - px(2) - iw / 2 + pressDy, iw, 0xFFFFFFFF);
                    tx0 += iw + px(12);
                }
                text(g, label, tx0, pty + px(26) - px(2) - capH(16) / 2 + pressDy, 0xFFFFFFFF, true, 16);
            }
        }
        cx += playW + gap;
        tip("Next");
        button(g, mx, my, cx, bty, b, b, NORMAL, manager::nextTrack);
        iconBtn(g, ICON_NEXT, 10, 10, cx, bty, b, b, px(20), th.text());
        cx += b + gap;
        boolean rep = cfg.getPlaybackMode() == PlaybackMode.REPEAT_ALL || cfg.getPlaybackMode() == PlaybackMode.REPEAT_ONE;
        tip("Repeat");
        button(g, mx, my, cx, bty, b, b, rep ? GREEN : NORMAL, manager::toggleRepeat);
        iconBtn(g, ICON_REPEAT, 10, 10, cx, bty, b, b, px(22), rep ? 0xFFFFFFFF : th.text());
        if (cfg.getPlaybackMode() == PlaybackMode.REPEAT_ONE) {
            text(g, "1", cx + b - px(12) - tw("1", true, 12), bty + b - px(22), 0xFFFFFFFF, true, 12);
        }

        boolean liked = isLiked(n.path);
        tip(liked ? "Remove from liked songs" : "Like");
        button(g, mx, my, lx, bty, b, b, NORMAL, () -> toggleLike(n.path));
        iconBtn(g, ICON_HEART, 8, 7, lx, bty, b, b, px(22), liked ? th.like() : th.dim());

        // secondary: speed + volume
        int sy = ay + px(232);
        float speed = manager.getEngine().getPlaybackSpeed();
        String speedLabel = (speed == (int) speed ? String.format("%.1f", speed) : String.valueOf(speed)) + "×";
        // speed only applies to your own files; the Minecraft soundtrack engine can't change speed
        Runnable speedClick = n.mc ? null : () -> {
            float next = nextSpeed(manager.getEngine().getPlaybackSpeed());
            cfg.setPlaybackSpeed(next);
            manager.getEngine().setPlaybackSpeed(next);
        };
        tip(n.mc ? "Speed isn't available for Minecraft music" : "Playback speed");
        button(g, mx, my, x0, sy, px(68), px(32), NORMAL, speedClick);
        textBtn(g, speedLabel, false, 16, x0, sy, px(68), px(32), n.mc ? th.dim() : th.text());

        float vol = manager.getVolume();
        String pct = Math.round(vol * 100) + "%";
        int pctW = px(44);
        text(g, pct, x0 + colW - tw(pct, false, 16), sy + (px(32) - capH(16)) / 2, th.dim(), false, 16);
        int volW = px(120), volX = x0 + colW - pctW - px(12) - volW;
        sliderTrack(g, volX, sy + px(8), volW, px(16), vol, f -> manager.setVolume((float) f));
        icon(g, ICON_SPEAKER, 8, 8, volX - px(12) - px(20), sy + (px(32) - px(20)) / 2, px(20), th.dim());

        // ---- up next card
        int ux = left + cw + px(20), uw = right - ux;
        card(g, ux, cy, uw, ch);
        text(g, "Up next", ux + px(20), cy + px(20) + (px(28) - capH(20)) / 2, th.text(), true, 20);
        String mode = shuffle ? "Shuffle" : rep ? "Repeat" : "In order";
        int modeW = tw(mode, false, 16);
        int modeX = ux + uw - px(20) - modeW;
        text(g, mode, modeX, cy + px(20) + (px(28) - capH(16)) / 2, th.dim(), false, 16);
        if (shuffle) {
            icon(g, ICON_SHUFFLE, 10, 10, modeX - px(8) - px(16), cy + px(20) + (px(28) - px(16)) / 2, px(16), th.dim());
        }
        List<SongInfo> q = manager.getUpNextQueue(5);
        int ry = cy + px(58);
        for (int i = 0; i < 5; i++) {
            int rowY = ry + i * px(44);
            g.fill(ux + px(20), rowY, ux + uw - px(20), rowY + px(2), th.sep());
            if (i >= q.size()) continue;
            SongInfo si = q.get(i);
            boolean hov = hit(mx, my, ux + px(20), rowY + px(2), uw - px(40), px(42), () -> manager.getPlaylist().stream()
                    .filter(s2 -> s2.getPath().equals(si.getPath())).findFirst()
                    .ifPresent(s2 -> manager.playSong(manager.getPlaylist().indexOf(s2))));
            if (hov) g.fill(ux + px(20), rowY + px(2), ux + uw - px(20), rowY + px(44), th.rowHover());
            int rowMid = rowY + px(2) + px(42) / 2;
            text(g, "+" + (i + 1), ux + px(28), rowMid - capH(16) / 2, th.dim(), false, 16);
            int sq = px(28), tx = ux + px(28) + px(40);
            g.fill(tx, rowMid - sq / 2, tx + sq, rowMid + sq / 2, th.edge());
            g.blit(coverFor(si), tx + px(2), rowMid - sq / 2 + px(2), tx + sq - px(2), rowMid + sq / 2 - px(2), 0f, 1f, 0f, 1f);
            String dur = durationOf(si);
            int durW = dur.isEmpty() ? 0 : tw(dur, false, 16) + px(8);
            int textX = tx + sq + px(12);
            int maxW = ux + uw - px(28) - durW - textX;
            text(g, fit(si.getTitle(), maxW, false, 16), textX, rowMid - capH(16) - px(1), th.text(), false, 16);
            text(g, fit(si.getArtist(), maxW, false, 16), textX, rowMid + px(2), th.dim(), false, 16);
            if (!dur.isEmpty()) {
                text(g, dur, ux + uw - px(28) - tw(dur, false, 16), rowMid - capH(16) / 2, th.dim(), false, 16);
            }
        }
        String link = "Open full queue";
        int lw = tw(link, false, 16) + px(8) + px(16);
        int lyy = cy + ch - px(16) - px(24) - px(4);
        int lxx = ux + uw - px(20) - lw;
        boolean lh = hit(mx, my, lxx, lyy, lw, px(24), () -> this.minecraft.gui.setScreen(new OreQueueScreen(this)));
        text(g, link, lxx, lyy + (px(24) - capH(16)) / 2, th.accent(), false, 16);
        icon(g, ICON_ARROW, 9, 9, ux + uw - px(20) - px(16), lyy + (px(24) - px(16)) / 2, px(16), th.accent());
        if (lh) g.fill(lxx, lyy + (px(24) + capH(16)) / 2 + 1, lxx + lw - px(24), lyy + (px(24) + capH(16)) / 2 + 2, th.accent());

        // ---- shelf
        int shy = cy + ch + px(20);
        text(g, "Your music", left, shy + (px(28) - capH(20)) / 2, th.text(), true, 20);
        String open = "Open library";
        int ow = tw(open, false, 16) + px(8) + px(16);
        hit(mx, my, right - ow, shy, ow, px(28), this::openLibrary);
        text(g, open, right - ow, shy + (px(28) - capH(16)) / 2, th.accent(), false, 16);
        icon(g, ICON_ARROW, 9, 9, right - px(16), shy + (px(28) - px(16)) / 2, px(16), th.accent());

        renderShelf(g, mx, my, left, shy + px(38), right - left);
    }

    private void renderShelf(GuiGraphicsExtractor g, int mx, int my, int x, int y, int totalW) {
        record Tile(String name, String sub, int color, int[][] icon, int gw, int gh, int iconSize, Runnable action, boolean dashed) {}
        int libCount = (int) manager.getCachedEditAllSongs().stream()
                .filter(s -> s.getSource() != SongInfo.SourceType.VANILLA).count();
        int mcCount = SoundtrackRegistry.getInstance().getAllTracks().size();
        int liked = manager.buildLikedSongsPlaylist().getSongs().size();

        List<Tile> tiles = new ArrayList<>();
        tiles.add(new Tile("Music Library", libCount + " songs", OreTheme.GREEN, ICON_NOTE, 12, 12, 40, this::openLibrary, false));
        tiles.add(new Tile("Minecraft", mcCount + " tracks", 0xFF4A7FB5, ICON_FRAME, 12, 12, 40,
                () -> this.minecraft.gui.setScreen(new OreMinecraftScreen()), false));
        tiles.add(new Tile("Liked Songs", liked + " songs", 0xFFA63C3C, ICON_HEART, 8, 7, 44,
                () -> this.minecraft.gui.setScreen(new OrePlaylistScreen(manager.buildLikedSongsPlaylist(), true).from(this)), false));
        int[] pc = {0xFFC97A3A, 0xFF4A4F9E};
        List<Playlist> pls = PlaylistManager.getInstance().getPlaylists();
        for (int i = 0; i < Math.min(2, pls.size()); i++) {
            Playlist p = pls.get(i);
            tiles.add(new Tile(p.getName(), p.getSongs().size() + " songs", pc[i], ICON_LIST, 12, 12, 40,
                    () -> this.minecraft.gui.setScreen(new OrePlaylistScreen(p, false).from(this)), false));
        }
        tiles.add(new Tile("New playlist", "Create or import", 0, ICON_PLUS, 12, 12, 36,
                () -> this.minecraft.gui.setScreen(new OrePlaylistsScreen()), true));

        int gap = px(16);
        int w = (totalW - gap * (tiles.size() - 1)) / tiles.size();
        int artH = px(88), h = px(146), b2 = px(2);
        int cell = px(8);
        for (int i = 0; i < tiles.size(); i++) {
            Tile t = tiles.get(i);
            int tx = x + i * (w + gap);
            boolean hov = hit(mx, my, tx, y, w, h, t.action());
            if (t.dashed()) {
                int c = hov ? th.text() : th.sep();
                int dash = px(6);
                for (int dx = 0; dx < w; dx += dash * 2) {
                    int e = Math.min(w, dx + dash);
                    g.fill(tx + dx, y, tx + e, y + b2, c);
                    g.fill(tx + dx, y + h - b2, tx + e, y + h, c);
                }
                for (int dy = 0; dy < h; dy += dash * 2) {
                    int e = Math.min(h, dy + dash);
                    g.fill(tx, y + dy, tx + b2, y + e, c);
                    g.fill(tx + w - b2, y + dy, tx + w, y + e, c);
                }
                icon(g, t.icon(), t.gw(), t.gh(), tx + (w - px(t.iconSize())) / 2, y + (artH - px(t.iconSize())) / 2, px(t.iconSize()), th.dim());
            } else {
                card(g, tx, y, w, h);
                int ax0 = tx + b2, ay0 = y + b2, aw = w - 2 * b2;
                g.fill(ax0, ay0, ax0 + aw, ay0 + artH, t.color());
                for (int cyi = 0; cyi * cell < artH; cyi++) {
                    for (int cxi = 0; cxi * cell < aw; cxi++) {
                        if (((cxi + cyi) & 1) == 0) {
                            g.fill(ax0 + cxi * cell, ay0 + cyi * cell,
                                    Math.min(ax0 + aw, ax0 + (cxi + 1) * cell), Math.min(ay0 + artH, ay0 + (cyi + 1) * cell), 0x17FFFFFF);
                        }
                    }
                }
                icon(g, t.icon(), t.gw(), t.gh(), tx + (w - px(t.iconSize())) / 2, ay0 + (artH - px(t.iconSize())) / 2, px(t.iconSize()), 0xFFFFFFFF);
                if (hov) g.fill(ax0, ay0, ax0 + aw, y + h - px(6), 0x14FFFFFF);
            }
            text(g, fit(t.name(), w - px(24), true, 16), tx + px(12), y + b2 + artH + px(10), th.text(), true, 16);
            text(g, fit(t.sub(), w - px(24), false, 16), tx + px(12), y + b2 + artH + px(32), th.dim(), false, 16);
        }
    }

    private void openLibrary() {
        this.minecraft.gui.setScreen(new OrePlaylistScreen(OrePlaylistsScreen.libraryPlaylist(manager), false, true).from(this));
    }

    private static String durationOf(SongInfo si) {
        if (si.getSource() != SongInfo.SourceType.VANILLA && si.getSource() != SongInfo.SourceType.LOCAL) return "";
        for (VanillaTrack vt : SoundtrackRegistry.getInstance().getAllTracks()) {
            if (vt.id().equals(si.getPath())) return vt.durationText();
        }
        return "";
    }

    private static float nextSpeed(float cur) {
        for (float sp : SPEEDS) if (sp > cur + 0.001f) return sp;
        return SPEEDS[0];
    }

    private static String greeting() {
        int h = LocalTime.now().getHour();
        return h < 5 ? "Good night" : h < 12 ? "Good morning" : h < 18 ? "Good afternoon" : "Good evening";
    }

    private static String hours(long seconds) {
        return String.format("%d:%02d h", seconds / 3600, (seconds % 3600) / 60);
    }

    private static String mostPlayed(ListeningStatisticsManager st) {
        String best = null;
        int bestN = 0;
        for (Map.Entry<String, Integer> e : st.getPlayCounts().entrySet()) {
            if (e.getValue() > bestN) { bestN = e.getValue(); best = e.getKey(); }
        }
        if (best == null) return "-";
        int slash = Math.max(best.lastIndexOf('/'), best.lastIndexOf('\\'));
        String name = slash >= 0 ? best.substring(slash + 1) : best;
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }
}
