package com.musicplayer.ore;

import com.musicplayer.MusicPlayerManager;
import com.musicplayer.Playlist;
import com.musicplayer.PlaylistManager;
import com.musicplayer.SongInfo;
import com.musicplayer.VanillaTrack;
import com.musicplayer.player.SoundtrackRegistry;
import com.musicplayer.widget.PlaylistCoverWidget;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Search results for songs and artists, from the design's Search.dc.html. The query lives in the header field. */
public class OreSearchScreen extends OreScreen {

    private static final int MAX_RESULTS = 200;

    private record Result(SongInfo song, boolean mc, String source) { }

    private final String initialQuery;
    private net.minecraft.client.gui.screens.Screen parent;
    private int focusFrames = 6;

    /** Where Back returns to. */
    public OreSearchScreen from(net.minecraft.client.gui.screens.Screen parent) {
        this.parent = parent;
        return this;
    }
    private int filter = 0; // 0 all, 1 library, 2 minecraft
    private String cachedQuery = null;
    private int cachedRev = -1;
    private int ctxRow = -1, ctxX, ctxY;
    private final List<int[]> rowRects = new ArrayList<>();
    private List<Result> all = new ArrayList<>();
    private int addRow = -1, addX, addY;

    public OreSearchScreen(String query) {
        super("Search");
        this.initialQuery = query == null ? "" : query;
    }

    @Override
    protected int activeTab() { return -1; }

    @Override
    protected void init() {
        super.init();
        if (search.getValue().isEmpty()) search.setValue(initialQuery);
        setFocused(search);
    }

    /** Enter in the header field: results already follow the text live. */
    @Override
    protected void onSearchSubmit() { }

    @Override
    protected boolean liveSearch() { return false; }

    private String query() { return search.getValue().trim(); }

    private void refresh() {
        String q = query();
        if (q.equals(cachedQuery) && cachedRev == OreLibrary.revision()) return;
        cachedQuery = q;
        cachedRev = OreLibrary.revision();
        all = new ArrayList<>();
        if (q.isEmpty()) return;
        String ql = q.toLowerCase(Locale.ROOT);
        for (SongInfo s : OreLibrary.searchable(manager)) {
            if (matches(s.getTitle(), ql) || matches(s.getArtist(), ql)) {
                all.add(new Result(s, false, s.getSource() == SongInfo.SourceType.YOUTUBE ? "YouTube" : "Library"));
            }
            if (all.size() >= MAX_RESULTS) break;
        }
        SoundtrackRegistry reg = SoundtrackRegistry.getInstance();
        if (reg.getAllTracks().isEmpty()) reg.discoverTracks();
        for (VanillaTrack vt : reg.getAllTracks()) {
            if (matches(vt.title(), ql) || matches(vt.artist(), ql)) {
                all.add(new Result(new SongInfo(vt.title(), vt.artist(), vt.id(), SongInfo.SourceType.VANILLA), true, "Minecraft"));
            }
        }
    }

    private static boolean matches(String s, String ql) {
        return s != null && s.toLowerCase(Locale.ROOT).contains(ql);
    }

    private List<Result> shown() {
        List<Result> out = new ArrayList<>();
        for (Result r : all) {
            if (filter == 0 || (filter == 1 && !r.mc()) || (filter == 2 && r.mc())) out.add(r);
        }
        return out;
    }

    private int count(int f) {
        int n = 0;
        for (Result r : all) if (f == 0 || (f == 1 && !r.mc()) || (f == 2 && r.mc())) n++;
        return n;
    }

    private void play(Result r) {
        if (r.mc()) {
            if (manager.getCurrentSource() != MusicPlayerManager.SongSource.MINECRAFT) {
                manager.setCurrentSource(MusicPlayerManager.SongSource.MINECRAFT);
            }
            manager.playVanillaTrackById(r.song().getPath());
            return;
        }
        Playlist lib = OrePlaylistsScreen.libraryPlaylist(manager);
        int idx = -1;
        for (int i = 0; i < lib.getSongs().size(); i++) {
            if (lib.getSongs().get(i).getPath().equals(r.song().getPath())) { idx = i; break; }
        }
        if (idx < 0) { // a song from an extra folder: play the folder results as a temporary list
            lib = new Playlist("Search results");
            for (Result o : shown()) if (!o.mc()) lib.getSongs().add(o.song());
            for (int i = 0; i < lib.getSongs().size(); i++) {
                if (lib.getSongs().get(i).getPath().equals(r.song().getPath())) { idx = i; break; }
            }
            if (idx < 0) return;
        }
        if (manager.getCurrentSource() != MusicPlayerManager.SongSource.USER) {
            manager.setCurrentSource(MusicPlayerManager.SongSource.USER);
        }
        Playlist cur = manager.getCurrentCustomPlaylist();
        if (cur == null || !cur.getName().equals(lib.getName())) manager.loadCustomPlaylist(lib);
        manager.playSong(idx);
    }

    /** Draws {@code str} with the matching part of the query highlighted. */
    private void highlighted(GuiGraphicsExtractor g, String str, int x, int y, int maxW, int color) {
        String s = fit(str == null ? "" : str, maxW, false, 16);
        String q = query().toLowerCase(Locale.ROOT);
        int k = q.isEmpty() ? -1 : s.toLowerCase(Locale.ROOT).indexOf(q);
        if (k < 0) {
            text(g, s, x, y, color, false, 16);
            return;
        }
        String a = s.substring(0, k), m = s.substring(k, k + q.length()), b = s.substring(k + q.length());
        text(g, a, x, y, color, false, 16);
        int mx0 = x + tw(a, false, 16);
        text(g, m, mx0, y, th.accent(), true, 16);
        text(g, b, mx0 + tw(m, true, 16), y, color, false, 16);
    }

    @Override
    protected void renderBody(GuiGraphicsExtractor g, int mx, int my) {
        refresh();
        // the screen was just opened by typing: keep the field focused so typing carries on
        if (focusFrames-- > 0) setFocused(search);
        Now now = now();
        List<Result> list = shown();
        rowRects.clear();
        int top = px(64);
        int left = px(32), right = this.width - px(32);
        int b2 = px(2);
        int y = top + px(24) - scrollY;

        g.enableScissor(0, top, this.width, this.height);

        // ---- back
        tip("Back");
        button(g, mx, my, left, y + px(5), px(48), px(48), NORMAL, () ->
                this.minecraft.gui.setScreen(parent != null ? parent : new OreHomeScreen()));
        iconBtn(g, ICON_BACK, 10, 10, left, y + px(5), px(48), px(48), px(22), th.text());
        int headX = left + px(48) + px(16);

        // ---- title row with source chips and Clear
        int cx = right;
        String clear = "Clear";
        int clW = tw(clear, false, 14) + px(32);
        cx -= clW;
        button(g, mx, my, cx, y + px(9), clW, px(40), NORMAL, y + px(49) > top ? () -> { search.setValue(""); filter = 0; } : null);
        textBtn(g, clear, false, 14, cx, y + px(9), clW, px(40), th.text());
        String[] labels = {"All", "Library", "Minecraft"};
        for (int i = labels.length - 1; i >= 0; i--) {
            final int idx = i;
            String l = labels[i] + " " + count(i);
            int w = tw(l, false, 14) + px(32);
            cx -= w + px(8);
            button(g, mx, my, cx, y + px(9), w, px(40), filter == i ? GREEN : NORMAL, y + px(49) > top ? () -> filter = idx : null);
            textBtn(g, l, false, 14, cx, y + px(9), w, px(40), filter == i ? 0xFFFFFFFF : th.text());
        }
        String q = query();
        String title = q.isEmpty() ? "Search" : "Results for “" + q + "”";
        text(g, fit(title, cx - px(16) - headX, true, 32), headX, y, th.text(), true, 32);
        String note = q.isEmpty() ? "Type in the search field to find songs and artists"
                : list.size() + (list.size() == 1 ? " song matches" : " songs match") + " by title or artist";
        text(g, fit(note, cx - px(16) - headX, false, 16), headX, y + px(42), th.dim(), false, 16);
        y += px(58) + px(18);

        int rowH = px(56);
        if (list.isEmpty()) {
            int h = px(64) * 2 + capH(20) + px(12) + capH(16) + px(12);
            card(g, left, y, right - left, h);
            String t1 = q.isEmpty() ? "Search your music" : "No results";
            String t2 = q.isEmpty() ? "Library songs and Minecraft tracks are searched together."
                    : "Nothing matches “" + q + "”. Check the spelling or try an artist name.";
            int ey = y + px(64);
            text(g, t1, left + ((right - left) - tw(t1, true, 20)) / 2, ey, th.text(), true, 20);
            String t2f = fit(t2, right - left - px(48), false, 16);
            text(g, t2f, left + ((right - left) - tw(t2f, false, 16)) / 2, ey + capH(20) + px(12), th.dim(), false, 16);
            y += h + px(32);
        } else {
            int listH = px(40) + rowH * list.size() + b2 + px(4);
            card(g, left, y, right - left, listH);
            int innerL = left + b2, innerR = right - b2;
            int addW = px(40), srcW = px(100);
            int addX0 = innerR - px(12) - addW;
            int srcX = addX0 - px(8) - srcW;
            int artistX = srcX - px(14) - px(220);
            int numX = innerL + px(16), numW = px(32);
            int thumbX = numX + numW + px(14);
            int titleX = thumbX + px(36) + px(14);
            int hdrY = y + b2 + (px(40) - capH(14)) / 2;
            text(g, "#", numX + (numW - tw("#", false, 14)) / 2, hdrY, th.dim(), false, 14);
            text(g, "Title", titleX, hdrY, th.dim(), false, 14);
            text(g, "Artist", artistX, hdrY, th.dim(), false, 14);
            text(g, "Source", srcX, hdrY, th.dim(), false, 14);

            for (int i = 0; i < list.size(); i++) {
                Result r = list.get(i);
                SongInfo si = r.song();
                int ry = y + b2 + px(40) + i * rowH;
                if (ry + rowH < top || ry > this.height) continue;
                rowRects.add(new int[]{innerL, ry, innerR - innerL, rowH, i});
                boolean playing = now.active && si.getPath().equals(now.path);
                boolean hov = hit(mx, my, innerL, ry, addX0 - innerL, rowH, () -> play(r));
                if (playing) g.fill(innerL, ry, innerR, ry + rowH, 0xFF2F6A21);
                else if (hov) g.fill(innerL, ry, innerR, ry + rowH, th.rowHover());
                g.fill(innerL, ry, innerR, ry + b2, th.sep());
                int tc = playing ? 0xFFFFFFFF : th.text();
                int dc = playing ? 0xFFD4F0C8 : th.dim();
                int mid = ry + b2 + (rowH - b2) / 2;
                if (playing) {
                    icon(g, ICON_BARS, 8, 8, numX + (numW - px(18)) / 2, mid - px(9), px(18), 0xFF8CE06A);
                } else if (hov) {
                    icon(g, ICON_PLAY, 10, 10, numX + (numW - px(18)) / 2, mid - px(9), px(18), th.text());
                } else {
                    String num = String.valueOf(i + 1);
                    text(g, num, numX + (numW - tw(num, false, 16)) / 2, mid - capH(16) / 2, th.dim(), false, 16);
                }
                g.fill(thumbX, mid - px(18), thumbX + px(36), mid + px(18), th.edge());
                g.blit(coverFor(si), thumbX + b2, mid - px(18) + b2, thumbX + px(36) - b2, mid + px(18) - b2, 0f, 1f, 0f, 1f);
                highlighted(g, si.getTitle(), titleX, mid - capH(16) / 2, artistX - px(14) - titleX, tc);
                highlighted(g, si.getArtist(), artistX, mid - capH(16) / 2, px(220), dc);
                text(g, r.source(), srcX, mid - capH(14) / 2, dc, false, 14);

                final int idx = i;
                tip("Add to a playlist");
                boolean ha = hit(mx, my, addX0, mid - px(20), addW, px(40), () -> {
                    addRow = addRow == idx ? -1 : idx;
                    addX = addX0 + addW - px(260);
                    addY = mid - px(20) + px(46);
                });
                if (ha) g.fill(addX0, mid - px(20), addX0 + addW, mid + px(20), playing ? 0x40000000 : th.rowHover());
                icon(g, ICON_PLUS, 12, 12, addX0 + px(10), mid - px(10), px(20), ha ? th.text() : dc);
            }
            y += listH + px(32);
        }
        g.disableScissor();

        scrollMax = Math.max(0, (y + scrollY) - top - (this.height - top));
        scrollY = Math.max(0, Math.min(scrollMax, scrollY));
        scrollbar(g, top, this.height);
    }

    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean isFocused) {
        if (event.button() == com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_RIGHT && addRow < 0 && ctxRow < 0) {
            for (int[] r : rowRects) {
                if (event.x() >= r[0] && event.x() < r[0] + r[2] && event.y() >= r[1] && event.y() < r[1] + r[3]
                        && event.y() > px(64)) {
                    ctxRow = r[4];
                    ctxX = (int) event.x();
                    ctxY = (int) event.y();
                    return true;
                }
            }
        }
        return super.mouseClicked(event, isFocused);
    }

    @Override
    protected void renderOverlay(GuiGraphicsExtractor g, int mx, int my) {
        List<Result> list = shown();
        if (ctxRow >= 0 && ctxRow < list.size()) {
            Result cr = list.get(ctxRow);
            boolean local = !cr.mc() && cr.song().getSource() == SongInfo.SourceType.LOCAL;
            g.nextStratum();
            catcher(mx, my, () -> ctxRow = -1);
            int w = px(260), h = px(4) + px(40) * (local ? 2 : 1) + px(8);
            int x = Math.max(px(8), Math.min(this.width - w - px(8), ctxX));
            int y = Math.min(ctxY, this.height - h - px(8));
            menuBox(g, x, y, w, h);
            int iy = y + px(4);
            final int row = ctxRow;
            menuItem(g, mx, my, x, iy, w, ICON_PLUS, 12, 12, "Add to playlist", false, () -> {
                addRow = row;
                addX = x;
                addY = y;
                ctxRow = -1;
            });
            iy += px(40);
            if (local) {
                menuItem(g, mx, my, x, iy, w, ICON_COVER, 12, 12, "Change album cover", false, () -> {
                    ctxRow = -1;
                    changeSongCover(cr.song());
                });
            }
            return;
        }
        if (addRow < 0 || addRow >= list.size()) return;
        SongInfo si = list.get(addRow).song();
        g.nextStratum();
        catcher(mx, my, () -> addRow = -1);
        List<Playlist> targets = new ArrayList<>(PlaylistManager.getInstance().getPlaylists());
        int w = px(260), rows = Math.max(1, Math.min(targets.size(), 8));
        int h = px(4) + px(40) * rows + px(8);
        int x = Math.max(px(8), Math.min(this.width - w - px(8), addX));
        int y = Math.min(addY, this.height - h - px(8));
        menuBox(g, x, y, w, h);
        int iy = y + px(4);
        if (targets.isEmpty()) {
            text(g, "No playlists yet", x + px(14), iy + (px(40) - capH(16)) / 2, th.dim(), false, 16);
        }
        for (int i = 0; i < Math.min(targets.size(), 8); i++) {
            Playlist p = targets.get(i);
            menuItem(g, mx, my, x, iy, w, ICON_LIST, 12, 12, fit(p.getName(), w - px(70), false, 16), false, () -> {
                p.addSong(si);
                PlaylistManager.getInstance().savePlaylist(p);
                PlaylistCoverWidget.invalidateCover(p);
                com.musicplayer.hud.NotificationManager.showSystem("Added to " + p.getName());
                addRow = -1;
            });
            iy += px(40);
        }
    }
}
