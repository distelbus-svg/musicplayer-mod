package com.musicplayer;

import com.musicplayer.config.MusicPlayerConfig;

public final class DebugLog {
    private DebugLog() {}

    public static void info(String message) {
        if (MusicPlayerConfig.getInstance().isDebugLogsEnabled()) {
            System.out.println("[MusicPlayer] " + message);
        }
    }

    public static void info(String format, Object... args) {
        if (MusicPlayerConfig.getInstance().isDebugLogsEnabled()) {
            System.out.println("[MusicPlayer] " + String.format(format, args));
        }
    }

    public static void warn(String message) {
        if (MusicPlayerConfig.getInstance().isDebugLogsEnabled()) {
            System.out.println("[MusicPlayer] " + message);
        }
    }

    public static void error(String message) {
        if (MusicPlayerConfig.getInstance().isDebugLogsEnabled()) {
            System.err.println("[MusicPlayer] " + message);
        }
    }

    public static void error(String format, Object... args) {
        if (MusicPlayerConfig.getInstance().isDebugLogsEnabled()) {
            System.err.println("[MusicPlayer] " + String.format(format, args));
        }
    }
}
