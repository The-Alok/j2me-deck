// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 J2ME Deck contributors
package j2medeck.ui;

import j2medeck.GameInfo;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/** Decoded game icons with a dominant colour (used for tile gradients and the screen-wide background wash). */
final class Covers {
    static final class Cover {
        final BufferedImage img;            // null when the jar has no usable icon
        final Color tint;
        final String letter;
        private final Map<Integer, BufferedImage> scaled = new HashMap<>();
        private BufferedImage card;
        private int cardSize, cardArc;

        Cover(BufferedImage img, Color tint, String letter) { this.img = img; this.tint = tint; this.letter = letter; }

        /** The finished cover card (gradient, icon, highlight, border) as an image, rendered once per size so tiles are a single blit per frame. */
        BufferedImage card(int size, double arc) {
            if (card == null || cardSize != size || cardArc != (int) arc) {
                BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
                Graphics2D g = Gfx.aa(img.createGraphics());
                Covers.paint(g, this, new Rectangle(0, 0, size, size), arc);
                g.dispose();
                card = img; cardSize = size; cardArc = (int) arc;
            }
            return card;
        }

        /** The icon scaled so its longer side is about {@code size} px (cached). Null for icon-less games. */
        BufferedImage at(int size) {
            return img == null ? null : scaled.computeIfAbsent(size, s -> Gfx.scaleIcon(img, s));
        }
    }

    private static final Map<Path, Cover> CACHE = new HashMap<>();

    private Covers() {}

    static synchronized Cover of(GameInfo g) {
        return CACHE.computeIfAbsent(g.jar, p -> {
            BufferedImage img = null;
            if (g.iconBytes != null) try { img = ImageIO.read(new ByteArrayInputStream(g.iconBytes)); } catch (IOException | RuntimeException ignored) {}
            String name = g.name();
            String letter = name.isEmpty() ? "?" : name.substring(0, 1).toUpperCase();
            Color tint = img != null ? Gfx.dominant(img) : null;
            if (tint == null) tint = Color.getHSBColor((Math.abs(name.hashCode()) % 360) / 360f, 0.5f, 0.6f);
            return new Cover(img, tint, letter);
        });
    }

    static synchronized void forget(GameInfo g) { CACHE.remove(g.jar); }

    /** Paints the cover card: tinted gradient, centred icon (or the game's first letter), soft highlight and hairline border. */
    static void paint(Graphics2D g, Cover cv, Rectangle r, double arc) {
        Shape shape = Gfx.rr(r.x, r.y, r.width, r.height, arc);
        g.setPaint(new GradientPaint(r.x, r.y, Gfx.mix(cv.tint, Color.WHITE, 0.14), r.x + r.width * 0.3f, r.y + r.height, Gfx.mix(cv.tint, Theme.BG, 0.62)));
        g.fill(shape);
        // top highlight: same rounded shape with a fading gradient (a clip would force slow non-rectangular clipping every frame)
        g.setPaint(new GradientPaint(0, r.y, Gfx.alpha(Color.WHITE, 0.16), 0, r.y + r.height * 0.55f, Gfx.alpha(Color.WHITE, 0.0)));
        g.fill(shape);
        double cx = r.x + r.width / 2.0, cy = r.y + r.height / 2.0;
        BufferedImage ic = cv.at(Math.round(r.width * 0.62f));
        if (ic != null) {
            g.drawImage(ic, (int) Math.round(cx - ic.getWidth() / 2.0), (int) Math.round(cy - ic.getHeight() / 2.0), null);
        } else {
            g.setFont(Theme.font(Font.BOLD, 1f).deriveFont(Font.BOLD, r.width * 0.46f));
            FontMetrics fm = g.getFontMetrics();
            g.setColor(Gfx.alpha(Color.WHITE, 0.92));
            g.drawString(cv.letter, (float) (cx - fm.stringWidth(cv.letter) / 2.0), (float) (cy + (fm.getAscent() - fm.getDescent()) / 2.0));
        }
        g.setColor(Gfx.alpha(Color.WHITE, 0.14));
        g.setStroke(new BasicStroke(1.2f));
        g.draw(Gfx.rr(r.x + .5, r.y + .5, r.width - 1, r.height - 1, arc));
    }
}
