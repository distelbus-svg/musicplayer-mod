package com.musicplayer;

import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;

/**
 * Data class representing a music playlist.
 */
public class Playlist {
    private String name;
    private final List<SongInfo> songs;
    private List<String> songPaths; // Legacy support for migration
    private int sortOrder;
    private long createdAt;
    private String customCoverPath = null;
    private transient Identifier cachedCoverId;

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
        return songs;
    }

    public void addSong(SongInfo song) {
        for (SongInfo s : songs) {
            if (s.getPath().equals(song.getPath())) return;
        }
        songs.add(song);
    }

    public void removeSong(SongInfo song) {
        songs.remove(song);
    }

    public void moveSongUp(SongInfo song) {
        int idx = songs.indexOf(song);
        if (idx > 0) {
            songs.remove(idx);
            songs.add(idx - 1, song);
        }
    }

    public void moveSongDown(SongInfo song) {
        int idx = songs.indexOf(song);
        if (idx >= 0 && idx < songs.size() - 1) {
            songs.remove(idx);
            songs.add(idx + 1, song);
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
