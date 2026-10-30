package com.musicplayer.ore;

import com.mojang.blaze3d.platform.InputConstants;
import com.musicplayer.AlbumArtManager;
import com.musicplayer.MusicPlayerManager;
import com.musicplayer.Playlist;
import com.musicplayer.PlaylistManager;
import com.musicplayer.SdlFileDialogs;
import com.musicplayer.SongInfo;
import com.musicplayer.UpNextScreen;
import com.musicplayer.config.MusicPlayerConfig.PlaybackMode;
import com.musicplayer.widget.PlaylistCoverWidget;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.MouseButtonEvent;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * One playlist: hero, song list, now-playing bar and (unless compact) the big player on the right.
 * From the design's Playlist.dc.html and Playlist-hfqd.dc.html (minimized).
 */
public class OrePlaylistScreen extends OreScreen {

    private static final float[][] DECO = {
            {0, 0, .20f}, {0, 64, .08f}, {0, 96, .14f}, {0, 160, .08f}, {32, 32, .14f},
            {32, 96, .08f}, {32, 128, .14f}, {64, 0, .08f}, {64, 64, .08f}};
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.ENGLISH);
    /** Remembered between openings, like a view mode. */
    private static boolean compact = false;

    private final Playlist pl;
    private final boolean liked;
    private final boolean library;

    private boolean menuOpen, confirmDelete;
    private int rowMenu = -1;
    private boolean subMenu;
    private int rowMenuX, rowMenuY;
    private int heroMenuX, heroMenuY;
    private final List<int[]> rowRects = new ArrayList<>(); // x, y, w, h, index

    public OrePlaylistScreen(Playlist pl, boolean liked) {
        this(pl, liked, false);
    }

    /** {@code library}: the read-only list of your music folder; {@code liked}: the pinned Liked Songs list. */
    public OrePlaylistScreen(Playlist pl, boolean liked, boolean library) {
        super(pl.getName());
        this.pl = pl;
        this.liked = liked;
        this.library = library;
    }

    @Override
    protected int activeTab() { return 2; }

    // ---------------------------------------------------------------- actions

    private boolean isCurrent(Now n) {
        Playlist cur = manager.getCurrentCustomPlaylist();
        return n.active && !n.mc && cur != null && cur.getName().equals(pl.getName());
    }

    private void playSongAt(int i) {
        if (manager.getCurrentSource() != MusicPlayerManager.SongSource.USER) {
            manager.setCurrentSource(MusicPlayerManager.SongSource.USER);
        }
        Playlist cur = manager.getCurrentCustomPlaylist();
        if (cur == null || !cur.getName().equals(pl.getName())) manager.loadCustomPlaylist(pl);
        manager.playSong(i);
    }

    private net.minecraft.client.gui.screens.Screen parent;

    /** Where Back returns to. */
    public OrePlaylistScreen from(net.minecraft.client.gui.screens.Screen parent) {
        this.parent = parent;
        return this;
    }

    private void goBack() {
        this.minecraft.gui.setScreen(parent != null ? parent : new OrePlaylistsScreen());
    }

    private void closeMenus() {
        menuOpen = false;
        confirmDelete = false;
        rowMenu = -1;
        subMenu = false;
    }

    private void edit(boolean focusName) {
        this.minecraft.gui.setScreen(new OrePlaylistEditScreen(this, pl, focusName));
    }

    private void changeCover() {
        if (blockedByFullscreen(this::changeCover)) return;
        String initial = manager.getConfig().getMusicFolderPath();
        if (initial.isEmpty()) initial = System.getProperty("user.home");
        SdlFileDialogs.openFile("Choose a cover image", initial, result -> {
            if (result == null) return;
            String lower = result.toLowerCase(Locale.ROOT);
            if (!(lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg"))) return;
            pl.setCustomCoverPath(result);
            PlaylistCoverWidget.invalidateCover(pl);
            PlaylistManager.getInstance().savePlaylist(pl);
        });
    }

    private void resetCover() {
        pl.setCustomCoverPath(null);
        PlaylistCoverWidget.invalidateCover(pl);
        PlaylistManager.getInstance().savePlaylist(pl);
    }

    private void exportM3u() {
        if (blockedByFullscreen(this::exportM3u)) return;
        String initial = pl.getName().replaceAll("[^a-zA-Z0-9.-]", "_") + ".m3u";
        SdlFileDialogs.saveFile("Export Playlist", initial, result -> {
            if (result == null) return;
            String path = result.toLowerCase(Locale.ROOT).endsWith(".m3u") ? result : result + ".m3u";
            if (com.musicplayer.M3UHelper.exportToM3U(pl, new java.io.File(path))) {
                com.musicplayer.hud.NotificationManager.showSystem("Exported: " + pl.getName());
            }
        });
    }

    /** Copies a read-only list (Liked Songs, Music Library) into a normal playlist you can edit. */
    private void saveAsPlaylist() {
        java.util.Set<String> names = new java.util.HashSet<>();
        for (Playlist p : PlaylistManager.getInstance().getPlaylists()) names.add(p.getName());
        String name = pl.getName();
        int n = 2;
        while (names.contains(name)) name = pl.getName() + " " + n++;
        Playlist copy = new Playlist(name);
        for (SongInfo si : pl.getSongs()) copy.addSong(si);
        PlaylistManager.getInstance().savePlaylist(copy);
        com.musicplayer.hud.NotificationManager.showSystem("Saved as playlist: " + name);
        this.minecraft.gui.setScreen(new OrePlaylistScreen(copy, false).from(parent));
    }

    private void export() {
        if (blockedByFullscreen(this::export)) return;
        String initial = pl.getName().replaceAll("[^a-zA-Z0-9.-]", "_") + ".json";
        SdlFileDialogs.saveFile("Export Playlist", initial, result -> {
            if (result == null) return;
            try {
                PlaylistManager.exportToJson(pl, new java.io.File(result).toPath());
                com.musicplayer.hud.NotificationManager.showSystem("Exported: " + pl.getName());
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    private void deletePlaylist() {
        if (!confirmDelete) { confirmDelete = true; return; }
        PlaylistManager.getInstance().deletePlaylist(pl);
        this.minecraft.gui.setScreen(new OrePlaylistsScreen());
    }

    // ---------------------------------------------------------------- small drawing helpers

    /** Bevelled button with an icon and a label, centred as a group. */
    private void iconLabel(GuiGraphicsExtractor g, int[][] ic, int gw, int gh, int iconSize, String label, boolean bold,
                           int x, int y, int w, int h, int color) {
        int cw = iconSize + px(12) + tw(label, bold, 16);
        int ix = x + (w - cw) / 2;
        int mid = y + h / 2 - px(2) + pressDy;
        icon(g, ic, gw, gh, ix, mid - iconSize / 2, iconSize, color);
        text(g, label, ix + iconSize + px(12), mid - capH(16) / 2, color, bold, 16);
    }

    private void cover(GuiGraphicsExtractor g, int x, int y, int size, int border) {
        g.fill(x - px(2), y - px(2), x + size + px(2), y + size + px(2), th.hi());
        g.fill(x, y, x + size, y + size, th.edge());
        int in = size - 2 * border;
        if (liked || library) {
            g.fill(x + border, y + border, x + border + in, y + border + in, liked ? 0xFF7A4FD6 : 0xFF4A7FB5);
            int hs = in / 3;
            if (liked) icon(g, ICON_HEART, 8, 7, x + border + (in - hs) / 2, y + border + (in - hs) / 2, hs, 0xFFFFFFFF);
            else icon(g, ICON_NOTE, 12, 12, x + border + (in - hs) / 2, y + border + (in - hs) / 2, hs, 0xFFFFFFFF);
        } else {
            PlaylistCoverWidget.renderCover(g, pl, x + border, y + border, in);
        }
    }

    // ---------------------------------------------------------------- body

    @Override
    protected void renderBody(GuiGraphicsExtractor g, int mx, int my) {
        Now now = now();
        List<SongInfo> songs = pl.getSongs();
        rowRects.clear();

        int top = px(64);
        int footH = px(88);
        int bodyBottom = this.height - footH;
        int asideW = compact ? 0 : px(336);
        int mainRight = this.width - asideW;
        int left = px(32), right = mainRight - px(32);
        int b2 = px(2);
        int y = top + px(24) - scrollY;

        g.enableScissor(0, top, mainRight, bodyBottom);

        // ---- back
        tip("Back");
        button(g, mx, my, left, y, px(48), px(48), NORMAL, this::goBack);
        iconBtn(g, ICON_BACK, 10, 10, left, y, px(48), px(48), px(22), th.text());
        y += px(48) + px(16);

        // ---- hero
        int hh = px(224);
        card(g, left, y, right - left, hh);
        hGradient(g, left + b2, y + b2, (int) ((right - left - 2 * b2) * 0.72f), hh - 2 * b2 - px(4), 0xC97A3A,
                th.light() ? 0.22f : 0.36f);
        deco(g, right - b2, y + b2, th.light() ? 0xA05418 : 0xC97A3A, DECO);
        int cov = px(176), cx = left + px(24), cy = y + px(24);
        cover(g, cx, cy, cov, px(4));
        if (!liked && !library) {
            tip("Click to change the cover");
            if (hit(mx, my, cx, cy, cov, cov, this::changeCover)) {
                g.fill(cx, cy, cx + cov, cy + cov, 0x66000000);
                String cc = "Change cover";
                text(g, cc, cx + (cov - tw(cc, true, 16)) / 2, cy + (cov - capH(16)) / 2, 0xFFFFFFFF, true, 16);
            }
        }

        int x0 = cx + cov + px(28);
        int colW = right - px(24) - x0;
        String badge = liked ? "Pinned" : library ? "Local files" : "Playlist";
        int bw = tw(badge, false, 16) + px(22);
        inset(g, x0, cy, bw, px(28));
        text(g, badge, x0 + px(10), cy + (px(28) - capH(16)) / 2, th.dim(), false, 16);
        int ty = cy + px(28) + px(12);
        text(g, fit(pl.getName(), colW, true, 32), x0, ty, th.text(), true, 32);
        String meta = songs.size() + " songs" + (liked || library ? "" : " · Created "
                + DATE.format(Instant.ofEpochMilli(pl.getCreatedAt()).atZone(ZoneId.systemDefault())));
        text(g, fit(meta, colW, false, 16), x0, ty + px(42), th.dim(), false, 16);

        int by = cy + cov - px(52);
        int bx = x0;
        boolean current = isCurrent(now);
        boolean playing = current && !now.paused;
        String playLabel = playing ? "Pause" : current ? "Resume" : "Play";
        int pw = px(148);
        tip(playing ? "Pause" : "Play this playlist");
        button(g, mx, my, bx, by, pw, px(52), GREEN, () -> {
            if (current) manager.togglePlayPause();
            else if (!songs.isEmpty()) playSongAt(0);
        });
        iconLabel(g, playing ? ICON_PAUSE : ICON_PLAY, 10, 10, px(22), playLabel, true, bx, by, pw, px(52), 0xFFFFFFFF);
        bx += pw + px(10);

        boolean shuffle = manager.getConfig().getPlaybackMode() == PlaybackMode.SHUFFLE;
        int shW = px(20) + px(22) + px(10) + tw("Shuffle", false, 16) + px(20);
        tip("Shuffle");
        button(g, mx, my, bx, by, shW, px(52), shuffle ? GREEN : NORMAL, manager::toggleShuffle);
        icon(g, ICON_SHUFFLE, 10, 10, bx + px(20), by + px(26) - px(2) - px(11) + pressDy, px(22), shuffle ? 0xFFFFFFFF : th.text());
        text(g, "Shuffle", bx + px(20) + px(22) + px(10), by + px(26) - px(2) - capH(16) / 2 + pressDy,
                shuffle ? 0xFFFFFFFF : th.text(), false, 16);
        bx += shW + px(10);

        if (!liked && !library) {
            int aw = px(20) + px(20) + px(10) + tw("Add songs", false, 16) + px(20);
            tip("Add or remove songs");
            button(g, mx, my, bx, by, aw, px(52), NORMAL, () -> edit(false));
            icon(g, ICON_PLUS, 12, 12, bx + px(20), by + px(26) - px(2) - px(10) + pressDy, px(20), th.text());
            text(g, "Add songs", bx + px(20) + px(20) + px(10), by + px(26) - px(2) - capH(16) / 2 + pressDy, th.text(), false, 16);
            bx += aw + px(10);
        }
        tip("More options");
        button(g, mx, my, bx, by, px(52), px(52), NORMAL, () -> { rowMenu = -1; menuOpen = !menuOpen; confirmDelete = false; });
        iconBtn(g, ICON_DOTS, 12, 12, bx, by, px(52), px(52), px(22), th.text());
        heroMenuX = compact ? bx : bx + px(52) - px(260);
        heroMenuY = by + px(60);
        y += hh + px(20);

        // ---- song list
        int rowH = px(56);
        int noteH = px(14) + capH(14) + px(16) + b2;
        int listH = px(40) + rowH * songs.size() + noteH + px(4);
        card(g, left, y, right - left, listH);
        int innerL = left + b2, innerR = right - b2;
        int numX = innerL + px(16), numW = px(32);
        int thumbX = numX + numW + px(14);
        int moreX = innerR - px(12) - px(40), likeX = moreX - px(40);
        int artistX = likeX - px(8) - px(220);
        int titleX = thumbX + px(36) + px(14);
        int hdrY = y + b2 + (px(40) - capH(14)) / 2;
        text(g, "#", numX + (numW - tw("#", false, 14)) / 2, hdrY, th.dim(), false, 14);
        text(g, "Title", titleX, hdrY, th.dim(), false, 14);
        text(g, "Artist", artistX, hdrY, th.dim(), false, 14);

        for (int i = 0; i < songs.size(); i++) {
            SongInfo si = songs.get(i);
            int ry = y + b2 + px(40) + i * rowH;
            if (ry + rowH < top || ry > bodyBottom) continue;
            boolean isPlaying = current && now.path != null && now.path.equals(si.getPath());
            final int idx = i;
            boolean hov = hit(mx, my, innerL, ry, likeX - innerL, rowH, () -> playSongAt(idx));
            rowRects.add(new int[]{innerL, ry, innerR - innerL, rowH, i});
            if (isPlaying) g.fill(innerL, ry, innerR, ry + rowH, 0xFF2F6A21);
            else if (hov) g.fill(innerL, ry, innerR, ry + rowH, th.rowHover());
            g.fill(innerL, ry, innerR, ry + b2, th.sep());
            int textCol = isPlaying ? 0xFFFFFFFF : th.text();
            int dimCol = isPlaying ? 0xFFD4F0C8 : th.dim();
            int mid = ry + b2 + (rowH - b2) / 2;
            if (isPlaying) {
                icon(g, ICON_BARS, 8, 8, numX + (numW - px(18)) / 2, mid - px(9), px(18), 0xFF8CE06A);
            } else if (hov) {
                icon(g, ICON_PLAY, 10, 10, numX + (numW - px(18)) / 2, mid - px(9), px(18), th.text());
            } else {
                String num = String.valueOf(i + 1);
                text(g, num, numX + (numW - tw(num, false, 16)) / 2, mid - capH(16) / 2, th.dim(), false, 16);
            }
            g.fill(thumbX, mid - px(18), thumbX + px(36), mid + px(18), th.edge());
            g.blit(coverFor(si), thumbX + b2, mid - px(18) + b2, thumbX + px(36) - b2, mid + px(18) - b2, 0f, 1f, 0f, 1f);
            boolean yt = si.getSource() == SongInfo.SourceType.YOUTUBE;
            int badgeW = yt ? tw("YouTube", false, 12) + px(16) : 0;
            int titleMax = artistX - px(14) - titleX - (yt ? badgeW + px(10) : 0);
            String tt = fit(si.getTitle(), titleMax, false, 16);
            text(g, tt, titleX, mid - capH(16) / 2, textCol, false, 16);
            if (yt) {
                int bxx = titleX + tw(tt, false, 16) + px(10);
                inset(g, bxx, mid - px(10), badgeW, px(20));
                text(g, "YouTube", bxx + px(8), mid - capH(12) / 2, th.like(), false, 12);
            }
            text(g, fit(si.getArtist(), px(220), false, 16), artistX, mid - capH(16) / 2, dimCol, false, 16);

            boolean lk = isLiked(si.getPath());
            tip(lk ? "Remove from liked songs" : "Like");
            boolean hl = hit(mx, my, likeX, mid - px(20), px(40), px(40), () -> toggleLike(si.getPath()));
            if (hl) g.fill(likeX, mid - px(20), likeX + px(40), mid + px(20), isPlaying ? 0x40000000 : th.rowHover());
            icon(g, ICON_HEART, 8, 7, likeX + px(10), mid - px(10), px(20), lk ? th.like() : (hl ? th.text() : dimCol));
            tip("More options");
            boolean hm = hit(mx, my, moreX, mid - px(20), px(40), px(40), () -> {
                menuOpen = false;
                subMenu = false;
                rowMenu = rowMenu == idx ? -1 : idx;
                rowMenuX = moreX + px(40) - px(260);
                rowMenuY = mid - px(20) + px(46);
            });
            if (hm) g.fill(moreX, mid - px(20), moreX + px(40), mid + px(20), isPlaying ? 0x40000000 : th.rowHover());
            icon(g, ICON_DOTS, 12, 12, moreX + px(10), mid - px(10), px(20), hm ? th.text() : dimCol);
        }
        int noteY = y + b2 + px(40) + rowH * songs.size();
        g.fill(innerL, noteY, innerR, noteY + b2, th.sep());
        text(g, "Reorder songs in Edit playlist. Right-click a song for more options.", innerL + px(16), noteY + b2 + px(14),
                th.dim(), false, 14);
        y += listH + px(32);
        g.disableScissor();

        int contentH = (y + scrollY) - top;
        scrollMax = Math.max(0, contentH - (bodyBottom - top));
        scrollY = Math.max(0, Math.min(scrollMax, scrollY));
        scrollbarAt(g, mainRight - px(16), top, bodyBottom);

        if (!compact) renderAside(g, mx, my, now, mainRight, top, bodyBottom, asideW);
        renderFooter(g, mx, my, now, bodyBottom, footH);
    }

    private void renderAside(GuiGraphicsExtractor g, int mx, int my, Now n, int ax, int top, int bottom, int asideW) {
        g.enableScissor(ax, top, this.width, bottom);
        int b2 = px(2);
        int x = ax, w = asideW - px(24);
        int y = top + px(24);
        int pad = px(20);
        int art = w - 2 * pad;
        int ch = pad + art + px(16) + px(30) + px(4) + px(20) + pad;
        card(g, x, y, w, ch);
        int glow = n.path == null || n.path.isEmpty() ? 0x4A7FB5 : hashColor(n.path) & 0xFFFFFF;
        vGradient(g, x + b2, y + b2, w - 2 * b2, ch - 2 * b2 - px(4), glow, th.light() ? 0.30f : 0.40f, 0.6f);
        int axx = x + pad, ayy = y + pad;
        g.fill(axx - px(2), ayy - px(2), axx + art + px(2), ayy + art + px(2), th.hi());
        g.fill(axx, ayy, axx + art, ayy + art, th.edge());
        g.blit(n.art, axx + px(4), ayy + px(4), axx + art - px(4), ayy + art - px(4), 0f, 1f, 0f, 1f);
        int ty = ayy + art + px(16);
        int likeW = px(40);
        String title = n.title.isEmpty() ? "Nothing playing" : n.title;
        text(g, fit(title, art - likeW - px(12), true, 24), axx, ty, th.text(), true, 24);
        text(g, fit(n.artist, art - likeW - px(12), false, 16), axx, ty + px(30) + px(4), th.dim(), false, 16);
        boolean lk = isLiked(n.path);
        tip(lk ? "Remove from liked songs" : "Like");
        boolean hl = hit(mx, my, axx + art - likeW, ty, likeW, likeW, () -> toggleLike(n.path));
        if (hl) g.fill(axx + art - likeW, ty, axx + art, ty + likeW, th.rowHover());
        icon(g, ICON_HEART, 8, 7, axx + art - likeW + px(8), ty + px(8), px(24), lk ? th.like() : th.dim());

        // next in queue
        int qy = y + ch + px(16);
        int qh = px(16) + capH(16) + px(12) + px(44) + px(16) + b2 + px(4);
        card(g, x, qy, w, qh);
        text(g, "Next in queue", x + pad, qy + px(16), th.text(), true, 16);
        String open = "Open queue";
        int ow = tw(open, false, 14);
        boolean ho = hit(mx, my, x + w - pad - ow, qy + px(12), ow, px(28), () -> this.minecraft.gui.setScreen(new OreQueueScreen(this)));
        text(g, open, x + w - pad - ow, qy + px(16) + (capH(16) - capH(14)) / 2, ho ? th.text() : th.dim(), false, 14);
        List<SongInfo> q = manager.getUpNextQueue(1);
        if (!q.isEmpty()) {
            SongInfo nx = q.get(0);
            int ry = qy + px(16) + capH(16) + px(12);
            g.fill(x + pad, ry, x + pad + px(44), ry + px(44), th.edge());
            g.blit(coverFor(nx), x + pad + b2, ry + b2, x + pad + px(44) - b2, ry + px(44) - b2, 0f, 1f, 0f, 1f);
            int tx = x + pad + px(44) + px(12);
            int mw = x + w - pad - tx;
            text(g, fit(nx.getTitle(), mw, false, 16), tx, ry + px(22) - capH(16) - px(1), th.text(), false, 16);
            text(g, fit(nx.getArtist(), mw, false, 14), tx, ry + px(22) + px(3), th.dim(), false, 14);
        }
        g.disableScissor();
    }

    private void renderFooter(GuiGraphicsExtractor g, int mx, int my, Now n, int fy, int fh) {
        int b2 = px(2);
        g.fill(0, fy, this.width, this.height, th.edge());
        g.fill(0, fy + b2, this.width, this.height, th.panel());
        g.fill(0, fy + b2, this.width, fy + b2 + b2, th.hi());
        int pad = px(24);
        int sideW = px(300);

        // left: art + title
        int ts = px(52);
        int ty0 = fy + (fh - ts) / 2;
        g.fill(pad, ty0, pad + ts, ty0 + ts, th.edge());
        g.blit(n.art, pad + b2, ty0 + b2, pad + ts - b2, ty0 + ts - b2, 0f, 1f, 0f, 1f);
        int tx = pad + ts + px(14);
        int tmax = pad + sideW - tx;
        String title = n.title.isEmpty() ? "Nothing playing" : n.title;
        text(g, fit(title, tmax, true, 16), tx, ty0 + px(8), th.text(), true, 16);
        text(g, fit(n.artist, tmax, false, 14), tx, ty0 + px(8) + px(26), th.dim(), false, 14);

        // centre: transport + progress
        int cl = pad + sideW + px(24), cr = this.width - pad - sideW - px(24);
        int cw = cr - cl;
        int rowTop = fy + (fh - px(62)) / 2;
        int b44 = px(44), bh = px(40), pw = px(64), gap = px(8);
        int tw3 = b44 * 2 + pw + gap * 2;
        int bx = cl + (cw - tw3) / 2;
        tip("Previous");
        button(g, mx, my, bx, rowTop, b44, bh, NORMAL, manager::previousTrack);
        iconBtn(g, ICON_PREV, 10, 10, bx, rowTop, b44, bh, px(20), th.text());
        bx += b44 + gap;
        boolean playing = n.active && !n.paused;
        tip(playing ? "Pause" : "Play");
        button(g, mx, my, bx, rowTop, pw, bh, LIGHT, () -> {
            if (manager.getCurrentSong() != null || n.mc) manager.togglePlayPause();
        });
        iconBtn(g, playing ? ICON_PAUSE : ICON_PLAY, 10, 10, bx, rowTop, pw, bh, px(20), 0xFF1E1E1F);
        bx += pw + gap;
        tip("Next");
        button(g, mx, my, bx, rowTop, b44, bh, NORMAL, manager::nextTrack);
        iconBtn(g, ICON_NEXT, 10, 10, bx, rowTop, b44, bh, px(20), th.text());

        int py = rowTop + bh + px(6);
        int tcol = px(40);
        String cur = time(n.posMs), dur = time(n.durMs);
        text(g, cur, cl + tcol - tw(cur, false, 14), py + (px(12) - capH(14)) / 2, th.dim(), false, 14);
        text(g, dur, cr - tcol + px(12), py + (px(12) - capH(14)) / 2, th.dim(), false, 14);
        sliderTrack(g, cl + tcol + px(12), py, cw - 2 * (tcol + px(12)), px(12), n.progress(), f -> {
            if (n.active) manager.seek((float) f);
        });

        // right: volume + view toggle
        int rx = this.width - pad;
        int vbtn = px(44);
        tip(compact ? "Open the full player" : "Switch to the compact view");
        rx -= vbtn;
        button(g, mx, my, rx, fy + (fh - bh) / 2, vbtn, bh, NORMAL, () -> { compact = !compact; scrollY = 0; });
        iconBtn(g, compact ? ICON_EXPAND : ICON_MINI, compact ? 11 : 12, compact ? 11 : 11, rx, fy + (fh - bh) / 2, vbtn, bh, px(20), th.text());
        rx -= px(12);
        int vw = px(110);
        rx -= vw;
        sliderTrack(g, rx, fy + (fh - px(12)) / 2, vw, px(12), manager.getVolume(), f -> manager.setVolume((float) f));
        icon(g, ICON_SPEAKER, 8, 8, rx - px(12) - px(20), fy + (fh - px(20)) / 2, px(20), th.dim());
    }

    // ---------------------------------------------------------------- popups

    @Override
    protected void renderOverlay(GuiGraphicsExtractor g, int mx, int my) {
        if (menuOpen && (liked || library)) {
            g.nextStratum();
            catcher(mx, my, this::closeMenus);
            int w = px(260), h = px(4) + px(40) * 3 + px(10) + px(8);
            int x = Math.max(px(8), Math.min(this.width - w - px(8), heroMenuX));
            int y = Math.min(heroMenuY, this.height - h - px(8));
            menuBox(g, x, y, w, h);
            int iy = y + px(4);
            menuItem(g, mx, my, x, iy, w, ICON_PLUS, 12, 12, "Save as new playlist", false, () -> { closeMenus(); saveAsPlaylist(); });
            iy += px(40);
            g.fill(x + px(2), iy + px(4), x + w - px(2), iy + px(6), th.sep());
            iy += px(10);
            menuItem(g, mx, my, x, iy, w, ICON_EXPORT, 12, 12, "Export as M3U", false, () -> { closeMenus(); exportM3u(); });
            iy += px(40);
            menuItem(g, mx, my, x, iy, w, ICON_EXPORT, 12, 12, "Export as JSON", false, () -> { closeMenus(); export(); });
        }
        if (menuOpen && !liked && !library) {
            g.nextStratum();
            catcher(mx, my, this::closeMenus);
            int w = px(260), h = px(4) + px(40) * 7 + px(10) * 2 + px(8);
            int x = Math.max(px(8), Math.min(this.width - w - px(8), heroMenuX));
            int y = Math.min(heroMenuY, this.height - h - px(8));
            menuBox(g, x, y, w, h);
            int iy = y + px(4);
            menuItem(g, mx, my, x, iy, w, ICON_EDIT, 12, 12, "Edit playlist", false, () -> { closeMenus(); edit(false); });
            iy += px(40);
            g.fill(x + px(2), iy + px(4), x + w - px(2), iy + px(6), th.sep());
            iy += px(10);
            menuItem(g, mx, my, x, iy, w, ICON_RENAME, 12, 12, "Rename playlist", false, () -> { closeMenus(); edit(true); });
            iy += px(40);
            menuItem(g, mx, my, x, iy, w, ICON_COVER, 12, 12, "Change cover", false, () -> { closeMenus(); changeCover(); });
            iy += px(40);
            menuItem(g, mx, my, x, iy, w, ICON_GRID, 12, 12, "Reset cover", false, () -> { closeMenus(); resetCover(); });
            iy += px(40);
            menuItem(g, mx, my, x, iy, w, ICON_EXPORT, 12, 12, "Export as M3U", false, () -> { closeMenus(); exportM3u(); });
            iy += px(40);
            menuItem(g, mx, my, x, iy, w, ICON_EXPORT, 12, 12, "Export as JSON", false, () -> { closeMenus(); export(); });
            iy += px(40);
            g.fill(x + px(2), iy + px(4), x + w - px(2), iy + px(6), th.sep());
            iy += px(10);
            menuItem(g, mx, my, x, iy, w, ICON_X, 11, 11, confirmDelete ? "Click again to delete" : "Delete playlist", true, this::deletePlaylist);
        }
        if (rowMenu >= 0 && rowMenu < pl.getSongs().size()) {
            SongInfo si = pl.getSongs().get(rowMenu);
            g.nextStratum();
            catcher(mx, my, this::closeMenus);
            boolean local = si.getSource() == SongInfo.SourceType.LOCAL;
            int w = px(260), h = px(4) + px(40) * (1 + (local ? 1 : 0) + (library ? 0 : 1)) + (library ? 0 : px(10)) + px(8);
            int x = Math.max(px(8), Math.min(this.width - w - px(8), rowMenuX));
            int y = Math.min(rowMenuY, this.height - h - px(8));
            menuBox(g, x, y, w, h);
            int iy = y + px(4);
            menuItem(g, mx, my, x, iy, w, ICON_PLUS, 12, 12, "Add to playlist", false, () -> subMenu = !subMenu);
            icon(g, ICON_CHEV, 8, 8, x + w - px(14) - px(14), iy + (px(40) - px(14)) / 2, px(14), th.dim());
            iy += px(40);
            if (local) {
                menuItem(g, mx, my, x, iy, w, ICON_COVER, 12, 12, "Change album cover", false, () -> { closeMenus(); changeSongCover(si); });
                iy += px(40);
            }
            if (!library) {
            g.fill(x + px(2), iy + px(4), x + w - px(2), iy + px(6), th.sep());
            iy += px(10);
            menuItem(g, mx, my, x, iy, w, ICON_X, 11, 11, liked ? "Remove from liked songs" : "Remove from playlist", true, () -> {
                if (liked) {
                    toggleLike(si.getPath());
                    pl.getSongs().remove(si);
                } else {
                    pl.removeSong(si);
                    PlaylistManager.getInstance().savePlaylist(pl);
                    PlaylistCoverWidget.invalidateCover(pl);
                }
                closeMenus();
            });
            }
            if (subMenu) {
                List<Playlist> targets = new ArrayList<>();
                for (Playlist p : PlaylistManager.getInstance().getPlaylists()) if (p != pl) targets.add(p);
                int sw = px(260), sh = px(4) + px(40) * Math.max(1, targets.size()) + px(8);
                int sx = x - sw - px(4);
                if (sx < px(8)) sx = x + w + px(4);
                int sy = Math.min(y, this.height - sh - px(8));
                menuBox(g, sx, sy, sw, sh);
                int yy = sy + px(4);
                if (targets.isEmpty()) {
                    text(g, "No other playlists", sx + px(14), yy + (px(40) - capH(16)) / 2, th.dim(), false, 16);
                }
                for (Playlist p : targets) {
                    menuItem(g, mx, my, sx, yy, sw, ICON_LIST, 12, 12, fit(p.getName(), sw - px(70), false, 16), false, () -> {
                        p.addSong(si);
                        PlaylistManager.getInstance().savePlaylist(p);
                        PlaylistCoverWidget.invalidateCover(p);
                        com.musicplayer.hud.NotificationManager.showSystem("Added to " + p.getName());
                        closeMenus();
                    });
                    yy += px(40);
                }
            }
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean isFocused) {
        if (event.button() == InputConstants.MOUSE_BUTTON_RIGHT && !menuOpen) {
            for (int[] r : rowRects) {
                if (event.x() >= r[0] && event.x() < r[0] + r[2] && event.y() >= r[1] && event.y() < r[1] + r[3]) {
                    rowMenu = r[4];
                    subMenu = false;
                    rowMenuX = (int) event.x();
                    rowMenuY = (int) event.y();
                    return true;
                }
            }
        }
        return super.mouseClicked(event, isFocused);
    }
}
