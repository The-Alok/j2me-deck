// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 J2ME Deck contributors
package j2medeck.ui;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;

/** The Settings tab: a centred column of big rows over a calm gradient. */
final class SettingsPage extends JComponent {
    final RowList list = new RowList();
    private BufferedImage bg;

    SettingsPage() {
        setOpaque(true);
        add(list);
    }

    @Override public void doLayout() {
        int u = Theme.base(), top = TopBar.heightFor(Math.max(13, Math.min(u, getHeight() / 28))) + u / 2;
        int w = Math.min(Math.round(u * 48), getWidth() - u * 3);
        list.setBounds((getWidth() - w) / 2, top, w, Math.max(0, getHeight() - top - u / 2));
    }

    @Override protected void paintComponent(Graphics g0) {
        Graphics2D g = Gfx.aa(g0);
        if (bg == null || bg.getWidth() != getWidth() || bg.getHeight() != getHeight()) {
            BufferedImage half = ShelfView.wash(null, getWidth(), getHeight());
            bg = new BufferedImage(Math.max(1, getWidth()), Math.max(1, getHeight()), BufferedImage.TYPE_INT_RGB);
            Graphics2D bgG = bg.createGraphics();
            bgG.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            bgG.drawImage(half, 0, 0, bg.getWidth(), bg.getHeight(), null);
            bgG.dispose();
        }
        g.drawImage(bg, 0, 0, null);
        g.dispose();
    }
}
