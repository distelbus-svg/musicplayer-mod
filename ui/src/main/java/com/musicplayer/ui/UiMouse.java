package com.musicplayer.ui;

/** A mouse button press, release or drag position (GUI pixels). */
public record UiMouse(double x, double y, int button) {}
