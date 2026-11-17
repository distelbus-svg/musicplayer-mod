package com.musicplayer.ore;

import com.musicplayer.port.*;
import com.musicplayer.ui.*;

import java.util.List;

/** Icons a playlist can use on the home screen. The id is stored in the playlist. */
final class OreIcons {

    record Entry(String id, String label, int[][] rects, int gw, int gh) {}

    private static final int[][] STAR = {
            {4, 0, 1, 2}, {3, 2, 3, 1}, {0, 3, 9, 1}, {1, 4, 7, 1}, {2, 5, 5, 1},
            {2, 6, 2, 1}, {5, 6, 2, 1}, {1, 7, 2, 1}, {6, 7, 2, 1}};
    private static final int[][] DIAMOND = {
            {4, 0, 1, 1}, {3, 1, 3, 1}, {2, 2, 5, 1}, {1, 3, 7, 1}, {0, 4, 9, 1},
            {1, 5, 7, 1}, {2, 6, 5, 1}, {3, 7, 3, 1}, {4, 8, 1, 1}};
    private static final int[][] CROWN = {
            {0, 0, 1, 1}, {5, 0, 1, 1}, {10, 0, 1, 1}, {0, 1, 2, 1}, {4, 1, 3, 1}, {9, 1, 2, 1},
            {0, 2, 11, 1}, {1, 3, 9, 3}, {0, 6, 11, 2}};

    static final List<Entry> ALL = List.of(
            new Entry("list", "List", OreScreen.ICON_LIST, 12, 12),
            new Entry("note", "Music note", OreScreen.ICON_NOTE, 12, 12),
            new Entry("heart", "Heart", OreScreen.ICON_HEART, 8, 7),
            new Entry("star", "Star", STAR, 9, 8),
            new Entry("diamond", "Diamond", DIAMOND, 9, 9),
            new Entry("crown", "Crown", CROWN, 11, 8),
            new Entry("bars", "Equalizer", OreScreen.ICON_BARS, 8, 8),
            new Entry("shuffle", "Shuffle", OreScreen.ICON_SHUFFLE, 10, 10),
            new Entry("repeat", "Repeat", OreScreen.ICON_REPEAT, 10, 10),
            new Entry("play", "Play", OreScreen.ICON_PLAY, 10, 10),
            new Entry("frame", "Block", OreScreen.ICON_FRAME, 12, 12),
            new Entry("folder", "Folder", OreScreen.ICON_FOLDER, 12, 12));

    private OreIcons() {}

    /** The icon for an id; unknown or missing ids give the default list icon. */
    static Entry get(String id) {
        if (id != null) {
            for (Entry e : ALL) if (e.id().equals(id)) return e;
        }
        return ALL.get(0);
    }
}
