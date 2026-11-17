package com.musicplayer.ui;

import com.musicplayer.port.Ports;
import com.musicplayer.port.UiScreen;

import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/** What the shared UI needs from the game, other than drawing. Implemented once per Minecraft version. */
public interface UiHost {
    /** Shows the screen; null closes the current screen. */
    void open(UiScreen screen);
    boolean inWorld();
    boolean fullscreen();
    /** Mouse position in GUI pixels and whether the left button is held (for HUD widgets). */
    double mouseX();
    double mouseY();
    boolean mouseLeftDown();
    /** Plays the short toast sound. */
    void playToastSound();
    /** True while a classic (non-Ore) Melodix screen is open. */
    boolean classicScreenOpen();
    void setFullscreen(boolean fullscreen);
    String playerName();
    void runOnMain(Runnable r);

    int textWidth(String s, boolean bold);
    int lineHeight();

    UiTextField newTextField(String hint, int maxLen);

    /** All Melodix key bindings in display order: open, play/pause, next, previous, volume up/down, custom volume. */
    List<KeyBindingView> keyBindings();
    void openKeybindsScreen(UiScreen parent);

    void openFile(String title, String defaultLocation, Consumer<String> onResult);
    void saveFile(String title, String defaultLocation, Consumer<String> onResult);
    void selectFolder(String title, String defaultLocation, Consumer<String> onResult);

    /** Registers a HUD element drawn every frame while in game; the float is the frame delta in ticks. */
    void registerHud(String id, BiConsumer<Gfx, Float> renderer);

    /** True if the classic (non-Ore) interface exists in this version. */
    boolean classicUiAvailable();
    /** Opens the classic interface chosen by the config (only if available). */
    void openClassicUi();

    static UiHost get() {
        Ports.host(); // forces the version bootstrap, which installs the host
        return Holder.host;
    }

    static void install(UiHost host) {
        Holder.host = host;
    }

    final class Holder {
        static volatile UiHost host;
        private Holder() {}
    }
}
