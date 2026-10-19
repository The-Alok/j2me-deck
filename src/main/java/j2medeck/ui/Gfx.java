// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 J2ME Deck contributors
package j2medeck.ui;

import java.awt.*;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

/** Painting helpers shared by the console-style screens. */
final class Gfx {
    static final Color BTN_A = new Color(0x5CC46B), BTN_B = new Color(0xE5604F), BTN_X = new Color(0x4C94EA), BTN_Y = new Color(0xF2C230);
    static final Color PLAY_TEXT = new Color(0x1A1300);

    private Gfx() {}

    static Graphics2D aa(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_GASP);
        g2.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
        g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        return g2;
    }

    static int clamp255(double v) { return (int) Math.max(0, Math.min(255, Math.round(v))); }
    static Color alpha(Color c, double a) { return new Color(c.getRed(), c.getGreen(), c.getBlue(), clamp255(a * 255)); }
    static Color mix(Color a, Color b, double t) {
        t = Math.max(0, Math.min(1, t));
        return new Color(clamp255(a.getRed() + (b.getRed() - a.getRed()) * t), clamp255(a.getGreen() + (b.getGreen() - a.getGreen()) * t),
                clamp255(a.getBlue() + (b.getBlue() - a.getBlue()) * t), clamp255(a.getAlpha() + (b.getAlpha() - a.getAlpha()) * t));
    }

    static RoundRectangle2D rr(double x, double y, double w, double h, double arc) { return new RoundRectangle2D.Double(x, y, w, h, arc, arc); }

    private static final java.util.Map<String, BufferedImage> SHADOWS = new java.util.LinkedHashMap<>(16, 0.75f, true) {
        @Override protected boolean removeEldestEntry(java.util.Map.Entry<String, BufferedImage> e) { return size() > 40; }
    };

    /**
     * Soft drop shadow: stacked translucent rounded rectangles, rendered once per (size, blur, strength) and then just blitted,
     * because redrawing dozens of anti-aliased layers per tile every frame is far too slow for animation.
     */
    static void shadow(Graphics2D g, double x, double y, double w, double h, double arc, int blur, double strength, int dy) {
        blur = Math.max(2, (blur + 1) / 2 * 2);                      // coarse steps keep the cache small while the focus animates
        int q = (int) Math.round(strength * 10), iw = (int) Math.round(w), ih = (int) Math.round(h), ia = (int) Math.round(arc);
        final int fb = blur;
        BufferedImage img;
        synchronized (SHADOWS) {
            img = SHADOWS.computeIfAbsent(iw + "x" + ih + "/" + ia + "/" + blur + "/" + q, k -> {
                BufferedImage out = new BufferedImage(iw + 2 * fb, ih + 2 * fb, BufferedImage.TYPE_INT_ARGB);
                Graphics2D sg = out.createGraphics();
                sg.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                for (int i = fb; i >= 1; i--) {
                    double t = 1.0 - (double) i / (fb + 1);
                    sg.setColor(new Color(0, 0, 0, clamp255(q / 10.0 * 255 * t * t * 0.15)));
                    sg.fill(rr(fb - i, fb - i, iw + 2 * i, ih + 2 * i, ia + 2 * i));
                }
                sg.dispose();
                return out;
            });
        }
        g.drawImage(img, (int) Math.round(x - blur), (int) Math.round(y - blur + dy), null);
    }

    private static final Graphics2D MEASURE = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB).createGraphics();
    private static synchronized FontMetrics measure(Font f) { return MEASURE.getFontMetrics(f); }

    static String fit(FontMetrics fm, String s, int maxW) {
        if (s == null) return "";
        if (fm.stringWidth(s) <= maxW) return s;
        String e = "\u2026";
        int n = s.length();
        while (n > 1 && fm.stringWidth(s.substring(0, n) + e) > maxW) n--;
        return s.substring(0, n).trim() + e;
    }

    static List<String> wrap(FontMetrics fm, String text, int maxW) {
        List<String> out = new ArrayList<>();
        for (String para : text.split("\n", -1)) {
            StringBuilder line = new StringBuilder();
            for (String w : para.split(" ")) {
                String t = line.length() == 0 ? w : line + " " + w;
                if (fm.stringWidth(t) > maxW && line.length() > 0) { out.add(line.toString()); line = new StringBuilder(w); }
                else { line = new StringBuilder(t); }
            }
            out.add(line.toString());
        }
        return out;
    }

    /** Controller-button badge ("A", "B", "X", "Y", "LB", "RB" ...). Returns its width; pass g == null to just measure. */
    static int glyph(Graphics2D g, Font base, String label, int x, int cy, int h) {
        Font f = base.deriveFont(Font.BOLD, h * 0.58f);
        FontMetrics fm = g != null ? g.getFontMetrics(f) : measure(f);
        boolean round = label.length() == 1;
        int w = round ? h : fm.stringWidth(label) + h / 2 + 4;
        if (g != null) {
            Color c = switch (label) { case "A" -> BTN_A; case "B" -> BTN_B; case "X" -> BTN_X; case "Y" -> BTN_Y; default -> new Color(0x5A6B91); };
            g.setColor(c);
            if (round) g.fillOval(x, cy - h / 2, h, h); else g.fill(rr(x, cy - h / 2.0, w, h, h * 0.55));
            g.setColor(round && label.equals("Y") ? PLAY_TEXT : Color.WHITE);
            g.setFont(f);
            g.drawString(label, x + (w - fm.stringWidth(label)) / 2f, cy + (fm.getAscent() - fm.getDescent()) / 2f);
        }
        return w;
    }

    // ------------------------------------------------------------------ icons
    static BufferedImage scaleIcon(BufferedImage src, int target) {
        int w = src.getWidth(), h = src.getHeight(), m = Math.max(w, h);
        double s = (double) target / m;
        if (s >= 1.5) {                                // pixel art: crisp integer scaling
            int k = (int) Math.round(s);
            while (k > 1 && k * m > target * 1.22) k--;
            return resize(src, w * k, h * k, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        }
        int tw = Math.max(1, (int) Math.round(w * s)), th = Math.max(1, (int) Math.round(h * s));
        BufferedImage cur = src;
        while (cur.getWidth() / 2 >= tw && cur.getHeight() / 2 >= th) cur = resize(cur, cur.getWidth() / 2, cur.getHeight() / 2, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        return resize(cur, tw, th, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
    }

    static BufferedImage resize(BufferedImage src, int w, int h, Object interp) {
        BufferedImage out = new BufferedImage(Math.max(1, w), Math.max(1, h), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, interp);
        g.drawImage(src, 0, 0, out.getWidth(), out.getHeight(), null);
        g.dispose();
        return out;
    }

    /** Average colour of the saturated, opaque pixels, nudged into a range that works as a background tint. */
    static Color dominant(BufferedImage img) {
        double r = 0, gr = 0, b = 0, wsum = 0;
        int step = Math.max(1, Math.max(img.getWidth(), img.getHeight()) / 48);
        for (int y = 0; y < img.getHeight(); y += step)
            for (int x = 0; x < img.getWidth(); x += step) {
                int p = img.getRGB(x, y);
                if ((p >>> 24) < 160) continue;
                float[] hsb = Color.RGBtoHSB((p >> 16) & 255, (p >> 8) & 255, p & 255, null);
                double wgt = 0.04 + hsb[1] * hsb[2] * hsb[2];
                r += ((p >> 16) & 255) * wgt; gr += ((p >> 8) & 255) * wgt; b += (p & 255) * wgt; wsum += wgt;
            }
        if (wsum == 0) return null;
        float[] hsb = Color.RGBtoHSB((int) (r / wsum), (int) (gr / wsum), (int) (b / wsum), null);
        return Color.getHSBColor(hsb[0], Math.max(0.28f, Math.min(0.8f, hsb[1])), Math.max(0.42f, Math.min(0.85f, hsb[2])));
    }
}
