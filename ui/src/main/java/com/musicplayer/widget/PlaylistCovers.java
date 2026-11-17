package com.musicplayer.widget;

import com.musicplayer.Playlist;
import com.musicplayer.SongInfo;
import com.musicplayer.VanillaTrack;
import com.musicplayer.port.Ports;
import com.musicplayer.ui.Gfx;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/** Draws a playlist cover: the custom image, or a 2x2 mosaic of the first four song covers. */
public final class PlaylistCovers {
    private static final Map<String, String> VANILLA_ALBUM_KEY_CACHE = new HashMap<>();
    private static final Map<Playlist, List<String>> COMPOSITE_CACHE = new WeakHashMap<>();

    private PlaylistCovers() {}

    public static void render(Gfx g, Playlist playlist, int x, int y, int size) {
        if (playlist == null) return;
        String coverPath = playlist.getCustomCoverPath();

        if (coverPath != null && !coverPath.isEmpty()) {
            String cachedId = playlist.getCachedCoverId();
            if (cachedId == null) {
                cachedId = loadCustomCover(coverPath, playlist);
                playlist.setCachedCoverId(cachedId);
            }
            String id = cachedId != null ? cachedId : Ports.art().defaultArt();
            g.blit(id, x, y, x + size, y + size, 0f, 1f, 0f, 1f);
            return;
        }

        List<String> ids = COMPOSITE_CACHE.get(playlist);
        if (ids == null) {
            ids = buildMosaic(playlist);
            COMPOSITE_CACHE.put(playlist, ids);
        }
        int half = size / 2;
        for (int i = 0; i < 4; i++) {
            int cx = x + (i % 2) * half;
            int cy = y + (i / 2) * half;
            g.blit(ids.get(i), cx, cy, cx + half, cy + half, 0f, 1f, 0f, 1f);
        }
    }

    public static void invalidate(Playlist playlist) {
        COMPOSITE_CACHE.remove(playlist);
    }

    private static List<String> buildMosaic(Playlist playlist) {
        String fallback = Ports.art().defaultArt();
        List<String> ids = new ArrayList<>();
        for (SongInfo song : playlist.getSongs()) {
            if (ids.size() >= 4) break;
            String path = song.getPath();
            if (path == null) continue;

            if (song.getSource() == SongInfo.SourceType.VANILLA) {
                String albumKey = findVanillaAlbumKey(path);
                if (albumKey != null) {
                    String builtIn = Ports.art().builtInArt(albumKey);
                    if (builtIn != null) {
                        ids.add(builtIn);
                        continue;
                    }
                }
            }

            String artId = Ports.art().artForSong(path, null);
            if (artId != null && !artId.equals(fallback) && !artId.contains("album_art/song_")) {
                ids.add(artId);
            } else if (path.endsWith(".mp3") || path.endsWith(".wav") || path.endsWith(".flac")) {
                String loaded = loadEmbeddedArt(path);
                ids.add(loaded != null && !loaded.equals(fallback) ? loaded : fallback);
            } else {
                ids.add(fallback);
            }
        }
        while (ids.size() < 4) ids.add(fallback);
        return ids;
    }

    private static String loadEmbeddedArt(String path) {
        try {
            org.jaudiotagger.audio.AudioFile af = org.jaudiotagger.audio.AudioFileIO.read(new File(path));
            if (af.getTag() != null) {
                var artwork = af.getTag().getFirstArtwork();
                if (artwork != null && artwork.getBinaryData() != null && artwork.getBinaryData().length > 0) {
                    byte[] data = artwork.getBinaryData();
                    if (isValidImage(data)) {
                        byte[] png = toPng(data);
                        if (png != null) return Ports.art().registerTexture("cover_sync/" + Math.abs(path.hashCode()), png);
                    }
                }
            }
        } catch (Exception ignored) {}
        return null;
    }

    private static boolean isValidImage(byte[] data) {
        if (data == null || data.length < 4) return false;
        if (data[0] == (byte) 0x89 && data[1] == 0x50 && data[2] == 0x4E && data[3] == 0x47) return true;
        return data[0] == (byte) 0xFF && data[1] == (byte) 0xD8 && data[2] == (byte) 0xFF;
    }

    private static byte[] toPng(byte[] data) {
        try {
            if (data.length >= 8 && data[0] == (byte) 0x89 && data[1] == 0x50 && data[2] == 0x4E && data[3] == 0x47) return data;
            java.awt.image.BufferedImage bi = javax.imageio.ImageIO.read(new ByteArrayInputStream(data));
            if (bi == null) return null;
            java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
            javax.imageio.ImageIO.write(bi, "PNG", baos);
            return baos.toByteArray();
        } catch (Exception e) {
            return null;
        }
    }

    private static String loadCustomCover(String path, Playlist playlist) {
        File file = new File(path);
        if (!file.exists()) return null;
        try {
            byte[] data = Files.readAllBytes(file.toPath());
            if (data == null || data.length < 4) return null;
            if (!(data[0] == (byte) 0x89 && data[1] == 0x50 && data[2] == 0x4E && data[3] == 0x47)) return null;
            return Ports.art().registerTexture("playlist_cover/" + Math.abs(playlist.getName().hashCode()), data);
        } catch (Exception e) {
            return null;
        }
    }

    private static String findVanillaAlbumKey(String trackId) {
        String cached = VANILLA_ALBUM_KEY_CACHE.get(trackId);
        if (cached != null) return cached.isEmpty() ? null : cached;
        for (VanillaTrack vt : Ports.registry().getAllTracks()) {
            if (vt.id().equals(trackId)) {
                VANILLA_ALBUM_KEY_CACHE.put(trackId, vt.coverKey());
                return vt.coverKey();
            }
        }
        VANILLA_ALBUM_KEY_CACHE.put(trackId, "");
        return null;
    }
}
