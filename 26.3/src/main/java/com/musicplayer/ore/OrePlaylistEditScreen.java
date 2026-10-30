package com.musicplayer.ore;

import com.musicplayer.AlbumArtManager;
import com.musicplayer.Playlist;
import com.musicplayer.PlaylistManager;
import com.musicplayer.SdlFileDialogs;
import com.musicplayer.SongInfo;
import com.musicplayer.widget.PlaylistCoverWidget;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.resources.Identifier;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Edit playlist (name, cover, songs) and the "Extra song folders" dialog, from the design's
 * Playlist-Edit.dc.html and Playlist-AddFolders.dc.html. Changes apply on "Save changes".
 */
public class OrePlaylistEditScreen extends OreScreen {

    /** Folders the user switched off in the dialog (kept for the session; the config has no such list). */
    private static final Set<String> OFF = new HashSet<>();
    private static final Map<String, List<SongInfo>> FOLDER_CACHE = new HashMap<>();

    private final Screen parent;
    private final Playlist pl;
    private final boolean focusName;

    private final List<SongInfo> items = new ArrayList<>();
    private final List<SongInfo> base = new ArrayList<>();
    private String baseName;
    private String baseCover;
    private EditBox nameField, searchField;
    private boolean confirmDelete;
    private boolean foldersOpen;
    private int dlgScroll, dlgScrollMax;

    public OrePlaylistEditScreen(Screen parent, Playlist pl) {
        this(parent, pl, false);
    }

    public OrePlaylistEditScreen(Screen parent, Playlist pl, boolean focusName) {
        super("Edit playlist");
        this.parent = parent;
        this.pl = pl;
        this.focusName = focusName;
        items.addAll(pl.getSongs());
        base.addAll(pl.getSongs());
        baseName = pl.getName();
        baseCover = pl.getCustomCoverPath();
    }

    @Override
    protected int activeTab() { return 2; }

    @Override
    protected boolean modal() { return foldersOpen; }

    @Override
    protected void init() {
        super.init();
        String nv = nameField != null ? nameField.getValue() : pl.getName();
        String sv = searchField != null ? searchField.getValue() : "";
        nameField = makeField("Playlist name", 64);
        nameField.setValue(nv);
        searchField = makeField("Add songs: search library and extra folders", 64);
        searchField.setValue(sv);
        if (focusName) setFocused(nameField);
    }

    // ---------------------------------------------------------------- state helpers

    private boolean sameList(List<SongInfo> a, List<SongInfo> b) {
        if (a.size() != b.size()) return false;
        for (int i = 0; i < a.size(); i++) if (!a.get(i).getPath().equals(b.get(i).getPath())) return false;
        return true;
    }

    private boolean changed() {
        return !sameList(items, base) || !nameField.getValue().equals(baseName)
                || !Objects.equals(pl.getCustomCoverPath(), baseCover);
    }

    private void restore() {
        items.clear();
        items.addAll(base);
        nameField.setValue(baseName);
        pl.setCustomCoverPath(baseCover);
        PlaylistCoverWidget.invalidateCover(pl);
    }

    private void cancel() {
        restore();
        this.minecraft.gui.setScreen(parent);
    }

    private void save() {
        String newName = nameField.getValue().trim();
        pl.getSongs().clear();
        pl.getSongs().addAll(items);
        PlaylistCoverWidget.invalidateCover(pl);
        if (!newName.isEmpty() && !newName.equals(pl.getName())) {
            PlaylistManager.getInstance().renamePlaylist(pl, newName);
        }
        PlaylistManager.getInstance().savePlaylist(pl);
        this.minecraft.gui.setScreen(parent);
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
        });
    }

    private void resetCover() {
        pl.setCustomCoverPath(null);
        PlaylistCoverWidget.invalidateCover(pl);
    }

    private void deletePlaylist() {
        if (!confirmDelete) { confirmDelete = true; return; }
        PlaylistManager.getInstance().deletePlaylist(pl);
        this.minecraft.gui.setScreen(new OrePlaylistsScreen());
    }

    // ---------------------------------------------------------------- song sources

    private static boolean isAudio(String name) {
        String l = name.toLowerCase(Locale.ROOT);
        return l.endsWith(".mp3") || l.endsWith(".wav") || l.endsWith(".flac");
    }

    private static void scan(File dir, List<SongInfo> out, int depth) {
        File[] files = dir.listFiles();
        if (files == null || depth > 8) return;
        for (File f : files) {
            if (f.isDirectory()) scan(f, out, depth + 1);
            else if (isAudio(f.getName())) out.add(SongInfo.fromFile(f));
        }
    }

    private List<SongInfo> folderSongs(String path) {
        return FOLDER_CACHE.computeIfAbsent(path, p -> {
            List<SongInfo> out = new ArrayList<>();
            File dir = new File(p);
            if (dir.isDirectory()) scan(dir, out, 0);
            return out;
        });
    }

    private List<SongInfo> searchResults(String query) {
        String q = query.trim().toLowerCase(Locale.ROOT);
        List<SongInfo> out = new ArrayList<>();
        if (q.isEmpty()) return out;
        Set<String> have = new HashSet<>();
        for (SongInfo s : items) have.add(s.getPath());
        List<SongInfo> pool = new ArrayList<>(manager.getCachedEditAllSongs());
        for (String f : manager.getConfig().getFolderPaths()) {
            if (!OFF.contains(f)) pool.addAll(folderSongs(f));
        }
        Set<String> seen = new HashSet<>();
        for (SongInfo s : pool) {
            if (out.size() >= 6) break;
            if (have.contains(s.getPath()) || !seen.add(s.getPath())) continue;
            String hay = (s.getTitle() + " " + s.getArtist()).toLowerCase(Locale.ROOT);
            if (hay.contains(q)) out.add(s);
        }
        return out;
    }

    // ---------------------------------------------------------------- drawing helpers

    /** Flat 36px icon button (the design's .sq): hover shows a frame; optional red hover. */
    private void sq(GuiGraphicsExtractor g, int mx, int my, int x, int y, int[][] ic, int gw, int gh, boolean enabled,
                    boolean danger, boolean accent, String tip, Runnable click) {
        int s36 = px(36), b2 = px(2);
        if (enabled) tip(tip);
        boolean hov = enabled ? hit(mx, my, x, y, s36, s36, click) : false;
        if (hov) {
            g.fill(x, y, x + s36, y + s36, th.edge());
            g.fill(x + b2, y + b2, x + s36 - b2, y + s36 - b2, th.rowHover());
        }
        int col = !enabled ? (th.dim() & 0x00FFFFFF) | 0x4D000000
                : hov ? (danger ? th.like() : accent ? th.accent() : th.text()) : th.dim();
        icon(g, ic, gw, gh, x + (s36 - px(18)) / 2, y + (s36 - px(18)) / 2, px(18), col);
    }

    private void thumb(GuiGraphicsExtractor g, SongInfo si, int x, int mid) {
        int b2 = px(2), s36 = px(36);
        g.fill(x, mid - s36 / 2, x + s36, mid + s36 / 2, th.edge());
        g.blit(coverFor(si), x + b2, mid - s36 / 2 + b2, x + s36 - b2, mid + s36 / 2 - b2, 0f, 1f, 0f, 1f);
    }

    private void drawCover(GuiGraphicsExtractor g, int x, int y, int size) {
        int bd = px(4), in = size - 2 * bd;
        g.fill(x - px(2), y - px(2), x + size + px(2), y + size + px(2), th.hi());
        g.fill(x, y, x + size, y + size, th.edge());
        if (pl.getCustomCoverPath() != null && !pl.getCustomCoverPath().isEmpty()) {
            PlaylistCoverWidget.renderCover(g, pl, x + bd, y + bd, in);
        } else {
            int half = in / 2;
            for (int i = 0; i < 4; i++) {
                Identifier id = i < items.size() ? coverFor(items.get(i)) : AlbumArtManager.DEFAULT_ALBUM_ART;
                int cx = x + bd + (i % 2) * half, cy = y + bd + (i / 2) * half;
                g.blit(id, cx, cy, cx + half, cy + half, 0f, 1f, 0f, 1f);
            }
        }
    }

    private void textButton(GuiGraphicsExtractor g, int mx, int my, int x, int y, int w, int h, int kind, String label,
                            boolean bold, float size, int color, Runnable click) {
        button(g, mx, my, x, y, w, h, kind, click);
        textBtn(g, label, bold, size, x, y, w, h, color);
    }

    // ---------------------------------------------------------------- body

    @Override
    protected void renderBody(GuiGraphicsExtractor g, int mx, int my) {
        int top = px(64);
        int left = px(32), right = this.width - px(32);
        int b2 = px(2);
        int y = top + px(24) - scrollY;
        boolean vis = !foldersOpen;

        g.enableScissor(0, top, this.width, this.height);

        // ---- title row
        boolean ch = changed();
        int by = y + px(5);
        tip("Back");
        button(g, mx, my, left, by, px(48), px(48), NORMAL, this::cancel);
        iconBtn(g, ICON_BACK, 10, 10, left, by, px(48), px(48), px(22), th.text());
        int sw = px(26) * 2 + tw("Save changes", true, 16);
        int cw = px(22) * 2 + tw("Cancel", false, 16);
        int sx = right - sw;
        textButton(g, mx, my, sx, by, sw, px(48), GREEN, "Save changes", true, 16, 0xFFFFFFFF, this::save);
        int cx = sx - px(16) - cw;
        textButton(g, mx, my, cx, by, cw, px(48), NORMAL, "Cancel", false, 16, th.text(), this::cancel);
        int tx = left + px(48) + px(16);
        text(g, "Edit playlist", tx, y, th.text(), true, 32);
        text(g, fit(items.size() + " songs" + (ch ? " · unsaved changes" : ""), cx - px(16) - tx, false, 16), tx, y + px(42), th.dim(), false, 16);
        y += px(58) + px(20);

        // ---- left card
        int lw = px(340), pad = px(20), gap = px(18);
        int ly = y;
        int lh = pad + px(20) + gap + px(200) + gap + px(18) + gap + px(44) + gap + px(2) + gap + px(20) + gap + px(48)
                + gap + px(2) + gap + px(44) + pad + px(4);
        card(g, left, ly, lw, lh);
        int ix = left + pad, iw = lw - 2 * pad;
        int cy = ly + pad;
        text(g, "Cover", ix, cy, th.text(), true, 16);
        cy += px(20) + gap;
        drawCover(g, ix + (iw - px(200)) / 2, cy, px(200));
        tip("Click to change the cover");
        if (hit(mx, my, ix + (iw - px(200)) / 2, cy, px(200), px(200), vis && cy + px(200) > top ? this::changeCover : null)) {
            g.fill(ix + (iw - px(200)) / 2, cy, ix + (iw - px(200)) / 2 + px(200), cy + px(200), 0x66000000);
            String cc = "Change cover";
            text(g, cc, ix + (iw - tw(cc, true, 16)) / 2, cy + (px(200) - capH(16)) / 2, 0xFFFFFFFF, true, 16);
        }
        cy += px(200) + gap;
        String note = pl.getCustomCoverPath() != null && !pl.getCustomCoverPath().isEmpty() ? "Custom cover" : "Auto collage from the first four songs";
        text(g, fit(note, iw, false, 14), ix + (iw - Math.min(iw, tw(note, false, 14))) / 2, cy, th.dim(), false, 14);
        cy += px(18) + gap;
        int bw = (iw - px(8)) / 2;
        textButton(g, mx, my, ix, cy, bw, px(44), NORMAL, "Change cover", false, 14, th.text(), this::changeCover);
        textButton(g, mx, my, ix + bw + px(8), cy, bw, px(44), NORMAL, "Reset cover", false, 14, th.text(), this::resetCover);
        cy += px(44) + gap;
        g.fill(ix, cy, ix + iw, cy + b2, th.sep());
        cy += b2 + gap;
        text(g, "Name", ix, cy, th.text(), true, 16);
        cy += px(20) + gap;
        field(g, mx, my, nameField, ix, cy, iw, px(48), px(14), px(14), vis && cy + px(48) > top);
        cy += px(48) + gap;
        g.fill(ix, cy, ix + iw, cy + b2, th.sep());
        cy += b2 + gap;
        String del = confirmDelete ? "Click again to delete" : "Delete playlist";
        button(g, mx, my, ix, cy, iw, px(44), NORMAL, this::deletePlaylist);
        int dc = px(18) + px(10) + tw(del, false, 14);
        int dx = ix + (iw - dc) / 2;
        icon(g, ICON_X, 11, 11, dx, cy + px(22) - px(2) - px(9) + pressDy, px(18), th.like());
        text(g, del, dx + px(18) + px(10), cy + px(22) - px(2) - capH(14) / 2 + pressDy, th.like(), false, 14);

        // ---- right card
        int rx = left + lw + px(20), rw = right - rx;
        int ry = y;
        List<SongInfo> results = searchResults(searchField.getValue());
        int rowH = px(56);
        int rh = px(20) + px(40) + px(16) + px(48) + px(16) + results.size() * rowH
                + Math.max(1, items.size()) * rowH + px(8) + px(4);
        if (items.isEmpty()) rh += px(0);
        card(g, rx, ry, rw, rh);
        int cy2 = ry + px(20);
        text(g, "Songs", rx + pad, cy2 + (px(40) - capH(16)) / 2, th.text(), true, 16);
        boolean can = ch;
        int uw = tw("Undo changes", false, 14) + px(32);
        button(g, mx, my, rx + rw - pad - uw, cy2, uw, px(40), NORMAL, can ? this::restore : null);
        textBtn(g, "Undo changes", false, 14, rx + rw - pad - uw, cy2, uw, px(40), can ? th.text() : th.dim());
        cy2 += px(40) + px(16);
        int fw = px(18) * 2 + px(18) + px(10) + tw("Folders", false, 14);
        int sfw = rw - 2 * pad - px(10) - fw;
        field(g, mx, my, searchField, rx + pad, cy2, sfw, px(48), px(46), px(14), vis && cy2 + px(48) > top);
        icon(g, ICON_SEARCH, 11, 11, rx + pad + px(14), cy2 + (px(48) - px(20)) / 2, px(20), th.dim());
        int fx = rx + pad + sfw + px(10);
        tip("Manage extra song folders");
        button(g, mx, my, fx, cy2, fw, px(48), NORMAL, () -> { foldersOpen = true; dlgScroll = 0; });
        icon(g, ICON_FOLDER, 12, 12, fx + px(18), cy2 + px(24) - px(2) - px(9) + pressDy, px(18), th.text());
        text(g, "Folders", fx + px(18) + px(18) + px(10), cy2 + px(24) - px(2) - capH(14) / 2 + pressDy, th.text(), false, 14);
        cy2 += px(48) + px(16);

        int numX = rx + px(16) + px(28) + px(12);
        for (SongInfo s : results) {
            g.fill(rx + b2, cy2, rx + rw - b2, cy2 + b2, th.sep());
            int mid = cy2 + b2 + (rowH - b2) / 2;
            boolean hov = hit(mx, my, rx + b2, cy2 + b2, rw - 2 * b2 - px(52), rowH - b2, () -> items.add(s));
            if (hov) g.fill(rx + b2, cy2 + b2, rx + rw - b2, cy2 + rowH, th.rowHover());
            icon(g, ICON_PLUS, 12, 12, rx + px(16) + px(4), mid - px(10), px(20), th.accent());
            thumb(g, s, numX + px(24) + px(12), mid);
            int tx2 = numX + px(24) + px(12) + px(36) + px(12);
            int mw = rx + rw - px(12) - px(36) - px(12) - tx2;
            text(g, fit(s.getTitle(), mw, false, 16), tx2, mid - capH(16) - px(1), th.text(), false, 16);
            text(g, fit(s.getArtist(), mw, false, 14), tx2, mid + px(3), th.dim(), false, 14);
            sq(g, mx, my, rx + rw - px(12) - px(36), mid - px(18), ICON_PLUS, 12, 12, true, false, true, "Add to playlist", () -> items.add(s));
            cy2 += rowH;
        }

        for (int i = 0; i < items.size(); i++) {
            SongInfo s = items.get(i);
            final int idx = i;
            g.fill(rx + b2, cy2, rx + rw - b2, cy2 + b2, th.sep());
            int mid = cy2 + b2 + (rowH - b2) / 2;
            if (mx >= rx + b2 && mx < rx + rw - b2 && my >= cy2 + b2 && my < cy2 + rowH && my >= top) {
                g.fill(rx + b2, cy2 + b2, rx + rw - b2, cy2 + rowH, th.rowHover());
            }
            int gx = rx + px(16);
            icon(g, ICON_GRIP, 8, 9, gx + (px(28) - px(16)) / 2, mid - px(9), px(18), th.dim());
            String num = String.valueOf(i + 1);
            int nx = gx + px(28) + px(12);
            text(g, num, nx + (px(24) - tw(num, false, 16)) / 2, mid - capH(16) / 2, th.dim(), false, 16);
            int thx = nx + px(24) + px(12);
            thumb(g, s, thx, mid);
            int tx2 = thx + px(36) + px(12);
            int btnX = rx + rw - px(12) - px(36);
            int downX = btnX - px(12) - px(36), upX = downX - px(12) - px(36);
            int mw = upX - px(12) - tx2;
            text(g, fit(s.getTitle(), mw, false, 16), tx2, mid - capH(16) - px(1), th.text(), false, 16);
            text(g, fit(s.getArtist(), mw, false, 14), tx2, mid + px(3), th.dim(), false, 14);
            sq(g, mx, my, upX, mid - px(18), ICON_UP, 10, 10, i > 0, false, false, "Move up", () -> swap(idx, idx - 1));
            sq(g, mx, my, downX, mid - px(18), ICON_DOWN, 10, 10, i < items.size() - 1, false, false, "Move down", () -> swap(idx, idx + 1));
            sq(g, mx, my, btnX, mid - px(18), ICON_X, 11, 11, true, true, false, "Remove", () -> items.remove(idx));
            cy2 += rowH;
        }
        if (items.isEmpty()) {
            g.fill(rx + b2, cy2, rx + rw - b2, cy2 + b2, th.sep());
            String e = "No songs left. Search above to add some.";
            text(g, e, rx + (rw - tw(e, false, 16)) / 2, cy2 + b2 + (rowH - capH(16)) / 2, th.dim(), false, 16);
        }

        int bottom = y + Math.max(lh, rh) + px(32);
        g.disableScissor();
        scrollMax = Math.max(0, (bottom + scrollY) - top - (this.height - top));
        scrollY = Math.max(0, Math.min(scrollMax, scrollY));
        scrollbar(g, top, this.height);
    }

    private void swap(int a, int b) {
        if (a < 0 || b < 0 || a >= items.size() || b >= items.size()) return;
        SongInfo t = items.get(a);
        items.set(a, items.get(b));
        items.set(b, t);
    }

    // ---------------------------------------------------------------- folders dialog

    private void addFolder() {
        if (blockedByFullscreen(this::addFolder)) return;
        String initial = manager.getConfig().getMusicFolderPath();
        if (initial.isEmpty()) initial = System.getProperty("user.home");
        SdlFileDialogs.selectFolder("Select a music folder", initial, result -> {
            if (result == null || result.isEmpty() || !new File(result).isDirectory()) return;
            String canon = result.replaceAll("/+$", "");
            List<String> list = new ArrayList<>(manager.getConfig().getFolderPaths());
            if (!list.contains(canon)) {
                list.add(canon);
                manager.getConfig().setFolderPaths(list);
            }
        });
    }

    @Override
    protected void renderOverlay(GuiGraphicsExtractor g, int mx, int my) {
        nameField.visible = nameField.visible && !foldersOpen;
        searchField.visible = searchField.visible && !foldersOpen;
        if (!foldersOpen) return;
        nameField.visible = false;
        searchField.visible = false;
        g.nextStratum();
        g.fill(0, 0, this.width, this.height, 0x9E000000);
        int dw = px(820), dh = Math.min(px(600), this.height - px(16));
        int dx = (this.width - dw) / 2, dy = (this.height - dh) / 2;
        int b2 = px(2);
        // swallow clicks on the dialog itself, close when clicking the scrim
        catcher(mx, my, () -> foldersOpen = false);
        nextPlainHit(mx, my, dx, dy, dw, dh);
        card(g, dx, dy, dw, dh);

        int pad = px(24);
        int hy = dy + px(20);
        int closeS = px(44);
        int tx = dx + pad, tmax = dw - pad - px(20) - closeS - px(12);
        text(g, "Extra song folders", tx, hy, th.text(), true, 24);
        List<String> desc = wrap("Songs in these folders show up when you search to add songs to a playlist. They are not part of your library.", tmax, false, 14);
        for (int i = 0; i < desc.size(); i++) text(g, desc.get(i), tx, hy + px(34) + i * (capH(14) + px(5)), th.dim(), false, 14);
        int headH = px(34) + desc.size() * (capH(14) + px(5)) + px(12);
        tip("Close");
        button(g, mx, my, dx + dw - px(20) - closeS, hy, closeS, closeS, NORMAL, () -> foldersOpen = false);
        iconBtn(g, ICON_X, 11, 11, dx + dw - px(20) - closeS, hy, closeS, closeS, px(18), th.text());

        int ry = hy + Math.max(headH, closeS) + px(4);
        int aw = px(22) * 2 + px(20) + px(10) + tw("Add folder", true, 16);
        button(g, mx, my, dx + pad, ry, aw, px(48), GREEN, this::addFolder);
        icon(g, ICON_FOLDER, 12, 12, dx + pad + px(22), ry + px(24) - px(2) - px(10) + pressDy, px(20), 0xFFFFFFFF);
        text(g, "Add folder", dx + pad + px(22) + px(20) + px(10), ry + px(24) - px(2) - capH(16) / 2 + pressDy, 0xFFFFFFFF, true, 16);
        String hint = "Opens your system folder picker. Added folders stay out of your library.";
        text(g, fit(hint, dw - 2 * pad - aw - px(12), false, 14), dx + pad + aw + px(12), ry + (px(48) - capH(14)) / 2, th.dim(), false, 14);

        int footH = px(16) + px(48) + px(20);
        int listTop = ry + px(48) + px(16);
        int listBottom = dy + dh - footH;
        g.fill(dx + b2, listTop, dx + dw - b2, listBottom, th.edge());
        g.fill(dx + b2, listTop + b2, dx + dw - b2, listBottom, th.inset());

        List<String> folders = manager.getConfig().getFolderPaths();
        int rowH = px(64);
        int contentH = folders.size() * rowH;
        dlgScrollMax = Math.max(0, contentH - (listBottom - listTop - b2));
        dlgScroll = Math.max(0, Math.min(dlgScrollMax, dlgScroll));
        g.enableScissor(dx + b2, listTop + b2, dx + dw - b2, listBottom);
        int total = 0, picked = 0;
        for (String f : folders) if (!OFF.contains(f)) { picked++; total += folderSongs(f).size(); }
        int yy = listTop + b2 - dlgScroll;
        if (folders.isEmpty()) {
            String e = "No folders yet. Choose Add folder to start.";
            text(g, e, dx + (dw - tw(e, false, 16)) / 2, listTop + px(56), th.dim(), false, 16);
        }
        for (String f : new ArrayList<>(folders)) {
            if (yy + rowH >= listTop && yy <= listBottom) {
                boolean on = !OFF.contains(f);
                if (yy > listTop - rowH) {
                    if (yy >= listTop) g.fill(dx + b2, yy, dx + dw - b2, yy + b2, th.sep());
                    int mid = yy + rowH / 2;
                    boolean hov = mx >= dx && mx < dx + dw && my >= Math.max(yy, listTop) && my < Math.min(yy + rowH, listBottom);
                    if (hov) g.fill(dx + b2, yy + b2, dx + dw - b2, yy + rowH, th.rowHover());
                    int cbx = dx + pad - px(8);
                    boolean live = mid - px(18) >= listTop && mid + px(18) <= listBottom;
                    if (live) checkbox(g, mx, my, cbx, mid - px(14), on, () -> { if (!OFF.remove(f)) OFF.add(f); });
                    icon(g, ICON_FOLDER, 12, 12, cbx + px(28) + px(14), mid - px(14), px(28), 0xFFC9B13A);
                    int nx = cbx + px(28) + px(14) + px(28) + px(14);
                    String name = new File(f).getName();
                    if (name.isEmpty()) name = f;
                    int rightW = px(210);
                    int mw = dx + dw - px(12) - px(36) - px(14) - rightW - px(14) - nx;
                    text(g, fit(name, mw, true, 16), nx, mid - capH(16) - px(1), th.text(), true, 16);
                    text(g, fit(f, mw, false, 14), nx, mid + px(3), th.dim(), false, 14);
                    int rr = dx + dw - px(12) - px(36) - px(14);
                    String cnt = folderSongs(f).size() + " songs";
                    String sub = on ? "Searched when adding songs" : "Not searched";
                    textRight(g, cnt, rr, mid - capH(16) - px(1), th.text(), false, 16);
                    textRight(g, fit(sub, rightW, false, 14), rr, mid + px(3), th.dim(), false, 14);
                    if (live) sq(g, mx, my, dx + dw - px(12) - px(36), mid - px(18), ICON_X, 11, 11, true, true, false, "Remove from the list", () -> {
                        List<String> list = new ArrayList<>(manager.getConfig().getFolderPaths());
                        list.remove(f);
                        manager.getConfig().setFolderPaths(list);
                        OFF.remove(f);
                        FOLDER_CACHE.remove(f);
                    });
                }
            }
            yy += rowH;
        }
        g.disableScissor();
        if (dlgScrollMax > 0) {
            int tw16 = px(16);
            int trackH = listBottom - listTop - b2;
            int thumbH = Math.max(px(40), trackH * trackH / (trackH + dlgScrollMax));
            int thumbY = listTop + b2 + (trackH - thumbH) * dlgScroll / dlgScrollMax;
            button(g, -1, -1, dx + dw - b2 - tw16, thumbY, tw16, thumbH, LIGHT, null);
        }

        int fy = listBottom;
        String a = total + " extra songs", b = " searchable from " + picked + " folders";
        int ty = fy + px(16) + (px(48) - capH(16)) / 2;
        text(g, a, dx + pad, ty, th.text(), true, 16);
        text(g, fit(b, dw - 2 * pad - tw(a, true, 16) - px(26) * 2 - tw("Done", true, 16) - px(12), false, 16),
                dx + pad + tw(a, true, 16), ty, th.dim(), false, 16);
        int dw2 = px(26) * 2 + tw("Done", true, 16);
        textButton(g, mx, my, dx + dw - pad - dw2, fy + px(16), dw2, px(48), GREEN, "Done", true, 16, 0xFFFFFFFF, () -> foldersOpen = false);
    }

    /** Registers a click-swallowing region (no hand cursor) over the dialog. */
    private void nextPlainHit(int mx, int my, int x, int y, int w, int h) {
        catcherArea(mx, my, x, y, w, h);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        if (foldersOpen) {
            dlgScroll = Math.max(0, Math.min(dlgScrollMax, dlgScroll - (int) Math.round(sy * px(56))));
            return true;
        }
        return super.mouseScrolled(mx, my, sx, sy);
    }
}
