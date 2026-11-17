package com.musicplayer.port;

import java.nio.file.Path;

/** What the mod needs from the running game that is not tied to one Minecraft version. */
public interface HostPort {
    Path configDir();
    String modVersion();
    /** Runs on the game thread (render thread). */
    void runOnMainThread(Runnable r);
    /** Stops the game own background music. */
    void stopGameMusic();
    /** Shows a short system message (toast). */
    void notifySystem(String message);
    /** Slide-in animation of the mini player widget for a newly started track. */
    void miniPlayerSlideIn();
    /** Shows the now-playing line above the hotbar. */
    void showNowPlaying(com.musicplayer.SongInfo song);
    /** Opens a link after asking the user to confirm. */
    void openLinkConfirm(String url);
}
