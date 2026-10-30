package com.musicplayer.player;

import com.musicplayer.DebugLog;
import com.musicplayer.VanillaTrack;
import com.musicplayer.config.MusicPlayerConfig;
import com.musicplayer.hud.MiniPlayerHud;
import com.musicplayer.mixin.WeighedSoundEventsAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.Sound;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.client.sounds.WeighedSoundEvents;
import net.minecraft.client.sounds.Weighted;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import java.util.List;

public class SoundtrackStateManager {
    private static SoundtrackStateManager instance;

    private final Minecraft client;
    private final SoundThreadExecutor soundExecutor;
    private Runnable onTrackFinished;

    private float volume = 1.0f;

    private volatile long playbackSessionId;

    private volatile int seekGeneration;
    private static final java.util.concurrent.ExecutorService SEEK_EXECUTOR =
        java.util.concurrent.Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "musicplayer-seek");
            t.setDaemon(true);
            return t;
        });

    // --- Manual playback state ---
    private boolean manualPlaying;
    private boolean manualPaused;
    private SoundInstance manualInstance;
    private VanillaTrack manualTrack;

    private long accumulatedPlayTime;
    private long resumeStartMs;

    // --- Orphaned state watchdog ---
    private int orphanedStateTicks;

    // --- Completion state machine ---
    private static final int PHASE_NONE = 0;
    private static final int PHASE_DETECTING = 1;
    private static final int PHASE_CLEANUP = 2;
    private static final int PHASE_CALLBACK = 3;
    private static final int INACTIVE_THRESHOLD = 3;

    private int completionPhase = PHASE_NONE;
    private int inactiveTickCount;
    private boolean callbackExecuted;

    // Only start completion detection after sound has confirmed active at least once
    private boolean confirmedActive;

    // Grace period: give the sound engine time to start before marking inactive
    private static final long PLAY_GRACE_PERIOD_MS = 800;
    private long lastPlayAttemptTime;

    // Deferred advance for failed tracks
    private boolean pendingFailedAdvance;

    // Pending pause (waiting for fade-out to complete)
    private boolean pendingPause;

    // PCM cache for visualizer (vanilla tracks)
    private volatile VanillaPCMCache vanillaPCMCache;

    // Retry storm detection
    private static final int STORM_THRESHOLD = 3;
    private static final long STORM_WINDOW_MS = 5_000;
    private static final long DISABLE_DURATION_MS = 10_000;

    private int failedPlaybackAttempts;
    private Identifier lastFailedTrack;
    private long lastFailedTime;
    private long disablePlaybackUntil;

    // --- Saved state for unfocus/refocus ---
    private VanillaTrack savedManualTrack;
    private boolean restoreOnFocus;

    // --- Auto-captured state (from MusicManager mixin) ---
    private SoundInstance autoInstance;
    private VanillaTrack autoTrack;
    private long autoStartMs;
    private boolean autoPaused;
    private long autoPausedAtMs;

    private SoundtrackStateManager(Minecraft client) {
        this.client = client;
        this.soundExecutor = SoundThreadExecutor.getInstance();
    }

    public static synchronized SoundtrackStateManager getInstance() {
        if (instance == null) {
            instance = new SoundtrackStateManager(Minecraft.getInstance());
        }
        return instance;
    }

    public void setOnTrackFinished(Runnable callback) {
        this.onTrackFinished = callback;
    }

    // ─── Manual playback ───

    public void playTrack(VanillaTrack track) {
        try {
            if (disablePlaybackUntil > monotonicTimeMs()) {
                DebugLog.info("playTrack: disabled until %d", disablePlaybackUntil);
                return;
            }

            if (SoundtrackRegistry.getInstance().isVariantFailed(track.resolvedSoundId())) {
                DebugLog.info("playTrack: variant %s is marked failed, advancing", track.resolvedSoundId());
                pendingFailedAdvance = true;
                return;
            }

            DebugLog.info("playTrack: \"%s\" by %s (event=%s variant=%s)",
                track.title(), track.artist(), track.soundIdentifier(), track.resolvedSoundId());

            stopManual();

            // Always stop MusicManager directly to prevent environmental music overlap
            client.getMusicManager().stopPlaying();

            if (autoInstance != null) {
                try {
                    client.getSoundManager().stop(autoInstance);
                } catch (Exception e) {
                    // ignore
                }
            }
            clearAutoMusic();

            playbackSessionId++;
            manualInstance = createTrackInstance(track);
            if (manualInstance == null) {
                DebugLog.info("playTrack: createTrackInstance returned null for \"%s\"", track.title());
                handleFailedTrack(track);
                return;
            }

            DebugLog.info("playTrack: created instance type=%s", manualInstance.getClass().getSimpleName());

            failedPlaybackAttempts = 0;
            lastFailedTrack = null;

            manualPlaying = true;
            manualPaused = false;
            accumulatedPlayTime = 0;
            resumeStartMs = monotonicTimeMs();
            manualTrack = track;

            resetCompletionState();
            confirmedActive = false;

            lastPlayAttemptTime = monotonicTimeMs();
            client.getSoundManager().play(manualInstance);
            DebugLog.info("playTrack: SoundManager.play() called, session=%d", playbackSessionId);

            // Start decoding PCM for visualizer (async)
            loadPCMCache(track);

            com.musicplayer.hud.MiniPlayerHud.triggerSlideIn();
        } catch (Throwable t) {
            DebugLog.info("playTrack: caught %s: %s", t.getClass().getSimpleName(), t.getMessage());
            t.printStackTrace();
            manualPlaying = false;
            manualInstance = null;
            resetCompletionState();
            if (track != null) handleFailedTrack(track);
        }
    }

    private void handleFailedTrack(VanillaTrack track) {
        Identifier id = track.resolvedSoundId() != null ? track.resolvedSoundId() : track.soundIdentifier();
        DebugLog.info("handleFailedTrack: variant=%s attempt=%d", id, failedPlaybackAttempts + 1);
        lastPlayAttemptTime = 0;
        SoundtrackRegistry.getInstance().markVariantFailed(track.resolvedSoundId());

        long now = monotonicTimeMs();
        if (id.equals(lastFailedTrack) && (now - lastFailedTime) < STORM_WINDOW_MS) {
            failedPlaybackAttempts++;
        } else {
            failedPlaybackAttempts = 1;
        }
        lastFailedTrack = id;
        lastFailedTime = now;

        if (failedPlaybackAttempts >= STORM_THRESHOLD) {
            disablePlaybackUntil = now + DISABLE_DURATION_MS;
            DebugLog.info("handleFailedTrack: storm threshold reached, disabling until %d", disablePlaybackUntil);
        }

        clearPCMCache();
        manualPlaying = false;
        manualInstance = null;
        manualTrack = null;
        manualPaused = false;
        accumulatedPlayTime = 0;
        resumeStartMs = 0;
        resetCompletionState();
        pendingFailedAdvance = true;
    }

    // ─── PCM cache (audio visualizer) ───

    private void loadPCMCache(VanillaTrack track) {
        clearPCMCache();
        if (track.resolvedSoundId() == null) return;
        String oggPath = "sounds/" + track.resolvedSoundId().getPath() + ".ogg";
        Identifier oggId = Identifier.fromNamespaceAndPath(track.resolvedSoundId().getNamespace(), oggPath);
        final int token = pcmLoadToken;
        Thread t = new Thread(() -> {
            VanillaPCMCache decoded = VanillaPCMCache.decode(oggId);
            if (token == pcmLoadToken) vanillaPCMCache = decoded;
        }, "musicplayer-pcm-loader");
        t.setDaemon(true);
        t.start();
    }

    private volatile int pcmLoadToken;

    private void clearPCMCache() {
        pcmLoadToken++;
        vanillaPCMCache = null;
    }

    /**
     * Fill visualizer bars from the PCM cache (vanilla tracks).
     * Returns true if data was written.
     */
    public boolean getVisualizerBars(float[] bars, int numBars) {
        VanillaPCMCache cache = vanillaPCMCache;
        if (cache == null) return false;
        cache.getBars(bars, numBars, getElapsedMs());
        return true;
    }

    public void stopManual() {
        clearPCMCache();
        if (manualInstance != null) {
            try {
                client.getSoundManager().stop(manualInstance);
            } catch (Exception e) {
                // ignore
            }
            manualInstance = null;
        }
        manualTrack = null;
        manualPlaying = false;
        manualPaused = false;
        accumulatedPlayTime = 0;
        resumeStartMs = 0;
        resetCompletionState();
    }

    /**
     * Stop everything on window unfocus. Uses SoundManager.stop() (official API)
     * rather than channel-level pause, which is more reliable across OS focus changes.
     * Saves the manual track so it can be restarted on refocus.
     */
    public void unfocus() {
        restoreOnFocus = manualPlaying && !manualPaused && manualTrack != null;
        if (restoreOnFocus) {
            savedManualTrack = manualTrack;
        }

        if (manualInstance != null) {
            try {
                client.getSoundManager().stop(manualInstance);
            } catch (Exception e) { /* ignore */ }
            manualInstance = null;
        }
        if (autoInstance != null) {
            try {
                client.getSoundManager().stop(autoInstance);
            } catch (Exception e) { /* ignore */ }
            clearAutoMusic();
        }

        clearPCMCache();
        manualTrack = null;
        manualPlaying = false;
        manualPaused = false;
        accumulatedPlayTime = 0;
        resumeStartMs = 0;
        resetCompletionState();
        pendingPause = false;
        fading = false;
    }

    /**
     * Restore playback on window refocus. Restarts the saved manual track
     * from the beginning only if it was actively playing (not user-paused) before unfocus.
     */
    public void refocus() {
        if (restoreOnFocus && savedManualTrack != null) {
            playTrack(savedManualTrack);
        }
        restoreOnFocus = false;
        savedManualTrack = null;
    }

    // ─── Real pause/resume (via SoundThreadExecutor) ───

    public void togglePause() {
        if (manualInstance == null || manualTrack == null) return;
        if (manualPaused) {
            resumeManual();
        } else {
            pauseManual();
        }
    }

    /**
     * Pause playback immediately without fade. Used by unfocus handler.
     */
    public void pauseImmediate() {
        if (manualPlaying && manualInstance != null && !manualPaused && !pendingPause) {
            long elapsed = accumulatedPlayTime + (monotonicTimeMs() - resumeStartMs);
            accumulatedPlayTime = elapsed;
            pendingPause = false;
            fading = false;
            soundExecutor.pause(manualInstance);
            manualPaused = true;
        }
        if (autoInstance != null && !autoPaused) {
            autoPausedAtMs = monotonicTimeMs() - autoStartMs;
            soundExecutor.pause(autoInstance);
            autoPaused = true;
        }
    }

    /**
     * Resume playback immediately without fade. Mirrors pauseImmediate(). Used by refocus handler.
     */
    public void resumeImmediate() {
        if (manualInstance != null && manualPaused) {
            soundExecutor.resume(manualInstance, volume);
            resumeStartMs = monotonicTimeMs();
            manualPaused = false;
        }
        if (autoInstance != null && autoPaused) {
            soundExecutor.resume(autoInstance, volume);
            autoStartMs = monotonicTimeMs() - autoPausedAtMs;
            autoPaused = false;
        }
    }

    private void pauseManual() {
        if (manualPaused || pendingPause) return;
        MusicPlayerConfig cfg = MusicPlayerConfig.getInstance();
        long fadeMs = cfg.getFadeDurationMs();
        if (cfg.isFadeOnPause() && fadeMs > 0) {
            pendingPause = true;
            startFade(0, fadeMs);
        } else {
            long elapsed = accumulatedPlayTime + (monotonicTimeMs() - resumeStartMs);
            accumulatedPlayTime = elapsed;
            soundExecutor.pause(manualInstance);
            manualPaused = true;
        }
    }

    private void resumeManual() {
        if (!manualPaused || manualInstance == null) return;
        MusicPlayerConfig cfg = MusicPlayerConfig.getInstance();
        long fadeMs = cfg.getFadeDurationMs();
        soundExecutor.resume(manualInstance, 0);
        resumeStartMs = monotonicTimeMs();
        manualPaused = false;
        if (cfg.isFadeOnPause() && fadeMs > 0) {
            soundExecutor.setVolume(manualInstance, 0);
            fading = true;
            fadeStartMs = monotonicTimeMs();
            fadeDurationMs = Math.max(1, fadeMs);
            fadeStartVol = 0;
            fadeTargetVol = volume;
        } else {
            soundExecutor.setVolume(manualInstance, volume);
        }
    }

    // ─── Volume ───

    public void setVolume(float vol) {
        this.volume = Math.max(0, Math.min(1, vol));
        if (!manualPaused && manualInstance != null) {
            soundExecutor.setVolume(manualInstance, volume);
        }
    }

    public float getVolume() { return volume; }

    // ─── Auto-captured state (called from mixin) ───

    public void captureAutoMusic(net.minecraft.sounds.Music music, SoundInstance instance) {
        if (manualPlaying) return;
        if (music == null || instance == null) return;
        var holder = music.sound();
        if (holder == null || !holder.isBound()) return;
        var soundEvent = holder.value();
        if (soundEvent == null) return;
        this.autoInstance = instance;
        Identifier id = soundEvent.location();
        this.autoTrack = SoundtrackRegistry.getInstance().getTrack(id);
        this.autoStartMs = monotonicTimeMs();
        this.autoPaused = false;
        this.autoPausedAtMs = 0;
        DebugLog.info("captureAutoMusic: event=%s track=%s", id,
            autoTrack != null ? autoTrack.title() : "null");
    }

    public void clearAutoMusic() {
        if (autoTrack != null) {
            DebugLog.info("clearAutoMusic: clearing \"%s\"", autoTrack.title());
        }
        this.autoInstance = null;
        this.autoTrack = null;
        this.autoStartMs = 0;
        this.autoPaused = false;
        this.autoPausedAtMs = 0;
    }

    public void captureAutoAsManual() {
        if (autoInstance == null || autoTrack == null) return;

        DebugLog.info("captureAutoAsManual: capturing \"%s\"", autoTrack.title());
        SoundInstance oldAuto = autoInstance;
        VanillaTrack oldTrack = autoTrack;
        clearAutoMusic();

        playbackSessionId++;
        manualInstance = createTrackInstance(oldTrack);
        if (manualInstance == null) {
            DebugLog.info("captureAutoAsManual: createTrackInstance returned null");
            handleFailedTrack(oldTrack);
            return;
        }

        failedPlaybackAttempts = 0;
        lastFailedTrack = null;

        manualTrack = oldTrack;
        manualPlaying = true;
        manualPaused = false;
        accumulatedPlayTime = 0;
        resumeStartMs = monotonicTimeMs();
        resetCompletionState();

        client.getSoundManager().play(manualInstance);
        DebugLog.info("captureAutoAsManual: playing \"%s\" via %s", oldTrack.title(),
            manualInstance.getClass().getSimpleName());
    }

    /**
     * Seek the current manual track to the given time in seconds.
     * Decodes the OGG from the seek position and replaces the channel's stream.
     * Runs the OGG decode on a background thread to avoid blocking the game thread.
     */
    public void seekManual(float seconds) {
        if (!manualPlaying || manualTrack == null || manualInstance == null) return;
        Identifier variantId = manualTrack.resolvedSoundId();
        if (variantId == null) return;

        String oggPath = "sounds/" + variantId.getPath() + ".ogg";
        Identifier oggId = Identifier.fromNamespaceAndPath(variantId.getNamespace(), oggPath);

        DebugLog.info("seekManual: track=\"%s\" seekTo=%.1fs variant=%s",
            manualTrack.title(), seconds, variantId);

        final SoundInstance targetInstance = manualInstance;
        final long session = playbackSessionId;
        final int generation = ++seekGeneration;

        SEEK_EXECUTOR.execute(() -> {
            // Dragging the progress bar queues many seeks; only the newest is worth decoding.
            if (generation != seekGeneration) return;
            try {
                var res = FullPcmDecoder.decodeSeeked(oggId, seconds);
                if (res == null) {
                    DebugLog.info("seekManual: decodeSeeked returned null for %s", oggId);
                    return;
                }
                // Drop the result if a newer seek arrived or the track changed while decoding.
                if (generation != seekGeneration || session != playbackSessionId
                        || targetInstance != manualInstance) return;
                PcmStream stream = new PcmStream(res.pcmData(), res.format());
                boolean wasPaused = manualPaused;
                soundExecutor.seek(targetInstance, stream, !wasPaused);
                accumulatedPlayTime = (long)(seconds * 1000);
                resumeStartMs = monotonicTimeMs();
                if (wasPaused) manualPaused = true;
                DebugLog.info("seekManual: seek complete to %.1fs", seconds);
            } catch (Exception e) {
                DebugLog.info("seekManual: error: %s", e.getMessage());
            }
        });
    }

    // ─── Auto music control ───

    public boolean isAutoPlaying() {
        if (autoInstance == null) return false;
        try {
            return client.getSoundManager().isActive(autoInstance);
        } catch (Exception e) {
            return false;
        }
    }

    public boolean isAutoPaused() {
        return autoPaused;
    }

    public void toggleAutoPause() {
        if (autoInstance == null) return;
        if (autoPaused) {
            resumeAuto();
        } else {
            pauseAuto();
        }
    }

    private void pauseAuto() {
        if (autoPaused || autoInstance == null) return;
        autoPausedAtMs = monotonicTimeMs() - autoStartMs;
        soundExecutor.pause(autoInstance);
        autoPaused = true;
    }

    private void resumeAuto() {
        if (!autoPaused || autoInstance == null) return;
        soundExecutor.resume(autoInstance, volume);
        autoStartMs = monotonicTimeMs() - autoPausedAtMs;
        autoPaused = false;
    }

    // ─── Status ───

    public boolean isManualPlaying() { return manualPlaying && !manualPaused; }
    public boolean isManualPaused() { return manualPaused; }
    /** True while a pause was requested and the fade-out is still running. */
    public boolean isPausePending() { return pendingPause; }

    public boolean isAnyPlaying() {
        return isManualPlaying() || isAutoPlaying();
    }

    public boolean isAnyPaused() {
        return manualPaused || autoPaused;
    }

    public VanillaTrack getCurrentTrack() {
        if (manualTrack != null) return manualTrack;
        return autoTrack;
    }

    public SoundInstance getCurrentInstance() {
        if (manualInstance != null) return manualInstance;
        return autoInstance;
    }

    public long getElapsedMs() {
        if (manualTrack != null) {
            if (manualPaused) return accumulatedPlayTime;
            if (!manualPlaying) return 0;
            return accumulatedPlayTime + (monotonicTimeMs() - resumeStartMs);
        }
        if (autoTrack != null) {
            if (autoPaused) return autoPausedAtMs;
            return monotonicTimeMs() - autoStartMs;
        }
        return 0;
    }

    public long getPlaybackSessionId() {
        return playbackSessionId;
    }

    // ─── Tick — Three-phase completion with multi-tick detection ───

    public void tick() {
        try {
            tickFade();

            // Deferred advance for failed tracks (prevents recursive playTrack)
            if (pendingFailedAdvance) {
                pendingFailedAdvance = false;
                if (onTrackFinished != null) {
                    try {
                        onTrackFinished.run();
                    } catch (Throwable t) {
                        // ignore
                    }
                }
                return;
            }

            // Phase 1: detect natural completion
            if (completionPhase == PHASE_NONE || completionPhase == PHASE_DETECTING) {
                if (manualPlaying && manualInstance != null && !manualPaused) {
                    boolean isActive;
                    try {
                        isActive = client.getSoundManager().isActive(manualInstance);
                    } catch (Exception e) {
                        isActive = false;
                    }
                    boolean hasChannel = soundExecutor.isChannelActive(manualInstance);
                    boolean active = isActive || hasChannel;

                    if (active && !confirmedActive) {
                        // First tick the engine has confirmed this instance active —
                        // apply the user's stored volume setting
                        confirmedActive = true;
                        if (volume < 1.0f) {
                            soundExecutor.setVolume(manualInstance, volume);
                        }
                    } else if (active) {
                        confirmedActive = true;
                    }

                    if (!confirmedActive) {
                        // Sound hasn't started yet — don't mark as complete
                        inactiveTickCount = 0;
                        completionPhase = PHASE_NONE;
                    } else if (monotonicTimeMs() - lastPlayAttemptTime < PLAY_GRACE_PERIOD_MS) {
                        inactiveTickCount = 0;
                        completionPhase = PHASE_NONE;
                    } else {
                        if (!active) {
                            inactiveTickCount++;
                            completionPhase = PHASE_DETECTING;

                            if (inactiveTickCount >= INACTIVE_THRESHOLD) {
                                DebugLog.info("tick: track completed naturally (inactive for %d ticks, active=%s confirmed=%s)",
                                    INACTIVE_THRESHOLD, active, confirmedActive);
                                completionPhase = PHASE_CLEANUP;
                                inactiveTickCount = 0;
                            }
                        } else {
                            inactiveTickCount = 0;
                            completionPhase = PHASE_NONE;
                        }
                    }
                } else {
                    inactiveTickCount = 0;
                    if (completionPhase == PHASE_DETECTING) {
                        completionPhase = PHASE_NONE;
                    }
                }
            }

            // Phase 2: clear references
            if (completionPhase == PHASE_CLEANUP) {
                DebugLog.info("tick: PHASE_CLEANUP clearing manual state");
                manualTrack = null;
                manualInstance = null;
        manualPlaying = false;
        manualPaused = false;
        lastPlayAttemptTime = 0;
                accumulatedPlayTime = 0;
                resumeStartMs = 0;
                completionPhase = PHASE_CALLBACK;
            }

            // Phase 3: invoke callback
            if (completionPhase == PHASE_CALLBACK) {
                DebugLog.info("tick: PHASE_CALLBACK invoking onTrackFinished");
                completionPhase = PHASE_NONE;
                if (onTrackFinished != null && !callbackExecuted) {
                    callbackExecuted = true;
                    try {
                        onTrackFinished.run();
                    } catch (Throwable t) {
                        DebugLog.info("tick: onTrackFinished threw %s: %s", t.getClass().getSimpleName(), t.getMessage());
                    }
                }
            }

            // Orphaned state watchdog
            if (manualPlaying && manualInstance == null) {
                orphanedStateTicks++;
                if (orphanedStateTicks > 5) {
                    DebugLog.info("tick: orphaned state detected, resetting");
                    resetPlaybackState();
                }
            } else {
                orphanedStateTicks = 0;
            }

            // Auto completion
            if (autoInstance != null && autoTrack != null && !manualPlaying) {
                boolean active;
                try {
                    active = client.getSoundManager().isActive(autoInstance);
                } catch (Exception e) {
                    active = false;
                }
                if (!active && !soundExecutor.isChannelActive(autoInstance)) {
                    DebugLog.info("tick: auto track completed: %s", autoTrack.title());
                    clearAutoMusic();
                }
            }
        } catch (Throwable t) {
            DebugLog.info("tick: caught %s: %s", t.getClass().getSimpleName(), t.getMessage());
        }
    }

    private static long monotonicTimeMs() {
        return System.nanoTime() / 1_000_000L;
    }

    private void resetPlaybackState() {
        clearPCMCache();
        manualPlaying = false;
        manualPaused = false;
        pendingPause = false;
        manualInstance = null;
        manualTrack = null;
        accumulatedPlayTime = 0;
        resumeStartMs = 0;
        resetCompletionState();
    }

    private void resetCompletionState() {
        completionPhase = PHASE_NONE;
        inactiveTickCount = 0;
        callbackExecuted = false;
        confirmedActive = false;
    }

    // ─── Fade / Duck ───

    private boolean fading;
    private long fadeStartMs;
    private long fadeDurationMs;
    private float fadeStartVol;
    private float fadeTargetVol;

    private boolean ducking;
    private float duckRestoreVolume;

    public void startFade(float targetVolume, long durationMs) {
        if (!isAnyPlaying()) return;
        fadeStartVol = getEffectiveVolume();
        fadeTargetVol = targetVolume;
        fadeDurationMs = Math.max(1, durationMs);
        fadeStartMs = monotonicTimeMs();
        fading = true;
    }

    public void duck(float duckVolume, long fadeLengthMs, float restoreVolume) {
        if (!isAnyPlaying()) return;
        ducking = true;
        duckRestoreVolume = restoreVolume;
        startFade(duckVolume, fadeLengthMs);
    }

    public boolean isDucking() { return ducking || fading; }
    public boolean hasFading() { return fading; }

    private float getEffectiveVolume() {
        SoundInstance target = manualInstance != null ? manualInstance : autoInstance;
        if (target == null) return volume;
        return volume * client.options.getSoundSourceVolume(target.getSource());
    }

    public void tickFade() {
        if (!fading) return;
        long elapsed = monotonicTimeMs() - fadeStartMs;
        float t = Math.min(1f, (float) elapsed / fadeDurationMs);
        float blended = fadeStartVol + (fadeTargetVol - fadeStartVol) * t;

        SoundInstance target = manualInstance != null ? manualInstance : autoInstance;
        if (target != null) {
            soundExecutor.setVolume(target, blended);
        }

        if (t >= 1f) {
            fading = false;
            if (pendingPause) {
                pendingPause = false;
                if (manualPlaying && manualInstance != null) {
                    long playedMs = accumulatedPlayTime + (monotonicTimeMs() - resumeStartMs);
                    accumulatedPlayTime = playedMs;
                    soundExecutor.pause(manualInstance);
                    manualPaused = true;
                }
            } else if (ducking) {
                ducking = false;
                startFade(duckRestoreVolume, fadeDurationMs);
            } else if (fadeTargetVol <= 0.001f) {
                if (manualPlaying) stopManual();
                else clearAutoMusic();
            }
        }
    }

    // ─── Helpers ───

    /**
     * Creates a SoundInstance for the given track.  If the track has a
     * resolved variant ID, a FixedSoundInstance is used (bypasses the
     * random-weighted variant selection), guaranteeing the *exact* song
     * that was clicked.  Otherwise falls back to the event-based path.
     */
    private static SoundInstance createTrackInstance(VanillaTrack track) {
        if (track == null) return null;
        if (track.resolvedSoundId() != null
            && SoundtrackRegistry.getInstance().hasMultipleVariants(track.soundIdentifier())) {
            return new FixedSoundInstance(track.soundIdentifier(), track.resolvedSoundId());
        }
        return createMusicInstance(track.soundIdentifier());
    }

    /**
     * Creates a SimpleSoundInstance only if the SoundEvent resolves to
     * at least one concrete Sound variant AND the underlying OGG file
     * exists on disk. Returns null if anything is missing or empty.
     */
    private static SimpleSoundInstance createMusicInstance(Identifier soundId) {
        return createMusicInstance(soundId, 0);
    }

    private static final int MAX_VALIDATION_DEPTH = 10;

    private static SimpleSoundInstance createMusicInstance(Identifier soundId, int depth) {
        if (depth > MAX_VALIDATION_DEPTH) return null;
        try {
            Minecraft mc = Minecraft.getInstance();
            SoundManager sm = mc.getSoundManager();
            WeighedSoundEvents wse = sm.getSoundEvent(soundId);
            if (wse == null || wse == SoundManager.INTENTIONALLY_EMPTY_SOUND_EVENT) return null;

            // The SoundEngine picks ONE variant at play time. We just need
            // at least one resolvable variant — broken variants with weight 0
            // will never be picked.
            List<Weighted<Sound>> variants = ((WeighedSoundEventsAccessor) wse).musicplayer$getList();
            if (variants == null || variants.isEmpty()) return null;

            boolean anyValid = false;
            for (Weighted<Sound> entry : variants) {
                if (entry.getWeight() <= 0) continue;
                if (isCreateEntryValid(entry, mc, depth)) {
                    anyValid = true;
                    break;
                }
            }
            if (!anyValid) return null;

            return new SimpleSoundInstance(
                soundId,
                SoundSource.MUSIC,
                1.0f,
                1.0f,
                RandomSource.create(),
                false,
                0,
                SoundInstance.Attenuation.NONE,
                0.0d,
                0.0d,
                0.0d,
                true
            );
        } catch (Exception e) {
            return null;
        }
    }

    private static boolean isCreateEntryValid(Weighted<Sound> entry, Minecraft mc, int depth) {
        try {
            if (entry instanceof WeighedSoundEvents nestedWse) {
                List<Weighted<Sound>> nestedList = ((WeighedSoundEventsAccessor) nestedWse).musicplayer$getList();
                if (nestedList == null || nestedList.isEmpty()) return false;
                boolean anyValid = false;
                for (Weighted<Sound> nestedEntry : nestedList) {
                    if (nestedEntry.getWeight() <= 0) continue;
                    if (isCreateEntryValid(nestedEntry, mc, depth + 1)) {
                        anyValid = true;
                        break;
                    }
                }
                return anyValid;
            }

            Sound sound = entry.getSound(RandomSource.create());
            if (sound == null
                || sound == SoundManager.EMPTY_SOUND
                || sound == SoundManager.INTENTIONALLY_EMPTY_SOUND) return false;

            if (sound.getType() == Sound.Type.SOUND_EVENT) {
                return createMusicInstance(sound.getLocation(), depth + 1) != null;
            }

            Identifier loc = sound.getLocation();
            Identifier oggRes = Identifier.fromNamespaceAndPath(loc.getNamespace(),
                "sounds/" + loc.getPath() + ".ogg");
            var resource = mc.getResourceManager().getResource(oggRes);
            return resource.isPresent();
        } catch (Exception e) {
            return false;
        }
    }
}
