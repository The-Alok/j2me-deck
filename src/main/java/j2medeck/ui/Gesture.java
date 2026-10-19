// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 J2ME Deck contributors
package j2medeck.ui;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;

/**
 * Turns the mouse events the Steam Deck's touchscreen produces into taps, long presses, drags and flings.
 * (Swing only ever sees one pointer, so multi-finger gestures are not possible.)
 */
final class Gesture extends MouseAdapter {
    interface Listener {
        default void pressed(Point p) {}
        default void tap(Point p) {}
        default void longPress(Point p) {}
        /** Movement since the previous event, reported once the finger has travelled past the touch slop. */
        default void dragged(Point p, int dx, int dy) {}
        /** The finger lifted after a drag; velocity in pixels per second. */
        default void flung(double vx, double vy) {}
        default void released() {}
        default void wheel(int notches, Point p) {}
    }

    /** True once the pointer (touch or mouse) was used more recently than the gamepad; switches the hints shown. */
    static volatile boolean pointerMode;

    private static final int LONG_PRESS_MS = 520;
    private final Listener l;
    private final int slop = Math.max(8, Theme.base() / 2);
    private Point start, last;
    private boolean dragging, longFired, down;
    private final Timer longTimer;
    private final long[] ts = new long[8];
    private final int[] xs = new int[8], ys = new int[8];
    private int n;

    private Gesture(Listener l) {
        this.l = l;
        longTimer = new Timer(LONG_PRESS_MS, e -> {
            if (down && !dragging) { longFired = true; l.longPress(start); }
        });
        longTimer.setRepeats(false);
    }

    static Gesture attach(JComponent c, Listener l) {
        Gesture g = new Gesture(l);
        c.addMouseListener(g);
        c.addMouseMotionListener(g);
        c.addMouseWheelListener(g);
        return g;
    }

    private void sample(MouseEvent e) {
        int i = n++ % ts.length;
        ts[i] = e.getWhen() > 0 ? e.getWhen() : System.currentTimeMillis();
        xs[i] = e.getX(); ys[i] = e.getY();
    }

    @Override public void mousePressed(MouseEvent e) {
        pointerMode = true;
        if (SwingUtilities.isRightMouseButton(e)) { l.longPress(e.getPoint()); return; }   // right click = long press for mouse users
        if (!SwingUtilities.isLeftMouseButton(e)) return;
        down = true; dragging = false; longFired = false; n = 0;
        start = last = e.getPoint();
        sample(e);
        l.pressed(start);
        longTimer.restart();
    }

    @Override public void mouseDragged(MouseEvent e) {
        if (!down) return;
        sample(e);
        if (!dragging && (Math.abs(e.getX() - start.x) > slop || Math.abs(e.getY() - start.y) > slop)) {
            dragging = true; longTimer.stop();
            last = start;                       // report the whole distance travelled so far, so the content doesn't jump
        }
        if (dragging) {
            l.dragged(e.getPoint(), e.getX() - last.x, e.getY() - last.y);
            last = e.getPoint();
        }
    }

    @Override public void mouseReleased(MouseEvent e) {
        if (!down) return;
        down = false; longTimer.stop();
        if (dragging) {
            int cnt = Math.min(n, ts.length), newest = (n - 1) % ts.length, oldest = newest;
            long now = ts[newest];
            for (int k = 1; k < cnt; k++) {                // oldest sample within the last 120 ms
                int idx = ((n - 1 - k) % ts.length + ts.length) % ts.length;
                if (now - ts[idx] > 120) break;
                oldest = idx;
            }
            double dt = Math.max(1, ts[newest] - ts[oldest]) / 1000.0;
            double vx = oldest == newest ? 0 : (xs[newest] - xs[oldest]) / dt, vy = oldest == newest ? 0 : (ys[newest] - ys[oldest]) / dt;
            l.flung(vx, vy);
        } else if (!longFired) l.tap(e.getPoint());
        l.released();
    }

    @Override public void mouseWheelMoved(MouseWheelEvent e) {
        pointerMode = true;
        l.wheel(e.getWheelRotation(), e.getPoint());
    }
}
