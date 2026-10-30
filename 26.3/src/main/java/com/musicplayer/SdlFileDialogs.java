package com.musicplayer;

import net.minecraft.client.Minecraft;
import org.lwjgl.sdl.SDLDialog;
import org.lwjgl.sdl.SDL_DialogFileCallback;
import org.lwjgl.system.MemoryUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Native file dialogs for 26.3+, which ships SDL instead of GLFW + tinyfd.
 *
 * SDL's file dialogs are asynchronous: the call returns immediately and the
 * result is delivered from a callback while Minecraft keeps pumping SDL events.
 * Callers therefore pass a {@link Consumer} instead of receiving a String.
 *
 * A callback returning {@code null} means the user cancelled or an error
 * occurred. Callback objects are retained until invoked and freed afterwards.
 */
public final class SdlFileDialogs {

    private static final List<SDL_DialogFileCallback> PENDING = new ArrayList<>();

    private SdlFileDialogs() {
    }

    public static void openFile(String title, String defaultLocation, Consumer<String> onResult) {
        show(SDLDialog.SDL_FILEDIALOG_OPENFILE, defaultLocation, onResult);
    }

    public static void saveFile(String title, String defaultLocation, Consumer<String> onResult) {
        show(SDLDialog.SDL_FILEDIALOG_SAVEFILE, defaultLocation, onResult);
    }

    public static void selectFolder(String title, String defaultLocation, Consumer<String> onResult) {
        show(SDLDialog.SDL_FILEDIALOG_OPENFOLDER, defaultLocation, onResult);
    }

    private static void show(int kind, String defaultLocation, Consumer<String> onResult) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.getWindow() == null) {
            if (onResult != null) onResult.accept(null);
            return;
        }
        long window = mc.getWindow().handle();
        String initial = defaultLocation == null ? "" : defaultLocation;

        final SDL_DialogFileCallback[] holder = new SDL_DialogFileCallback[1];
        SDL_DialogFileCallback callback = SDL_DialogFileCallback.create((userdata, filelist, filter) -> {
            String result = null;
            try {
                if (filelist != 0L) {
                    long pathPtr = MemoryUtil.memGetAddress(filelist);
                    if (pathPtr != 0L) {
                        result = MemoryUtil.memUTF8(pathPtr);
                    }
                }
            } catch (Throwable t) {
                DebugLog.error("File dialog callback failed: %s", t.getMessage());
            } finally {
                SDL_DialogFileCallback cb = holder[0];
                if (cb != null) {
                    PENDING.remove(cb);
                    try {
                        cb.free();
                    } catch (Throwable ignored) {
                    }
                }
            }
            if (onResult != null) onResult.accept(result);
        });
        holder[0] = callback;
        PENDING.add(callback);

        switch (kind) {
            case SDLDialog.SDL_FILEDIALOG_SAVEFILE ->
                    SDLDialog.SDL_ShowSaveFileDialog(callback, 0L, window, null, initial);
            case SDLDialog.SDL_FILEDIALOG_OPENFOLDER ->
                    SDLDialog.SDL_ShowOpenFolderDialog(callback, 0L, window, initial, false);
            case SDLDialog.SDL_FILEDIALOG_OPENFILE ->
                    SDLDialog.SDL_ShowOpenFileDialog(callback, 0L, window, null, initial, false);
            default -> {
            }
        }
    }
}
