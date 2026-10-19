// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 J2ME Deck contributors
package javax.microedition.lcdui;

import javax.swing.JComponent;
import java.awt.EventQueue;
import java.awt.GraphicsEnvironment;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * MIDP Canvas. The framebuffer persists between paints (double-buffered semantics); paint() runs on the
 * UI thread (or the caller of serviceRepaints) and is clipped to the dirty region, like on a handset.
 */
public abstract class Canvas extends Displayable {
    public static final int KEY_POUND = 35, KEY_STAR = 42, KEY_NUM0 = 48, KEY_NUM1 = 49, KEY_NUM2 = 50, KEY_NUM3 = 51,
            KEY_NUM4 = 52, KEY_NUM5 = 53, KEY_NUM6 = 54, KEY_NUM7 = 55, KEY_NUM8 = 56, KEY_NUM9 = 57;
    // Device key codes (negative) used by the loader; they match common SE/Nokia values.
    public static final int KEY_UP = -1, KEY_DOWN = -2, KEY_LEFT = -3, KEY_RIGHT = -4, KEY_FIRE = -5,
            KEY_SOFT_LEFT = -6, KEY_SOFT_RIGHT = -7, KEY_CLEAR = -8, KEY_SEND = -10, KEY_END = -11;
    public static final int UP = 1, LEFT = 2, RIGHT = 5, DOWN = 6, FIRE = 8, GAME_A = 9, GAME_B = 10, GAME_C = 11, GAME_D = 12;

    private final int w, h;
    private final BufferedImage screen;
    private final int[] screenPixels;
    private final Graphics screenG;
    private final Object paintLock = new Object();
    private final Object dirtyLock = new Object();
    private final AtomicBoolean scheduled = new AtomicBoolean();
    private boolean dirty = true;
    private int dx0 = 0, dy0 = 0, dx1, dy1;
    private boolean fullScreen;
    private volatile JComponent view;
    private volatile Runnable frameListener;
    protected boolean $suppressKeys;

    protected Canvas() {
        w = Displayable.getVirtualWidth();
        h = Displayable.getVirtualHeight();
        dx1 = w; dy1 = h;
        screen = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        screenPixels = ((DataBufferInt) screen.getRaster().getDataBuffer()).getData();
        screenG = new Graphics(screen);
        java.util.Arrays.fill(screenPixels, 0xFFFFFFFF);
    }

    @Override public int getWidth() { return w; }
    @Override public int getHeight() { return h; }
    public boolean hasPointerEvents() { return j2medeck.Settings.hasPointer != 0; }
    public boolean hasPointerMotionEvents() { return j2medeck.Settings.hasPointer != 0; }
    public boolean hasRepeatEvents() { return true; }
    public boolean isDoubleBuffered() { return true; }
    public void setFullScreenMode(boolean b) { fullScreen = b || j2medeck.Settings.ignoreFullScreen; $changed(); }
    public boolean isFullScreenMode() { return fullScreen || j2medeck.Settings.ignoreFullScreen; }

    // ---- key mapping (MIDP); direction/fire/soft key codes follow the active vendor KeyLayout ----
    public int getKeyCode(int action) {
        j2medeck.KeyLayout l = j2medeck.KeyLayout.current;
        switch (action) {
            case UP: return l.up;
            case DOWN: return l.down;
            case LEFT: return l.left;
            case RIGHT: return l.right;
            case FIRE: return l.fire;
            case GAME_A: return KEY_NUM1;
            case GAME_B: return KEY_NUM3;
            case GAME_C: return KEY_NUM7;
            case GAME_D: return KEY_NUM9;
            default: throw new IllegalArgumentException("gameAction");
        }
    }

    /** UP/DOWN/LEFT/RIGHT/FIRE/GAME_A..D for a key code, or 0 when the key has no game action. */
    protected int $action(int key) {
        int a = j2medeck.KeyLayout.current.actionFor(key, UP, DOWN, LEFT, RIGHT, FIRE);
        if (a != 0) return a;
        switch (key) {
            case KEY_NUM2: return UP;
            case KEY_NUM8: return DOWN;
            case KEY_NUM4: return LEFT;
            case KEY_NUM6: return RIGHT;
            case KEY_NUM5: return FIRE;
            case KEY_NUM1: return GAME_A;
            case KEY_NUM3: return GAME_B;
            case KEY_NUM7: return GAME_C;
            case KEY_NUM9: return GAME_D;
            default: return 0;
        }
    }
    public int getGameAction(int key) {
        int a = $action(key);
        if (a == 0) throw new IllegalArgumentException("keyCode " + key);
        return a;
    }
    public String getKeyName(int key) {
        switch (key) {
            case KEY_UP: return "UP"; case KEY_DOWN: return "DOWN"; case KEY_LEFT: return "LEFT";
            case KEY_RIGHT: return "RIGHT"; case KEY_FIRE: return "SELECT";
            case KEY_SOFT_LEFT: return "SOFT1"; case KEY_SOFT_RIGHT: return "SOFT2"; case KEY_CLEAR: return "CLEAR";
            case KEY_STAR: return "*"; case KEY_POUND: return "#";
            default: return key >= 32 && key < 127 ? String.valueOf((char) key) : "KEY" + key;
        }
    }

    // ---- painting ----
    protected abstract void paint(Graphics g);

    public final void repaint() { repaint(0, 0, w, h); }

    public final void repaint(int x, int y, int rw, int rh) {
        if (j2medeck.Settings.ignoreRegionRepaint) { x = 0; y = 0; rw = w; rh = h; }
        synchronized (dirtyLock) {
            int x1 = Math.max(0, x), y1 = Math.max(0, y), x2 = Math.min(w, x + rw), y2 = Math.min(h, y + rh);
            if (x2 <= x1 || y2 <= y1) return;
            if (!dirty) { dx0 = x1; dy0 = y1; dx1 = x2; dy1 = y2; dirty = true; }
            else { dx0 = Math.min(dx0, x1); dy0 = Math.min(dy0, y1); dx1 = Math.max(dx1, x2); dy1 = Math.max(dy1, y2); }
        }
        if (scheduled.compareAndSet(false, true)) EventQueue.invokeLater(() -> { scheduled.set(false); paintDirty(); });
    }

    public final void serviceRepaints() { if (!j2medeck.Settings.ignoreServiceRepaints) paintDirty(); }

    private void paintDirty() {
        if (!shown) return;
        int x0, y0, x1, y1;
        synchronized (dirtyLock) {
            if (!dirty) return;
            x0 = dx0; y0 = dy0; x1 = dx1; y1 = dy1; dirty = false;
        }
        synchronized (paintLock) {
            screenG.reset(x0, y0, x1 - x0, y1 - y0);
            try { paint(screenG); } catch (Throwable t) { t.printStackTrace(); }
        }
        framePublished();
    }

    /** GameCanvas.flushGraphics support: copy an offscreen image region to the screen buffer and show it. */
    protected final void $blit(Image img, int x, int y, int bw, int bh) {
        synchronized (paintLock) {
            java.awt.Graphics2D g = screenG.awt();
            java.awt.Shape clip = g.getClip();
            java.awt.geom.AffineTransform tr = g.getTransform();
            g.setTransform(new java.awt.geom.AffineTransform());
            g.setClip(0, 0, w, h);
            g.drawImage(img.awt(), x, y, x + bw, y + bh, x, y, x + bw, y + bh, null);
            g.setTransform(tr);
            g.setClip(clip);
        }
        framePublished();
    }

    private long lastFrameNanos;

    /** Caps the frame rate by delaying the thread that publishes the frame (never the UI thread). */
    private void limitFps() {
        int fps = j2medeck.Settings.fpsLimit;
        if (fps <= 0 || EventQueue.isDispatchThread()) return;
        long frame = 1_000_000_000L / fps, now = System.nanoTime();
        long wait = lastFrameNanos + frame - now;
        if (wait > 0) { try { Thread.sleep(wait / 1_000_000L, (int) (wait % 1_000_000L)); } catch (InterruptedException ignored) {} now = System.nanoTime(); }
        lastFrameNanos = now;
    }

    private void framePublished() {
        j2medeck.Diag.frames.incrementAndGet();
        limitFps();
        JComponent v = view;
        if (v != null) {
            v.repaint();
            if (j2medeck.Settings.syncFlush && !EventQueue.isDispatchThread()) {
                if (v instanceof j2medeck.ScreenView && ((j2medeck.ScreenView) v).glActive()) {
                    ((j2medeck.ScreenView) v).glAwaitPresented(100);              // GL: until the frame was swapped to the screen
                } else {
                    try { EventQueue.invokeAndWait(() -> {}); } catch (Exception ignored) {}   // returns once the queued repaint ran
                }
            }
        }
        Runnable l = frameListener;
        if (l != null) l.run();
        j2medeck.Compat.frameDone();
    }

    // ---- hooks used by the desktop front-end (not part of MIDP) ----
    public final void $copyFrame(int[] dst) {
        synchronized (paintLock) { System.arraycopy(screenPixels, 0, dst, 0, Math.min(dst.length, screenPixels.length)); }
    }
    public final void $setFrameListener(Runnable r) { frameListener = r; }
    public final synchronized JComponent $view() {
        if (view == null && !GraphicsEnvironment.isHeadless()) view = new j2medeck.ScreenView(this);
        return view;
    }

    @Override void $becameCurrent() {
        super.$becameCurrent();
        try { sizeChanged(w, h); } catch (Throwable t) { t.printStackTrace(); }
        try { showNotify(); } catch (Throwable t) { t.printStackTrace(); }
        if (!j2medeck.Settings.noRepaintOnSetCurrent) repaint();
    }
    @Override void $noLongerCurrent() {
        super.$noLongerCurrent();
        try { hideNotify(); } catch (Throwable t) { t.printStackTrace(); }
    }
    /** Test hook (headless self-test): show and synchronously paint. */
    public final void $becameCurrentForTest() { $becameCurrent(); serviceRepaints(); }

    /** Re-run showNotify/hideNotify without changing the current displayable (focus loss / regain). */
    final void $pause(boolean paused) {
        if (paused) { shown = false; try { hideNotify(); } catch (Throwable t) { t.printStackTrace(); } }
        else { shown = true; try { showNotify(); } catch (Throwable t) { t.printStackTrace(); } repaint(); }
    }

    // ---- input events ----
    protected void keyPressed(int keyCode) {}
    protected void keyRepeated(int keyCode) {}
    protected void keyReleased(int keyCode) {}
    protected void pointerPressed(int x, int y) {}
    protected void pointerDragged(int x, int y) {}
    protected void pointerReleased(int x, int y) {}

    /** GameCanvas overrides this to track key state for getKeyStates(). */
    protected void $trackKey(int code, boolean down) {}

    public final void $dispatchKey(int kind, int code) {
        try {
            if (kind == 1) { $trackKey(code, false); if (!$suppressKeys) keyReleased(code); }
            else if (kind == 0) { $trackKey(code, true); if (!$suppressKeys) keyPressed(code); }
            else { if (!$suppressKeys) keyRepeated(code); }
        } catch (Throwable t) { t.printStackTrace(); }
    }
    public final void $dispatchPointer(int kind, int x, int y) {
        try {
            if (kind == 0) pointerPressed(x, y); else if (kind == 1) pointerReleased(x, y); else pointerDragged(x, y);
        } catch (Throwable t) { t.printStackTrace(); }
    }
}
