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
import net.minecraft.sounds.SoundEvent;
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

    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void onTickHead(CallbackInfo ci) {
        try {
            boolean isPauseScreen = minecraft.gui.screen() instanceof net.minecraft.client.gui.screens.PauseScreen;

            if (isPauseScreen) {
                if (!wasPauseScreen) {
                    // Pause screen just opened — pause mod-managed tracks and stop vanilla
                    SoundtrackStateManager ssm = SoundtrackStateManager.getInstance();
                    ssm.pauseImmediate();
                    if (this.currentMusic != null) {
                        minecraft.getSoundManager().stop(this.currentMusic);
                        this.currentMusic = null;
                    }
                    this.nextSongDelay = Integer.MAX_VALUE;
                }
                wasPauseScreen = true;
                ci.cancel();
                return;
            }

            if (wasPauseScreen) {
                // Pause screen just closed — resume mod-managed tracks
                SoundtrackStateManager.getInstance().resumeImmediate();
                wasPauseScreen = false;
            }

            SoundtrackStateManager ssm = SoundtrackStateManager.getInstance();
            boolean isManual = ssm.isManualPlaying();
            boolean suppress = shouldSuppressAutoSchedule();

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
                if (music.sound().isBound()) {
                    SoundEvent event = music.sound().value();
                    String title = prettifyPath(event.location().getPath());
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
        SongSource source = MusicPlayerManager.getInstance().getCurrentSource();
        if (source == SongSource.USER) return true;
        SoundtrackStateManager ssm = SoundtrackStateManager.getInstance();
        if (ssm.isManualPlaying() || ssm.isManualPaused()) return true;
        if (ssm.isAutoPlaying() || ssm.isAutoPaused()) return true;
        return false;
    }

    private static String prettifyPath(String path) {
        String name = path.replace("music/", "").replace("record/", "").replace("_", " ");
        if (name.isEmpty()) return path;
        StringBuilder sb = new StringBuilder();
        boolean nextUpper = true;
        for (char c : name.toCharArray()) {
            if (c == ' ') { sb.append(c); nextUpper = true; }
            else if (nextUpper) { sb.append(Character.toUpperCase(c)); nextUpper = false; }
            else { sb.append(c); }
        }
        return sb.toString();
    }
}
