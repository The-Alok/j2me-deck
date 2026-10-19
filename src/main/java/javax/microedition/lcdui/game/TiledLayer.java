// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 J2ME Deck contributors
package javax.microedition.lcdui.game;

import javax.microedition.lcdui.Graphics;
import javax.microedition.lcdui.Image;

public class TiledLayer extends Layer {
    private final int cols, rows;
    private final int[][] cells;
    private int[] anim = new int[4];
    private int animCount;
    private Image tiles;
    private int tw, th, numTiles;
    private int[] tilePixels;

    public TiledLayer(int cols, int rows, Image image, int tileW, int tileH) {
        super(cols * tileW, rows * tileH);
        if (cols < 1 || rows < 1 || tileW < 1 || tileH < 1) throw new IllegalArgumentException();
        if (image == null) throw new NullPointerException();
        this.cols = cols; this.rows = rows;
        cells = new int[rows][cols];
        setStaticTileSet(image, tileW, tileH);
    }

    public void setStaticTileSet(Image image, int tileW, int tileH) {
        if (image == null) throw new NullPointerException();
        if (tileW < 1 || tileH < 1 || image.getWidth() % tileW != 0 || image.getHeight() % tileH != 0)
            throw new IllegalArgumentException();
        int n = (image.getWidth() / tileW) * (image.getHeight() / tileH);
        if (tiles == null || n < numTiles) { for (int[] r : cells) java.util.Arrays.fill(r, 0); anim = new int[4]; animCount = 0; }
        tiles = image; tw = tileW; th = tileH; numTiles = n;
        width = cols * tw; height = rows * th;
        tilePixels = null;
    }

    public int createAnimatedTile(int staticTileIndex) {
        if (staticTileIndex < 0 || staticTileIndex > numTiles) throw new IndexOutOfBoundsException();
        if (animCount + 1 >= anim.length) anim = java.util.Arrays.copyOf(anim, anim.length * 2);
        animCount++;
        anim[animCount] = staticTileIndex;
        return -animCount;
    }
    public void setAnimatedTile(int animIndex, int staticTileIndex) {
        if (staticTileIndex < 0 || staticTileIndex > numTiles) throw new IndexOutOfBoundsException();
        int i = -animIndex;
        if (i < 1 || i > animCount) throw new IndexOutOfBoundsException();
        anim[i] = staticTileIndex;
    }
    public int getAnimatedTile(int animIndex) {
        int i = -animIndex;
        if (i < 1 || i > animCount) throw new IndexOutOfBoundsException();
        return anim[i];
    }

    public void setCell(int col, int row, int tile) {
        if (col < 0 || col >= cols || row < 0 || row >= rows) throw new IndexOutOfBoundsException();
        if (tile > numTiles || tile < -animCount) throw new IndexOutOfBoundsException();
        cells[row][col] = tile;
    }
    public int getCell(int col, int row) {
        if (col < 0 || col >= cols || row < 0 || row >= rows) throw new IndexOutOfBoundsException();
        return cells[row][col];
    }
    public void fillCells(int col, int row, int nc, int nr, int tile) {
        if (nc < 0 || nr < 0) throw new IllegalArgumentException();
        if (col < 0 || row < 0 || col + nc > cols || row + nr > rows) throw new IndexOutOfBoundsException();
        if (tile > numTiles || tile < -animCount) throw new IndexOutOfBoundsException();
        for (int r = row; r < row + nr; r++) for (int c = col; c < col + nc; c++) cells[r][c] = tile;
    }
    public final int getCellWidth() { return tw; }
    public final int getCellHeight() { return th; }
    public final int getColumns() { return cols; }
    public final int getRows() { return rows; }

    int resolve(int col, int row) {
        int t = cells[row][col];
        return t < 0 ? anim[-t] : t;
    }

    public final void paint(Graphics g) {
        if (!visible) return;
        int cx = g.getClipX(), cy = g.getClipY(), cw = g.getClipWidth(), ch = g.getClipHeight();
        int c0 = Math.max(0, (cx - x) / tw), c1 = Math.min(cols - 1, (cx + cw - 1 - x) / tw);
        int r0 = Math.max(0, (cy - y) / th), r1 = Math.min(rows - 1, (cy + ch - 1 - y) / th);
        if (cx - x < 0) c0 = 0;
        if (cy - y < 0) r0 = 0;
        int perRow = tiles.getWidth() / tw;
        for (int r = r0; r <= r1; r++) {
            for (int c = c0; c <= c1; c++) {
                int t = resolve(c, r);
                if (t == 0) continue;
                t--;
                g.drawRegion(tiles, (t % perRow) * tw, (t / perRow) * th, tw, th, Image.TRANS_NONE,
                        x + c * tw, y + r * th, Graphics.TOP | Graphics.LEFT);
            }
        }
    }

    // ---- pixel access for Sprite collision ----
    boolean tilePixelOpaque(int tile, int px, int py) {
        if (tilePixels == null) {
            tilePixels = new int[tiles.getWidth() * tiles.getHeight()];
            tiles.getRGB(tilePixels, 0, tiles.getWidth(), 0, 0, tiles.getWidth(), tiles.getHeight());
        }
        int t = tile - 1, perRow = tiles.getWidth() / tw;
        int sx = (t % perRow) * tw + px, sy = (t / perRow) * th + py;
        return (tilePixels[sy * tiles.getWidth() + sx] >>> 24) != 0;
    }
}
