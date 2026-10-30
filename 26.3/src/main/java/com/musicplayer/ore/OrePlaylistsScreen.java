package com.musicplayer.ore;

import com.musicplayer.AlbumArtManager;
import com.musicplayer.M3UHelper;
import com.musicplayer.MusicPlayerManager;
import com.musicplayer.Playlist;
import com.musicplayer.PlaylistManager;
import com.musicplayer.SdlFileDialogs;
import com.musicplayer.SongInfo;
import com.musicplayer.player.SoundtrackRegistry;
import com.musicplayer.widget.PlaylistCoverWidget;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** "Your playlists" grid, from the design's Playlists.dc.html. */
public class OrePlaylistsScreen extends OreScreen {

    private static final int KIND_USER = 0, KIND_LIKED = 1, KIND_MC = 2, KIND_LIB = 3;
    private static final String[] FILTERS = {"All", "Yours", "Built-in"};
    private static final String[] SORTS = {"Recent", "Name", "Songs"};

    private record Entry(String name, String meta, Playlist pl, int kind, int count, int order) {}

    private int filter = 0, sort = 0;
    private Playlist likedPl, libPl;
    private int mcCount, libCount;

    public OrePlaylistsScreen() {
        super("Playlists");
    }

    @Override
    protected int activeTab() { return 2; }

    @Override
    protected void init() {
        super.init();
        likedPl = manager.buildLikedSongsPlaylist();
        SoundtrackRegistry reg = SoundtrackRegistry.getInstance();
        if (reg.getAllTracks().isEmpty()) reg.discoverTracks();
        mcCount = reg.getAllTracks().size();
        libPl = libraryPlaylist(manager);
        libCount = libPl.getSongs().size();
    }

    /** Your music folder as an unsaved, read-only playlist. */
    static Playlist libraryPlaylist(MusicPlayerManager manager) {
        Playlist p = new Playlist("Music Library");
        for (SongInfo s : manager.getCachedEditAllSongs()) {
            if (s.getSource() != SongInfo.SourceType.VANILLA) p.getSongs().add(s);
        }
        return p;
    }

    private List<Entry> entries() {
        List<Entry> all = new ArrayList<>();
        all.add(new Entry("Liked Songs", likedPl.getSongs().size() + " songs · Pinned", likedPl, KIND_LIKED, likedPl.getSongs().size(), -1));
        int i = 0;
        for (Playlist p : PlaylistManager.getInstance().getPlaylists()) {
            all.add(new Entry(p.getName(), p.getSongs().size() + " songs", p, KIND_USER, p.getSongs().size(), i++));
        }
        all.add(new Entry("Minecraft", mcCount + " songs · Game soundtrack", null, KIND_MC, mcCount, i++));
        all.add(new Entry("Music Library", libCount + " songs · Local files", null, KIND_LIB, libCount, i));
        List<Entry> out = new ArrayList<>();
        for (Entry e : all) {
            if (filter == 1 && e.kind() != KIND_USER) continue;
            if (filter == 2 && e.kind() == KIND_USER) continue;
            out.add(e);
        }
        Comparator<Entry> cmp = switch (sort) {
            case 1 -> Comparator.comparing(e -> e.name().toLowerCase());
            case 2 -> Comparator.comparingInt((Entry e) -> e.count()).reversed();
            default -> Comparator.comparingInt(Entry::order);
        };
        out.sort(cmp);
        return out;
    }

    private static String uniqueName() {
        Set<String> names = new HashSet<>();
        for (Playlist p : PlaylistManager.getInstance().getPlaylists()) names.add(p.getName());
        if (!names.contains("New playlist")) return "New playlist";
        int n = 2;
        while (names.contains("New playlist " + n)) n++;
        return "New playlist " + n;
    }

    private void createPlaylist() {
        Playlist p = new Playlist(uniqueName());
        PlaylistManager.getInstance().savePlaylist(p);
        this.minecraft.gui.setScreen(new OrePlaylistEditScreen(this, p));
    }

    private void importPlaylist() {
        if (blockedByFullscreen(this::importPlaylist)) return;
        String initial = manager.getConfig().getMusicFolderPath();
        if (initial.isEmpty()) initial = System.getProperty("user.home");
        SdlFileDialogs.openFile("Import Playlist", initial, result -> {
            if (result == null) return;
            java.io.File file = new java.io.File(result);
            String lower = file.getName().toLowerCase();
            Playlist imported = null;
            if (lower.endsWith(".json")) {
                try {
                    imported = PlaylistManager.importFromJson(file.toPath());
                } catch (Exception e) {
                    e.printStackTrace();
                }
            } else if (lower.endsWith(".m3u")) {
                imported = M3UHelper.importFromM3U(file);
            }
            if (imported != null && !imported.getSongs().isEmpty()) {
                PlaylistManager.getInstance().savePlaylist(imported);
                com.musicplayer.hud.NotificationManager.showSystem("Imported: " + imported.getName() + " (" + imported.getSongs().size() + " songs)");
            }
        });
    }

    private void open(Entry e) {
        switch (e.kind()) {
            case KIND_USER -> this.minecraft.gui.setScreen(new OrePlaylistScreen(e.pl(), false).from(this));
            case KIND_LIKED -> this.minecraft.gui.setScreen(new OrePlaylistScreen(e.pl(), true).from(this));
            case KIND_MC -> this.minecraft.gui.setScreen(new OreMinecraftScreen());
            default -> this.minecraft.gui.setScreen(new OrePlaylistScreen(libPl, false, true).from(this));
        }
    }

    static void playEntryPlaylist(MusicPlayerManager manager, Playlist pl) {
        if (manager.getCurrentSource() != MusicPlayerManager.SongSource.USER) {
            manager.setCurrentSource(MusicPlayerManager.SongSource.USER);
        }
        manager.loadCustomPlaylist(pl);
        if (!pl.getSongs().isEmpty()) manager.playSong(0);
    }

    private void play(Entry e) {
        switch (e.kind()) {
            case KIND_USER, KIND_LIKED -> playEntryPlaylist(manager, e.pl());
            case KIND_MC -> manager.setCurrentSource(MusicPlayerManager.SongSource.MINECRAFT);
            default -> playEntryPlaylist(manager, libPl);
        }
    }

    private boolean isNow(Entry e, Now now) {
        if (!now.active) return false;
        return switch (e.kind()) {
            case KIND_USER, KIND_LIKED -> !now.mc && manager.getCurrentCustomPlaylist() != null
                    && manager.getCurrentCustomPlaylist().getName().equals(e.pl().getName());
            case KIND_MC -> now.mc;
            default -> !now.mc && manager.getCurrentCustomPlaylist() != null
                    && manager.getCurrentCustomPlaylist().getName().equals("Music Library");
        };
    }

    private int chip(GuiGraphicsExtractor g, int mx, int my, int x, int y, String label, boolean sel, Runnable click, int top) {
        int cw = tw(label, false, 14) + px(32);
        button(g, mx, my, x, y, cw, px(40), sel ? GREEN : NORMAL, y + px(40) > top ? click : null);
        textBtn(g, label, false, 14, x, y, cw, px(40), sel ? 0xFFFFFFFF : th.text());
        return cw;
    }

    @Override
    protected void renderBody(GuiGraphicsExtractor g, int mx, int my) {
        Now now = now();
        List<Entry> list = entries();
        int top = px(64);
        int left = px(32), right = this.width - px(32);
        int y = top + px(28) - scrollY;
        int b2 = px(2);

        g.enableScissor(0, top, this.width, this.height);

        // ---- title row
        int songsTotal = 0;
        for (Playlist p : PlaylistManager.getInstance().getPlaylists()) songsTotal += p.getSongs().size();
        int total = PlaylistManager.getInstance().getPlaylists().size();
        int bx = right;
        String nl = "New playlist", im = "Import";
        int nw = px(22) + px(20) + px(10) + tw(nl, true, 16) + px(22);
        int iw = px(20) + px(20) + px(10) + tw(im, false, 16) + px(20);
        int by = y + px(5);
        bx -= nw;
        tip("Create a new playlist");
        button(g, mx, my, bx, by, nw, px(48), GREEN, by + px(48) > top ? this::createPlaylist : null);
        icon(g, ICON_PLUS, 12, 12, bx + px(22), by + px(24) - px(2) - px(10) + pressDy, px(20), 0xFFFFFFFF);
        text(g, nl, bx + px(22) + px(20) + px(10), by + px(24) - px(2) - capH(16) / 2 + pressDy, 0xFFFFFFFF, true, 16);
        bx -= px(16) + iw;
        tip("Import a .m3u or .json playlist");
        button(g, mx, my, bx, by, iw, px(48), NORMAL, by + px(48) > top ? this::importPlaylist : null);
        icon(g, ICON_IMPORT, 12, 12, bx + px(20), by + px(24) - px(2) - px(10) + pressDy, px(20), th.text());
        text(g, im, bx + px(20) + px(20) + px(10), by + px(24) - px(2) - capH(16) / 2 + pressDy, th.text(), false, 16);
        int maxTitleW = bx - px(16) - left;
        text(g, fit("Your playlists", maxTitleW, true, 32), left, y, th.text(), true, 32);
        text(g, fit(total + " playlists · " + songsTotal + " songs", maxTitleW, false, 16), left, y + px(42), th.dim(), false, 16);
        y += px(58) + px(22);

        // ---- filter + sort chips
        int cx = left;
        for (int i = 0; i < FILTERS.length; i++) {
            final int idx = i;
            cx += chip(g, mx, my, cx, y, FILTERS[i], filter == i, () -> filter = idx, top) + px(8);
        }
        int sx = right;
        for (int i = SORTS.length - 1; i >= 0; i--) {
            final int idx = i;
            int cw = tw(SORTS[i], false, 14) + px(32);
            sx -= cw;
            chip(g, mx, my, sx, y, SORTS[i], sort == i, () -> sort = idx, top);
            sx -= px(8);
        }
        String sb = "Sort by";
        text(g, sb, sx - px(6) - tw(sb, false, 14) + px(8), y + px(20) - px(2) - capH(14) / 2, th.dim(), false, 14);
        y += px(40) + px(22);

        // ---- grid
        int gap = px(20), cols = 5;
        int cardW = (right - left - gap * (cols - 1)) / cols;
        int cover = cardW - px(32);
        int cardH = px(16) + cover + px(12) + px(20) + px(4) + px(16) + px(18);
        int count = list.size() + (filter == 2 ? 0 : 1);
        for (int i = 0; i < count; i++) {
            int cx0 = left + (i % cols) * (cardW + gap);
            int cy0 = y + (i / cols) * (cardH + gap);
            if (cy0 + cardH < top || cy0 > this.height) continue;
            int visTop = Math.max(cy0, top);
            if (i >= list.size()) { // dashed "New playlist"
                boolean hov = hit(mx, my, cx0, visTop, cardW, cy0 + cardH - visTop, this::createPlaylist);
                int c = hov ? th.text() : th.sep();
                int dash = px(6);
                for (int dx = 0; dx < cardW; dx += dash * 2) {
                    int e = Math.min(cardW, dx + dash);
                    g.fill(cx0 + dx, cy0, cx0 + e, cy0 + b2, c);
                    g.fill(cx0 + dx, cy0 + cardH - b2, cx0 + e, cy0 + cardH, c);
                }
                for (int dy = 0; dy < cardH; dy += dash * 2) {
                    int e = Math.min(cardH, dy + dash);
                    g.fill(cx0, cy0 + dy, cx0 + b2, cy0 + e, c);
                    g.fill(cx0 + cardW - b2, cy0 + dy, cx0 + cardW, cy0 + e, c);
                }
                int bs = px(64);
                int bxx = cx0 + (cardW - bs) / 2, byy = cy0 + (cardH - bs - px(14) - capH(16)) / 2;
                button(g, -1, -1, bxx, byy, bs, bs, NORMAL, null);
                iconBtn(g, ICON_PLUS, 12, 12, bxx, byy, bs, bs, px(28), th.text());
                text(g, nl, cx0 + (cardW - tw(nl, false, 16)) / 2, byy + bs + px(14), th.dim(), false, 16);
                continue;
            }
            Entry e = list.get(i);
            boolean isNow = isNow(e, now);
            boolean hov = hit(mx, my, cx0, visTop, cardW, cy0 + cardH - visTop, () -> open(e));
            hov = mx >= cx0 && mx < cx0 + cardW && my >= visTop && my < cy0 + cardH && my >= top;
            g.fill(cx0, cy0, cx0 + cardW, cy0 + cardH, isNow ? 0xFF6CC349 : th.edge());
            g.fill(cx0 + b2, cy0 + b2, cx0 + cardW - b2, cy0 + cardH - b2, hov ? th.rowHover() : th.card());
            g.fill(cx0 + b2, cy0 + b2, cx0 + cardW - b2, cy0 + b2 + b2, th.hi());
            g.fill(cx0 + b2, cy0 + cardH - b2 - px(4), cx0 + cardW - b2, cy0 + cardH - b2, th.cardLip());

            int covX = cx0 + px(16), covY = cy0 + px(16), bd = px(4);
            g.fill(covX - b2, covY - b2, covX + cover + b2, covY + cover + b2, th.hi());
            g.fill(covX, covY, covX + cover, covY + cover, th.edge());
            drawCover(g, e, covX + bd, covY + bd, cover - 2 * bd);

            int nameY = covY + cover + px(12);
            int barsW = isNow ? px(16) + px(8) : 0;
            text(g, fit(e.name(), cardW - px(32) - barsW, true, 16), covX, nameY, th.text(), true, 16);
            if (isNow) {
                int nx = covX + Math.min(tw(e.name(), true, 16), cardW - px(32) - barsW) + px(8);
                icon(g, ICON_BARS, 8, 8, nx, nameY + (capH(16) - px(16)) / 2, px(16), 0xFF6CC349);
            }
            text(g, fit(e.meta(), cardW - px(32), false, 14), covX, nameY + px(24), th.dim(), false, 14);

            if (hov) {
                int pw = px(52), ph = px(44);
                int pxx = covX + cover - px(10) - pw, pyy = covY + cover - px(10) - ph;
                tip("Play " + e.name());
                button(g, mx, my, pxx, pyy, pw, ph, GREEN, () -> play(e));
                iconBtn(g, ICON_PLAY, 10, 10, pxx, pyy, pw, ph, px(20), 0xFFFFFFFF);
            }
        }
        int rows = (count + cols - 1) / cols;
        int bottom = y + rows * (cardH + gap) - gap + px(32);
        g.disableScissor();
        scrollMax = Math.max(0, (bottom + scrollY) - top - (this.height - top));
        scrollY = Math.max(0, Math.min(scrollMax, scrollY));
        scrollbar(g, top, this.height);
    }

    private void drawCover(GuiGraphicsExtractor g, Entry e, int x, int y, int size) {
        switch (e.kind()) {
            case KIND_USER -> PlaylistCoverWidget.renderCover(g, e.pl(), x, y, size);
            case KIND_LIKED -> {
                g.fill(x, y, x + size, y + size, 0xFF7A4FD6);
                int hs = size * 64 / 192;
                icon(g, ICON_HEART, 8, 7, x + (size - hs) / 2, y + (size - hs) / 2, hs, 0xFFFFFFFF);
            }
            case KIND_MC -> {
                Identifier art = AlbumArtManager.getBuiltInArt("minecraft");
                g.blit(art != null ? art : AlbumArtManager.DEFAULT_ALBUM_ART, x, y, x + size, y + size, 0f, 1f, 0f, 1f);
            }
            default -> {
                g.fill(x, y, x + size, y + size, 0xFF4A7FB5);
                int hs = size / 3;
                icon(g, ICON_NOTE, 12, 12, x + (size - hs) / 2, y + (size - hs) / 2, hs, 0xFFFFFFFF);
            }
        }
    }
}
