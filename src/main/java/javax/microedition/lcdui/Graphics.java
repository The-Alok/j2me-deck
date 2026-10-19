// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 J2ME Deck contributors
package javax.microedition.lcdui;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;

/** MIDP 2.0 Graphics on top of Java2D. Coordinates, clip and translate follow MIDP semantics. */
public class Graphics {
    public static final int HCENTER = 1, VCENTER = 2, LEFT = 4, RIGHT = 8, TOP = 16, BOTTOM = 32, BASELINE = 64;
    public static final int SOLID = 0, DOTTED = 1;

    private static final BasicStroke SOLID_STROKE = new BasicStroke(1f);
    private static final BasicStroke DOTTED_STROKE =
            new BasicStroke(1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f, new float[]{1f, 1f}, 0f);

    private final BufferedImage target;
    private final Graphics2D g;
    private Font font = Font.getDefaultFont();
    private int rgb;
    private int strokeStyle = SOLID;
    private int tx, ty;
    private BufferedImage rgbScratch;

    Graphics(BufferedImage target) {
        this.target = target;
        g = target.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_OFF);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        g.setComposite(AlphaComposite.SrcOver);
        reset(0, 0, target.getWidth(), target.getHeight());
    }

    /** Restores default state (origin, clip rect, black, default font, solid stroke). */
    void reset(int x, int y, int w, int h) {
        g.setTransform(new AffineTransform());
        tx = ty = 0;
        g.setClip(x, y, Math.max(0, w), Math.max(0, h));
        setColor(0);
        setFont(Font.getDefaultFont());
        setStrokeStyle(SOLID);
    }

    void resetAll() { reset(0, 0, target.getWidth(), target.getHeight()); }

    Graphics2D awt() { return g; }
    /** Vendor-API access (com.nokia.*): the underlying Java2D context in MIDP user space. */
    public java.awt.Graphics2D $awt() { return g; }
    public java.awt.image.BufferedImage $target() { return target; }
    /** Read pixels of the drawing surface (device coordinates, MIDP Image.getRGB argument order). Used by 3D renderers. */
    public void $getRGB(int[] data, int offset, int scanlength, int x, int y, int w, int h) {
        target.getRGB(x, y, w, h, data, offset, scanlength);
    }
    BufferedImage targetImage() { return target; }

    // ---- colour ----
    public void setColor(int r, int gr, int b) {
        if ((r | gr | b) < 0 || r > 255 || gr > 255 || b > 255) throw new IllegalArgumentException("color out of range");
        setColor((r << 16) | (gr << 8) | b);
    }
    public void setColor(int color) {
        color &= 0xFFFFFF;
        rgb = color;
        g.setColor(new Color(color));
    }
    public void setGrayScale(int v) {
        if (v < 0 || v > 255) throw new IllegalArgumentException("gray out of range");
        setColor(v, v, v);
    }
    public int getColor() { return rgb; }
    public int getRedComponent() { return (rgb >> 16) & 255; }
    public int getGreenComponent() { return (rgb >> 8) & 255; }
    public int getBlueComponent() { return rgb & 255; }
    public int getGrayScale() { return (getRedComponent() * 30 + getGreenComponent() * 59 + getBlueComponent() * 11) / 100; }
    public int getDisplayColor(int c) { return c & 0xFFFFFF; }

    // ---- stroke / font ----
    public void setStrokeStyle(int style) {
        if (style != SOLID && style != DOTTED) throw new IllegalArgumentException("stroke style");
        strokeStyle = style;
        g.setStroke(style == DOTTED ? DOTTED_STROKE : SOLID_STROKE);
    }
    public int getStrokeStyle() { return strokeStyle; }
    public void setFont(Font f) {
        font = f == null ? Font.getDefaultFont() : f;
        g.setFont(font.awt());
    }
    public Font getFont() { return font; }

    // ---- clip / translate ----
    public void setClip(int x, int y, int w, int h) { g.setClip(x, y, Math.max(0, w), Math.max(0, h)); }
    public void clipRect(int x, int y, int w, int h) { g.clipRect(x, y, Math.max(0, w), Math.max(0, h)); }
    public int getClipX() { Rectangle b = g.getClipBounds(); return b == null ? 0 : b.x; }
    public int getClipY() { Rectangle b = g.getClipBounds(); return b == null ? 0 : b.y; }
    public int getClipWidth() { Rectangle b = g.getClipBounds(); return b == null ? target.getWidth() : b.width; }
    public int getClipHeight() { Rectangle b = g.getClipBounds(); return b == null ? target.getHeight() : b.height; }
    public void translate(int dx, int dy) { g.translate(dx, dy); tx += dx; ty += dy; }
    public int getTranslateX() { return tx; }
    public int getTranslateY() { return ty; }

    // ---- primitives ----
    public void drawLine(int x1, int y1, int x2, int y2) { g.drawLine(x1, y1, x2, y2); }
    public void drawRect(int x, int y, int w, int h) { if (w >= 0 && h >= 0) g.drawRect(x, y, w, h); }
    public void fillRect(int x, int y, int w, int h) { if (w > 0 && h > 0) g.fillRect(x, y, w, h); }
    public void drawRoundRect(int x, int y, int w, int h, int aw, int ah) { if (w >= 0 && h >= 0) g.drawRoundRect(x, y, w, h, aw, ah); }
    public void fillRoundRect(int x, int y, int w, int h, int aw, int ah) { if (w > 0 && h > 0) g.fillRoundRect(x, y, w, h, aw, ah); }
    public void drawArc(int x, int y, int w, int h, int sa, int aa) { if (w >= 0 && h >= 0) g.drawArc(x, y, w, h, sa, aa); }
    public void fillArc(int x, int y, int w, int h, int sa, int aa) { if (w > 0 && h > 0) g.fillArc(x, y, w, h, sa, aa); }
    public void fillTriangle(int x1, int y1, int x2, int y2, int x3, int y3) {
        g.fillPolygon(new int[]{x1, x2, x3}, new int[]{y1, y2, y3}, 3);
    }

    // ---- text ----
    public void drawString(String s, int x, int y, int anchor) {
        if (s == null) throw new NullPointerException("str");
        FontMetrics fm = g.getFontMetrics();
        int w = fm.stringWidth(s);
        if ((anchor & HCENTER) != 0) x -= w / 2; else if ((anchor & RIGHT) != 0) x -= w;
        if ((anchor & BASELINE) != 0) { /* y already baseline */ }
        else if ((anchor & BOTTOM) != 0) y -= fm.getHeight() - fm.getAscent();
        else y += fm.getAscent();
        g.drawString(s, x, y);
    }
    public void drawSubstring(String s, int off, int len, int x, int y, int anchor) {
        if (s == null) throw new NullPointerException("str");
        if (off < 0 || len < 0 || off + len > s.length()) throw new StringIndexOutOfBoundsException();
        drawString(s.substring(off, off + len), x, y, anchor);
    }
    public void drawChar(char c, int x, int y, int anchor) { drawString(String.valueOf(c), x, y, anchor); }
    public void drawChars(char[] d, int off, int len, int x, int y, int anchor) { drawString(new String(d, off, len), x, y, anchor); }

    // ---- images ----
    private int ax(int x, int w, int anchor) {
        if ((anchor & HCENTER) != 0) return x - w / 2;
        if ((anchor & RIGHT) != 0) return x - w;
        return x;
    }
    private int ay(int y, int h, int anchor) {
        if ((anchor & VCENTER) != 0) return y - h / 2;
        if ((anchor & BOTTOM) != 0) return y - h;
        return y;
    }

    public void drawImage(Image img, int x, int y, int anchor) {
        if (img == null) return;
        g.drawImage(img.awt(), ax(x, img.getWidth(), anchor), ay(y, img.getHeight(), anchor), null);
    }

    /** MIDP 2.0 drawRegion: draws a (possibly transformed) sub-rectangle without allocating. */
    public void drawRegion(Image src, int sx, int sy, int w, int h, int transform, int x, int y, int anchor) {
        if (src == null) throw new NullPointerException("src");
        if (transform < 0 || transform > 7) throw new IllegalArgumentException("transform");
        if (w < 0 || h < 0 || sx < 0 || sy < 0 || sx + w > src.getWidth() || sy + h > src.getHeight())
            throw new IllegalArgumentException("region outside image");
        if (w == 0 || h == 0) return;
        boolean swap = (transform & 4) != 0;
        int dw = swap ? h : w, dh = swap ? w : h;
        int dx = ax(x, dw, anchor), dy = ay(y, dh, anchor);
        if (transform == Image.TRANS_NONE) {
            g.drawImage(src.awt(), dx, dy, dx + w, dy + h, sx, sy, sx + w, sy + h, null);
            return;
        }
        java.awt.Shape oldClip = g.getClip();
        AffineTransform oldT = g.getTransform();
        g.clipRect(dx, dy, dw, dh);
        AffineTransform t = new AffineTransform();
        t.translate(dx, dy);
        t.concatenate(Image.transformMatrix(transform, w, h));
        t.translate(-sx, -sy);
        g.transform(t);
        g.drawImage(src.awt(), 0, 0, null);
        g.setTransform(oldT);
        g.setClip(oldClip);
    }

    public void drawRGB(int[] data, int offset, int scanlength, int x, int y, int w, int h, boolean processAlpha) {
        if (data == null) throw new NullPointerException("rgbData");
        if (w <= 0 || h <= 0) return;
        if (rgbScratch == null || rgbScratch.getWidth() != w || rgbScratch.getHeight() != h)
            rgbScratch = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        int[] dst = ((java.awt.image.DataBufferInt) rgbScratch.getRaster().getDataBuffer()).getData();
        for (int row = 0; row < h; row++) {
            int si = offset + row * scanlength, di = row * w;
            if (processAlpha) System.arraycopy(data, si, dst, di, w);
            else for (int i = 0; i < w; i++) dst[di + i] = data[si + i] | 0xFF000000;
        }
        rgbScratch.flush();
        g.drawImage(rgbScratch, x, y, null);
    }

    /** MIDP 2.0 copyArea (mutable images / offscreen only). Source coords are in the translated space. */
    public void copyArea(int xs, int ys, int w, int h, int xd, int yd, int anchor) {
        if (w <= 0 || h <= 0) return;
        int dx = ax(xd, w, anchor), dy = ay(yd, h, anchor);
        BufferedImage copy = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        java.awt.Graphics2D cg = copy.createGraphics();
        cg.setComposite(AlphaComposite.Src);
        cg.drawImage(target, 0, 0, w, h, xs + tx, ys + ty, xs + tx + w, ys + ty + h, null);
        cg.dispose();
        g.drawImage(copy, dx, dy, null);
    }
}
