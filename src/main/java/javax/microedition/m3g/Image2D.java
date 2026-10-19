// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 J2ME Deck contributors
package javax.microedition.m3g;

import java.util.Map;

public class Image2D extends Object3D {
    public static final int ALPHA = 96, LUMINANCE = 97, LUMINANCE_ALPHA = 98, RGB = 99, RGBA = 100;

    private final int format, w, h;
    private final boolean mutable;
    private byte[] pixels, palette;
    private int[] argb;

    public Image2D(int format, int width, int height) {
        this(format, width, height, new byte[Math.max(0, width) * Math.max(0, height) * bpp(format)], true);
    }
    public Image2D(int format, int width, int height, byte[] image) {
        this(format, width, height, image == null ? null : image.clone(), false);
        if (image != null && image.length < width * height * bpp(format)) throw new IllegalArgumentException("image data too short");
    }
    public Image2D(int format, int width, int height, byte[] image, byte[] palette) {
        this(format, width, height, null, false);
        if (image == null || palette == null) throw new NullPointerException();
        int bp = bpp(format);
        if (image.length < width * height) throw new IllegalArgumentException("index data too short");
        if (palette.length % bp != 0 || palette.length / bp > 256 || palette.length == 0) throw new IllegalArgumentException("bad palette size");
        pixels = new byte[width * height * bp];
        for (int i = 0; i < width * height; i++) {
            int idx = image[i] & 0xFF;
            if (idx * bp + bp > palette.length) throw new IllegalArgumentException("palette index out of range");
            System.arraycopy(palette, idx * bp, pixels, i * bp, bp);
        }
        this.palette = palette.clone();
    }
    public Image2D(int format, Object image) {
        this(format, imageWidth(image), imageHeight(image), null, false);
        javax.microedition.lcdui.Image img = (javax.microedition.lcdui.Image) image;
        int[] px = new int[w * h];
        img.getRGB(px, 0, w, 0, 0, w, h);
        int bp = bpp(format);
        pixels = new byte[w * h * bp];
        for (int i = 0; i < px.length; i++) {
            int p = px[i], a = p >>> 24, r = (p >> 16) & 255, g = (p >> 8) & 255, b = p & 255, l = (r * 30 + g * 59 + b * 11) / 100;
            int o = i * bp;
            switch (format) {
                case ALPHA: pixels[o] = (byte) a; break;
                case LUMINANCE: pixels[o] = (byte) l; break;
                case LUMINANCE_ALPHA: pixels[o] = (byte) l; pixels[o + 1] = (byte) a; break;
                case RGB: pixels[o] = (byte) r; pixels[o + 1] = (byte) g; pixels[o + 2] = (byte) b; break;
                default: pixels[o] = (byte) r; pixels[o + 1] = (byte) g; pixels[o + 2] = (byte) b; pixels[o + 3] = (byte) a;
            }
        }
    }
    private static int imageWidth(Object o) { if (o == null) throw new NullPointerException(); if (!(o instanceof javax.microedition.lcdui.Image)) throw new IllegalArgumentException("image must be a javax.microedition.lcdui.Image"); return ((javax.microedition.lcdui.Image) o).getWidth(); }
    private static int imageHeight(Object o) { return ((javax.microedition.lcdui.Image) o).getHeight(); }

    private Image2D(int format, int width, int height, byte[] data, boolean mutable) {
        if (format < ALPHA || format > RGBA) throw new IllegalArgumentException("invalid image format");
        if (width <= 0 || height <= 0) throw new IllegalArgumentException("width and height must be positive");
        this.format = format; w = width; h = height; this.mutable = mutable; this.pixels = data;
        if (mutable && data != null) java.util.Arrays.fill(data, (byte) 0xFF);   // mutable images start opaque white
    }

    static int bpp(int f) {
        switch (f) { case ALPHA: case LUMINANCE: return 1; case LUMINANCE_ALPHA: return 2; case RGB: return 3; case RGBA: return 4; default: throw new IllegalArgumentException("invalid image format"); }
    }

    public boolean isMutable() { return mutable; }
    public int getFormat() { return format; }
    public int getWidth() { return w; }
    public int getHeight() { return h; }

    public void set(int x, int y, int width, int height, byte[] image) {
        if (!mutable) throw new IllegalStateException("image is immutable");
        if (image == null) throw new NullPointerException();
        if (width < 0 || height < 0 || x < 0 || y < 0 || x + width > w || y + height > h) throw new IllegalArgumentException("region outside the image");
        int bp = bpp(format);
        if (image.length < width * height * bp) throw new IllegalArgumentException("not enough image data");
        for (int row = 0; row < height; row++) System.arraycopy(image, row * width * bp, pixels, ((y + row) * w + x) * bp, width * bp);
        argb = null;
    }

    /** Pixels as 0xAARRGGBB, converted once and cached. */
    int[] argb() {
        if (argb != null) return argb;
        int[] out = new int[w * h];
        int bp = bpp(format);
        for (int i = 0; i < out.length; i++) {
            int o = i * bp;
            switch (format) {
                case ALPHA: out[i] = ((pixels[o] & 255) << 24) | 0xFFFFFF; break;
                case LUMINANCE: { int l = pixels[o] & 255; out[i] = 0xFF000000 | (l << 16) | (l << 8) | l; break; }
                case LUMINANCE_ALPHA: { int l = pixels[o] & 255; out[i] = ((pixels[o + 1] & 255) << 24) | (l << 16) | (l << 8) | l; break; }
                case RGB: out[i] = 0xFF000000 | ((pixels[o] & 255) << 16) | ((pixels[o + 1] & 255) << 8) | (pixels[o + 2] & 255); break;
                default: out[i] = ((pixels[o + 3] & 255) << 24) | ((pixels[o] & 255) << 16) | ((pixels[o + 1] & 255) << 8) | (pixels[o + 2] & 255);
            }
        }
        return argb = out;
    }
    /** Store 0xAARRGGBB pixels (the target of Graphics3D.bindTarget(Image2D)); RGB and RGBA only. */
    void writeArgb(int[] src) {
        int bp = bpp(format);
        for (int i = 0; i < w * h; i++) {
            int p = src[i], o = i * bp;
            pixels[o] = (byte) (p >> 16); pixels[o + 1] = (byte) (p >> 8); pixels[o + 2] = (byte) p;
            if (bp == 4) pixels[o + 3] = (byte) (p >>> 24);
        }
        argb = null;
    }
    boolean hasAlpha() { return format == ALPHA || format == LUMINANCE_ALPHA || format == RGBA; }

    @Override Object3D newInstance() { throw new UnsupportedOperationException(); }
}
