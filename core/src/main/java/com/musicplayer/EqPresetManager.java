package com.musicplayer;

import com.musicplayer.port.*;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.musicplayer.config.MusicPlayerConfig;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Equalizer presets: a fixed set of built-ins plus the user's own, stored in
 * {@code config/musicplayer/eq_presets.json}. A preset is a preamp value and the five band gains (dB).
 */
public final class EqPresetManager {

    public static final int MAX_NAME_LENGTH = 32;

    public static final class Preset {
        private String name;
        private float preamp = 1f;
        private float[] gains = new float[5];
        private transient boolean builtIn;

        private Preset() {}

        Preset(String name, float preamp, float[] gains, boolean builtIn) {
            this.name = name;
            this.preamp = preamp;
            this.gains = Arrays.copyOf(gains, 5);
            this.builtIn = builtIn;
        }

        public String name() { return name; }
        public float preamp() { return preamp; }
        public float[] gains() { return gains == null || gains.length < 5 ? new float[5] : Arrays.copyOf(gains, 5); }
        public boolean builtIn() { return builtIn; }
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path FILE = Ports.host().configDir()
            .resolve("musicplayer").resolve("eq_presets.json");

    // Band centres: 60 Hz, 230 Hz, 910 Hz, 3.6 kHz, 14 kHz
    private static final List<Preset> BUILT_IN = List.of(
            new Preset("Flat", 1.0f, new float[]{0, 0, 0, 0, 0}, true),
            new Preset("Bass Boost", 0.9f, new float[]{8, 5, 0, 0, 0}, true),
            new Preset("Treble Boost", 0.9f, new float[]{0, 0, 0, 5, 8}, true),
            new Preset("Vocal", 1.0f, new float[]{-2, 0, 4, 3, -1}, true),
            new Preset("Rock", 0.9f, new float[]{5, 3, -2, 3, 5}, true),
            new Preset("Pop", 1.0f, new float[]{-1, 3, 4, 2, -1}, true),
            new Preset("Jazz", 1.0f, new float[]{3, 2, -2, 2, 4}, true),
            new Preset("Classical", 1.0f, new float[]{3, 2, 0, 2, 3}, true),
            new Preset("Electronic", 0.9f, new float[]{6, 3, -1, 3, 5}, true),
            new Preset("Loudness", 0.85f, new float[]{6, 2, 0, 2, 5}, true));

    private static final List<Preset> USER = new ArrayList<>();
    private static boolean loaded;

    private EqPresetManager() {}

    // ---------------------------------------------------------------- queries

    public static synchronized List<Preset> builtIns() {
        return BUILT_IN;
    }

    public static synchronized List<Preset> userPresets() {
        ensureLoaded();
        return Collections.unmodifiableList(new ArrayList<>(USER));
    }

    /** Built-in presets first, then the user's. */
    public static synchronized List<Preset> all() {
        ensureLoaded();
        List<Preset> out = new ArrayList<>(BUILT_IN);
        out.addAll(USER);
        return out;
    }

    public static synchronized Preset find(String name) {
        ensureLoaded();
        if (name == null) return null;
        for (Preset p : BUILT_IN) if (p.name.equalsIgnoreCase(name)) return p;
        for (Preset p : USER) if (p.name.equalsIgnoreCase(name)) return p;
        return null;
    }

    /** Name of the preset matching the current equalizer settings, or null when they are custom. */
    public static synchronized String currentName() {
        ensureLoaded();
        MusicPlayerConfig cfg = MusicPlayerConfig.getInstance();
        float pre = cfg.getPreamp();
        float[] g = cfg.getEqGains();
        for (Preset p : all()) {
            if (Math.abs(p.preamp - pre) > 0.03f) continue;
            float[] pg = p.gains();
            boolean same = true;
            for (int i = 0; i < 5; i++) {
                if (Math.abs(pg[i] - g[i]) > 0.5f) { same = false; break; }
            }
            if (same) return p.name;
        }
        return null;
    }

    /**
     * Checks a name for saving. {@code renaming} is the preset being renamed (null when saving a new one).
     * Returns an error message, or null when the name is fine.
     */
    public static synchronized String validateName(String name, String renaming) {
        ensureLoaded();
        String n = name == null ? "" : name.trim();
        if (n.isEmpty()) return "Enter a name for the preset.";
        if (n.length() > MAX_NAME_LENGTH) return "Names can be at most " + MAX_NAME_LENGTH + " characters.";
        for (Preset p : BUILT_IN) {
            if (p.name.equalsIgnoreCase(n)) return "That name belongs to a built-in preset.";
        }
        if (renaming != null && !renaming.equalsIgnoreCase(n)) {
            for (Preset p : USER) {
                if (p.name.equalsIgnoreCase(n)) return "A preset with that name already exists.";
            }
        }
        return null;
    }

    /** First free "My Preset N" name. */
    public static synchronized String suggestName() {
        ensureLoaded();
        for (int i = 1; ; i++) {
            String n = "My Preset " + i;
            if (find(n) == null) return n;
        }
    }

    // ---------------------------------------------------------------- actions

    /** Applies a preset to the equalizer and switches the equalizer on. */
    public static void apply(Preset p) {
        if (p == null) return;
        MusicPlayerConfig cfg = MusicPlayerConfig.getInstance();
        cfg.setEqAll(p.preamp, p.gains());
        cfg.setEqEnabled(true);
    }

    /** Saves the current equalizer settings under {@code name}, replacing a user preset of the same name. */
    public static synchronized boolean saveCurrent(String name) {
        if (validateName(name, null) != null) return false;
        String n = name.trim();
        MusicPlayerConfig cfg = MusicPlayerConfig.getInstance();
        Preset fresh = new Preset(n, cfg.getPreamp(), cfg.getEqGains(), false);
        for (int i = 0; i < USER.size(); i++) {
            if (USER.get(i).name.equalsIgnoreCase(n)) {
                USER.set(i, fresh);
                persist();
                return true;
            }
        }
        USER.add(fresh);
        persist();
        return true;
    }

    public static synchronized boolean rename(String oldName, String newName) {
        if (validateName(newName, oldName) != null) return false;
        for (Preset p : USER) {
            if (p.name.equalsIgnoreCase(oldName)) {
                p.name = newName.trim();
                persist();
                return true;
            }
        }
        return false;
    }

    public static synchronized boolean delete(String name) {
        ensureLoaded();
        boolean removed = USER.removeIf(p -> p.name.equalsIgnoreCase(name));
        if (removed) persist();
        return removed;
    }

    /** Replaces all user presets (used when restoring a backup). Built-in name clashes are dropped. */
    public static synchronized void replaceUserPresets(List<Preset> presets) {
        ensureLoaded();
        USER.clear();
        if (presets != null) {
            for (Preset p : presets) {
                if (p == null || p.name == null) continue;
                String n = p.name.trim();
                if (n.isEmpty() || n.length() > MAX_NAME_LENGTH) continue;
                boolean clash = false;
                for (Preset b : BUILT_IN) if (b.name.equalsIgnoreCase(n)) { clash = true; break; }
                for (Preset u : USER) if (u.name.equalsIgnoreCase(n)) { clash = true; break; }
                if (clash) continue;
                USER.add(new Preset(n, p.preamp, p.gains(), false));
            }
        }
        persist();
    }

    // ---------------------------------------------------------------- persistence

    private static void ensureLoaded() {
        if (loaded) return;
        loaded = true;
        USER.clear();
        if (!Files.exists(FILE)) return;
        try {
            List<Preset> read = GSON.fromJson(Files.readString(FILE), new TypeToken<List<Preset>>() {}.getType());
            if (read != null) {
                for (Preset p : read) {
                    if (p != null && p.name != null && !p.name.isBlank()) USER.add(new Preset(p.name, p.preamp, p.gains(), false));
                }
            }
        } catch (IOException | RuntimeException e) {
            System.err.println("[MusicPlayer] Failed to load EQ presets: " + e.getMessage());
        }
    }

    private static void persist() {
        try {
            Files.createDirectories(FILE.getParent());
            Path tmp = FILE.resolveSibling("eq_presets.json.tmp");
            Files.writeString(tmp, GSON.toJson(USER));
            try {
                Files.move(tmp, FILE, java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                        java.nio.file.StandardCopyOption.ATOMIC_MOVE);
            } catch (java.nio.file.AtomicMoveNotSupportedException e) {
                Files.move(tmp, FILE, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            System.err.println("[MusicPlayer] Failed to save EQ presets: " + e.getMessage());
        }
    }

    /** Lower-case key helper for callers that need to compare names. */
    public static String key(String name) {
        return name == null ? "" : name.trim().toLowerCase(Locale.ROOT);
    }
}
