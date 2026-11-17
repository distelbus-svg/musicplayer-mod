package com.musicplayer.hud;

import com.musicplayer.port.*;
import com.musicplayer.ui.*;

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
