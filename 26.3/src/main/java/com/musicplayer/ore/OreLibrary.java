package com.musicplayer.ore;

import com.musicplayer.MusicPlayerManager;
import com.musicplayer.SongInfo;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Everything you can search and add to playlists: the music folder plus the extra song folders from
 * "Add songs from folders". Extra folders are scanned once in the background and cached.
 */
public final class OreLibrary {

    private static final Map<String, List<SongInfo>> FOLDERS = new ConcurrentHashMap<>();
    private static final Set<String> SCANNING = ConcurrentHashMap.newKeySet();
    /** Bumped whenever a background scan finishes, so screens know to search again. */
    private static volatile int revision;

    private OreLibrary() { }

    public static int revision() { return revision; }

    /** Forgets a folder's songs (after it is removed or should be rescanned). */
    public static void forget(String path) {
        FOLDERS.remove(path);
        revision++;
    }

    public static boolean isAudio(String name) {
        String n = name.toLowerCase(Locale.ROOT);
        return n.endsWith(".mp3") || n.endsWith(".wav") || n.endsWith(".flac");
    }

    private static void scan(File dir, List<SongInfo> out, int depth) {
        File[] files = dir.listFiles();
        if (files == null || depth > 8) return;
        for (File f : files) {
            if (f.isDirectory()) scan(f, out, depth + 1);
            else if (isAudio(f.getName())) {
                try { out.add(SongInfo.fromFile(f)); } catch (Throwable ignored) { }
            }
        }
    }

    /** Songs of one extra folder; empty until its background scan has finished. */
    public static List<SongInfo> folderSongs(String path) {
        List<SongInfo> cached = FOLDERS.get(path);
        if (cached != null) return cached;
        if (SCANNING.add(path)) {
            Thread t = new Thread(() -> {
                List<SongInfo> out = new ArrayList<>();
                try {
                    File dir = new File(path);
                    if (dir.isDirectory()) scan(dir, out, 0);
                } catch (Throwable ignored) {
                } finally {
                    FOLDERS.put(path, out);
                    SCANNING.remove(path);
                    revision++;
                }
            }, "melodix-folder-scan");
            t.setDaemon(true);
            t.start();
        }
        return List.of();
    }

    /** Music folder, every extra folder and every playlist song (no Minecraft tracks), without duplicates. */
    public static List<SongInfo> searchable(MusicPlayerManager manager) {
        List<SongInfo> out = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (SongInfo s : manager.getCachedEditAllSongs()) {
            if (s.getSource() != SongInfo.SourceType.VANILLA && seen.add(s.getPath())) out.add(s);
        }
        for (String f : manager.getConfig().getFolderPaths()) {
            for (SongInfo s : folderSongs(f)) {
                if (seen.add(s.getPath())) out.add(s);
            }
        }
        // every song already in a playlist counts too, wherever its file lives
        for (com.musicplayer.Playlist p : com.musicplayer.PlaylistManager.getInstance().getPlaylists()) {
            for (SongInfo s : p.getSongs()) {
                if (s.getSource() != SongInfo.SourceType.VANILLA && seen.add(s.getPath())) out.add(s);
            }
        }
        return out;
    }
}
