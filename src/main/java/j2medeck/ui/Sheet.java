// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 J2ME Deck contributors
package j2medeck.ui;

import javax.microedition.lcdui.Canvas;
import javax.swing.*;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.util.ArrayList;
import java.util.List;

/**
 * A modal bottom sheet that replaces JOptionPane: a title, optional message and big rows (or scrollable text), all
 * reachable by touch, mouse or d-pad. Tapping outside, or B, closes it.
 */
final class Sheet extends JComponent {
    private final RowList list = new RowList();
    private final TextBody body = new TextBody();
    private String title = "", message = "";
    private boolean textMode, open;
    private final Anim.Spring slide = new Anim.Spring(0, 260, () -> { doLayout(); repaint(); });
    private Runnable onClose;

    Sheet() {
        setOpaque(false);
        setVisible(false);
        add(list);
        add(body);
        body.setVisible(false);
        list.onActivate = r -> { Runnable a = r.action; close(); if (a != null) a.run(); };
        Gesture.attach(this, new Gesture.Listener() {
            @Override public void tap(Point p) { if (!panel().contains(p)) close(); }
        });
        addComponentListener(new ComponentAdapter() { @Override public void componentResized(ComponentEvent e) { doLayout(); } });
    }

    private int u() { return Theme.base(); }
    boolean isOpen() { return open; }

    /** Show a titled list of rows. A "Cancel" row is added automatically unless one is already there. */
    void showRows(String title, String message, List<RowList.Row> rows) {
        List<RowList.Row> all = new ArrayList<>(rows);
        all.add(new RowList.Row("Cancel", null, null));
        begin(title, message, false);
        list.setRows(all);
        list.setActive(true);
        slideIn();
    }

    /** A short notice with a single OK row. */
    void showMessage(String title, String message) {
        begin(title, message, false);
        list.setRows(List.of(new RowList.Row("OK", null, null)));
        list.setActive(true);
        slideIn();
    }

    /** Show long text (a log, a compatibility report) in a scrollable monospaced area with a Close row. */
    void showText(String title, String text) {
        begin(title, "", true);
        body.setText(text);
        list.setRows(List.of(new RowList.Row("Close", null, null)));
        list.setActive(true);
        slideIn();
    }

    private void begin(String t, String m, boolean text) {
        title = t == null ? "" : t; message = m == null ? "" : m; textMode = text;
        body.setVisible(text);
        open = true;
        setVisible(true);
        slide.snap(0);
    }
    private void slideIn() { doLayout(); slide.to(1); repaint(); }

    /** Jump the slide animation to its end (screenshot tests). */
    void settle() { slide.snap(open ? 1 : 0); doLayout(); repaint(); }

    void close() {
        if (!open) return;
        open = false;
        setVisible(false);
        slide.snap(0);
        if (onClose != null) onClose.run();
    }
    void setOnClose(Runnable r) { onClose = r; }

    /** Gamepad / keyboard input while the sheet is up. Always consumes. */
    void key(int code) {
        switch (code) {
            case Canvas.KEY_UP: if (textMode) body.scrollLines(-3); else list.move(-1); break;
            case Canvas.KEY_DOWN: if (textMode) body.scrollLines(3); else list.move(1); break;
            case Canvas.KEY_FIRE: list.activate(); break;
            case Canvas.KEY_SOFT_RIGHT: case Canvas.KEY_CLEAR: close(); break;
            default:
        }
    }

    // ------------------------------------------------------------------ layout
    private Rectangle panel() {
        int w = getWidth(), h = getHeight();
        int pw = Math.min(w - u() * 2, Math.round(u() * (textMode ? 62 : 40)));
        int titleH = titleHeight(pw);
        int avail = h - Math.round(u() * 4.0f) - u();
        int maxBody = avail - titleH - u();
        int bodyH;
        if (textMode) bodyH = Math.min(maxBody, Math.round(u() * 22)) + list.rowH() + u() / 2;
        else bodyH = Math.min(list.contentHeight(), maxBody);
        int ph = titleH + bodyH + u() / 2;
        int y = Math.round(h - Math.round(u() * 3.8f) - ph * (float) slide.x);
        return new Rectangle((w - pw) / 2, y, pw, ph);
    }

    private int titleHeight(int pw) {
        int h = Math.round(u() * 3.4f);
        if (!message.isEmpty()) {
            FontMetrics fm = getFontMetrics(Theme.font(Font.PLAIN, 0.9f));
            h += Gfx.wrap(fm, message, pw - u() * 3).size() * Math.round(u() * 1.3f) + u() / 2;
        }
        return h;
    }

    @Override public void doLayout() {
        if (getWidth() == 0) return;
        Rectangle p = panel();
        int th = titleHeight(p.width), inner = p.width;
        if (textMode) {
            int closeH = list.rowH() + u() / 2;
            body.setBounds(p.x + u() / 2, p.y + th, inner - u(), p.height - th - closeH - u() / 2);
            list.setBounds(p.x, p.y + p.height - closeH - u() / 2, inner, closeH);
        } else list.setBounds(p.x, p.y + th, inner, p.height - th - u() / 2);
    }

    @Override protected void paintComponent(Graphics g0) {
        Graphics2D g = Gfx.aa(g0);
        g.setColor(new Color(0, 0, 0, Gfx.clamp255(150 * slide.x)));
        g.fillRect(0, 0, getWidth(), getHeight());
        Rectangle p = panel();
        Gfx.shadow(g, p.x, p.y, p.width, p.height, u() * 2, 18, 0.7, 6);
        g.setColor(Theme.PANEL);
        g.fill(Gfx.rr(p.x, p.y, p.width, p.height, u() * 2));
        g.setColor(Theme.BORDER);
        g.setStroke(new BasicStroke(1.2f));
        g.draw(Gfx.rr(p.x + .5, p.y + .5, p.width - 1, p.height - 1, u() * 2));
        g.setFont(Theme.font(Font.BOLD, 1.25f));
        g.setColor(Theme.TEXT);
        FontMetrics fm = g.getFontMetrics();
        g.drawString(Gfx.fit(fm, title, p.width - u() * 3), p.x + u() * 1.5f, p.y + u() * 2.2f);
        if (!message.isEmpty()) {
            g.setFont(Theme.font(Font.PLAIN, 0.9f));
            g.setColor(Theme.MUTED);
            int y = p.y + Math.round(u() * 3.5f);
            for (String line : Gfx.wrap(g.getFontMetrics(), message, p.width - u() * 3)) { g.drawString(line, p.x + u() * 1.5f, y); y += Math.round(u() * 1.3f); }
        }
        g.dispose();
    }

    /** Scrollable monospaced text. */
    private final class TextBody extends JComponent {
        private String text = "";
        private List<String> lines = new ArrayList<>();
        private int wrapW = -1;
        private final Anim.Scroller sc = new Anim.Scroller(this::repaint, () -> {});

        TextBody() {
            setOpaque(false);
            Gesture.attach(this, new Gesture.Listener() {
                @Override public void dragged(Point p, int dx, int dy) { sc.drag(-dy); }
                @Override public void flung(double vx, double vy) { sc.release(-vy); }
                @Override public void wheel(int n, Point p) { sc.scrollTo(sc.pos + n * lineH() * 3); }
            });
        }
        private Font font() { return new Font(Font.MONOSPACED, Font.PLAIN, Math.round(u() * 0.78f)); }
        private int lineH() { return Math.round(u() * 1.15f); }
        void setText(String t) { text = t; wrapW = -1; sc.snap(0); }
        void scrollLines(int n) { sc.scrollTo(sc.pos + n * lineH()); }

        private void rewrap() {
            if (wrapW == getWidth()) return;
            wrapW = getWidth();
            lines = Gfx.wrap(getFontMetrics(font()), text.replace("\t", "    "), Math.max(40, wrapW - u()));
            sc.setRange(0, Math.max(0, lines.size() * lineH() + u() - getHeight()));
        }

        @Override protected void paintComponent(Graphics g0) {
            Graphics2D g = Gfx.aa(g0);
            rewrap();
            g.setColor(Theme.BG);
            g.fill(Gfx.rr(0, 0, getWidth(), getHeight(), u()));
            g.clip(Gfx.rr(0, 0, getWidth(), getHeight(), u()));
            g.setFont(font());
            g.setColor(Theme.TEXT);
            int y = (int) -Math.round(sc.pos) + lineH() + u() / 4;
            for (String line : lines) {
                if (y > -lineH() && y < getHeight() + lineH()) g.drawString(line, u() / 2, y);
                y += lineH();
            }
            g.dispose();
        }
        void toEnd() { rewrap(); sc.snap(sc.max); }
    }

    /** Scroll the text to its end (used for logs). Call after showText. */
    void textToEnd() { body.setSize(Math.max(1, body.getWidth()), Math.max(1, body.getHeight())); body.toEnd(); }
}
