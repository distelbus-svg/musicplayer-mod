package com.musicplayer;

import com.musicplayer.port.*;

import java.io.File;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Metadata for a song in a playlist.
 */
public class SongInfo {
    public enum SourceType {
        LOCAL,
        YOUTUBE,
        VANILLA
    }

    /** Older versions stored this instead of a real artist; it is replaced by the file's tag when shown. */
    private static final String PLACEHOLDER_ARTIST = "Local File";

    private static final Map<String, String> ARTIST_CACHE = new ConcurrentHashMap<>();
    private static final Set<String> ARTIST_PENDING = ConcurrentHashMap.newKeySet();
    private static final ExecutorService TAG_READER = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "musicplayer-tag-reader");
        t.setDaemon(true);
        return t;
    });

    static {
        java.util.logging.Logger.getLogger("org.jaudiotagger").setLevel(java.util.logging.Level.OFF);
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

    /**
     * The artist. For local files without a stored artist this is read from the file's tags in the background
     * (empty until it is known), so scanning a big library stays fast.
     */
    public String getArtist() {
        if (source == SourceType.LOCAL && path != null && (artist == null || PLACEHOLDER_ARTIST.equals(artist))) {
            String cached = ARTIST_CACHE.get(path);
            if (cached != null) return cached;
            if (ARTIST_PENDING.add(path)) {
                final String p = path;
                TAG_READER.execute(() -> {
                    try {
                        ARTIST_CACHE.put(p, readArtist(p));
                    } finally {
                        ARTIST_PENDING.remove(p);
                    }
                });
            }
            return "";
        }
        return artist;
    }

    public String getPath() {
        return path;
    }

    public SourceType getSource() {
        return source;
    }

    public String getDisplayName() {
        String a = getArtist();
        if (a != null && !a.isEmpty() && !a.equals("Unknown")) {
            return a + " - " + title;
        }
        return title;
    }

    public static SongInfo fromFile(File file) {
        String name = file.getName();
        int dot = name.lastIndexOf('.');
        String title = dot > 0 ? name.substring(0, dot) : name;
        return new SongInfo(title, PLACEHOLDER_ARTIST, file.getAbsolutePath(), SourceType.LOCAL);
    }

    private static String readArtist(String path) {
        try {
            org.jaudiotagger.audio.AudioFile af = org.jaudiotagger.audio.AudioFileIO.read(new File(path));
            org.jaudiotagger.tag.Tag tag = af.getTag();
            if (tag != null) {
                String a = tag.getFirst(org.jaudiotagger.tag.FieldKey.ARTIST);
                if (a == null || a.isBlank()) a = tag.getFirst(org.jaudiotagger.tag.FieldKey.ALBUM_ARTIST);
                if (a != null && !a.isBlank()) return a.trim();
            }
        } catch (Throwable ignored) {
            // unreadable or untagged file
        }
        return "Unknown artist";
    }
}
