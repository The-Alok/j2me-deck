// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 J2ME Deck contributors
package com.nokia.mid.ui;

import javax.microedition.lcdui.Image;

public interface DirectGraphics {
    int FLIP_HORIZONTAL = 0x2000, FLIP_VERTICAL = 0x4000, ROTATE_90 = 90, ROTATE_180 = 180, ROTATE_270 = 270;
    int TYPE_BYTE_1_GRAY = 1, TYPE_BYTE_1_GRAY_VERTICAL = -1, TYPE_BYTE_2_GRAY = 2, TYPE_BYTE_4_GRAY = 4, TYPE_BYTE_8_GRAY = 8,
            TYPE_BYTE_332_RGB = 332, TYPE_USHORT_4444_ARGB = 4444, TYPE_USHORT_444_RGB = 444, TYPE_USHORT_555_RGB = 555,
            TYPE_USHORT_1555_ARGB = 1555, TYPE_USHORT_565_RGB = 565, TYPE_INT_888_RGB = 888, TYPE_INT_8888_ARGB = 8888;

    void setARGBColor(int argb);
    int getAlphaComponent();
    int getNativePixelFormat();
    void drawImage(Image img, int x, int y, int anchor, int manipulation);
    void drawPixels(int[] pixels, boolean transparency, int offset, int scanlength, int x, int y, int width, int height, int manipulation, int format);
    void drawPixels(byte[] pixels, byte[] transparencyMask, int offset, int scanlength, int x, int y, int width, int height, int manipulation, int format);
    void drawPixels(short[] pixels, boolean transparency, int offset, int scanlength, int x, int y, int width, int height, int manipulation, int format);
    void drawPolygon(int[] xPoints, int xOffset, int[] yPoints, int yOffset, int nPoints, int argbColor);
    void drawTriangle(int x1, int y1, int x2, int y2, int x3, int y3, int argbColor);
    void fillPolygon(int[] xPoints, int xOffset, int[] yPoints, int yOffset, int nPoints, int argbColor);
    void fillTriangle(int x1, int y1, int x2, int y2, int x3, int y3, int argbColor);
    void getPixels(int[] pixels, int offset, int scanlength, int x, int y, int width, int height, int format);
    void getPixels(byte[] pixels, byte[] transparencyMask, int offset, int scanlength, int x, int y, int width, int height, int format);
    void getPixels(short[] pixels, int offset, int scanlength, int x, int y, int width, int height, int format);
}
