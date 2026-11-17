package com.musicplayer.ui;

import java.util.function.Consumer;

/** A single-line borderless text field; the version-specific host draws and edits it. */
public interface UiTextField {
    String getValue();
    void setValue(String v);
    void setHint(String hint);
    void setTextColor(int argb);
    void setMaxLength(int n);
    void setResponder(Consumer<String> r);
    void setBounds(int x, int y, int w);
    void setVisible(boolean v);
    boolean isVisible();
    boolean isFocused();
    void setFocused(boolean focused);
    void moveCursorToStart();
}
