// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 J2ME Deck contributors
package j2medeck;

/**
 * Runtime shims that the class rewriter redirects game calls to: scaled game clock (speed up / slow down / pause),
 * optional no-op gc/sleep, and a configurable heap size for games that check Runtime.freeMemory().
 * Calls made by the JDK itself are never redirected, only calls found in the game's own classes.
 */
public final class Compat {
    private static final Object LOCK = new Object();
    private static volatile double speed = 1.0;
    private static volatile boolean paused;
    private static volatile int stepFrames;           // >0: run until this many frames were published, then pause again
    private static long baseReal = System.currentTimeMillis(), baseVirtual = baseReal;   // virtual = baseVirtual + (real-baseReal)*speed
    private static long frozenVirtual;

    public static volatile boolean ignoreGc, ignoreSleep, yieldSleeps;
    public static volatile int heapMb;                  // 0 = report the real heap

    private Compat() {}

    // ------------------------------------------------------------------ clock
    public static long currentTimeMillis() {
        if (speed == 1.0 && !paused && baseReal == baseVirtual) return System.currentTimeMillis();
        synchronized (LOCK) { return virtualNow(); }
    }
    private static long virtualNow() {
        return paused ? frozenVirtual : baseVirtual + (long) ((System.currentTimeMillis() - baseReal) * speed);
    }

    public static void sleep(long ms) throws InterruptedException {
        if (ignoreSleep) return;
        while (paused) { if (stepFrames > 0) break; Thread.sleep(20); }
        if (ms > 0 && speed != 1.0) ms = Math.max(1, (long) (ms / speed));
        Thread.sleep(ms);
    }

    public static void yield() throws InterruptedException {
        if (yieldSleeps) Thread.sleep(1); else Thread.yield();
    }

    public static void gc() { if (!ignoreGc) System.gc(); }
    public static void gc(Runtime r) { if (!ignoreGc) r.gc(); }

    public static long totalMemory(Runtime r) {
        long real = r.totalMemory();
        return heapMb > 0 ? Math.min((long) heapMb << 20, Math.max(real, (long) heapMb << 20)) : real;
    }
    public static long freeMemory(Runtime r) {
        if (heapMb <= 0) return r.freeMemory();
        long used = r.totalMemory() - r.freeMemory();
        return Math.max(0, ((long) heapMb << 20) - used);
    }

    // ------------------------------------------------------------------ controls (hotkeys / tests)
    public static double speed() { return speed; }
    public static boolean isPaused() { return paused; }

    public static void setSpeed(double s) {
        s = Math.max(0.05, Math.min(16, s));
        synchronized (LOCK) {
            long now = virtualNow();
            baseVirtual = now; baseReal = System.currentTimeMillis();
            speed = s;
            if (paused) frozenVirtual = now;
        }
    }

    /** Next speed step in the list 0.25, 0.5, 1, 2, 4, 8 (dir = +1 faster, -1 slower). */
    public static double stepSpeed(int dir) {
        double[] steps = {0.25, 0.5, 1, 2, 4, 8};
        int i = 2;
        for (int k = 0; k < steps.length; k++) if (Math.abs(steps[k] - speed) < 0.01) i = k;
        i = Math.max(0, Math.min(steps.length - 1, i + dir));
        setSpeed(steps[i]);
        return steps[i];
    }

    public static void pause() {
        synchronized (LOCK) { if (paused) return; frozenVirtual = virtualNow(); paused = true; }
    }
    public static void resume() {
        synchronized (LOCK) {
            if (!paused) return;
            baseVirtual = frozenVirtual; baseReal = System.currentTimeMillis();
            paused = false; stepFrames = 0;
        }
    }
    public static boolean togglePause() { if (paused) resume(); else pause(); return paused; }

    /** Let the game run for exactly one published frame, then freeze again (only while paused). */
    public static void step() {
        synchronized (LOCK) {
            if (!paused) return;
            baseVirtual = frozenVirtual; baseReal = System.currentTimeMillis();
            paused = false; stepFrames = 1;
        }
    }
    /** Called whenever a frame is published; ends a single-step. */
    public static void frameDone() {
        if (stepFrames > 0 && --stepFrames == 0) pause();
    }

    /** Restore defaults (used by tests and when a new game starts). */
    public static void reset() {
        synchronized (LOCK) {
            speed = 1.0; paused = false; stepFrames = 0;
            baseReal = System.currentTimeMillis(); baseVirtual = baseReal;
        }
        ignoreGc = ignoreSleep = yieldSleeps = false; heapMb = 0;
    }
}
