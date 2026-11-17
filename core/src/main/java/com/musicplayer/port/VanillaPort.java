package com.musicplayer.port;

import com.musicplayer.VanillaTrack;
import java.util.List;

/** Playback of the Minecraft soundtrack (backed by the game sound system). */
public interface VanillaPort {
    void setActive(boolean active);
    boolean isActive();
    void playTrack(VanillaTrack track);
    void stop();
    void togglePause();
    boolean isPaused();
    boolean isPlaying();
    VanillaTrack getCurrentTrack();
    long getElapsedMs();
    int getElapsedSeconds();
    void seek(float seconds);
    void setVolume(float volume);
    float getVolume();
    List<VanillaTrack> getAllTracks();
    void discoverTracks();
    void tick();
}
