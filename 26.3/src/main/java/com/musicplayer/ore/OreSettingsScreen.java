package com.musicplayer.ore;

import com.musicplayer.ListeningStatsScreen;
import com.musicplayer.MusicPlayerMod;
import com.musicplayer.SdlFileDialogs;
import com.musicplayer.SongInfo;
import com.musicplayer.config.MusicPlayerConfig;
import com.musicplayer.config.MusicPlayerConfig.MiniPlayerStyle;
import com.musicplayer.config.MusicPlayerConfig.NowPlayingStyle;
import com.musicplayer.stats.ListeningStatisticsManager;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.controls.KeyBindsScreen;

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

    private static final int SWITCH = 0, SLIDER = 1, SEG = 2, PATH = 3, KEY = 4, BTN = 5, THEMES = 6, EQ = 7, STATS = 8;
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

    private final Screen parent;
    private final MusicPlayerConfig cfg;
    private final Map<String, List<Section>> cats = new LinkedHashMap<>();
    private int cat = 0;
    private String lastQuery = "";

    public OreSettingsScreen(Screen parent) {
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
        search.setHint(net.minecraft.network.chat.Component.literal("Filter settings"));
        int sw = px(320), sh = px(44);
        int sx = this.width - px(24) - sw;
        int sy = (px(64) - px(2) - sh) / 2 + px(2);
        search.setX(sx + px(46));
        search.setY(sy + (sh - 7) / 2);
        search.setWidth(sw - px(46) - px(14));
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

    private static Row special(int kind) {
        Row r = new Row();
        r.kind = kind;
        return r;
    }

    private static Row when(Row r, BooleanSupplier w) {
        r.when = w;
        return r;
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
                sw("Equalizer", "Shape the sound with five bands and a preamp.", cfg::isEqEnabled, cfg::setEqEnabled), eq)));
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
            SdlFileDialogs.selectFolder("Select your music folder", initial, result -> {
                if (result != null && !result.isEmpty() && new java.io.File(result).isDirectory()) manager.setMusicFolder(result);
            });
        };
        library.add(new Section("Music folder", List.of(folder,
                sw("Resume where you left off", "Remember the queue and position between sessions.", cfg::isQueuePersistence, cfg::setQueuePersistence))));
        library.add(new Section("Listening statistics", List.of(special(STATS))));
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
        open.action = () -> this.minecraft.gui.setScreen(new KeyBindsScreen(this, this.minecraft.options));
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
                    if (!v) this.minecraft.gui.setScreen(cfg.isNewUiEnabled() ? new com.musicplayer.MusicPlayerScreen() : new com.musicplayer.MusicPlayerScreenLegacy());
                }))));
        Row file = new Row();
        file.kind = PATH; file.label = "Config file"; file.desc = "Every setting on these pages is stored here.";
        file.text = () -> "config/musicplayer.json";
        advanced.add(new Section("Diagnostics", List.of(
                sw("Debug logging", "Write detailed logs for bug reports.", cfg::isDebugLogsEnabled, cfg::setDebugLogsEnabled), file)));
        cats.put("advanced", advanced);
    }

    // ---------------------------------------------------------------- header

    @Override
    protected void renderHeader(GuiGraphicsExtractor g, int mx, int my) {
        int hh = px(64), b2 = px(2);
        g.fill(0, 0, this.width, hh, th.edge());
        g.fill(0, 0, this.width, hh - b2, th.panel());
        g.fill(0, 0, this.width, b2, th.hi());
        int x = px(24);
        String home = "Home";
        int bw = px(20) * 2 + px(18) + px(10) + tw(home, false, 16);
        tip("Back");
        button(g, mx, my, x, px(10), bw, px(44), NORMAL, () -> this.minecraft.gui.setScreen(parent));
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
        hit(mx, my, sx, sy, sw, sh, () -> setFocused(search));
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
        KeyMapping[] keys = MusicPlayerMod.getAllKeys();
        if (i >= keys.length || keys[i] == null) return "Unbound";
        return keys[i].isUnbound() ? "Unbound" : keys[i].getTranslatedKeyMessage().getString();
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
            default -> stdHeight(r, innerW);
        };
    }

    private List<Section> visibleSections(String query) {
        if (query.isEmpty()) return cats.get(CAT_IDS[cat]);
        List<Section> out = new ArrayList<>();
        for (String id : CAT_IDS) {
            for (Section s : cats.get(id)) {
                List<Row> rows = new ArrayList<>();
                for (Row r : s.rows()) {
                    if (r.kind >= THEMES) {
                        if ((r.kind == THEMES && "theme color".contains(query)) || (r.kind == EQ && "equalizer bands".contains(query))
                                || (r.kind == STATS && "statistics listening".contains(query))) rows.add(r);
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
    protected void renderBody(GuiGraphicsExtractor g, int mx, int my) {
        int top = px(64), b2 = px(2);
        String query = search.getValue().trim().toLowerCase(Locale.ROOT);
        if (!query.equals(lastQuery)) { lastQuery = query; scrollY = 0; }

        // rail
        int railW = px(272);
        g.fill(0, top, railW, this.height, th.edge());
        g.fill(0, top, railW - b2, this.height, th.panel());
        int ry = top + px(20);
        for (int i = 0; i < CAT_IDS.length; i++) {
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
            icon(g, CAT_ICONS[i], CAT_GRID[i][0], CAT_GRID[i][1], rx + px(16), ry + (rh - px(22)) / 2 + off, px(22), col);
            text(g, CAT_NAMES[i], rx + px(16) + px(22) + px(14), ry + (rh - capH(16)) / 2 + off, col, false, 16);
            ry += rh + px(6);
        }
        String ver = "Melodix " + FabricLoader.getInstance().getModContainer("musicplayer")
                .map(c -> c.getMetadata().getVersion().getFriendlyString()).orElse("");
        text(g, ver, px(20), this.height - px(20) - capH(14), th.dim(), false, 14);

        // content
        int cl = railW + px(32), cr = this.width - px(32);
        int cw = cr - cl;
        int y = top + px(24) - scrollY;
        g.enableScissor(railW, top, this.width, this.height);

        String title = query.isEmpty() ? HEADS[cat][0] : "Search results";
        String sub = query.isEmpty() ? HEADS[cat][1] : "Matching settings from every page.";
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

    private void drawRow(GuiGraphicsExtractor g, int mx, int my, Row r, int x, int y, int w, int h) {
        if (r.kind == THEMES) { drawThemes(g, mx, my, x, y, w); return; }
        if (r.kind == EQ) { drawEq(g, mx, my, x, y, w); return; }
        if (r.kind == STATS) { drawStats(g, mx, my, x, y, w); return; }

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

    private void drawThemes(GuiGraphicsExtractor g, int mx, int my, int x, int y, int w) {
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

    private void drawEq(GuiGraphicsExtractor g, int mx, int my, int x, int y, int w) {
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

    private void drawStats(GuiGraphicsExtractor g, int mx, int my, int x, int y, int w) {
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
        button(g, mx, my, bx, by, bw, px(76), NORMAL, () -> this.minecraft.gui.setScreen(new OreStatsScreen(this)));
        textBtn(g, bt, false, 16, bx, by, bw, px(76), th.text());
    }
}
