package com.musicplayer;

import com.mojang.blaze3d.platform.InputConstants;
import com.musicplayer.config.MusicPlayerConfig;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.io.File;
import java.util.*;

public class MusicSettingsScreen extends Screen {

    private final Screen parent;
    private final MusicPlayerManager manager;
    private final MusicPlayerConfig config;

    private static final int PANEL_WIDTH = 380;
    private static final int CATEGORY_HEIGHT = 22;
    private static final int SCROLL_SPEED = 20;
    private static final int CONTENT_PADDING_BOTTOM = 40;
    private int panelLeft, panelTop, panelHeight;
    private int contentY;
    private int totalContentHeight;

    private EditBox searchBox;
    private EditBox folderInput;
    private Button browseButton;
    private Component statusMessage = Component.empty();
    private int statusColor = 0xFFFFFFFF;

    private final Set<String> expandedCategories = new HashSet<>(Arrays.asList("Library", "Playback"));
    private final Set<String> savedExpandedState = new HashSet<>();

    private List<SettingDef> settingDefs = new ArrayList<>();
    private String draggingSliderId = null;
    private boolean draggingScrollbar = false;
    private int scrollbarDragStartY = 0;
    private int scrollbarDragStartScroll = 0;
    private String hoveredTooltip = null;
    private int scrollY = 0;

    // Double-click to type slider values
    private String typingSliderId = null;
    private String typingBuffer = "";
    private String lastSliderClickId = null;
    private long lastSliderClickTime = 0;

    private enum SettingType { FOLDER, SLIDER_FLOAT, SLIDER_INT, TOGGLE, CYCLE, ACTION }

    private record SettingDef(String id, String label, String category, String tooltip, int y, int height, SettingType type) {}

    private static final List<String> CATEGORY_ORDER = Arrays.asList(
        "Library", "Playback", "Volume Control", "Display", "Audio", "Effects", "Extras"
    );

    public MusicSettingsScreen(Screen parent) {
        super(Component.literal("Music Player Settings"));
        this.parent = parent;
        this.manager = MusicPlayerManager.getInstance();
        this.config = manager.getConfig();
    }

    @Override
    protected void init() {
        buildSettingDefs();
        panelHeight = Math.min(this.height - 40, this.height - 40);
        panelLeft = (this.width - PANEL_WIDTH) / 2;
        panelTop = Math.max(16, (this.height - panelHeight) / 2);
        contentY = panelTop + 50;
        scrollY = 0;

        searchBox = new EditBox(this.font, panelLeft + 10, panelTop + 26, PANEL_WIDTH - 20, 16, Component.literal("Search..."));
        searchBox.setMaxLength(64);
        searchBox.setResponder(query -> {
            if (!query.isEmpty()) {
                if (savedExpandedState.isEmpty()) {
                    savedExpandedState.addAll(expandedCategories);
                }
                expandedCategories.addAll(CATEGORY_ORDER);
            } else if (!savedExpandedState.isEmpty()) {
                expandedCategories.clear();
                expandedCategories.addAll(savedExpandedState);
                savedExpandedState.clear();
            }
            updateFolderWidgets();
        });
        addRenderableWidget(searchBox);

        // Create folder widgets if present
        createFolderWidgets();

        addRenderableWidget(Button.builder(Component.literal("Done"), b -> {
            applyFolder();
            this.minecraft.gui.setScreen(parent);
        }).bounds(panelLeft + PANEL_WIDTH / 2 - 30, panelTop + panelHeight - 26, 60, 20).build());
    }

    private int computeFolderY() {
        int renderY = contentY;
        String filter = currentFilter();
        boolean searchActive = !filter.isEmpty();
        for (SettingDef def : settingDefs) {
            boolean isCat = def.id() != null && def.id().startsWith("_cat_");
            if (isCat) {
                renderY += CATEGORY_HEIGHT + 2;
                continue;
            }
            if (def.type() == SettingType.FOLDER) {
                return renderY + scrollY;
            }
            boolean settingVisible = searchActive
                ? matchesFilter(def, filter)
                : expandedCategories.contains(def.category());
            if (settingVisible) {
                renderY += def.height() + 2;
            }
        }
        return renderY + scrollY;
    }

    private boolean isFolderVisible() {
        String filter = currentFilter();
        if (!filter.isEmpty()) return true;
        return expandedCategories.contains("Library");
    }

    private void createFolderWidgets() {
        if (folderInput != null || !isFolderVisible()) return;

        int wx = panelLeft + 110;
        int ww = PANEL_WIDTH - 130;
        int wy = computeFolderY();
        if (wy + 20 >= panelTop + 50 && wy <= panelTop + panelHeight - 30) {
            folderInput = new EditBox(this.font, wx + 4, wy + 1, ww - 46, 18, Component.literal("Path"));
            folderInput.setMaxLength(512);
            folderInput.setValue(config.getMusicFolderPath());
            addRenderableWidget(folderInput);
            browseButton = Button.builder(Component.literal("Browse"), b -> {
                if (this.minecraft.options.fullscreen().get()) {
                    statusMessage = Component.literal("Please exit Fullscreen to use File Browser");
                    statusColor = 0xFFFF5555;
                    return;
                }
                String initial = config.getMusicFolderPath();
                if (initial.isEmpty()) initial = System.getProperty("user.home");
                SdlFileDialogs.selectFolder("Select Music Folder", initial, result -> {
                    if (result != null) folderInput.setValue(result);
                });
            }).bounds(wx + ww - 40, wy, 40, 20).build();
            addRenderableWidget(browseButton);
        }
    }

    private void updateFolderWidgetPositions() {
        if (folderInput == null) return;
        int wy = computeFolderY();
        folderInput.setY(wy + 1);
        browseButton.setY(wy);
    }

    private void buildSettingDefs() {
        settingDefs.clear();
        int y = 4;

        y = addCategorySettings("Library", y, List.of(
            new SettingDef("_folder", "", "Library", "Path to your music folder", 0, 22, SettingType.FOLDER)
        ));

        y = addCategorySettings("Playback", y, List.of(
            new SettingDef("volume", "Volume", "Playback", "Music playback volume level", 0, 22, SettingType.SLIDER_FLOAT),
            new SettingDef("masterVolume", "Master Vol.", "Playback", "Global master volume for all sources", 0, 22, SettingType.SLIDER_FLOAT),
            new SettingDef("playbackMode", "Mode", "Playback", "Song ordering: Sequential, Shuffle, Repeat All, Repeat One", 0, 22, SettingType.CYCLE),
            new SettingDef("playbackSpeed", "Speed", "Playback", "Playback speed multiplier (0.5x to 2.0x)", 0, 22, SettingType.CYCLE),
            new SettingDef("pauseOnOpen", "Pause Singleplayer", "Playback", "Pause the game in single-player when the music player is open", 0, 22, SettingType.TOGGLE),
            new SettingDef("pauseOnPauseScreen", "Pause Screen", "Playback", "Pause music when the game pause screen is open", 0, 22, SettingType.TOGGLE),
            new SettingDef("stopWhenUnfocused", "Unfocused", "Playback", "Stop music when Minecraft isn't the active window", 0, 22, SettingType.TOGGLE)
        ));

        y = addCategorySettings("Volume Control", y, List.of(
            new SettingDef("duckOnDamage", "Duck on Hit", "Volume Control", "Temporarily lower volume when taking damage", 0, 22, SettingType.TOGGLE),
            new SettingDef("duckVolume", "Duck Level", "Volume Control", "Volume level during damage ducking", 0, 22, SettingType.SLIDER_FLOAT),
            new SettingDef("duckDuration", "Duck Time", "Volume Control", "How long the volume stays lowered after damage (500ms – 5000ms)", 0, 22, SettingType.SLIDER_INT),
            new SettingDef("customVolumeLevel", "Keybind Vol.", "Volume Control", "Volume level set by the custom volume keybind", 0, 22, SettingType.SLIDER_FLOAT)
        ));

        y = addCategorySettings("Display", y, List.of(
            new SettingDef("nowPlayingStyle", "Style", "Display", "Now-playing notification style: Widget, Above Hotbar, or Off", 0, 22, SettingType.CYCLE),
            new SettingDef("showNowPlayingToast", "Toast", "Display", "Show a toast notification when a new song starts", 0, 22, SettingType.TOGGLE),
            new SettingDef("songEntryHeight", "Row Height", "Display", "Height of each song row in the list (0 = auto)", 0, 22, SettingType.SLIDER_INT),
            new SettingDef("visualizerEnabled", "Visualizer", "Display", "Show a live audio visualizer when music is playing", 0, 22, SettingType.TOGGLE),
            new SettingDef("miniPlayerEnabled", "Mini Player", "Display", "Show a movable mini player overlay with album art and controls", 0, 22, SettingType.TOGGLE),
            new SettingDef("miniPlayerStyle", "MP Style", "Display", "Mini player style: Big (current) or Compact", 0, 22, SettingType.CYCLE),
            new SettingDef("themeSelector", "Theme", "Display", "Change the UI color theme", 0, 22, SettingType.CYCLE)
        ));

        y = addCategorySettings("Audio", y, List.of(
            new SettingDef("fadeOnPause", "Fade", "Audio", "Smoothly fade volume when pausing or resuming", 0, 22, SettingType.TOGGLE),
            new SettingDef("fadeDuration", "Fade Time", "Audio", "Duration of the fade effect (200ms – 2000ms)", 0, 22, SettingType.SLIDER_INT)
        ));

        y = addCategorySettings("Effects", y, List.of(
            new SettingDef("eqEnabled", "Equalizer", "Effects", "Enable 5-band graphic equalizer", 0, 22, SettingType.TOGGLE),
            new SettingDef("preamp", "Preamp", "Effects", "Pre-amplification gain before EQ (0.5x – 2.0x)", 0, 22, SettingType.SLIDER_FLOAT),
            new SettingDef("eqBand0", "31Hz", "Effects", "EQ band 1: 31 Hz (Low bass)", 0, 22, SettingType.SLIDER_FLOAT),
            new SettingDef("eqBand1", "125Hz", "Effects", "EQ band 2: 125 Hz (Lower mid)", 0, 22, SettingType.SLIDER_FLOAT),
            new SettingDef("eqBand2", "500Hz", "Effects", "EQ band 3: 500 Hz (Mid)", 0, 22, SettingType.SLIDER_FLOAT),
            new SettingDef("eqBand3", "2kHz", "Effects", "EQ band 4: 2 kHz (Upper mid)", 0, 22, SettingType.SLIDER_FLOAT),
            new SettingDef("eqBand4", "8kHz", "Effects", "EQ band 5: 8 kHz (High)", 0, 22, SettingType.SLIDER_FLOAT),
            new SettingDef("reverbEnabled", "Reverb", "Effects", "Enable reverb effect", 0, 22, SettingType.TOGGLE),
            new SettingDef("reverbMix", "Reverb Mix", "Effects", "Wet/dry mix for reverb effect (0% – 100%)", 0, 22, SettingType.SLIDER_FLOAT)
        ));

        y = addCategorySettings("Extras", y, List.of(
            new SettingDef("listeningTime", "Listening Time", "Extras", "View listening time statistics per song", 0, 22, SettingType.ACTION),
            new SettingDef("queuePersistence", "Save Queue", "Extras", "Save the current queue (song, position) across game restarts", 0, 22, SettingType.TOGGLE),
            new SettingDef("debugLogs", "Debug Logs", "Extras", "Enable detailed logging to debug issues", 0, 22, SettingType.TOGGLE)
        ));
    }

    private int addCategorySettings(String category, int baseY, List<SettingDef> defs) {
        int catY = baseY;
        settingDefs.add(new SettingDef("_cat_" + category, category, category, "", catY, CATEGORY_HEIGHT, null));
        int sOff = CATEGORY_HEIGHT + 2;
        for (SettingDef def : defs) {
            settingDefs.add(new SettingDef(def.id(), def.label(), def.category(), def.tooltip(), catY + sOff, def.height(), def.type()));
            sOff += def.height() + 2;
        }
        return baseY + sOff + 6;
    }

    private void toggleCategory(String category) {
        if (expandedCategories.contains(category)) {
            expandedCategories.remove(category);
        } else {
            expandedCategories.add(category);
        }
        updateFolderWidgets();
    }

    private void updateFolderWidgets() {
        if (folderInput != null) {
            removeWidget(folderInput);
            folderInput = null;
        }
        if (browseButton != null) {
            removeWidget(browseButton);
            browseButton = null;
        }
        createFolderWidgets();
    }

    @Override
    public void rebuildWidgets() {
        this.children().clear();
        scrollY = 0;
        folderInput = null;
        browseButton = null;
        this.init();
    }

    // ─── Setting value helpers ───

    private String getToggleText(String id) {
        boolean val = switch (id) {
            case "showNowPlayingToast" -> config.isShowNowPlayingToast();
            case "fadeOnPause" -> config.isFadeOnPause();
            case "eqEnabled" -> config.isEqEnabled();
            case "reverbEnabled" -> config.isReverbEnabled();
            case "duckOnDamage" -> config.isDuckOnDamage();
            case "pauseOnOpen" -> config.isPauseOnOpen();
            case "pauseOnPauseScreen" -> config.isPauseOnPauseScreen();
            case "stopWhenUnfocused" -> config.isStopWhenUnfocused();
            case "visualizerEnabled" -> config.isVisualizerEnabled();
            case "miniPlayerEnabled" -> config.isMiniPlayerEnabled();
            case "debugLogs" -> config.isDebugLogsEnabled();
            case "queuePersistence" -> config.isQueuePersistence();
            default -> false;
        };
        return val ? "ON" : "OFF";
    }

    private void toggleSetting(String id) {
        switch (id) {
            case "showNowPlayingToast" -> config.setShowNowPlayingToast(!config.isShowNowPlayingToast());
            case "fadeOnPause" -> config.setFadeOnPause(!config.isFadeOnPause());
            case "eqEnabled" -> config.setEqEnabled(!config.isEqEnabled());
            case "reverbEnabled" -> config.setReverbEnabled(!config.isReverbEnabled());
            case "duckOnDamage" -> config.setDuckOnDamage(!config.isDuckOnDamage());
            case "pauseOnOpen" -> config.setPauseOnOpen(!config.isPauseOnOpen());
            case "pauseOnPauseScreen" -> config.setPauseOnPauseScreen(!config.isPauseOnPauseScreen());
            case "stopWhenUnfocused" -> config.setStopWhenUnfocused(!config.isStopWhenUnfocused());
            case "visualizerEnabled" -> config.setVisualizerEnabled(!config.isVisualizerEnabled());
            case "miniPlayerEnabled" -> config.setMiniPlayerEnabled(!config.isMiniPlayerEnabled());
            case "debugLogs" -> config.setDebugLogsEnabled(!config.isDebugLogsEnabled());
            case "queuePersistence" -> config.setQueuePersistence(!config.isQueuePersistence());
        }
    }

    private String getCycleText(String id) {
        return switch (id) {
            case "playbackMode" -> config.getPlaybackMode().name();
            case "playbackSpeed" -> String.format("%.2f", config.getPlaybackSpeed()).replaceAll("0*$", "").replaceAll("\\.$", "") + "x";
            case "nowPlayingStyle" -> config.getNowPlayingStyle().name();
            case "miniPlayerStyle" -> config.getMiniPlayerStyle() == MusicPlayerConfig.MiniPlayerStyle.BIG ? "Big" : "Compact";
            case "themeSelector" -> com.musicplayer.theme.ColorTheme.get(config.getThemeId()).name();
            default -> "";
        };
    }

    private void cycleSetting(String id) {
        switch (id) {
            case "playbackMode" -> {
                var vals = MusicPlayerConfig.PlaybackMode.values();
                config.setPlaybackMode(vals[(config.getPlaybackMode().ordinal() + 1) % vals.length]);
            }
            case "playbackSpeed" -> {
                float[] presets = {0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f};
                float cur = config.getPlaybackSpeed();
                int idx = 2;
                for (int i = 0; i < presets.length; i++) {
                    if (Math.abs(cur - presets[i]) < 0.01f) { idx = i; break; }
                }
                config.setPlaybackSpeed(presets[(idx + 1) % presets.length]);
            }
            case "nowPlayingStyle" -> {
                var vals = MusicPlayerConfig.NowPlayingStyle.values();
                config.setNowPlayingStyle(vals[(config.getNowPlayingStyle().ordinal() + 1) % vals.length]);
            }
            case "miniPlayerStyle" -> {
                var vals = MusicPlayerConfig.MiniPlayerStyle.values();
                config.setMiniPlayerStyle(vals[(config.getMiniPlayerStyle().ordinal() + 1) % vals.length]);
            }
            case "themeSelector" -> {
                var themes = com.musicplayer.theme.ColorTheme.all().keySet().toArray(new String[0]);
                String cur = config.getThemeId();
                int idx = 0;
                for (int i = 0; i < themes.length; i++) {
                    if (themes[i].equals(cur)) { idx = i; break; }
                }
                config.setThemeId(themes[(idx + 1) % themes.length]);
            }
        }
    }

    private float getSliderFloat(String id) {
        return switch (id) {
            case "volume" -> config.getVolume();
            case "masterVolume" -> config.getMasterVolume();
            case "preamp" -> config.getPreamp() / 2.0f;
            case "reverbMix" -> config.getReverbWet();
            case "duckVolume" -> config.getDuckVolume();
            case "customVolumeLevel" -> config.getCustomVolumeKeybindLevel();
            default -> {
                if (id.startsWith("eqBand")) {
                    int band = Integer.parseInt(id.substring(6));
                    float[] gains = config.getEqGains();
                    yield gains.length > band ? (gains[band] + 12f) / 24f : 0.5f;
                }
                yield 0;
            }
        };
    }

    private void setSliderFloat(String id, float v) {
        switch (id) {
            case "volume" -> config.setVolume(v);
            case "masterVolume" -> config.setMasterVolume(v);
            case "preamp" -> config.setPreamp(v * 2.0f);
            case "reverbMix" -> config.setReverbWet(v);
            case "duckVolume" -> config.setDuckVolume(v);
            case "customVolumeLevel" -> config.setCustomVolumeKeybindLevel(v);
            default -> {
                if (id.startsWith("eqBand")) {
                    int band = Integer.parseInt(id.substring(6));
                    float gain = v * 24f - 12f;
                    config.setEqBand(band, gain);
                }
            }
        }
    }

    private int getSliderInt(String id) {
        return switch (id) {
            case "fadeDuration" -> config.getFadeDurationMs();
            case "duckDuration" -> config.getDuckDurationMs();
            case "songEntryHeight" -> config.getSongEntryHeight();
            default -> 0;
        };
    }

    private void setSliderInt(String id, int v) {
        switch (id) {
            case "fadeDuration" -> config.setFadeDurationMs(v);
            case "duckDuration" -> config.setDuckDurationMs(v);
            case "songEntryHeight" -> config.setSongEntryHeight(v);
        }
    }

    private void applyFolder() {
        if (folderInput == null) return;
        String path = folderInput.getValue().trim();
        if (path.isEmpty()) return;
        File folder = new File(path);
        if (folder.exists() && folder.isDirectory()) {
            manager.setMusicFolder(path);
            String folderName = folder.getName();
            Playlist pl = new Playlist(folderName);
            for (SongInfo s : manager.getPlaylist()) pl.addSong(s);
            PlaylistManager.getInstance().savePlaylist(pl);
            if (parent instanceof MusicPlayerScreen mps) mps.refreshSongList();
        }
    }

    // ─── Search / Visibility ───

    private String currentFilter() {
        return searchBox == null ? "" : searchBox.getValue().toLowerCase().trim();
    }

    private boolean matchesFilter(SettingDef def, String filter) {
        if (filter.isEmpty()) return true;
        if (def.id() != null && def.id().startsWith("_cat_")) return true;
        if (def.label().toLowerCase().contains(filter)) return true;
        if (def.category().toLowerCase().contains(filter)) return true;
        if (def.tooltip().toLowerCase().contains(filter)) return true;
        return false;
    }

    // ─── Rendering ───

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, this.width, this.height, 0xC0000000);
        int pb = panelTop + panelHeight;
        graphics.fill(panelLeft - 2, panelTop - 2, panelLeft + PANEL_WIDTH + 2, pb + 2, 0xFF555555);
        graphics.fill(panelLeft, panelTop, panelLeft + PANEL_WIDTH, pb, 0xFF2D2D2D);
        graphics.centeredText(this.font, this.title, panelLeft + PANEL_WIDTH / 2, panelTop + 8, 0xFFFFFFFF);
        graphics.text(this.font, "Search:", panelLeft + PANEL_WIDTH - 70, panelTop + 28, 0xFFAAAAAA, true);

        hoveredTooltip = null;
        String filter = currentFilter();
        boolean searchActive = !filter.isEmpty();
        int labelX = panelLeft + 14;
        int sliderX = panelLeft + 110;
        int sliderW = PANEL_WIDTH - 155;

        int clipTop = panelTop + 44;
        int clipBottom = panelTop + panelHeight - 30;

        // Enable scissor test via fill trick — content renders in clipped area
        int renderY = contentY;
        for (SettingDef def : settingDefs) {
            boolean isCat = def.id() != null && def.id().startsWith("_cat_");
            if (isCat) {
                boolean isExpanded = expandedCategories.contains(def.label());
                String arrow = isExpanded ? "\u25BC" : "\u25B6";
                int sy = renderY + scrollY;
                if (sy + CATEGORY_HEIGHT >= clipTop && sy <= clipBottom) {
                    graphics.fill(panelLeft + 4, sy, panelLeft + PANEL_WIDTH - 4, sy + CATEGORY_HEIGHT, 0xFF333333);
                    graphics.fill(panelLeft + 4, sy + CATEGORY_HEIGHT - 1, panelLeft + PANEL_WIDTH - 4, sy + CATEGORY_HEIGHT, 0xFF555555);
                    int catColor = searchActive ? 0xFFFFAA00 : 0xFFFFDD77;
                    graphics.text(this.font, arrow + " " + def.label(), labelX, sy + 4, catColor, true);
                }
                renderY += CATEGORY_HEIGHT + 2;
                continue;
            }

            boolean settingVisible = searchActive
                ? matchesFilter(def, filter)
                : expandedCategories.contains(def.category());
            if (!settingVisible) continue;

            int sy = renderY + scrollY;
            renderY += def.height() + 2;

            if (sy + def.height() < clipTop || sy > clipBottom) continue;

            if (def.type() == SettingType.FOLDER) {
                graphics.text(this.font, "Folder:", labelX, sy + 4, 0xFFAAAAAA, true);
                if (statusMessage != Component.empty()) {
                    graphics.centeredText(this.font, statusMessage, panelLeft + PANEL_WIDTH / 2, sy + 24, statusColor);
                }
                continue;
            }

            if (def.type() == SettingType.TOGGLE || def.type() == SettingType.CYCLE || def.type() == SettingType.ACTION) {
                graphics.text(this.font, def.label(), labelX, sy + 4, 0xFFCCCCCC, true);
                // Draw button
                int bx = sliderX + sliderW - (def.type() == SettingType.CYCLE ? 72 : def.type() == SettingType.ACTION ? 56 : 44);
                int bw = def.type() == SettingType.CYCLE ? 72 : def.type() == SettingType.ACTION ? 56 : 44;
                boolean btnHovered = mouseX >= bx && mouseX <= bx + bw && mouseY >= sy && mouseY <= sy + 20;
                graphics.fill(bx, sy, bx + bw, sy + 20, btnHovered ? 0xFF555555 : 0xFF333333);
                graphics.fill(bx + 1, sy + 1, bx + bw - 1, sy + 19, btnHovered ? 0xFF444444 : 0xFF222222);
                String btnText = switch (def.type()) {
                    case TOGGLE -> getToggleText(def.id());
                    case CYCLE -> getCycleText(def.id());
                    case ACTION -> "Open \u2192";
                    default -> "";
                };
                graphics.centeredText(this.font, btnText, bx + bw / 2, sy + 4, 0xFFFFFFFF);
                continue;
            }

            if (def.type() == SettingType.SLIDER_FLOAT || def.type() == SettingType.SLIDER_INT) {
                graphics.text(this.font, def.label(), labelX, sy + 4, 0xFFCCCCCC, true);

                int sl = sliderX;
                int sw = sliderW;
                int st = sy + 4;
                int sh = 12;
                boolean hovered = mouseX >= sl && mouseX <= sl + sw && mouseY >= st && mouseY <= st + sh;

                graphics.fill(sl, st, sl + sw, st + sh, hovered ? 0xFF444444 : 0xFF333333);
                graphics.fill(sl, st + 1, sl + sw, st + sh - 1, 0xFF222222);

                float norm;
                if (def.type() == SettingType.SLIDER_FLOAT) {
                    norm = getSliderFloat(def.id());
                } else if (def.id().equals("duckDuration")) {
                    norm = (getSliderInt(def.id()) - 500f) / 4500f;
                } else if (def.id().equals("songEntryHeight")) {
                    norm = getSliderInt(def.id()) / 36f;
                } else {
                    norm = (getSliderInt(def.id()) - 200f) / 1800f;
                }
                int fillW = Math.round(norm * sw);
                if (fillW > 0) {
                    graphics.fill(sl + 1, st + 1, sl + fillW, st + sh - 1, 0xFFAAAAAA);
                }
                graphics.fill(sl + fillW - 1, st - 1, sl + fillW + 2, st + sh + 1, 0xFFFFFFFF);

                String valStr;
                if (def.type() == SettingType.SLIDER_FLOAT) {
                    valStr = switch (def.id()) {
                        case "preamp" -> String.format("%.1f", getSliderFloat(def.id()) * 2.0f) + "x";
                        case "reverbMix" -> Math.round(getSliderFloat(def.id()) * 100) + "%";
                        case "volume" -> Math.round(getSliderFloat(def.id()) * 100) + "%";
                        case "masterVolume" -> Math.round(getSliderFloat(def.id()) * 100) + "%";
                        case "duckVolume" -> Math.round(getSliderFloat(def.id()) * 100) + "%";
                        case "customVolumeLevel" -> Math.round(getSliderFloat(def.id()) * 100) + "%";
                        default -> {
                            if (def.id().startsWith("eqBand")) {
                                float vn = getSliderFloat(def.id());
                                float db = vn * 24f - 12f;
                                yield (db >= 0 ? "+" : "") + String.format("%.0f", db) + "dB";
                            }
                            yield Math.round(getSliderFloat(def.id()) * 100) + "%";
                        }
                    };
                } else if (def.id().equals("songEntryHeight")) {
                    int h = getSliderInt(def.id());
                    valStr = h > 0 ? h + "px" : "Auto";
                } else {
                    valStr = getSliderInt(def.id()) + "ms";
                }

                if (def.type() == SettingType.SLIDER_INT && def.id().equals(typingSliderId)) {
                    int ex = sl + sw + 4;
                    int ey = sy + 3;
                    int ew = 52;
                    int eh = 14;
                    graphics.fill(ex, ey, ex + ew, ey + eh, 0xFF555555);
                    graphics.fill(ex + 1, ey + 1, ex + ew - 1, ey + eh - 1, 0xFF222222);
                    graphics.text(this.font, typingBuffer + (System.currentTimeMillis() / 600 % 2 == 0 ? "_" : " "), ex + 3, ey + 2, 0xFFFFFFFF, true);
                } else {
                    graphics.text(this.font, valStr, sl + sw + 6, sy + 4, 0xFFAAAAAA, true);
                }

                if (hovered) {
                    hoveredTooltip = def.tooltip();
                }
            }
        }

        // Draw scrollbar
        int sbLeft = panelLeft + PANEL_WIDTH - 8;
        int sbTop = panelTop + 44;
        int sbHeight = clipBottom - clipTop;
        int visibleContentH = computeVisibleContentHeight();
        if (visibleContentH > sbHeight) {
            float thumbRatio = (float) sbHeight / visibleContentH;
            int thumbH = Math.max(8, (int) (sbHeight * thumbRatio));
            float scrollFrac = (float) -scrollY / (visibleContentH - sbHeight);
            int thumbY = sbTop + (int) (scrollFrac * (sbHeight - thumbH));
            graphics.fill(sbLeft, sbTop, sbLeft + 4, sbTop + sbHeight, 0x40000000);
            graphics.fill(sbLeft, thumbY, sbLeft + 4, thumbY + thumbH, 0x80808080);
        }

        if (hoveredTooltip != null && !hoveredTooltip.isEmpty()) {
            int tw = this.font.width(hoveredTooltip);
            int tx = Math.min(mouseX + 10, this.width - tw - 12);
            int ty = Math.max(8, mouseY - 18);
            graphics.fill(tx - 4, ty - 2, tx + tw + 4, ty + 12, 0xCC333333);
            graphics.fill(tx - 3, ty - 1, tx + tw + 3, ty + 11, 0xFF555555);
            graphics.text(this.font, hoveredTooltip, tx, ty, 0xFFFFFFFF, true);
        }

        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }

    // ─── Keyboard ───

    @Override
    public boolean keyPressed(net.minecraft.client.input.KeyEvent event) {
        if (typingSliderId != null) {
            int key = event.key();
            if (key == InputConstants.KEY_RETURN || key == InputConstants.KEY_NUMPADENTER) {
                finishTyping();
                return true;
            }
            if (key == InputConstants.KEY_ESCAPE) {
                cancelTyping();
                return true;
            }
            if (key == InputConstants.KEY_BACKSPACE && !typingBuffer.isEmpty()) {
                typingBuffer = typingBuffer.substring(0, typingBuffer.length() - 1);
                return true;
            }
            return true;
        }
        if (super.keyPressed(event)) return true;
        if (searchBox != null && searchBox.isFocused()) return searchBox.keyPressed(event);
        return false;
    }

    @Override
    public boolean charTyped(net.minecraft.client.input.CharacterEvent event) {
        if (typingSliderId != null) {
            char ch = (char) event.codepoint();
            if (Character.isDigit(ch) || ch == '-') {
                if (typingBuffer.length() < 8) {
                    typingBuffer += ch;
                }
                return true;
            }
            return true;
        }
        if (searchBox != null && searchBox.isFocused()) return searchBox.charTyped(event);
        return super.charTyped(event);
    }

    // ─── Mouse ───

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean isFocused) {
        if (typingSliderId != null) {
            cancelTyping();
            return true;
        }
        if (super.mouseClicked(event, isFocused)) return true;
        if (event.buttonInfo().button() != com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT) return false;

        double mx = event.x();
        double my = event.y();

        String filter = currentFilter();
        boolean searchActive = !filter.isEmpty();
        int sliderX = panelLeft + 110;
        int sliderW = PANEL_WIDTH - 155;

        int renderY = contentY;
        for (SettingDef def : settingDefs) {
            boolean isCat = def.id() != null && def.id().startsWith("_cat_");
            if (isCat) {
                int sy = renderY + scrollY;
                if (mx >= panelLeft + 4 && mx <= panelLeft + PANEL_WIDTH - 4 &&
                    my >= sy && my <= sy + CATEGORY_HEIGHT) {
                    toggleCategory(def.label());
                    return true;
                }
                renderY += CATEGORY_HEIGHT + 2;
                continue;
            }

            boolean settingVisible = searchActive
                ? matchesFilter(def, filter)
                : expandedCategories.contains(def.category());
            if (!settingVisible) continue;

            int sy = renderY + scrollY;
            renderY += def.height() + 2;

            if (def.type() == SettingType.TOGGLE || def.type() == SettingType.CYCLE || def.type() == SettingType.ACTION) {
                int bw = def.type() == SettingType.CYCLE ? 72 : def.type() == SettingType.ACTION ? 56 : 44;
                int bx = sliderX + sliderW - bw;
                if (mx >= bx && mx <= bx + bw && my >= sy && my <= sy + 20) {
                    switch (def.type()) {
                        case TOGGLE -> toggleSetting(def.id());
                        case CYCLE -> cycleSetting(def.id());
                        case ACTION -> this.minecraft.gui.setScreen(new ListeningStatsScreen(this));
                        default -> {}
                    }
                    return true;
                }
                continue;
            }

            if (def.type() != SettingType.SLIDER_FLOAT && def.type() != SettingType.SLIDER_INT) continue;

            int st = sy + 4;
            int sh = 12;
            if (mx >= sliderX && mx <= sliderX + sliderW && my >= st && my <= st + sh) {
                // Double-click on SLIDER_INT → open typing input
                if (def.type() == SettingType.SLIDER_INT) {
                    long now = System.currentTimeMillis();
                    if (def.id().equals(lastSliderClickId) && now - lastSliderClickTime < 500) {
                        startTyping(def.id());
                        return true;
                    }
                    lastSliderClickId = def.id();
                    lastSliderClickTime = now;
                }
                draggingSliderId = def.id();
                setSliderFromMouse(def.id(), mx - sliderX, sliderW);
                return true;
            }
        }

        // Check scrollbar click
        int scrollableH = panelHeight - 60;
        int visH = computeVisibleContentHeight();
        if (visH > scrollableH) {
            int sbLeft = panelLeft + PANEL_WIDTH - 8;
            int sbTop = panelTop + 44;
            int sbH = scrollableH;
            if (mx >= sbLeft && mx <= sbLeft + 4 && my >= sbTop && my <= sbTop + sbH) {
                float thumbRatio = (float) sbH / visH;
                int thumbH = Math.max(8, (int) (sbH * thumbRatio));
                float scrollFrac = (float) -scrollY / (visH - sbH);
                int thumbY = sbTop + (int) (scrollFrac * (sbH - thumbH));
                if (my >= thumbY && my <= thumbY + thumbH) {
                    draggingScrollbar = true;
                    scrollbarDragStartY = (int) my;
                    scrollbarDragStartScroll = scrollY;
                    return true;
                }
            }
        }

        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollYAmt) {
        int scrollableHeight = panelHeight - 60;
        int visibleContentH = computeVisibleContentHeight();
        int maxScroll = Math.max(0, visibleContentH - scrollableHeight);
        int oldScrollY = scrollY;
        scrollY += (int) (scrollYAmt * SCROLL_SPEED);
        scrollY = Math.max(-maxScroll, Math.min(0, scrollY));
        if (scrollY != oldScrollY) {
            updateFolderWidgetPositions();
        }
        return true;
    }

    private int computeVisibleContentHeight() {
        String filter = currentFilter();
        boolean searchActive = !filter.isEmpty();
        int h = 0;
        for (SettingDef def : settingDefs) {
            boolean isCat = def.id() != null && def.id().startsWith("_cat_");
            if (isCat) {
                h += CATEGORY_HEIGHT + 2;
                continue;
            }
            boolean visible = searchActive
                ? matchesFilter(def, filter)
                : expandedCategories.contains(def.category());
            if (visible) {
                h += def.height() + 2;
            }
        }
        return h + CONTENT_PADDING_BOTTOM;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        if (draggingSliderId != null && event.buttonInfo().button() == com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT) {
            int sliderX = panelLeft + 110;
            int sliderW = PANEL_WIDTH - 155;
            setSliderFromMouse(draggingSliderId, event.x() - sliderX, sliderW);
            return true;
        }
        if (draggingScrollbar && event.buttonInfo().button() == com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT) {
            int scrollableH = panelHeight - 60;
            int visH = computeVisibleContentHeight();
            if (visH > scrollableH) {
                int sbTop = panelTop + 44;
                int sbH = scrollableH;
                float thumbRatio = (float) sbH / visH;
                int thumbH = Math.max(8, (int) (sbH * thumbRatio));
                float pixelsPerScroll = (float) (visH - scrollableH) / (sbH - thumbH);
                int dy = (int) event.y() - scrollbarDragStartY;
                scrollY = Math.max(-(visH - scrollableH), Math.min(0, scrollbarDragStartScroll + (int) (dy * pixelsPerScroll)));
                updateFolderWidgetPositions();
            }
            return true;
        }
        return super.mouseDragged(event, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (event.buttonInfo().button() == com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT) {
            if (draggingSliderId != null) {
                draggingSliderId = null;
                return true;
            }
            if (draggingScrollbar) {
                draggingScrollbar = false;
                return true;
            }
        }
        return super.mouseReleased(event);
    }

    // ─── Double-click to type ───

    private void startTyping(String id) {
        typingSliderId = id;
        draggingSliderId = null;
        switch (id) {
            case "songEntryHeight" -> {
                int h = config.getSongEntryHeight();
                typingBuffer = h > 0 ? String.valueOf(h) : "0";
            }
            case "fadeDuration" -> typingBuffer = String.valueOf(config.getFadeDurationMs());
            case "duckDuration" -> typingBuffer = String.valueOf(config.getDuckDurationMs());
        }
    }

    private void finishTyping() {
        if (typingSliderId == null) return;
        try {
            switch (typingSliderId) {
                case "fadeDuration" -> config.setFadeDurationMs(Math.max(200, Math.min(2000, Integer.parseInt(typingBuffer))));
                case "duckDuration" -> config.setDuckDurationMs(Math.max(500, Math.min(5000, Integer.parseInt(typingBuffer))));
                case "songEntryHeight" -> config.setSongEntryHeight(Math.max(0, Math.min(36, Integer.parseInt(typingBuffer))));
            }
        } catch (NumberFormatException ignored) {}
        typingSliderId = null;
        typingBuffer = "";
    }

    private void cancelTyping() {
        typingSliderId = null;
        typingBuffer = "";
    }

    private void setSliderFromMouse(String id, double offset, int width) {
        float norm = (float) Math.max(0, Math.min(1, offset / width));
        if (id.equals("fadeDuration")) {
            setSliderInt(id, 200 + Math.round(norm * 1800));
        } else if (id.equals("duckDuration")) {
            setSliderInt(id, 500 + Math.round(norm * 4500));
        } else if (id.equals("songEntryHeight")) {
            setSliderInt(id, Math.round(norm * 36f));
        } else {
            setSliderFloat(id, norm);
        }
    }

    @Override
    public void onClose() {
        this.minecraft.gui.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return config.isPauseOnOpen() && minecraft != null && minecraft.level != null;
    }
}
