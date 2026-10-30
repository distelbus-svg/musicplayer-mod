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
            List<Path> files = Files.list(PLAYLISTS_DIR)
                    .filter(p -> p.toString().endsWith(".json"))
                    .collect(Collectors.toList());

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
                        playlists.add(playlist);
                    }
                } catch (Exception e) {
                    System.err.println("Failed to load playlist: " + file);
                    e.printStackTrace();
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void savePlaylist(Playlist playlist) {
        String fileName = playlist.getName().replaceAll("[^a-zA-Z0-9.-]", "_") + ".json";
        Path path = PLAYLISTS_DIR.resolve(fileName);

        try (Writer writer = Files.newBufferedWriter(path)) {
            GSON.toJson(playlist, writer);
            if (!playlists.contains(playlist)) {
                playlists.add(playlist);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void deletePlaylist(Playlist playlist) {
        String fileName = playlist.getName().replaceAll("[^a-zA-Z0-9.-]", "_") + ".json";
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

        String oldFile = playlist.getName().replaceAll("[^a-zA-Z0-9.-]", "_") + ".json";
        String newFile = trimmed.replaceAll("[^a-zA-Z0-9.-]", "_") + ".json";
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
            playlists.get(i).setSortOrder(i);
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
