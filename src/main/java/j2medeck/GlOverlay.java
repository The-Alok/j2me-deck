// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 J2ME Deck contributors
package j2medeck;

import java.awt.image.BufferedImage;

/** A small ARGB picture drawn on top of the game frame. {@code key} changes whenever the picture's content changes. */
public final class GlOverlay {
    public final String key;
    public final BufferedImage image;
    public final int x, y;

    public GlOverlay(String key, BufferedImage image, int x, int y) { this.key = key; this.image = image; this.x = x; this.y = y; }
}
