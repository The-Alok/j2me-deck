// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 J2ME Deck contributors
package j2medeck.ui;

import j2medeck.GamepadReader;

import javax.swing.*;
import java.awt.*;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;
import java.util.stream.Stream;

/** Console-style header: logo, tabs (LB / RB switch them), controller indicator, battery and clock. */
final class TopBar extends JComponent {
    private final String[] tabs;
    private int sel;
    private boolean focused;
    private final List<Rectangle> hit = new ArrayList<>();
    private IntConsumer onSelect = i -> {};
    private int battery = -1;
    private boolean charging;
    private final Timer tick = new Timer(20_000, e -> { refreshStatus(); repaint(); });

    TopBar(String... tabs) {
        this.tabs = tabs;
        setOpaque(false);
        Gesture.attach(this, new Gesture.Listener() {
            @Override public void tap(Point p) {
                for (int i = 0; i < hit.size(); i++) if (hit.get(i).contains(p)) { select(i, true); return; }
            }
        });
        refreshStatus();
    }

    static int heightFor(int u) { return Math.round(u * 3.7f); }
    @Override public Dimension getPreferredSize() { return new Dimension(100, heightFor(Theme.base())); }

    void setOnSelect(IntConsumer c) { onSelect = c; }
    int selected() { return sel; }
    void select(int i, boolean notify) {
        i = Math.max(0, Math.min(tabs.length - 1, i));
        boolean changed = i != sel;
        sel = i;
        repaint();
        if (changed && notify) onSelect.accept(i);
    }
    void setFocused(boolean f) { focused = f; repaint(); }
    boolean isFocused() { return focused; }

    @Override public void addNotify() { super.addNotify(); tick.start(); }
    @Override public void removeNotify() { tick.stop(); super.removeNotify(); }

    private void refreshStatus() {
        battery = -1;
        try (Stream<Path> s = Files.list(Path.of("/sys/class/power_supply"))) {
            Path bat = s.filter(p -> p.getFileName().toString().startsWith("BAT")).sorted().findFirst().orElse(null);
            if (bat != null) {
                battery = Integer.parseInt(Files.readString(bat.resolve("capacity")).trim());
                charging = Files.readString(bat.resolve("status")).trim().equalsIgnoreCase("Charging");
            }
        } catch (IOException | RuntimeException ignored) {}
    }

    @Override protected void paintComponent(Graphics g0) {
        Graphics2D g = Gfx.aa(g0);
        int h = getHeight(), w = getWidth();
        int u = Math.max(10, Math.round(h / 3.7f)), pad = Math.round(u * 1.6f);
        // logo (the wordmark is dropped on narrow windows)
        int badge = Math.round(u * 2.0f), x = pad, cy = h / 2;
        g.setColor(Theme.ACCENT);
        g.fill(Gfx.rr(x, cy - badge / 2.0, badge, badge, badge * 0.34));
        g.setFont(Theme.font(Font.BOLD, 1.15f).deriveFont(Font.BOLD, u * 1.15f));
        g.setColor(Gfx.PLAY_TEXT);
        FontMetrics fm = g.getFontMetrics();
        g.drawString("J", x + (badge - fm.stringWidth("J")) / 2f, cy + (fm.getAscent() - fm.getDescent()) / 2f);
        x += badge + u / 2;
        if (w >= u * 52) {
            g.setFont(Theme.font(Font.BOLD, 1.0f).deriveFont(Font.BOLD, u * 1.0f));
            g.setColor(Theme.TEXT);
            fm = g.getFontMetrics();
            g.drawString("J2ME", x, cy + (fm.getAscent() - fm.getDescent()) / 2f);
            x += fm.stringWidth("J2ME");
        }
        x += Math.round(u * 2.0f);
        // tabs
        hit.clear();
        g.setFont(Theme.font(Font.BOLD, 1.0f).deriveFont(Font.BOLD, u * 1.0f));
        fm = g.getFontMetrics();
        int th = Math.round(u * 2.5f);
        for (int i = 0; i < tabs.length; i++) {
            int tw = fm.stringWidth(tabs[i]) + Math.round(u * 2.4f);
            Rectangle r = new Rectangle(x, cy - th / 2, tw, th);
            hit.add(new Rectangle(r.x - u / 4, 0, tw + u / 2, h));      // generous touch area above and below the pill
            if (i == sel) {
                g.setColor(Gfx.alpha(Color.WHITE, focused ? 0.2 : 0.12));
                g.fill(Gfx.rr(r.x, r.y, r.width, r.height, th));
                if (focused) { g.setColor(Theme.ACCENT); g.setStroke(new BasicStroke(Math.max(2f, u / 9f))); g.draw(Gfx.rr(r.x + 1, r.y + 1, r.width - 2, r.height - 2, th)); }
            }
            g.setColor(i == sel ? Theme.TEXT : Theme.MUTED);
            g.drawString(tabs[i], r.x + (r.width - fm.stringWidth(tabs[i])) / 2f, cy + (fm.getAscent() - fm.getDescent()) / 2f);
            x += tw + u / 3;
        }
        int tabsEnd = x;
        // right side, right to left: clock, battery, controller; anything that would run into the tabs is left out
        g.setFont(Theme.font(Font.PLAIN, 0.95f).deriveFont(Font.PLAIN, u * 0.95f));
        fm = g.getFontMetrics();
        int rx = w - pad, base = cy + (fm.getAscent() - fm.getDescent()) / 2;
        String clock = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"));
        int cw0 = fm.stringWidth(clock);
        if (rx - cw0 > tabsEnd + u) {
            rx -= cw0;
            g.setColor(Theme.TEXT);
            g.drawString(clock, rx, base);
            rx -= u;
        }
        if (battery >= 0) {
            String pct = battery + "%";
            int bw = Math.round(u * 1.5f), bh = Math.round(u * 0.8f), need = fm.stringWidth(pct) + bw + u / 3 + u;
            if (rx - need > tabsEnd) {
                rx -= fm.stringWidth(pct);
                g.setColor(battery <= 15 && !charging ? Theme.BAD : Theme.MUTED);
                g.drawString(pct, rx, base);
                rx -= bw + u / 3;
                g.setStroke(new BasicStroke(1.6f));
                g.draw(Gfx.rr(rx, cy - bh / 2.0, bw, bh, bh * 0.3));
                g.fillRect(rx + bw + 1, cy - bh / 6, 2, bh / 3);
                g.setColor(charging ? Theme.OK : g.getColor());
                g.fill(Gfx.rr(rx + 2.5, cy - bh / 2.0 + 2.5, Math.max(1, (bw - 5) * battery / 100.0), bh - 5, 2));
                rx -= u;
            }
        }
        int cw = Math.round(u * 1.9f), ch = Math.round(u * 1.1f);
        if (rx - cw > tabsEnd) {
            rx -= cw;
            boolean pad2 = GamepadReader.connected();
            g.setColor(pad2 ? Theme.OK : Gfx.alpha(Theme.MUTED, 0.5));
            g.fill(Gfx.rr(rx, cy - ch / 2.0, cw, ch, ch * 0.9));
            g.setColor(Theme.BG);
            int d = Math.max(3, ch / 4);
            g.fillOval(rx + cw / 5, cy - d / 2, d, d);
            g.fillOval(rx + cw - cw / 5 - d, cy - d / 2, d, d);
        }
        g.dispose();
    }
}
