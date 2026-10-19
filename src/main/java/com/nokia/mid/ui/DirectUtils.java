// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 J2ME Deck contributors
package com.nokia.mid.ui;

import javax.microedition.lcdui.Graphics;
import javax.microedition.lcdui.Image;

public final class DirectUtils {
    private DirectUtils() {}
    public static DirectGraphics getDirectGraphics(Graphics g) { return new DirectGraphicsImpl(g); }
    /** Mutable image initialised with an ARGB colour (alpha is honoured, unlike Image.createImage(w,h)). */
    public static Image createImage(int width, int height, int argb) { return Image.$mutableArgb(width, height, argb); }
    public static Image createImage(byte[] data, int off, int len) { return Image.$mutableCopy(Image.createImage(data, off, len)); }
}
