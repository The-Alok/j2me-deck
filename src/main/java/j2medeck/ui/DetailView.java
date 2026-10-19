// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 J2ME Deck contributors
package j2medeck.ui;

import j2medeck.ApiScanner;
import j2medeck.GameInfo;
import j2medeck.LibraryState;
import j2medeck.Profile;
import j2medeck.Settings;

import javax.microedition.lcdui.Canvas;
import javax.swing.*;
import java.awt.*;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * A full page for one game: big cover, name, compatibility, Play, and the settings people change most as large cards
 * (screen size, scaling, LCD grid, renderer, sound, controls). Everything else sits behind the Options sheet.
 */
final class DetailView extends JComponent {
    interface Host {
        void back();
        void play(GameInfo g);
        void options(GameInfo g);
        void toggleFavorite(GameInfo g);
        void sheet(String title, String message, List<RowList.Row> rows);
        void fullSettings(GameInfo g);
        void keyMapping(GameInfo g);
        ApiScanner.Report compat(GameInfo g);
    }

    private static final class Item {
        final String id; final Rectangle r = new Rectangle(); final Runnable action;
        Item(String id, Runnable action) { this.id = id; this.action = action; }
    }

    private static final String[][] SIZES = {
            {"128 x 160", "128", "160"}, {"176 x 208", "176", "208"}, {"176 x 220", "176", "220"}, {"240 x 320", "240", "320"},
            {"240 x 400", "240", "400"}, {"320 x 240 (landscape)", "320", "240"}, {"352 x 416", "352", "416"},
            {"360 x 640", "360", "640"}, {"480 x 800", "480", "800"}};

    private final Host host;
    private GameInfo g;
    private final List<Item> items = new ArrayList<>();
    private int focus;
    private int pressed = -2;                    // -2 none, -1 back chip, >= 0 item
    private final Rectangle backChip = new Rectangle();
    private int contentH;
    private final Anim.Scroller sc = new Anim.Scroller(this::repaint, () -> {});
    private final Anim.Spring appear = new Anim.Spring(1, 140, this::repaint);
    private BufferedImage wash;
    private int washW, washH;

    DetailView(Host host) {
        this.host = host;
        setOpaque(true);
        Gesture.attach(this, new Gesture.Listener() {
            @Override public void pressed(Point p) { pressed = hit(p); repaint(); }
            @Override public void released() { pressed = -2; repaint(); }
            @Override public void tap(Point p) {
                int i = hit(p);
                if (i == -1) host.back();
                else if (i >= 0) { focus = i; items.get(i).action.run(); repaint(); }
            }
            @Override public void dragged(Point p, int dx, int dy) { pressed = -2; sc.drag(-dy); }
            @Override public void flung(double vx, double vy) { sc.release(-vy); }
            @Override public void wheel(int n, Point p) { sc.scrollTo(sc.pos + n * u() * 4); }
        });
    }

    private int u() { return Math.max(13, Math.min(Theme.base(), Math.max(1, getHeight()) / 28)); }
    /** Fonts follow this view's own scale (which shrinks in small windows), not the global one. */
    private Font fnt(int style, float f) { return Theme.font(style, 1f).deriveFont(style, u() * f); }

    GameInfo game() { return g; }

    void show(GameInfo game) {
        g = game;
        cached = null;
        focus = 0;
        wash = null;
        sc.snap(0);
        appear.snap(0);
        appear.to(1);
        repaint();
    }
    void settle() { appear.snap(1); sc.snap(0); repaint(); }
    void refresh() { cached = null; repaint(); }

    private int hit(Point p) {
        if (backChip.contains(p)) return -1;
        int y = p.y + (int) Math.round(sc.pos);
        for (int i = 0; i < items.size(); i++) if (items.get(i).r.contains(p.x, y)) return i;
        return -2;
    }

    // ------------------------------------------------------------------ settings model
    private Profile cached;
    /** The game's profile, read from disk only after it changed (painting asks for it many times per frame). */
    private Profile profile() { if (cached == null) cached = Profile.load(g.id()); return cached; }

    private String sizeValue() {
        Profile p = profile();
        int w = p.getInt("width", 0), h = p.getInt("height", 0);
        if (w > 0 && h > 0) return w + " \u00D7 " + h;
        return p.preset() != null ? p.preset().width + " \u00D7 " + p.preset().height : "Default";
    }

    private void change(java.util.function.Consumer<Profile> edit) {
        Profile p = Profile.load(g.id());
        edit.accept(p);
        cached = null;
        try { p.save(); } catch (IOException e) { System.err.println("[ui] could not save settings: " + e.getMessage()); }
        repaint();
    }

    private void screenSheet() {
        List<RowList.Row> rows = new ArrayList<>();
        String cur = sizeValue();
        rows.add(new RowList.Row("Game default", "Use the size the game or its device preset asks for", () -> change(p -> {
            p.set("width", p.inheritsGlobal("width") ? "0" : null);
            p.set("height", p.inheritsGlobal("height") ? "0" : null);
        })).value("Default".equals(cur) ? "Current" : null));
        for (String[] s : SIZES) {
            String shown = s[1] + " \u00D7 " + s[2];
            rows.add(new RowList.Row(s[0], null, () -> change(p -> { p.set("width", s[1]); p.set("height", s[2]); })).value(shown.equals(cur) ? "Current" : null));
        }
        rows.add(new RowList.Row("More display options\u2026", "Custom size, aspect ratio, fonts, window", () -> host.fullSettings(g)).chevron());
        host.sheet("Screen size", "How big the game's screen is. Bigger sizes need a game that supports them.", rows);
    }

    private void choiceSheet(String title, String message, String key, String[] names, String[] values) {
        Profile p = profile();
        String cur = p.get(key, null);
        List<RowList.Row> rows = new ArrayList<>();
        for (int i = 0; i < names.length; i++) {
            String v = values[i];
            boolean isCur = v == null ? cur == null : v.equalsIgnoreCase(cur);
            rows.add(new RowList.Row(names[i], null, () -> change(pp -> pp.set(key, v))).value(isCur ? "Current" : null));
        }
        host.sheet(title, message, rows);
    }

    private String value(String key, String none) { return profile().get(key, none); }

    // ------------------------------------------------------------------ layout
    private void layoutItems() {
        items.clear();
        int u = u(), w = getWidth(), m = Math.round(u * 2.2f);
        int cover = Math.max(Math.round(u * 9), Math.min(Math.round(u * 15), Math.round(getHeight() * 0.34f)));
        int rx = m + cover + Math.round(u * 2.6f);
        int rw = Math.max(Math.round(u * 20), w - rx - m);
        int y = Math.round(u * 5.6f);
        // title block occupies about 8.5u, then the buttons
        int btnH = Math.round(u * 3.4f);
        int by = y + Math.round(u * 8.6f);
        Item play = new Item("play", () -> host.play(g));
        play.r.setBounds(rx, by, Math.round(u * 12), btnH);
        Item opts = new Item("options", () -> host.options(g));
        opts.r.setBounds(play.r.x + play.r.width + u / 2, by, Math.round(u * 8.5f), btnH);
        Item fav = new Item("fav", () -> host.toggleFavorite(g));
        fav.r.setBounds(opts.r.x + opts.r.width + u / 2, by, btnH, btnH);
        items.add(play); items.add(opts); items.add(fav);
        // quick settings: three cards per row
        int cy = by + btnH + Math.round(u * 3.4f);
        int cols = rw >= u * 34 ? 3 : 2, gap = u / 2 + 2;
        int cw = (rw - gap * (cols - 1)) / cols, ch = Math.round(u * 4.6f);
        Runnable[] acts = {
                this::screenSheet,
                () -> choiceSheet("Scaling", "How the game picture is fitted to the screen.", "scale",
                        new String[]{"Default", "Sharp bilinear", "Nearest (pixel exact)", "Integer", "Stretch"}, new String[]{null, "sharp-bilinear", "nearest", "integer", "stretch"}),
                () -> choiceSheet("LCD grid", "A faux pixel grid that makes small screens look like the original phone.", "lcdgrid",
                        new String[]{"Default", "Off", "Low", "Medium", "High"}, new String[]{null, "off", "low", "medium", "high"}),
                () -> choiceSheet("Renderer", "auto uses OpenGL ES when it is available and falls back to the software renderer.", "renderer",
                        new String[]{"Default", "Auto", "OpenGL ES", "Software (Swing)"}, new String[]{null, "auto", "gles", "swing"}),
                () -> change(p -> p.set("mute", Boolean.TRUE.equals(p.getBool("mute")) ? null : "true")),
                () -> host.keyMapping(g)};
        String[] ids = {"screen", "scale", "lcd", "renderer", "sound", "controls"};
        for (int i = 0; i < ids.length; i++) {
            Item it = new Item(ids[i], acts[i]);
            it.r.setBounds(rx + (i % cols) * (cw + gap), cy + (i / cols) * (ch + gap), cw, ch);
            items.add(it);
        }
        int rows = (ids.length + cols - 1) / cols;
        int bottom = cy + rows * (ch + gap);
        ApiScanner.Report r = host.compat(g);
        if (r != null && r.status != ApiScanner.Status.OK) {
            FontMetrics fm = getFontMetrics(fnt(Font.PLAIN, 0.9f));
            bottom += Math.round(u * 0.8f) + Gfx.wrap(fm, r.summary(), rw).size() * Math.round(u * 1.4f) + Math.round(u * 1.4f);
        }
        contentH = Math.max(bottom, y + cover) + m;
        sc.setRange(0, Math.max(0, contentH - getHeight() + 0));
        coverRect.setBounds(m, y, cover, cover);
        textX = rx; textW = rw; titleY = y; cardTop = cy - Math.round(u * 2.2f);
    }
    private final Rectangle coverRect = new Rectangle();
    private int textX, textW, titleY, cardTop;

    // ------------------------------------------------------------------ input
    boolean key(int code) {
        if (g == null) return false;
        switch (code) {
            case Canvas.KEY_UP: nav(0); return true;
            case Canvas.KEY_DOWN: nav(1); return true;
            case Canvas.KEY_LEFT: nav(2); return true;
            case Canvas.KEY_RIGHT: nav(3); return true;
            case Canvas.KEY_FIRE: if (focus >= 0 && focus < items.size()) items.get(focus).action.run(); repaint(); return true;
            case Canvas.KEY_SOFT_LEFT: host.options(g); return true;
            case Canvas.KEY_NUM5: host.toggleFavorite(g); return true;
            case Canvas.KEY_SOFT_RIGHT: case Canvas.KEY_CLEAR: host.back(); return true;
            default: return false;
        }
    }

    private void nav(int dir) {
        layoutItems();
        if (items.isEmpty()) return;
        Rectangle a = items.get(focus).r;
        double ax = a.getCenterX(), ay = a.getCenterY();
        int best = -1; double bestScore = Double.MAX_VALUE;
        for (int j = 0; j < items.size(); j++) {
            if (j == focus) continue;
            Rectangle b = items.get(j).r;
            double dx = b.getCenterX() - ax, dy = b.getCenterY() - ay;
            boolean ok = dir == 0 ? dy < -2 : dir == 1 ? dy > 2 : dir == 2 ? dx < -2 : dx > 2;
            if (!ok) continue;
            double primary = dir < 2 ? Math.abs(dy) : Math.abs(dx), secondary = dir < 2 ? Math.abs(dx) : Math.abs(dy);
            double score = primary + secondary * 2.5;
            if (score < bestScore) { bestScore = score; best = j; }
        }
        if (best >= 0) {
            focus = best;
            Rectangle r = items.get(best).r;
            if (r.y < sc.pos + u()) sc.scrollTo(Math.max(0, r.y - u() * 5));
            else if (r.y + r.height > sc.pos + getHeight() - u()) sc.scrollTo(r.y + r.height - getHeight() + u() * 2);
        }
        repaint();
    }

    // ------------------------------------------------------------------ painting
    @Override protected void paintComponent(Graphics g0) {
        Graphics2D g = Gfx.aa(g0);
        int w = getWidth(), h = getHeight(), u = u();
        g.setColor(Theme.BG);
        g.fillRect(0, 0, w, h);
        if (this.g == null) { g.dispose(); return; }
        if (wash == null || washW != w || washH != h) {
            BufferedImage half = ShelfView.wash(this.g, w, h);       // stretched to full size once, so painting is a plain blit
            wash = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
            Graphics2D wg = wash.createGraphics();
            wg.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            wg.drawImage(half, 0, 0, w, h, null);
            wg.dispose();
            washW = w; washH = h;
        }
        g.drawImage(wash, 0, 0, null);
        layoutItems();
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, (float) Math.max(0, Math.min(1, appear.x))));
        g.translate((1 - appear.x) * u * 3, 0);
        // back chip stays put
        Graphics2D fixed = (Graphics2D) g.create();
        paintBack(fixed);
        fixed.dispose();
        g.translate(0, -Math.round(sc.pos));
        paintBody(g);
        g.dispose();
    }

    private void paintBack(Graphics2D g) {
        int u = u();
        g.setFont(fnt(Font.BOLD, 0.95f));
        FontMetrics fm = g.getFontMetrics();
        String t = "Library";
        int bh = Math.round(u * 2.6f), bw = fm.stringWidth(t) + Math.round(u * 4.2f);
        backChip.setBounds(Math.round(u * 1.6f), Math.round(u * 1.1f), bw, bh);
        g.setColor(pressed == -1 ? Gfx.alpha(Color.WHITE, 0.26) : Gfx.alpha(Color.WHITE, 0.12));
        g.fill(Gfx.rr(backChip.x, backChip.y, bw, bh, bh));
        g.setColor(Theme.TEXT);
        g.setStroke(new BasicStroke(Math.max(2f, u / 9f), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        int ax = backChip.x + Math.round(u * 1.3f), ay = backChip.y + bh / 2, s = u / 3;
        g.drawPolyline(new int[]{ax + s / 2, ax - s / 2, ax + s / 2}, new int[]{ay - s, ay, ay + s}, 3);
        g.drawString(t, backChip.x + Math.round(u * 2.5f), backChip.y + (bh + fm.getAscent() - fm.getDescent()) / 2f);
    }

    private void paintBody(Graphics2D g) {
        int u = u();
        GameInfo gi = this.g;
        Covers.Cover cv = Covers.of(gi);
        double arc = u * 2;
        Gfx.shadow(g, coverRect.x, coverRect.y, coverRect.width, coverRect.height, arc, u * 2, 0.8, u / 2);
        g.drawImage(cv.card(coverRect.width, arc), coverRect.x, coverRect.y, null);
        // title block
        int x = textX, y = titleY;
        long last = LibraryState.lastPlayed(gi);
        g.setFont(fnt(Font.BOLD, 0.78f));
        g.setColor(Theme.ACCENT);
        g.drawString(last > 0 ? "LAST PLAYED  \u00B7  " + LibraryState.ago(last).toUpperCase() : "NOT PLAYED YET", x, y + u * 0.9f);
        g.setFont(fnt(Font.BOLD, 2.3f));
        FontMetrics fm = g.getFontMetrics();
        g.setColor(Theme.TEXT);
        int ty = y + Math.round(u * 0.9f) + fm.getAscent() + u / 3;
        g.drawString(Gfx.fit(fm, gi.name(), textW), x, ty);
        g.setFont(fnt(Font.PLAIN, 1.0f));
        fm = g.getFontMetrics();
        g.setColor(Theme.MUTED);
        String sub = (gi.vendor().isEmpty() ? "" : gi.vendor()) + (gi.version().isEmpty() ? "" : (gi.vendor().isEmpty() ? "" : "   \u00B7   ") + "v" + gi.version());
        g.drawString(Gfx.fit(fm, sub, textW), x, ty + Math.round(u * 1.7f));
        // chips
        int chipH = Math.round(u * 1.9f), chipY = ty + Math.round(u * 2.4f), cx = x;
        ApiScanner.Report rep = host.compat(gi);
        String ct; Color cc;
        if (rep == null) { ct = "Checking compatibility\u2026"; cc = Theme.MUTED; }
        else if (rep.status == ApiScanner.Status.OK) { ct = "Compatible"; cc = Theme.OK; }
        else if (rep.status == ApiScanner.Status.PARTIAL) { ct = "May have problems"; cc = Theme.WARN; }
        else { ct = "Unsupported features"; cc = Theme.BAD; }
        cx += chip(g, cx, chipY, chipH, ct, cc, true) + u / 2;
        if (gi.midlets.size() > 1) cx += chip(g, cx, chipY, chipH, gi.midlets.size() + " programs", Theme.MUTED, false) + u / 2;
        int plays = LibraryState.plays(gi);
        if (plays > 0) chip(g, cx, chipY, chipH, "Played " + plays + (plays == 1 ? " time" : " times"), Theme.MUTED, false);
        // buttons
        Item play = items.get(0), opts = items.get(1), fav = items.get(2);
        button(g, play.r, "Play", true, focus == 0, pressed == 0);
        button(g, opts.r, "Options", false, focus == 1, pressed == 1);
        boolean isFav = LibraryState.isFavorite(gi);
        pill(g, fav.r, Gfx.alpha(Color.WHITE, 0.14), pressed == 2);
        ShelfView.star(g, fav.r.getCenterX(), fav.r.getCenterY() + 1, fav.r.height * 0.30, isFav, isFav ? Theme.ACCENT : Theme.TEXT);
        if (focus == 2) ring(g, fav.r, fav.r.height);
        // quick settings header
        g.setFont(fnt(Font.BOLD, 0.78f));
        g.setColor(Theme.MUTED);
        g.drawString("QUICK SETTINGS", x + u / 4, cardTop + u * 0.8f);
        String[] labels = {"Screen size", "Scaling", "LCD grid", "Renderer", "Sound", "Controls"};
        Profile p = profile();
        String[] vals = {sizeValue(), cap(value("scale", "Default")), cap(value("lcdgrid", "Default")), rendererName(value("renderer", "Default")),
                Boolean.TRUE.equals(p.getBool("mute")) ? "Muted" : "On", "Key mapping"};
        for (int i = 0; i < labels.length; i++) card(g, items.get(3 + i).r, labels[i], vals[i], focus == 3 + i, pressed == 3 + i, i == 4 ? !Boolean.TRUE.equals(p.getBool("mute")) : null);
        // compatibility details
        if (rep != null && rep.status != ApiScanner.Status.OK) {
            Rectangle last2 = items.get(items.size() - 1).r;
            int ry = last2.y + last2.height + Math.round(u * 1.2f);
            if (items.size() > 2) {
                int maxBottom = 0;
                for (Item it : items) maxBottom = Math.max(maxBottom, it.r.y + it.r.height);
                ry = maxBottom + Math.round(u * 1.2f);
            }
            g.setFont(fnt(Font.PLAIN, 0.9f));
            g.setColor(Gfx.mix(Theme.MUTED, rep.status == ApiScanner.Status.PARTIAL ? Theme.WARN : Theme.BAD, 0.25));
            for (String line : Gfx.wrap(g.getFontMetrics(), rep.summary(), textW)) { g.drawString(line, x, ry + u * 0.6f); ry += Math.round(u * 1.4f); }
        }
    }

    private static String cap(String s) {
        if (s == null || s.isEmpty()) return "";
        if (s.equals("sharp-bilinear")) return "Sharp bilinear";
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
    private static String rendererName(String s) {
        switch (s.toLowerCase()) { case "gles": return "OpenGL ES"; case "swing": return "Software"; default: return cap(s); }
    }

    private void pill(Graphics2D g, Rectangle r, Color fill, boolean pressed) {
        g.setColor(pressed ? Gfx.mix(fill, Color.BLACK, 0.2) : fill);
        g.fill(Gfx.rr(r.x, r.y, r.width, r.height, r.height));
    }
    private void ring(Graphics2D g, Rectangle r, double arc) {
        g.setColor(Gfx.alpha(Theme.ACCENT, 0.28));
        g.setStroke(new BasicStroke(u() * 0.8f));
        g.draw(Gfx.rr(r.x - 1, r.y - 1, r.width + 2, r.height + 2, arc + 2));
        g.setColor(Theme.ACCENT);
        g.setStroke(new BasicStroke(Math.max(3f, u() / 6.5f)));
        g.draw(Gfx.rr(r.x - 2, r.y - 2, r.width + 4, r.height + 4, arc + 4));
    }

    private void button(Graphics2D g, Rectangle r, String text, boolean primary, boolean focused, boolean pressed) {
        int u = u();
        pill(g, r, primary ? Theme.ACCENT : Gfx.alpha(Color.WHITE, 0.14), pressed);
        g.setFont(fnt(Font.BOLD, primary ? 1.2f : 1.0f));
        FontMetrics fm = g.getFontMetrics();
        g.setColor(primary ? Gfx.PLAY_TEXT : Theme.TEXT);
        int tw = fm.stringWidth(text), off = primary ? Math.round(u * 0.9f) : 0;
        g.drawString(text, r.x + (r.width - tw) / 2f + off, r.y + (r.height + fm.getAscent() - fm.getDescent()) / 2f);
        if (primary) {
            int ts = Math.round(u * 1.0f), tx0 = r.x + (r.width - tw) / 2 - Math.round(u * 0.9f) + off - ts / 2, ty = r.y + r.height / 2;
            Path2D tri = new Path2D.Double();
            tri.moveTo(tx0, ty - ts / 2.0); tri.lineTo(tx0 + ts * 0.85, ty); tri.lineTo(tx0, ty + ts / 2.0); tri.closePath();
            g.fill(tri);
        }
        if (focused) ring(g, r, r.height);
    }

    private void card(Graphics2D g, Rectangle r, String label, String value, boolean focused, boolean pressed, Boolean on) {
        int u = u();
        double arc = u * 1.3;
        Color base = focused ? Theme.FOCUS : Gfx.alpha(Color.WHITE, 0.07);
        if (pressed) base = Gfx.mix(base, Color.WHITE, 0.14);
        g.setColor(base);
        g.fill(Gfx.rr(r.x, r.y, r.width, r.height, arc));
        if (focused) { g.setColor(Theme.ACCENT); g.setStroke(new BasicStroke(Math.max(2.5f, u / 7f))); g.draw(Gfx.rr(r.x + 1, r.y + 1, r.width - 2, r.height - 2, arc)); }
        g.setFont(fnt(Font.PLAIN, 0.8f));
        g.setColor(focused ? Theme.TEXT : Theme.MUTED);
        g.drawString(label, r.x + u, r.y + u * 1.5f);
        g.setFont(fnt(Font.BOLD, 1.1f));
        FontMetrics fm = g.getFontMetrics();
        g.setColor(on != null ? (on ? Theme.OK : Theme.WARN) : (focused ? Theme.TEXT : Theme.ACCENT));
        g.drawString(Gfx.fit(fm, value, r.width - Math.round(u * 3.2f)), r.x + u, r.y + r.height - u * 1.0f);
        // chevron
        g.setColor(Gfx.alpha(Theme.MUTED, 0.9));
        g.setStroke(new BasicStroke(Math.max(2f, u / 9f), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        int cx = r.x + r.width - Math.round(u * 1.2f), cy = r.y + r.height - Math.round(u * 1.5f), s = u / 3;
        if (on == null) g.drawPolyline(new int[]{cx - s / 2, cx + s / 2, cx - s / 2}, new int[]{cy - s, cy, cy + s}, 3);
    }

    private int chip(Graphics2D g, int x, int y, int h, String text, Color c, boolean dot) {
        int u = u();
        g.setFont(fnt(Font.BOLD, 0.82f));
        FontMetrics fm = g.getFontMetrics();
        int w = fm.stringWidth(text) + Math.round(u * (dot ? 3.2f : 1.9f));
        g.setColor(Gfx.alpha(Color.BLACK, 0.35));
        g.fill(Gfx.rr(x, y, w, h, h));
        g.setColor(Gfx.alpha(c, 0.9));
        g.setStroke(new BasicStroke(1.3f));
        g.draw(Gfx.rr(x + .5, y + .5, w - 1, h - 1, h));
        int tx = x + Math.round(u * (dot ? 2.2f : 0.95f));
        if (dot) { int d = Math.round(u * 0.7f); g.setColor(c); g.fillOval(x + Math.round(u * 0.9f), y + (h - d) / 2, d, d); }
        g.setColor(dot ? Theme.TEXT : c);
        g.drawString(text, tx, y + (h + fm.getAscent() - fm.getDescent()) / 2f);
        return w;
    }
}
