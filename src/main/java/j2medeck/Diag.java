// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 J2ME Deck contributors
package j2medeck;

import javax.microedition.lcdui.Display;
import java.awt.EventQueue;
import java.awt.GraphicsEnvironment;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Freeze diagnostics for runs where nobody can attach a debugger (Steam Game Mode). Everything goes to the game's log:
 * a line every 5 s for the first minute saying whether the game is still publishing frames, and a thread dump when the
 * Swing event thread stops answering. Costs one sleeping daemon thread.
 */
public final class Diag {
    /** Incremented by Canvas each time the game publishes a frame. */
    public static final AtomicLong frames = new AtomicLong();
    private static volatile long lastBeat = System.nanoTime();
    private static volatile boolean focused, everFocused;
    private static boolean started;

    private Diag() {}

    public static void focus(boolean gained) { focused = gained; if (gained) everFocused = true; }

    public static synchronized void start() {
        if (started || GraphicsEnvironment.isHeadless()) return;
        started = true;
        Thread t = new Thread(Diag::loop, "j2me-diag");
        t.setDaemon(true);
        t.start();
    }

    private static void loop() {
        long lastFrames = 0, lastDump = 0;
        int dumps = 0, tick = 0;
        try {
            while (true) {
                Thread.sleep(1000);
                tick++;
                Display d = Display.getInstance();
                if (d != null && d.getCurrent() != null) {          // only poke the UI thread once the game has a window
                    EventQueue.invokeLater(() -> lastBeat = System.nanoTime());
                    long behindMs = (System.nanoTime() - lastBeat) / 1_000_000L;
                    long now = System.nanoTime();
                    if (behindMs > 4000 && dumps < 3 && now - lastDump > 10_000_000_000L) {
                        dumps++;
                        lastDump = now;
                        System.err.println("[j2me] UI thread has not responded for " + behindMs / 1000 + " s. Thread dump:");
                        for (Map.Entry<Thread, StackTraceElement[]> e : Thread.getAllStackTraces().entrySet()) {
                            System.err.println("  \"" + e.getKey().getName() + "\" " + e.getKey().getState());
                            int n = 0;
                            for (StackTraceElement f : e.getValue()) { if (n++ >= 12) break; System.err.println("      at " + f); }
                        }
                    }
                }
                if (tick % 5 == 0) {
                    long f = frames.get(), delta = f - lastFrames;
                    lastFrames = f;
                    // every 5 s for the first minute, afterwards only when the game stopped publishing frames (every 30 s)
                    if (tick <= 60 || (delta == 0 && tick % 30 == 0)) {
                        boolean paused = d != null && d.$isAutoPaused();
                        System.err.println("[j2me] alive t=" + tick + "s frames(+" + delta + ") focus=" + focused + " everFocused=" + everFocused + " autoPaused=" + paused);
                    }
                }
            }
        } catch (InterruptedException ignored) {
        }
    }
}
