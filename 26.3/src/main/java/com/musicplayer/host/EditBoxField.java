package com.musicplayer.host;

import com.musicplayer.ui.UiTextField;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

import java.util.function.Consumer;

/** A borderless EditBox behind the shared UiTextField interface. */
final class EditBoxField implements UiTextField {
    final EditBox box;

    EditBoxField(String hint, int maxLen) {
        var font = Minecraft.getInstance().font;
        box = new EditBox(font, 0, 0, 10, font.lineHeight, Component.literal(hint));
        box.setBordered(false);
        box.setHint(Component.literal(hint));
        box.setMaxLength(maxLen);
    }

    @Override public String getValue() { return box.getValue(); }
    @Override public void setValue(String v) { box.setValue(v); }
    @Override public void setHint(String hint) { box.setHint(Component.literal(hint)); }
    @Override public void setTextColor(int argb) { box.setTextColor(argb); }
    @Override public void setMaxLength(int n) { box.setMaxLength(n); }
    @Override public void setResponder(Consumer<String> r) { box.setResponder(r); }
    @Override public void setBounds(int x, int y, int w) { box.setX(x); box.setY(y); box.setWidth(w); }
    @Override public void setVisible(boolean v) { box.visible = v; }
    @Override public boolean isVisible() { return box.visible; }
    @Override public boolean isFocused() { return box.isFocused(); }
    @Override public void setFocused(boolean focused) { box.setFocused(focused); }
    @Override public void moveCursorToStart() { box.moveCursorToStart(false); }
}
