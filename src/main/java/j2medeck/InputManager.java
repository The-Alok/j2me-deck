// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 J2ME Deck contributors
package j2medeck;

import javax.microedition.lcdui.Canvas;
import javax.microedition.lcdui.Display;
import java.awt.EventQueue;
import java.awt.event.KeyEvent;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Turns keyboard and gamepad input into MIDP key events.
 * X11 key auto-repeat arrives as release+press pairs; releases are therefore delayed by a few ms and
 * cancelled if the same key is pressed again, so held keys stay held (and become keyRepeated events).
 */
public final class InputManager {
    private static final InputManager INSTANCE = new InputManager();
    public static InputManager get() { return INSTANCE; }

    private static final long RELEASE_DELAY_MS = 25;

    private final ScheduledExecutorService timer = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "input-debounce"); t.setDaemon(true); return t;
    });
    private final Map<Integer, ScheduledFuture<?>> pendingRelease = new HashMap<>();   // by AWT key code
    private final Map<Integer, Integer> heldCount = new HashMap<>();                   // by MIDP key code
    private final Map<Integer, Integer> awtToMidp = new HashMap<>();                   // AWT key code -> MIDP code while held

    private volatile java.util.Properties custom = new java.util.Properties();
    private InputManager() { reloadMappings(); }

    public synchronized void reloadMappings() { custom = KeyMapper.loadMerged("keyboard.properties"); }

    // ---------- keyboard ----------
    public static int mapKey(KeyEvent e) {
        int c = e.getKeyCode();
        java.util.Properties cfg = INSTANCE.custom;
        for (String name : new String[]{"UP","DOWN","LEFT","RIGHT","FIRE","SOFT_LEFT","SOFT_RIGHT","CLEAR","SEND","END","STAR","POUND","0","1","2","3","4","5","6","7","8","9"}) {
            String mapped = cfg.getProperty("key." + name);
            if (mapped != null && mapped.equalsIgnoreCase(KeyEvent.getKeyText(c))) return KeyMapper.code(name);
        }
        switch (c) {
            case KeyEvent.VK_UP: case KeyEvent.VK_KP_UP: return Canvas.KEY_UP;
            case KeyEvent.VK_DOWN: case KeyEvent.VK_KP_DOWN: return Canvas.KEY_DOWN;
            case KeyEvent.VK_LEFT: case KeyEvent.VK_KP_LEFT: return Canvas.KEY_LEFT;
            case KeyEvent.VK_RIGHT: case KeyEvent.VK_KP_RIGHT: return Canvas.KEY_RIGHT;
            case KeyEvent.VK_ENTER: case KeyEvent.VK_SPACE: return Canvas.KEY_FIRE;
            case KeyEvent.VK_F1: case KeyEvent.VK_Q: return Canvas.KEY_SOFT_LEFT;
            case KeyEvent.VK_F2: case KeyEvent.VK_E: return Canvas.KEY_SOFT_RIGHT;
            case KeyEvent.VK_BACK_SPACE: case KeyEvent.VK_ESCAPE: return Canvas.KEY_CLEAR;
            case KeyEvent.VK_W: return Canvas.KEY_UP;
            case KeyEvent.VK_S: return Canvas.KEY_DOWN;
            case KeyEvent.VK_A: return Canvas.KEY_LEFT;
            case KeyEvent.VK_D: return Canvas.KEY_RIGHT;
            case KeyEvent.VK_MULTIPLY: return Canvas.KEY_STAR;
            case KeyEvent.VK_NUMBER_SIGN: return Canvas.KEY_POUND;
            default:
        }
        if (c >= KeyEvent.VK_0 && c <= KeyEvent.VK_9) return Canvas.KEY_NUM0 + (c - KeyEvent.VK_0);
        if (c >= KeyEvent.VK_NUMPAD0 && c <= KeyEvent.VK_NUMPAD9) return Canvas.KEY_NUM0 + (c - KeyEvent.VK_NUMPAD0);
        char ch = e.getKeyChar();
        if (ch == '*') return Canvas.KEY_STAR;
        if (ch == '#') return Canvas.KEY_POUND;
        return 0;
    }

    public synchronized void awtPressed(KeyEvent e) {
        int vk = e.getKeyCode();
        ScheduledFuture<?> f = pendingRelease.remove(vk);
        if (f != null) f.cancel(false);
        Integer held = awtToMidp.get(vk);
        if (held != null) { dispatch(2, held); return; }          // auto-repeat of a key still down
        int code = mapKey(e);
        if (code == 0) return;
        awtToMidp.put(vk, code);
        press(code);
    }

    public synchronized void awtReleased(KeyEvent e) {
        final int vk = e.getKeyCode();
        if (!awtToMidp.containsKey(vk)) return;
        ScheduledFuture<?> old = pendingRelease.remove(vk);
        if (old != null) old.cancel(false);
        pendingRelease.put(vk, timer.schedule(() -> finishRelease(vk), RELEASE_DELAY_MS, TimeUnit.MILLISECONDS));
    }

    private synchronized void finishRelease(int vk) {
        pendingRelease.remove(vk);
        Integer code = awtToMidp.remove(vk);
        if (code != null) release(code);
    }

    // ---------- generic (also used by the gamepad and the headless test script) ----------
    public synchronized void press(int code) {
        int n = heldCount.merge(code, 1, Integer::sum);
        if (n == 1) dispatch(0, code);
    }

    public synchronized void release(int code) {
        Integer n = heldCount.get(code);
        if (n == null) return;
        if (n <= 1) { heldCount.remove(code); dispatch(1, code); } else heldCount.put(code, n - 1);
    }

    /** Release everything (focus lost, window switch) so the game never sees stuck keys. */
    public synchronized void releaseAll() {
        for (ScheduledFuture<?> f : pendingRelease.values()) f.cancel(false);
        pendingRelease.clear();
        awtToMidp.clear();
        Integer[] codes = heldCount.keySet().toArray(new Integer[0]);
        heldCount.clear();
        for (int c : codes) dispatch(1, c);
    }

    private static volatile java.util.function.IntConsumer sink;
    /** While set (library window), key presses go to the consumer instead of the running game. */
    public static void setSink(java.util.function.IntConsumer c) { sink = c; }

    private static final boolean DEBUG = System.getenv("J2ME_DEBUG_INPUT") != null;

    private void dispatch(int kind, int code) {
        if (DEBUG) System.err.println("[input] " + (kind == 0 ? "press " : kind == 1 ? "release " : "repeat ") + code);
        java.util.function.IntConsumer sk = sink;
        if (sk != null) { if (kind != 1) EventQueue.invokeLater(() -> sk.accept(code)); return; }
        Runnable r = () -> Display.$routeKey(kind, code);
        if (EventQueue.isDispatchThread()) r.run(); else EventQueue.invokeLater(r);
    }
}
