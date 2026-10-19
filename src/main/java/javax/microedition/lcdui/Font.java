// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 J2ME Deck contributors
package javax.microedition.lcdui;

import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.concurrent.ConcurrentHashMap;

public final class Font {
    public static final int FACE_SYSTEM = 0, FACE_MONOSPACE = 32, FACE_PROPORTIONAL = 64;
    public static final int STYLE_PLAIN = 0, STYLE_BOLD = 1, STYLE_ITALIC = 2, STYLE_UNDERLINED = 4;
    public static final int SIZE_SMALL = 8, SIZE_MEDIUM = 0, SIZE_LARGE = 16;
    public static final int FONT_STATIC_TEXT = 0, FONT_INPUT_TEXT = 1;

    private static final ConcurrentHashMap<Integer, Font> CACHE = new ConcurrentHashMap<>();
    private static final Graphics2D SCRATCH = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB).createGraphics();

    private final int face, style, size;
    private final java.awt.Font awt;
    private final FontMetrics fm;

    private Font(int face, int style, int size) {
        this.face = face; this.style = style; this.size = size;
        int px = size == SIZE_SMALL ? j2medeck.Settings.fontSmall : size == SIZE_LARGE ? j2medeck.Settings.fontLarge : j2medeck.Settings.fontMedium;
        String family = face == FACE_MONOSPACE ? java.awt.Font.MONOSPACED : j2medeck.Settings.fontFamily;
        int s = java.awt.Font.PLAIN;
        if ((style & STYLE_BOLD) != 0) s |= java.awt.Font.BOLD;
        if ((style & STYLE_ITALIC) != 0) s |= java.awt.Font.ITALIC;
        awt = new java.awt.Font(family, s, px);
        synchronized (SCRATCH) { fm = SCRATCH.getFontMetrics(awt); }
    }

    public static Font getFont(int face, int style, int size) {
        if (face != FACE_SYSTEM && face != FACE_MONOSPACE && face != FACE_PROPORTIONAL) throw new IllegalArgumentException("face");
        if (size != SIZE_SMALL && size != SIZE_MEDIUM && size != SIZE_LARGE) throw new IllegalArgumentException("size");
        int key = face | (style << 8) | (size << 16);
        return CACHE.computeIfAbsent(key, k -> new Font(face, style, size));
    }
    public static Font getFont(int specifier) { return getDefaultFont(); }
    public static Font getDefaultFont() { return getFont(FACE_SYSTEM, STYLE_PLAIN, SIZE_MEDIUM); }

    java.awt.Font awt() { return awt; }

    public int getHeight() { return fm.getHeight(); }
    public int getBaselinePosition() { return fm.getAscent(); }
    public int stringWidth(String s) { if (s == null) throw new NullPointerException(); return fm.stringWidth(s); }
    public int substringWidth(String s, int off, int len) { return fm.stringWidth(s.substring(off, off + len)); }
    public int charWidth(char c) { return fm.charWidth(c); }
    public int charsWidth(char[] ch, int off, int len) { return fm.charsWidth(ch, off, len); }
    public int getFace() { return face; }
    public int getStyle() { return style; }
    public int getSize() { return size; }
    public boolean isPlain() { return style == 0; }
    public boolean isBold() { return (style & STYLE_BOLD) != 0; }
    public boolean isItalic() { return (style & STYLE_ITALIC) != 0; }
    public boolean isUnderlined() { return (style & STYLE_UNDERLINED) != 0; }
}
