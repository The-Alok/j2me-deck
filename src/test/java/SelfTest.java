// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 J2ME Deck contributors
import j2medeck.*;
import javax.microedition.lcdui.*;
import javax.microedition.lcdui.game.*;
import java.awt.image.BufferedImage;

/** Headless sanity checks for the MIDP layer. Run with tools/selftest.sh. */
public class SelfTest {
    static int fails;
    static void check(boolean ok, String what) { System.out.println((ok ? "PASS  " : "FAIL  ") + what); if (!ok) fails++; }

    static Image asym() {   // 3x2 image, every pixel unique
        int[] px = {0xFFFF0000, 0xFF00FF00, 0xFF0000FF, 0xFFFFFF00, 0xFF00FFFF, 0xFFFF00FF};
        return Image.createRGBImage(px, 3, 2, true);
    }
    static int[] pix(Image i) { int[] a = new int[i.getWidth() * i.getHeight()]; i.getRGB(a, 0, i.getWidth(), 0, 0, i.getWidth(), i.getHeight()); return a; }

    public static void main(String[] a) throws Exception {
        // 1. region transforms: drawRegion must equal createImage(...,transform) for every transform and anchor
        Image src = asym();
        for (int t = 0; t < 8; t++) {
            Image ref = Image.createImage(src, 0, 0, 3, 2, t);
            Image dst = Image.createImage(10, 10);
            Graphics g = dst.getGraphics();
            g.setColor(0x000000); g.fillRect(0, 0, 10, 10);
            g.drawRegion(src, 0, 0, 3, 2, t, 2, 3, Graphics.TOP | Graphics.LEFT);
            int[] d = new int[ref.getWidth() * ref.getHeight()], r = pix(ref);
            dst.getRGB(d, 0, ref.getWidth(), 2, 3, ref.getWidth(), ref.getHeight());
            check(java.util.Arrays.equals(d, r), "drawRegion == createImage for transform " + t);
        }
        // known ground truth for ROT90 (clockwise): top-left source pixel ends top-right
        Image r90 = Image.createImage(src, 0, 0, 3, 2, Image.TRANS_ROT90);
        check(r90.getWidth() == 2 && r90.getHeight() == 3, "ROT90 swaps dimensions");
        check(pix(r90)[1] == 0xFFFF0000, "ROT90: src(0,0) lands at top-right");
        Image mir = Image.createImage(src, 0, 0, 3, 2, Image.TRANS_MIRROR);
        check(pix(mir)[0] == 0xFF0000FF, "MIRROR: src(2,0) lands at left");

        // 2. translate + setClip must not double-apply the offset
        Image t = Image.createImage(20, 20);
        Graphics g = t.getGraphics();
        g.setColor(0xFFFFFF); g.fillRect(0, 0, 20, 20);
        g.translate(5, 5);
        g.setClip(0, 0, 4, 4);
        g.setColor(0xFF0000); g.fillRect(-10, -10, 100, 100);
        int[] p = pix(t);
        check(p[5 * 20 + 5] == 0xFFFF0000 && p[8 * 20 + 8] == 0xFFFF0000, "translate+clip: clipped square covers (5..8)");
        check(p[4 * 20 + 4] == 0xFFFFFFFF && p[9 * 20 + 9] == 0xFFFFFFFF, "translate+clip: nothing outside clip");
        check(g.getClipX() == 0 && g.getClipWidth() == 4 && g.getTranslateX() == 5, "clip/translate getters in translated space");

        // 3. mutable images start white; text anchors put the glyphs where the spec says
        check(pix(Image.createImage(2, 2))[0] == 0xFFFFFFFF, "new mutable image is opaque white");
        Image ti = Image.createImage(120, 60); Graphics tg = ti.getGraphics();
        tg.setColor(0); tg.drawString("Hg", 10, 10, Graphics.TOP | Graphics.LEFT);
        int[] tp = pix(ti); int minY = 99, maxY = -1;
        for (int y = 0; y < 60; y++) for (int x = 0; x < 120; x++) if ((tp[y * 120 + x] & 0xFFFFFF) != 0xFFFFFF) { minY = Math.min(minY, y); maxY = Math.max(maxY, y); }
        check(minY >= 10 && maxY <= 10 + Font.getDefaultFont().getHeight(), "drawString TOP anchor draws below y (" + minY + ".." + maxY + ")");

        // 4. Sprite: frames, transforms keep the reference pixel fixed, collisions
        Image sheet = Image.createImage(20, 10); sheet.getGraphics().setColor(0xFF0000);
        Graphics sg = sheet.getGraphics(); sg.setColor(0xFF0000); sg.fillRect(0, 0, 20, 10);
        Sprite s1 = new Sprite(sheet, 10, 10), s2 = new Sprite(sheet, 10, 10);
        check(s1.getRawFrameCount() == 2, "sprite frame count");
        s1.defineReferencePixel(0, 0); s1.setRefPixelPosition(50, 50); s1.setTransform(Sprite.TRANS_ROT90);
        check(s1.getRefPixelX() == 50 && s1.getRefPixelY() == 50, "setTransform keeps ref pixel in place");
        s1.setPosition(0, 0); s2.setPosition(5, 5);
        check(s1.collidesWith(s2, false), "overlapping sprites collide");
        s2.setPosition(40, 40);
        check(!s1.collidesWith(s2, false), "distant sprites don't collide");

        // 5. GameCanvas key state latching + LayerManager paint
        GameCanvas gc = new GameCanvas(false) {};
        gc.$dispatchKey(0, Canvas.KEY_UP); gc.$dispatchKey(1, Canvas.KEY_UP);
        check((gc.getKeyStates() & GameCanvas.UP_PRESSED) != 0, "getKeyStates latches a press that already ended");
        check((gc.getKeyStates() & GameCanvas.UP_PRESSED) == 0, "...and clears the latch afterwards");
        gc.$dispatchKey(0, Canvas.KEY_NUM4);
        check((gc.getKeyStates() & GameCanvas.LEFT_PRESSED) != 0, "digit 4 maps to LEFT in getKeyStates");
        check(gc.getGameAction(Canvas.KEY_NUM2) == Canvas.UP && gc.getGameAction(Canvas.KEY_NUM1) == Canvas.GAME_A, "getGameAction digit mapping");

        // 6. ScreenView scaling in all three modes, rendered offscreen at Steam Deck resolution
        Canvas cv = new Canvas() { protected void paint(Graphics gg) {
            gg.setColor(0x202040); gg.fillRect(0, 0, 240, 320); gg.setColor(0xFFC000); gg.fillRect(0, 0, 240, 10);
            gg.setColor(0xFFFFFF); gg.drawRect(0, 0, 239, 319); gg.drawString("J2ME", 100, 150, Graphics.TOP | Graphics.LEFT); } };
        cv.$becameCurrentForTest();
        for (int m = 0; m < 3; m++) {
            Settings.scaleMode = m;
            ScreenView v = new ScreenView(cv);
            v.setSize(1280, 800);
            BufferedImage out = new BufferedImage(1280, 800, BufferedImage.TYPE_INT_RGB);
            v.paint(out.createGraphics());
            int mid = out.getRGB(640, 400), edge = out.getRGB(10, 400);
            check(edge == 0xFF000000 && mid != 0xFF000000, "ScreenView mode " + Settings.SCALE_NAMES[m] + ": letterboxed, content centred");
            javax.imageio.ImageIO.write(out, "png", new java.io.File("/tmp/scale_" + m + ".png"));
        }
        System.out.println(fails == 0 ? "ALL PASSED" : fails + " FAILED");
        System.exit(fails == 0 ? 0 : 1);
    }
}
