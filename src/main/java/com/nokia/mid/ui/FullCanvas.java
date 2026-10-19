// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 J2ME Deck contributors
package com.nokia.mid.ui;

import javax.microedition.lcdui.Canvas;

/** Nokia Series 40 full-screen canvas (no title or soft-key bar). */
public abstract class FullCanvas extends Canvas {
    public static final int KEY_SOFTKEY1 = -6, KEY_SOFTKEY2 = -7, KEY_SOFTKEY3 = -5, KEY_UP_ARROW = -1, KEY_DOWN_ARROW = -2,
            KEY_LEFT_ARROW = -3, KEY_RIGHT_ARROW = -4, KEY_SEND = -10, KEY_END = -11, KEY_POUND = 35, KEY_STAR = 42,
            KEY_NUM0 = 48, KEY_NUM1 = 49, KEY_NUM2 = 50, KEY_NUM3 = 51, KEY_NUM4 = 52, KEY_NUM5 = 53, KEY_NUM6 = 54,
            KEY_NUM7 = 55, KEY_NUM8 = 56, KEY_NUM9 = 57;

    protected FullCanvas() { super(); setFullScreenMode(true); }
    @Override public void addCommand(javax.microedition.lcdui.Command c) { throw new IllegalStateException("FullCanvas does not support commands"); }
}
