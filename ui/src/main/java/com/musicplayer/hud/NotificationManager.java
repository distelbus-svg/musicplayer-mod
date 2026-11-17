package com.musicplayer.hud;

import com.musicplayer.port.*;
import com.musicplayer.ui.*;

import com.musicplayer.SongInfo;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;

public class NotificationManager {
    private static NotificationManager instance;

    private static final int PANEL_WIDTH = 180;
    private static final int PANEL_HEIGHT = 34;
    private static final int MAX_VISIBLE = 3;
    private static final int MARGIN = 8;

    private final Deque<ActiveNotification> queue = new ArrayDeque<>();
    private long lastSoundPlayedMs = 0;

    private static class ActiveNotification {
        final Notification notification;
        float slideX;
        boolean slidingOut;

        ActiveNotification(Notification notification) {
            this.notification = notification;
            this.slideX = PANEL_WIDTH + 10;
            this.slidingOut = false;
        }
    }

    private NotificationManager() {}

    public static void init() {
        if (instance == null) {
            instance = new NotificationManager();
            UiHost.get().registerHud("notifications", instance::render);
        }
    }

    public static void showNowPlaying(SongInfo song) {
        if (instance == null) return;
        String title = song.getTitle();
        String subtitle = song.getArtist();
        String displayTitle = (title != null && !title.isEmpty()) ? title : "Unknown";
        String displaySub = (subtitle != null && !subtitle.isEmpty()) ? subtitle : "";
        Notification n = new Notification(displayTitle, displaySub,
                System.currentTimeMillis(), 4000, 0xFF00AA00);
        instance.push(n);
    }

    public static void showSystem(String message) {
        if (instance == null) return;
        Notification n = new Notification(message, "",
                System.currentTimeMillis(), 4000, 0xFF0088FF);
        instance.push(n);
    }

    public static void notify(String title, String subtitle) {
        if (instance == null) return;
        Notification n = new Notification(title, subtitle,
                System.currentTimeMillis(), 4000, 0xFF00AA00);
        instance.push(n);
    }

    public static void notify(String title, String subtitle, int iconColor) {
        if (instance == null) return;
        Notification n = new Notification(title, subtitle,
                System.currentTimeMillis(), 4000, iconColor);
        instance.push(n);
    }

    private void push(Notification n) {
        queue.addLast(new ActiveNotification(n));
        while (queue.size() > MAX_VISIBLE) {
            queue.pollFirst();
        }
        playSound();
    }

    private void playSound() {
        long now = System.currentTimeMillis();
        if (now - lastSoundPlayedMs > 500) {
            UiHost.get().playToastSound();
            lastSoundPlayedMs = now;
        }
    }

    private void render(Gfx graphics, Float delta) {

        // Update animations
        Iterator<ActiveNotification> it = queue.iterator();
        while (it.hasNext()) {
            ActiveNotification an = it.next();

            if (!an.slidingOut && an.notification.isExpired()) {
                an.slidingOut = true;
            }

            float target = an.slidingOut ? PANEL_WIDTH + 10 : 0;
            an.slideX += (target - an.slideX) * 0.2f;

            if (an.slidingOut && an.slideX > PANEL_WIDTH) {
                it.remove();
                continue;
            }
            if (!an.slidingOut && an.slideX < 0.5f) {
                an.slideX = 0;
            }
        }

        // Render
        int screenWidth = graphics.guiWidth();
        int startY = MARGIN;

        for (ActiveNotification an : queue) {
            if (an.slidingOut && an.slideX > PANEL_WIDTH) continue;

            int panelLeft = screenWidth - MARGIN - PANEL_WIDTH + (int) an.slideX;
            renderPanel(graphics, an.notification, panelLeft, startY, screenWidth);
            startY += PANEL_HEIGHT + 2;
        }
    }

    private void renderPanel(Gfx graphics, Notification n, int panelLeft, int panelTop, int screenWidth) {

        int panelRight = Math.min(panelLeft + PANEL_WIDTH, screenWidth - MARGIN);
        int panelBottom = panelTop + PANEL_HEIGHT;

        graphics.fill(panelLeft, panelTop, panelRight, panelBottom, 0xE02D2D2D);
        graphics.fill(panelLeft, panelTop, panelLeft + 4, panelBottom, n.iconColor() | 0xFF000000);

        int titleX = panelLeft + 10;
        String title = n.title();
        if (title.length() > 22) {
            title = title.substring(0, 20) + "..";
        }
        graphics.text(title, false, titleX, panelTop + 3, 0xFFFFFFFF, true);

        if (n.subtitle() != null && !n.subtitle().isEmpty()) {
            String sub = n.subtitle();
            if (sub.length() > 28) {
                sub = sub.substring(0, 26) + "..";
            }
            graphics.text(sub, false, titleX, panelTop + 15, 0xFFAAAAAA, true);
        }
    }
}
