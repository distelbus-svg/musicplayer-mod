package com.musicplayer.stats;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

public class ListeningStatisticsManager {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path STATS_FILE = FabricLoader.getInstance()
            .getConfigDir().resolve("musicplayer").resolve("stats.json");

    private static ListeningStatisticsManager instance;

    private long totalListeningTimeSeconds = 0;
    private Map<String, Integer> playCounts = new HashMap<>();
    private Map<String, Long> listeningTimes = new HashMap<>();

    // Session state (transient — not persisted)
    private transient long sessionListeningTimeSeconds = 0;
    private transient Map<String, Integer> sessionPlayCounts = new HashMap<>();
    private transient Map<String, Long> sessionListeningTimes = new HashMap<>();

    private transient long currentTrackStartMs = -1;
    private transient String currentTrackPath = null;

    private ListeningStatisticsManager() {
    }

    public static synchronized ListeningStatisticsManager getInstance() {
        if (instance == null) {
            instance = new ListeningStatisticsManager();
            instance.load();
        }
        return instance;
    }

    public synchronized void load() {
        if (!Files.exists(STATS_FILE)) return;
        try {
            String json = Files.readString(STATS_FILE);
            ListeningStatisticsManager data = GSON.fromJson(json, ListeningStatisticsManager.class);
            if (data != null) {
                this.totalListeningTimeSeconds = data.totalListeningTimeSeconds;
                this.playCounts.clear();
                this.playCounts.putAll(data.playCounts);
                this.listeningTimes.clear();
                if (data.listeningTimes != null) {
                    this.listeningTimes.putAll(data.listeningTimes);
                }
            }
        } catch (IOException e) {
            System.err.println("[MusicPlayer] Failed to load stats: " + e.getMessage());
        }
    }

    public synchronized void save() {
        try {
            Files.createDirectories(STATS_FILE.getParent());
            Files.writeString(STATS_FILE, GSON.toJson(this));
        } catch (IOException e) {
            System.err.println("[MusicPlayer] Failed to save stats: " + e.getMessage());
        }
    }

    public synchronized void startTrack(String songPath) {
        stopTrack();

        currentTrackPath = songPath;
        currentTrackStartMs = System.currentTimeMillis();

        playCounts.put(songPath, playCounts.getOrDefault(songPath, 0) + 1);
        sessionPlayCounts.put(songPath, sessionPlayCounts.getOrDefault(songPath, 0) + 1);
        save();
    }

    public synchronized void stopTrack() {
        if (currentTrackStartMs != -1 && currentTrackPath != null) {
            long elapsedMs = System.currentTimeMillis() - currentTrackStartMs;
            long elapsedSecs = elapsedMs / 1000;
            if (elapsedSecs > 0) {
                totalListeningTimeSeconds += elapsedSecs;
                sessionListeningTimeSeconds += elapsedSecs;
                listeningTimes.put(currentTrackPath, listeningTimes.getOrDefault(currentTrackPath, 0L) + elapsedSecs);
                sessionListeningTimes.put(currentTrackPath, sessionListeningTimes.getOrDefault(currentTrackPath, 0L) + elapsedSecs);
            }
            currentTrackStartMs = -1;
            currentTrackPath = null;
        }
    }

    public synchronized void pauseTrack() {
        stopTrack();
    }

    public synchronized void resumeTrack(String songPath) {
        currentTrackPath = songPath;
        currentTrackStartMs = System.currentTimeMillis();
    }

    public synchronized long getTotalListeningTimeSeconds() { return totalListeningTimeSeconds; }
    public synchronized Map<String, Integer> getPlayCounts() { return new HashMap<>(playCounts); }
    public synchronized Map<String, Long> getListeningTimes() { return new HashMap<>(listeningTimes); }

    public synchronized long getSessionListeningTimeSeconds() { return sessionListeningTimeSeconds; }
    public synchronized Map<String, Integer> getSessionPlayCounts() { return new HashMap<>(sessionPlayCounts); }
    public synchronized Map<String, Long> getSessionListeningTimes() { return new HashMap<>(sessionListeningTimes); }
}
