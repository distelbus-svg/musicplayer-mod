package com.musicplayer.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Manages persistent configuration for the Music Player mod.
 * Stores music folder path, volume, last played song, and playback mode.
 */
public class MusicPlayerConfig {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_PATH = FabricLoader.getInstance()
            .getConfigDir().resolve("musicplayer.json");

    private static MusicPlayerConfig instance;

    // Config fields
    private String musicFolderPath = "";
    private float volume = 0.75f;
    private float masterVolume = 1.0f;
    private String lastPlayedSong = "";
    private PlaybackMode playbackMode = PlaybackMode.SEQUENTIAL;
    private NowPlayingStyle nowPlayingStyle = NowPlayingStyle.WIDGET;
    private boolean showNowPlayingToast = true;
    private boolean fadeOnPause = true;
    private int fadeDurationMs = 500;
    private float playbackSpeed = 1.0f;
    private List<String> likedSongPaths = new ArrayList<>();

    // Audio effects
    private boolean eqEnabled = false;
    private float preamp = 1.0f;
    private List<Float> eqGains = new ArrayList<>(java.util.Arrays.asList(0f, 0f, 0f, 0f, 0f));
    private boolean reverbEnabled = false;
    private float reverbWet = 0.3f;

    // Crossfade
    private boolean crossfadeEnabled = true;
    private int crossfadeDurationMs = 2000;

    // Visualizer
    private boolean visualizerEnabled = false;

    // Mini per-track equalizer in the song list (replaces the ▶ indicator on the playing row)
    private boolean miniVisualizerEnabled = true;

    // Mini player
    private boolean miniPlayerEnabled = false;
    private int miniPlayerX = 10;
    private int miniPlayerY = 10;
    private int miniPlayerWidth = 150;
    private int miniPlayerHeight = 200;

    // Volume duck
    private boolean duckOnDamage = true;
    private float duckVolume = 0.2f;
    private int duckDurationMs = 2000;
    private float customVolumeKeybindLevel = 0.5f;

    // Vanilla music
    private boolean suppressVanillaMusic = true;
    private boolean pauseOnOpen = false;
    private boolean stopWhenUnfocused = false;
    private boolean pauseOnPauseScreen = true;
    private String themeId = "dark";

    // Mini player
    private MiniPlayerStyle miniPlayerStyle = MiniPlayerStyle.COMPACT;

    // UI layout: full-screen immersive (true) or legacy small-window (false)
    private boolean newUiEnabled = true;

    // Panel resize persistence
    private boolean rememberResizing = true;
    private int sidebarWidth = 0;
    private int heroWidth = 0;

    // Debug
    private boolean debugLogs = false;

    // Persisted folder paths (for the playlist editor source tabs)
    private List<String> folderPaths = new ArrayList<>();

    public enum NowPlayingStyle { WIDGET, ABOVE_HOTBAR, OFF }

    public enum MiniPlayerStyle { BIG, COMPACT }

    public enum PlaybackMode {
        SEQUENTIAL,
        SHUFFLE,
        REPEAT_ALL,
        REPEAT_ONE
    }

    private MusicPlayerConfig() {}

    public static MusicPlayerConfig getInstance() {
        if (instance == null) {
            instance = load();
        }
        return instance;
    }

    /**
     * Loads config from disk, or returns defaults if file doesn't exist.
     */
    private static MusicPlayerConfig load() {
        if (Files.exists(CONFIG_PATH)) {
            try {
                String json = Files.readString(CONFIG_PATH);
                MusicPlayerConfig config = GSON.fromJson(json, MusicPlayerConfig.class);
                if (config != null) {
                    return config;
                }
            } catch (IOException e) {
                System.err.println("[MusicPlayer] Failed to load config: " + e.getMessage());
            }
        }
        return new MusicPlayerConfig();
    }

    /**
     * Saves the current config to disk.
     */
    public void save() {
        try {
            Files.createDirectories(CONFIG_PATH.getParent());
            Files.writeString(CONFIG_PATH, GSON.toJson(this));
        } catch (IOException e) {
            System.err.println("[MusicPlayer] Failed to save config: " + e.getMessage());
        }
    }

    // --- Getters and Setters ---

    public String getMusicFolderPath() {
        return musicFolderPath;
    }

    public void setMusicFolderPath(String path) {
        this.musicFolderPath = path;
        save();
    }

    public float getVolume() {
        return volume;
    }

    public void setVolume(float volume) {
        this.volume = Math.max(0f, Math.min(1f, volume));
        save();
    }

    public float getMasterVolume() {
        return masterVolume;
    }

    public void setMasterVolume(float masterVolume) {
        this.masterVolume = Math.max(0f, Math.min(1f, masterVolume));
        save();
        com.musicplayer.MusicPlayerManager.getInstance().getEngine().updateMasterVolume();
    }

    public String getLastPlayedSong() {
        return lastPlayedSong;
    }

    public void setLastPlayedSong(String songName) {
        this.lastPlayedSong = songName;
        save();
    }

    public PlaybackMode getPlaybackMode() {
        return playbackMode;
    }

    public void setPlaybackMode(PlaybackMode mode) {
        this.playbackMode = mode;
        save();
    }

    public NowPlayingStyle getNowPlayingStyle() {
        return nowPlayingStyle;
    }

    public void setNowPlayingStyle(NowPlayingStyle style) {
        this.nowPlayingStyle = style;
        save();
    }

    public boolean isShowNowPlayingToast() {
        return showNowPlayingToast;
    }

    public void setShowNowPlayingToast(boolean show) {
        this.showNowPlayingToast = show;
        save();
    }

    public boolean isFadeOnPause() {
        return fadeOnPause;
    }

    public void setFadeOnPause(boolean fade) {
        this.fadeOnPause = fade;
        save();
    }

    public int getFadeDurationMs() {
        return fadeDurationMs;
    }

    public void setFadeDurationMs(int ms) {
        this.fadeDurationMs = Math.max(200, Math.min(2000, ms));
        save();
    }

    public float getPlaybackSpeed() {
        return playbackSpeed;
    }

    public void setPlaybackSpeed(float speed) {
        this.playbackSpeed = Math.max(0.5f, Math.min(2.0f, speed));
        save();
    }

    public boolean isEqEnabled() { return eqEnabled; }
    public void setEqEnabled(boolean v) { this.eqEnabled = v; save(); }

    public float getPreamp() { return preamp; }
    public void setPreamp(float v) { this.preamp = Math.max(0f, Math.min(2f, v)); save(); }

    public float[] getEqGains() {
        if (eqGains == null || eqGains.size() < 5) {
            eqGains = new ArrayList<>(java.util.Arrays.asList(0f, 0f, 0f, 0f, 0f));
        }
        float[] arr = new float[5];
        for (int i = 0; i < 5; i++) arr[i] = eqGains.get(i);
        return arr;
    }

    public void setEqBand(int band, float gain) {
        if (eqGains == null || eqGains.size() < 5) {
            eqGains = new ArrayList<>(java.util.Arrays.asList(0f, 0f, 0f, 0f, 0f));
        }
        eqGains.set(band, Math.max(-12f, Math.min(12f, gain)));
        save();
    }

    public boolean isReverbEnabled() { return reverbEnabled; }
    public void setReverbEnabled(boolean v) { this.reverbEnabled = v; save(); }

    public float getReverbWet() { return reverbWet; }
    public void setReverbWet(float v) { this.reverbWet = Math.max(0f, Math.min(1f, v)); save(); }

    public boolean isCrossfadeEnabled() { return crossfadeEnabled; }
    public void setCrossfadeEnabled(boolean v) { this.crossfadeEnabled = v; save(); }

    public int getCrossfadeDurationMs() { return crossfadeDurationMs; }
    public void setCrossfadeDurationMs(int ms) { this.crossfadeDurationMs = Math.max(500, Math.min(5000, ms)); save(); }

    public boolean isDuckOnDamage() { return duckOnDamage; }
    public void setDuckOnDamage(boolean v) { this.duckOnDamage = v; save(); }

    public float getDuckVolume() { return duckVolume; }
    public void setDuckVolume(float v) { this.duckVolume = Math.max(0f, Math.min(1f, v)); save(); }

    public int getDuckDurationMs() { return duckDurationMs; }
    public void setDuckDurationMs(int ms) { this.duckDurationMs = Math.max(500, Math.min(5000, ms)); save(); }

    public float getCustomVolumeKeybindLevel() { return customVolumeKeybindLevel; }
    public void setCustomVolumeKeybindLevel(float v) { this.customVolumeKeybindLevel = Math.max(0f, Math.min(1f, v)); save(); }

    public boolean isVisualizerEnabled() { return visualizerEnabled; }
    public void setVisualizerEnabled(boolean v) { this.visualizerEnabled = v; save(); }

    public boolean isMiniVisualizerEnabled() { return miniVisualizerEnabled; }
    public void setMiniVisualizerEnabled(boolean v) { this.miniVisualizerEnabled = v; save(); }

    public boolean isMiniPlayerEnabled() { return miniPlayerEnabled; }
    public void setMiniPlayerEnabled(boolean v) { this.miniPlayerEnabled = v; save(); }
    public int getMiniPlayerX() { return miniPlayerX; }
    public void setMiniPlayerX(int v) { this.miniPlayerX = Math.max(0, v); save(); }
    public int getMiniPlayerY() { return miniPlayerY; }
    public void setMiniPlayerY(int v) { this.miniPlayerY = Math.max(0, v); save(); }
    public int getMiniPlayerWidth() { return miniPlayerWidth; }
    public void setMiniPlayerWidth(int v) { this.miniPlayerWidth = Math.max(100, Math.min(300, v)); save(); }
    public int getMiniPlayerHeight() { return miniPlayerHeight; }
    public void setMiniPlayerHeight(int v) { this.miniPlayerHeight = Math.max(120, Math.min(400, v)); save(); }

    public boolean isSuppressVanillaMusic() { return suppressVanillaMusic; }
    public void setSuppressVanillaMusic(boolean v) { this.suppressVanillaMusic = v; save(); }

    public boolean isPauseOnOpen() { return pauseOnOpen; }
    public void setPauseOnOpen(boolean v) { this.pauseOnOpen = v; save(); }

    public boolean isStopWhenUnfocused() { return stopWhenUnfocused; }
    public void setStopWhenUnfocused(boolean v) { this.stopWhenUnfocused = v; save(); }

    public boolean isPauseOnPauseScreen() { return pauseOnPauseScreen; }
    public void setPauseOnPauseScreen(boolean v) { this.pauseOnPauseScreen = v; save(); }

    public MiniPlayerStyle getMiniPlayerStyle() { return miniPlayerStyle; }
    public void setMiniPlayerStyle(MiniPlayerStyle s) { this.miniPlayerStyle = s; save(); }

    public String getThemeId() { return themeId; }
    public void setThemeId(String id) { this.themeId = id; save(); }

    public boolean isNewUiEnabled() { return newUiEnabled; }
    public void setNewUiEnabled(boolean v) { this.newUiEnabled = v; save(); }

    public boolean isDebugLogsEnabled() { return debugLogs; }
    public void setDebugLogsEnabled(boolean v) { this.debugLogs = v; save(); }

    public List<String> getFolderPaths() { return folderPaths; }
    public void setFolderPaths(List<String> v) { this.folderPaths = new ArrayList<>(v); save(); }

    public boolean isLiked(String path) {
        return likedSongPaths.contains(path);
    }

    public void likeSong(String path) {
        if (!likedSongPaths.contains(path)) {
            likedSongPaths.add(path);
            save();
        }
    }

    public void unlikeSong(String path) {
        likedSongPaths.remove(path);
        save();
    }

    public Set<String> getLikedSongPaths() {
        return new HashSet<>(likedSongPaths);
    }

    public boolean isRememberResizing() { return rememberResizing; }
    public void setRememberResizing(boolean v) { this.rememberResizing = v; save(); }

    public int getSidebarWidth() { return sidebarWidth; }
    public void setSidebarWidth(int v) { this.sidebarWidth = v; save(); }

    public int getHeroWidth() { return heroWidth; }
    public void setHeroWidth(int v) { this.heroWidth = v; save(); }

    public void resetLayout() {
        sidebarWidth = 0;
        heroWidth = 0;
        save();
    }
}
