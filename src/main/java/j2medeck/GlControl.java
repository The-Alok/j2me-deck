// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 J2ME Deck contributors
package j2medeck;

/** Handle to a running OpenGL ES surface (implemented by the AWT canvas the GL backend returns). */
public interface GlControl {
    /** Ask for a redraw (game frame, overlay or window change). Cheap and callable from any thread. */
    void requestFrame();
    /** Blocks (at most {@code ms}) until everything requested so far has been presented. Returns false on timeout. */
    boolean awaitPresented(long ms);
    /** Stops the render thread and releases GL/EGL objects. */
    void shutdown();
}
