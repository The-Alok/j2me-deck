// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 J2ME Deck contributors
package javax.microedition.lcdui.game;

import javax.microedition.lcdui.Graphics;
import java.util.ArrayList;
import java.util.List;

public class LayerManager {
    private final List<Layer> layers = new ArrayList<>();
    private int vx, vy, vw = Integer.MAX_VALUE, vh = Integer.MAX_VALUE;

    public void append(Layer l) { if (l == null) throw new NullPointerException(); layers.remove(l); layers.add(l); }
    public void insert(Layer l, int i) {
        if (l == null) throw new NullPointerException();
        layers.remove(l);
        if (i < 0 || i > layers.size()) throw new IndexOutOfBoundsException();
        layers.add(i, l);
    }
    public Layer getLayerAt(int i) { return layers.get(i); }
    public int getSize() { return layers.size(); }
    public void remove(Layer l) { if (l == null) throw new NullPointerException(); layers.remove(l); }
    public void setViewWindow(int x, int y, int w, int h) {
        if (w < 0 || h < 0) throw new IllegalArgumentException();
        vx = x; vy = y; vw = w; vh = h;
    }
    public void paint(Graphics g, int x, int y) {
        int cx = g.getClipX(), cy = g.getClipY(), cw = g.getClipWidth(), ch = g.getClipHeight();
        g.translate(x - vx, y - vy);
        g.clipRect(vx, vy, vw, vh);
        for (int i = layers.size() - 1; i >= 0; i--) {
            Layer l = layers.get(i);
            if (l.isVisible()) l.paint(g);
        }
        g.translate(-(x - vx), -(y - vy));
        g.setClip(cx, cy, cw, ch);
    }
}
