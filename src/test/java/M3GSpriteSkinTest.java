// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 J2ME Deck contributors
import javax.microedition.lcdui.*;
import javax.microedition.m3g.*;

/** Regression tests for bugs found with a real game (Medal of Honor Airborne): sprite blending/size/crop and skeleton children. */
public class M3GSpriteSkinTest {
    static int fails;
    static void check(boolean ok, String name) { System.out.println((ok ? "ok    " : "FAIL  ") + name); if (!ok) fails++; }

    static Image canvas(int bg) { Image im = Image.createImage(64, 64); Graphics g = im.getGraphics(); g.setColor(bg); g.fillRect(0, 0, 64, 64); return im; }
    static int px(Image im, int x, int y) { int[] p = new int[1]; im.getRGB(p, 0, 1, x, y, 1, 1); return p[0] & 0xFFFFFF; }

    static Image drawSprite(Image2D tex, int blending, float scale, int cx, int cy, int cw, int ch) {
        Image im = canvas(0x808080); Graphics g = im.getGraphics();
        Appearance ap = new Appearance(); CompositingMode cm = new CompositingMode();
        cm.setDepthTestEnable(false); cm.setDepthWriteEnable(false); cm.setBlending(blending); ap.setCompositingMode(cm);
        Sprite3D sp = new Sprite3D(true, tex, ap); sp.setScale(scale, scale, 1); if (cw != 0) sp.setCrop(cx, cy, cw, ch);
        World w = new World(); Camera cam = new Camera(); cam.setParallel(64, 1, -1, 1); w.addChild(cam); w.setActiveCamera(cam); w.addChild(sp);
        Background bg = new Background(); bg.setColorClearEnable(false); w.setBackground(bg);
        Graphics3D g3 = Graphics3D.getInstance(); g3.bindTarget(g); g3.setViewport(0, 0, 64, 64); g3.render(w); g3.releaseTarget();
        return im;
    }
    static Image2D solid(int r, int g, int b) { Image2D t = new Image2D(Image2D.RGB, 1, 1); t.set(0, 0, 1, 1, new byte[]{(byte) r, (byte) g, (byte) b}); return t; }

    public static void main(String[] a) {
        // full-screen MODULATE tint: dst * src, not an opaque fill
        Image im = drawSprite(solid(128, 128, 128), CompositingMode.MODULATE, 100, 0, 0, 0, 0);
        int p = px(im, 32, 32); check(Math.abs(((p >> 16) & 255) - 64) <= 2, "MODULATE halves the backdrop (got " + Integer.toHexString(p) + ")");
        im = drawSprite(solid(255, 255, 255), CompositingMode.MODULATE, 100, 0, 0, 0, 0); check(px(im, 5, 5) == 0x808080, "MODULATE with white leaves the backdrop unchanged");
        im = drawSprite(solid(64, 0, 0), CompositingMode.ALPHA_ADD, 100, 0, 0, 0, 0); p = px(im, 32, 32);
        check(((p >> 16) & 255) >= 0xBF && ((p >> 16) & 255) <= 0xC1 && (p & 255) == 0x80, "ALPHA_ADD adds to the backdrop (got " + Integer.toHexString(p) + ")");
        // scaled sprite size comes from the scale, not from the crop size: scale 16 on a 64-unit view = a 16 px square
        Image2D big = new Image2D(Image2D.RGB, 64, 64); byte[] white = new byte[64 * 64 * 3]; java.util.Arrays.fill(white, (byte) 255); big.set(0, 0, 64, 64, white);
        im = drawSprite(big, CompositingMode.REPLACE, 16, 0, 0, 32, 32);
        check(px(im, 32, 32) == 0xFFFFFF && px(im, 32 + 7, 32) == 0xFFFFFF && px(im, 32 + 10, 32) == 0x808080 && px(im, 32, 32 + 10) == 0x808080, "scaled sprite covers 16x16 px for scale 16 with a 32x32 crop");
        // negative crop height mirrors but still starts at cropY and extends downward
        Image2D two = new Image2D(Image2D.RGB, 1, 8); byte[] d = new byte[8 * 3]; for (int i = 0; i < 8; i++) { int v = i < 4 ? 255 : 0; d[i * 3] = d[i * 3 + 1] = d[i * 3 + 2] = (byte) v; } two.set(0, 0, 1, 8, d);
        im = drawSprite(two, CompositingMode.REPLACE, 16, 0, 4, 1, -4); // rows 4..7 (all black), mirrored
        check(px(im, 32, 32) == 0x000000, "negative crop height reads rows cropY..cropY+|h| (black rows), not cropY-|h|..cropY");
        // renderable node under a SkinnedMesh skeleton is drawn with the mesh
        VertexBuffer vb = new VertexBuffer(); VertexArray pos = new VertexArray(3, 3, 2); pos.set(0, 3, new short[]{-8, -8, 0, 8, -8, 0, 0, 8, 0}); vb.setPositions(pos, 1f, null);
        IndexBuffer ib = new TriangleStripArray(0, new int[]{3}); Appearance ap = new Appearance();
        Group skeleton = new Group(); Group bone = new Group(); skeleton.addChild(bone);
        Mesh gun = new Mesh(vb, ib, ap); bone.addChild(gun);
        VertexBuffer vb2 = new VertexBuffer(); VertexArray pos2 = new VertexArray(3, 3, 2); pos2.set(0, 3, new short[]{0, 0, 0, 0, 0, 0, 0, 0, 0}); vb2.setPositions(pos2, 1f, null);
        SkinnedMesh sm = new SkinnedMesh(vb2, new TriangleStripArray(0, new int[]{3}), new Appearance(), skeleton);
        sm.addTransform(bone, 1, 0, 3);
        Group root = new Group(); root.addChild(sm);
        Image img = canvas(0x000000); Graphics3D g3 = Graphics3D.getInstance(); g3.bindTarget(img.getGraphics()); g3.setViewport(0, 0, 64, 64);
        Camera cam = new Camera(); cam.setParallel(32, 1, -10, 10); g3.setCamera(cam, null); g3.render(root, null); g3.releaseTarget();
        boolean any = false; for (int y = 0; y < 64 && !any; y++) for (int x = 0; x < 64; x++) if (px(img, x, y) != 0) { any = true; break; }
        check(any, "mesh attached to a bone of a SkinnedMesh skeleton is rendered");
        System.out.println(fails == 0 ? "ALL PASSED" : fails + " FAILED");
        if (fails != 0) System.exit(1);
    }
}
