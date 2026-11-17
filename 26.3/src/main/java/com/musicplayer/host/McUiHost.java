package com.musicplayer.host;

import com.musicplayer.Ids;

import com.musicplayer.MusicPlayerMod;
import com.musicplayer.MusicPlayerScreen;
import com.musicplayer.MusicPlayerScreenLegacy;
import com.musicplayer.SdlFileDialogs;
import com.musicplayer.config.MusicPlayerConfig;
import com.musicplayer.ore.OreScreen;
import com.musicplayer.port.UiScreen;
import com.musicplayer.ui.Gfx;
import com.musicplayer.ui.KeyBindingView;
import com.musicplayer.ui.UiHost;
import com.musicplayer.ui.UiTextField;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.options.controls.KeyBindsScreen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import org.lwjgl.sdl.SDLMouse;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/** 26.x implementation of what the shared UI needs from the game. */
public final class McUiHost implements UiHost {
    private Minecraft mc() { return Minecraft.getInstance(); }

    @Override public void open(UiScreen screen) {
        mc().gui.setScreen(screen instanceof OreScreen o ? new HostScreen(o) : null);
    }

    @Override public boolean inWorld() { return mc().level != null; }
    @Override public boolean fullscreen() { return mc().options.fullscreen().get(); }

    @Override public void setFullscreen(boolean fullscreen) {
        mc().options.fullscreen().set(fullscreen); // the option own callback switches the window
        mc().options.save();
    }

    @Override public double mouseX() {
        var win = mc().getWindow();
        return mc().mouseHandler.xpos() * win.getGuiScaledWidth() / win.getScreenWidth();
    }

    @Override public double mouseY() {
        var win = mc().getWindow();
        return mc().mouseHandler.ypos() * win.getGuiScaledHeight() / win.getScreenHeight();
    }

    @Override public boolean mouseLeftDown() {
        return (SDLMouse.SDL_GetMouseState(null, null) & SDLMouse.SDL_BUTTON_LMASK) != 0;
    }

    @Override public void playToastSound() {
        var sm = mc().getSoundManager();
        if (sm != null) sm.play(SimpleSoundInstance.forUI(SoundEvents.UI_TOAST_IN, 1.0f));
    }

    @Override public boolean classicScreenOpen() {
        var cur = mc().gui.screen();
        return cur instanceof MusicPlayerScreen || cur instanceof MusicPlayerScreenLegacy;
    }

    @Override public String playerName() { return mc().getUser().getName(); }
    @Override public void runOnMain(Runnable r) { mc().execute(r); }

    @Override public int textWidth(String s, boolean bold) {
        return mc().font.width(bold ? net.minecraft.network.chat.Component.literal(s).withStyle(net.minecraft.network.chat.Style.EMPTY.withBold(true))
                : net.minecraft.network.chat.Component.literal(s));
    }

    @Override public int lineHeight() { return mc().font.lineHeight; }

    @Override public UiTextField newTextField(String hint, int maxLen) { return new EditBoxField(hint, maxLen); }

    @Override public List<KeyBindingView> keyBindings() {
        List<KeyBindingView> out = new ArrayList<>();
        for (KeyMapping km : MusicPlayerMod.getAllKeys()) {
            if (km == null) out.add(new KeyBindingView("", "Unbound", true));
            else out.add(new KeyBindingView(km.getName(), km.getTranslatedKeyMessage().getString(), km.isUnbound()));
        }
        return out;
    }

    @Override public void openKeybindsScreen(UiScreen parent) {
        mc().gui.setScreen(new KeyBindsScreen(mc().gui.screen(), mc().options));
    }

    @Override public void openFile(String title, String defaultLocation, Consumer<String> onResult) {
        SdlFileDialogs.openFile(title, defaultLocation, onResult);
    }

    @Override public void saveFile(String title, String defaultLocation, Consumer<String> onResult) {
        SdlFileDialogs.saveFile(title, defaultLocation, onResult);
    }

    @Override public void selectFolder(String title, String defaultLocation, Consumer<String> onResult) {
        SdlFileDialogs.selectFolder(title, defaultLocation, onResult);
    }

    @Override public void registerHud(String id, BiConsumer<Gfx, Float> renderer) {
        HudElementRegistry.addLast(Ids.rl("musicplayer", id),
                (graphics, delta) -> renderer.accept(new GfxImpl(graphics), 0f));
    }

    @Override public boolean classicUiAvailable() { return true; }

    @Override public void openClassicUi() {
        MusicPlayerConfig cfg = MusicPlayerConfig.getInstance();
        mc().gui.setScreen(cfg.isNewUiEnabled() ? new MusicPlayerScreen() : new MusicPlayerScreenLegacy());
    }
}
