// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 J2ME Deck contributors
package j2medeck;

import javax.microedition.lcdui.Canvas;
import javax.swing.JComponent;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;

/** Swing component that blits a Canvas framebuffer, scaled to fit, and forwards input. */
public final class ScreenView extends JComponent implements GlHost {
    private final Canvas canvas;
    private final int w, h;
    private final BufferedImage frame;
    private final int[] frameData;
    private BufferedImage pre;      // nearest-neighbour pre-scaled copy for "sharp bilinear"
    private int preK;
    private String toast;
    private long toastUntil;
    private java.awt.Canvas glCanvas;           // non-null while the picture is drawn with OpenGL ES instead of Java2D
    private volatile GlControl gl;
    private javax.swing.Timer toastTimer;
    private final java.util.Map<String, BufferedImage> labelCache = new java.util.HashMap<>();

    public ScreenView(Canvas canvas) {
        this.canvas = canvas;
        this.w = canvas.getWidth();
        this.h = canvas.getHeight();
        frame = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        frameData = ((DataBufferInt) frame.getRaster().getDataBuffer()).getData();
        setFocusable(true);
        setOpaque(true);
        setBackground(Color.BLACK);
        setDoubleBuffered(true);
        setFocusTraversalKeysEnabled(false);
        setPreferredSize(new Dimension(w * 2, h * 2));
        installInput(this);
        java.awt.Canvas g = GlBackend.wanted() ? GlBackend.create(this) : null;
        if (g != null) {
            glCanvas = g;
            gl = (GlControl) g;
            setLayout(new BorderLayout());
            add(g, BorderLayout.CENTER);
            installInput(g);
        }
    }

    /** Keyboard and mouse handling; attached to this view and, when OpenGL ES is used, to its heavyweight canvas (which receives the events itself). */
    private void installInput(Component target) {
        final InputManager in = InputManager.get();
        target.addKeyListener(new KeyAdapter() {
            @Override public void keyPressed(KeyEvent e) {
                int c = e.getKeyCode();
                if (c == KeyEvent.VK_F8) { in.releaseAll(); if (e.isShiftDown()) KeyMapper.showGlobal(ScreenView.this); else KeyMapper.show(ScreenView.this); requestFocusInWindow(); return; }
                if (c == KeyEvent.VK_F7) { in.releaseAll(); j2medeck.ui.Theme.install(); j2medeck.ui.GameSettingsDialog.showGlobal(ScreenView.this, null); requestFocusInWindow(); showToast("Global settings apply when a game starts"); repaint(); return; }
                if (c == KeyEvent.VK_F12) { screenshot(); return; }
                if (c == KeyEvent.VK_F3 || c == KeyEvent.VK_F4) { double sp = Compat.stepSpeed(c == KeyEvent.VK_F4 ? 1 : -1); showToast("Speed x" + (sp == (int) sp ? String.valueOf((int) sp) : String.valueOf(sp))); repaint(); return; }
                if (c == KeyEvent.VK_F5) { showToast(Compat.togglePause() ? "Paused" : "Resumed"); repaint(); return; }
                if (c == KeyEvent.VK_F6) { Compat.step(); repaint(); return; }
                if (c == KeyEvent.VK_F9) { Settings.cycleLcdGrid(); showToast("LCD grid: " + Settings.LCD_NAMES[Settings.lcdLevel]); repaint(); return; }
                if (c == KeyEvent.VK_F11 || (c == KeyEvent.VK_ENTER && e.isAltDown())) { javax.microedition.lcdui.Display.toggleFullscreen(); return; }
                if (c == KeyEvent.VK_F10) { Settings.cycleScaleMode(); showToast("Scaling: " + Settings.SCALE_NAMES[Settings.scaleMode]); repaint(); return; }
                in.awtPressed(e);
            }
            @Override public void keyReleased(KeyEvent e) {
                int c = e.getKeyCode();
                if (c == KeyEvent.VK_F11 || c == KeyEvent.VK_F10 || c == KeyEvent.VK_F8 || c == KeyEvent.VK_F7 || c == KeyEvent.VK_F12 || c == KeyEvent.VK_F9 || (c >= KeyEvent.VK_F3 && c <= KeyEvent.VK_F6)) return;
                in.awtReleased(e);
            }
        });
        MouseAdapter mouse = new MouseAdapter() {
            @Override public void mousePressed(MouseEvent e) { requestFocusInWindow(); pointer(0, e); }
            @Override public void mouseReleased(MouseEvent e) { pointer(1, e); }
            @Override public void mouseDragged(MouseEvent e) { pointer(2, e); }
        };
        target.addMouseListener(mouse);
        target.addMouseMotionListener(mouse);
    }

    private void showToast(String s) {
        toast = s; toastUntil = System.currentTimeMillis() + 1500;
        if (gl != null) {                                   // GL has no repaint loop for the toast: redraw now and once more when it expires
            gl.requestFrame();
            if (toastTimer == null) {
                toastTimer = new javax.swing.Timer(1550, ev -> { GlControl g = gl; if (g != null) g.requestFrame(); });
                toastTimer.setRepeats(false);
            }
            toastTimer.restart();
        }
    }

    // ------------------------------------------------------------------ OpenGL ES hooks
    /** True while the picture is drawn by the GL canvas. */
    public boolean glActive() { return gl != null; }

    /** Waits (briefly) until the GL canvas has presented what was requested; used by the "wait for each frame" tweak. */
    public boolean glAwaitPresented(long ms) { GlControl g = gl; return g != null && g.awaitPresented(ms); }

    @Override public void repaint() { GlControl g = gl; if (g != null) g.requestFrame(); else super.repaint(); }
    @Override public void repaint(long tm) { GlControl g = gl; if (g != null) g.requestFrame(); else super.repaint(tm); }

    /** The heavyweight GL canvas is what must own the keyboard focus while it is in use. */
    @Override public boolean requestFocusInWindow() {
        java.awt.Canvas c = glCanvas;
        return c != null && gl != null ? c.requestFocusInWindow() : super.requestFocusInWindow();
    }
    @Override public void requestFocus() {
        java.awt.Canvas c = glCanvas;
        if (c != null && gl != null) c.requestFocus(); else super.requestFocus();
    }

    @Override public int frameWidth() { return w; }
    @Override public int frameHeight() { return h; }
    @Override public void copyFrame(int[] dst) { canvas.$copyFrame(dst); }

    /** A small rounded label (toast / pause tag) as an ARGB picture, cached by content. */
    private BufferedImage label(String text, int fontSize, int padX, int boxH, int baseline, Color fg) {
        String key = fontSize + "|" + padX + "|" + boxH + "|" + baseline + "|" + fg.getRGB() + "|" + text;
        synchronized (labelCache) {
            BufferedImage img = labelCache.get(key);
            if (img != null) return img;
            Font f = new Font(Font.SANS_SERIF, Font.BOLD, fontSize);
            BufferedImage probe = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
            Graphics2D pg = probe.createGraphics();
            int tw = pg.getFontMetrics(f).stringWidth(text);
            pg.dispose();
            img = new BufferedImage(tw + 2 * padX, boxH, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = img.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(new Color(0, 0, 0, fontSize == 16 ? 170 : 160));
            g.fillRoundRect(0, 0, img.getWidth(), boxH, 8, 8);
            g.setFont(f);
            g.setColor(fg);
            g.drawString(text, padX, baseline);
            g.dispose();
            if (labelCache.size() > 40) labelCache.clear();
            labelCache.put(key, img);
            return img;
        }
    }

    @Override public void overlays(int sw, int sh, java.util.List<GlOverlay> out) {
        boolean paused = Compat.isPaused();
        if (paused || Compat.speed() != 1.0) {
            String tag = paused ? "\u23F8 PAUSED" : "x" + (Compat.speed() == (int) Compat.speed() ? String.valueOf((int) Compat.speed()) : String.valueOf(Compat.speed()));
            BufferedImage img = label(tag, 14, 7, 24, 17, paused ? new Color(0xFFC000) : Color.WHITE);
            out.add(new GlOverlay("tag|" + tag + "|" + paused, img, sw - img.getWidth() - 8, 8));
        }
        String t = toast;
        if (t != null && System.currentTimeMillis() < toastUntil) {
            BufferedImage img = label(t, 16, 8, 28, 20, Color.WHITE);
            out.add(new GlOverlay("toast|" + t, img, 10, 10));
        }
    }

    /** The GL path stopped working: go back to painting with Java2D in this same window. */
    @Override public void glFailed(String reason) {
        GlBackend.disable(reason);
        EventQueue.invokeLater(() -> {
            GlControl g = gl;
            java.awt.Canvas c = glCanvas;
            gl = null;
            glCanvas = null;
            if (g != null) g.shutdown();
            if (c != null) remove(c);
            setLayout(null);
            showToast("OpenGL ES failed - using the software renderer");
            revalidate();
            requestFocusInWindow();
            super.repaint();
        });
    }

    /** Destination rectangle of the game picture inside this component for the current scaling mode. */
    private Rectangle destRect() {
        int cw = getWidth(), ch = getHeight();
        if (Settings.scaleMode == Settings.SCALE_STRETCH) return new Rectangle(0, 0, cw, ch);
        double s = Math.min(cw / (double) w, ch / (double) h);
        if (Settings.scaleMode == Settings.SCALE_INTEGER) s = Math.max(1, Math.floor(s));
        s = Math.max(0.01, s);
        int rw = (int) Math.round(w * s), rh = (int) Math.round(h * s);
        return new Rectangle((cw - rw) / 2, (ch - rh) / 2, rw, rh);
    }

    private void pointer(int kind, MouseEvent e) {
        Rectangle r = destRect();
        if (r.width <= 0 || r.height <= 0) return;
        int x = (int) ((e.getX() - r.x) * (double) w / r.width), y = (int) ((e.getY() - r.y) * (double) h / r.height);
        if (x < 0 || y < 0 || x >= w || y >= h) return;
        final int fx = x, fy = y;
        EventQueue.invokeLater(() -> canvas.$dispatchPointer(kind, fx, fy));
    }

    private BufferedImage gridImg;
    private int gridW, gridH, gridLevel;

    /** Transparent overlay with a dark line on every source-pixel boundary; cached per output size. */
    private BufferedImage grid(int rw, int rh, int level) {
        if (gridImg != null && gridW == rw && gridH == rh && gridLevel == level) return gridImg;
        BufferedImage img = new BufferedImage(rw, rh, BufferedImage.TYPE_INT_ARGB);
        int[] px = ((DataBufferInt) img.getRaster().getDataBuffer()).getData();
        double sx = rw / (double) w, sy = rh / (double) h;
        // strength per level; a little stronger still when the picture is scaled up more
        int[] base = {0, 40, 85, 135};
        int alpha = (int) Math.min(200, base[level] + 6 * Math.min(sx, sy));
        int line = (alpha << 24);
        int thick = (level == 3 && Math.min(sx, sy) >= 3) || Math.min(sx, sy) >= 6 ? 2 : 1;
        for (int i = 0; i < w; i++) {
            int x = (int) Math.round(i * sx);
            for (int t = 0; t < thick && x + t < rw; t++) for (int y = 0; y < rh; y++) px[y * rw + x + t] = line;
        }
        for (int j = 0; j < h; j++) {
            int y = (int) Math.round(j * sy);
            for (int t = 0; t < thick && y + t < rh; t++) for (int x = 0; x < rw; x++) px[(y + t) * rw + x] = line;
        }
        gridImg = img; gridW = rw; gridH = rh; gridLevel = level;
        return img;
    }

    /** Save the current frame as a PNG in ~/Pictures/j2me-screenshots (F12). */
    private void screenshot() {
        try {
            java.io.File dir = new java.io.File(System.getProperty("user.home"), "Pictures/j2me-screenshots");
            dir.mkdirs();
            String name = System.getProperty("j2me.title", "game").replaceAll("[^A-Za-z0-9._-]", "_") + "-"
                    + new java.text.SimpleDateFormat("yyyyMMdd-HHmmss").format(new java.util.Date()) + ".png";
            BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
            canvas.$copyFrame(((DataBufferInt) img.getRaster().getDataBuffer()).getData());
            javax.imageio.ImageIO.write(img, "png", new java.io.File(dir, name));
            showToast("Saved " + name);
        } catch (Exception ex) { showToast("Screenshot failed: " + ex.getMessage()); }
        repaint();
    }

    @Override protected void paintComponent(Graphics g0) {
        if (gl != null) { g0.setColor(Color.BLACK); g0.fillRect(0, 0, getWidth(), getHeight()); return; }
        Graphics2D g = (Graphics2D) g0.create();
        g.setColor(Color.BLACK);
        g.fillRect(0, 0, getWidth(), getHeight());
        canvas.$copyFrame(frameData);
        frame.flush();
        Rectangle r = destRect();
        int ox = r.x, oy = r.y, rw = r.width, rh = r.height;
        double sx = rw / (double) w, sy = rh / (double) h, s = Math.min(sx, sy);
        boolean integer = Math.abs(sx - Math.rint(sx)) < 0.001 && Math.abs(sy - Math.rint(sy)) < 0.001;
        if ((Settings.scaleMode == Settings.SCALE_SHARP || Settings.scaleMode == Settings.SCALE_STRETCH) && !integer && s > 1) {
            int k = (int) Math.ceil(Math.max(sx, sy));
            if (pre == null || preK != k) { pre = new BufferedImage(w * k, h * k, BufferedImage.TYPE_INT_RGB); preK = k; }
            Graphics2D pg = pre.createGraphics();
            pg.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
            pg.drawImage(frame, 0, 0, w * k, h * k, null);
            pg.dispose();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.drawImage(pre, ox, oy, rw, rh, null);
        } else {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                    s < 1 ? RenderingHints.VALUE_INTERPOLATION_BILINEAR : RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
            g.drawImage(frame, ox, oy, rw, rh, null);
        }
        if (Compat.isPaused() || Compat.speed() != 1.0) {
            String tag = Compat.isPaused() ? "\u23F8 PAUSED" : "x" + (Compat.speed() == (int) Compat.speed() ? String.valueOf((int) Compat.speed()) : String.valueOf(Compat.speed()));
            g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 14));
            int tw = g.getFontMetrics().stringWidth(tag);
            g.setColor(new Color(0, 0, 0, 160));
            g.fillRoundRect(getWidth() - tw - 22, 8, tw + 14, 24, 8, 8);
            g.setColor(Compat.isPaused() ? new Color(0xFFC000) : Color.WHITE);
            g.drawString(tag, getWidth() - tw - 15, 25);
        }
        int lvl = Settings.lcdLevel;
        if (lvl > 0 && sx >= 2 && sy >= 2) g.drawImage(grid(rw, rh, lvl), ox, oy, null);
        if (toast != null && System.currentTimeMillis() < toastUntil) {
            g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 16));
            g.setColor(new Color(0, 0, 0, 170));
            int tw = g.getFontMetrics().stringWidth(toast);
            g.fillRoundRect(10, 10, tw + 16, 28, 8, 8);
            g.setColor(Color.WHITE);
            g.drawString(toast, 18, 30);
            repaint(100);
        }
        g.dispose();
    }
}
