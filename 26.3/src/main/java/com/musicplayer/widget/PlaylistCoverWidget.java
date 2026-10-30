package com.musicplayer.widget;

import com.mojang.blaze3d.platform.NativeImage;
import com.musicplayer.AlbumArtManager;
import com.musicplayer.MusicPlayerManager;
import com.musicplayer.Playlist;
import com.musicplayer.SongInfo;
import com.musicplayer.VanillaTrack;
import com.musicplayer.player.SoundtrackRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

public class PlaylistCoverWidget extends AbstractWidget {

    private Playlist playlist;
    private final int coverSize;
    private static final Map<String, String> VANILLA_ALBUM_KEY_CACHE = new HashMap<>();
    private static final Map<Playlist, List<Identifier>> compositeCoverCache = new WeakHashMap<>();

    public PlaylistCoverWidget(int x, int y, int size, Playlist playlist) {
        super(x, y, size, size, Component.literal(""));
        this.playlist = playlist;
        this.coverSize = size;
    }

    public void setPlaylist(Playlist playlist) {
        this.playlist = playlist;
    }

    public static void renderCover(GuiGraphicsExtractor graphics, Playlist playlist, int x, int y, int size) {
        if (playlist == null) return;
        AlbumArtManager artMan = AlbumArtManager.getInstance();
        String coverPath = playlist.getCustomCoverPath();

        if (coverPath != null && !coverPath.isEmpty()) {
            Identifier cachedId = playlist.getCachedCoverId();
            if (cachedId == null) {
                cachedId = loadCustomCoverStatic(coverPath, playlist);
                playlist.setCachedCoverId(cachedId);
            }
            Identifier id = cachedId != null ? cachedId : AlbumArtManager.DEFAULT_ALBUM_ART;
            graphics.blit(id, x, y, x + size, y + size, 0.0f, 1.0f, 0.0f, 1.0f);
            return;
        }

        List<Identifier> cachedArtIds = compositeCoverCache.get(playlist);
        if (cachedArtIds != null) {
            int half = size / 2;
            for (int i = 0; i < 4; i++) {
                int cx = x + (i % 2) * half;
                int cy = y + (i / 2) * half;
                graphics.blit(cachedArtIds.get(i), cx, cy, cx + half, cy + half, 0.0f, 1.0f, 0.0f, 1.0f);
            }
            return;
        }

        List<Identifier> artIds = new ArrayList<>();
        for (SongInfo song : playlist.getSongs()) {
            if (artIds.size() >= 4) break;
            String path = song.getPath();
            if (path == null) continue;

            if (song.getSource() == SongInfo.SourceType.VANILLA) {
                String albumKey = findVanillaAlbumKeyStatic(path);
                if (albumKey != null) {
                    Identifier builtIn = AlbumArtManager.getBuiltInArt(albumKey);
                    if (builtIn != null) {
                        artIds.add(builtIn);
                        continue;
                    }
                }
            }

            Identifier artId = artMan.getArtForSong(path, null);
            if (artId != null && !artId.equals(AlbumArtManager.DEFAULT_ALBUM_ART)
                    && !artId.getPath().startsWith("album_art/song_")) {
                artIds.add(artId);
            } else if (path.endsWith(".mp3") || path.endsWith(".wav") || path.endsWith(".flac")) {
                Identifier loaded = tryLoadSongArtSync(path, artMan);
                if (loaded != null && !loaded.equals(AlbumArtManager.DEFAULT_ALBUM_ART)) {
                    artIds.add(loaded);
                } else {
                    artIds.add(AlbumArtManager.DEFAULT_ALBUM_ART);
                }
            } else {
                artIds.add(AlbumArtManager.DEFAULT_ALBUM_ART);
            }
        }

        while (artIds.size() < 4) {
            artIds.add(AlbumArtManager.DEFAULT_ALBUM_ART);
        }
        compositeCoverCache.put(playlist, artIds);

        int half = size / 2;
        for (int i = 0; i < 4; i++) {
            int cx = x + (i % 2) * half;
            int cy = y + (i / 2) * half;
            graphics.blit(artIds.get(i), cx, cy, cx + half, cy + half, 0.0f, 1.0f, 0.0f, 1.0f);
        }
    }

    public static void invalidateCover(Playlist playlist) {
        compositeCoverCache.remove(playlist);
    }

    private static Identifier tryLoadSongArtSync(String path, AlbumArtManager artMan) {
        try {
            org.jaudiotagger.audio.AudioFile af = org.jaudiotagger.audio.AudioFileIO.read(new File(path));
            if (af.getTag() != null) {
                var artwork = af.getTag().getFirstArtwork();
                if (artwork != null && artwork.getBinaryData() != null && artwork.getBinaryData().length > 0) {
                    byte[] data = artwork.getBinaryData();
                    if (isValidImage(data)) {
                        byte[] pngData = toPng(data);
                        if (pngData != null) {
                            NativeImage image = NativeImage.read(new ByteArrayInputStream(pngData));
                            if (image != null) {
                                Identifier id = Identifier.fromNamespaceAndPath("musicplayer",
                                        "cover_sync/" + Math.abs(path.hashCode()));
                                DynamicTexture tex = new DynamicTexture(() -> id.toString(), image);
                                Minecraft.getInstance().getTextureManager().register(id, tex);
                                tex.upload();
                                return id;
                            }
                        }
                    }
                }
            }
        } catch (Exception ignored) {}
        return null;
    }

    private static boolean isValidImage(byte[] data) {
        if (data == null || data.length < 4) return false;
        if (data[0] == (byte)0x89 && data[1] == 0x50 && data[2] == 0x4E && data[3] == 0x47) return true;
        if (data[0] == (byte)0xFF && data[1] == (byte)0xD8 && data[2] == (byte)0xFF) return true;
        return false;
    }

    private static byte[] toPng(byte[] data) {
        try {
            if (data.length >= 8 && data[0] == (byte)0x89 && data[1] == 0x50 &&
                data[2] == 0x4E && data[3] == 0x47) return data;
            java.awt.image.BufferedImage bi = javax.imageio.ImageIO.read(new ByteArrayInputStream(data));
            if (bi == null) return null;
            java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
            javax.imageio.ImageIO.write(bi, "PNG", baos);
            return baos.toByteArray();
        } catch (Exception e) { return null; }
    }

    private static Identifier loadCustomCoverStatic(String path, Playlist playlist) {
        File file = new File(path);
        if (!file.exists()) return null;

        Identifier id = Identifier.fromNamespaceAndPath("musicplayer", "playlist_cover/" + Math.abs(playlist.getName().hashCode()));
        try {
            byte[] data = Files.readAllBytes(file.toPath());
            if (data == null || data.length < 4) return null;
            if (!(data[0] == (byte)0x89 && data[1] == 0x50 && data[2] == 0x4E && data[3] == 0x47)) {
                return null;
            }
            NativeImage image = NativeImage.read(new ByteArrayInputStream(data));
            DynamicTexture tex = new DynamicTexture(() -> id.toString(), image);
            Minecraft.getInstance().getTextureManager().register(id, tex);
            tex.upload();
            return id;
        } catch (Exception e) {
            return null;
        }
    }

    private static String findVanillaAlbumKeyStatic(String trackId) {
        String cached = VANILLA_ALBUM_KEY_CACHE.get(trackId);
        if (cached != null) return cached;
        for (VanillaTrack vt : SoundtrackRegistry.getInstance().getAllTracks()) {
            if (vt.id().equals(trackId)) {
                VANILLA_ALBUM_KEY_CACHE.put(trackId, vt.coverKey());
                return vt.coverKey();
            }
        }
        VANILLA_ALBUM_KEY_CACHE.put(trackId, "");
        return null;
    }

    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        renderCover(graphics, playlist, getX(), getY(), coverSize);
    }

    private String findVanillaAlbumKey(String trackId) {
        String cached = VANILLA_ALBUM_KEY_CACHE.get(trackId);
        if (cached != null) return cached;
        for (VanillaTrack vt : SoundtrackRegistry.getInstance().getAllTracks()) {
            if (vt.id().equals(trackId)) {
                VANILLA_ALBUM_KEY_CACHE.put(trackId, vt.coverKey());
                return vt.coverKey();
            }
        }
        VANILLA_ALBUM_KEY_CACHE.put(trackId, "");
        return null;
    }

    private Identifier loadCustomCover(String path) {
        File file = new File(path);
        if (!file.exists()) return null;

        Identifier id = Identifier.fromNamespaceAndPath("musicplayer", "playlist_cover/" + Math.abs(playlist.getName().hashCode()));
        try {
            byte[] data = Files.readAllBytes(file.toPath());
            if (data == null || data.length < 4) return null;
            if (!(data[0] == (byte)0x89 && data[1] == 0x50 && data[2] == 0x4E && data[3] == 0x47)) {
                return null;
            }
            NativeImage image = NativeImage.read(new ByteArrayInputStream(data));
            DynamicTexture tex = new DynamicTexture(() -> id.toString(), image);
            Minecraft.getInstance().getTextureManager().register(id, tex);
            tex.upload();
            return id;
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean isFocused) {
        return false;
    }
}
