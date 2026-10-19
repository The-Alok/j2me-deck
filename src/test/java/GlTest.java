// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 J2ME Deck contributors
import j2medeck.GlBackend;
import j2medeck.GlHost;
import j2medeck.GlOverlay;
import j2medeck.Settings;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * OpenGL ES output checks. Needs a display (xvfb-run) and Mesa EGL/GLES2; run through tools/gltest.sh, which also passes the JVM flags.
 * Exercises the real EGL surface: pixel-exact output per scale mode, LCD grid, overlays, window resize and surface re-creation.
 */
public class GlTest {
    static int pass, fail;
    static void check(String name, boolean ok) { if (ok) pass++; else fail++; System.out.println((ok ? "  ok   " : "  FAIL ") + name); }

    /** 4x2 frame: row 0 red green blue white, row 1 black yellow cyan magenta. */
    static final int[] PIX = {0xFF0000, 0x00FF00, 0x0000FF, 0xFFFFFF, 0x000000, 0xFFFF00, 0x00FFFF, 0xFF00FF};

    static volatile String failed;
    static volatile List<GlOverlay> extra = new java.util.ArrayList<>();

    static class Host implements GlHost {
        public int frameWidth() { return 4; }
        public int frameHeight() { return 2; }
        public void copyFrame(int[] dst) { System.arraycopy(PIX, 0, dst, 0, PIX.length); }
        public void overlays(int w, int h, List<GlOverlay> out) { out.addAll(extra); }
        public void glFailed(String reason) { failed = reason; System.out.println("  glFailed: " + reason); }
    }

    static int near(int a, int b) { int d = 0; for (int s = 0; s <= 16; s += 8) d = Math.max(d, Math.abs(((a >> s) & 255) - ((b >> s) & 255))); return d; }
    static boolean is(int actual, int expect, int tol) { return near(actual & 0xFFFFFF, expect) <= tol; }

    public static void main(String[] a) throws Exception {
        if (GraphicsEnvironment.isHeadless()) { System.out.println("GlTest needs a display (use tools/gltest.sh)"); System.exit(2); }
        Settings.renderer = "gles";
        Settings.scaleMode = Settings.SCALE_NEAREST;
        Settings.lcdLevel = 0;
        Host host = new Host();
        Canvas c = GlBackend.create(host);
        check("GlBackend.create returns a canvas (reason if not: " + GlBackend.unavailableReason() + ")", c != null);
        if (c == null) { System.out.println("\n" + pass + " passed, " + fail + " failed"); System.exit(1); }
        j2medeck.gl.GlSurface gl = (j2medeck.gl.GlSurface) c;
        AtomicReference<JFrame> fr = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            JFrame f = new JFrame("gl-test");
            f.getContentPane().setLayout(new BorderLayout());
            f.getContentPane().add(c, BorderLayout.CENTER);
            f.setSize(640, 400);
            f.setLocation(0, 0);
            f.setVisible(true);
            fr.set(f);
        });
        Thread.sleep(500);

        int[] px = gl.captureFrame(5000);
        check("first frame captured; renderer: " + gl.rendererInfo(), px != null);
        if (px == null) { System.out.println("\n" + pass + " passed, " + fail + " failed"); System.exit(1); }
        int w = gl.capturedWidth(), h = gl.capturedHeight();
        System.out.println("  surface " + w + "x" + h);

        // nearest: 4x2 into w x h keeps 2:1 aspect -> scale = min(w/4, h/2)
        double s = Math.min(w / 4.0, h / 2.0);
        int rw = (int) Math.round(4 * s), rh = (int) Math.round(2 * s), ox = (w - rw) / 2, oy = (h - rh) / 2;
        boolean allOk = true;
        for (int i = 0; i < 8; i++) {
            int x = ox + (int) ((i % 4 + 0.5) * rw / 4), y = oy + (int) ((i / 4 + 0.5) * rh / 2);
            boolean ok = is(px[y * w + x], PIX[i], 2);
            if (!ok) System.out.printf("    texel %d at (%d,%d): got %06X want %06X%n", i, x, y, px[y * w + x] & 0xFFFFFF, PIX[i]);
            allOk &= ok;
        }
        check("nearest: every source pixel appears with the right colour and orientation (top row first)", allOk);
        check("letterbox bars are black", is(px[2 * w + 2], 0, 0) || oy == 0);
        check("top-left of picture is red (not flipped)", is(px[(oy + 2) * w + ox + 2], 0xFF0000, 2));

        // sharp-bilinear needs a non-integer scale (at exact multiples it is plain nearest): resize to 830x470 -> 207.5x
        SwingUtilities.invokeAndWait(() -> fr.get().setSize(830, 470));
        Thread.sleep(400);
        Settings.scaleMode = Settings.SCALE_SHARP;
        px = gl.captureFrame(5000);
        w = gl.capturedWidth(); h = gl.capturedHeight();
        s = Math.min(w / 4.0, h / 2.0);
        rw = (int) Math.round(4 * s); rh = (int) Math.round(2 * s); ox = (w - rw) / 2; oy = (h - rh) / 2;
        int row = oy + (int) (s * 0.5);
        check("sharp-bilinear keeps texel centres pure (scale " + s + ")", is(px[row * w + ox + (int) (s * 0.5)], 0xFF0000, 3));
        int edgeX = ox + (int) Math.round(s);          // boundary between texel 0 (red) and 1 (green)
        int minR = 255, maxMid = 0, blended = 0;
        for (int x = edgeX - 6; x <= edgeX + 6; x++) {
            int v = px[row * w + x] & 0xFFFFFF, rr = (v >> 16) & 255, gg = (v >> 8) & 255;
            if (rr > 20 && gg > 20) blended++;
        }
        check("sharp-bilinear blends only a narrow band at the texel edge (" + blended + " blended pixels of 13)", blended >= 1 && blended <= 4);
        int away = px[row * w + edgeX - (int) (s * 0.3)] & 0xFFFFFF;
        check("...and a few pixels away it is pure again", is(away, 0xFF0000, 3));
        SwingUtilities.invokeAndWait(() -> fr.get().setSize(640, 400));
        Thread.sleep(400);
        px = gl.captureFrame(5000);
        w = gl.capturedWidth(); h = gl.capturedHeight();
        s = Math.min(w / 4.0, h / 2.0);
        rw = (int) Math.round(4 * s); rh = (int) Math.round(2 * s); ox = (w - rw) / 2; oy = (h - rh) / 2;

        // integer scaling: picture is an exact multiple
        Settings.scaleMode = Settings.SCALE_INTEGER;
        px = gl.captureFrame(5000);
        int k = (int) Math.max(1, Math.floor(s));
        int ix = (w - 4 * k) / 2, iy = (h - 2 * k) / 2;
        check("integer: picture is exactly " + k + "x and centred",
                is(px[(iy + 1) * w + ix + 1], 0xFF0000, 2) && is(px[(iy + k - 1) * w + ix + k - 1], 0xFF0000, 2)
                        && is(px[(iy + 1) * w + ix + k + 1], 0x00FF00, 2) && near(px[(iy - 1) * w + ix + 1] & 0xFFFFFF, 0) == 0);

        // stretch fills the whole surface
        Settings.scaleMode = Settings.SCALE_STRETCH;
        px = gl.captureFrame(5000);
        check("stretch: corners show the corner pixels", is(px[1 * w + 1], 0xFF0000, 6) && is(px[(h - 2) * w + w - 2], 0xFF00FF, 6));

        // LCD grid darkens lines on source-pixel boundaries
        Settings.scaleMode = Settings.SCALE_NEAREST;
        Settings.lcdLevel = 3;
        px = gl.captureFrame(5000);
        int lineX = ox + (int) Math.round(s);        // boundary after texel 0, inside the white texel? use texel 2 (blue)
        int bx = ox + (int) Math.round(2 * s);       // start of texel 2 (blue): grid line at its left edge
        int yMid = oy + (int) (s * 0.5);
        int onLine = px[yMid * w + bx] & 0xFF, offLine = px[yMid * w + bx + (int) (s * 0.5)] & 0xFF;
        check("LCD grid: boundary pixel darker than texel interior (" + onLine + " < " + offLine + ")", onLine < offLine - 40);
        Settings.lcdLevel = 0;

        // overlay: a 10x6 opaque green box at (20,30) and a half-transparent one
        BufferedImage ov = new BufferedImage(10, 6, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = ov.createGraphics(); g.setColor(new Color(0, 255, 0, 255)); g.fillRect(0, 0, 10, 6); g.dispose();
        extra = new java.util.ArrayList<>(List.of(new GlOverlay("box", ov, 20, 30)));
        px = gl.captureFrame(5000);
        check("overlay: opaque box drawn at (20,30)", is(px[32 * w + 24], 0x00FF00, 2) && !is(px[28 * w + 24], 0x00FF00, 40));
        BufferedImage ov2 = new BufferedImage(10, 6, BufferedImage.TYPE_INT_ARGB);
        g = ov2.createGraphics(); g.setColor(new Color(255, 255, 255, 128)); g.fillRect(0, 0, 10, 6); g.dispose();
        extra = new java.util.ArrayList<>(List.of(new GlOverlay("half", ov2, 20, 30)));
        px = gl.captureFrame(5000);
        int hv = px[32 * w + 24] & 0xFF;      // black bar behind -> ~128 after 50% white
        check("overlay: alpha blends (value " + hv + ")", hv > 100 && hv < 160);
        extra = new java.util.ArrayList<>();

        // resize
        SwingUtilities.invokeAndWait(() -> fr.get().setSize(800, 500));
        Thread.sleep(400);
        px = gl.captureFrame(5000);
        check("resize: surface follows the window (" + gl.capturedWidth() + "x" + gl.capturedHeight() + ")", gl.capturedWidth() > 700 && gl.capturedHeight() > 400);

        // surface re-creation: remove from the window and add again (what compose() does when a Form is shown), then dispose/show (fullscreen toggle)
        SwingUtilities.invokeAndWait(() -> { fr.get().getContentPane().remove(c); fr.get().revalidate(); });
        Thread.sleep(300);
        SwingUtilities.invokeAndWait(() -> { fr.get().getContentPane().add(c, BorderLayout.CENTER); fr.get().revalidate(); });
        Thread.sleep(500);
        Settings.scaleMode = Settings.SCALE_NEAREST;
        px = gl.captureFrame(5000);
        check("re-add: renders again after the component was removed and re-added", px != null && is(px[(gl.capturedHeight() / 2) * gl.capturedWidth() + gl.capturedWidth() / 2], PIX[1], 255) );
        SwingUtilities.invokeAndWait(() -> { fr.get().dispose(); fr.get().setUndecorated(true); fr.get().setVisible(true); });
        Thread.sleep(600);
        px = gl.captureFrame(5000);
        check("dispose/show (fullscreen toggle path): still renders", px != null);
        check("no GL failure was reported", failed == null);

        // many frames quickly: no crash, thread survives
        for (int i = 0; i < 200; i++) gl.requestFrame();
        check("200 queued frame requests coalesce and are presented", gl.awaitPresented(5000));

        gl.shutdown();
        SwingUtilities.invokeAndWait(() -> fr.get().dispose());
        System.out.println("\n" + pass + " passed, " + fail + " failed");
        System.exit(fail == 0 ? 0 : 1);
    }
}
