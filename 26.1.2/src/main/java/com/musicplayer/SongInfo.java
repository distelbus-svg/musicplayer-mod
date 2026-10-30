package com.musicplayer;

import java.io.File;

/**
 * Metadata for a song in a playlist.
 */
public class SongInfo {
    public enum SourceType {
        LOCAL,
        YOUTUBE,
        VANILLA
    }

    private final String title;
    private final String artist;
    private final String path; // File path or YouTube URL
    private final SourceType source;

    public SongInfo(String title, String artist, String path, SourceType source) {
        this.title = title;
        this.artist = artist;
        this.path = path;
        this.source = source;
    }

    public String getTitle() {
        return title;
    }

    public String getArtist() {
        return artist;
    }

    public String getPath() {
        return path;
    }

    public SourceType getSource() {
        return source;
    }

    public String getDisplayName() {
        if (artist != null && !artist.isEmpty() && !artist.equals("Unknown")) {
            return artist + " - " + title;
        }
        return title;
    }

    public static SongInfo fromFile(File file) {
        String name = file.getName();
        int dot = name.lastIndexOf('.');
        String title = dot > 0 ? name.substring(0, dot) : name;
        return new SongInfo(title, "Local File", file.getAbsolutePath(), SourceType.LOCAL);
    }
}
