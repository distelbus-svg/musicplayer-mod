package com.musicplayer;

import com.musicplayer.player.SoundtrackRegistry;
import com.musicplayer.player.SoundtrackStateManager;
import net.minecraft.client.Minecraft;
import net.minecraft.sounds.Music;
import net.minecraft.sounds.SoundEvent;
import java.util.List;

public class VanillaMusicPlayer {

    private static boolean active = false;

    private VanillaMusicPlayer() {}

    private static final VanillaMusicPlayer INSTANCE = new VanillaMusicPlayer();

    public static VanillaMusicPlayer getInstance() { return INSTANCE; }
    public static boolean isActive() { return active; }
    public static void setActive(boolean v) { active = v; }

    // ─── Playback (delegates to SoundtrackStateManager) ───

    public void playTrack(VanillaTrack track) {
        SoundtrackStateManager.getInstance().playTrack(track);
    }

    public void stop() {
        SoundtrackStateManager.getInstance().stopManual();
    }

    public void togglePause() {
        SoundtrackStateManager.getInstance().togglePause();
    }

    public boolean isPaused() { return SoundtrackStateManager.getInstance().isManualPaused(); }
    public boolean isPlaying() { return SoundtrackStateManager.getInstance().isManualPlaying(); }

    public VanillaTrack getCurrentTrack() { return SoundtrackStateManager.getInstance().getCurrentTrack(); }

    public long getElapsedMs() { return SoundtrackStateManager.getInstance().getElapsedMs(); }
    public int getElapsedSeconds() { return (int)(getElapsedMs() / 1000); }

    public void seek(float seconds) {
        SoundtrackStateManager.getInstance().seekManual(seconds);
    }

    public void setVolume(float vol) { SoundtrackStateManager.getInstance().setVolume(vol); }
    public float getVolume() { return SoundtrackStateManager.getInstance().getVolume(); }

    // ─── Track discovery ───

    public List<VanillaTrack> getAllTracks() {
        return SoundtrackRegistry.getInstance().getAllTracks();
    }

    public void discoverTracks() {
        SoundtrackRegistry.getInstance().discoverTracks();
    }

    // ─── Tick ───

    public void tick() {
        SoundtrackStateManager.getInstance().tick();
    }
}
