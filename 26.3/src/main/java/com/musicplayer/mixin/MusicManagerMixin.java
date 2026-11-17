package com.musicplayer.mixin;

import com.musicplayer.MusicPlayerManager;
import com.musicplayer.MusicPlayerManager.SongSource;
import com.musicplayer.config.MusicPlayerConfig;
import com.musicplayer.hud.MiniPlayerHud;
import com.musicplayer.player.SoundtrackStateManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.MusicManager;
import net.minecraft.sounds.Music;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MusicManager.class)
public class MusicManagerMixin {

    @Shadow @Final private Minecraft minecraft;
    @Shadow private SoundInstance currentMusic;
    @Shadow private int nextSongDelay;

    private boolean wasManualPlaying = false;
    private boolean wasSuppressing = false;
    private boolean wasPauseScreen = false;
    private boolean wasEnginePlaying = false;
    private boolean pausedTracksForScreen = false;
    private net.minecraft.client.resources.sounds.SoundInstance foreignPaused = null;

    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void onTickHead(CallbackInfo ci) {
        try {
            boolean isPauseScreen = minecraft.gui.screen() instanceof net.minecraft.client.gui.screens.PauseScreen;

            if (isPauseScreen) {
                if (!wasPauseScreen) {
                    // Pause screen just opened: pause what Melodix and the game are playing (only if the setting is on)
                    SoundtrackStateManager ssm = SoundtrackStateManager.getInstance();
                    MusicPlayerConfig cfg = MusicPlayerConfig.getInstance();
                    if (cfg.isPauseOnPauseScreen()) {
                        // remember whether we paused something, so a song the person paused themselves stays paused
                        pausedTracksForScreen = ssm.isManualPlaying() || (ssm.isAutoPlaying() && !ssm.isAutoPaused());
                        ssm.pauseImmediate();
                        MusicPlayerManager mpm = MusicPlayerManager.getInstance();
                        if (mpm.getCurrentSource() == SongSource.USER) {
                            var eng = mpm.getEngine();
                            if (eng.isActive() && !eng.isPaused()) {
                                eng.togglePause();
                                wasEnginePlaying = true;
                            }
                        }
                    }
                    this.nextSongDelay = Integer.MAX_VALUE;
                }
                if (MusicPlayerConfig.getInstance().isPauseOnPauseScreen()) {
                    SoundtrackStateManager ssm2 = SoundtrackStateManager.getInstance();
                    ssm2.enforcePause();
                    // the game's own track that Melodix doesn't track: pause it too
                    if (this.currentMusic != null && this.currentMusic != ssm2.getAutoInstance()
                            && !ssm2.isManualPlaying()) {
                        ssm2.pauseForeign(this.currentMusic);
                        foreignPaused = this.currentMusic;
                    }
                }
                wasPauseScreen = true;
                ci.cancel();
                return;
            }

            if (wasPauseScreen) {
                // Pause screen just closed — resume mod-managed tracks
                if (foreignPaused != null) {
                    SoundtrackStateManager.getInstance().resumeForeign(foreignPaused);
                    foreignPaused = null;
                }
                if (pausedTracksForScreen) {
                    pausedTracksForScreen = false;
                    SoundtrackStateManager.getInstance().resumeImmediate();
                }
                // Resume AudioEngine for USER source if we paused it
                if (wasEnginePlaying) {
                    wasEnginePlaying = false;
                    MusicPlayerManager mpm = MusicPlayerManager.getInstance();
                    if (mpm.getCurrentSource() == SongSource.USER) {
                        var eng = mpm.getEngine();
                        if (eng.isPaused()) {
                            eng.togglePause();
                        }
                    }
                }
                wasPauseScreen = false;
            }

            SoundtrackStateManager ssm = SoundtrackStateManager.getInstance();
            boolean isManual = ssm.isManualPlaying();
            boolean suppress = shouldSuppressAutoSchedule();

            // "Mute Minecraft music" was switched on (or Melodix started) while the game's own track was already
            // playing: stop it instead of letting it run to the end.
            if (suppress && this.currentMusic != null
                    && MusicPlayerManager.getInstance().getCurrentSource() == SongSource.USER) {
                minecraft.getSoundManager().stop(this.currentMusic);
                this.currentMusic = null;
                ssm.clearAutoMusic();
            }

            if (suppress) {
                if (!wasSuppressing) {
                    wasSuppressing = true;
                }
                this.nextSongDelay = Integer.MAX_VALUE;
            } else {
                if (wasSuppressing) {
                    wasSuppressing = false;
                }
                if (wasManualPlaying) {
                    this.nextSongDelay = 100;
                }
            }

            wasManualPlaying = isManual;
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }

    @Inject(method = "startPlaying", at = @At("TAIL"))
    private void onStartPlaying(Music music, CallbackInfo ci) {
        try {
            SoundtrackStateManager ssm = SoundtrackStateManager.getInstance();
            if (!ssm.isManualPlaying() && this.currentMusic != null) {
                ssm.captureAutoMusic(music, this.currentMusic);
                // the game started a track by itself while Melodix is idle: show it in the player so it can be controlled
                MusicPlayerManager mpm = MusicPlayerManager.getInstance();
                if (mpm.getCurrentSource() == SongSource.USER && !mpm.getEngine().isActive()) {
                    mpm.adoptAutoMusic();
                }
                if (music.sound().isBound()) {
                    MiniPlayerHud.triggerSlideIn();
                }
            }
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }

    @Inject(method = "stopPlaying()V", at = @At("HEAD"))
    private void onStopPlaying(CallbackInfo ci) {
        try {
            SoundtrackStateManager.getInstance().clearAutoMusic();
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }

    private static boolean shouldSuppressAutoSchedule() {
        SoundtrackStateManager ssm = SoundtrackStateManager.getInstance();
        // A Minecraft song chosen in Melodix is playing or paused: the game must not start another on top of it.
        if (ssm.isManualPlaying() || ssm.isManualPaused()) return true;

        MusicPlayerManager mpm = MusicPlayerManager.getInstance();
        if (mpm.getCurrentSource() == SongSource.USER) {
            // Own songs: silence the game's music only when asked to, and only while Melodix is really playing.
            // Otherwise the game's music plays by itself as usual.
            return MusicPlayerConfig.getInstance().isSuppressVanillaMusic() && mpm.getEngine().isActive();
        }
        return ssm.isAutoPlaying() || ssm.isAutoPaused();
    }
}
