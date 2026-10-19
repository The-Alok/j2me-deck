// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 J2ME Deck contributors
package javax.microedition.lcdui;

import j2medeck.Settings;
import j2medeck.ui.CommandBar;
import j2medeck.ui.ScreenPanel;
import j2medeck.ui.Theme;
import javax.microedition.midlet.MIDlet;
import javax.swing.*;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;

public final class Display {
    public static final int LIST_ELEMENT = 1, CHOICE_GROUP_ELEMENT = 2, ALERT = 3;
    public static final int COLOR_BACKGROUND = 0, COLOR_FOREGROUND = 1, COLOR_HIGHLIGHTED_BACKGROUND = 2,
            COLOR_HIGHLIGHTED_FOREGROUND = 3, COLOR_BORDER = 4, COLOR_HIGHLIGHTED_BORDER = 5;

    private static Display instance;
    private static final CountDownLatch closed = new CountDownLatch(1);
    private static volatile Runnable closeHandler;
    private static final AtomicBoolean refreshQueued = new AtomicBoolean();

    private volatile Displayable current;
    private Displayable alertNext;               // where to go when the current Alert is dismissed
    private JFrame frame;
    private final JPanel root = new JPanel(new BorderLayout());
    private ScreenPanel panel;                   // non-null while a Screen/Alert is showing
    private CommandBar canvasBar;                // non-null while a Canvas shows its soft-key bar
    private boolean fullscreen, everFocused, autoPaused;
    private Rectangle windowedBounds;
    private javax.swing.Timer focusLossTimer;

    private Display() {
        root.setBackground(Color.BLACK);
        int sw = Displayable.getVirtualWidth(), sh = Displayable.getVirtualHeight();
        Dimension scr = GraphicsEnvironment.isHeadless() ? new Dimension(1280, 800)
                : GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds().getSize();
        int k = Settings.windowScale > 0 ? Settings.windowScale
                : Math.max(1, Math.min((int) (scr.width * 0.9) / sw, (int) (scr.height * 0.85) / sh));
        root.setPreferredSize(new Dimension(sw * k, sh * k));
    }

    public static synchronized Display getDisplay(MIDlet midlet) {
        if (instance == null) instance = new Display();
        return instance;
    }
    public static synchronized Display getInstance() { return instance; }
    /** Diagnostics: true while the game is paused because the window lost focus. */
    public boolean $isAutoPaused() { return autoPaused; }
    public static synchronized void initDisplay() { instance = null; }
    public static void setCloseHandler(Runnable r) { closeHandler = r; }
    public static void awaitClosed() throws InterruptedException { closed.await(); }

    public Displayable getCurrent() { return current; }

    public void setCurrent(Displayable d) {
        if (d == null) return;
        final Displayable old = current;
        if (d instanceof Alert && alertNext == null && old != null && !(old instanceof Alert)) alertNext = old;
        current = d;
        Runnable r = () -> switchTo(old, d);
        if (EventQueue.isDispatchThread()) r.run(); else EventQueue.invokeLater(r);
    }
    public void setCurrent(Alert alert, Displayable next) {
        if (alert == null || next == null) throw new NullPointerException();
        if (next instanceof Alert) throw new IllegalArgumentException("next must not be an Alert");
        alertNext = next;
        setCurrent(alert);
    }
    public void setCurrentItem(Item item) {
        if (item != null && item.owner != null) setCurrent(item.owner);
    }

    public void callSerially(Runnable r) { EventQueue.invokeLater(r); }
    public boolean isColor() { return true; }
    public int numColors() { return 1 << 24; }
    public int numAlphaLevels() { return 256; }
    public boolean vibrate(int ms) { return false; }
    public boolean flashBacklight(int ms) { return false; }
    public int getColor(int c) {
        switch (c) {
            case COLOR_BACKGROUND: return 0x0F1626;
            case COLOR_FOREGROUND: return 0xE8EEFC;
            case COLOR_HIGHLIGHTED_BACKGROUND: return 0x2B4C8C;
            case COLOR_HIGHLIGHTED_FOREGROUND: return 0xFFFFFF;
            case COLOR_BORDER: return 0x2C3E66;
            case COLOR_HIGHLIGHTED_BORDER: return 0xFFC000;
            default: throw new IllegalArgumentException("color");
        }
    }
    public int getBorderStyle(boolean highlighted) { return Graphics.SOLID; }
    public int getBestImageWidth(int type) { return 32; }
    public int getBestImageHeight(int type) { return 32; }

    // ------------------------------------------------------------------ switching and layout
    private void switchTo(Displayable old, Displayable d) {
        if (old != null && old != d && old.shown) {
            try { old.$noLongerCurrent(); } catch (Throwable t) { t.printStackTrace(); }
        }
        autoPaused = false;
        compose(d);
        d.$becameCurrent();
        if (frame == null && !GraphicsEnvironment.isHeadless()) createFrame();
        showFrame();
        focusCurrent();
    }

    private boolean needsCanvasBar(Canvas c) {
        String mode = Settings.softKeyBar;
        if ("never".equals(mode)) return false;
        if ("always".equals(mode)) return true;
        return !c.isFullScreenMode() && c.getCommands().length > 0;
    }

    /** (Re)build the window content for d. */
    private void compose(Displayable d) {
        if (panel != null) { panel.dispose(); panel = null; }
        canvasBar = null;
        root.removeAll();
        if (d instanceof Canvas) {
            Canvas c = (Canvas) d;
            JComponent v = c.$view();
            if (v == null) v = new JPanel();
            root.add(v, BorderLayout.CENTER);
            if (needsCanvasBar(c)) {
                canvasBar = new CommandBar();
                canvasBar.bind(c, null);
                root.add(canvasBar, BorderLayout.SOUTH);
            }
        } else {
            panel = new ScreenPanel(d);
            if (d instanceof Alert) panel.onAlertDismissed(() -> {
                Displayable next = alertNext; alertNext = null;
                if (next != null) setCurrent(next);
            });
            root.add(panel, BorderLayout.CENTER);
        }
        root.revalidate(); root.repaint();
    }

    /** Model of the current displayable changed (items added, commands changed, fullscreen toggled...). */
    static void $refresh(Displayable d) {
        Display i = instance;
        if (i == null) return;
        if (refreshQueued.compareAndSet(false, true)) EventQueue.invokeLater(() -> { refreshQueued.set(false); i.doRefresh(); });
    }

    private void doRefresh() {
        Displayable d = current;
        if (d == null) return;
        if (d instanceof Canvas) {
            boolean want = needsCanvasBar((Canvas) d);
            if (want != (canvasBar != null)) compose(d);
            else if (canvasBar != null) canvasBar.update();
        } else if (panel != null && panel.displayable() == d) {
            panel.rebuild();
        }
    }

    /** Routes a MIDP key event (from keyboard or gamepad) to whatever is on screen. Call on the UI thread. */
    public static void $routeKey(int kind, int code) {
        Display d = instance;
        if (d == null) return;
        Displayable cur = d.current;
        // Safety net: if the game was auto-paused by a focus change and the focus event to resume never arrives, any button press resumes it.
        if (d.autoPaused && kind == 0 && cur instanceof Canvas) {
            d.autoPaused = false;
            System.err.println("[j2me] resuming from auto-pause (button pressed while the window had no focus)");
            ((Canvas) cur).$pause(false);
        }
        if (j2medeck.Settings.swapSoftKeys) {
            if (code == Canvas.KEY_SOFT_LEFT) code = Canvas.KEY_SOFT_RIGHT;
            else if (code == Canvas.KEY_SOFT_RIGHT) code = Canvas.KEY_SOFT_LEFT;
        }
        if (cur instanceof Canvas) {
            Canvas c = (Canvas) cur;
            int dev = j2medeck.KeyLayout.current.toDevice(code);
            if (kind == 2) {
                if (!j2medeck.Settings.keyRepeat) return;
                if (j2medeck.Settings.keyPressOnRepeat) kind = 0;
            }
            if (d.canvasBar != null && (code == Canvas.KEY_SOFT_LEFT || code == Canvas.KEY_SOFT_RIGHT)) {
                if (kind == 0 && d.canvasBar.softKey(code == Canvas.KEY_SOFT_LEFT)) {
                    if (j2medeck.Settings.softKeyFix) { c.$dispatchKey(0, dev); c.$dispatchKey(1, dev); }
                    return;
                }
                if (d.canvasBar.hasCommands()) return;          // swallow the matching release too
            }
            c.$dispatchKey(kind, dev);
        } else if (d.panel != null) {
            d.panel.onKey(kind, code);
        }
    }

    /** Test/dev hook: render whatever is showing (screen panel or command bar layout) at the given size. */
    public static BufferedImage $snapshotWindow(int w, int h) {
        Display d = instance;
        if (d == null) return null;
        d.root.setSize(w, h);
        layoutAll(d.root);
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        java.awt.Graphics2D g = img.createGraphics();
        d.root.paint(g);
        g.dispose();
        return img;
    }
    private static void layoutAll(java.awt.Component c) {
        if (c instanceof java.awt.Container) {
            java.awt.Container k = (java.awt.Container) c;
            k.doLayout();
            for (java.awt.Component ch : k.getComponents()) layoutAll(ch);
        }
    }
    public static boolean $isScreenShowing() { Display d = instance; return d != null && d.panel != null; }

    // ------------------------------------------------------------------ window
    private void createFrame() {
        frame = new JFrame(System.getProperty("j2me.title", "J2ME Deck"));
        frame.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        frame.addWindowListener(new WindowAdapter() {
            @Override public void windowClosing(WindowEvent e) { requestClose(); }
        });
        frame.addWindowFocusListener(new java.awt.event.WindowFocusListener() {
            @Override public void windowGainedFocus(WindowEvent e) { onFocus(true); }
            @Override public void windowLostFocus(WindowEvent e) { onFocus(false); }
        });
        frame.setContentPane(root);
        try {
            java.io.InputStream in = j2medeck.ResourceBridge.getResourceAsStream("icon.png");
            if (in != null) frame.setIconImage(javax.imageio.ImageIO.read(in));
        } catch (Exception ignored) {}
    }

    private void showFrame() {
        if (frame == null || frame.isVisible()) return;
        frame.pack();
        if (Settings.fullscreen && !fullscreen) setFullscreen(true);
        else { frame.setLocationRelativeTo(null); frame.setVisible(true); }
    }

    private void focusCurrent() {
        EventQueue.invokeLater(() -> {
            Displayable d = current;
            if (d instanceof Canvas) {
                JComponent v = ((Canvas) d).$view();
                if (v != null) v.requestFocusInWindow();
            }
        });
    }

    /** Pausing on focus loss (e.g. Steam overlay / Steam button) only after we had focus once. */
    private void onFocus(boolean gained) {
        System.err.println("[j2me] window focus " + (gained ? "gained" : "lost") + (Settings.autoPause ? "" : " (auto-pause off)"));
        j2medeck.Diag.focus(gained);
        if (gained) {
            everFocused = true;
            if (focusLossTimer != null) focusLossTimer.stop();
            j2medeck.InputManager.get().releaseAll();
            if (autoPaused && current instanceof Canvas) { autoPaused = false; System.err.println("[j2me] resumed after auto-pause"); ((Canvas) current).$pause(false); }
            focusCurrent();
        } else if (everFocused && Settings.autoPause) {
            j2medeck.InputManager.get().releaseAll();
            if (focusLossTimer != null) focusLossTimer.stop();
            focusLossTimer = new javax.swing.Timer(400, ev -> {
                if (frame != null && !frame.isActive() && current instanceof Canvas && !autoPaused) {
                    autoPaused = true;
                    System.err.println("[j2me] auto-pause: window has been without focus for 400 ms (any button resumes)");
                    ((Canvas) current).$pause(true);
                }
            });
            focusLossTimer.setRepeats(false);
            focusLossTimer.start();
        }
    }

    private static Cursor blankCursor() {
        return Toolkit.getDefaultToolkit().createCustomCursor(
                new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB), new Point(0, 0), "blank");
    }

    public static synchronized void toggleFullscreen() {
        Display d = instance;
        if (d == null || d.frame == null) return;
        d.setFullscreen(!d.fullscreen);
    }

    private void setFullscreen(boolean on) {
        GraphicsDevice device = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice();
        if (on) {
            windowedBounds = frame.isVisible() ? frame.getBounds() : null;
            frame.dispose();
            frame.setUndecorated(true);
            if (device.isFullScreenSupported()) device.setFullScreenWindow(frame);
            else { frame.setBounds(device.getDefaultConfiguration().getBounds()); frame.setVisible(true); }
            if (current instanceof Canvas) root.setCursor(blankCursor());
            fullscreen = true;
        } else {
            if (device.getFullScreenWindow() == frame) device.setFullScreenWindow(null);
            frame.dispose();
            frame.setUndecorated(false);
            if (windowedBounds != null) frame.setBounds(windowedBounds); else frame.pack();
            frame.setVisible(true);
            root.setCursor(Cursor.getDefaultCursor());
            fullscreen = false;
        }
        focusCurrent();
    }

    private void requestClose() {
        Runnable h = closeHandler;
        if (h != null) h.run(); else shutdown();
    }

    public static synchronized void shutdown() {
        Display d = instance;
        if (d != null && d.frame != null) {
            final JFrame f = d.frame;
            EventQueue.invokeLater(() -> {
                GraphicsDevice dev = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice();
                if (dev.getFullScreenWindow() == f) dev.setFullScreenWindow(null);
                f.dispose();
            });
        }
        closed.countDown();
    }
}
