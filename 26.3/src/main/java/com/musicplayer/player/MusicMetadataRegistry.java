package com.musicplayer.player;

import com.musicplayer.TrackMetadata;
import net.minecraft.resources.Identifier;

import java.util.HashMap;
import java.util.Map;

public class MusicMetadataRegistry {

    private static final Map<String, TrackMetadata> METADATA = new HashMap<>();

    static {
        // ──────────────────────────────────────────────
        // Minecraft: Volume Alpha — C418
        // ──────────────────────────────────────────────
        put("music/game/calm1",     "Minecraft",     "C418", "Minecraft: Volume Alpha");
        put("music/game/calm2",     "Clark",         "C418", "Minecraft: Volume Alpha");
        put("music/game/calm3",     "Sweden",        "C418", "Minecraft: Volume Alpha");
        put("music/game/hal1",      "Danny",         "C418", "Minecraft: Volume Alpha");
        put("music/game/hal2",      "Dry Hands",     "C418", "Minecraft: Volume Alpha");
        put("music/game/hal3",      "Wet Hands",     "C418", "Minecraft: Volume Alpha");
        put("music/game/hal4",      "Mice on Venus", "C418", "Minecraft: Volume Alpha");
        put("music/game/nuance1",   "Key",           "C418", "Minecraft: Volume Alpha");
        put("music/game/piano1",    "Living Mice",   "C418", "Minecraft: Volume Alpha");
        put("music/game/piano2",    "Haggstrom",     "C418", "Minecraft: Volume Alpha");
        put("music/game/piano3",    "Dreiton",       "C418", "Minecraft: Volume Alpha");
        put("music/menu/menu1",     "Moog City",     "C418", "Minecraft: Volume Alpha");
        put("music/game/creative/creative",  "Creative",  "C418", "Minecraft: Volume Alpha");
        put("music/game/creative/creative1", "Creative",  "C418", "Minecraft: Volume Alpha");
        put("music/game/end/end",   "The End",       "C418", "Minecraft: Volume Alpha");
        put("music/game/end/credits_music", "Alpha", "C418", "Minecraft: Volume Alpha");

        // Discs — Volume Alpha
        put("records/13",       "13",       "C418", "Minecraft: Volume Alpha");
        put("records/cat",      "cat",      "C418", "Minecraft: Volume Alpha");
        put("records/blocks",   "blocks",   "C418", "Minecraft: Volume Alpha");
        put("records/chirp",    "chirp",    "C418", "Minecraft: Volume Alpha");
        put("records/far",      "far",      "C418", "Minecraft: Volume Alpha");
        put("records/mall",     "mall",     "C418", "Minecraft: Volume Alpha");
        put("records/mellohi",  "mellohi",  "C418", "Minecraft: Volume Alpha");
        put("records/stal",     "stal",     "C418", "Minecraft: Volume Alpha");
        put("records/strad",    "strad",    "C418", "Minecraft: Volume Alpha");
        put("records/ward",     "ward",     "C418", "Minecraft: Volume Alpha");
        put("records/11",       "11",       "C418", "Minecraft: Volume Alpha");
        put("records/wait",     "wait",     "C418", "Minecraft: Volume Alpha");

        // ──────────────────────────────────────────────
        // Minecraft: Volume Beta — C418
        // ──────────────────────────────────────────────
        put("music/menu/menu2",                     "Moog City 2",           "C418",  "Minecraft: Volume Beta");
        put("music/game/creative/creative2",        "Creative",              "C418",  "Minecraft: Volume Beta");
        put("music/game/piano2",                    "Haggstrom",             "C418",  "Minecraft: Volume Beta");
        put("music/game/piano3",                    "Dreiton",               "C418",  "Minecraft: Volume Beta");
        put("music/game/hal4",                      "Mice on Venus",         "C418",  "Minecraft: Volume Beta");
        put("music/game/nuance1",                   "Key",                   "C418",  "Minecraft: Volume Beta");
        put("music/game/beginning2",                "Beginning",             "C418",  "Minecraft: Volume Beta");
        put("music/game/oxidation",                 "Dreiton",               "C418",  "Minecraft: Volume Beta");

        // ──────────────────────────────────────────────
        // Minecraft: Nether Update — Lena Raine
        // ──────────────────────────────────────────────
        put("music/game/nether/crimson_forest/crimson_forest1",  "Chrysopoeia",  "Lena Raine",  "Minecraft: Nether Update");
        put("music/game/nether/crimson_forest/crimson_forest2",  "Rubedo",       "Lena Raine",  "Minecraft: Nether Update");
        put("music/game/nether/crimson_forest/crimson_forest3",  "So Below",     "Lena Raine",  "Minecraft: Nether Update");
        put("music/game/nether/nether_wastes/nether_wastes1",    "Chrysopoeia",  "Lena Raine",  "Minecraft: Nether Update");
        put("music/game/nether/nether_wastes/nether_wastes2",    "Rubedo",       "Lena Raine",  "Minecraft: Nether Update");
        put("music/game/nether/nether_wastes/nether_wastes3",    "So Below",     "Lena Raine",  "Minecraft: Nether Update");
        put("music/game/nether/soul_sand_valley/soul_sand_valley1",  "Rubedo",   "Lena Raine",  "Minecraft: Nether Update");
        put("music/game/nether/soul_sand_valley/soul_sand_valley2",  "So Below",  "Lena Raine",  "Minecraft: Nether Update");
        put("music/game/nether/warped_forest/warped_forest1",    "Rubedo",       "Lena Raine",  "Minecraft: Nether Update");
        put("music/game/nether/warped_forest/warped_forest2",    "Chrysopoeia",  "Lena Raine",  "Minecraft: Nether Update");
        put("music/game/nether/warped_forest/warped_forest3",    "So Below",     "Lena Raine",  "Minecraft: Nether Update");
        put("music/game/nether/basalt_deltas/basalt_deltas1",    "So Below",     "Lena Raine",  "Minecraft: Nether Update");

        // ──────────────────────────────────────────────
        // Minecraft: Caves & Cliffs — Lena Raine / Kumi Tanioka
        // ──────────────────────────────────────────────
        put("music/game/caves/cave_lush",      "Ancestry",       "Lena Raine",    "Minecraft: Caves & Cliffs");
        put("music/game/caves/cave_pitch",     "Infinite Amethyst", "Lena Raine", "Minecraft: Caves & Cliffs");
        put("music/game/caves/dripstone_caves", "Wending",       "Kumi Tanioka",  "Minecraft: Caves & Cliffs");
        put("music/game/caves/lush_caves",     "Left to Bloom",  "Lena Raine",    "Minecraft: Caves & Cliffs");
        put("music/game/caves/meadow",         "Floating Dream", "Kumi Tanioka",  "Minecraft: Caves & Cliffs");
        put("music/game/caves/concrete_jungle", "One More Day",  "Lena Raine",    "Minecraft: Caves & Cliffs");

        // ──────────────────────────────────────────────
        // Minecraft: The Wild Update — Lena Raine / Kumi Tanioka
        // ──────────────────────────────────────────────
        put("music/game/deep_dark/deep_dark1",  "ancestry",      "Lena Raine",    "Minecraft: The Wild Update");
        put("music/game/mangrove_swamp/mangrove_swamp1", "Aeongrown", "Kumi Tanioka", "Minecraft: The Wild Update");
        put("music/game/deep_dark/deep_dark2", "Infinite Amethyst", "Lena Raine", "Minecraft: The Wild Update");

        // ──────────────────────────────────────────────
        // Minecraft: Trails & Tales — Aaron Cherof
        // ──────────────────────────────────────────────
        put("music/game/cherry_grove/cherry_grove1",   "Bloom",         "Aaron Cherof",  "Minecraft: Trails & Tales");
        put("music/game/cherry_grove/cherry_grove2",   "Echo in the Wind", "Aaron Cherof", "Minecraft: Trails & Tales");
        put("music/game/meadow/meadow1",                "A Familiar Room","Aaron Cherof",  "Minecraft: Trails & Tales");
        put("music/game/bamboo_jungle/bamboo_jungle1",  "Yakusoku",     "Aaron Cherof",  "Minecraft: Trails & Tales");
        put("music/game/bamboo_jungle/bamboo_jungle2",  "Komorebi",     "Aaron Cherof",  "Minecraft: Trails & Tales");

        // Drops
        put("records/creator", "Creator", "Lena Raine", "Minecraft: Tricky Trials");
        put("records/creator_music_box", "Creator (Music Box)", "Lena Raine", "Minecraft: Tricky Trials");
        put("records/precipice", "Precipice", "Aaron Cherof", "Minecraft: Tricky Trials");

        // ──────────────────────────────────────────────
        // Minecraft: Tricky Trials — Lena Raine / Aaron Cherof
        // ──────────────────────────────────────────────
        put("music/game/tricky_trials/trial_chambers1", "Eld Unknown", "Lena Raine", "Minecraft: Tricky Trials");
        put("music/game/tricky_trials/trial_chambers2", "Deeper",      "Lena Raine", "Minecraft: Tricky Trials");
        put("music/game/tricky_trials/trial_chambers3", "Puzzle Box",  "Aaron Cherof", "Minecraft: Tricky Trials");

        // ──────────────────────────────────────────────
        // Minecraft: The Garden Awakens — Aaron Cherof / Kumi Tanioka
        // ──────────────────────────────────────────────
        put("music/game/pale_garden/pale_garden1", "Woven Hearts",  "Kumi Tanioka",  "Minecraft: The Garden Awakens");
        put("music/game/pale_garden/pale_garden2", "Petrichor",     "Aaron Cherof",  "Minecraft: The Garden Awakens");
        put("music/game/pale_garden/pale_garden3", "Gloaming",      "Aaron Cherof",  "Minecraft: The Garden Awakens");

        // ──────────────────────────────────────────────
        // Additional discs (post-Volume Alpha)
        // ──────────────────────────────────────────────
        put("records/pigstep", "Pigstep", "Lena Raine", "Minecraft: Nether Update");
        put("records/otherside", "Otherside", "Lena Raine", "Minecraft: The Wild Update");
        put("records/5", "5", "Samuel Åberg", "Minecraft: Trails & Tales");
        put("records/relic", "Relic", "Aaron Cherof", "Minecraft: Trails & Tales");
    }

    private static void put(String path, String title, String artist, String album) {
        METADATA.put("minecraft:" + path, new TrackMetadata(title, artist, album));
    }

    /**
     * Look up metadata for a resolved sound identifier.
     * Returns null if no metadata is registered.
     */
    public static TrackMetadata lookup(Identifier resolvedSoundId) {
        return METADATA.get(resolvedSoundId.toString());
    }


}
