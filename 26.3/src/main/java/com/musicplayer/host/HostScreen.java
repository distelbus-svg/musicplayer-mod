package com.musicplayer.host;

import com.mojang.blaze3d.platform.InputConstants;
import com.musicplayer.ore.OreScreen;
import com.musicplayer.ui.UiChar;
import com.musicplayer.ui.UiKey;
import com.musicplayer.ui.UiKeys;
import com.musicplayer.ui.UiMouse;
import com.musicplayer.ui.UiTextField;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** The game Screen that hosts a shared Ore screen. */
public final class HostScreen extends Screen implements OreScreen.Host {
    private final OreScreen ui;
    private GuiGraphicsExtractor curG;
    private int curMx, curMy;
    private float curPt;

    public HostScreen(OreScreen ui) {
        super(Component.literal(ui.title()));
        this.ui = ui;
        ui.attach(this);
    }

    public OreScreen ui() { return ui; }

    @Override protected void init() {
        ui.attach(this);
        ui.onInit(this.width, this.height);
    }

    @Override public void extractBackground(GuiGraphicsExtractor g, int mx, int my, float pt) {
        // the shared screen paints its own background
    }

    @Override public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float pt) {
        curG = g; curMx = mx; curMy = my; curPt = pt;
        ui.onRender(new GfxImpl(g), mx, my, pt);
    }

    // ---- OreScreen.Host
    @Override public void addField(UiTextField f) { addRenderableWidget(((EditBoxField) f).box); }
    @Override public void focusField(UiTextField f) { setFocused(f == null ? null : ((EditBoxField) f).box); }
    @Override public boolean anyFieldFocused() { return getFocused() != null; }
    @Override public void drawFields() { super.extractRenderState(curG, curMx, curMy, curPt); }

    // ---- input
    /** 26.3 uses SDL button numbers (left = 1); the shared UI uses UiKeys (left = 0). */
    private static UiMouse mouse(MouseButtonEvent e) {
        int b = e.button();
        int mapped = b == InputConstants.MOUSE_BUTTON_LEFT ? UiKeys.MOUSE_LEFT
                : b == InputConstants.MOUSE_BUTTON_RIGHT ? UiKeys.MOUSE_RIGHT : -1;
        return new UiMouse(e.x(), e.y(), mapped);
    }

    @Override public boolean mouseClicked(MouseButtonEvent e, boolean isFocused) {
        if (super.mouseClicked(e, isFocused)) return true;
        return ui.onMouseClicked(mouse(e));
    }

    @Override public boolean mouseDragged(MouseButtonEvent e, double dx, double dy) {
        if (ui.onMouseDragged(mouse(e), dx, dy)) return true;
        return super.mouseDragged(e, dx, dy);
    }

    @Override public boolean mouseReleased(MouseButtonEvent e) {
        if (ui.onMouseReleased(mouse(e))) return true;
        return super.mouseReleased(e);
    }

    @Override public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        if (ui.onMouseScrolled(mx, my, sx, sy)) return true;
        return super.mouseScrolled(mx, my, sx, sy);
    }

    @Override public boolean keyPressed(KeyEvent e) {
        int k = e.key();
        int mapped = k == InputConstants.KEY_ESCAPE ? UiKeys.ESCAPE
                : k == InputConstants.KEY_RETURN ? UiKeys.ENTER
                : k == InputConstants.KEY_NUMPADENTER ? UiKeys.NUMPAD_ENTER
                : k == InputConstants.KEY_SLASH ? UiKeys.SLASH : UiKeys.UNKNOWN;
        if (ui.onKeyPressed(new UiKey(mapped))) return true;
        return super.keyPressed(e);
    }

    @Override public boolean charTyped(CharacterEvent e) {
        if (ui.onCharTyped(new UiChar(e.codepoint()))) return true;
        return super.charTyped(e);
    }

    @Override public boolean isPauseScreen() { return ui.isPauseScreen(); }

    @Override public void onClose() { ui.onClose(); }
}
