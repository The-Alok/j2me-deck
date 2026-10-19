// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 J2ME Deck contributors
package javax.microedition.lcdui.game;

import javax.microedition.lcdui.Canvas;
import javax.microedition.lcdui.Graphics;
import javax.microedition.lcdui.Image;

public abstract class GameCanvas extends Canvas {
    public static final int UP_PRESSED = 1 << Canvas.UP, DOWN_PRESSED = 1 << Canvas.DOWN, LEFT_PRESSED = 1 << Canvas.LEFT,
            RIGHT_PRESSED = 1 << Canvas.RIGHT, FIRE_PRESSED = 1 << Canvas.FIRE, GAME_A_PRESSED = 1 << Canvas.GAME_A,
            GAME_B_PRESSED = 1 << Canvas.GAME_B, GAME_C_PRESSED = 1 << Canvas.GAME_C, GAME_D_PRESSED = 1 << Canvas.GAME_D;

    private final Image buffer;
    private int held, latched;   // MIDP: getKeyStates() latches presses that happened since the last call

    protected GameCanvas(boolean suppressKeyEvents) {
        super();
        $suppressKeys = suppressKeyEvents;
        buffer = Image.createImage(getWidth(), getHeight());
    }

    private int bit(int key) {
        int action = $action(key);
        return action == 0 ? 0 : 1 << action;      // UP_PRESSED = 1 << UP etc.
    }

    @Override protected void $trackKey(int code, boolean down) {
        int b = bit(code);
        if (b == 0) return;
        synchronized (this) { if (down) { held |= b; latched |= b; } else held &= ~b; }
    }

    public synchronized int getKeyStates() { int s = held | latched; latched = 0; return s; }

    public Graphics getGraphics() { return buffer.getGraphics(); }

    public void flushGraphics() { $blit(buffer, 0, 0, getWidth(), getHeight()); }
    public void flushGraphics(int x, int y, int w, int h) {
        x = Math.max(0, x); y = Math.max(0, y);
        w = Math.min(w, getWidth() - x); h = Math.min(h, getHeight() - y);
        if (w > 0 && h > 0) $blit(buffer, x, y, w, h);
    }

    @Override protected void paint(Graphics g) { g.drawImage(buffer, 0, 0, Graphics.TOP | Graphics.LEFT); }
}
