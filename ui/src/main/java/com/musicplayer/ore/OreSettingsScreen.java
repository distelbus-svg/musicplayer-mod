package com.musicplayer.ore;

import com.musicplayer.port.*;
import com.musicplayer.ui.*;

import com.musicplayer.DataExporter;
import com.musicplayer.EqPresetManager;
import com.musicplayer.MelodixApi;
import com.musicplayer.SongInfo;
import com.musicplayer.config.MusicPlayerConfig;
import com.musicplayer.config.MusicPlayerConfig.MiniPlayerStyle;
import com.musicplayer.config.MusicPlayerConfig.NowPlayingStyle;
import com.musicplayer.stats.ListeningStatisticsManager;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleFunction;
import java.util.function.DoubleSupplier;
import java.util.function.Supplier;

/** Settings, from the design's Settings.dc.html: a category rail and card sections, everything saved on change. */
public class OreSettingsScreen extends OreScreen {

    private static final int SWITCH = 0, SLIDER = 1, SEG = 2, PATH = 3, KEY = 4, BTN = 5, THEMES = 6, EQ = 7, STATS = 8, PRESETS = 9;
    private static final String[] CAT_IDS = {"sound", "playback", "appearance", "library", "controls", "advanced"};
    private static final String[] CAT_NAMES = {"Sound", "Playback", "Appearance", "Library", "Controls", "Advanced"};
    private static final int[][][] CAT_ICONS = {
            ICON_SPEAKER, ICON_PLAY, ICON_GRID, ICON_FOLDER,
            new int[][]{{1, 2, 10, 2}, {1, 8, 10, 2}, {1, 4, 2, 4}, {9, 4, 2, 4}}, ICON_GEAR};
    private static final int[][] CAT_GRID = {{8, 8}, {10, 10}, {12, 12}, {12, 12}, {12, 12}, {12, 12}};
    private static final String[][] HEADS = {
            {"Sound", "Volume, equalizer and reverb."},
            {"Playback", "Transitions, pausing and how music behaves in the game."},
            {"Appearance", "Theme, pop-ups and the in-game overlays."},
            {"Library", "Where your music lives and what you have listened to."},
            {"Controls", "Keybinds and the custom volume key."},
            {"Advanced", "Rarely needed options."}};
    private static final String[][] THEME_DEFS = { // id, name, panel, bar, accent, text
            {"dark", "Dark", "2D2D2D", "383838", "55FF55", "FFFFFF"},
            {"light", "Light", "F0F0F0", "E0E0E0", "228822", "222222"},
            {"ocean", "Ocean", "1A2B4A", "1F3A5A", "44DDFF", "CCEEFF"},
            {"nether", "Nether", "2A1010", "3A1515", "FF4400", "FF6666"},
            {"end", "The End", "1A1A2A", "222238", "AA66FF", "CCAAFF"},
            {"grass", "Grass", "1A3A1A", "1F4A1F", "55FF55", "CCFFCC"}};
    private static final String[][] BANDS = {
            {"Preamp", "gain"}, {"Bass", "60 Hz"}, {"Low mid", "230 Hz"}, {"Mid", "910 Hz"}, {"High mid", "3.6 kHz"}, {"Treble", "14 kHz"}};
    private static final String[][] KEY_TEXT = {
            {"Open the player", "Open or close Melodix from anywhere in the game."},
            {"Play / Pause", "Toggle playback."},
            {"Next track", "Skip to the next song."},
            {"Previous track", "Go back to the previous song."},
            {"Volume up", "Raise the volume by 5%."},
            {"Volume down", "Lower the volume by 5%."},
            {"Set custom volume", "Jump to your custom volume level."}};

    private static final class Row {
        int kind;
        String label = "", desc = "";
        BooleanSupplier when = () -> true;
        BooleanSupplier getB;
        Consumer<Boolean> setB;
        DoubleSupplier getD;
        DoubleConsumer setD;
        DoubleFunction<String> fmt;
        List<String> opts;
        Supplier<String> getS;
        Consumer<String> setS;
        Supplier<String> text;
        Runnable action;
        String btn;
        boolean browse;
        int keyIdx;
    }

    private record Section(String title, List<Row> rows) {}

    private final UiScreen parent;
    private final MusicPlayerConfig cfg;
    private final Map<String, List<Section>> cats = new LinkedHashMap<>();
    private int cat = 0;
    private String lastQuery = "";

    private enum Dialog { NONE, PRESETS, CONFIRM_IMPORT }

    private Dialog dialog = Dialog.NONE;
    private UiTextField presetName;
    private String renaming;
    private String dialogMsg = "";
    private boolean dialogErr;
    private int dialogScroll, dialogScrollMax;
    private Path pendingImport;
    // Built-in categories first, then any registered through MelodixApi.
    private final List<String> catIds = new ArrayList<>(List.of(CAT_IDS));
    private final List<String> catNames = new ArrayList<>(List.of(CAT_NAMES));
    private final List<String[]> heads = new ArrayList<>(List.of(HEADS));

    public OreSettingsScreen(UiScreen parent) {
        super("Settings");
        this.parent = parent;
        this.cfg = manager.getConfig();
        build();
    }

    @Override
    protected void onSearchSubmit() { }

    @Override
    protected boolean liveSearch() { return false; }

    @Override
    protected int activeTab() { return -1; }

    @Override
    protected void init() {
        super.init();
        search.setHint("Filter settings");
        int sw = px(320), sh = px(44);
        int sx = this.width - px(24) - sw;
        int sy = (px(64) - px(2) - sh) / 2 + px(2);
        search.setBounds(sx + px(46), sy + (sh - 7) / 2, sw - px(46) - px(14));

        String prevName = presetName != null ? presetName.getValue() : "";
        presetName = makeField("Preset name", EqPresetManager.MAX_NAME_LENGTH);
        presetName.setValue(prevName);
        presetName.setVisible(false);
    }

    // ---------------------------------------------------------------- row factories

    private static Row sw(String label, String desc, BooleanSupplier get, Consumer<Boolean> set) {
        Row r = new Row();
        r.kind = SWITCH; r.label = label; r.desc = desc; r.getB = get; r.setB = set;
        return r;
    }

    private static Row sl(String label, String desc, DoubleSupplier get, DoubleConsumer set, DoubleFunction<String> fmt) {
        Row r = new Row();
        r.kind = SLIDER; r.label = label; r.desc = desc; r.getD = get; r.setD = set; r.fmt = fmt;
        return r;
    }

    private static Row sg(String label, String desc, List<String> opts, Supplier<String> get, Consumer<String> set) {
        Row r = new Row();
        r.kind = SEG; r.label = label; r.desc = desc; r.opts = opts; r.getS = get; r.setS = set;
        return r;
    }

    private static Row btnRow(String label, String desc, String btn, Runnable action) {
        Row r = new Row();
        r.kind = BTN; r.label = label; r.desc = desc; r.btn = btn; r.action = action;
        return r;
    }

    private static Row special(int kind) {
        Row r = new Row();
        r.kind = kind;
        return r;
    }

    private static Row when(Row r, BooleanSupplier w) {
        r.when = w;
        return r;
    }

    private static Row toRow(MelodixApi.SettingsRow s) {
        switch (s.kind()) {
            case TOGGLE:
                return sw(s.label(), s.desc(), s.getB(), s.setB());
            case BUTTON: {
                Row r = new Row();
                r.kind = BTN; r.label = s.label(); r.desc = s.desc(); r.btn = s.buttonText(); r.action = s.action();
                return r;
            }
            default: {
                Row r = new Row();
                r.kind = PATH; r.label = s.label(); r.desc = s.desc(); r.text = s.text();
                return r;
            }
        }
    }

    private static String pct(double p) { return Math.round(p * 100) + "%"; }

    private static DoubleFunction<String> secs(double lo, double hi) {
        return p -> String.format(Locale.ROOT, "%.1f s", (lo + p * (hi - lo)) / 1000.0);
    }

    private void build() {
        List<Section> sound = new ArrayList<>();
        sound.add(new Section("Volume", List.of(
                sl("Melodix volume", "Loudness of the music played by the mod.", manager::getVolume, v -> manager.setVolume((float) v), OreSettingsScreen::pct),
                sl("Master volume", "Affects all sounds, including Minecraft's own.", cfg::getMasterVolume, v -> cfg.setMasterVolume((float) v), OreSettingsScreen::pct))));
        Row eq = special(EQ);
        eq.when = cfg::isEqEnabled;
        sound.add(new Section("Equalizer", List.of(
                sw("Equalizer", "Shape the sound with five bands and a preamp.", cfg::isEqEnabled, cfg::setEqEnabled),
                special(PRESETS), eq)));
        sound.add(new Section("Reverb", List.of(
                sw("Reverb", "Adds a sense of space to the sound.", cfg::isReverbEnabled, cfg::setReverbEnabled),
                when(sl("Reverb mix", "Balance between the dry and the reverb signal.", cfg::getReverbWet, v -> cfg.setReverbWet((float) v), OreSettingsScreen::pct), cfg::isReverbEnabled))));
        cats.put("sound", sound);

        List<Section> playback = new ArrayList<>();
        playback.add(new Section("Transitions", List.of(
                sw("Crossfade", "Blend the end of one song into the next.", cfg::isCrossfadeEnabled, cfg::setCrossfadeEnabled),
                when(sl("Crossfade length", "How long the two songs overlap.", () -> (cfg.getCrossfadeDurationMs() - 500) / 4500.0,
                        v -> cfg.setCrossfadeDurationMs((int) Math.round(500 + v * 4500)), secs(500, 5000)), cfg::isCrossfadeEnabled),
                sw("Fade on pause", "Ease the volume out and back in when pausing.", cfg::isFadeOnPause, cfg::setFadeOnPause),
                when(sl("Fade length", "How long the fade takes.", () -> (cfg.getFadeDurationMs() - 200) / 1800.0,
                        v -> cfg.setFadeDurationMs((int) Math.round(200 + v * 1800)), secs(200, 2000)), cfg::isFadeOnPause))));
        playback.add(new Section("While you play", List.of(
                sw("Mute Minecraft music", "Silence the game's own music while Melodix plays.", cfg::isSuppressVanillaMusic, cfg::setSuppressVanillaMusic),
                sw("Pause when unfocused", "Pause when Minecraft is not the active window.", cfg::isStopWhenUnfocused, cfg::setStopWhenUnfocused),
                sw("Keep playing in menus", "Keep the music going in the main menu and the world and server lists instead of stopping when you leave a world.", cfg::isContinueInMenus, cfg::setContinueInMenus),
                sw("Pause the world while open", "Single-player only. Freezes the game while the player is open.", cfg::isPauseOnOpen, cfg::setPauseOnOpen),
                sw("Pause on the game menu", "Pause the music while the pause screen is open.", cfg::isPauseOnPauseScreen, cfg::setPauseOnPauseScreen))));
        playback.add(new Section("Damage ducking", List.of(
                sw("Lower volume when hit", "Briefly dips the music when you take damage.", cfg::isDuckOnDamage, cfg::setDuckOnDamage),
                when(sl("Duck level", "Volume while the music is ducked.", cfg::getDuckVolume, v -> cfg.setDuckVolume((float) v), OreSettingsScreen::pct), cfg::isDuckOnDamage),
                when(sl("Duck time", "How long the volume stays lowered.", () -> (cfg.getDuckDurationMs() - 500) / 4500.0,
                        v -> cfg.setDuckDurationMs((int) Math.round(500 + v * 4500)), secs(500, 5000)), cfg::isDuckOnDamage))));
        cats.put("playback", playback);

        List<Section> appearance = new ArrayList<>();
        appearance.add(new Section("Theme", List.of(special(THEMES))));
        appearance.add(new Section("In-game display", List.of(
                sg("Now playing pop-up", "How the current song is shown in the game.", List.of("Widget", "Above hotbar", "Off"),
                        () -> switch (cfg.getNowPlayingStyle()) { case WIDGET -> "Widget"; case ABOVE_HOTBAR -> "Above hotbar"; default -> "Off"; },
                        v -> cfg.setNowPlayingStyle(switch (v) { case "Widget" -> NowPlayingStyle.WIDGET; case "Above hotbar" -> NowPlayingStyle.ABOVE_HOTBAR; default -> NowPlayingStyle.OFF; })),
                sw("Song change toast", "Show a toast when a new song starts.", cfg::isShowNowPlayingToast, cfg::setShowNowPlayingToast),
                sw("Mini player", "Floating controls while you play.", cfg::isMiniPlayerEnabled, cfg::setMiniPlayerEnabled),
                when(sg("Mini player size", "Compact shows controls only. Big adds album art.", List.of("Compact", "Big"),
                        () -> cfg.getMiniPlayerStyle() == MiniPlayerStyle.BIG ? "Big" : "Compact",
                        v -> cfg.setMiniPlayerStyle(v.equals("Big") ? MiniPlayerStyle.BIG : MiniPlayerStyle.COMPACT)), cfg::isMiniPlayerEnabled),
                sw("Hotbar visualizer", "32 frequency bars above the hotbar.", cfg::isVisualizerEnabled, cfg::setVisualizerEnabled))));
        appearance.add(new Section("Song lists", List.of(
                sl("Song row height", "Taller rows are easier to hit.", () -> cfg.getSongEntryHeight() / 36.0,
                        v -> cfg.setSongEntryHeight((int) Math.round(v * 36)),
                        p -> { int v = (int) Math.round(p * 36); return v == 0 ? "Auto" : Math.max(12, v) + " px"; }))));
        cats.put("appearance", appearance);

        List<Section> library = new ArrayList<>();
        Row folder = new Row();
        folder.kind = PATH; folder.label = "Music folder"; folder.browse = true;
        folder.text = () -> cfg.getMusicFolderPath().isEmpty() ? "No folder chosen" : cfg.getMusicFolderPath();
        folder.desc = "MP3 and WAV files are scanned from here.";
        folder.action = () -> {
            if (blockedByFullscreen(folder.action)) return;
            String initial = cfg.getMusicFolderPath().isEmpty() ? System.getProperty("user.home") : cfg.getMusicFolderPath();
            UiHost.get().selectFolder("Select your music folder", initial, result -> {
                if (result != null && !result.isEmpty() && new java.io.File(result).isDirectory()) manager.setMusicFolder(result);
            });
        };
        library.add(new Section("Music folder", List.of(folder,
                sw("Resume where you left off", "Remember the queue and position between sessions.", cfg::isQueuePersistence, cfg::setQueuePersistence))));
        library.add(new Section("Listening statistics", List.of(special(STATS))));
        library.add(new Section("Backup and restore", List.of(
                btnRow("Export data", "Save your settings, EQ presets, playlists, listening statistics and music folder to one file.",
                        "Export...", this::exportData),
                btnRow("Import data", "Restore everything from an exported file, for example on a new instance. Playlists with the same name are replaced.",
                        "Import...", this::importData))));
        library.add(new Section("Welcome tour", List.of(
                btnRow("Welcome tour", "Show the first-time introduction on the home screen again.", "Start", () -> {
                    UiHost.get().open(new OreSetupScreen());
                }))));
        cats.put("library", library);

        List<Row> keys = new ArrayList<>();
        for (int i = 0; i < KEY_TEXT.length; i++) {
            Row r = new Row();
            r.kind = KEY; r.label = KEY_TEXT[i][0]; r.desc = KEY_TEXT[i][1]; r.keyIdx = i;
            keys.add(r);
        }
        Row open = new Row();
        open.kind = BTN; open.label = "Change keybinds"; open.desc = "Keybinds are edited in Minecraft's Controls screen.";
        open.btn = "Open Controls";
        open.action = () -> UiHost.get().openKeybindsScreen(this);
        keys.add(open);
        List<Section> controls = new ArrayList<>();
        controls.add(new Section("Keybinds", keys));
        controls.add(new Section("Custom volume", List.of(
                sl("Custom volume level", "Volume applied by the Set custom volume key.", cfg::getCustomVolumeKeybindLevel,
                        v -> cfg.setCustomVolumeKeybindLevel((float) v), OreSettingsScreen::pct))));
        cats.put("controls", controls);

        List<Section> advanced = new ArrayList<>();
        advanced.add(new Section("Interface", List.of(
                sw("OreUI interface", "Turn off to go back to the previous design. You can switch back in its settings.", cfg::isOreUiEnabled, v -> {
                    cfg.setOreUiEnabled(v);
                    if (!v) UiHost.get().openClassicUi();
                }))));
        Row file = new Row();
        file.kind = PATH; file.label = "Config file"; file.desc = "Every setting on these pages is stored here.";
        file.text = () -> "config/musicplayer.json";
        advanced.add(new Section("Diagnostics", List.of(
                sw("Debug logging", "Write detailed logs for bug reports.", cfg::isDebugLogsEnabled, cfg::setDebugLogsEnabled), file)));
        cats.put("advanced", advanced);

        if (!MelodixApi.hasStreamSourceProviders()) {
            // No YouTube add-on installed: explain it and link to it. The add-on brings its own page when present.
            List<Row> rows = new ArrayList<>();
            rows.add(toRow(MelodixApi.SettingsRow.info("YouTube support",
                    "Play YouTube songs inside Melodix through a separate add-on.", () -> "Not installed")));
            rows.add(toRow(MelodixApi.SettingsRow.button("Get YouTube support",
                    "Nothing is installed or downloaded unless you add it. Restart the game after installing.",
                    "Open download page", () -> MelodixApi.openYoutubeAddonPage())));
            catIds.add("youtube");
            catNames.add("YouTube");
            heads.add(new String[]{"YouTube", "Play YouTube songs inside Melodix."});
            cats.put("youtube", List.of(new Section("YouTube support", rows)));
        }

        for (MelodixApi.SettingsCategory c : MelodixApi.settingsCategories()) {
            List<Row> rows = new ArrayList<>();
            for (MelodixApi.SettingsRow sr : c.rows().get()) rows.add(toRow(sr));
            if (rows.isEmpty()) continue;
            catIds.add(c.id());
            catNames.add(c.name());
            heads.add(new String[]{c.name(), c.blurb()});
            cats.put(c.id(), List.of(new Section(c.name(), rows)));
        }
    }

    // ---------------------------------------------------------------- header

    @Override
    protected void renderHeader(Gfx g, int mx, int my) {
        int hh = px(64), b2 = px(2);
        g.fill(0, 0, this.width, hh, th.edge());
        g.fill(0, 0, this.width, hh - b2, th.panel());
        g.fill(0, 0, this.width, b2, th.hi());
        int x = px(24);
        String home = "Home";
        int bw = px(20) * 2 + px(18) + px(10) + tw(home, false, 16);
        tip("Back");
        button(g, mx, my, x, px(10), bw, px(44), NORMAL, () -> UiHost.get().open(parent));
        icon(g, ICON_BACK, 10, 10, x + px(20), px(10) + px(22) - px(2) - px(9) + pressDy, px(18), th.text());
        text(g, home, x + px(20) + px(18) + px(10), px(10) + px(22) - px(2) - capH(16) / 2 + pressDy, th.text(), false, 16);
        x += bw + px(16);
        textMid(g, "Settings", x, b2, hh - b2, th.text(), true, 24);

        int sw = px(320), sh = px(44);
        int sx = this.width - px(24) - sw;
        int sy = (hh - b2 - sh) / 2 + b2;
        g.fill(sx, sy, sx + sw, sy + sh, search.isFocused() ? 0xFF6CC349 : th.edge());
        g.fill(sx + b2, sy + b2, sx + sw - b2, sy + sh - b2, th.inset());
        icon(g, ICON_SEARCH, 11, 11, sx + px(14), sy + (sh - px(20)) / 2, px(20), th.dim());
        hit(mx, my, sx, sy, sw, sh, () -> focusField(search));
    }

    // ---------------------------------------------------------------- body

    private int ctlWidth(Row r) {
        return switch (r.kind) {
            case SWITCH -> px(88);
            case SLIDER -> px(72) + px(14) + px(220);
            case SEG -> {
                int w = 0;
                for (String o : r.opts) w += tw(o, false, 16) + px(32) + px(6);
                yield w - px(6);
            }
            case PATH -> px(300) + (r.browse ? px(10) + tw("Browse", false, 16) + px(36) : 0);
            case KEY -> Math.max(px(48), tw(keyText(r.keyIdx), false, 16) + px(28));
            case BTN -> tw(r.btn, false, 16) + px(36);
            default -> 0;
        };
    }

    private String keyText(int i) {
        List<KeyBindingView> keys = UiHost.get().keyBindings();
        if (i >= keys.size() || keys.get(i) == null) return "Unbound";
        return keys.get(i).unbound() ? "Unbound" : keys.get(i).keyText();
    }

    private int stdHeight(Row r, int innerW) {
        int leftW = innerW - 2 * px(20) - ctlWidth(r) - px(20);
        int lines = wrap(r.desc, Math.max(px(80), leftW), false, 14).size();
        return Math.max(px(64), px(20) + px(20) + px(2) + lines * px(18));
    }

    private int rowHeight(Row r, int innerW) {
        return switch (r.kind) {
            case THEMES -> px(16) + px(20) + px(20) + px(14) + px(64) + px(8) + px(20) + px(10) + px(4) + px(20);
            case EQ -> px(274);
            case STATS -> px(16) + px(76) + px(20);
            case PRESETS -> px(16) + px(44) + px(12) + chipLines(innerW - px(40)) * px(40) + (chipLines(innerW - px(40)) - 1) * px(6) + px(20);
            default -> stdHeight(r, innerW);
        };
    }

    private List<Section> visibleSections(String query) {
        if (query.isEmpty()) return cats.get(catIds.get(cat));
        List<Section> out = new ArrayList<>();
        for (String id : catIds) {
            for (Section s : cats.get(id)) {
                List<Row> rows = new ArrayList<>();
                for (Row r : s.rows()) {
                    if (r.kind >= THEMES) {
                        if ((r.kind == THEMES && "theme color".contains(query)) || (r.kind == EQ && "equalizer bands".contains(query))
                                || (r.kind == STATS && "statistics listening".contains(query))
                                || (r.kind == PRESETS && "equalizer presets preset".contains(query))) rows.add(r);
                        continue;
                    }
                    if ((r.label + " " + r.desc).toLowerCase(Locale.ROOT).contains(query)) rows.add(r);
                }
                if (!rows.isEmpty()) out.add(new Section(s.title(), rows));
            }
        }
        return out;
    }

    @Override
    protected void renderBody(Gfx g, int mx, int my) {
        int top = px(64), b2 = px(2);
        String query = search.getValue().trim().toLowerCase(Locale.ROOT);
        if (!query.equals(lastQuery)) { lastQuery = query; scrollY = 0; }

        // rail
        int railW = px(272);
        g.fill(0, top, railW, this.height, th.edge());
        g.fill(0, top, railW - b2, this.height, th.panel());
        int ry = top + px(20);
        for (int i = 0; i < catIds.size(); i++) {
            final int idx = i;
            int rx = px(16), rw = railW - px(32), rh = px(52);
            boolean on = i == cat && query.isEmpty();
            boolean hov;
            if (on) {
                button(g, mx, my, rx, ry, rw, rh, GREEN, () -> { cat = idx; scrollY = 0; });
                hov = false;
            } else {
                hov = hit(mx, my, rx, ry, rw, rh, () -> { cat = idx; scrollY = 0; search.setValue(""); });
                if (hov) g.fill(rx, ry, rx + rw, ry + rh, th.rowHover());
            }
            int off = on ? -px(2) + pressDy : 0;
            int col = on ? 0xFFFFFFFF : th.text();
            int[][] catIcon = i < CAT_ICONS.length ? CAT_ICONS[i] : ICON_LIST;
            int[] catGrid = i < CAT_GRID.length ? CAT_GRID[i] : new int[]{12, 12};
            icon(g, catIcon, catGrid[0], catGrid[1], rx + px(16), ry + (rh - px(22)) / 2 + off, px(22), col);
            text(g, catNames.get(i), rx + px(16) + px(22) + px(14), ry + (rh - capH(16)) / 2 + off, col, false, 16);
            ry += rh + px(6);
        }
        String ver = "Melodix " + Ports.host().modVersion();
        text(g, ver, px(20), this.height - px(20) - capH(14), th.dim(), false, 14);

        // content
        int cl = railW + px(32), cr = this.width - px(32);
        int cw = cr - cl;
        int y = top + px(24) - scrollY;
        g.enableScissor(railW, top, this.width, this.height);

        String title = query.isEmpty() ? heads.get(cat)[0] : "Search results";
        String sub = query.isEmpty() ? heads.get(cat)[1] : "Matching settings from every page.";
        text(g, title, cl, y, th.text(), true, 24);
        text(g, sub, cl, y + px(32), th.dim(), false, 16);
        String auto = "Changes save automatically";
        int aw = tw(auto, false, 14);
        text(g, auto, cr - aw, y + px(34), th.dim(), false, 14);
        icon(g, ICON_CHECK, 8, 8, cr - aw - px(8) - px(16), y + px(34) + (capH(14) - px(16)) / 2, px(16), th.dim());
        y += px(58) + px(20);

        List<Section> sections = visibleSections(query);
        if (sections.isEmpty()) {
            text(g, "No settings match your filter.", cl, y + px(20), th.dim(), false, 16);
            y += px(60);
        }
        for (Section sec : sections) {
            int inner = cw - 2 * b2;
            int total = px(14) + px(24) + px(12);
            for (Row r : sec.rows()) total += b2 + rowHeight(r, inner);
            total += b2 + px(4);
            card(g, cl, y, cw, total);
            text(g, sec.title(), cl + px(20), y + px(14) + (px(24) - capH(20)) / 2, th.text(), true, 20);
            int ry2 = y + px(14) + px(24) + px(12);
            for (Row r : sec.rows()) {
                int h = rowHeight(r, inner);
                g.fill(cl + b2, ry2, cl + cw - b2, ry2 + b2, th.sep());
                int mark = hitMark();
                drawRow(g, mx, my, r, cl + b2, ry2 + b2, inner, h);
                if (!r.when.getAsBoolean() && r.kind != SWITCH) {
                    dropHitsSince(mark);
                    g.fill(cl + b2, ry2 + b2, cl + cw - b2, ry2 + b2 + h, (th.light() ? 0x80D8D9DC : 0x803B3C3D));
                }
                ry2 += b2 + h;
            }
            y += total + px(20);
        }
        g.disableScissor();
        int contentH = (y + scrollY) - top - px(20) + px(32);
        scrollMax = Math.max(0, contentH - (this.height - top));
        scrollY = Math.max(0, Math.min(scrollMax, scrollY));
        scrollbar(g, top, this.height);
    }

    private void drawRow(Gfx g, int mx, int my, Row r, int x, int y, int w, int h) {
        if (r.kind == THEMES) { drawThemes(g, mx, my, x, y, w); return; }
        if (r.kind == EQ) { drawEq(g, mx, my, x, y, w); return; }
        if (r.kind == STATS) { drawStats(g, mx, my, x, y, w); return; }
        if (r.kind == PRESETS) { drawPresets(g, mx, my, x, y, w); return; }

        int ctlW = ctlWidth(r);
        int leftX = x + px(20), leftW = w - 2 * px(20) - ctlW - px(20);
        List<String> lines = wrap(r.desc, Math.max(px(80), leftW), false, 14);
        int blockH = px(20) + px(2) + lines.size() * px(18);
        int ty = y + (h - blockH) / 2;
        text(g, fit(r.label, Math.max(px(80), leftW), true, 16), leftX, ty + (px(20) - capH(16)) / 2, th.text(), true, 16);
        for (int i = 0; i < lines.size(); i++) {
            text(g, lines.get(i), leftX, ty + px(22) + i * px(18) + (px(18) - capH(14)) / 2, th.dim(), false, 14);
        }
        int cx = x + w - px(20) - ctlW;
        int mid = y + h / 2;
        switch (r.kind) {
            case SWITCH -> {
                boolean on = r.getB.getAsBoolean();
                drawSwitch(g, mx, my, cx, mid - px(18), on, r, true, () -> r.setB.accept(!on));
            }
            case SLIDER -> {
                double p = Math.max(0, Math.min(1, r.getD.getAsDouble()));
                textRight(g, r.fmt.apply(p), cx + px(72), mid - capH(16) / 2, th.text(), false, 16);
                sliderTrack(g, cx + px(72) + px(14), mid - px(8), px(220), px(16), (float) p, f -> r.setD.accept(f));
            }
            case SEG -> {
                String cur = r.getS.get();
                int bx = cx;
                for (String o : r.opts) {
                    int bw = tw(o, false, 16) + px(32);
                    boolean sel = o.equals(cur);
                    button(g, mx, my, bx, mid - px(20), bw, px(40), sel ? GREEN : NORMAL, () -> r.setS.accept(o));
                    textBtn(g, o, false, 16, bx, mid - px(20), bw, px(40), sel ? 0xFFFFFFFF : th.text());
                    bx += bw + px(6);
                }
            }
            case PATH -> {
                int pw = px(300);
                inset(g, cx, mid - px(20), pw, px(40));
                text(g, fit(r.text.get(), pw - px(24), false, 16), cx + px(12), mid - capH(16) / 2, th.dim(), false, 16);
                if (r.browse) {
                    int bx = cx + pw + px(10), bw = tw("Browse", false, 16) + px(36);
                    tip("Choose a folder");
                    button(g, mx, my, bx, mid - px(20), bw, px(40), NORMAL, r.action);
                    textBtn(g, "Browse", false, 16, bx, mid - px(20), bw, px(40), th.text());
                }
            }
            case KEY -> {
                String kt = keyText(r.keyIdx);
                button(g, -1, -1, cx, mid - px(18), ctlW, px(36), NORMAL, null);
                textBtn(g, kt, false, 16, cx, mid - px(18), ctlW, px(36), kt.equals("Unbound") ? th.dim() : th.text());
            }
            case BTN -> {
                button(g, mx, my, cx, mid - px(20), ctlW, px(40), NORMAL, r.action);
                textBtn(g, r.btn, false, 16, cx, mid - px(20), ctlW, px(40), th.text());
            }
            default -> { }
        }
    }

    private void drawThemes(Gfx g, int mx, int my, int x, int y, int w) {
        int b2 = px(2);
        int lx = x + px(20);
        int py = y + px(16);
        text(g, "Color theme", lx, py, th.text(), true, 16);
        text(g, "Tints the surfaces and the accent color across Melodix.", lx, py + px(22), th.dim(), false, 14);
        int ty = py + px(20) + px(20) + px(14);
        int gap = px(12), cols = THEME_DEFS.length;
        int tw0 = (w - px(40) - gap * (cols - 1)) / cols;
        String cur = cfg.getThemeId();
        for (int i = 0; i < cols; i++) {
            String[] d = THEME_DEFS[i];
            int tx = lx + i * (tw0 + gap);
            boolean sel = d[0].equals(cur == null ? "dark" : cur);
            int th0 = px(64) + px(8) + px(20) + px(10) + px(4);
            if (sel) g.fill(tx - b2, ty - b2, tx + tw0 + b2, ty + th0 + b2, 0xFF6CC349);
            boolean hov = hit(mx, my, tx, ty, tw0, th0, () -> cfg.setThemeId(d[0]));
            g.fill(tx, ty, tx + tw0, ty + th0, th.edge());
            g.fill(tx + b2, ty + b2, tx + tw0 - b2, ty + th0 - b2, hov ? th.rowHover() : th.inset());
            int pw = tw0 - 2 * b2, ph = px(64);
            int px0 = tx + b2, py0 = ty + b2;
            g.fill(px0, py0, px0 + pw, py0 + ph, 0xFF000000 | Integer.parseInt(d[2], 16));
            g.fill(px0, py0, px0 + pw, py0 + px(12), 0xFF000000 | Integer.parseInt(d[3], 16));
            g.fill(px0 + px(12), py0 + px(24), px0 + px(12) + px(36), py0 + px(34), 0xFF000000 | Integer.parseInt(d[4], 16));
            g.fill(px0 + px(12), py0 + px(42), px0 + Math.min(pw - px(4), px(12) + px(70)), py0 + px(48),
                    0x73000000 | Integer.parseInt(d[5], 16));
            int ny = py0 + ph + px(8);
            text(g, d[1], px0 + px(10), ny, th.text(), false, 16);
            if (sel) icon(g, ICON_CHECK, 8, 8, px0 + pw - px(10) - px(16), ny + (capH(16) - px(16)) / 2, px(16), 0xFF6CC349);
        }
    }

    private void drawEq(Gfx g, int mx, int my, int x, int y, int w) {
        int b2 = px(2);
        int lx = x + px(20), iw = w - px(40);
        int py = y + px(16);
        text(g, "Bands", lx, py, th.text(), true, 16);
        text(g, "Click a band to set its level. The middle line is flat.", lx, py + px(22), th.dim(), false, 14);
        int fw = tw("Flat", false, 16) + px(36);
        button(g, mx, my, lx + iw - fw, py, fw, px(36), NORMAL, () -> {
            for (int i = 0; i < 5; i++) cfg.setEqBand(i, 0f);
            cfg.setPreamp(1f);
        });
        textBtn(g, "Flat", false, 16, lx + iw - fw, py, fw, px(36), th.text());

        int cy = py + px(36) + px(16);
        int gap = px(12), cols = 6;
        int colW = (iw - gap * (cols - 1)) / cols;
        float[] gains = cfg.getEqGains();
        for (int i = 0; i < cols; i++) {
            final int bi = i;
            int colX = lx + i * (colW + gap);
            float p = i == 0 ? cfg.getPreamp() / 2f : (gains[i - 1] + 12f) / 24f;
            p = Math.max(0f, Math.min(1f, p));
            String vt;
            if (i == 0) vt = String.format(Locale.ROOT, "%.1f×", cfg.getPreamp());
            else { int db = Math.round(gains[i - 1]); vt = (db > 0 ? "+" : "") + db + " dB"; }
            int centre = colX + colW / 2 - (i == 0 ? px(6) : 0);
            text(g, vt, centre - tw(vt, true, 16) / 2, cy, th.text(), true, 16);
            int trackW = px(28), trackH = px(112);
            int tx = centre - trackW / 2, ty = cy + px(28);
            inset(g, tx, ty, trackW, trackH);
            int innerH = trackH - 2 * b2;
            int fillH = Math.round(innerH * p);
            if (fillH > 0) {
                g.fill(tx + b2, ty + trackH - b2 - fillH, tx + trackW - b2, ty + trackH - b2, OreTheme.GREEN);
                g.fill(tx + b2, ty + trackH - b2 - fillH, tx + trackW - b2, ty + trackH - b2 - fillH + b2, OreTheme.GREEN_HI);
            }
            g.fill(tx + b2, ty + trackH / 2 - 1, tx + trackW - b2, ty + trackH / 2 + 1, 0xB31E1E1F);
            int ky = ty + trackH - b2 - fillH - px(7);
            button(g, -1, -1, tx - px(6), ky, trackW + px(12), px(14), LIGHT, null);
            vslider(tx, ty, trackW, trackH, f -> {
                if (bi == 0) cfg.setPreamp((float) (f * 2));
                else {
                    float db = (float) (f * 24 - 12);
                    if (Math.abs(db) < 0.7f) db = 0f;
                    cfg.setEqBand(bi - 1, Math.round(db));
                }
            });
            String[] nm = BANDS[i];
            text(g, nm[0], centre - tw(nm[0], false, 16) / 2, ty + trackH + px(8), th.text(), false, 16);
            text(g, nm[1], centre - tw(nm[1], false, 14) / 2, ty + trackH + px(8) + px(24), th.dim(), false, 14);
            if (i == 0) g.fill(colX + colW - b2, cy, colX + colW, ty + trackH + px(8) + px(44), th.sep());
        }
    }

    // ---------------------------------------------------------------- EQ presets

    private String chipText(EqPresetManager.Preset p) {
        return fit(p.name(), px(260), false, 16);
    }

    private int chipLines(int iw) {
        int lines = 1, x = 0;
        for (EqPresetManager.Preset p : EqPresetManager.all()) {
            int cw = tw(chipText(p), false, 16) + px(32);
            if (x > 0 && x + cw > iw) { lines++; x = 0; }
            x += cw + px(6);
        }
        return lines;
    }

    private void drawPresets(Gfx g, int mx, int my, int x, int y, int w) {
        int lx = x + px(20), iw = w - px(40);
        int py = y + px(16);
        String cur = EqPresetManager.currentName();
        text(g, "Presets", lx, py, th.text(), true, 16);
        text(g, "Current: " + (cur == null ? "Custom" : fit(cur, px(300), false, 14)), lx, py + px(22), th.dim(), false, 14);
        String mt = "Manage presets";
        int mw = tw(mt, false, 16) + px(36);
        button(g, mx, my, lx + iw - mw, py, mw, px(36), NORMAL, this::openPresetDialog);
        textBtn(g, mt, false, 16, lx + iw - mw, py, mw, px(36), th.text());

        int cy = py + px(44) + px(12), cx = 0;
        for (EqPresetManager.Preset p : EqPresetManager.all()) {
            String label = chipText(p);
            int cw = tw(label, false, 16) + px(32);
            if (cx > 0 && cx + cw > iw) { cx = 0; cy += px(46); }
            boolean sel = p.name().equals(cur);
            button(g, mx, my, lx + cx, cy, cw, px(40), sel ? GREEN : NORMAL, () -> EqPresetManager.apply(p));
            textBtn(g, label, false, 16, lx + cx, cy, cw, px(40), sel ? 0xFFFFFFFF : th.text());
            cx += cw + px(6);
        }
    }

    private void openPresetDialog() {
        dialog = Dialog.PRESETS;
        renaming = null;
        dialogMsg = "";
        dialogErr = false;
        dialogScroll = 0;
        presetName.setValue("");
    }

    private void closeDialog() {
        dialog = Dialog.NONE;
        renaming = null;
        pendingImport = null;
        if (presetName != null) {
            presetName.setFocused(false);
            presetName.setVisible(false);
        }
        focusField(null);
    }

    private void setMsg(String msg, boolean err) {
        dialogMsg = msg;
        dialogErr = err;
    }

    private void submitPresetName() {
        String n = presetName.getValue().trim();
        String err = EqPresetManager.validateName(n, renaming);
        if (err != null) { setMsg(err, true); return; }
        if (renaming != null) {
            EqPresetManager.rename(renaming, n);
            setMsg("Renamed to \"" + n + "\"", false);
            renaming = null;
        } else {
            boolean existed = EqPresetManager.find(n) != null;
            EqPresetManager.saveCurrent(n);
            setMsg((existed ? "Updated \"" : "Saved \"") + n + "\"", false);
        }
        presetName.setValue("");
    }

    private Runnable presetAction(String label, EqPresetManager.Preset p) {
        return switch (label) {
            case "Apply" -> () -> {
                EqPresetManager.apply(p);
                setMsg("Applied \"" + p.name() + "\"", false);
            };
            case "Update" -> () -> {
                EqPresetManager.saveCurrent(p.name());
                setMsg("Updated \"" + p.name() + "\" with the current equalizer", false);
            };
            case "Rename" -> () -> {
                renaming = p.name();
                presetName.setValue(p.name());
                focusField(presetName);
                setMsg("", false);
            };
            default -> () -> {
                EqPresetManager.delete(p.name());
                if (p.name().equalsIgnoreCase(renaming)) {
                    renaming = null;
                    presetName.setValue("");
                }
                setMsg("Deleted \"" + p.name() + "\"", false);
            };
        };
    }

    @Override
    protected boolean modal() { return dialog != Dialog.NONE; }

    @Override
    protected void renderOverlay(Gfx g, int mx, int my) {
        if (dialog != Dialog.PRESETS && presetName != null) presetName.setVisible(false);
        if (dialog == Dialog.NONE) return;
        g.fill(0, 0, this.width, this.height, 0xB0000000);
        catcher(mx, my, this::closeDialog);
        if (dialog == Dialog.PRESETS) drawPresetDialog(g, mx, my);
        else drawConfirmImport(g, mx, my);
    }

    private void drawPresetDialog(Gfx g, int mx, int my) {
        int b2 = px(2);
        int w = Math.min(px(760), this.width - px(40)), h = Math.min(px(600), this.height - px(40));
        int x = (this.width - w) / 2, y = (this.height - h) / 2;
        menuBox(g, x, y, w, h);
        catcherArea(mx, my, x, y, w, h);
        int pad = px(24), ix = x + pad, iw = w - 2 * pad;

        text(g, "Equalizer presets", ix, y + pad + (px(40) - capH(20)) / 2 - px(6), th.text(), true, 20);
        String done = "Done";
        int dw = tw(done, false, 16) + px(36);
        int dx = x + w - pad - dw, dy = y + pad - px(6);
        button(g, mx, my, dx, dy, dw, px(40), NORMAL, this::closeDialog);
        textBtn(g, done, false, 16, dx, dy, dw, px(40), th.text());

        int ny = y + pad + px(52), nh = px(40);
        boolean ren = renaming != null;
        String saveT = ren ? "Rename" : "Save current";
        int sw2 = tw(saveT, false, 16) + px(36);
        int cw2 = tw("Cancel", false, 16) + px(36);
        int fieldW = iw - sw2 - px(10) - (ren ? cw2 + px(10) : 0);
        field(g, mx, my, presetName, ix, ny, fieldW, nh, px(12), px(12), true);
        int bx = ix + fieldW + px(10);
        button(g, mx, my, bx, ny, sw2, nh, GREEN, this::submitPresetName);
        textBtn(g, saveT, false, 16, bx, ny, sw2, nh, 0xFFFFFFFF);
        if (ren) {
            bx += sw2 + px(10);
            button(g, mx, my, bx, ny, cw2, nh, NORMAL, () -> {
                renaming = null;
                presetName.setValue("");
                setMsg("", false);
            });
            textBtn(g, "Cancel", false, 16, bx, ny, cw2, nh, th.text());
        }

        String info = !dialogMsg.isEmpty() ? dialogMsg
                : ren ? "Renaming \"" + renaming + "\""
                : "Saves the current equalizer bands and preamp. Using an existing name replaces that preset.";
        text(g, fit(info, iw, false, 14), ix, ny + nh + px(10), dialogErr ? th.like() : th.dim(), false, 14);

        int listTop = ny + nh + px(10) + px(28), listBottom = y + h - pad;
        List<EqPresetManager.Preset> all = EqPresetManager.all();
        int rowH = px(52), gap = px(4);
        int visibleH = listBottom - listTop - 2 * b2 - px(12);
        dialogScrollMax = Math.max(0, all.size() * (rowH + gap) - visibleH);
        dialogScroll = Math.max(0, Math.min(dialogScrollMax, dialogScroll));
        String cur = EqPresetManager.currentName();

        inset(g, ix, listTop, iw, listBottom - listTop);
        g.enableScissor(ix + b2, listTop + b2, ix + iw - b2, listBottom - b2);
        int ry = listTop + b2 + px(6) - dialogScroll;
        for (EqPresetManager.Preset p : all) {
            boolean vis = ry >= listTop + b2 && ry + rowH <= listBottom - b2;
            boolean isCur = p.name().equals(cur);
            if (isCur) g.fill(ix + b2, ry, ix + iw - b2, ry + rowH, th.rowHover());
            int by = ry + (rowH - px(40)) / 2;
            String[] labels = p.builtIn() ? new String[]{"Apply"} : new String[]{"Apply", "Update", "Rename", "Delete"};
            int rx = ix + iw - px(14) - (dialogScrollMax > 0 ? px(10) : 0);
            for (int i = labels.length - 1; i >= 0; i--) {
                String lb = labels[i];
                int bw = tw(lb, false, 16) + px(28);
                rx -= bw;
                button(g, mx, my, rx, by, bw, px(40), NORMAL, vis ? presetAction(lb, p) : null);
                textBtn(g, lb, false, 16, rx, by, bw, px(40), lb.equals("Delete") ? th.like() : th.text());
                rx -= px(6);
            }
            int nx = ix + px(14);
            if (isCur) icon(g, ICON_CHECK, 8, 8, nx, ry + (rowH - px(16)) / 2, px(16), 0xFF6CC349);
            nx += px(28);
            String tag = p.builtIn() ? "Built-in" : "";
            int tagW = p.builtIn() ? tw(tag, false, 14) + px(14) : 0;
            String nm = fit(p.name(), Math.max(px(60), rx - nx - px(8) - tagW), false, 16);
            text(g, nm, nx, ry + (rowH - capH(16)) / 2, th.text(), false, 16);
            if (p.builtIn()) text(g, tag, nx + tw(nm, false, 16) + px(14), ry + (rowH - capH(14)) / 2, th.dim(), false, 14);
            ry += rowH + gap;
        }
        g.disableScissor();
        if (dialogScrollMax > 0) {
            int trackH = listBottom - listTop - 2 * b2;
            int thumbH = Math.max(px(24), trackH * trackH / (trackH + dialogScrollMax));
            int thumbY = listTop + b2 + (trackH - thumbH) * dialogScroll / dialogScrollMax;
            g.fill(ix + iw - b2 - px(8), thumbY, ix + iw - b2 - px(2), thumbY + thumbH, th.dim());
        }
    }

    private void drawConfirmImport(Gfx g, int mx, int my) {
        int w = Math.min(px(640), this.width - px(40));
        int pad = px(24);
        String fname = pendingImport == null ? "" : pendingImport.getFileName().toString();
        List<String> lines = wrap("Your settings, EQ presets and listening statistics will be replaced by the contents of "
                + fname + ". Playlists with the same name are replaced; your other playlists stay.", w - 2 * pad, false, 16);
        int lineH = px(22);
        int h = pad + px(36) + lines.size() * lineH + px(16) + px(40) + pad;
        int x = (this.width - w) / 2, y = (this.height - h) / 2;
        menuBox(g, x, y, w, h);
        catcherArea(mx, my, x, y, w, h);
        text(g, "Import data?", x + pad, y + pad, th.text(), true, 20);
        int ty = y + pad + px(36);
        for (String l : lines) {
            text(g, l, x + pad, ty, th.dim(), false, 16);
            ty += lineH;
        }
        int by = y + h - pad - px(40);
        String ok = "Import", no = "Cancel";
        int okW = tw(ok, false, 16) + px(40), noW = tw(no, false, 16) + px(40);
        int okX = x + w - pad - okW, noX = okX - px(10) - noW;
        button(g, mx, my, noX, by, noW, px(40), NORMAL, this::closeDialog);
        textBtn(g, no, false, 16, noX, by, noW, px(40), th.text());
        button(g, mx, my, okX, by, okW, px(40), GREEN, this::runImport);
        textBtn(g, ok, false, 16, okX, by, okW, px(40), 0xFFFFFFFF);
    }

    @Override
    public boolean onKeyPressed(UiKey event) {
        if (dialog != Dialog.NONE) {
            int k = event.key();
            if (k == UiKeys.ESCAPE) { closeDialog(); return true; }
            if (dialog == Dialog.PRESETS && presetName.isFocused()
                    && (k == UiKeys.ENTER || k == UiKeys.NUMPAD_ENTER)) {
                submitPresetName();
                return true;
            }
        }
        return super.onKeyPressed(event);
    }

    @Override
    public boolean onMouseScrolled(double mx, double my, double sx, double sy) {
        if (dialog == Dialog.PRESETS) {
            dialogScroll = Math.max(0, Math.min(dialogScrollMax, dialogScroll - (int) Math.round(sy * px(40))));
            return true;
        }
        return super.onMouseScrolled(mx, my, sx, sy);
    }

    // ---------------------------------------------------------------- data export / import

    private void exportData() {
        if (blockedByFullscreen(this::exportData)) return;
        String initial = Path.of(System.getProperty("user.home"), DataExporter.defaultFileName()).toString();
        UiHost.get().saveFile("Export Melodix data", initial, result -> {
            if (result == null || result.isEmpty()) return;
            UiHost.get().runOnMain(() -> {
                try {
                    Path out = DataExporter.withExtension(Path.of(result));
                    DataExporter.export(out);
                    com.musicplayer.hud.NotificationManager.showSystem("Exported data to " + out.getFileName());
                } catch (Exception e) {
                    com.musicplayer.hud.NotificationManager.showSystem("Export failed: " + e.getMessage());
                }
            });
        });
    }

    private void importData() {
        if (blockedByFullscreen(this::importData)) return;
        UiHost.get().openFile("Import Melodix data", System.getProperty("user.home"), result -> {
            if (result == null || result.isEmpty()) return;
            UiHost.get().runOnMain(() -> {
                pendingImport = Path.of(result);
                dialog = Dialog.CONFIRM_IMPORT;
            });
        });
    }

    private void runImport() {
        Path p = pendingImport;
        closeDialog();
        if (p == null) return;
        try {
            DataExporter.Result r = DataExporter.importFrom(p);
            com.musicplayer.hud.NotificationManager.showSystem(r.describe());
            if (r.musicFolderMissing) {
                com.musicplayer.hud.NotificationManager.showSystem("Music folder not found on this device. Choose it again under Library.");
            }
        } catch (Exception e) {
            com.musicplayer.hud.NotificationManager.showSystem("Import failed: " + e.getMessage());
        }
    }

    private void drawStats(Gfx g, int mx, int my, int x, int y, int w) {
        ListeningStatisticsManager st = ListeningStatisticsManager.getInstance();
        String most = "-";
        int bestN = 0;
        for (Map.Entry<String, Integer> e : st.getPlayCounts().entrySet()) {
            if (e.getValue() > bestN) {
                bestN = e.getValue();
                String k = e.getKey();
                int slash = Math.max(k.lastIndexOf('/'), k.lastIndexOf('\\'));
                String n = slash >= 0 ? k.substring(slash + 1) : k;
                int dot = n.lastIndexOf('.');
                most = dot > 0 ? n.substring(0, dot) : n;
            }
        }
        String[][] vals = {
                {"This session", String.format("%d:%02d h", st.getSessionListeningTimeSeconds() / 3600, (st.getSessionListeningTimeSeconds() % 3600) / 60)},
                {"All-time", String.format("%d:%02d h", st.getTotalListeningTimeSeconds() / 3600, (st.getTotalListeningTimeSeconds() % 3600) / 60)},
                {"Most played", most}};
        int lx = x + px(20), iw = w - px(40);
        int by = y + px(16);
        String bt = "Open full statistics";
        int bw = tw(bt, false, 16) + px(44);
        int gap = px(12);
        int boxW = (iw - bw - gap * 3) / 3;
        for (int i = 0; i < 3; i++) {
            int bx = lx + i * (boxW + gap);
            inset(g, bx, by, boxW, px(76));
            text(g, fit(vals[i][0], boxW - px(28), false, 16), bx + px(14), by + px(8), th.dim(), false, 16);
            text(g, fit(vals[i][1], boxW - px(28), true, 16), bx + px(14), by + px(8) + px(28), th.text(), true, 16);
        }
        int bx = lx + iw - bw;
        button(g, mx, my, bx, by, bw, px(76), NORMAL, () -> UiHost.get().open(new OreStatsScreen(this)));
        textBtn(g, bt, false, 16, bx, by, bw, px(76), th.text());
    }
}
