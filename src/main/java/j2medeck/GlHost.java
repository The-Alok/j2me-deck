// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 J2ME Deck contributors
package j2medeck;

import java.util.List;

/** What the OpenGL ES surface needs from the game view that hosts it. Plain Java 17, so the main jar never links against the GL classes. */
public interface GlHost {
    /** Size of the game framebuffer in pixels. */
    int frameWidth();
    int frameHeight();
    /** Copies the current frame as 0x00RRGGBB ints (frameWidth*frameHeight entries). */
    void copyFrame(int[] dst);
    /** Adds the small overlay images (toast, pause tag) to draw this frame, positioned for a surface of the given size. */
    void overlays(int surfaceW, int surfaceH, List<GlOverlay> out);
    /** The GL path cannot be used (init failure, lost window...); the host should fall back to Swing painting. Any thread. */
    void glFailed(String reason);
}
