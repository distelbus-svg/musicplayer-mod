package com.musicplayer;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import com.mojang.blaze3d.platform.InputConstants;
import org.lwjgl.glfw.GLFW;

/**
 * Music Player Mod — Client-side Fabric mod entry point.
 * Registers keybindings and initializes the music player manager.
 *
 * Keybindings (all rebindable in Controls menu):
 *   M          — Open the music player GUI
 *   Right Arrow — Next track
 *   Left Arrow  — Previous track
 *   P          — Play / Pause
 *   Up Arrow   — Volume up (+5%)
 *   Down Arrow — Volume down (-5%)
 */
public class MusicPlayerMod implements ClientModInitializer {

    public static final String MOD_ID = "musicplayer";

    public static KeyMapping getOpenGuiKey() { return openGuiKey; }
    private static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(
            Identifier.fromNamespaceAndPath(MOD_ID, "main")
    );

    // Keybindings
    private static KeyMapping openGuiKey;
    private static KeyMapping playPauseKey;
    private static KeyMapping nextTrackKey;
    private static KeyMapping prevTrackKey;
    private static KeyMapping volumeUpKey;
    private static KeyMapping volumeDownKey;
    private static KeyMapping customVolumeKey;

    @Override
    public void onInitializeClient() {
        DebugLog.info("Initializing Music Player Mod...");
        
        com.musicplayer.hud.NotificationManager.init();
        com.musicplayer.hud.NowPlayingHud.init();
        com.musicplayer.hud.AudioVisualizerHud.init();
        com.musicplayer.hud.MiniPlayerHud.init();

        // Register keybindings
        openGuiKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.musicplayer.open_gui",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_M,
                CATEGORY
        ));

        playPauseKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.musicplayer.play_pause",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_P,
                CATEGORY
        ));

        nextTrackKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.musicplayer.next_track",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_RIGHT,
                CATEGORY
        ));

        prevTrackKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.musicplayer.previous_track",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_LEFT,
                CATEGORY
        ));

        volumeUpKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.musicplayer.volume_up",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_UP,
                CATEGORY
        ));

        volumeDownKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.musicplayer.volume_down",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_DOWN,
                CATEGORY
        ));

        customVolumeKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.musicplayer.custom_volume",
                InputConstants.Type.KEYSYM,
                InputConstants.UNKNOWN.getValue(),
                CATEGORY
        ));

        // Initialize manager (loads config + playlist)
        MusicPlayerManager manager = MusicPlayerManager.getInstance();
        manager.reloadLibraryFromDisk();

        // Register GLFW window focus callback once window is available (deferred to first tick)
        final boolean[] focusCallbackRegistered = {false};
        final boolean[] albumArtInitialized = {false};

        final boolean[] wasInWorld = {false};

        // Register tick event to handle keybinds
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (!albumArtInitialized[0] && client.getResourceManager() != null) {
                albumArtInitialized[0] = true;
                AlbumArtManager.getInstance().init();
            }

            // Stop music when not in a world (main menu, loading screen)
            boolean inWorld = client.level != null;
            if (!inWorld && wasInWorld[0]) {
                manager.stop();
            }
            wasInWorld[0] = inWorld;

            if (!focusCallbackRegistered[0] && client.getWindow() != null) {
                focusCallbackRegistered[0] = true;
                long windowHandle = client.getWindow().handle();
                org.lwjgl.glfw.GLFWWindowFocusCallback[] prevHolder = new org.lwjgl.glfw.GLFWWindowFocusCallback[1];
                prevHolder[0] = org.lwjgl.glfw.GLFW.glfwSetWindowFocusCallback(
                    windowHandle, (window, focused) -> {
                        MusicPlayerManager mgr = MusicPlayerManager.getInstance();
                        var cfg = com.musicplayer.config.MusicPlayerConfig.getInstance();
                        if (focused) {
                            if (mgr.getCurrentSource() == MusicPlayerManager.SongSource.MINECRAFT) {
                                com.musicplayer.player.SoundtrackStateManager.getInstance().refocus();
                            }
                        } else {
                            if (mgr.getCurrentSource() == MusicPlayerManager.SongSource.MINECRAFT) {
                                com.musicplayer.player.SoundtrackStateManager.getInstance().unfocus();
                            } else if (cfg.isStopWhenUnfocused()) {
                                if (mgr.getEngine().isActive() && !mgr.getEngine().isPaused()) {
                                    mgr.getEngine().togglePause();
                                }
                            }
                        }
                        if (prevHolder[0] != null) prevHolder[0].invoke(window, focused);
                    }
                );
            }
            // Check for pending playback callbacks (from audio thread, flag-based)
            manager.getEngine().checkPendingCallback();
            // Process pending queue restore (auto-play from saved position)
            manager.processQueueRestore();

            // Only process keybinds when no screen is open (in-game)
            // Exception: open GUI key always works
            if (openGuiKey.consumeClick()) {
                boolean newUi = com.musicplayer.config.MusicPlayerConfig.getInstance().isNewUiEnabled();
                var cur = client.screen;
                if (cur == null) {
                    client.setScreen(newUi ? new MusicPlayerScreen() : new MusicPlayerScreenLegacy());
                } else if (cur instanceof MusicPlayerScreen || cur instanceof MusicPlayerScreenLegacy
                        || cur instanceof MusicSettingsScreen || cur instanceof UpNextScreen
                        || cur instanceof ListeningStatsScreen) {
                    client.setScreen(null);
                }
            }

            // These controls work even without the GUI open
            if (client.screen == null) {
                while (playPauseKey.consumeClick()) {
                    manager.togglePlayPause();
                }
                while (nextTrackKey.consumeClick()) {
                    manager.nextTrack();
                }
                while (prevTrackKey.consumeClick()) {
                    manager.previousTrack();
                }
                while (volumeUpKey.consumeClick()) {
                    manager.adjustVolume(0.05f);
                    showVolumeOverlay(client, manager.getVolume());
                }
                while (volumeDownKey.consumeClick()) {
                    manager.adjustVolume(-0.05f);
                    showVolumeOverlay(client, manager.getVolume());
                }
                var cfg = com.musicplayer.config.MusicPlayerConfig.getInstance();
                if (customVolumeKey.consumeClick()) {
                    manager.setVolume(cfg.getCustomVolumeKeybindLevel());
                    showVolumeOverlay(client, manager.getVolume());
                }

                // Volume duck on damage
                if (cfg.isDuckOnDamage() && client.player != null
                        && client.player.hurtTime > 0
                        && client.player.hurtTime == client.player.hurtDuration - 1) {
                    if (manager.getCurrentSource() == MusicPlayerManager.SongSource.MINECRAFT) {
                        com.musicplayer.player.SoundtrackStateManager ssm = com.musicplayer.player.SoundtrackStateManager.getInstance();
                        if (ssm.isAnyPlaying()) {
                            float restore = ssm.isDucking() ? cfg.getVolume() : ssm.getVolume();
                            ssm.duck(cfg.getDuckVolume(), cfg.getDuckDurationMs(), restore);
                        }
                    } else if (manager.getEngine().isActive()) {
                        float restore = manager.getEngine().isDucking() ? cfg.getVolume() : manager.getEngine().getVolume();
                        manager.getEngine().duck(cfg.getDuckVolume(), cfg.getDuckDurationMs(), restore);
                    }
                }

                // Tick vanilla playback fades
                VanillaMusicPlayer.getInstance().tick();

            }

        });

        DebugLog.info("Music Player Mod initialized!");
    }

    private static void showVolumeOverlay(Minecraft client, float volume) {
        if (client.gui != null) {
            int pct = Math.round(volume * 100);
            client.gui.setOverlayMessage(Component.literal("Volume: " + pct + "%"), false);
        }
    }
}
