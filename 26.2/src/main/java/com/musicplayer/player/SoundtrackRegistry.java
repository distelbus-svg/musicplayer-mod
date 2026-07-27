package com.musicplayer.player;

import com.musicplayer.DebugLog;
import com.musicplayer.TrackMetadata;
import com.musicplayer.VanillaTrack;
import com.musicplayer.mixin.WeighedSoundEventsAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.Sound;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.client.sounds.WeighedSoundEvents;
import net.minecraft.client.sounds.Weighted;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import java.io.InputStream;
import java.util.*;

public class SoundtrackRegistry {
    private static SoundtrackRegistry instance;
    private final Map<String, VanillaTrack> trackCache = new LinkedHashMap<>();
    private final Map<Identifier, Integer> durationCache = new HashMap<>();
    private final Map<Identifier, List<String>> eventToVariants = new HashMap<>();
    private boolean discovered;

    // Failed tracks: prevents infinite retry loops for 30 seconds
    private static final long FAILED_COOLDOWN_MS = 30_000;
    private final Map<Identifier, Long> failedTrackTimestamps = new HashMap<>();

    private SoundtrackRegistry() {}

    public static synchronized SoundtrackRegistry getInstance() {
        if (instance == null) instance = new SoundtrackRegistry();
        return instance;
    }

    public List<VanillaTrack> discoverTracks() {
        if (discovered) return List.copyOf(trackCache.values());
        trackCache.clear();
        durationCache.clear();
        eventToVariants.clear();
        discovered = true;

        Minecraft mc = Minecraft.getInstance();
        Set<Identifier> soundIds = new HashSet<>(mc.getSoundManager().getAvailableSounds());
        DebugLog.info("SoundtrackRegistry: %d available sounds", soundIds.size());

        int count = 0;
        for (Identifier id : soundIds) {
            String path = id.getPath();
            if (path.startsWith("music/") || path.startsWith("record/") || path.startsWith("music_disc.") || path.startsWith("music.")) {
                registerVariants(id);
                count++;
            }
        }
        DebugLog.info("SoundtrackRegistry: processed %d music events, registered %d variant tracks",
            count, trackCache.size());

        return List.copyOf(trackCache.values());
    }

    public VanillaTrack getTrack(Identifier soundId) {
        if (isTrackFailed(soundId)) return null;

        String key = soundId.toString();
        VanillaTrack cached = trackCache.get(key);
        if (cached != null) {
            DebugLog.info("SoundtrackRegistry.getTrack: cache hit for %s -> %s", soundId, cached.title());
            return cached;
        }

        List<String> variantKeys = eventToVariants.get(soundId);
        if (variantKeys != null && !variantKeys.isEmpty()) {
            VanillaTrack v = trackCache.get(variantKeys.get(0));
            DebugLog.info("SoundtrackRegistry.getTrack: event->variant for %s -> %s", soundId,
                v != null ? v.title() : "null");
            return v;
        }

        DebugLog.info("SoundtrackRegistry.getTrack: not found, registering variants for %s", soundId);
        registerVariants(soundId);
        variantKeys = eventToVariants.get(soundId);
        if (variantKeys != null && !variantKeys.isEmpty()) {
            return trackCache.get(variantKeys.get(0));
        }

        DebugLog.info("SoundtrackRegistry.getTrack: no variants found for %s", soundId);
        return null;
    }

    // ─── Failed-track API ───

    public void markTrackFailed(Identifier id) {
        failedTrackTimestamps.put(id, monotonicTimeMs());
    }

    public boolean isTrackFailed(Identifier id) {
        Long failedAt = failedTrackTimestamps.get(id);
        if (failedAt == null) return false;
        if (monotonicTimeMs() - failedAt > FAILED_COOLDOWN_MS) {
            failedTrackTimestamps.remove(id);
            return false;
        }
        return true;
    }

    // Per-variant failed-track API: keyed on resolved variant ID so one
    // broken variant does not block all siblings under the same parent event.
    public void markVariantFailed(Identifier resolvedId) {
        if (resolvedId != null) failedTrackTimestamps.put(resolvedId, monotonicTimeMs());
    }

    public boolean isVariantFailed(Identifier resolvedId) {
        if (resolvedId == null) return false;
        Long failedAt = failedTrackTimestamps.get(resolvedId);
        if (failedAt == null) return false;
        if (monotonicTimeMs() - failedAt > FAILED_COOLDOWN_MS) {
            failedTrackTimestamps.remove(resolvedId);
            return false;
        }
        return true;
    }

    /**
     * Returns true if the given event ID has more than one registered variant.
     * If the event is not yet registered, attempts to register variants.
     */
    public boolean hasMultipleVariants(Identifier eventId) {
        if (!eventToVariants.containsKey(eventId)) {
            registerVariants(eventId);
        }
        List<String> v = eventToVariants.get(eventId);
        return v != null && v.size() > 1;
    }

    public void clearFailedTracks() {
        failedTrackTimestamps.clear();
    }

    // ─── Per-variant track registration ───
    // Each SoundEvent may resolve to multiple concrete Sound variants (e.g.
    // music.game -> calm1, calm2, calm3, ...).  We register one VanillaTrack
    // per variant so that each song can carry real metadata (title, artist).

    private static final int MAX_VALIDATION_DEPTH = 10;

    private void registerVariants(Identifier eventId) {
        if (isTrackFailed(eventId)) return;
        if (eventToVariants.containsKey(eventId)) return;

        try {
            SoundManager sm = Minecraft.getInstance().getSoundManager();
            WeighedSoundEvents wse = sm.getSoundEvent(eventId);
            if (wse == null || wse == SoundManager.INTENTIONALLY_EMPTY_SOUND_EVENT) {
                DebugLog.info("registerVariants: %s has no WSE or is empty", eventId);
                return;
            }

            List<Weighted<Sound>> variants = ((WeighedSoundEventsAccessor) wse).overtune$getList();
            if (variants == null || variants.isEmpty()) {
                DebugLog.info("registerVariants: %s has no variants", eventId);
                return;
            }

            DebugLog.info("registerVariants: %s has %d variants", eventId, variants.size());
            Set<String> seenPaths = new HashSet<>();
            for (Weighted<Sound> entry : variants) {
                if (entry.getWeight() <= 0) continue;
                registerVariant(entry, eventId, seenPaths, 0);
            }
            DebugLog.info("registerVariants: %s -> registered %d tracks", eventId,
                eventToVariants.getOrDefault(eventId, List.of()).size());
        } catch (Exception e) {
            DebugLog.info("registerVariants: %s threw %s: %s", eventId, e.getClass().getSimpleName(), e.getMessage());
        }
    }

    private void registerVariant(Weighted<Sound> entry, Identifier eventId, Set<String> seenPaths, int depth) {
        if (depth > MAX_VALIDATION_DEPTH) return;
        try {
            if (entry instanceof WeighedSoundEvents nestedWse) {
                List<Weighted<Sound>> nestedList = ((WeighedSoundEventsAccessor) nestedWse).overtune$getList();
                if (nestedList != null) {
                    for (Weighted<Sound> nestedEntry : nestedList) {
                        if (nestedEntry.getWeight() <= 0) continue;
                        registerVariant(nestedEntry, eventId, seenPaths, depth + 1);
                    }
                }
                return;
            }

            Minecraft mc = Minecraft.getInstance();
            Sound sound = entry.getSound(RandomSource.create());
            if (sound == null
                || sound == SoundManager.EMPTY_SOUND
                || sound == SoundManager.INTENTIONALLY_EMPTY_SOUND) return;

            if (sound.getType() == Sound.Type.SOUND_EVENT) {
                registerVariants(sound.getLocation());
                return;
            }

            Identifier resolvedLoc = sound.getLocation();
            String resolvedKey = resolvedLoc.toString();

            if (seenPaths.contains(resolvedKey)) return;
            seenPaths.add(resolvedKey);
            if (trackCache.containsKey(resolvedKey)) return;

            Identifier oggRes = Identifier.fromNamespaceAndPath(resolvedLoc.getNamespace(),
                "sounds/" + resolvedLoc.getPath() + ".ogg");
            if (mc.getResourceManager().getResource(oggRes).isEmpty()) {
                DebugLog.info("registerVariant: OGG not found: %s", oggRes);
                return;
            }

            TrackMetadata meta = MusicMetadataRegistry.lookup(resolvedLoc);
            String title = meta != null ? meta.displayTitle() : prettifyName(resolvedLoc);
            String artist = meta != null ? meta.artist() : "Unknown";
            String album = meta != null ? meta.album() : inferAlbum(eventId);

            int duration = getRealDuration(resolvedLoc, eventId, oggRes);
            if (duration <= 0) {
                DebugLog.info("registerVariant: duration <= 0 for %s (ogg=%s)", resolvedKey, oggRes);
                return;
            }

            DebugLog.info("registerVariant: %s -> title=\"%s\" artist=\"%s\" album=\"%s\" duration=%ds event=%s",
                resolvedKey, title, artist, album, duration, eventId);

            VanillaTrack track = new VanillaTrack(resolvedKey, title, artist, album, duration, albumKey(album), eventId, resolvedLoc);
            trackCache.put(resolvedKey, track);
            eventToVariants.computeIfAbsent(eventId, k -> new ArrayList<>()).add(resolvedKey);
        } catch (Exception e) {
            // skip broken variants
        }
    }

    public void resetDiscovery() {
        discovered = false;
        trackCache.clear();
        durationCache.clear();
        eventToVariants.clear();
    }

    public List<VanillaTrack> getAllTracks() {
        return List.copyOf(trackCache.values());
    }

    public List<VanillaTrack> getSoundtracks() {
        return trackCache.values().stream()
            .filter(t -> t.trackSource() == com.musicplayer.PlayableTrack.TrackSource.VANILLA_SOUNDTRACK)
            .toList();
    }

    public List<VanillaTrack> getMusicDiscs() {
        return trackCache.values().stream()
            .filter(t -> t.trackSource() == com.musicplayer.PlayableTrack.TrackSource.VANILLA_DISC)
            .toList();
    }

    // ─── Duration ───

    private int getRealDuration(Identifier resolvedLoc, Identifier eventId, Identifier oggRes) {
        Integer cached = durationCache.get(oggRes);
        if (cached != null) return cached;

        int duration = readOggFromResource(oggRes);
        if (duration <= 0) {
            duration = fallbackDuration(eventId);
        }

        durationCache.put(oggRes, duration);
        return duration;
    }

    private static int readOggFromResource(Identifier oggResourceId) {
        try {
            var res = Minecraft.getInstance().getResourceManager().getResource(oggResourceId);
            if (res.isEmpty()) return -1;

            byte[] data;
            try (InputStream in = res.get().open()) {
                data = in.readAllBytes();
            }

            int len = data.length;
            int pos = 0;
            long lastGranule = 0;
            int sampleRate = 44100;
            boolean foundIdHeader = false;

            while (pos + 27 <= len) {
                while (pos + 4 <= len) {
                    if (data[pos] == 'O' && data[pos + 1] == 'g'
                        && data[pos + 2] == 'g' && data[pos + 3] == 'S')
                        break;
                    pos++;
                }
                if (pos + 27 > len) break;

                long granule = readLE64(data, pos + 6);
                int segments = data[pos + 26] & 0xFF;

                int segTableOffset = pos + 27;
                if (segTableOffset + segments > len) break;

                int pageDataLen = 0;
                for (int i = 0; i < segments; i++) {
                    pageDataLen += data[segTableOffset + i] & 0xFF;
                }

                int pageDataOffset = segTableOffset + segments;
                if (pageDataOffset > len) break;
                int pageDataEnd = pageDataOffset + pageDataLen;
                if (pageDataEnd > len) pageDataEnd = len;

                if (!foundIdHeader && pageDataLen >= 30 && pageDataOffset + 30 <= len) {
                    if (data[pageDataOffset] == 1
                        && data[pageDataOffset + 1] == 'v'
                        && data[pageDataOffset + 2] == 'o'
                        && data[pageDataOffset + 3] == 'r'
                        && data[pageDataOffset + 4] == 'b'
                        && data[pageDataOffset + 5] == 'i'
                        && data[pageDataOffset + 6] == 's')
                    {
                        int vorbisOffset = pageDataOffset + 12;
                        if (vorbisOffset + 4 <= len) {
                            sampleRate = readLE32(data, vorbisOffset);
                            foundIdHeader = true;
                        }
                    }
                }

                lastGranule = granule;

                pos = pageDataEnd;
                if (pos <= segTableOffset + segments) {
                    pos = segTableOffset + segments + 1;
                }
            }

            if (lastGranule > 0 && foundIdHeader && sampleRate > 0) {
                return (int) (lastGranule / sampleRate);
            }
        } catch (Exception e) {
            // OGG parse failed silently
        }
        return -1;
    }

    private static long readLE64(byte[] data, int off) {
        return (data[off] & 0xFFL)
             | ((data[off + 1] & 0xFFL) << 8)
             | ((data[off + 2] & 0xFFL) << 16)
             | ((data[off + 3] & 0xFFL) << 24)
             | ((data[off + 4] & 0xFFL) << 32)
             | ((data[off + 5] & 0xFFL) << 40)
             | ((data[off + 6] & 0xFFL) << 48)
             | ((data[off + 7] & 0xFFL) << 56);
    }

    private static int readLE32(byte[] data, int off) {
        return (data[off] & 0xFF)
             | ((data[off + 1] & 0xFF) << 8)
             | ((data[off + 2] & 0xFF) << 16)
             | ((data[off + 3] & 0xFF) << 24);
    }

    private static long monotonicTimeMs() {
        return System.nanoTime() / 1_000_000L;
    }

    private static int fallbackDuration(Identifier id) {
        String path = id.getPath();
        if (path.contains("music/game")) return 300;
        if (path.contains("music.creative")) return 180;
        if (path.contains("music.menu")) return 120;
        if (path.contains("music.credits")) return 360;
        if (path.contains("music.under_water")) return 180;
        if (path.contains("music.end")) return 240;
        if (path.contains("music.dragon")) return 180;
        if (path.contains("music_disc.13")) return 178;
        if (path.contains("music_disc.cat")) return 185;
        if (path.contains("music_disc.blocks")) return 345;
        if (path.contains("music_disc.chirp")) return 185;
        if (path.contains("music_disc.far")) return 174;
        if (path.contains("music_disc.mall")) return 197;
        if (path.contains("music_disc.mellohi")) return 96;
        if (path.contains("music_disc.stal")) return 150;
        if (path.contains("music_disc.strad")) return 188;
        if (path.contains("music_disc.ward")) return 251;
        if (path.contains("music_disc.eleven") || path.contains("music_disc.11")) return 71;
        if (path.contains("music_disc.wait")) return 238;
        if (path.contains("music_disc.pigstep")) return 148;
        if (path.contains("music_disc.otherside")) return 195;
        if (path.contains("music_disc.relic")) return 218;
        if (path.contains("music_disc.creator")) return 176;
        if (path.contains("music_disc.creator_music_box")) return 73;
        if (path.contains("music_disc.precipice") || path.contains("music_disc.precentipede")) return 214;
        if (path.contains("music_disc.fragment")) return 120;
        if (path.contains("music_disc.5")) return 204;
        if (path.contains("music_disc.lava_chicken") || path.contains("music_disc.lava")) return 185;
        if (path.contains("music_disc.tears")) return 162;
        return 180;
    }

    public static String prettifyName(Identifier id) {
        String path = id.getPath();
        String name = path;

        if (name.startsWith("music/game/")) {
            name = name.substring("music/game/".length());
        } else if (name.startsWith("music/")) {
            name = name.substring("music/".length());
        } else if (name.startsWith("music_disc.")) {
            name = name.substring("music_disc.".length());
        } else if (name.startsWith("music.")) {
            name = name.substring("music.".length());
        } else if (name.startsWith("record/")) {
            name = name.substring("record/".length());
        }

        int lastSlash = name.lastIndexOf('/');
        if (lastSlash >= 0) {
            name = name.substring(lastSlash + 1);
        }

        name = name.replace('.', ' ').replace('_', ' ').replace('/', ' ');

        if (name.isEmpty()) name = id.getNamespace();

        StringBuilder sb = new StringBuilder();
        boolean nextUpper = true;
        for (char c : name.toCharArray()) {
            if (c == ' ') { sb.append(c); nextUpper = true; }
            else if (nextUpper) { sb.append(Character.toUpperCase(c)); nextUpper = false; }
            else { sb.append(c); }
        }
        return sb.toString().trim();
    }

    private static String inferAlbum(Identifier id) {
        String path = id.getPath();
        if (path.contains("nether") || path.contains("crimson") || path.contains("warped")) return "Minecraft: Nether Update";
        if (path.contains("end") || path.contains("dragon")) return "Minecraft: The End";
        if (path.contains("caves") || path.contains("cliffs") || path.contains("dripstone")) return "Minecraft: Caves & Cliffs";
        if (path.contains("wild") || path.contains("deep") || path.contains("mangrove") || path.contains("frog")) return "Minecraft: The Wild Update";
        if (path.contains("trail") || path.contains("ruins") || path.contains("cherry")) return "Minecraft: Trails & Tales";
        if (path.contains("tricky") || path.contains("trial") || path.contains("ominous") || path.contains("creator")) return "Minecraft: Tricky Trials";
        if (path.contains("pale") || path.contains("garden") || path.contains("creaking")) return "Minecraft: The Garden Awakens";
        if (path.contains("record") || path.contains("disc")) return "Minecraft: Music Discs";
        if (path.contains("menu") || path.contains("credits")) return "Minecraft: Volume Alpha";
        if (path.startsWith("music/game")) {
            String sub = path.substring("music/game".length());
            if (sub.isEmpty()) return "Minecraft: Volume Alpha";
            if (sub.startsWith("/nether") || sub.startsWith("/crimson")) return "Minecraft: Nether Update";
            if (sub.startsWith("/end") || sub.startsWith("/dragon")) return "Minecraft: The End";
            if (sub.startsWith("/deep_dark") || sub.startsWith("/ancient_city")) return "Minecraft: The Wild Update";
            if (sub.startsWith("/caves") || sub.startsWith("/dripstone")) return "Minecraft: Caves & Cliffs";
            if (sub.startsWith("/bamboo") || sub.startsWith("/cherry") || sub.startsWith("/meadow")) return "Minecraft: Trails & Tales";
            if (sub.startsWith("/pale_garden")) return "Minecraft: The Garden Awakens";
        }
        return "Minecraft";
    }

    private static String albumKey(String album) {
        if (album == null) return "minecraft";
        return album.toLowerCase()
            .replace("minecraft: volume ", "minecraft_volume_")
            .replace("minecraft: ", "minecraft_")
            .replace(" ", "_")
            .replace("&", "and")
            .replace("'", "")
            .replace("\u00e9", "e");
    }
}
