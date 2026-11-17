package com.musicplayer.port;

import com.musicplayer.VanillaTrack;

/** The game-sound-system side of soundtrack playback (state, ducking, visualiser). */
public interface SoundtrackPort {
    void setOnTrackFinished(Runnable callback);
    boolean getVisualizerBars(float[] bars, int numBars);
    void setVolume(float volume);
    float getVolume();
    void clearAutoMusic();
    boolean isAutoPlaying();
    boolean isAutoPaused();
    void toggleAutoPause();
    boolean isPausePending();
    VanillaTrack getCurrentTrack();
    boolean isAnyPlaying();
    boolean isDucking();
    void duck(float volume, int durationMs, float restore);
    long getElapsedMs();
}
