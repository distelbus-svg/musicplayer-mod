package com.musicplayer;

import com.musicplayer.hud.MiniPlayerHud;
import com.musicplayer.hud.NotificationManager;
import com.musicplayer.hud.NowPlayingHud;
import com.musicplayer.player.SoundtrackRegistry;
import com.musicplayer.player.SoundtrackStateManager;
import com.musicplayer.port.ArtPort;
import com.musicplayer.port.HostPort;
import com.musicplayer.port.Ports;
import com.musicplayer.port.SoundtrackPort;
import com.musicplayer.port.TrackRegistryPort;
import com.musicplayer.port.VanillaPort;
import com.mojang.blaze3d.platform.NativeImage;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConfirmLinkScreen;
import net.minecraft.resources.Identifier;

import java.io.File;
import java.net.URI;
import java.nio.file.Path;
import java.util.List;

/**
 * Version-specific side of the shared code: implements the ports in {@code core}.
 * Looked up by {@link Ports} on first use. Everything here must stay thin and lazy:
 * no singleton may be touched while installing (the singletons load config, which needs the host).
 */
public final class McBootstrap {
    private McBootstrap() {}

    public static void install() {
        com.musicplayer.ui.UiHost.install(new com.musicplayer.host.McUiHost());
        Ports.install(new Host(), new Vanilla(), new Soundtrack(), new Registry(), new Art());
    }

    private static String str(Identifier id) {
        return id == null ? null : id.toString();
    }

    private static final class Host implements HostPort {
        @Override public Path configDir() { return FabricLoader.getInstance().getConfigDir(); }
        @Override public String modVersion() {
            return FabricLoader.getInstance().getModContainer("musicplayer")
                    .map(c -> c.getMetadata().getVersion().getFriendlyString()).orElse("unknown");
        }
        @Override public void runOnMainThread(Runnable r) { Minecraft.getInstance().execute(r); }
        @Override public void stopGameMusic() { Minecraft.getInstance().getMusicManager().stopPlaying(); }
        @Override public void notifySystem(String message) { NotificationManager.showSystem(message); }
        @Override public void miniPlayerSlideIn() { MiniPlayerHud.triggerSlideIn(); }
        @Override public void showNowPlaying(SongInfo song) { NowPlayingHud.show(song); }
        @Override public void openLinkConfirm(String url) {
            Minecraft mc = Minecraft.getInstance();
            ConfirmLinkScreen.confirmLinkNow(mc.gui.screen(), URI.create(url));
        }
    }

    private static final class Vanilla implements VanillaPort {
        private VanillaMusicPlayer v() { return VanillaMusicPlayer.getInstance(); }
        @Override public void setActive(boolean active) { VanillaMusicPlayer.setActive(active); }
        @Override public boolean isActive() { return VanillaMusicPlayer.isActive(); }
        @Override public void playTrack(VanillaTrack track) { v().playTrack(track); }
        @Override public void stop() { v().stop(); }
        @Override public void togglePause() { v().togglePause(); }
        @Override public boolean isPaused() { return v().isPaused(); }
        @Override public boolean isPlaying() { return v().isPlaying(); }
        @Override public VanillaTrack getCurrentTrack() { return v().getCurrentTrack(); }
        @Override public long getElapsedMs() { return v().getElapsedMs(); }
        @Override public int getElapsedSeconds() { return v().getElapsedSeconds(); }
        @Override public void seek(float seconds) { v().seek(seconds); }
        @Override public void setVolume(float volume) { v().setVolume(volume); }
        @Override public float getVolume() { return v().getVolume(); }
        @Override public List<VanillaTrack> getAllTracks() { return v().getAllTracks(); }
        @Override public void discoverTracks() { v().discoverTracks(); }
        @Override public void tick() { v().tick(); }
    }

    private static final class Soundtrack implements SoundtrackPort {
        private SoundtrackStateManager s() { return SoundtrackStateManager.getInstance(); }
        @Override public void setOnTrackFinished(Runnable callback) { s().setOnTrackFinished(callback); }
        @Override public boolean getVisualizerBars(float[] bars, int numBars) { return s().getVisualizerBars(bars, numBars); }
        @Override public void setVolume(float volume) { s().setVolume(volume); }
        @Override public float getVolume() { return s().getVolume(); }
        @Override public void clearAutoMusic() { s().clearAutoMusic(); }
        @Override public boolean isAutoPlaying() { return s().isAutoPlaying(); }
        @Override public boolean isAutoPaused() { return s().isAutoPaused(); }
        @Override public void toggleAutoPause() { s().toggleAutoPause(); }
        @Override public boolean isPausePending() { return s().isPausePending(); }
        @Override public VanillaTrack getCurrentTrack() { return s().getCurrentTrack(); }
        @Override public boolean isAnyPlaying() { return s().isAnyPlaying(); }
        @Override public boolean isDucking() { return s().isDucking(); }
        @Override public void duck(float volume, int durationMs, float restore) { s().duck(volume, durationMs, restore); }
        @Override public long getElapsedMs() { return s().getElapsedMs(); }
    }

    private static final class Registry implements TrackRegistryPort {
        private SoundtrackRegistry r() { return SoundtrackRegistry.getInstance(); }
        @Override public List<VanillaTrack> discoverTracks() { return r().discoverTracks(); }
        @Override public void resetDiscovery() { r().resetDiscovery(); }
        @Override public int trackCount() { return r().trackCount(); }
        @Override public List<VanillaTrack> getAllTracks() { return r().getAllTracks(); }
    }

    private static final class Art implements ArtPort {
        private AlbumArtManager a() { return AlbumArtManager.getInstance(); }
        @Override public String defaultArt() { return str(AlbumArtManager.DEFAULT_ALBUM_ART); }
        @Override public String albumArtId() { return str(AlbumArtManager.ALBUM_ART_ID); }
        @Override public String builtInArt(String albumKey) { return str(AlbumArtManager.getBuiltInArt(albumKey)); }
        @Override public String artForSong(String path, String albumKey) { return str(a().getArtForSong(path, albumKey)); }
        @Override public boolean hasArt() { return a().hasArt(); }
        @Override public void clear() { a().clear(); }
        @Override public void loadForSong(File songFile) { a().loadForSong(songFile); }
        @Override public void loadForRemote(String path) { a().loadForRemote(path); }
        @Override public void setCustomArt(String songPath, File image) { a().setCustomArt(songPath, image); }
        @Override public String registerTexture(String path, byte[] png) {
            try {
                NativeImage image = NativeImage.read(new java.io.ByteArrayInputStream(png));
                Identifier id = Ids.rl("musicplayer", path);
                DynamicTexture tex = new DynamicTexture(() -> id.toString(), image);
                Minecraft.getInstance().getTextureManager().register(id, tex);
                tex.upload();
                return id.toString();
            } catch (Exception e) {
                return null;
            }
        }
    }
}
