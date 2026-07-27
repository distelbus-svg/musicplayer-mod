package com.musicplayer.hud;

public record Notification(
    String title,
    String subtitle,
    long createdMs,
    long displayMs,
    int iconColor
) {
    public boolean isExpired() {
        return System.currentTimeMillis() > createdMs + displayMs;
    }
}
