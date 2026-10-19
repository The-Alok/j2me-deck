// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 J2ME Deck contributors
package javax.microedition.lcdui;

import j2medeck.ResourceBridge;

import javax.imageio.ImageIO;
import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

public class Image {
    public static final int TRANS_NONE = 0, TRANS_MIRROR_ROT180 = 1, TRANS_MIRROR = 2, TRANS_ROT180 = 3,
            TRANS_MIRROR_ROT270 = 4, TRANS_ROT90 = 5, TRANS_ROT270 = 6, TRANS_MIRROR_ROT90 = 7;

    private final BufferedImage image;
    private final boolean mutable;
    private Graphics graphics;

    Image(BufferedImage image, boolean mutable) { this.image = image; this.mutable = mutable; }
    BufferedImage awt() { return image; }

    /** MIDP transform matrix for a w*h source region (see MIDP spec TRANS_* constants). */
    static AffineTransform transformMatrix(int t, int w, int h) {
        switch (t) {
            case TRANS_MIRROR_ROT180: return new AffineTransform(1, 0, 0, -1, 0, h);
            case TRANS_MIRROR:        return new AffineTransform(-1, 0, 0, 1, w, 0);
            case TRANS_ROT180:        return new AffineTransform(-1, 0, 0, -1, w, h);
            case TRANS_MIRROR_ROT270: return new AffineTransform(0, 1, 1, 0, 0, 0);
            case TRANS_ROT90:         return new AffineTransform(0, 1, -1, 0, h, 0);
            case TRANS_ROT270:        return new AffineTransform(0, -1, 1, 0, 0, w);
            case TRANS_MIRROR_ROT90:  return new AffineTransform(0, -1, -1, 0, h, w);
            default:                  return new AffineTransform();
        }
    }

    private static BufferedImage toArgb(BufferedImage src) {
        if (src.getType() == BufferedImage.TYPE_INT_ARGB) return src;
        BufferedImage out = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        g.setComposite(AlphaComposite.Src);
        g.drawImage(src, 0, 0, null);
        g.dispose();
        return out;
    }

    public static Image createImage(int width, int height) {
        if (width <= 0 || height <= 0) throw new IllegalArgumentException("size");
        BufferedImage b = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = b.createGraphics();      // MIDP: mutable images start opaque white
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, width, height);
        g.dispose();
        return new Image(b, true);
    }

    public static Image createImage(String name) throws IOException {
        if (name == null) throw new NullPointerException("name");
        InputStream in = ResourceBridge.getResourceAsStream(name);
        if (in == null) throw new IOException("Resource not found: " + name);
        try (in) { return createImage(in); }
    }

    public static Image createImage(InputStream in) throws IOException {
        if (in == null) throw new NullPointerException("stream");
        BufferedImage b = ImageIO.read(in);
        if (b == null) throw new IOException("Unsupported image format");
        return new Image(toArgb(b), false);
    }

    public static Image createImage(byte[] data, int off, int len) {
        if (data == null) throw new NullPointerException("imageData");
        if (off < 0 || len < 0 || off + len > data.length) throw new ArrayIndexOutOfBoundsException();
        try { return createImage(new ByteArrayInputStream(data, off, len)); }
        catch (IOException e) { throw new IllegalArgumentException("Bad image data: " + e.getMessage()); }
    }

    public static Image createImage(Image src) {
        if (src == null) throw new NullPointerException("image");
        if (!src.mutable) return src;
        BufferedImage b = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = b.createGraphics(); g.setComposite(AlphaComposite.Src); g.drawImage(src.image, 0, 0, null); g.dispose();
        return new Image(b, false);
    }

    public static Image createImage(Image src, int x, int y, int w, int h, int transform) {
        if (src == null) throw new NullPointerException("image");
        if (transform < 0 || transform > 7) throw new IllegalArgumentException("transform");
        if (w <= 0 || h <= 0 || x < 0 || y < 0 || x + w > src.getWidth() || y + h > src.getHeight())
            throw new IllegalArgumentException("region outside image");
        boolean swap = (transform & 4) != 0;
        BufferedImage out = new BufferedImage(swap ? h : w, swap ? w : h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        g.setComposite(AlphaComposite.Src);
        AffineTransform t = transformMatrix(transform, w, h);
        t.translate(-x, -y);
        g.drawImage(src.image, t, null);
        g.dispose();
        return new Image(out, false);
    }

    public static Image createRGBImage(int[] rgb, int width, int height, boolean processAlpha) {
        if (rgb == null) throw new NullPointerException("rgb");
        if (width <= 0 || height <= 0) throw new IllegalArgumentException("size");
        if (rgb.length < width * height) throw new ArrayIndexOutOfBoundsException();
        BufferedImage b = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        int[] dst = ((DataBufferInt) b.getRaster().getDataBuffer()).getData();
        if (processAlpha) System.arraycopy(rgb, 0, dst, 0, width * height);
        else for (int i = 0; i < width * height; i++) dst[i] = rgb[i] | 0xFF000000;
        return new Image(b, false);
    }

    /** Vendor-API helper (DirectUtils.createImage): mutable image filled with an ARGB colour, alpha preserved. */
    public static Image $mutableArgb(int width, int height, int argb) {
        if (width <= 0 || height <= 0) throw new IllegalArgumentException("size");
        BufferedImage b = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        int[] px = ((DataBufferInt) b.getRaster().getDataBuffer()).getData();
        java.util.Arrays.fill(px, argb);
        return new Image(b, true);
    }
    public static Image $mutableCopy(Image src) {
        BufferedImage b = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = b.createGraphics(); g.setComposite(AlphaComposite.Src); g.drawImage(src.image, 0, 0, null); g.dispose();
        return new Image(b, true);
    }

    public Graphics getGraphics() {
        if (graphics == null) graphics = new Graphics(image);
        else graphics.resetAll();
        return graphics;
    }
    public boolean isMutable() { return mutable; }
    public int getWidth() { return image.getWidth(); }
    public int getHeight() { return image.getHeight(); }

    public void getRGB(int[] rgb, int offset, int scanlength, int x, int y, int w, int h) {
        if (rgb == null) throw new NullPointerException("rgbData");
        image.getRGB(x, y, w, h, rgb, offset, scanlength);
    }
}
