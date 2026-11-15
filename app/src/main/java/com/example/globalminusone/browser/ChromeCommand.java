package com.example.globalminusone.browser;

import android.view.KeyEvent;

/** Fixed Chromium Android shortcuts, not a general privileged key injection API. */
public enum ChromeCommand {
    FORWARD(KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.META_ALT_ON),
    RELOAD(KeyEvent.KEYCODE_R, KeyEvent.META_CTRL_ON),
    TABS(KeyEvent.KEYCODE_A, KeyEvent.META_CTRL_ON | KeyEvent.META_SHIFT_ON),
    MENU(KeyEvent.KEYCODE_MENU, 0);
    public final int keyCode;
    public final int metaState;
    ChromeCommand(int keyCode, int metaState) { this.keyCode = keyCode; this.metaState = metaState; }
    public static ChromeCommand fromWire(int value) {
        if (value < 0 || value >= values().length) throw new IllegalArgumentException("Unknown Chrome command");
        return values()[value];
    }
}
