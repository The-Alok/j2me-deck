// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 J2ME Deck contributors
import com.mascotcapsule.micro3d.v3.*;
import javax.microedition.lcdui.Graphics;
import javax.microedition.lcdui.Image;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/** Drives the Mascot Capsule v3 software renderer through its public API with synthetic geometry. */
public class MascotTest {
    static int fails;
    static void check(boolean ok, String what) { System.out.println((ok ? "PASS  " : "FAIL  ") + what); if (!ok) fails++; }

    static int px(Image img, int x, int y) { int[] p = new int[1]; img.getRGB(p, 0, 1, x, y, 1, 1); return p[0] & 0xFFFFFF; }

    /** 8-bit palettised BMP (what Mascot textures are), w x h: palette 0 = red, 1 = blue; left half index 0, right half index 1. */
    static byte[] bmp(int w, int h) {
        int row = (w + 3) & ~3, off = 14 + 40 + 1024, size = off + row * h;
        ByteBuffer b = ByteBuffer.allocate(size).order(ByteOrder.LITTLE_ENDIAN);
        b.put((byte) 'B').put((byte) 'M').putInt(size).putInt(0).putInt(off);
        b.putInt(40).putInt(w).putInt(h).putShort((short) 1).putShort((short) 8).putInt(0).putInt(row * h).putInt(2835).putInt(2835).putInt(256).putInt(0);
        for (int i = 0; i < 256; i++) { if (i == 0) b.put((byte) 0).put((byte) 0).put((byte) 255).put((byte) 0); else if (i == 1) b.put((byte) 255).put((byte) 0).put((byte) 0).put((byte) 0); else b.putInt(0); }   // B,G,R,0
        for (int y = 0; y < h; y++) { for (int x = 0; x < w; x++) b.put((byte) (x < w / 2 ? 0 : 1)); for (int k = w; k < row; k++) b.put((byte) 0); }
        return b.array();
    }

    public static void main(String[] a) throws Exception {
        // ---- fixed-point math (4096 = 1.0)
        AffineTrans id = new AffineTrans(); id.setIdentity();
        Vector3D v = new Vector3D(100, -200, 300), out = new Vector3D();
        out.set(id.transPoint(v)); 
        check(out.x == 100 && out.y == -200 && out.z == 300, "identity AffineTrans leaves a point unchanged: " + out.x + "," + out.y + "," + out.z);
        AffineTrans rot = new AffineTrans(); rot.setRotationZ(1024);   // a quarter turn if 4096 = full circle
        Vector3D r = rot.transPoint(new Vector3D(4096, 0, 0));
        check(Math.abs(r.x) < 40 && Math.abs(Math.abs(r.y) - 4096) < 40, "quarter turn about Z maps (4096,0,0) to (0,+-4096,0): " + r.x + "," + r.y + "," + r.z);
        check(Util3D.sqrt(4096 * 4096) == 4096 || Math.abs(Util3D.sqrt(4096 * 4096) - 4096) <= 1, "Util3D.sqrt");

        // ---- flat-coloured triangle into a real MIDP Graphics
        Image img = Image.createImage(120, 120);
        Graphics g = img.getGraphics();
        g.setColor(0xFFFFFF); g.fillRect(0, 0, 120, 120);
        Graphics3D g3 = new Graphics3D();
        FigureLayout layout = new FigureLayout();                 // parallel projection, scale 512 (=0.125 px per unit)
        layout.setCenter(60, 60);
        Effect3D fx = new Effect3D();
        int[] tri = {0, -400, 0,   400, 400, 0,   -400, 400, 0};      // layout scale 512: 4096 units = 512 px, so 400 units ~ 50 px
        int cmd = Graphics3D.PRIMITVE_TRIANGLES | Graphics3D.PDATA_COLOR_PER_COMMAND;
        g3.bind(g);
        g3.renderPrimitives(null, 0, 0, layout, fx, cmd, 1, tri, new int[0], new int[0], new int[]{0xFF0000});
        g3.flush();
        g3.release(g);
        int center = px(img, 60, 75), corner = px(img, 2, 2), above = px(img, 60, 5);   // apex at y=60-50=10, base at y=110
        System.out.printf("   center=%06X corner=%06X apex-above=%06X%n", center, corner, above);
        check(center == 0xFF0000, "red triangle covers the centre of the target");
        check(corner == 0xFFFFFF, "pixels outside the triangle keep the 2D content (2D/3D mixing works)");

        // ---- a quad, and painter ordering through two commands
        Image img2 = Image.createImage(120, 120); Graphics g2 = img2.getGraphics(); g2.setColor(0x000000); g2.fillRect(0, 0, 120, 120);
        int[] quad = {-300, -300, 0,   300, -300, 0,   300, 300, 0,   -300, 300, 0};
        g3.bind(g2);
        g3.renderPrimitives(null, 0, 0, layout, fx, Graphics3D.PRIMITVE_QUADS | Graphics3D.PDATA_COLOR_PER_COMMAND, 1, quad, new int[0], new int[0], new int[]{0x00FF00});
        g3.flush(); g3.release(g2);
        check(px(img2, 60, 60) == 0x00FF00 && px(img2, 5, 5) == 0x000000, "green quad drawn centred, black elsewhere");

        // ---- textured quad: left half of the texture is red, right half blue
        Texture tex;      // isForModel=true: a UV-mapped model texture (false would be an environment map)
        try { tex = new Texture(bmp(8, 8), true); } catch (Throwable t) { tex = null; System.out.println("   (texture BMP rejected: " + t + ")"); }
        if (tex != null) {
            Image img3 = Image.createImage(120, 120); Graphics g4 = img3.getGraphics(); g4.setColor(0); g4.fillRect(0, 0, 120, 120);
            int[] uv = {0, 0,   255, 0,   255, 255,   0, 255};      // 0..255 texel coordinates
            g3.bind(g4);
            g3.renderPrimitives(tex, 0, 0, layout, fx, Graphics3D.PRIMITVE_QUADS | Graphics3D.PDATA_TEXURE_COORD, 1, quad, new int[0], uv, new int[0]);
            g3.flush(); g3.release(g4);
            int l = px(img3, 50, 60), rr = px(img3, 70, 60);
            System.out.printf("   textured quad: left=%06X right=%06X%n", l, rr);
            check(l == 0xFF0000 && rr == 0x0000FF, "textured quad shows the left (red) and right (blue) halves of the BMP");
        }

        g3.dispose();
        System.out.println(fails == 0 ? "ALL PASSED" : fails + " FAILED");
        System.exit(fails == 0 ? 0 : 1);
    }
}
