// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 J2ME Deck contributors
package j2medeck.ui;

import javax.swing.Timer;
import java.util.ArrayList;
import java.util.List;

/** One shared ~60 Hz timer that only runs while something is animating. EDT only. */
final class Anim {
    interface Tickable {
        /** Advance by dt seconds; return true while more ticks are needed. */
        boolean tick(double dt);
    }

    private static final List<Tickable> ACTIVE = new ArrayList<>();
    private static Timer timer;
    private static long last;

    private Anim() {}

    static void start(Tickable t) {
        if (!ACTIVE.contains(t)) ACTIVE.add(t);
        if (timer == null) { timer = new Timer(16, e -> step()); timer.setCoalesce(true); }
        if (!timer.isRunning()) { last = System.nanoTime(); timer.start(); }
    }

    private static void step() {
        long now = System.nanoTime();
        double dt = Math.min(0.05, (now - last) / 1e9);
        last = now;
        for (Tickable t : new ArrayList<>(ACTIVE)) if (!t.tick(dt)) ACTIVE.remove(t);
        if (ACTIVE.isEmpty()) timer.stop();
    }

    /** A critically damped spring: x eases to target without overshoot. */
    static final class Spring implements Tickable {
        double x, v, target;
        private final double k, c;
        private final Runnable changed;

        Spring(double x, double stiffness, Runnable changed) {
            this.x = this.target = x;
            this.k = stiffness;
            this.c = 2 * Math.sqrt(stiffness);
            this.changed = changed;
        }
        void to(double t) { target = t; Anim.start(this); }
        void snap(double t) { x = target = t; v = 0; }
        boolean moving() { return x != target || v != 0; }

        @Override public boolean tick(double dt) {
            double a = -k * (x - target) - c * v;
            v += a * dt;
            x += v * dt;
            boolean done = Math.abs(x - target) < 0.0005 && Math.abs(v) < 0.005;
            if (done) { x = target; v = 0; }
            changed.run();
            return !done;
        }
    }

    /** Touch scrolling physics: drag with rubber-band edges, fling momentum and animated scrollTo. */
    static final class Scroller implements Tickable {
        double pos, vel, min, max;
        private double target = Double.NaN;
        private boolean dragging;
        private final Runnable changed, settled;

        Scroller(Runnable changed, Runnable settled) { this.changed = changed; this.settled = settled; }

        void setRange(double min, double max) { this.min = min; this.max = Math.max(min, max); }
        boolean isDragging() { return dragging; }
        boolean moving() { return !Double.isNaN(target) || Math.abs(vel) > 0; }

        /** Content follows the finger by delta (in position units). */
        void drag(double delta) {
            dragging = true; target = Double.NaN; vel = 0;
            if (pos < min || pos > max) delta *= 0.35;         // resist past the edges
            pos += delta;
            changed.run();
        }
        void release(double velocity) {
            dragging = false;
            vel = velocity;
            Anim.start(this);
        }
        void scrollTo(double t) {
            dragging = false;
            target = Math.max(min, Math.min(max, t));
            vel = 0;
            Anim.start(this);
        }
        void snap(double p) { pos = p; vel = 0; target = Double.NaN; dragging = false; }

        @Override public boolean tick(double dt) {
            if (dragging) return false;
            boolean more;
            if (!Double.isNaN(target)) {
                double k = 190, c = 2 * Math.sqrt(k);
                vel += (-k * (pos - target) - c * vel) * dt;
                pos += vel * dt;
                more = Math.abs(pos - target) > 0.4 || Math.abs(vel) > 4;
                if (!more) { pos = target; vel = 0; target = Double.NaN; }
            } else if (pos < min || pos > max) {
                double edge = pos < min ? min : max, k = 240, c = 2 * Math.sqrt(k);
                vel += (-k * (pos - edge) - c * vel) * dt;
                pos += vel * dt;
                more = Math.abs(pos - edge) > 0.4 || Math.abs(vel) > 4;
                if (!more) { pos = edge; vel = 0; }
            } else {
                vel *= Math.exp(-3.4 * dt);
                pos += vel * dt;
                more = Math.abs(vel) > 12;
                if (!more) vel = 0;
            }
            changed.run();
            if (!more) settled.run();
            return more;
        }
    }
}
