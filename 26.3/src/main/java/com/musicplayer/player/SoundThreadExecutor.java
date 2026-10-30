package com.musicplayer.player;

import com.musicplayer.mixin.SoundEngineAccessor;
import com.musicplayer.mixin.SoundManagerAccessor;
import com.musicplayer.DebugLog;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.ChannelAccess.ChannelHandle;
import net.minecraft.client.sounds.SoundEngine;
import net.minecraft.client.sounds.SoundManager;
import org.lwjgl.openal.AL10;
import java.util.Map;

public class SoundThreadExecutor {
    private static SoundThreadExecutor instance;
    private final Minecraft client;

    private SoundThreadExecutor(Minecraft client) {
        this.client = client;
    }

    public static synchronized SoundThreadExecutor getInstance() {
        if (instance == null) {
            instance = new SoundThreadExecutor(Minecraft.getInstance());
        }
        return instance;
    }

    /**
     * Schedule Channel::pause on the sound thread.
     * Safe: reads instanceToChannel from game thread, schedules on sound thread.
     */
    public void pause(SoundInstance instance) {
        ChannelHandle handle = getHandle(instance);
        if (handle != null) {
            handle.execute(ch -> {
                if (!ch.stopped()) {
                    ch.pause();
                }
            });
        }
    }

    /**
     * Schedule Channel::unpause + volume restore on the sound thread.
     */
    public void resume(SoundInstance instance, float volume) {
        ChannelHandle handle = getHandle(instance);
        if (handle != null) {
            handle.execute(ch -> {
                if (!ch.stopped()) {
                    ch.unpause();
                    ch.setVolume(volume);
                }
            });
        }
    }

    /**
     * Schedule volume change on the sound thread.
     */
    public void setVolume(SoundInstance instance, float volume) {
        ChannelHandle handle = getHandle(instance);
        if (handle != null) {
            handle.execute(ch -> {
                if (!ch.stopped()) {
                    ch.setVolume(volume);
                }
            });
        }
    }

    /**
     * Schedule a raw channel operation on the sound thread.
     * The consumer must handle stopped channels gracefully.
     * @param instance  the SoundInstance whose channel to operate on
     * @param operation consumer that receives the Channel on the sound thread
     */
    public void execute(SoundInstance instance, java.util.function.Consumer<com.mojang.blaze3d.audio.Channel> operation) {
        ChannelHandle handle = getHandle(instance);
        if (handle != null) {
            handle.execute(ch -> {
                if (!ch.stopped()) {
                    operation.accept(ch);
                }
            });
        }
    }

    /**
     * Seek the channel for the given instance — pause, unqueue all OpenAL buffers,
     * attach a new stream, and optionally resume playback.
     * @param resumePlayback if true, call ch.play() after attaching the new stream
     */
    public void seek(SoundInstance instance, net.minecraft.client.sounds.AudioStream newStream, boolean resumePlayback) {
        ChannelHandle handle = getHandle(instance);
        if (handle == null) return;
        handle.execute(ch -> {
            try {
                ch.pause();
                int source = ((com.musicplayer.mixin.ChannelSourceAccessor) ch).musicplayer$getSource();
                int queued = AL10.alGetSourcei(source, AL10.AL_BUFFERS_QUEUED);
                if (queued > 0) {
                    int[] bufs = new int[queued];
                    AL10.alSourceUnqueueBuffers(source, bufs);
                    AL10.alDeleteBuffers(bufs);
                }
                ch.attachBufferStream(newStream);
                if (resumePlayback) {
                    ch.play();
                }
            } catch (Exception e) {
                DebugLog.info("SoundThreadExecutor.seek: %s", e.getMessage());
            }
        });
    }

    /**
     * Check if the channel for this SoundInstance still exists and is not stopped.
     * Safe to call from game thread: reads instanceToChannel map (concurrent-friendly).
     * Returns false if handle is null or stopped (sound finished or cleaned up).
     */
    public boolean isChannelActive(SoundInstance instance) {
        return getHandle(instance) != null;
    }

    /**
     * Centralized safe access to instanceToChannel.
     * Only reads the map — no mutation. Returns null if anything is unavailable.
     */
    private ChannelHandle getHandle(SoundInstance instance) {
        if (instance == null) return null;
        try {
            SoundManager sm = client.getSoundManager();
            if (sm == null) return null;
            SoundEngine engine = ((SoundManagerAccessor) sm).musicplayer$getSoundEngine();
            if (engine == null) return null;
            Map<SoundInstance, ChannelHandle> map = ((SoundEngineAccessor) engine).musicplayer$getInstanceToChannel();
            if (map == null) return null;
            ChannelHandle handle = map.get(instance);
            if (handle == null || handle.isStopped()) return null;
            return handle;
        } catch (Exception e) {
            DebugLog.info("SoundThreadExecutor.getHandle: " + e.getMessage());
            return null;
        }
    }
}
