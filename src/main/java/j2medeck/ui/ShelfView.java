// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 J2ME Deck contributors
package j2medeck.ui;

import j2medeck.ApiScanner;
import j2medeck.GameInfo;
import j2medeck.LibraryState;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The library home: a blurred, colour-tinted backdrop taken from the focused game, a hero block with its name and a big Play button,
 * and a horizontal shelf of cover tiles. Drag or swipe the shelf, tap a tile to focus it and tap it again to play, long-press for options.
 * With a gamepad: left / right to browse, A to play.
 */
final class ShelfView extends JComponent {
    interface Host {
        void play(GameInfo g);
        void details(GameInfo g);
        void options(GameInfo g);
        void toggleFavorite(GameInfo g);
        void addGames();
        ApiScanner.Report compat(GameInfo g);
    }

    private final Host host;
    private final List<GameInfo> games = new ArrayList<>();
    private boolean addTile = true;
    private String emptyTitle = "", emptyHint = "";
    private int sel;
    private boolean focused = true, userScroll;
    private final Anim.Scroller sc = new Anim.Scroller(this::onScroll, this::onSettle);
    private final Anim.Spring fade = new Anim.Spring(1, 90, this::repaint);
    private BufferedImage washCur, washPrev;
    private final Map<GameInfo, BufferedImage> washCache = new LinkedHashMap<>(8, 0.75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<GameInfo, BufferedImage> e) { return size() > 5; }
    };
    // hit areas computed while painting
    private Rectangle playBtn = new Rectangle(), detailsBtn = new Rectangle(), favBtn = new Rectangle();
    private final List<Rectangle> tileRects = new ArrayList<>();
    private int pressedTile = -1;
    private String pressedBtn = "";
    private int tileTop, tileSize;
    /** Called when the focused tile changes (the window swaps its button hints for the add tile). */
    Runnable onSelectionChanged = () -> {};

    ShelfView(Host host) {
        this.host = host;
        setOpaque(true);
        setFocusable(false);
        Gesture.attach(this, new Gesture.Listener() {
            @Override public void pressed(Point p) { pressedBtn = buttonAt(p); pressedTile = pressedBtn.isEmpty() ? tileAt(p) : -1; repaint(); }
            @Override public void released() { pressedBtn = ""; pressedTile = -1; repaint(); }
            @Override public void tap(Point p) { onTap(p); }
            @Override public void longPress(Point p) {
                int i = tileAt(p);
                if (i >= 0 && i < games.size()) { select(i, true); host.options(games.get(i)); }
            }
            @Override public void dragged(Point p, int dx, int dy) {
                if (p.y < tileTop - Theme.base() * 2 || count() < 2) return;
                userScroll = true;
                pressedTile = -1; pressedBtn = "";
                sc.drag(-dx);
            }
            @Override public void flung(double vx, double vy) { if (userScroll) sc.release(-vx); }
            @Override public void wheel(int n, Point p) { move(n); }
        });
    }

    // ------------------------------------------------------------------ model
    private int u() { return Math.max(13, Math.min(Theme.base(), getHeight() / 28)); }
    /** Fonts follow this view's own scale (which shrinks in small windows), not the global one. */
    private Font fnt(int style, float f) { return Theme.font(style, 1f).deriveFont(style, u() * f); }
    private int stride() { return tileSize + Math.round(u() * 1.1f); }
    private int count() { return games.size() + (addTile ? 1 : 0); }

    void setGames(List<GameInfo> list, boolean withAddTile, String emptyTitle, String emptyHint, GameInfo keep) {
        games.clear(); games.addAll(list);
        addTile = withAddTile;
        this.emptyTitle = emptyTitle; this.emptyHint = emptyHint;
        int idx = keep == null ? 0 : Math.max(0, games.indexOf(keep));
        sel = Math.max(0, Math.min(Math.max(0, count() - 1), idx));
        userScroll = false;
        layoutMetrics();
        sc.snap(sel * stride());
        washPrev = null;
        washCur = null;
        washValid = false;
        fade.snap(1);
        refreshWash();
        repaint();
    }

    GameInfo selectedGame() { return sel >= 0 && sel < games.size() ? games.get(sel) : null; }
    boolean addTileSelected() { return addTile && sel == games.size(); }
    boolean isEmpty() { return count() == 0; }
    void setFocused(boolean f) { focused = f; repaint(); }
    void refreshInfo() { repaint(); }

    void move(int delta) { select(sel + delta, true); }

    void select(int i, boolean animate) {
        int n = count();
        if (n == 0) return;
        i = Math.max(0, Math.min(n - 1, i));
        userScroll = false;
        boolean changed = i != sel;
        sel = i;
        layoutMetrics();
        if (animate) sc.scrollTo(i * stride()); else sc.snap(i * stride());
        if (changed) { refreshWash(); onSelectionChanged.run(); }
        repaint();
    }

    /** Jump all animations to their end (used by the screenshot tests). */
    void settle() { layoutMetrics(); sc.snap(sel * stride()); fade.snap(1); washPrev = null; refreshWash(); repaint(); }

    private void onScroll() {
        if (userScroll && stride() > 0) {
            int i = (int) Math.max(0, Math.min(count() - 1, Math.round(sc.pos / stride())));
            if (i != sel) { sel = i; refreshWash(); onSelectionChanged.run(); }
        }
        repaint();
    }
    private void onSettle() {
        if (count() == 0) return;
        if (userScroll) { userScroll = false; sc.scrollTo(sel * stride()); }
    }

    private void onTap(Point p) {
        String b = buttonAt(p);
        GameInfo cur = selectedGame();
        if (b.equals("play")) { if (cur != null) host.play(cur); else if (addTileSelected() || isEmpty()) host.addGames(); return; }
        if (b.equals("details") && cur != null) { host.details(cur); return; }
        if (b.equals("fav") && cur != null) { host.toggleFavorite(cur); repaint(); return; }
        int i = tileAt(p);
        if (i < 0) return;
        if (i != sel) select(i, true);
        else if (i < games.size()) host.play(games.get(i));
        else host.addGames();
    }

    private String buttonAt(Point p) {
        if (playBtn.contains(p)) return "play";
        if (detailsBtn.contains(p)) return "details";
        if (favBtn.contains(p)) return "fav";
        return "";
    }
    private int tileAt(Point p) {
        for (int i = 0; i < tileRects.size(); i++) {
            Rectangle r = tileRects.get(i);
            // the touch target includes the caption below the tile
            if (r.x <= p.x && p.x < r.x + r.width && r.y - u() / 2 <= p.y && p.y < r.y + r.height + Math.round(u() * 2.2f)) return i;
        }
        return -1;
    }

    // ------------------------------------------------------------------ layout & backdrop
    private void layoutMetrics() {
        int h = getHeight() == 0 ? 720 : getHeight(), u = u();
        int avail = h - TopBar.heightFor(u);
        tileSize = Math.max(Math.round(u * 6f), Math.min(Math.round(u * 10.5f), Math.round(avail * 0.28f)));
        tileTop = h - Math.round(u * 1.3f) - Math.round(u * 2.3f) - tileSize;
        sc.setRange(0, Math.max(0, (count() - 1) * stride()));
    }

    @Override public void doLayout() { layoutMetrics(); if (sc.moving() == false) sc.snap(sel * stride()); }

    private GameInfo washGame;
    private boolean washValid;

    /** Picks the backdrop for the focused game. Backdrops are stored at half size and stretched to full size once here, so painting is a plain blit. */
    private void refreshWash() {
        if (getWidth() == 0) return;
        GameInfo g = selectedGame();
        if (washValid && washGame == g) return;
        BufferedImage half = g == null ? emptyWash() : washCache.computeIfAbsent(g, this::buildWash);
        BufferedImage full = new BufferedImage(getWidth(), getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D fg = full.createGraphics();
        fg.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        fg.drawImage(half, 0, 0, getWidth(), getHeight(), null);
        fg.dispose();
        washPrev = washCur;
        washCur = full;
        washGame = g;
        washValid = true;
        if (washPrev != null) { fade.snap(0); fade.to(1); } else fade.snap(1);
    }

    private BufferedImage emptyWash() {
        if (washCache.containsKey(null)) return washCache.get(null);
        BufferedImage img = wash(null, getWidth(), getHeight());
        washCache.put(null, img);
        return img;
    }
    private BufferedImage buildWash(GameInfo g) { return wash(g, getWidth(), getHeight()); }

    /** The backdrop for a game at half the given size (it is stretched on paint, which doubles as a blur). Null game = neutral blue. */
    static BufferedImage wash(GameInfo g, int fullW, int fullH) {
        int W = Math.max(2, fullW / 2), H = Math.max(2, fullH / 2);
        BufferedImage img = new BufferedImage(W, H, BufferedImage.TYPE_INT_RGB);
        Graphics2D gr = Gfx.aa(img.createGraphics());
        gr.setColor(Theme.BG);
        gr.fillRect(0, 0, W, H);
        Color tint = g == null ? new Color(0x24407A) : Covers.of(g).tint;
        // colour glow on the right, where the hero has no text
        gr.setPaint(new RadialGradientPaint(new Point(Math.round(W * 0.78f), Math.round(H * 0.28f)), Math.max(W, H) * 0.75f,
                new float[]{0f, 1f}, new Color[]{Gfx.alpha(tint, 0.78), Gfx.alpha(tint, 0.0)}));
        gr.fillRect(0, 0, W, H);
        // the icon itself, blown up and blurred by scaling a tiny copy
        Covers.Cover c = g == null ? null : Covers.of(g);
        if (c != null && c.img != null) {
            BufferedImage tiny = Gfx.resize(c.img, 14, 9, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            gr.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.30f));
            gr.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            gr.drawImage(tiny, -Math.round(W * 0.1f), -Math.round(H * 0.1f), Math.round(W * 1.2f), Math.round(H * 1.2f), null);
            gr.setComposite(AlphaComposite.SrcOver);
        }
        // darken left (text) and bottom (shelf)
        gr.setPaint(new GradientPaint(0, 0, Gfx.alpha(Theme.BG, 0.92), W * 0.62f, 0, Gfx.alpha(Theme.BG, 0.0)));
        gr.fillRect(0, 0, W, H);
        gr.setPaint(new GradientPaint(0, H * 0.42f, Gfx.alpha(Theme.BG, 0.0), 0, H, Gfx.alpha(Theme.BG, 0.96)));
        gr.fillRect(0, 0, W, H);
        gr.dispose();
        return img;
    }

    @Override public void setBounds(int x, int y, int w, int h) {
        boolean resized = w != getWidth() || h != getHeight();
        super.setBounds(x, y, w, h);
        if (resized && w > 0) { washCache.clear(); washPrev = null; washCur = null; washValid = false; layoutMetrics(); refreshWash(); fade.snap(1); }
    }

    // ------------------------------------------------------------------ painting
    @Override protected void paintComponent(Graphics g0) {
        Graphics2D g = Gfx.aa(g0);
        int w = getWidth(), h = getHeight();
        if (washCur == null) refreshWash();
        g.setColor(Theme.BG);
        g.fillRect(0, 0, w, h);
        if (washPrev != null && fade.x < 1) g.drawImage(washPrev, 0, 0, null);
        if (washCur != null) {
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, (float) Math.max(0, Math.min(1, washPrev == null ? 1 : fade.x))));
            g.drawImage(washCur, 0, 0, null);
            g.setComposite(AlphaComposite.SrcOver);
        }
        layoutMetrics();
        paintHero(g);
        paintShelf(g);
        g.dispose();
    }

    private void pill(Graphics2D g, Rectangle r, Color fill, boolean pressed) {
        g.setColor(pressed ? Gfx.mix(fill, Color.BLACK, 0.18) : fill);
        g.fill(Gfx.rr(r.x, r.y, r.width, r.height, r.height));
    }

    private void paintHero(Graphics2D g) {
        int u = u(), pad = Math.round(u * 2f), w = getWidth();
        int top = TopBar.heightFor(u);
        GameInfo cur = selectedGame();
        playBtn = new Rectangle(); detailsBtn = new Rectangle(); favBtn = new Rectangle();
        int btnH = Math.round(u * 3.2f);
        int maxTextW = Math.min(Math.round(w * 0.62f), Math.round(u * 42));
        // bottom-align the block just above the shelf
        int blockBottom = tileTop - Math.round(u * 1.6f);
        int by = blockBottom - btnH;
        String label, title, sub;
        if (cur != null) {
            long last = LibraryState.lastPlayed(cur);
            label = last > 0 ? "LAST PLAYED  \u00B7  " + LibraryState.ago(last).toUpperCase() : "NOT PLAYED YET";
            title = cur.name();
            sub = (cur.vendor().isEmpty() ? "" : cur.vendor()) + (cur.version().isEmpty() ? "" : (cur.vendor().isEmpty() ? "" : "   \u00B7   ") + "v" + cur.version());
        } else if (games.isEmpty() && addTile) {     // nothing imported yet
            label = "WELCOME";
            title = emptyTitle.isEmpty() ? "Your library is empty" : emptyTitle;
            sub = emptyHint.isEmpty() ? "Add J2ME games (.jar / .jad), or drop files or a whole folder onto this window." : emptyHint;
        } else if (count() > 0) {                    // the "add games" tile is focused
            label = "LIBRARY";
            title = "Add games";
            sub = "Import .jar / .jad files, or a whole folder. You can also drop them onto this window.";
        } else {
            label = "";
            title = emptyTitle;
            sub = emptyHint;
        }
        // buttons
        int bx = pad;
        String primary = cur != null ? "Play" : (addTile ? "Add games" : "");
        if (!primary.isEmpty()) {
            Font bf = fnt(Font.BOLD, 1.15f);
            g.setFont(bf);
            int tw = g.getFontMetrics().stringWidth(primary);
            int bw = tw + Math.round(u * (cur != null ? 5.6f : 3.6f));
            playBtn = new Rectangle(bx, by, bw, btnH);
            pill(g, playBtn, Theme.ACCENT, pressedBtn.equals("play"));
            g.setColor(Gfx.PLAY_TEXT);
            FontMetrics fm = g.getFontMetrics();
            int tx = bx + (bw - tw) / 2 + (cur != null ? Math.round(u * 0.9f) : 0);
            g.drawString(primary, tx, by + (btnH + fm.getAscent() - fm.getDescent()) / 2f);
            if (cur != null) {                                   // play triangle
                int ts = Math.round(u * 1.0f), tx0 = bx + Math.round(u * 1.5f), ty = by + btnH / 2;
                Path2D tri = new Path2D.Double();
                tri.moveTo(tx0, ty - ts / 2.0); tri.lineTo(tx0 + ts * 0.85, ty); tri.lineTo(tx0, ty + ts / 2.0); tri.closePath();
                g.fill(tri);
            }
            bx += bw + u / 2;
        }
        if (cur != null) {
            Font bf = fnt(Font.BOLD, 1.0f);
            g.setFont(bf);
            FontMetrics fm = g.getFontMetrics();
            int bw = fm.stringWidth("Details") + Math.round(u * 3.2f);
            detailsBtn = new Rectangle(bx, by, bw, btnH);
            pill(g, detailsBtn, Gfx.alpha(Color.WHITE, 0.14), pressedBtn.equals("details"));
            g.setColor(Theme.TEXT);
            g.drawString("Details", bx + (bw - fm.stringWidth("Details")) / 2f, by + (btnH + fm.getAscent() - fm.getDescent()) / 2f);
            bx += bw + u / 2;
            favBtn = new Rectangle(bx, by, btnH, btnH);
            boolean fav = LibraryState.isFavorite(cur);
            pill(g, favBtn, Gfx.alpha(Color.WHITE, 0.14), pressedBtn.equals("fav"));
            star(g, favBtn.x + btnH / 2.0, favBtn.y + btnH / 2.0 + 1, btnH * 0.30, fav, fav ? Theme.ACCENT : Theme.TEXT);
        }
        // chips row above the buttons (dropped, then the label too, when the window is short)
        int avail = blockBottom - top;
        boolean showChips = avail >= u * 11.5f, showLabel = avail >= u * 14.6f;
        int cy = by - Math.round(cur != null ? u * 1.0f : u * 1.6f);
        if (cur != null && showChips) {
            int chipH = Math.round(u * 1.9f), chipY = cy - chipH, x = pad;
            ApiScanner.Report r = host.compat(cur);
            String ct; Color cc;
            if (r == null) { ct = "Checking compatibility\u2026"; cc = Theme.MUTED; }
            else if (r.status == ApiScanner.Status.OK) { ct = "Compatible"; cc = Theme.OK; }
            else if (r.status == ApiScanner.Status.PARTIAL) { ct = "May have problems"; cc = Theme.WARN; }
            else { ct = "Unsupported features"; cc = Theme.BAD; }
            x += chip(g, x, chipY, chipH, ct, cc, true) + u / 2;
            if (cur.midlets.size() > 1) x += chip(g, x, chipY, chipH, cur.midlets.size() + " programs", Theme.MUTED, false) + u / 2;
            int plays = LibraryState.plays(cur);
            if (plays > 0) chip(g, x, chipY, chipH, "Played " + plays + (plays == 1 ? " time" : " times"), Theme.MUTED, false);
            cy = chipY - Math.round(u * 0.9f);
        }
        // sub line, title, label (stacked upward)
        g.setFont(fnt(Font.PLAIN, 1.0f));
        FontMetrics sfm = g.getFontMetrics();
        List<String> subLines = Gfx.wrap(sfm, sub, maxTextW);
        if (subLines.size() > 2) subLines = new ArrayList<>(subLines.subList(0, 2));
        g.setColor(Theme.MUTED);
        int y = cy;
        for (int i = subLines.size() - 1; i >= 0; i--) { g.drawString(subLines.get(i), pad, y); y -= Math.round(u * 1.45f); }
        y -= Math.round(u * 0.35f);
        Font tf = fnt(Font.BOLD, 2.5f);
        g.setFont(tf);
        FontMetrics tfm = g.getFontMetrics();
        g.setColor(Theme.TEXT);
        g.drawString(Gfx.fit(tfm, title, maxTextW), pad, y);
        y -= tfm.getAscent() + Math.round(u * 0.5f);
        if (!label.isEmpty() && showLabel) {
            g.setFont(fnt(Font.BOLD, 0.78f));
            g.setColor(Theme.ACCENT);
            g.drawString(label, pad, y);
        }
    }

    private int chip(Graphics2D g, int x, int y, int h, String text, Color c, boolean dot) {
        Font f = fnt(Font.BOLD, 0.82f);
        g.setFont(f);
        FontMetrics fm = g.getFontMetrics();
        int u = u(), w = fm.stringWidth(text) + Math.round(u * (dot ? 3.2f : 1.9f));
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

    static void star(Graphics2D g, double cx, double cy, double r, boolean filled, Color c) {
        Path2D s = new Path2D.Double();
        for (int i = 0; i < 10; i++) {
            double a = -Math.PI / 2 + i * Math.PI / 5, rad = i % 2 == 0 ? r : r * 0.45;
            double px = cx + Math.cos(a) * rad, py = cy + Math.sin(a) * rad;
            if (i == 0) s.moveTo(px, py); else s.lineTo(px, py);
        }
        s.closePath();
        g.setColor(c);
        if (filled) g.fill(s); else { g.setStroke(new BasicStroke(Math.max(1.8f, (float) r / 6), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)); g.draw(s); }
    }

    private void paintShelf(Graphics2D g) {
        tileRects.clear();
        int n = count(), u = u(), pad = Math.round(u * 2f), stride = stride();
        if (n == 0) return;
        int[] order = new int[n];
        for (int i = 0; i < n; i++) order[i] = i;
        // far tiles first, the focused one last so its glow and scale sit on top
        List<Integer> idx = new ArrayList<>();
        for (int i : order) idx.add(i);
        idx.sort((a, b) -> Double.compare(focus(b), focus(a)));
        Rectangle[] rects = new Rectangle[n];
        for (int i = 0; i < n; i++) rects[i] = new Rectangle(pad + i * stride - (int) Math.round(sc.pos), tileTop, tileSize, tileSize);
        for (int i = 0; i < n; i++) tileRects.add(rects[i]);
        Shape oldClip = g.getClip();
        for (int i : idx) {
            Rectangle r = rects[i];
            if (r.x > getWidth() + tileSize || r.x + r.width < -tileSize) continue;
            paintTile(g, i, r, focus(i));
        }
        g.setClip(oldClip);
    }

    /** 1 when tile i sits at the focus position, falling to 0 one stride away. */
    private double focus(int i) {
        double d = Math.abs(i * stride() - sc.pos) / Math.max(1, stride());
        double t = Math.max(0, 1 - d);
        return t * t * (3 - 2 * t);
    }

    private void paintTile(Graphics2D g0, int i, Rectangle r, double f) {
        Graphics2D g = (Graphics2D) g0.create();
        int u = u();
        double scale = 1 + 0.10 * f, arc = u * 1.5;
        double cx = r.x + r.width / 2.0, cy = r.y + r.height / 2.0;
        if (i == pressedTile) scale *= 0.97;
        g.translate(cx, cy);
        g.scale(scale, scale);
        g.translate(-cx, -cy);
        boolean isAdd = i >= games.size();
        Gfx.shadow(g, r.x, r.y, r.width, r.height, arc, Math.round(u * (0.9f + 0.8f * (float) f)), 0.55 + 0.25 * f, Math.round(u * 0.5f));
        if (isAdd) {
            g.setColor(Gfx.alpha(Color.WHITE, 0.06 + 0.05 * f));
            g.fill(Gfx.rr(r.x, r.y, r.width, r.height, arc));
            g.setColor(Gfx.alpha(Color.WHITE, 0.35));
            g.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 1f, new float[]{u * 0.6f, u * 0.6f}, 0));
            g.draw(Gfx.rr(r.x + 2, r.y + 2, r.width - 4, r.height - 4, arc - 2));
            g.setStroke(new BasicStroke(Math.max(3f, u / 5f), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.setColor(Theme.TEXT);
            int a = Math.round(r.width * 0.16f);
            g.drawLine((int) cx - a, (int) cy, (int) cx + a, (int) cy);
            g.drawLine((int) cx, (int) cy - a, (int) cx, (int) cy + a);
        } else {
            GameInfo gi = games.get(i);
            Covers.Cover cv = Covers.of(gi);
            g.drawImage(cv.card(r.width, arc), r.x, r.y, null);
            // badges
            if (LibraryState.isFavorite(gi)) star(g, r.x + r.width - u * 1.35, r.y + u * 1.35, u * 0.8, true, Theme.ACCENT);
            ApiScanner.Report rep = host.compat(gi);
            if (rep != null) {
                int d = Math.round(u * 0.95f);
                Color c = rep.status == ApiScanner.Status.OK ? Theme.OK : rep.status == ApiScanner.Status.PARTIAL ? Theme.WARN : Theme.BAD;
                g.setColor(Gfx.alpha(Theme.BG, 0.85));
                g.fillOval(r.x + u / 2 - 2, r.y + r.height - d - u / 2 - 2, d + 4, d + 4);
                g.setColor(c);
                g.fillOval(r.x + u / 2, r.y + r.height - d - u / 2, d, d);
            }
        }
        if (focused && f > 0.01) {
            g.setColor(Gfx.alpha(Theme.ACCENT, 0.2 * f));
            g.setStroke(new BasicStroke(u * 0.55f));
            g.draw(Gfx.rr(r.x - 1, r.y - 1, r.width + 2, r.height + 2, arc + 2));
            g.setColor(Gfx.alpha(Theme.ACCENT, f));
            g.setStroke(new BasicStroke(Math.max(3f, u / 6.5f)));
            g.draw(Gfx.rr(r.x - 2, r.y - 2, r.width + 4, r.height + 4, arc + 4));
        }
        g.dispose();
        // caption (not scaled with the tile)
        g0.setFont(fnt(f > 0.5 ? Font.BOLD : Font.PLAIN, 0.9f));
        FontMetrics fm = g0.getFontMetrics();
        String cap = isAdd ? "Add games" : games.get(i).name();
        g0.setColor(Gfx.mix(Theme.MUTED, Theme.TEXT, f));
        g0.drawString(Gfx.fit(fm, cap, r.width + u / 2), r.x, r.y + r.height + Math.round(u * 1.7f) + (int) Math.round(r.height * 0.05 * f));
    }
}
