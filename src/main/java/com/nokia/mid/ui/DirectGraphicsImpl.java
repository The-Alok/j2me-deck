// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 J2ME Deck contributors
package com.nokia.mid.ui;

import javax.microedition.lcdui.Graphics;
import javax.microedition.lcdui.Image;
import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;

final class DirectGraphicsImpl implements DirectGraphics {
    private final Graphics g;
    private int argb = 0xFF000000;

    DirectGraphicsImpl(Graphics g) { this.g = g; }

    public void setARGBColor(int c) { argb = c; g.setColor(c & 0xFFFFFF); }
    public int getAlphaComponent() { return argb >>> 24; }
    public int getNativePixelFormat() { return TYPE_INT_8888_ARGB; }

    /** Nokia manipulation = optional flips, then a counter-clockwise rotation. Returns the matrix for a w*h image. */
    private static AffineTransform matrix(int manip, int w, int h) {
        AffineTransform t = new AffineTransform();
        int rot = manip & 0xFF;
        boolean fh = (manip & FLIP_HORIZONTAL) != 0, fv = (manip & FLIP_VERTICAL) != 0;
        // compose (applied to source points, in order): flips, then rotation
        if (fh) { t.concatenate(new AffineTransform(-1, 0, 0, 1, w, 0)); }
        if (fv) { AffineTransform f = new AffineTransform(1, 0, 0, -1, 0, h); f.concatenate(t); t = f; }
        AffineTransform r = new AffineTransform();
        if (rot == ROTATE_90) r = new AffineTransform(0, -1, 1, 0, 0, w);         // 90 counter-clockwise
        else if (rot == ROTATE_180) r = new AffineTransform(-1, 0, 0, -1, w, h);
        else if (rot == ROTATE_270) r = new AffineTransform(0, 1, -1, 0, h, 0);   // 270 counter-clockwise
        r.concatenate(t);
        return r;
    }

    public void drawImage(Image img, int x, int y, int anchor, int manip) {
        if (img == null) return;
        BufferedImage b = argbOf(img);
        blit(b, img.getWidth(), img.getHeight(), x, y, anchor, manip);
    }

    private static BufferedImage argbOf(Image img) {
        int w = img.getWidth(), h = img.getHeight();
        int[] px = new int[w * h];
        img.getRGB(px, 0, w, 0, 0, w, h);
        BufferedImage b = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        b.setRGB(0, 0, w, h, px, 0, w);
        return b;
    }

    private void blit(BufferedImage b, int w, int h, int x, int y, int anchor, int manip) {
        AffineTransform m = matrix(manip, w, h);
        boolean swap = (manip & 0xFF) == ROTATE_90 || (manip & 0xFF) == ROTATE_270;
        int dw = swap ? h : w, dh = swap ? w : h;
        if ((anchor & Graphics.HCENTER) != 0) x -= dw / 2; else if ((anchor & Graphics.RIGHT) != 0) x -= dw;
        if ((anchor & Graphics.VCENTER) != 0) y -= dh / 2; else if ((anchor & Graphics.BOTTOM) != 0) y -= dh;
        AffineTransform t = new AffineTransform();
        t.translate(x, y);
        t.concatenate(m);
        g.$awt().drawImage(b, t, null);
    }

    // ---- pixel format conversion
    private static int toArgb(int v, int format) {
        switch (format) {
            case TYPE_INT_8888_ARGB: return v;
            case TYPE_INT_888_RGB: return 0xFF000000 | v;
            case TYPE_USHORT_565_RGB: { int r = (v >> 11) & 31, gg = (v >> 5) & 63, b = v & 31; return 0xFF000000 | (r * 255 / 31) << 16 | (gg * 255 / 63) << 8 | (b * 255 / 31); }
            case TYPE_USHORT_555_RGB: { int r = (v >> 10) & 31, gg = (v >> 5) & 31, b = v & 31; return 0xFF000000 | (r * 255 / 31) << 16 | (gg * 255 / 31) << 8 | (b * 255 / 31); }
            case TYPE_USHORT_1555_ARGB: { int a = (v >> 15) & 1, r = (v >> 10) & 31, gg = (v >> 5) & 31, b = v & 31; return (a * 255) << 24 | (r * 255 / 31) << 16 | (gg * 255 / 31) << 8 | (b * 255 / 31); }
            case TYPE_USHORT_444_RGB: { int r = (v >> 8) & 15, gg = (v >> 4) & 15, b = v & 15; return 0xFF000000 | (r * 17) << 16 | (gg * 17) << 8 | (b * 17); }
            case TYPE_USHORT_4444_ARGB: { int a = (v >> 12) & 15, r = (v >> 8) & 15, gg = (v >> 4) & 15, b = v & 15; return (a * 17) << 24 | (r * 17) << 16 | (gg * 17) << 8 | (b * 17); }
            case TYPE_BYTE_332_RGB: { int r = (v >> 5) & 7, gg = (v >> 2) & 7, b = v & 3; return 0xFF000000 | (r * 255 / 7) << 16 | (gg * 255 / 7) << 8 | (b * 255 / 3); }
            case TYPE_BYTE_8_GRAY: return 0xFF000000 | (v & 255) * 0x010101;
            default: throw new IllegalArgumentException("unsupported pixel format " + format);
        }
    }
    private static int fromArgb(int a, int format) {
        int al = a >>> 24, r = (a >> 16) & 255, gg = (a >> 8) & 255, b = a & 255;
        switch (format) {
            case TYPE_INT_8888_ARGB: return a;
            case TYPE_INT_888_RGB: return a & 0xFFFFFF;
            case TYPE_USHORT_565_RGB: return (r >> 3) << 11 | (gg >> 2) << 5 | (b >> 3);
            case TYPE_USHORT_555_RGB: return (r >> 3) << 10 | (gg >> 3) << 5 | (b >> 3);
            case TYPE_USHORT_1555_ARGB: return (al >> 7) << 15 | (r >> 3) << 10 | (gg >> 3) << 5 | (b >> 3);
            case TYPE_USHORT_444_RGB: return (r >> 4) << 8 | (gg >> 4) << 4 | (b >> 4);
            case TYPE_USHORT_4444_ARGB: return (al >> 4) << 12 | (r >> 4) << 8 | (gg >> 4) << 4 | (b >> 4);
            case TYPE_BYTE_332_RGB: return (r >> 5) << 5 | (gg >> 5) << 2 | (b >> 6);
            case TYPE_BYTE_8_GRAY: return (r * 30 + gg * 59 + b * 11) / 100;
            default: throw new IllegalArgumentException("unsupported pixel format " + format);
        }
    }

    public void drawPixels(int[] p, boolean transparency, int off, int scan, int x, int y, int w, int h, int manip, int format) {
        BufferedImage b = new BufferedImage(Math.max(1, w), Math.max(1, h), BufferedImage.TYPE_INT_ARGB);
        for (int row = 0; row < h; row++) for (int col = 0; col < w; col++) {
            int c = toArgb(p[off + row * scan + col], format);
            if (!transparency) c |= 0xFF000000;
            b.setRGB(col, row, c);
        }
        blit(b, w, h, x, y, Graphics.TOP | Graphics.LEFT, manip);
    }
    public void drawPixels(short[] p, boolean transparency, int off, int scan, int x, int y, int w, int h, int manip, int format) {
        BufferedImage b = new BufferedImage(Math.max(1, w), Math.max(1, h), BufferedImage.TYPE_INT_ARGB);
        for (int row = 0; row < h; row++) for (int col = 0; col < w; col++) {
            int c = toArgb(p[off + row * scan + col] & 0xFFFF, format);
            if (!transparency) c |= 0xFF000000;
            b.setRGB(col, row, c);
        }
        blit(b, w, h, x, y, Graphics.TOP | Graphics.LEFT, manip);
    }
    public void drawPixels(byte[] p, byte[] mask, int off, int scan, int x, int y, int w, int h, int manip, int format) {
        BufferedImage b = new BufferedImage(Math.max(1, w), Math.max(1, h), BufferedImage.TYPE_INT_ARGB);
        for (int row = 0; row < h; row++) for (int col = 0; col < w; col++) {
            int i = off + row * scan + col;
            int c;
            if (format == TYPE_BYTE_1_GRAY || format == TYPE_BYTE_1_GRAY_VERTICAL || format == TYPE_BYTE_2_GRAY || format == TYPE_BYTE_4_GRAY) {
                int bits = format == TYPE_BYTE_4_GRAY ? 4 : format == TYPE_BYTE_2_GRAY ? 2 : 1;
                int perByte = 8 / bits, idx = row * scan + col + off, bytePos;
                int v;
                if (format == TYPE_BYTE_1_GRAY_VERTICAL) { int bi = (row / 8) * scan + col + off; v = (p[bi] >> (row & 7)) & 1; }
                else { bytePos = idx / perByte; int shift = (perByte - 1 - idx % perByte) * bits; v = (p[bytePos] >> shift) & ((1 << bits) - 1); }
                int gray = v * 255 / ((1 << bits) - 1);
                c = 0xFF000000 | gray * 0x010101;
            } else c = toArgb(p[i] & 0xFF, format);
            if (mask != null) {
                int mi = i;
                boolean opaque = format == TYPE_BYTE_1_GRAY || format == TYPE_BYTE_1_GRAY_VERTICAL ? ((mask[(row * scan + col + off) / 8] >> (7 - (row * scan + col + off) % 8)) & 1) != 0 : mask[mi] != 0;
                if (!opaque) c &= 0x00FFFFFF;
            }
            b.setRGB(col, row, c);
        }
        blit(b, w, h, x, y, Graphics.TOP | Graphics.LEFT, manip);
    }

    public void drawPolygon(int[] xs, int xo, int[] ys, int yo, int n, int c) { poly(xs, xo, ys, yo, n, c, false); }
    public void fillPolygon(int[] xs, int xo, int[] ys, int yo, int n, int c) { poly(xs, xo, ys, yo, n, c, true); }
    public void drawTriangle(int x1, int y1, int x2, int y2, int x3, int y3, int c) { poly(new int[]{x1, x2, x3}, 0, new int[]{y1, y2, y3}, 0, 3, c, false); }
    public void fillTriangle(int x1, int y1, int x2, int y2, int x3, int y3, int c) { poly(new int[]{x1, x2, x3}, 0, new int[]{y1, y2, y3}, 0, 3, c, true); }

    private void poly(int[] xs, int xo, int[] ys, int yo, int n, int color, boolean fill) {
        int[] x = new int[n], y = new int[n];
        System.arraycopy(xs, xo, x, 0, n);
        System.arraycopy(ys, yo, y, 0, n);
        Graphics2D g2 = g.$awt();
        Color old = g2.getColor();
        g2.setColor(new Color(color, true));
        if (fill) g2.fillPolygon(x, y, n); else g2.drawPolygon(x, y, n);
        g2.setColor(old);
    }

    private int[] grab(int x, int y, int w, int h) {
        BufferedImage t = g.$target();
        Graphics2D g2 = g.$awt();
        AffineTransform at = g2.getTransform();
        int ox = (int) at.getTranslateX(), oy = (int) at.getTranslateY();
        int[] out = new int[w * h];
        for (int row = 0; row < h; row++) for (int col = 0; col < w; col++) {
            int px = x + col + ox, py = y + row + oy;
            out[row * w + col] = px >= 0 && py >= 0 && px < t.getWidth() && py < t.getHeight() ? t.getRGB(px, py) : 0;
        }
        return out;
    }
    public void getPixels(int[] p, int off, int scan, int x, int y, int w, int h, int format) {
        int[] s = grab(x, y, w, h);
        for (int row = 0; row < h; row++) for (int col = 0; col < w; col++) p[off + row * scan + col] = fromArgb(s[row * w + col], format);
    }
    public void getPixels(short[] p, int off, int scan, int x, int y, int w, int h, int format) {
        int[] s = grab(x, y, w, h);
        for (int row = 0; row < h; row++) for (int col = 0; col < w; col++) p[off + row * scan + col] = (short) fromArgb(s[row * w + col], format);
    }
    public void getPixels(byte[] p, byte[] mask, int off, int scan, int x, int y, int w, int h, int format) {
        int[] s = grab(x, y, w, h);
        for (int row = 0; row < h; row++) for (int col = 0; col < w; col++) {
            int c = s[row * w + col];
            p[off + row * scan + col] = (byte) fromArgb(c, format);
            if (mask != null) mask[off + row * scan + col] = (byte) ((c >>> 24) != 0 ? 0xFF : 0);
        }
    }
}
