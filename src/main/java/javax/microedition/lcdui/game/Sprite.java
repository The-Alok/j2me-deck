// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 J2ME Deck contributors
package javax.microedition.lcdui.game;

import javax.microedition.lcdui.Graphics;
import javax.microedition.lcdui.Image;

public class Sprite extends Layer {
    public static final int TRANS_NONE = 0, TRANS_MIRROR_ROT180 = 1, TRANS_MIRROR = 2, TRANS_ROT180 = 3,
            TRANS_MIRROR_ROT270 = 4, TRANS_ROT90 = 5, TRANS_ROT270 = 6, TRANS_MIRROR_ROT90 = 7;

    private Image image;
    private int fw, fh, frames;
    private int[] seq;
    private int seqIndex;
    private int refX, refY;
    private int cx, cy, cw, ch;   // collision rect in untransformed frame coords
    private int transform;
    private int[] pixels;

    public Sprite(Image image) { this(image, image.getWidth(), image.getHeight()); }
    public Sprite(Image image, int frameW, int frameH) {
        super(frameW, frameH);
        initImage(image, frameW, frameH);
        defineCollisionRectangle(0, 0, frameW, frameH);
    }
    public Sprite(Sprite s) {
        super(s.fw, s.fh);
        if (s == null) throw new NullPointerException();
        image = s.image; fw = s.fw; fh = s.fh; frames = s.frames; pixels = s.pixels;
        seq = s.seq.clone(); seqIndex = s.seqIndex; refX = s.refX; refY = s.refY;
        cx = s.cx; cy = s.cy; cw = s.cw; ch = s.ch; transform = s.transform;
        x = s.x; y = s.y; visible = s.visible; width = s.width; height = s.height;
    }

    private void initImage(Image img, int w, int h) {
        if (img == null) throw new NullPointerException();
        if (w < 1 || h < 1 || img.getWidth() % w != 0 || img.getHeight() % h != 0) throw new IllegalArgumentException("frame size");
        image = img; fw = w; fh = h;
        frames = (img.getWidth() / w) * (img.getHeight() / h);
        seq = new int[frames];
        for (int i = 0; i < frames; i++) seq[i] = i;
        seqIndex = 0;
        pixels = null;
    }

    public void setImage(Image img, int w, int h) {
        int oldFrames = frames, oldW = fw, oldH = fh;
        initImage(img, w, h);
        if (w != oldW || h != oldH) {
            int rx = getRefPixelX(), ry = getRefPixelY();
            width = (transform & 4) != 0 ? h : w; height = (transform & 4) != 0 ? w : h;
            defineCollisionRectangle(0, 0, w, h);
            refX = 0; refY = 0;
            setRefPixelPosition(rx, ry);
        }
        if (frames < oldFrames) { seqIndex = 0; }
    }

    // ---- transform helpers (discrete pixel mapping, MIDP semantics) ----
    private int mapX(int t, int px, int py) {
        switch (t) {
            case TRANS_ROT90: case TRANS_MIRROR_ROT90: return fh - 1 - py;
            case TRANS_ROT180: case TRANS_MIRROR: return fw - 1 - px;
            case TRANS_ROT270: case TRANS_MIRROR_ROT270: return py;
            default: return px;
        }
    }
    private int mapY(int t, int px, int py) {
        switch (t) {
            case TRANS_ROT90: return px;
            case TRANS_ROT180: case TRANS_MIRROR_ROT180: return fh - 1 - py;
            case TRANS_ROT270: return fw - 1 - px;
            case TRANS_MIRROR_ROT90: return fw - 1 - px;
            case TRANS_MIRROR_ROT270: return px;
            default: return py;
        }
    }

    public void defineReferencePixel(int rx, int ry) { refX = rx; refY = ry; }
    public int getRefPixelX() { return x + mapX(transform, refX, refY); }
    public int getRefPixelY() { return y + mapY(transform, refX, refY); }
    public void setRefPixelPosition(int px, int py) { x = px - mapX(transform, refX, refY); y = py - mapY(transform, refX, refY); }

    public void setTransform(int t) {
        if (t < 0 || t > 7) throw new IllegalArgumentException();
        int rx = getRefPixelX(), ry = getRefPixelY();
        transform = t;
        boolean swap = (t & 4) != 0;
        width = swap ? fh : fw; height = swap ? fw : fh;
        setRefPixelPosition(rx, ry);
    }

    // ---- frames ----
    public final int getFrame() { return seqIndex; }
    public void setFrame(int i) { if (i < 0 || i >= seq.length) throw new IndexOutOfBoundsException(); seqIndex = i; }
    public final int getRawFrameCount() { return frames; }
    public int getFrameSequenceLength() { return seq.length; }
    public void nextFrame() { seqIndex = (seqIndex + 1) % seq.length; }
    public void prevFrame() { seqIndex = (seqIndex + seq.length - 1) % seq.length; }
    public void setFrameSequence(int[] s) {
        if (s == null) { seq = new int[frames]; for (int i = 0; i < frames; i++) seq[i] = i; seqIndex = 0; return; }
        if (s.length < 1) throw new IllegalArgumentException();
        for (int v : s) if (v < 0 || v >= frames) throw new ArrayIndexOutOfBoundsException();
        seq = s.clone(); seqIndex = 0;
    }

    @Override public void paint(Graphics g) {
        if (g == null) throw new NullPointerException();
        if (!visible) return;
        int raw = seq[seqIndex], perRow = image.getWidth() / fw;
        g.drawRegion(image, (raw % perRow) * fw, (raw / perRow) * fh, fw, fh, transform, x, y, Graphics.TOP | Graphics.LEFT);
    }

    // ---- collisions ----
    public void defineCollisionRectangle(int rx, int ry, int w, int h) {
        if (w < 0 || h < 0) throw new IllegalArgumentException();
        cx = rx; cy = ry; cw = w; ch = h;
    }

    /** Collision rectangle in layer coordinates after transform: {left, top, right, bottom} (exclusive). */
    private int[] rect() {
        if (cw == 0 || ch == 0) return new int[]{x, y, x, y};
        int x1 = mapX(transform, cx, cy), y1 = mapY(transform, cx, cy);
        int x2 = mapX(transform, cx + cw - 1, cy + ch - 1), y2 = mapY(transform, cx + cw - 1, cy + ch - 1);
        return new int[]{x + Math.min(x1, x2), y + Math.min(y1, y2), x + Math.max(x1, x2) + 1, y + Math.max(y1, y2) + 1};
    }

    private boolean opaqueAt(int px, int py) {
        int lx = px - x, ly = py - y, sx, sy;
        switch (transform) {
            case TRANS_ROT90: sx = ly; sy = fh - 1 - lx; break;
            case TRANS_ROT180: sx = fw - 1 - lx; sy = fh - 1 - ly; break;
            case TRANS_ROT270: sx = fw - 1 - ly; sy = lx; break;
            case TRANS_MIRROR: sx = fw - 1 - lx; sy = ly; break;
            case TRANS_MIRROR_ROT90: sx = fw - 1 - ly; sy = fh - 1 - lx; break;
            case TRANS_MIRROR_ROT180: sx = lx; sy = fh - 1 - ly; break;
            case TRANS_MIRROR_ROT270: sx = ly; sy = lx; break;
            default: sx = lx; sy = ly;
        }
        if (sx < 0 || sy < 0 || sx >= fw || sy >= fh) return false;
        if (pixels == null) {
            pixels = new int[image.getWidth() * image.getHeight()];
            image.getRGB(pixels, 0, image.getWidth(), 0, 0, image.getWidth(), image.getHeight());
        }
        int raw = seq[seqIndex], perRow = image.getWidth() / fw;
        int ix = (raw % perRow) * fw + sx, iy = (raw / perRow) * fh + sy;
        return (pixels[iy * image.getWidth() + ix] >>> 24) != 0;
    }

    public final boolean collidesWith(Sprite s, boolean pixelLevel) {
        if (s == null) throw new NullPointerException();
        if (!visible || !s.visible) return false;
        int[] a = rect(), b = s.rect();
        int l = Math.max(a[0], b[0]), t = Math.max(a[1], b[1]), r = Math.min(a[2], b[2]), btm = Math.min(a[3], b[3]);
        if (l >= r || t >= btm) return false;
        if (!pixelLevel) return true;
        for (int py = t; py < btm; py++) for (int px = l; px < r; px++)
            if (opaqueAt(px, py) && s.opaqueAt(px, py)) return true;
        return false;
    }

    public final boolean collidesWith(TiledLayer tl, boolean pixelLevel) {
        if (tl == null) throw new NullPointerException();
        if (!visible || !tl.isVisible()) return false;
        int[] a = rect();
        int tw = tl.getCellWidth(), th = tl.getCellHeight();
        int c0 = Math.max(0, (a[0] - tl.getX()) / tw), c1 = Math.min(tl.getColumns() - 1, (a[2] - 1 - tl.getX()) / tw);
        int r0 = Math.max(0, (a[1] - tl.getY()) / th), r1 = Math.min(tl.getRows() - 1, (a[3] - 1 - tl.getY()) / th);
        if (a[0] - tl.getX() < 0) c0 = 0;
        if (a[1] - tl.getY() < 0) r0 = 0;
        for (int r = r0; r <= r1; r++) for (int c = c0; c <= c1; c++) {
            int tile = tl.resolve(c, r);
            if (tile == 0) continue;
            int left = tl.getX() + c * tw, top = tl.getY() + r * th;
            int l = Math.max(a[0], left), t = Math.max(a[1], top), rr = Math.min(a[2], left + tw), bb = Math.min(a[3], top + th);
            if (l >= rr || t >= bb) continue;
            if (!pixelLevel) return true;
            for (int py = t; py < bb; py++) for (int px = l; px < rr; px++)
                if (opaqueAt(px, py) && tl.tilePixelOpaque(tile, px - left, py - top)) return true;
        }
        return false;
    }

    public final boolean collidesWith(Image img, int ix, int iy, boolean pixelLevel) {
        if (img == null) throw new NullPointerException();
        if (!visible) return false;
        int[] a = rect();
        int l = Math.max(a[0], ix), t = Math.max(a[1], iy), r = Math.min(a[2], ix + img.getWidth()), b = Math.min(a[3], iy + img.getHeight());
        if (l >= r || t >= b) return false;
        if (!pixelLevel) return true;
        int[] px = new int[1];
        for (int py = t; py < b; py++) for (int pxx = l; pxx < r; pxx++) {
            img.getRGB(px, 0, 1, pxx - ix, py - iy, 1, 1);
            if (opaqueAt(pxx, py) && (px[0] >>> 24) != 0) return true;
        }
        return false;
    }
}
