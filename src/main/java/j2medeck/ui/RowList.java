// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 J2ME Deck contributors
package j2medeck.ui;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/** A vertical list of big, touch-sized rows (label, optional sub-text, value, toggle). Scrolls by dragging; fully usable with a d-pad. */
final class RowList extends JComponent {
    static final class Row {
        final String label;
        String sub, value;
        boolean header, danger, toggle, on, chevron;
        Runnable action;

        Row(String label, String sub, Runnable action) { this.label = label; this.sub = sub; this.action = action; }
        static Row header(String text) { Row r = new Row(text, null, null); r.header = true; return r; }
        Row value(String v) { value = v; return this; }
        Row danger() { danger = true; return this; }
        Row toggle(boolean on) { toggle = true; this.on = on; return this; }
        Row chevron() { chevron = true; return this; }
        boolean selectable() { return !header; }
    }

    private final List<Row> rows = new ArrayList<>();
    private int sel = -1, pressed = -1;
    private boolean active = true;
    private final Anim.Scroller scroller = new Anim.Scroller(this::repaint, () -> {});
    /** Called for a chosen row instead of running its action directly (a sheet uses it to close itself first). */
    Consumer<Row> onActivate;

    RowList() {
        setOpaque(false);
        setFocusable(false);
        Gesture.attach(this, new Gesture.Listener() {
            @Override public void pressed(Point p) { pressed = rowAt(p.y); repaint(); }
            @Override public void released() { pressed = -1; repaint(); }
            @Override public void tap(Point p) {
                int i = rowAt(p.y);
                if (i >= 0 && rows.get(i).selectable()) { sel = i; activate(); }
            }
            @Override public void dragged(Point p, int dx, int dy) { pressed = -1; scroller.drag(-dy); }
            @Override public void flung(double vx, double vy) { scroller.release(-vy); }
            @Override public void wheel(int n, Point p) { scroller.scrollTo(scroller.pos + n * rowH()); }
        });
        addComponentListener(new ComponentAdapter() { @Override public void componentResized(ComponentEvent e) { updateRange(); } });
    }

    private int u() { return Theme.base(); }
    int rowH() { return Math.round(u() * (hasSub() ? 3.6f : 3.2f)); }
    private int headerH() { return Math.round(u() * 2.3f); }
    private boolean hasSub() { for (Row r : rows) if (r.sub != null && !r.sub.isEmpty()) return true; return false; }

    void setRows(List<Row> list) {
        rows.clear(); rows.addAll(list);
        sel = -1;
        for (int i = 0; i < rows.size(); i++) if (rows.get(i).selectable()) { sel = i; break; }
        scroller.snap(0);
        updateRange();
        repaint();
    }
    void setActive(boolean a) { active = a; repaint(); }
    int selected() { return sel; }
    void setSelected(int i) { if (i >= 0 && i < rows.size() && rows.get(i).selectable()) { sel = i; ensureVisible(); repaint(); } }
    Row selectedRow() { return sel >= 0 ? rows.get(sel) : null; }

    int contentHeight() {
        int h = 0;
        for (Row r : rows) h += r.header ? headerH() : rowH();
        return h + u() / 2;
    }

    private void updateRange() { scroller.setRange(0, Math.max(0, contentHeight() - getHeight())); }

    private int top(int idx) { int y = 0; for (int i = 0; i < idx; i++) y += rows.get(i).header ? headerH() : rowH(); return y; }
    private int rowAt(int py) {
        int y = (int) -Math.round(scroller.pos);
        for (int i = 0; i < rows.size(); i++) {
            int h = rows.get(i).header ? headerH() : rowH();
            if (py >= y && py < y + h) return i;
            y += h;
        }
        return -1;
    }

    /** Move the selection by one row (skipping headers); returns false at the ends so callers can hand focus elsewhere. */
    boolean move(int delta) {
        int i = sel;
        do { i += delta; } while (i >= 0 && i < rows.size() && !rows.get(i).selectable());
        if (i < 0 || i >= rows.size()) return false;
        sel = i;
        ensureVisible();
        repaint();
        return true;
    }

    boolean activate() {
        if (sel < 0) return false;
        Row r = rows.get(sel);
        if (r.header) return false;
        if (onActivate != null) onActivate.accept(r); else if (r.action != null) r.action.run();
        repaint();
        return true;
    }

    private void ensureVisible() {
        if (sel < 0) return;
        int t = top(sel), h = rowH(), view = getHeight();
        if (t < scroller.pos) scroller.scrollTo(Math.max(0, t - u() / 2));
        else if (t + h > scroller.pos + view) scroller.scrollTo(t + h - view + u() / 2);
    }

    @Override protected void paintComponent(Graphics g0) {
        Graphics2D g = Gfx.aa(g0);
        g.clipRect(0, 0, getWidth(), getHeight());
        int pad = u() / 2, y = (int) -Math.round(scroller.pos), w = getWidth();
        for (int i = 0; i < rows.size(); i++) {
            Row r = rows.get(i);
            int h = r.header ? headerH() : rowH();
            if (y + h >= 0 && y <= getHeight()) {
                if (r.header) {
                    g.setFont(Theme.font(Font.BOLD, 0.72f));
                    g.setColor(Theme.MUTED);
                    g.drawString(r.label.toUpperCase(), pad + u() / 2, y + h - u() * 0.55f);
                } else paintRow(g, r, pad, y + 3, w - 2 * pad, h - 6, i == sel && active, i == pressed);
            }
            y += h;
        }
        // thin scroll indicator so a long list is recognisable as scrollable
        int total = contentHeight(), view = getHeight();
        if (total > view + 2) {
            int bh = Math.max(u(), (int) ((long) view * view / total)), by = (int) ((view - bh) * (scroller.pos / Math.max(1.0, total - view)));
            g.setColor(Gfx.alpha(Color.WHITE, 0.28));
            g.fill(Gfx.rr(w - 5, Math.max(0, Math.min(view - bh, by)), 3, bh, 3));
        }
        g.dispose();
    }

    private void paintRow(Graphics2D g, Row r, int x, int y, int w, int h, boolean focus, boolean press) {
        double arc = u() * 1.1;
        Color base = focus ? Theme.FOCUS : Gfx.alpha(Color.WHITE, 0.055);
        if (press) base = Gfx.mix(base, Color.WHITE, 0.14);
        g.setColor(base);
        g.fill(Gfx.rr(x, y, w, h, arc));
        if (focus) { g.setColor(Theme.ACCENT); g.setStroke(new BasicStroke(Math.max(2f, u() / 9f))); g.draw(Gfx.rr(x + 1, y + 1, w - 2, h - 2, arc)); }
        int tx = x + u();
        Font lf = Theme.font(Font.BOLD, 1.0f), sf = Theme.font(Font.PLAIN, 0.8f);
        FontMetrics lfm = g.getFontMetrics(lf), sfm = g.getFontMetrics(sf);
        int right = x + w - u();
        // right-hand decoration
        if (r.toggle) {
            int sw = Math.round(u() * 2.5f), sh = Math.round(u() * 1.4f);
            g.setColor(r.on ? Theme.ACCENT : new Color(0x3A4F7C));
            g.fill(Gfx.rr(right - sw, y + (h - sh) / 2.0, sw, sh, sh));
            g.setColor(Color.WHITE);
            int k = sh - 6;
            g.fillOval(r.on ? right - k - 3 : right - sw + 3, y + (h - k) / 2, k, k);
            right -= sw + u();
        } else if (r.chevron) {
            g.setColor(Theme.MUTED);
            g.setStroke(new BasicStroke(Math.max(2f, u() / 9f), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            int cx = right - u() / 3, cy = y + h / 2, s = u() / 3;
            g.drawPolyline(new int[]{cx - s / 2, cx + s / 2, cx - s / 2}, new int[]{cy - s, cy, cy + s}, 3);
            right -= u();
        }
        if (r.value != null) {
            g.setFont(lf);
            g.setColor(focus ? Theme.TEXT : Theme.ACCENT);
            String v = Gfx.fit(lfm, r.value, w / 2);
            g.drawString(v, right - lfm.stringWidth(v), y + (h + lfm.getAscent() - lfm.getDescent()) / 2f);
            right -= lfm.stringWidth(v) + u();
        }
        int maxW = right - tx;
        g.setColor(r.danger ? Theme.BAD : Theme.TEXT);
        g.setFont(lf);
        boolean two = r.sub != null && !r.sub.isEmpty();
        float ly = two ? y + h / 2f - u() * 0.15f : y + (h + lfm.getAscent() - lfm.getDescent()) / 2f;
        g.drawString(Gfx.fit(lfm, r.label, maxW), tx, ly);
        if (two) {
            g.setFont(sf);
            g.setColor(focus ? Theme.TEXT : Theme.MUTED);
            g.drawString(Gfx.fit(sfm, r.sub, maxW), tx, y + h / 2f + u() * 0.95f);
        }
    }
}
