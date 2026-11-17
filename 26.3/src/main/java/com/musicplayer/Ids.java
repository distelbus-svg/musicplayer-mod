package com.musicplayer;

import net.minecraft.resources.Identifier;

/**
 * Converts between the string texture/sound ids used by the shared code and game Identifiers, and hides
 * the factory methods whose names differ between Minecraft versions.
 */
public final class Ids {
    private Ids() {}

    public static Identifier of(String s) {
        return s == null ? null : Identifier.parse(s);
    }

    public static String str(Identifier id) {
        return id == null ? null : id.toString();
    }

    public static Identifier rl(String namespace, String path) {
        return Identifier.fromNamespaceAndPath(namespace, path);
    }

    public static Identifier parse(String s) {
        return Identifier.parse(s);
    }
}
