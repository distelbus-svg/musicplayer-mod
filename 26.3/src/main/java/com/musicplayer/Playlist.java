package com.musicplayer;

import net.minecraft.resources.Identifier;
import java.util.ArrayList;
import java.util.List;

/**
 * Data class representing a music playlist.
 */
public class Playlist {
    private String name;
    private List<SongInfo> songs;
    private List<String> songPaths; // Legacy support for migration
    private String customCoverPath = null;
    private transient Identifier cachedCoverId;
    private int sortOrder;
    private long createdAt;

    public Playlist(String name) {
        this.name = name;
        this.songs = new ArrayList<>();
        this.sortOrder = 0;
        this.createdAt = System.currentTimeMillis();
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public List<SongInfo> getSongs() {
        // Gson bypasses the constructor, so legacy files without a songs key leave this null
        if (songs == null) songs = new ArrayList<>();
        return songs;
    }

    public void addSong(SongInfo song) {
        for (SongInfo s : getSongs()) {
            if (s.getPath().equals(song.getPath())) return;
        }
        getSongs().add(song);
    }

    public void removeSong(SongInfo song) {
        getSongs().remove(song);
    }

    public void moveSongUp(SongInfo song) {
        int idx = getSongs().indexOf(song);
        if (idx > 0) {
            getSongs().remove(idx);
            getSongs().add(idx - 1, song);
        }
    }

    public void moveSongDown(SongInfo song) {
        int idx = getSongs().indexOf(song);
        if (idx >= 0 && idx < songs.size() - 1) {
            getSongs().remove(idx);
            getSongs().add(idx + 1, song);
        }
    }

    public int getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(int sortOrder) {
        this.sortOrder = sortOrder;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(long createdAt) {
        this.createdAt = createdAt;
    }

    public List<String> getLegacyPaths() {
        return songPaths;
    }

    public String getCustomCoverPath() {
        return customCoverPath;
    }

    public void setCustomCoverPath(String path) {
        this.customCoverPath = path;
        this.cachedCoverId = null;
    }

    public Identifier getCachedCoverId() {
        return cachedCoverId;
    }

    public void setCachedCoverId(Identifier id) {
        this.cachedCoverId = id;
    }
}
