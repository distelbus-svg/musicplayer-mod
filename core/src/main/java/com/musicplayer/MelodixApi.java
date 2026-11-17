package com.musicplayer;

import com.musicplayer.port.*;


import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Public extension point for Melodix addons. Addons register from their own client initializer;
 * the core never depends on any addon being present.
 *
 * Tabs and settings categories appear in the OreUI interface.
 */
public final class MelodixApi {

    private static final List<StreamSourceProvider> STREAM_PROVIDERS = new CopyOnWriteArrayList<>();
    private static final List<Tab> TABS = new CopyOnWriteArrayList<>();
    private static final List<SettingsCategory> SETTINGS = new CopyOnWriteArrayList<>();

    /** TODO: placeholder until the YouTube add-on is published. */
    public static final String YOUTUBE_ADDON_URL = "https://modrinth.com/mod/melodix-youtube";

    private MelodixApi() {}

    // ---- stream sources

    public static void registerStreamSourceProvider(StreamSourceProvider provider) {
        if (provider != null && !STREAM_PROVIDERS.contains(provider)) STREAM_PROVIDERS.add(provider);
    }

    /** First registered provider that accepts the path, or null if none does. */
    public static StreamSourceProvider streamSourceFor(String path) {
        if (path == null) return null;
        for (StreamSourceProvider p : STREAM_PROVIDERS) {
            if (p.accepts(path)) return p;
        }
        return null;
    }

    /** True once any addon can play non-file tracks (i.e. the YouTube add-on is installed). */
    public static boolean hasStreamSourceProviders() { return !STREAM_PROVIDERS.isEmpty(); }

    /** Asks the user to confirm, then opens the add-on's download page in their browser. */
    public static void openYoutubeAddonPage() {
        try {
            Ports.host().openLinkConfirm(YOUTUBE_ADDON_URL);
        } catch (Exception ignored) {}
    }

    // ---- cover art for non-file songs

    private static final List<java.util.function.Function<String, byte[]>> ART_PROVIDERS = new CopyOnWriteArrayList<>();

    /**
     * Supplies cover art for songs whose path is not a local file. The function receives a
     * {@code SongInfo.getPath()}, may block (it runs on a background thread), and returns image bytes
     * (PNG, JPEG, ...) already cropped the way they should be shown, or null if the path isn't its own.
     */
    public static void registerArtProvider(java.util.function.Function<String, byte[]> provider) {
        if (provider != null && !ART_PROVIDERS.contains(provider)) ART_PROVIDERS.add(provider);
    }

    public static boolean hasArtProviders() { return !ART_PROVIDERS.isEmpty(); }

    /** First non-null result from the registered art providers. Blocking: call off the render thread. */
    public static byte[] artFor(String path) {
        for (java.util.function.Function<String, byte[]> p : ART_PROVIDERS) {
            try {
                byte[] b = p.apply(path);
                if (b != null && b.length > 0) return b;
            } catch (RuntimeException ignored) {
                // A misbehaving provider must not break the cover lookup for others.
            }
        }
        return null;
    }

    // ---- header tabs

    /**
     * A tab in the OreUI header, after Home / Minecraft / Playlists. The screen the opener returns
     * should extend {@code OreScreen} and override {@code activeCustomTab()} to return this tab's id.
     */
    public record Tab(String id, String label, Supplier<UiScreen> opener) {}

    public static void registerTab(String id, String label, Supplier<UiScreen> opener) {
        if (id == null || label == null || opener == null) return;
        for (Tab t : TABS) if (t.id().equals(id)) return;
        TABS.add(new Tab(id, label, opener));
    }

    public static List<Tab> tabs() { return TABS; }

    // ---- settings

    /** One line in an addon settings page. Build with {@link #toggle}, {@link #button} or {@link #info}. */
    public record SettingsRow(Kind kind, String label, String desc, BooleanSupplier getB, Consumer<Boolean> setB,
                              String buttonText, Runnable action, Supplier<String> text) {
        public enum Kind { TOGGLE, BUTTON, INFO }

        public static SettingsRow toggle(String label, String desc, BooleanSupplier get, Consumer<Boolean> set) {
            return new SettingsRow(Kind.TOGGLE, label, desc, get, set, null, null, null);
        }

        public static SettingsRow button(String label, String desc, String buttonText, Runnable action) {
            return new SettingsRow(Kind.BUTTON, label, desc, null, null, buttonText, action, null);
        }

        /** Read-only value shown in a box, e.g. a status or a path. */
        public static SettingsRow info(String label, String desc, Supplier<String> text) {
            return new SettingsRow(Kind.INFO, label, desc, null, null, null, null, text);
        }
    }

    /** A category in the settings rail. {@code rows} is called each time the settings screen opens. */
    public record SettingsCategory(String id, String name, String blurb, Supplier<List<SettingsRow>> rows) {}

    public static void registerSettingsCategory(String id, String name, String blurb, Supplier<List<SettingsRow>> rows) {
        if (id == null || name == null || rows == null) return;
        for (SettingsCategory c : SETTINGS) if (c.id().equals(id)) return;
        SETTINGS.add(new SettingsCategory(id, name, blurb == null ? "" : blurb, rows));
    }

    public static List<SettingsCategory> settingsCategories() { return SETTINGS; }
}
