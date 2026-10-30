package com.musicplayer;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Handles persistence and management of playlists.
 * Playlists are stored in .minecraft/config/musicplayer/playlists/
 */
public class PlaylistManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path PLAYLISTS_DIR = FabricLoader.getInstance().getConfigDir().resolve("musicplayer").resolve("playlists");

    private final List<Playlist> playlists = new ArrayList<>();
    private static PlaylistManager instance;

    private PlaylistManager() {
        loadAll();
    }

    public static PlaylistManager getInstance() {
        if (instance == null) {
            instance = new PlaylistManager();
        }
        return instance;
    }

    /** Keeps letters/digits of any script so names like "Ünï" and "Åbc" no longer collapse to "___". */
    private static String fileNameFor(String name) {
        String base = name == null ? "" : name.replaceAll("[^\\p{L}\\p{N}.\\- ]", "_").trim();
        if (base.isEmpty() || base.equals(".") || base.equals("..")) base = "playlist";
        return base + ".json";
    }

    private void loadAll() {
        playlists.clear();
        if (!Files.exists(PLAYLISTS_DIR)) {
            try {
                Files.createDirectories(PLAYLISTS_DIR);
            } catch (IOException e) {
                e.printStackTrace();
                return;
            }
        }

        try {
            List<Path> files;
            try (java.util.stream.Stream<Path> stream = Files.list(PLAYLISTS_DIR)) {
                files = stream.filter(p -> p.toString().endsWith(".json")).collect(Collectors.toList());
            }

            for (Path file : files) {
                try (Reader reader = Files.newBufferedReader(file)) {
                    Playlist playlist = GSON.fromJson(reader, Playlist.class);
                    if (playlist != null) {
                        // Migration from old format
                        if (playlist.getSongs().isEmpty() && playlist.getLegacyPaths() != null) {
                            for (String path : playlist.getLegacyPaths()) {
                                File f = new File(path);
                                if (f.exists()) {
                                    playlist.addSong(SongInfo.fromFile(f));
                                }
                            }
                            savePlaylist(playlist); // Save converted version
                        }
                        if (!playlists.contains(playlist)) playlists.add(playlist);
                        Path expected = PLAYLISTS_DIR.resolve(fileNameFor(playlist.getName()));
                        if (!expected.equals(file) && !Files.exists(expected)) {
                            try { Files.move(file, expected); } catch (IOException ignored) {}
                        }
                    }
                } catch (Exception e) {
                    System.err.println("Failed to load playlist: " + file);
                    e.printStackTrace();
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        playlists.sort(java.util.Comparator.comparingInt(Playlist::getSortOrder)
            .thenComparingLong(Playlist::getCreatedAt));
    }

    public void savePlaylist(Playlist playlist) {
        String fileName = fileNameFor(playlist.getName());
        Path path = PLAYLISTS_DIR.resolve(fileName);

        Path tmp = path.resolveSibling(fileName + ".tmp");
        try {
            try (Writer writer = Files.newBufferedWriter(tmp)) {
                GSON.toJson(playlist, writer);
            }
            try {
                Files.move(tmp, path, java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                    java.nio.file.StandardCopyOption.ATOMIC_MOVE);
            } catch (java.nio.file.AtomicMoveNotSupportedException e) {
                Files.move(tmp, path, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
            if (!playlists.contains(playlist)) {
                playlists.add(playlist);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void deletePlaylist(Playlist playlist) {
        String fileName = fileNameFor(playlist.getName());
        Path path = PLAYLISTS_DIR.resolve(fileName);
        try {
            Files.deleteIfExists(path);
            playlists.remove(playlist);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void renamePlaylist(Playlist playlist, String newName) {
        if (newName == null || newName.trim().isEmpty()) return;
        String trimmed = newName.trim();

        String oldFile = fileNameFor(playlist.getName());
        String newFile = fileNameFor(trimmed);
        Path oldPath = PLAYLISTS_DIR.resolve(oldFile);
        Path newPath = PLAYLISTS_DIR.resolve(newFile);

        if (!oldFile.equals(newFile) && Files.exists(newPath)) return;

        playlist.setName(trimmed);

        try {
            if (Files.exists(oldPath) && !oldFile.equals(newFile)) {
                Files.delete(oldPath);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        savePlaylist(playlist);
    }

    public void movePlaylistUp(Playlist playlist) {
        int idx = playlists.indexOf(playlist);
        if (idx > 0) {
            playlists.remove(idx);
            playlists.add(idx - 1, playlist);
            updateSortOrders();
        }
    }

    public void movePlaylistDown(Playlist playlist) {
        int idx = playlists.indexOf(playlist);
        if (idx >= 0 && idx < playlists.size() - 1) {
            playlists.remove(idx);
            playlists.add(idx + 1, playlist);
            updateSortOrders();
        }
    }

    public void sortPlaylistsByName() {
        playlists.sort((a, b) -> a.getName().compareToIgnoreCase(b.getName()));
        updateSortOrders();
    }

    public void sortPlaylistsByDate() {
        playlists.sort((a, b) -> Long.compare(a.getCreatedAt(), b.getCreatedAt()));
        updateSortOrders();
    }

    private void updateSortOrders() {
        for (int i = 0; i < playlists.size(); i++) {
            Playlist pl = playlists.get(i);
            if (pl.getSortOrder() != i) {
                pl.setSortOrder(i);
                savePlaylist(pl);
            }
        }
    }

    public List<Playlist> getPlaylists() {
        return playlists;
    }

    public void reload() {
        loadAll();
    }

    public static void exportToJson(Playlist playlist, Path outputPath) throws IOException {
        try (Writer writer = Files.newBufferedWriter(outputPath)) {
            GSON.toJson(playlist, writer);
        }
    }

    public static Playlist importFromJson(Path jsonPath) throws IOException {
        try (Reader reader = Files.newBufferedReader(jsonPath)) {
            Playlist playlist = GSON.fromJson(reader, Playlist.class);
            if (playlist != null && playlist.getSongs().isEmpty() && playlist.getLegacyPaths() != null) {
                for (String path : playlist.getLegacyPaths()) {
                    File f = new File(path);
                    if (f.exists()) {
                        playlist.addSong(SongInfo.fromFile(f));
                    }
                }
            }
            return playlist;
        }
    }

}
