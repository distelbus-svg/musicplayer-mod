package com.musicplayer;

import com.musicplayer.port.*;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.musicplayer.config.MusicPlayerConfig;
import com.musicplayer.stats.ListeningStatisticsManager;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Exports everything Melodix stores about the user into one file and restores it again, e.g. on a new
 * instance: settings (which include the music folder, liked songs and the saved queue), equalizer presets,
 * listening statistics and playlists.
 *
 * The file is plain JSON with the extension {@code .melodix}. Song paths are stored as they are, so they only
 * resolve on a machine where the music sits at the same location.
 */
public final class DataExporter {

    public static final String EXTENSION = ".melodix";
    private static final String MAGIC = "melodix-backup";
    private static final int FORMAT_VERSION = 1;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private DataExporter() {}

    /** What an import changed, for the confirmation message. */
    public static final class Result {
        public boolean settings, stats, theme, keys;
        public int playlists, presets;
        public String musicFolder = "";
        public boolean musicFolderMissing;

        public String describe() {
            List<String> parts = new ArrayList<>();
            if (settings) parts.add("settings");
            if (theme) parts.add("theme");
            if (keys) parts.add("keybinds");
            if (presets > 0) parts.add(presets + (presets == 1 ? " EQ preset" : " EQ presets"));
            if (playlists > 0) parts.add(playlists + (playlists == 1 ? " playlist" : " playlists"));
            if (stats) parts.add("listening statistics");
            return parts.isEmpty() ? "Nothing to import" : "Imported " + String.join(", ", parts);
        }
    }

    /** Which parts of a backup to restore. Everything by default. */
    public static final class Options {
        public boolean settings = true, theme = true, playlists = true, keys = true, stats = true;
    }

    /** Basic facts about a backup file, for the file card. */
    public record FileInfo(String name, long bytes, java.time.LocalDate saved) {}

    /** Checks that the file is a Melodix backup (without applying anything) and describes it. */
    public static FileInfo inspect(Path in) throws IOException {
        parseRoot(in);
        long modified = Files.getLastModifiedTime(in).toMillis();
        java.time.LocalDate d = Instant.ofEpochMilli(modified).atZone(java.time.ZoneId.systemDefault()).toLocalDate();
        return new FileInfo(in.getFileName().toString(), Files.size(in), d);
    }

    private static JsonObject parseRoot(Path in) throws IOException {
        JsonElement parsedRoot;
        try {
            parsedRoot = com.google.gson.JsonParser.parseString(Files.readString(in, StandardCharsets.UTF_8));
        } catch (JsonParseException e) {
            throw new IOException("This is not a Melodix data file.");
        }
        if (!parsedRoot.isJsonObject()) throw new IOException("This is not a Melodix data file.");
        JsonObject root = parsedRoot.getAsJsonObject();
        if (!root.has("format") || !MAGIC.equals(root.get("format").getAsString())) {
            throw new IOException("This is not a Melodix data file.");
        }
        int version = root.has("formatVersion") ? root.get("formatVersion").getAsInt() : 0;
        if (version < 1 || version > FORMAT_VERSION) {
            throw new IOException("This file was made by a newer Melodix. Update the mod to import it.");
        }
        return root;
    }

    public static Path withExtension(Path p) {
        String name = p.getFileName().toString();
        if (name.toLowerCase(java.util.Locale.ROOT).endsWith(EXTENSION)) return p;
        return p.resolveSibling(name + EXTENSION);
    }

    public static String defaultFileName() {
        return "melodix-backup-" + java.time.LocalDate.now() + EXTENSION;
    }

    // ---------------------------------------------------------------- export

    public static void export(Path out) throws IOException {
        JsonObject root = new JsonObject();
        root.addProperty("format", MAGIC);
        root.addProperty("formatVersion", FORMAT_VERSION);
        root.addProperty("modVersion", Ports.host().modVersion());
        root.addProperty("exportedAt", Instant.now().toString());

        root.add("config", MusicPlayerConfig.getInstance().toJsonTree());
        root.add("eqPresets", GSON.toJsonTree(EqPresetManager.userPresets()));

        ListeningStatisticsManager st = ListeningStatisticsManager.getInstance();
        JsonObject stats = new JsonObject();
        stats.addProperty("totalListeningTimeSeconds", st.getTotalListeningTimeSeconds());
        stats.add("playCounts", GSON.toJsonTree(st.getPlayCounts()));
        stats.add("listeningTimes", GSON.toJsonTree(st.getListeningTimes()));
        root.add("stats", stats);

        JsonArray playlists = new JsonArray();
        for (Playlist p : new ArrayList<>(PlaylistManager.getInstance().getPlaylists())) {
            playlists.add(GSON.toJsonTree(p, Playlist.class));
        }
        root.add("playlists", playlists);

        Path parent = out.toAbsolutePath().getParent();
        if (parent != null) Files.createDirectories(parent);
        Path tmp = out.resolveSibling(out.getFileName() + ".tmp");
        Files.writeString(tmp, GSON.toJson(root), StandardCharsets.UTF_8);
        try {
            Files.move(tmp, out, java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                    java.nio.file.StandardCopyOption.ATOMIC_MOVE);
        } catch (java.nio.file.AtomicMoveNotSupportedException e) {
            Files.move(tmp, out, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
    }

    // ---------------------------------------------------------------- import

    /**
     * Restores a file written by {@link #export}. The whole file is parsed and checked first, so a bad file
     * changes nothing. Playlists with the same name are replaced; other playlists are kept. Settings,
     * equalizer presets and statistics are replaced. Call on the game thread.
     */
    public static Result importFrom(Path in) throws IOException {
        return importFrom(in, new Options());
    }

    /**
     * Like {@link #importFrom(Path)} but only restores the parts that are switched on: settings (which include
     * the music folder, liked songs, queue and EQ presets), theme, playlists, keybinds (the custom volume level)
     * and listening statistics.
     */
    public static Result importFrom(Path in, Options o) throws IOException {
        JsonObject root = parseRoot(in);

        // parse everything before touching any state
        MusicPlayerConfig newConfig = root.has("config") ? MusicPlayerConfig.parse(root.get("config")) : null;

        List<EqPresetManager.Preset> presets = null;
        if (root.has("eqPresets") && root.get("eqPresets").isJsonArray()) {
            presets = new ArrayList<>();
            for (JsonElement e : root.getAsJsonArray("eqPresets")) {
                EqPresetManager.Preset p = GSON.fromJson(e, EqPresetManager.Preset.class);
                if (p != null) presets.add(p);
            }
        }

        boolean hasStats = root.has("stats") && root.get("stats").isJsonObject();
        long statsTotal = 0;
        Map<String, Integer> counts = new HashMap<>();
        Map<String, Long> times = new HashMap<>();
        if (hasStats) {
            JsonObject so = root.getAsJsonObject("stats");
            if (so.has("totalListeningTimeSeconds")) statsTotal = so.get("totalListeningTimeSeconds").getAsLong();
            if (so.has("playCounts") && so.get("playCounts").isJsonObject()) {
                for (Map.Entry<String, JsonElement> e : so.getAsJsonObject("playCounts").entrySet()) {
                    counts.put(e.getKey(), e.getValue().getAsInt());
                }
            }
            if (so.has("listeningTimes") && so.get("listeningTimes").isJsonObject()) {
                for (Map.Entry<String, JsonElement> e : so.getAsJsonObject("listeningTimes").entrySet()) {
                    times.put(e.getKey(), e.getValue().getAsLong());
                }
            }
        }

        List<Playlist> playlists = new ArrayList<>();
        if (root.has("playlists") && root.get("playlists").isJsonArray()) {
            for (JsonElement e : root.getAsJsonArray("playlists")) {
                Playlist p = GSON.fromJson(e, Playlist.class);
                if (p != null && p.getName() != null && !p.getName().isBlank()) playlists.add(p);
            }
        }

        // apply
        Result result = new Result();
        MusicPlayerManager manager = MusicPlayerManager.getInstance();

        if (newConfig != null && (o.settings || o.theme || o.keys)) {
            MusicPlayerConfig cfg = MusicPlayerConfig.getInstance();
            if (o.settings) {
                String oldFolder = cfg.getMusicFolderPath();
                String keepTheme = cfg.getThemeId();
                float keepKey = cfg.getCustomVolumeKeybindLevel();
                cfg.restoreFrom(newConfig);
                if (!o.theme) cfg.setThemeId(keepTheme);
                if (!o.keys) cfg.setCustomVolumeKeybindLevel(keepKey);
                result.settings = true;
                manager.setVolume(cfg.getVolume());
                manager.getEngine().updateMasterVolume();

                String folder = cfg.getMusicFolderPath();
                result.musicFolder = folder == null ? "" : folder;
                if (!result.musicFolder.isEmpty()) {
                    File dir = new File(result.musicFolder);
                    if (dir.isDirectory()) {
                        if (!result.musicFolder.equals(oldFolder)) manager.setMusicFolder(result.musicFolder);
                    } else {
                        result.musicFolderMissing = true;
                    }
                }
            } else {
                if (o.theme) { cfg.setThemeId(newConfig.getThemeId()); result.theme = true; }
                if (o.keys) { cfg.setCustomVolumeKeybindLevel(newConfig.getCustomVolumeKeybindLevel()); result.keys = true; }
            }
        }

        if (presets != null && o.settings) {
            EqPresetManager.replaceUserPresets(presets);
            result.presets = EqPresetManager.userPresets().size();
        }

        if (hasStats && o.stats) {
            ListeningStatisticsManager.getInstance().restore(statsTotal, counts, times);
            result.stats = true;
        }

        if (!playlists.isEmpty() && o.playlists) {
            PlaylistManager pm = PlaylistManager.getInstance();
            for (Playlist p : playlists) pm.savePlaylist(p);
            pm.reload();
            result.playlists = playlists.size();
        }
        return result;
    }
}
