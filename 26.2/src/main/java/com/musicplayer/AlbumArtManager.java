package com.musicplayer;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

import org.jaudiotagger.audio.AudioFileIO;
import org.jaudiotagger.tag.Tag;
import org.jaudiotagger.tag.images.Artwork;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AlbumArtManager {

    private static final Logger LOGGER = LoggerFactory.getLogger("musicplayer");

    private static final AlbumArtManager INSTANCE = new AlbumArtManager();

    public static final Identifier ALBUM_ART_ID =
            Identifier.fromNamespaceAndPath("musicplayer", "album_art/current");

    public static final Identifier DEFAULT_ALBUM_ART =
            Identifier.fromNamespaceAndPath("musicplayer", "textures/music/albums/cover_fallback");

    // Formats that reliably support embedded cover art
    private static final Set<String> SUPPORTED_EXTENSIONS = Set.of(
        "mp3", "flac", "ogg", "m4a", "aac"
    );

    private static final Map<String, Identifier> BUILT_IN_ART = new HashMap<>();
    static {
        String ns = "musicplayer";
        BUILT_IN_ART.put("minecraft",                   Identifier.fromNamespaceAndPath(ns, "textures/music/albums/minecraft"));
        BUILT_IN_ART.put("minecraft_volume_alpha",       Identifier.fromNamespaceAndPath(ns, "textures/music/albums/minecraft_volume_alpha"));
        BUILT_IN_ART.put("minecraft_volume_beta",        Identifier.fromNamespaceAndPath(ns, "textures/music/albums/minecraft_volume_beta"));
        BUILT_IN_ART.put("minecraft_nether_update",      Identifier.fromNamespaceAndPath(ns, "textures/music/albums/nether"));
        BUILT_IN_ART.put("minecraft_the_end",            Identifier.fromNamespaceAndPath(ns, "textures/music/albums/the_end"));
        BUILT_IN_ART.put("minecraft_caves_and_cliffs",   Identifier.fromNamespaceAndPath(ns, "textures/music/albums/caves_cliffs"));
        BUILT_IN_ART.put("minecraft_the_wild_update",    Identifier.fromNamespaceAndPath(ns, "textures/music/albums/wild_update"));
        BUILT_IN_ART.put("minecraft_trails_and_tales",   Identifier.fromNamespaceAndPath(ns, "textures/music/albums/cover_fallback"));
        BUILT_IN_ART.put("minecraft_tricky_trials",      Identifier.fromNamespaceAndPath(ns, "textures/music/albums/cover_fallback"));
        BUILT_IN_ART.put("minecraft_the_garden_awakens", Identifier.fromNamespaceAndPath(ns, "textures/music/albums/cover_fallback"));
        BUILT_IN_ART.put("minecraft_music_discs",        Identifier.fromNamespaceAndPath(ns, "textures/music/albums/music_discs"));
    }

    public static Identifier getBuiltInArt(String albumKey) {
        if (albumKey == null) return null;
        return BUILT_IN_ART.get(albumKey);
    }

    private final Map<String, Identifier> songArtCache = new HashMap<>();
    private final Set<String> pendingProcessing = new HashSet<>();
    private int songArtCounter;

    /**
     * Returns a cached Identifier for a song's album art, or triggers lazy loading.
     * For vanilla tracks (non-file paths), delegates to getBuiltInArt.
     * Never returns null: falls back to DEFAULT_ALBUM_ART.
     */
    public Identifier getArtForSong(String path, String albumKey) {
        if (albumKey != null) {
            Identifier builtIn = getBuiltInArt(albumKey);
            if (builtIn != null) return builtIn;
        }
        if (path == null || path.isEmpty()) return DEFAULT_ALBUM_ART;

        Identifier cached = songArtCache.get(path);
        if (cached != null) return cached;

        if (!pendingProcessing.add(path)) {
            Identifier placeholderId = Identifier.fromNamespaceAndPath("musicplayer", "album_art/pending_" + (songArtCounter));
            songArtCache.put(path, placeholderId);
            return placeholderId;
        }

        Identifier newId = Identifier.fromNamespaceAndPath("musicplayer", "album_art/song_" + (songArtCounter++));
        songArtCache.put(path, newId);

        try {
            Minecraft mc = Minecraft.getInstance();
            NativeImage placeholder = new NativeImage(1, 1, false);
            DynamicTexture placeholderTex = new DynamicTexture(() -> newId.toString(), placeholder);
            mc.getTextureManager().register(newId, placeholderTex);
            placeholderTex.upload();
        } catch (Exception ignored) {}

        File songFile = new File(path);
        if (songFile.exists()) {
            executor.submit(() -> {
                try {
                    pendingProcessing.remove(path);
                    org.jaudiotagger.audio.AudioFile af = AudioFileIO.read(songFile);
                    if (af.getTag() != null) {
                        var artwork = af.getTag().getFirstArtwork();
                        if (artwork != null && artwork.getBinaryData() != null && artwork.getBinaryData().length > 0) {
                            uploadSongArt(newId, artwork.getBinaryData(), path);
                            return;
                        }
                    }
                    byte[] rawImage = extractAPIC(songFile);
                    if (rawImage != null) {
                        uploadSongArt(newId, rawImage, path);
                        return;
                    }
                } catch (Exception e) {
                    LOGGER.error("Failed to load art for song: {}", path, e);
                }
            });
        }

        return newId;
    }

    private DynamicTexture currentTexture;
    private boolean hasArt = false;
    private int artWidth = 0;
    private int artHeight = 0;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private AlbumArtManager() {}

    public static AlbumArtManager getInstance() {
        return INSTANCE;
    }

    public void init() {
        Minecraft mc = Minecraft.getInstance();
        Set<Identifier> loaded = new HashSet<>();
        // Load default fallback + all built-in art textures (deduplicated)
        for (Identifier id : BUILT_IN_ART.values()) {
            registerTexture(id, mc, loaded);
        }
        registerTexture(DEFAULT_ALBUM_ART, mc, loaded);
    }

    private void registerTexture(Identifier id, Minecraft mc, Set<Identifier> loaded) {
        if (!loaded.add(id)) return;
        try {
            Identifier pngId = Identifier.fromNamespaceAndPath(id.getNamespace(), id.getPath() + ".png");
            try (var resource = mc.getResourceManager().open(pngId)) {
                byte[] bytes = readAllBytes(resource);
                if (bytes.length > 0) {
                    NativeImage image = NativeImage.read(new ByteArrayInputStream(bytes));
                    var tex = new DynamicTexture(() -> id.toString(), image);
                    mc.getTextureManager().register(id, tex);
                    tex.upload();
                }
            }
        } catch (Exception ignored) {
        }
    }

    public boolean hasArt() {
        return hasArt;
    }

    public int getArtWidth() { return artWidth; }
    public int getArtHeight() { return artHeight; }

    public void loadForSong(File songFile) {
        if (songFile == null) {
            clear();
            return;
        }
        String name = songFile.getName().toLowerCase();
        int dot = name.lastIndexOf('.');
        if (dot < 0 || !SUPPORTED_EXTENSIONS.contains(name.substring(dot + 1))) {
            LOGGER.debug("Skipping album art for unsupported format: {}", name);
            clear();
            return;
        }

        LOGGER.debug("Loading album art for: {} (format: {})", name, name.substring(dot + 1));

        executor.submit(() -> {
            try {
                // Primary: JAudioTagger artwork API
                org.jaudiotagger.audio.AudioFile af = AudioFileIO.read(songFile);
                Tag tag = af.getTag();
                String id3v = (af.getAudioHeader() != null) ? af.getAudioHeader().getEncodingType() : "unknown";
                if (tag != null) {
                    LOGGER.debug("Tag detected for: {} (encoding: {})", name, id3v);
                    Artwork artwork = tag.getFirstArtwork();
                    if (artwork != null) {
                        String mime = artwork.getMimeType();
                        byte[] data = artwork.getBinaryData();
                        int dlen = (data != null) ? data.length : 0;
                        LOGGER.debug("JAudioTagger artwork: {} ({} bytes, mime={})", name, dlen, (mime != null ? mime : "null"));
                        if (data != null && dlen > 0) {
                            uploadImageBytes(data, name);
                            return;
                        }
                    }
                    LOGGER.debug("No artwork via JAudioTagger for: {}", name);
                } else {
                    LOGGER.debug("No tag detected for: {} (encoding: {})", name, id3v);
                }

                // Fallback: manual ID3v2 APIC extraction
                byte[] rawImage = extractAPIC(songFile);
                if (rawImage != null) {
                    String fmt = (rawImage.length >= 4)
                        ? String.format("0x%02X%02X%02X%02X", rawImage[0], rawImage[1], rawImage[2], rawImage[3])
                        : "short";
                    LOGGER.debug("Fallback APIC found ({} bytes, magic={}) for: {}", rawImage.length, fmt, name);
                    uploadImageBytes(rawImage, name);
                    return;
                }

                LOGGER.debug("No album art found for: {}", name);
                clearOnMain();
            } catch (Exception e) {
                LOGGER.error("Failed to load album art for: {}", name, e);
                clearOnMain();
            }
        });
    }

    public void loadManualArt(File pngFile) {
        if (pngFile == null) {
            clear();
            return;
        }
        String name = pngFile.getName();
        LOGGER.debug("Loading manual album art from: {}", name);
        executor.submit(() -> {
            try {
                byte[] fileData = readAllBytes(new FileInputStream(pngFile));
                uploadImageBytes(fileData, name);
            } catch (Exception e) {
                LOGGER.error("Failed to load manual album art from: {}", name, e);
                clearOnMain();
            }
        });
    }

    private void uploadSongArt(Identifier targetId, byte[] imageData, String sourceName) {
        if (!isValidImageFormat(imageData)) return;
        byte[] pngData;
        try {
            pngData = ensurePng(imageData);
        } catch (Exception e) {
            return;
        }
        NativeImage image;
        try {
            image = NativeImage.read(new ByteArrayInputStream(pngData));
        } catch (Exception e) {
            return;
        }
        NativeImage finalImage = image;
        Minecraft.getInstance().execute(() -> {
            try {
                DynamicTexture tex = new DynamicTexture(() -> targetId.toString(), finalImage);
                Minecraft.getInstance().getTextureManager().register(targetId, tex);
                tex.upload();
            } catch (Exception e) {
                finalImage.close();
            }
        });
    }

    // ─── Image format validation ───

    private static boolean isValidImageFormat(byte[] data) {
        if (data == null || data.length < 4) return false;
        // PNG: 89 50 4E 47
        if (data[0] == (byte) 0x89 && data[1] == 0x50 && data[2] == 0x4E && data[3] == 0x47) return true;
        // JPEG: FF D8 FF
        if (data[0] == (byte) 0xFF && data[1] == (byte) 0xD8 && data[2] == (byte) 0xFF) return true;
        // WebP: RIFF .... WEBP
        if (data.length >= 12 &&
            data[0] == 0x52 && data[1] == 0x49 && data[2] == 0x46 && data[3] == 0x46 &&
            data[8] == 0x57 && data[9] == 0x45 && data[10] == 0x42 && data[11] == 0x50) return true;
        // BMP: 42 4D
        if (data[0] == 0x42 && data[1] == 0x4D) return true;
        // GIF: 47 49 46
        if (data[0] == 0x47 && data[1] == 0x49 && data[2] == 0x46) return true;
        return false;
    }

    // ─── Texture upload (decode off-main, register/upload on main thread) ───

    private static byte[] ensurePng(byte[] data) throws IOException {
        if (data.length >= 8 &&
            data[0] == (byte)0x89 && data[1] == 0x50 &&
            data[2] == 0x4E && data[3] == 0x47 &&
            data[4] == 0x0D && data[5] == 0x0A &&
            data[6] == 0x1A && data[7] == 0x0A) {
            return data;
        }
        BufferedImage bi = ImageIO.read(new ByteArrayInputStream(data));
        if (bi == null) throw new IOException("ImageIO could not decode image");
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(bi, "PNG", baos);
        return baos.toByteArray();
    }

    private void uploadImageBytes(byte[] imageData, String sourceName) {
        if (!isValidImageFormat(imageData)) {
            LOGGER.warn("Album art has invalid image format for: {}", sourceName);
            clearOnMain();
            return;
        }

        LOGGER.debug("Converting/decoding album art ({} bytes) for: {}", imageData.length, sourceName);

        byte[] pngData;
        try {
            pngData = ensurePng(imageData);
        } catch (Exception e) {
            LOGGER.error("Failed to convert album art to PNG for: {}", sourceName, e);
            clearOnMain();
            return;
        }

        NativeImage image;
        try {
            image = NativeImage.read(new ByteArrayInputStream(pngData));
        } catch (Exception e) {
            LOGGER.error("Failed to decode album art image for: {}", sourceName, e);
            clearOnMain();
            return;
        }

        artWidth = image.getWidth();
        artHeight = image.getHeight();
        LOGGER.debug("Decoded {}x{} album art for: {}", artWidth, artHeight, sourceName);

        NativeImage finalImage = image;
        Minecraft.getInstance().execute(() -> {
            try {
                if (currentTexture != null) {
                    currentTexture.close();
                }
                currentTexture = new DynamicTexture(() -> ALBUM_ART_ID.toString(), finalImage);
                Minecraft.getInstance().getTextureManager().register(ALBUM_ART_ID, currentTexture);
                currentTexture.upload();
                hasArt = true;
                LOGGER.debug("Album art uploaded: {}x{}, registered as {}", artWidth, artHeight, ALBUM_ART_ID);
            } catch (Exception e) {
                LOGGER.error("Failed to upload album art texture for: {}", sourceName, e);
                finalImage.close();
                hasArt = false;
            }
        });
    }

    public void clear() {
        if (currentTexture != null) {
            currentTexture.close();
            currentTexture = null;
        }
        hasArt = false;
        artWidth = 0;
        artHeight = 0;
    }

    private void clearOnMain() {
        Minecraft.getInstance().execute(this::clear);
    }

    // ─── Fallback: manual ID3v2 APIC extraction ───

    private byte[] extractAPIC(File file) throws IOException {
        try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
            byte[] header = new byte[10];
            if (raf.read(header) != 10) return null;
            if (header[0] != 'I' || header[1] != 'D' || header[2] != '3') return null;

            int version = header[3]; // major version (3 = v2.3, 4 = v2.4)
            LOGGER.debug("ID3v2.{} tag found for APIC fallback", version);

            int tagSize = ((header[6] & 0x7F) << 21)
                        | ((header[7] & 0x7F) << 14)
                        | ((header[8] & 0x7F) << 7)
                        | (header[9] & 0x7F);
            if (tagSize <= 0) return null;

            byte[] tagData = new byte[tagSize];
            if (raf.read(tagData) != tagSize) return null;

            LOGGER.debug("ID3 tag size: {} bytes", tagSize);
            return findAPIC(tagData, version);
        }
    }

    private byte[] findAPIC(byte[] tagData, int version) {
        boolean isV4 = version >= 4; // ID3v2.4 uses syncsafe frame sizes
        int offset = 0;
        while (offset + 10 <= tagData.length) {
            if (tagData[offset] == 0) break; // padding

            String frameId = new String(tagData, offset, 4, StandardCharsets.ISO_8859_1);

            int frameSize;
            if (isV4) {
                // Syncsafe integer (7 bits per byte)
                frameSize = ((tagData[offset + 4] & 0x7F) << 21)
                          | ((tagData[offset + 5] & 0x7F) << 14)
                          | ((tagData[offset + 6] & 0x7F) << 7)
                          | (tagData[offset + 7] & 0x7F);
            } else {
                // Regular big-endian 32-bit (ID3v2.3)
                frameSize = ((tagData[offset + 4] & 0xFF) << 24)
                          | ((tagData[offset + 5] & 0xFF) << 16)
                          | ((tagData[offset + 6] & 0xFF) << 8)
                          | (tagData[offset + 7] & 0xFF);
            }
            offset += 10; // 4 ID + 4 size + 2 flags

            if (frameId.equals("APIC")) {
                LOGGER.debug("APIC frame: {} bytes (ID3v2.{})", frameSize, version);
                byte[] image = parseAPIC(tagData, offset, frameSize);
                if (image != null) {
                    String fmt = (image.length >= 4)
                        ? String.format("0x%02X%02X%02X%02X", image[0], image[1], image[2], image[3])
                        : "short";
                    LOGGER.debug("APIC extracted: {} bytes (magic={})", image.length, fmt);
                }
                return image;
            }
            offset += frameSize;
        }
        return null;
    }

    private byte[] parseAPIC(byte[] data, int offset, int size) {
        if (size <= 0 || offset + size > data.length) return null;

        int encoding = data[offset] & 0xFF;
        int pos = offset + 1;

        // MIME type (null-terminated, always single-byte)
        int mimeEnd = findNull(data, pos, offset + size);
        if (mimeEnd < 0) return null;
        pos = mimeEnd + 1;

        // Picture type (1 byte)
        pos++;

        // Description (encoding-dependent null-termination)
        if (encoding == 1 || encoding == 2) {
            // UTF-16 / UTF-16BE: 2-byte null terminator (00 00)
            int descEnd = findDoubleNull(data, pos, offset + size);
            if (descEnd < 0) {
                pos = offset + size;
            } else {
                pos = descEnd + 2;
            }
        } else {
            // ISO-8859-1 (0) or UTF-8 (3): 1-byte null terminator
            int descEnd = findNull(data, pos, offset + size);
            if (descEnd < 0) {
                pos = offset + size;
            } else {
                pos = descEnd + 1;
            }
        }

        int imgSize = offset + size - pos;
        if (imgSize <= 0) return null;
        byte[] imgData = new byte[imgSize];
        System.arraycopy(data, pos, imgData, 0, imgSize);
        return imgData;
    }

    private int findNull(byte[] data, int start, int end) {
        for (int i = start; i < end; i++) {
            if (data[i] == 0) return i;
        }
        return -1;
    }

    private int findDoubleNull(byte[] data, int start, int end) {
        for (int i = start; i < end - 1; i++) {
            if (data[i] == 0 && data[i + 1] == 0) return i;
        }
        return -1;
    }

    private byte[] readAllBytes(InputStream in) throws IOException {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        byte[] tmp = new byte[4096];
        int n;
        while ((n = in.read(tmp)) != -1) buf.write(tmp, 0, n);
        return buf.toByteArray();
    }
}
