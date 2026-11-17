package com.musicplayer.ore;

import com.musicplayer.port.*;
import com.musicplayer.ui.*;

import com.musicplayer.DataExporter;
import com.musicplayer.SongInfo;
import com.musicplayer.config.MusicPlayerConfig;
import com.musicplayer.config.MusicPlayerConfig.NowPlayingStyle;
import com.musicplayer.theme.ColorTheme;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * First-launch setup: a step rail on the left, the page in the middle, back/next in the footer.
 * Everything is saved to the config as it is changed. Layout follows the "Intro" design.
 */
public class OreSetupScreen extends OreScreen {

    // 12x12 pixel icons as {x, y, w, h} rectangles
    private static final int[][] I_NOTE = {{7,1,1,7},{8,1,2,1},{9,2,1,2},{4,7,2,1},{3,8,4,2},{4,10,2,1}};
    private static final int[][] I_FOLDER = {{1,2,4,2},{1,4,10,6}};
    private static final int[][] I_BOOKMARK = {{3,1,6,8},{3,9,2,2},{7,9,2,2}};
    private static final int[][] I_FADE = {{1,2,1,8},{3,3,1,6},{5,4,1,4},{7,3,1,6},{9,2,1,8}};
    private static final int[][] I_PAUSE = {{3,2,2,8},{7,2,2,8}};
    private static final int[][] I_MUTE = {{1,4,2,4},{3,3,1,6},{4,2,1,8},{7,4,1,1},{8,5,1,1},{9,6,1,1},{10,7,1,1},{10,4,1,1},{9,5,1,1},{8,6,1,1},{7,7,1,1}};
    private static final int[][] I_WINDOWED = {{1,2,10,8},{2,4,8,5}};
    private static final int[][] I_CUBE = {{2,2,8,8},{3,3,6,6},{5,5,2,2}};
    private static final int[][] I_MENU = {{2,2,8,2},{2,5,8,2},{2,8,8,2}};
    private static final int[][] I_HEART = {{2,2,3,1},{7,2,3,1},{1,3,10,2},{2,5,8,1},{3,6,6,1},{4,7,4,1},{5,8,2,1}};
    private static final int[][] I_EQ = {{2,1,1,2},{2,5,1,6},{1,3,3,2},{5,1,1,5},{5,8,1,3},{4,6,3,2},{8,1,1,3},{8,6,1,5},{7,4,3,2}};
    private static final int[][] I_REVERB = {{1,1,10,10},{2,2,8,8},{3,3,6,6},{4,4,4,4},{5,5,2,2}};
    private static final int[][] I_PALETTE = {{1,1,4,4},{7,1,4,4},{1,7,4,4},{7,7,4,4}};
    private static final int[][] I_NOWPLAYING = {{1,2,10,8},{2,3,8,6},{4,4,1,4},{5,5,1,2}};
    private static final int[][] I_TOAST = {{1,2,10,6},{2,8,2,2}};
    private static final int[][] I_MINI = {{1,1,10,10},{2,2,8,8},{5,6,4,3}};
    private static final int[][] I_VIZ = {{1,6,2,4},{4,2,2,8},{7,4,2,6},{10,7,1,3}};
    private static final int[][] I_KEYCAP = {{1,1,10,10},{2,2,8,7},{5,4,2,3}};
    private static final int[][] I_PLAY = {{3,2,1,8},{4,3,1,6},{5,4,1,4},{6,5,1,2}};
    private static final int[][] I_NEXT = {{2,3,1,6},{3,4,1,4},{4,5,1,2},{7,3,2,6}};
    private static final int[][] I_PREV = {{3,3,2,6},{9,3,1,6},{8,4,1,4},{7,5,1,2}};
    private static final int[][] I_VOLUP = {{1,4,2,4},{3,3,1,6},{4,2,1,8},{7,4,1,4},{9,3,1,6}};
    private static final int[][] I_VOLDOWN = {{1,4,2,4},{3,3,1,6},{4,2,1,8}};
    private static final int[][] I_LIST = {{1,2,10,2},{1,5,10,2},{1,8,6,2}};
    private static final int[][] I_STATS = {{1,8,2,3},{4,5,2,6},{7,3,2,8}};
    private static final int[][] I_CHECK = {{1,6,2,2},{3,8,2,2},{5,6,2,2},{7,4,2,2},{9,2,2,2}};
    private static final int[][] I_CHECK_SM = {{0,4,1,1},{1,5,1,1},{2,6,1,1},{3,5,1,1},{4,4,1,1},{5,3,1,1},{6,2,1,1},{7,1,1,1}};

    private static final int[][] I_UPLOAD = {{5,1,2,1},{4,2,4,1},{3,3,6,1},{5,4,2,4},{1,9,10,2}};
    private static final int[][] I_FILE = {{2,1,8,10},{3,2,6,8},{4,4,4,1},{4,6,4,1}};
    private static final int[][] I_RESTORE = {{1,1,10,10},{2,2,8,8},{5,3,2,3},{5,5,3,1}};
    private static final int[][] I_SPARKLE = {{5,1,2,10},{1,5,10,2}};

    private static final String[] PAGE_NAMES = {"Welcome", "Restore", "Library", "Playback", "Appearance", "Controls", "Ready"};
    private static final int[][][] PAGE_ICONS = {I_NOTE, I_RESTORE, I_FOLDER, I_PLAY, I_PALETTE, I_KEYCAP, I_CHECK};
    private static final String[] TITLES = {"Let's get started", "Restore a backup", "Your music", "Sound & playback", "Look & feel", "Controls", "You're all set"};
    private static final String[] SUBS = {
            "Here is what Melodix can do. Setup takes about a minute.",
            "Start fresh, or bring your settings over from a .melodix backup.",
            "Choose where Melodix looks for songs.",
            "Decide how music behaves while you play.",
            "Pick a theme and what shows up in the game.",
            "The keys you will use most.",
            "Melodix is ready. Here is what you chose."};
    private static final int LAST = PAGE_NAMES.length - 1;
    private static final String[] THEME_IDS = {"dark", "light", "ocean", "nether", "end", "grass"};

    private static final int SW = 0, SEG = 1, KEY = 2, BTN = 3, PATH = 4, INFO = 5, THEMES = 6;

    private static final class Row {
        int type;
        int[][] icon;
        String label, desc;
        BooleanSupplier get;
        Consumer<Boolean> set;
        String[] opts;
        int sel;
        Consumer<Integer> pick;
        String text; // key cap text, button label or path
        Runnable action;
    }

    private static final class Card {
        final String title;
        final List<Row> rows = new ArrayList<>();
        Card(String title) { this.title = title; }
    }

    private int step;
    // restore step
    private boolean importMode;
    private Path backupFile;
    private DataExporter.FileInfo backupInfo;
    private String fileErr = "";
    private boolean restored;
    private String restoreError = "";
    private final DataExporter.Options restoreOpts = new DataExporter.Options();
    private static final String LOGO = "musicplayer:textures/gui/melodix_logo.png";
    private final String version = Ports.host().modVersion();

    public OreSetupScreen() {
        super("Melodix Setup");
    }

    @Override
    protected int activeTab() { return -1; }

    @Override
    protected void renderHeader(Gfx g, int mx, int my) { }

    @Override
    protected void renderBody(Gfx g, int mx, int my) { }

    /** Everything is drawn in the overlay, so the search box and top bar stay out of the way. */
    @Override
    protected boolean modal() { return true; }

    @Override
    public boolean onKeyPressed(UiKey event) {
        if (event.key() == UiKeys.ESCAPE) {
            finish();
            return true;
        }
        return super.onKeyPressed(event);
    }

    @Override
    public boolean onMouseScrolled(double mx, double my, double sx, double sy) {
        scrollY = Math.max(0, Math.min(scrollMax, scrollY - (int) Math.round(sy * px(56))));
        return true;
    }

    @Override
    public void onClose() {
        finish();
    }

    private void go(int s) {
        step = Math.max(0, Math.min(LAST, s));
        scrollY = 0;
    }

    private void finish() {
        manager.getConfig().setWelcomeShown(true);
        UiHost.get().open(new OreHomeScreen());
    }

    // ---------------------------------------------------------------- state helpers

    private MusicPlayerConfig cfg() { return manager.getConfig(); }

    private Row sw(int[][] icon, String label, String desc, BooleanSupplier get, Consumer<Boolean> set) {
        Row r = new Row();
        r.type = SW; r.icon = icon; r.label = label; r.desc = desc; r.get = get; r.set = set;
        return r;
    }

    private Row seg(int[][] icon, String label, String desc, String[] opts, int sel, Consumer<Integer> pick) {
        Row r = new Row();
        r.type = SEG; r.icon = icon; r.label = label; r.desc = desc; r.opts = opts; r.sel = sel; r.pick = pick;
        return r;
    }

    private Row key(int[][] icon, String label, String desc, KeyBindingView km) {
        Row r = new Row();
        r.type = KEY; r.icon = icon; r.label = label; r.desc = desc;
        String t = km == null || km.unbound() ? "Unbound" : km.keyText();
        r.text = t == null || t.isEmpty() ? "Unbound" : t;
        return r;
    }

    private Row info(int[][] icon, String label, String desc) {
        Row r = new Row();
        r.type = INFO; r.icon = icon; r.label = label; r.desc = desc;
        return r;
    }

    private Row button(int[][] icon, String label, String desc, String btn, Runnable action) {
        Row r = new Row();
        r.type = BTN; r.icon = icon; r.label = label; r.desc = desc; r.text = btn; r.action = action;
        return r;
    }

    private List<List<Card>> columnsFor(int s) {
        MusicPlayerConfig c = cfg();
        List<List<Card>> cols = new ArrayList<>();
        if (s == 1) {
            if (importMode && backupInfo != null) {
                Card what = new Card("What to restore");
                what.rows.add(sw(I_EQ, "Settings", "Volume, playback, sound and in-game display options.", () -> restoreOpts.settings, b -> restoreOpts.settings = b));
                what.rows.add(sw(I_PALETTE, "Theme", "Your color theme.", () -> restoreOpts.theme, b -> restoreOpts.theme = b));
                what.rows.add(sw(I_LIST, "Playlists", "Your playlists and their song order.", () -> restoreOpts.playlists, b -> restoreOpts.playlists = b));
                what.rows.add(sw(I_KEYCAP, "Keybinds", "Your custom volume key and other bindings.", () -> restoreOpts.keys, b -> restoreOpts.keys = b));
                what.rows.add(sw(I_STATS, "Listening statistics", "Play counts and listening time.", () -> restoreOpts.stats, b -> restoreOpts.stats = b));
                cols.add(List.of(what));
            }
        } else if (s == 2) {
            Card folder = new Card("Music folder");
            Row path = new Row();
            path.type = PATH; path.icon = I_FOLDER; path.label = "Music folder";
            path.desc = "MP3, WAV and FLAC files are scanned from here, including subfolders.";
            path.text = c.getMusicFolderPath() == null || c.getMusicFolderPath().isEmpty() ? "No folder chosen yet" : c.getMusicFolderPath();
            path.action = this::chooseFolder;
            folder.rows.add(path);
            long n = manager.getCachedEditAllSongs().stream().filter(x -> x.getSource() != SongInfo.SourceType.VANILLA).count();
            folder.rows.add(info(I_CHECK, n + (n == 1 ? " song found" : " songs found"), n > 0 ? "Ready to play." : "Choose a folder to see your songs here."));
            Card startup = new Card("On startup");
            startup.rows.add(sw(I_BOOKMARK, "Resume where you left off", "Remember the queue and position between sessions.",
                    c::isQueuePersistence, c::setQueuePersistence));
            cols.add(List.of(folder, startup));
        } else if (s == 3) {
            Card trans = new Card("Transitions");
            trans.rows.add(sw(I_FADE, "Crossfade", "Blend one song into the next.", c::isCrossfadeEnabled, c::setCrossfadeEnabled));
            trans.rows.add(sw(I_PAUSE, "Fade on pause", "Ease volume out and back in.", c::isFadeOnPause, c::setFadeOnPause));
            Card sound = new Card("Sound");
            sound.rows.add(sw(I_EQ, "Equalizer", "Five bands and a preamp.", c::isEqEnabled, c::setEqEnabled));
            sound.rows.add(sw(I_REVERB, "Reverb", "Adds a sense of space.", c::isReverbEnabled, c::setReverbEnabled));
            cols.add(List.of(trans, sound));
            Card play = new Card("While you play");
            play.rows.add(sw(I_MUTE, "Mute Minecraft music", "Silence the game's own music.", c::isSuppressVanillaMusic, c::setSuppressVanillaMusic));
            play.rows.add(sw(I_WINDOWED, "Pause when unfocused", "Pause when you tab away.", c::isStopWhenUnfocused, c::setStopWhenUnfocused));
            play.rows.add(sw(I_CUBE, "Pause the world", "Single-player only.", c::isPauseOnOpen, c::setPauseOnOpen));
            play.rows.add(sw(I_MENU, "Pause on game menu", "Pause on the pause screen.", c::isPauseOnPauseScreen, c::setPauseOnPauseScreen));
            Card duck = new Card("Damage ducking");
            duck.rows.add(sw(I_HEART, "Lower volume when hit", "Dips when you take damage.", c::isDuckOnDamage, c::setDuckOnDamage));
            cols.add(List.of(play, duck));
        } else if (s == 4) {
            Card theme = new Card("Theme");
            Row t = new Row();
            t.type = THEMES;
            theme.rows.add(t);
            cols.add(List.of(theme));
            Card disp = new Card("In-game display");
            NowPlayingStyle cur = c.getNowPlayingStyle();
            NowPlayingStyle[] styles = {NowPlayingStyle.WIDGET, NowPlayingStyle.ABOVE_HOTBAR, NowPlayingStyle.OFF};
            disp.rows.add(seg(I_NOWPLAYING, "Now playing pop-up", "How the current song is shown.",
                    new String[]{"Widget", "Above hotbar", "Off"}, cur == null ? 0 : cur.ordinal(),
                    i -> c.setNowPlayingStyle(styles[Math.max(0, Math.min(2, i))])));
            disp.rows.add(sw(I_TOAST, "Song change toast", "Show a toast on a new song.", c::isShowNowPlayingToast, c::setShowNowPlayingToast));
            disp.rows.add(sw(I_MINI, "Mini player", "Floating controls while you play.", c::isMiniPlayerEnabled, c::setMiniPlayerEnabled));
            disp.rows.add(sw(I_VIZ, "Hotbar visualizer", "Frequency bars above the hotbar.", c::isVisualizerEnabled, c::setVisualizerEnabled));
            cols.add(List.of(disp));
        } else if (s == 5) {
            List<KeyBindingView> k = UiHost.get().keyBindings();
            Card playKeys = new Card("Playback keys");
            playKeys.rows.add(key(I_NOTE, "Open the player", "Open or close Melodix.", k.size() > 0 ? k.get(0) : null));
            playKeys.rows.add(key(I_PLAY, "Play / Pause", "Toggle playback.", k.size() > 1 ? k.get(1) : null));
            playKeys.rows.add(key(I_NEXT, "Next track", "Skip to the next song.", k.size() > 2 ? k.get(2) : null));
            playKeys.rows.add(key(I_PREV, "Previous track", "Back to the last song.", k.size() > 3 ? k.get(3) : null));
            cols.add(List.of(playKeys));
            Card volKeys = new Card("Volume keys");
            volKeys.rows.add(key(I_VOLUP, "Volume up", "Raise the volume.", k.size() > 4 ? k.get(4) : null));
            volKeys.rows.add(key(I_VOLDOWN, "Volume down", "Lower the volume.", k.size() > 5 ? k.get(5) : null));
            volKeys.rows.add(key(I_EQ, "Set custom volume", "Jump to your own level.", k.size() > 6 ? k.get(6) : null));
            volKeys.rows.add(button(I_KEYCAP, "Change keybinds", "Edited in Minecraft's Controls.", "Open Controls", this::openControls));
            cols.add(List.of(volKeys));
        }
        return cols;
    }

    private void chooseFolder() {
        if (blockedByFullscreen(this::chooseFolder)) return;
        String cur = cfg().getMusicFolderPath();
        String initial = cur == null || cur.isEmpty() ? System.getProperty("user.home") : cur;
        UiHost.get().selectFolder("Select your music folder", initial, result -> {
            if (result != null && !result.isEmpty() && new java.io.File(result).isDirectory()) {
                UiHost.get().runOnMain(() -> manager.setMusicFolder(result));
            }
        });
    }

    private void chooseBackup() {
        if (blockedByFullscreen(this::chooseBackup)) return;
        UiHost.get().openFile("Choose a Melodix backup", System.getProperty("user.home"), result -> {
            if (result == null || result.isEmpty()) return;
            UiHost.get().runOnMain(() -> {
                restoreError = "";
                Path p = Path.of(result);
                String n = p.getFileName().toString().toLowerCase(java.util.Locale.ROOT);
                if (!n.endsWith(DataExporter.EXTENSION)) {
                    fileErr = "That file is not a .melodix backup. Try another one.";
                    return;
                }
                try {
                    backupInfo = DataExporter.inspect(p);
                    backupFile = p;
                    fileErr = "";
                } catch (Exception e) {
                    fileErr = "That file is not a .melodix backup. Try another one.";
                }
            });
        });
    }

    private void clearBackup() {
        backupFile = null;
        backupInfo = null;
        fileErr = "";
        restoreError = "";
    }

    private boolean importPending() { return step == 1 && importMode && backupInfo == null; }

    private void next() {
        if (step == 1) {
            if (importMode) {
                if (backupInfo != null) runRestore();
                return;
            }
            restored = false;
            go(2);
            return;
        }
        if (step < LAST) go(step + 1); else finish();
    }

    private void runRestore() {
        try {
            DataExporter.importFrom(backupFile, restoreOpts);
            restored = true;
            restoreError = "";
            go(LAST);
        } catch (Exception e) {
            restoreError = "Restore failed: " + e.getMessage();
        }
    }

    /** Pixel-art icon with the design's even-odd fill (overlapping rectangles cut holes), centred in a size box. */
    private void eo(Gfx g, int[][] rects, int gw, int gh, int x, int y, int size, int color) {
        int k = Math.max(1, Math.round(size / (float) Math.max(gw, gh)));
        int ox = x + (size - gw * k) / 2, oy = y + (size - gh * k) / 2;
        int[][] n = new int[gh][gw];
        for (int[] r : rects) {
            for (int yy = r[1]; yy < r[1] + r[3] && yy < gh; yy++) {
                for (int xx = r[0]; xx < r[0] + r[2] && xx < gw; xx++) n[yy][xx]++;
            }
        }
        for (int yy = 0; yy < gh; yy++) {
            int xx = 0;
            while (xx < gw) {
                if ((n[yy][xx] & 1) == 1) {
                    int st = xx;
                    while (xx < gw && (n[yy][xx] & 1) == 1) xx++;
                    g.fill(ox + st * k, oy + yy * k, ox + xx * k, oy + (yy + 1) * k, color);
                } else {
                    xx++;
                }
            }
        }
    }

    private void logo(Gfx g, int x, int y, int size) {
        int b2 = px(2);
        g.fill(x, y, x + size, y + size, th.edge());
        g.blit(LOGO, x + b2, y + b2, x + size - b2, y + size - b2, 0f, 1f, 0f, 1f);
    }

    private void openControls() {
        UiHost.get().openKeybindsScreen(this);
    }

    // ---------------------------------------------------------------- drawing

    private int railW() { return Math.min(px(256), this.width / 4); }

    private int footH() { return px(84); }

    @Override
    protected void renderOverlay(Gfx g, int mx, int my) {
        int rw = railW(), fh = footH();
        int mainBottom = this.height - fh;

        drawMain(g, mx, my, rw, mainBottom);
        drawRail(g, mx, my, rw);
        drawFooter(g, mx, my, rw, fh);
    }

    private void drawRail(Gfx g, int mx, int my, int rw) {
        int b2 = px(2);
        g.fill(0, 0, rw, this.height, th.edge());
        g.fill(0, 0, rw - b2, this.height, th.panel());
        g.fill(0, 0, rw - b2, b2, th.hi());
        int pad = px(16);
        int y = px(20);
        int logo = px(52);
        logo(g, pad + px(4), y, logo);
        int tx = pad + px(4) + logo + px(14);
        text(g, "Melodix", tx, y + px(6), th.text(), true, 20);
        text(g, "Setup", tx, y + px(6) + capH(20) + px(6), th.dim(), false, 14);
        y += logo + px(18);

        int rowH = px(52);
        for (int i = 0; i <= LAST; i++) {
            int ry = y + i * (rowH + px(6));
            final int idx = i;
            int bw = rw - 2 * pad;
            boolean on = i == step;
            if (on) {
                button(g, mx, my, pad, ry, bw, rowH, GREEN, () -> go(idx));
            } else {
                boolean hov = hit(mx, my, pad, ry, bw, rowH, () -> go(idx));
                if (hov) g.fill(pad, ry, pad + bw, ry + rowH, th.rowHover());
            }
            int col = on ? 0xFFFFFFFF : th.text();
            eo(g, PAGE_ICONS[i], 12, 12, pad + px(16), ry + (rowH - px(24)) / 2 - (on ? px(2) : 0), px(24), col);
            text(g, fit(PAGE_NAMES[i], bw - px(16) - px(24) - px(14) - px(16) - px(30), false, 16), pad + px(16) + px(24) + px(14),
                    ry + (rowH - capH(16)) / 2 - (on ? px(2) : 0), col, false, 16);
            if (i < step) {
                eo(g, I_CHECK_SM, 8, 8, pad + bw - px(16) - px(16), ry + (rowH - px(16)) / 2, px(16), on ? 0xFFFFFFFF : th.accent());
            }
        }

        // progress
        int py = this.height - px(20) - px(16) - px(14) - capH(14) - px(14) - capH(14);
        int pct = Math.round((step + 1) * 100f / PAGE_NAMES.length);
        text(g, "Step " + (step + 1) + " of " + PAGE_NAMES.length, pad + px(4), py, th.dim(), false, 14);
        String p = pct + "%";
        text(g, p, rw - pad - px(4) - px(2) - tw(p, false, 14), py, th.dim(), false, 14);
        int by = py + capH(14) + px(8);
        int bw2 = rw - 2 * pad - px(8) - b2;
        inset(g, pad + px(4), by, bw2, px(16));
        g.fill(pad + px(4) + b2, by + b2, pad + px(4) + b2 + Math.round((bw2 - 2 * b2) * pct / 100f), by + px(16) - b2, OreTheme.GREEN);
        text(g, "Melodix " + version, pad + px(4), by + px(16) + px(14), th.dim(), false, 14);
    }

    private void drawFooter(Gfx g, int mx, int my, int rw, int fh) {
        int b2 = px(2);
        int fy = this.height - fh;
        g.fill(rw, fy, this.width, this.height, th.edge());
        g.fill(rw, fy + b2, this.width, this.height, th.panel());
        g.fill(rw, fy + b2, this.width, fy + 2 * b2, th.hi());
        int bh = px(48), by = fy + (fh - bh) / 2;
        int pad = px(32);

        if (step < LAST) {
            String skip = "Skip setup";
            int sw = tw(skip, false, 16) + px(40);
            button(g, mx, my, rw + pad, by, sw, bh, NORMAL, this::finish);
            textBtn(g, skip, false, 16, rw + pad, by, sw, bh, th.text());
        }

        int dot = px(14), gap = px(10);
        int dx = rw + (this.width - rw - (PAGE_NAMES.length * dot + (PAGE_NAMES.length - 1) * gap)) / 2;
        for (int i = 0; i < PAGE_NAMES.length; i++) {
            int x = dx + i * (dot + gap), y = by + (bh - dot) / 2;
            g.fill(x, y, x + dot, y + dot, th.edge());
            g.fill(x + b2, y + b2, x + dot - b2, y + dot - b2, i < step ? OreTheme.GREEN : i == step ? th.text() : th.inset());
        }

        String next = step == 0 ? "Get started" : step == 1 && importMode ? (backupInfo != null ? "Restore & continue" : "Choose a file first")
                : step < LAST ? "Next" : "Start listening";
        int nw = Math.max(px(160), tw(next, false, 16) + px(70) + (step < LAST ? px(10) : 0));
        int nx = this.width - pad - nw;
        boolean pending = importPending();
        button(g, mx, my, nx, by, nw, bh, GREEN, pending ? null : this::next);
        int nextCol = pending ? 0x73FFFFFF : 0xFFFFFFFF;
        int arrow = px(18);
        int cw = tw(next, false, 16) + (step < LAST ? px(10) + arrow : 0);
        int cx = nx + (nw - cw) / 2;
        text(g, next, cx, by + (bh - capH(16)) / 2 - px(2) + pressDy, nextCol, false, 16);
        if (step < LAST) {
            int ax = cx + tw(next, false, 16) + px(10);
            eo(g, new int[][]{{1, 4, 6, 1}, {5, 2, 1, 5}, {6, 3, 1, 3}}, 9, 9, ax, by + (bh - arrow) / 2 - px(2) + pressDy, arrow, nextCol);
        }
        if (step > 0) {
            String back = "Back";
            int bw = tw(back, false, 16) + px(70);
            int bx = nx - px(12) - bw;
            button(g, mx, my, bx, by, bw, bh, NORMAL, () -> go(step - 1));
            int cw2 = tw(back, false, 16) + px(10) + arrow;
            int cx2 = bx + (bw - cw2) / 2;
            eo(g, new int[][]{{2, 4, 6, 1}, {3, 2, 1, 5}, {2, 3, 1, 3}}, 9, 9, cx2, by + (bh - arrow) / 2 - px(2) + pressDy, arrow, th.text());
            text(g, back, cx2 + arrow + px(10), by + (bh - capH(16)) / 2 - px(2) + pressDy, th.text(), false, 16);
        }
    }

    private void drawMain(Gfx g, int mx, int my, int rw, int mainBottom) {
        g.fill(rw, 0, this.width, mainBottom, th.bg());
        int pad = px(32);
        int x = rw + pad, w = this.width - rw - 2 * pad;
        g.enableScissor(rw, 0, this.width, mainBottom);
        int y = px(24) - scrollY;

        // header
        text(g, "Step " + (step + 1) + " of " + PAGE_NAMES.length, x, y, th.accent(), true, 14);
        text(g, fit(TITLES[step], w, true, 28), x, y + capH(14) + px(6), th.text(), true, 28);
        int subY = y + capH(14) + px(6) + capH(28) + px(8);
        String note = "Changes save automatically";
        int noteW = tw(note, false, 14) + px(24);
        boolean noteFits = w > tw(SUBS[step], false, 16) + noteW + px(24);
        text(g, fit(SUBS[step], w, false, 16), x, subY, th.dim(), false, 16);
        if (noteFits) {
            int nx = x + w - noteW;
            eo(g, I_CHECK_SM, 8, 8, nx, subY, px(16), th.dim());
            text(g, note, nx + px(24), subY + px(1), th.dim(), false, 14);
        }
        y = subY + capH(16) + px(20);

        int bottom;
        if (step == 0) bottom = drawWelcome(g, x, y, w, mainBottom);
        else if (step == 1) bottom = drawRestore(g, mx, my, x, y, w, mainBottom);
        else if (step == LAST) bottom = drawReady(g, mx, my, x, y, w, mainBottom);
        else bottom = drawColumns(g, mx, my, x, y, w, mainBottom);
        g.disableScissor();

        int contentH = bottom + scrollY + px(24);
        scrollMax = Math.max(0, contentH - mainBottom);
        scrollY = Math.max(0, Math.min(scrollMax, scrollY));
        if (scrollMax > 0) scrollbarAt(g, this.width - px(16), 0, mainBottom);
    }

    private int drawWelcome(Gfx g, int x, int y, int w, int mainBottom) {
        boolean wide = w >= px(720);
        int gap = px(16);
        int leftW = wide ? px(340) : w;
        String[][] feats = {
                {"Playlists", "Build playlists from your own MP3 and WAV files."},
                {"In-game widgets", "A now-playing pop-up, mini player and hotbar visualizer."},
                {"Sound", "Five-band equalizer, reverb and crossfade."},
                {"Listening stats", "See your top songs and total listening time."}};
        int[][][] icons = {I_LIST, I_NOWPLAYING, I_EQ, I_STATS};
        int gw = wide ? w - leftW - gap : w;
        int cw = (gw - gap) / 2;
        int tile = px(56), cpad = px(20);

        // heights are measured from the text so nothing is ever cramped
        int need = 0;
        for (String[] f : feats) {
            int lines = wrap(f[1], cw - 2 * cpad, false, 15).size();
            need = Math.max(need, px(16) + tile + px(14) + capH(20) + px(6) + lines * (capH(15) + px(4)) + px(16));
        }
        String blurb = "A music player for Minecraft. Play your own songs without leaving the game.";
        List<String> blines = wrap(blurb, leftW - px(48), false, 16);
        int logoS = px(112);
        int heroNeed = px(24) + logoS + px(16) + capH(36) + px(16) + blines.size() * (capH(16) + px(6)) + px(24);
        int h1 = Math.max(Math.max(px(300), heroNeed), 2 * need + gap);
        h1 = Math.max(h1, mainBottom - y - px(24));
        int ch = (h1 - gap) / 2;

        // hero, content centred vertically
        card(g, x, y, leftW, h1);
        int contentH = logoS + px(16) + capH(36) + px(16) + blines.size() * (capH(16) + px(6)) - px(6);
        int ty0 = y + (h1 - contentH) / 2;
        logo(g, x + (leftW - logoS) / 2, ty0, logoS);
        String name = "Melodix";
        int ny = ty0 + logoS + px(16);
        text(g, name, x + (leftW - tw(name, true, 36)) / 2, ny, th.text(), true, 36);
        int ty = ny + capH(36) + px(16);
        for (String l : blines) {
            text(g, l, x + (leftW - tw(l, false, 16)) / 2, ty, th.dim(), false, 16);
            ty += capH(16) + px(6);
        }

        int gx = wide ? x + leftW + gap : x;
        int gy = wide ? y : y + h1 + gap;
        for (int i = 0; i < 4; i++) {
            int cx = gx + (i % 2) * (cw + gap), cy = gy + (i / 2) * (ch + gap);
            card(g, cx, cy, cw, ch);
            int lines = wrap(feats[i][1], cw - 2 * cpad, false, 15).size();
            int blockH = tile + px(14) + capH(20) + px(6) + lines * (capH(15) + px(4)) - px(4);
            int by = cy + (ch - blockH) / 2;
            inset(g, cx + cpad, by, tile, tile);
            eo(g, icons[i], 12, 12, cx + cpad, by, tile, th.accent());
            int tx = cx + cpad, tyy = by + tile + px(14);
            text(g, fit(feats[i][0], cw - 2 * cpad, true, 20), tx, tyy, th.text(), true, 20);
            tyy += capH(20) + px(6);
            for (String l : wrap(feats[i][1], cw - 2 * cpad, false, 15)) {
                text(g, l, tx, tyy, th.dim(), false, 15);
                tyy += capH(15) + px(4);
            }
        }
        return wide ? y + h1 : gy + h1;
    }

    private static String kb(long n) {
        return n < 1024 ? n + " B" : String.format(java.util.Locale.ROOT, "%.1f KB", n / 1024.0);
    }

    private int drawRestore(Gfx g, int mx, int my, int x, int y, int w, int mainBottom) {
        int gap = px(16), b2 = px(2);
        String[][] ch = {{"Start fresh", "Set Melodix up from scratch in the next few steps."},
                {"Import a backup", "Restore your settings from a .melodix file."}};
        int[][][] icons = {I_SPARKLE, I_RESTORE};
        boolean twoCol = w >= px(560);
        int cw = twoCol ? (w - gap) / 2 : w;
        int tile = px(48), pad = px(20);
        int textW = cw - 2 * pad - tile - px(16) - px(20) - px(12);
        int rowH = 0;
        for (String[] c : ch) {
            int lines = wrap(c[1], textW, false, 14).size();
            rowH = Math.max(rowH, Math.max(tile, capH(20) + px(4) + lines * (capH(14) + px(4))) + 2 * pad);
        }
        for (int i = 0; i < 2; i++) {
            int cx = twoCol ? x + i * (cw + gap) : x, cy = twoCol ? y : y + i * (rowH + gap);
            boolean sel = (i == 1) == importMode;
            if (sel) g.fill(cx - b2, cy - b2, cx + cw + b2, cy + rowH + b2, OreTheme.GREEN);
            card(g, cx, cy, cw, rowH);
            final int idx = i;
            hit(mx, my, cx, cy, cw, rowH, cy < mainBottom ? () -> { importMode = idx == 1; } : null);
            int ty = cy + (rowH - tile) / 2;
            inset(g, cx + pad, ty, tile, tile);
            eo(g, icons[i], 12, 12, cx + pad, ty, tile, sel ? th.accent() : th.dim());
            int tx = cx + pad + tile + px(16);
            List<String> lines = wrap(ch[i][1], textW, false, 14);
            int blockH = capH(20) + px(4) + lines.size() * (capH(14) + px(4)) - px(4);
            int ly = cy + (rowH - blockH) / 2;
            text(g, fit(ch[i][0], textW, true, 20), tx, ly, th.text(), true, 20);
            ly += capH(20) + px(4);
            for (String l : lines) {
                text(g, l, tx, ly, th.dim(), false, 14);
                ly += capH(14) + px(4);
            }
            if (sel) eo(g, I_CHECK_SM, 8, 8, cx + cw - pad - px(20), cy + (rowH - px(20)) / 2, px(20), 0xFF6CC349);
        }
        int bottom = twoCol ? y + rowH : y + 2 * rowH + gap;
        if (!importMode) return bottom;

        // file card
        int fy = bottom + gap;
        int innerPad = px(20);
        int iw = w - 2 * innerPad;
        int big = px(64);
        int fh;
        if (backupInfo == null) {
            String t = "Choose file";
            int bw = tw(t, false, 16) + px(24) * 2 + px(10) + px(18);
            int bh = px(48);
            int boxPad = px(20);
            int textW2 = iw - 2 * boxPad - big - px(20) - bw - px(20);
            List<String> dl = wrap("Pick a .melodix file exported from Melodix. You can export one from Settings, Advanced.", Math.max(px(120), textW2), false, 15);
            int errH = fileErr.isEmpty() ? 0 : px(8) + wrap(fileErr, Math.max(px(120), textW2), false, 14).size() * (capH(14) + px(4));
            int textH = capH(20) + px(4) + dl.size() * (capH(15) + px(4)) + errH;
            int boxH = Math.max(big, textH) + 2 * boxPad;
            fh = boxH + 2 * innerPad;
            card(g, x, fy, w, fh);
            int bx = x + innerPad, by = fy + innerPad;
            g.fill(bx, by, bx + iw, by + boxH, th.inset());
            dashed(g, bx, by, iw, boxH, th.sep());
            int tx0 = bx + boxPad, ty0 = by + (boxH - big) / 2;
            g.fill(tx0, ty0, tx0 + big, ty0 + big, th.edge());
            g.fill(tx0 + b2, ty0 + b2, tx0 + big - b2, ty0 + big - b2, th.card());
            eo(g, I_UPLOAD, 12, 12, tx0, ty0, big, th.accent());
            int tx = tx0 + big + px(20);
            int blockH = textH - (errH > 0 ? 0 : 0);
            int ly = by + (boxH - blockH) / 2;
            text(g, "Choose your backup file", tx, ly, th.text(), true, 20);
            ly += capH(20) + px(4);
            for (String l : dl) {
                text(g, l, tx, ly, th.dim(), false, 15);
                ly += capH(15) + px(4);
            }
            if (!fileErr.isEmpty()) {
                ly += px(4);
                for (String l : wrap(fileErr, Math.max(px(120), textW2), false, 14)) {
                    text(g, l, tx, ly, 0xFFFF7B6B, true, 14);
                    ly += capH(14) + px(4);
                }
            }
            int bbx = bx + iw - boxPad - bw, bby = by + (boxH - bh) / 2;
            boolean live = fy < mainBottom;
            button(g, mx, my, bbx, bby, bw, bh, GREEN, live ? this::chooseBackup : null);
            int cwid = tw(t, false, 16) + px(10) + px(18);
            int cx0 = bbx + (bw - cwid) / 2;
            eo(g, I_FOLDER, 12, 12, cx0, bby + (bh - px(18)) / 2 - px(2) + pressDy, px(18), 0xFFFFFFFF);
            text(g, t, cx0 + px(18) + px(10), bby + (bh - capH(16)) / 2 - px(2) + pressDy, 0xFFFFFFFF, false, 16);
        } else {
            int bh = px(44);
            String again = "Choose another";
            int aw = tw(again, false, 16) + px(40);
            String ready = "Ready to restore";
            int rw = tw(ready, true, 16) + px(8) + px(18);
            int textW2 = iw - big - px(20) - rw - px(20) - aw - px(20);
            String meta = kb(backupInfo.bytes()) + " · Saved " + backupInfo.saved().format(
                    java.time.format.DateTimeFormatter.ofPattern("d MMM yyyy", java.util.Locale.ENGLISH));
            int errH = restoreError.isEmpty() ? 0 : px(12) + wrap(restoreError, iw, false, 14).size() * (capH(14) + px(4));
            int rowH2 = big;
            fh = rowH2 + 2 * innerPad + errH;
            card(g, x, fy, w, fh);
            int tx0 = x + innerPad, ty0 = fy + innerPad;
            g.fill(tx0, ty0, tx0 + big, ty0 + big, th.edge());
            g.fill(tx0 + b2, ty0 + b2, tx0 + big - b2, ty0 + big - b2, th.inset());
            eo(g, I_FILE, 12, 12, tx0, ty0, big, th.accent());
            int tx = tx0 + big + px(20);
            int blockH = capH(20) + px(4) + capH(15);
            int ly = ty0 + (big - blockH) / 2;
            text(g, fit(backupInfo.name(), Math.max(px(100), textW2), true, 20), tx, ly, th.text(), true, 20);
            text(g, fit(meta, Math.max(px(100), textW2), false, 15), tx, ly + capH(20) + px(4), th.dim(), false, 15);
            int ax = x + w - innerPad - aw;
            int ay = ty0 + (big - bh) / 2;
            boolean live = fy < mainBottom;
            button(g, mx, my, ax, ay, aw, bh, NORMAL, live ? this::clearBackup : null);
            textBtn(g, again, false, 16, ax, ay, aw, bh, th.text());
            int rx = ax - px(20) - rw;
            eo(g, I_CHECK_SM, 8, 8, rx, ty0 + (big - px(18)) / 2, px(18), th.accent());
            text(g, ready, rx + px(18) + px(8), ty0 + (big - capH(16)) / 2, th.accent(), true, 16);
            if (!restoreError.isEmpty()) {
                int ey = ty0 + big + px(12);
                for (String l : wrap(restoreError, iw, false, 14)) {
                    text(g, l, tx0, ey, 0xFFFF7B6B, true, 14);
                    ey += capH(14) + px(4);
                }
            }
        }
        int after = fy + fh;
        if (backupInfo != null) {
            int end = drawColumns(g, mx, my, x, after + gap, w, mainBottom);
            return end;
        }
        return after;
    }

    private void dashed(Gfx g, int x, int y, int w, int h, int color) {
        int b2 = px(2), dash = px(8);
        for (int i = 0; i < w; i += dash * 2) {
            int e = Math.min(w, i + dash);
            g.fill(x + i, y, x + e, y + b2, color);
            g.fill(x + i, y + h - b2, x + e, y + h, color);
        }
        for (int i = 0; i < h; i += dash * 2) {
            int e = Math.min(h, i + dash);
            g.fill(x, y + i, x + b2, y + e, color);
            g.fill(x + w - b2, y + i, x + w, y + e, color);
        }
    }

    private int drawReady(Gfx g, int mx, int my, int x, int y, int w, int mainBottom) {
        MusicPlayerConfig c = cfg();
        int bw = px(80);
        String title = restored ? "Backup restored" : "Your settings are saved";
        String body = restored
                ? "Restored from " + (backupInfo != null ? backupInfo.name() : "your backup") + ". Anything you skipped keeps its default value, and you can change it later from Settings."
                : "Everything you chose lives in config/musicplayer.json. Change any of it later from Settings.";
        int tx = x + px(24) + bw + px(22), tw0 = w - (tx - x) - px(24);
        List<String> bl = wrap(body, tw0, false, 16);
        int textH = capH(22) + px(6) + bl.size() * (capH(16) + px(6)) - px(6);
        int ch = Math.max(bw, textH) + px(44);
        card(g, x, y, w, ch);
        button(g, 0, 0, x + px(24), y + (ch - bw) / 2, bw, bw, GREEN, null);
        eo(g, I_CHECK, 12, 12, x + px(24), y + (ch - bw) / 2 - px(2), bw, 0xFFFFFFFF);
        int ly = y + (ch - textH) / 2;
        text(g, title, tx, ly, th.text(), true, 22);
        ly += capH(22) + px(6);
        for (String l : bl) {
            text(g, l, tx, ly, th.dim(), false, 16);
            ly += capH(16) + px(6);
        }
        y += ch + px(16);

        String[][] recap;
        int[][][] icons;
        if (restored) {
            recap = new String[][]{
                    {"Backup file", backupInfo != null ? backupInfo.name() : "None"},
                    {"Settings", restoreOpts.settings ? "Restored" : "Skipped"},
                    {"Theme", restoreOpts.theme ? "Restored" : "Skipped"},
                    {"Playlists", restoreOpts.playlists ? "Restored" : "Skipped"},
                    {"Keybinds", restoreOpts.keys ? "Restored" : "Skipped"},
                    {"Listening statistics", restoreOpts.stats ? "Restored" : "Skipped"}};
            icons = new int[][][]{I_FILE, I_EQ, I_PALETTE, I_LIST, I_KEYCAP, I_STATS};
        } else {
            String folder = c.getMusicFolderPath() == null || c.getMusicFolderPath().isEmpty() ? "Not set" : c.getMusicFolderPath();
            NowPlayingStyle np = c.getNowPlayingStyle();
            String npName = np == NowPlayingStyle.ABOVE_HOTBAR ? "Above hotbar" : np == NowPlayingStyle.OFF ? "Off" : "Widget";
            ColorTheme ct = ColorTheme.get(c.getThemeId());
            recap = new String[][]{
                    {"Music folder", folder}, {"Crossfade", c.isCrossfadeEnabled() ? "On" : "Off"}, {"Equalizer", c.isEqEnabled() ? "On" : "Off"},
                    {"Theme", ct.name()}, {"Now playing", npName}, {"Mini player", c.isMiniPlayerEnabled() ? "On" : "Off"}};
            icons = new int[][][]{I_FOLDER, I_FADE, I_EQ, I_PALETTE, I_NOWPLAYING, I_MINI};
        }
        int cols = w >= px(760) ? 3 : w >= px(480) ? 2 : 1;
        int gap = px(16), cw = (w - gap * (cols - 1)) / cols;
        int tile = px(48), rh = tile + px(32);
        for (int i = 0; i < recap.length; i++) {
            int cx = x + (i % cols) * (cw + gap), cy = y + (i / cols) * (rh + gap);
            card(g, cx, cy, cw, rh);
            int ty = cy + (rh - tile) / 2;
            inset(g, cx + px(18), ty, tile, tile);
            eo(g, icons[i], 12, 12, cx + px(18), ty, tile, th.accent());
            int rx = cx + px(18) + tile + px(14), rwid = cw - (rx - cx) - px(14);
            int blockH = capH(14) + px(4) + capH(16);
            int by = cy + (rh - blockH) / 2;
            text(g, fit(recap[i][0], rwid, false, 14), rx, by, th.dim(), false, 14);
            boolean on = recap[i][1].equals("On") || recap[i][1].equals("Restored");
            text(g, fit(recap[i][1], rwid, true, 16), rx, by + capH(14) + px(4), on ? th.accent() : th.text(), true, 16);
        }
        int rows = (recap.length + cols - 1) / cols;
        return y + rows * (rh + gap) - gap;
    }

    private int drawColumns(Gfx g, int mx, int my, int x, int y, int w, int mainBottom) {
        List<List<Card>> cols = columnsFor(step);
        int gap = px(16);
        boolean twoCol = cols.size() > 1 && w >= px(700);
        int colW = twoCol ? (w - gap) / 2 : w;
        int end = y;
        if (twoCol) {
            for (int ci = 0; ci < cols.size(); ci++) {
                int cy = y;
                for (Card c : cols.get(ci)) cy += drawCard(g, mx, my, x + ci * (colW + gap), cy, colW, c, mainBottom) + gap;
                end = Math.max(end, cy - gap);
            }
        } else {
            int cy = y;
            for (List<Card> col : cols) {
                for (Card c : col) cy += drawCard(g, mx, my, x, cy, colW, c, mainBottom) + gap;
            }
            end = cy - gap;
        }
        return end;
    }

    private int rowTextW(int cardW, Row r) {
        int ctrl = switch (r.type) {
            case SW -> px(88);
            case KEY -> Math.max(px(48), tw(r.text, false, 16) + px(28));
            case BTN -> tw(r.text, false, 16) + px(36);
            default -> 0;
        };
        int w = cardW - 2 * px(20) - px(48) - px(16) - (ctrl > 0 ? ctrl + px(16) : 0);
        return Math.max(px(80), w);
    }

    private int drawCard(Gfx g, int mx, int my, int x, int y, int w, Card c, int mainBottom) {
        // measure
        int[] heights = new int[c.rows.size()];
        int total = px(14) + capH(20) + px(12);
        for (int i = 0; i < heights.length; i++) {
            heights[i] = rowHeight(c.rows.get(i), w);
            total += heights[i];
        }
        total += px(4);
        card(g, x, y, w, total);
        text(g, fit(c.title, w - px(40), true, 20), x + px(20), y + px(14), th.text(), true, 20);
        int ry = y + px(14) + capH(20) + px(12);
        for (int i = 0; i < heights.length; i++) {
            drawRow(g, mx, my, x, ry, w, c.rows.get(i), heights[i], mainBottom);
            ry += heights[i];
        }
        return total;
    }

    private int rowHeight(Row r, int w) {
        if (r.type == THEMES) {
            int cols = w >= px(460) ? 3 : 2;
            int n = THEME_IDS.length;
            int rows = (n + cols - 1) / cols;
            int tileH = px(56) + capH(16) + px(22);
            return px(2) + px(16) + capH(16) + px(6) + capH(14) + px(14) + rows * (tileH + px(12)) + px(8);
        }
        int tw0 = rowTextW(w, r);
        int lines = wrap(r.desc == null ? "" : r.desc, tw0, false, 14).size();
        int h = px(14) + capH(16) + px(4) + Math.max(1, lines) * (capH(14) + px(4)) + px(14);
        if (r.type == SEG) h += px(10) + px(40);
        if (r.type == PATH) h += px(10) + px(44);
        return Math.max(px(64) + px(2), h + px(2));
    }

    private void drawRow(Gfx g, int mx, int my, int cx, int y, int cw, Row r, int h, int mainBottom) {
        int b2 = px(2);
        g.fill(cx + px(20), y, cx + cw - px(20), y + b2, th.sep());
        int ix = cx + px(20), iw = cw - px(40);
        boolean live = y >= 0 && y + h <= mainBottom + px(2);

        if (r.type == THEMES) {
            drawThemes(g, mx, my, cx, y + b2, cw, live);
            return;
        }
        int top = y + b2;
        int tile = px(48);
        int tileY = top + px(12);
        inset(g, ix, tileY, tile, tile);
        eo(g, r.icon, 12, 12, ix + (tile - px(24)) / 2, tileY + (tile - px(24)) / 2, px(24),
                (r.type == SW && r.get != null && !r.get.getAsBoolean()) ? th.dim() : th.accent());
        int tx = ix + tile + px(16);
        int textW = rowTextW(cw, r);
        text(g, fit(r.label, textW, true, 16), tx, top + px(14), th.text(), true, 16);
        int dy = top + px(14) + capH(16) + px(4);
        for (String l : wrap(r.desc == null ? "" : r.desc, textW, false, 14)) {
            text(g, l, tx, dy, th.dim(), false, 14);
            dy += capH(14) + px(4);
        }
        int ctrlRight = ix + iw;
        int midY = top + px(12) + tile / 2;

        switch (r.type) {
            case SW -> {
                boolean on = r.get.getAsBoolean();
                int sy = midY - px(18);
                drawSwitch(g, mx, my, ctrlRight - px(88), sy, on, r, true, live ? () -> r.set.accept(!on) : null);
            }
            case KEY -> {
                int kw = Math.max(px(48), tw(r.text, false, 16) + px(28)), kh = px(36);
                g.fill(ctrlRight - kw, midY - kh / 2, ctrlRight, midY + kh / 2, th.edge());
                g.fill(ctrlRight - kw + b2, midY - kh / 2 + b2, ctrlRight - b2, midY + kh / 2 - b2, th.inset());
                text(g, r.text, ctrlRight - kw + (kw - tw(r.text, false, 16)) / 2, midY - capH(16) / 2,
                        r.text.equals("Unbound") ? th.dim() : th.text(), false, 16);
            }
            case BTN -> {
                int bw = tw(r.text, false, 16) + px(36), bh = px(40);
                button(g, mx, my, ctrlRight - bw, midY - bh / 2, bw, bh, NORMAL, live ? r.action : null);
                textBtn(g, r.text, false, 16, ctrlRight - bw, midY - bh / 2, bw, bh, th.text());
            }
            case SEG -> {
                int bx = tx, by = dy + px(6);
                for (int i = 0; i < r.opts.length; i++) {
                    int bw = tw(r.opts[i], false, 16) + px(32), bh = px(40);
                    if (bx + bw > ix + iw && bx > tx) { bx = tx; by += bh + px(6); }
                    final int idx = i;
                    boolean sel = i == r.sel;
                    button(g, mx, my, bx, by, bw, bh, sel ? GREEN : NORMAL, live ? () -> r.pick.accept(idx) : null);
                    textBtn(g, r.opts[i], false, 16, bx, by, bw, bh, sel ? 0xFFFFFFFF : th.text());
                    bx += bw + px(6);
                }
            }
            case PATH -> {
                int by = dy + px(6), bh = px(44);
                String browse = "Browse";
                int bw = tw(browse, false, 16) + px(40);
                int fw = ix + iw - tx - bw - px(10);
                inset(g, tx, by, fw, bh);
                text(g, fit(r.text, fw - px(28), false, 16), tx + px(14), by + (bh - capH(16)) / 2, th.dim(), false, 16);
                button(g, mx, my, tx + fw + px(10), by, bw, bh, NORMAL, live ? r.action : null);
                textBtn(g, browse, false, 16, tx + fw + px(10), by, bw, bh, th.text());
            }
            default -> { }
        }
    }

    private void drawThemes(Gfx g, int mx, int my, int cx, int top, int cw, boolean live) {
        int ix = cx + px(20), iw = cw - px(40);
        text(g, "Color theme", ix, top + px(16), th.text(), true, 16);
        text(g, fit("Tints the surfaces and the accent color across Melodix.", iw, false, 14), ix, top + px(16) + capH(16) + px(6), th.dim(), false, 14);
        int cols = cw >= px(460) ? 3 : 2;
        int gap = px(12);
        int tw0 = (iw - gap * (cols - 1)) / cols;
        int prevH = px(56), tileH = prevH + capH(16) + px(22);
        int gy = top + px(16) + capH(16) + px(6) + capH(14) + px(14);
        String cur = cfg().getThemeId();
        for (int i = 0; i < THEME_IDS.length; i++) {
            String id = THEME_IDS[i];
            int tx = ix + (i % cols) * (tw0 + gap), ty = gy + (i / cols) * (tileH + gap);
            boolean sel = id.equalsIgnoreCase(cur);
            OreTheme t = OreTheme.of(id);
            ColorTheme ct = ColorTheme.get(id);
            if (sel) g.fill(tx - px(2), ty - px(2), tx + tw0 + px(2), ty + tileH + px(2), OreTheme.GREEN);
            g.fill(tx, ty, tx + tw0, ty + tileH, th.edge());
            g.fill(tx + px(2), ty + px(2), tx + tw0 - px(2), ty + tileH - px(2), th.inset());
            int px0 = tx + px(2), py0 = ty + px(2), pw = tw0 - px(4);
            g.fill(px0, py0, px0 + pw, py0 + prevH, t.panel());
            g.fill(px0, py0, px0 + pw, py0 + px(12), t.card());
            g.fill(px0 + px(12), py0 + px(22), px0 + px(48), py0 + px(32), t.accent());
            g.fill(px0 + px(12), py0 + px(40), px0 + px(12) + Math.min(pw - px(24), px(70)), py0 + px(46), (t.text() & 0x00FFFFFF) | 0x73000000);
            text(g, fit(ct.name(), tw0 - px(40), false, 16), tx + px(10), py0 + prevH + px(10), th.text(), false, 16);
            if (sel) eo(g, I_CHECK_SM, 8, 8, tx + tw0 - px(26), py0 + prevH + px(10), px(16), 0xFF6CC349);
            hit(mx, my, tx, ty, tw0, tileH, live ? () -> cfg().setThemeId(id) : null);
        }
    }
}
