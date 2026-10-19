// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 J2ME Deck contributors
package j2medeck.ui;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/** Bottom strip: status on the left, button hints on the right (controller glyphs, or touch tips after the screen was touched). */
final class HintBar extends JComponent {
    private final List<String[]> hints = new ArrayList<>();      // {glyph, label}
    private String touch = "", status = "";
    private boolean lastPointer;

    HintBar() {
        setOpaque(false);
        // keep the hints honest: repaint when the last-used input device changes
        new Timer(300, e -> { if (lastPointer != Gesture.pointerMode) { lastPointer = Gesture.pointerMode; repaint(); } }).start();
    }

    @Override public Dimension getPreferredSize() { return new Dimension(100, Math.round(Theme.base() * 2.9f)); }

    void setHints(String touchText, String... glyphLabelPairs) {
        hints.clear();
        for (int i = 0; i + 1 < glyphLabelPairs.length; i += 2) hints.add(new String[]{glyphLabelPairs[i], glyphLabelPairs[i + 1]});
        touch = touchText;
        repaint();
    }
    void setStatus(String s) { status = s == null ? "" : s; repaint(); }

    @Override protected void paintComponent(Graphics g0) {
        Graphics2D g = Gfx.aa(g0);
        int w = getWidth(), h = getHeight(), cy = h / 2;
        int u = Math.max(10, Math.round(h / 2.9f));
        g.setColor(new Color(0x0B111E));
        g.fillRect(0, 0, w, h);
        g.setColor(Theme.BORDER);
        g.fillRect(0, 0, w, 1);
        Font f = Theme.font(Font.PLAIN, 0.9f).deriveFont(Font.PLAIN, u * 0.9f);
        g.setFont(f);
        FontMetrics fm = g.getFontMetrics();
        int base = cy + (fm.getAscent() - fm.getDescent()) / 2;
        int rx = w - Math.round(u * 1.6f), usedLeft = rx;
        if (Gesture.pointerMode) {
            g.setColor(Theme.MUTED);
            String t = Gfx.fit(fm, touch, w - Math.round(u * 12f));
            g.drawString(t, rx - fm.stringWidth(t), base);
            usedLeft = rx - fm.stringWidth(t);
        } else {
            int gh = Math.round(u * 1.5f);
            for (int i = hints.size() - 1; i >= 0; i--) {
                String[] hnt = hints.get(i);
                rx -= fm.stringWidth(hnt[1]);
                g.setColor(Theme.TEXT);
                g.setFont(f);
                if (!hnt[1].isEmpty()) g.drawString(hnt[1], rx, base);
                int gw = Gfx.glyph(null, f, hnt[0], 0, 0, gh);
                rx -= gw + (hnt[1].isEmpty() ? 0 : u / 3);
                Gfx.glyph(g, f, hnt[0], rx, cy, gh);
                usedLeft = rx;
                rx -= Math.round(u * (i > 0 && hints.get(i - 1)[1].isEmpty() ? 0.35f : 1.4f));
            }
        }
        int room = usedLeft - Math.round(u * 3.2f);
        if (room > u * 4) {
            g.setColor(Theme.MUTED);
            g.setFont(f);
            g.drawString(Gfx.fit(fm, status, room), Math.round(u * 1.6f), base);
        }
        g.dispose();
    }
}
