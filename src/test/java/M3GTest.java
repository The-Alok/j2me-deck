// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 J2ME Deck contributors
import javax.microedition.lcdui.Graphics;
import javax.microedition.lcdui.Image;
import javax.microedition.m3g.*;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.zip.Deflater;

/** M3G (JSR-184) tests: math, scene graph, animation, software renderer, picking and the .m3g loader. */
public class M3GTest {
    static int fails;
    static void check(boolean ok, String what) { System.out.println((ok ? "PASS  " : "FAIL  ") + what); if (!ok) fails++; }
    static boolean near(float a, float b, float eps) { return Math.abs(a - b) <= eps; }

    // ------------------------------------------------------------------ helpers
    static VertexBuffer vb(short[] pos, int[] colorArgb, byte[] normals) {
        VertexArray p = new VertexArray(pos.length / 3, 3, 2); p.set(0, pos.length / 3, pos);
        VertexBuffer v = new VertexBuffer(); v.setPositions(p, 1f, null);
        if (colorArgb != null) v.setDefaultColor(colorArgb[0]);
        if (normals != null) { VertexArray n = new VertexArray(normals.length / 3, 3, 1); n.set(0, normals.length / 3, normals); v.setNormals(n); }
        return v;
    }
    static Camera camera(float fov) { Camera c = new Camera(); c.setPerspective(fov, 1f, 0.1f, 100f); return c; }
    static Transform at(float x, float y, float z) { Transform t = new Transform(); t.postTranslate(x, y, z); return t; }
    static int px(Image img, int x, int y) { int[] p = new int[1]; img.getRGB(p, 0, 1, x, y, 1, 1); return p[0] & 0xFFFFFF; }
    static Image blank(int w, int h, int rgb) { Image i = Image.createImage(w, h); Graphics g = i.getGraphics(); g.setColor(rgb); g.fillRect(0, 0, w, h); return i; }

    interface Scene { void draw(Graphics3D g3, Camera cam); }
    /** Renders into a 100x100 white image with a camera 5 units in front of the origin and returns the image. */
    static Image shot(Scene s) {
        Image img = blank(100, 100, 0xFFFFFF);
        Graphics3D g3 = Graphics3D.getInstance();
        g3.resetLights();
        g3.bindTarget(img.getGraphics());
        try { Camera cam = camera(60); g3.setCamera(cam, at(0, 0, 5)); s.draw(g3, cam); } finally { g3.releaseTarget(); }
        return img;
    }
    static final short[] TRI = {-1, -1, 0, 1, -1, 0, 0, 1, 0};                     // CCW seen from +Z
    static TriangleStripArray one() { return new TriangleStripArray(0, new int[]{3}); }
    static Appearance plain() { return new Appearance(); }

    public static void main(String[] a) throws Exception {
        // ================================================================== transforms
        Transform t = new Transform();
        t.postTranslate(1, 2, 3);
        float[] v = {0, 0, 0, 1}; t.transform(v);
        check(near(v[0], 1, 1e-5f) && near(v[1], 2, 1e-5f) && near(v[2], 3, 1e-5f), "Transform: translate moves the origin to (1,2,3)");
        Transform r = new Transform(); r.postRotate(90, 0, 0, 1);
        float[] x = {1, 0, 0, 1}; r.transform(x);
        check(near(x[0], 0, 1e-5f) && near(x[1], 1, 1e-5f), "Transform: 90 degrees about Z maps +X to +Y");
        Transform m = new Transform(); m.postTranslate(5, 0, 0); m.postRotate(90, 0, 0, 1);       // translate THEN rotate (post = on the right)
        float[] q = {1, 0, 0, 1}; m.transform(q);
        check(near(q[0], 5, 1e-4f) && near(q[1], 1, 1e-4f), "Transform: postMultiply order is T*R (applies R to the point first)");
        Transform inv = new Transform(m); inv.invert(); inv.postMultiply(m);
        float[] id = new float[16]; inv.get(id);
        check(near(id[0], 1, 1e-4f) && near(id[5], 1, 1e-4f) && near(id[3], 0, 1e-4f), "Transform: M^-1 * M = identity");
        boolean threw = false; try { Transform z = new Transform(); z.postScale(0, 0, 0); z.invert(); } catch (ArithmeticException e) { threw = true; }
        check(threw, "Transform: inverting a singular matrix throws ArithmeticException");

        // ================================================================== scene graph
        Group root = new Group(), child = new Group(), leaf = new Group();
        root.addChild(child); child.addChild(leaf);
        root.setTranslation(10, 0, 0); child.setTranslation(0, 20, 0); leaf.setTranslation(0, 0, 30);
        Transform tt = new Transform();
        check(leaf.getTransformTo(root, tt), "getTransformTo: nodes in one tree");
        float[] o = {0, 0, 0, 1}; tt.transform(o);
        check(near(o[0], 0, 1e-5f) && near(o[1], 20, 1e-5f) && near(o[2], 30, 1e-5f), "getTransformTo(leaf -> root) accumulates child translations (0,20,30)");
        leaf.getTransformTo(new Group(), tt);
        check(!leaf.getTransformTo(new Group(), tt), "getTransformTo: different trees returns false");
        boolean cyc = false; try { leaf.addChild(root); } catch (IllegalArgumentException e) { cyc = true; }
        check(cyc, "addChild rejects a cycle");
        boolean twice = false; try { new Group().addChild(child); } catch (IllegalArgumentException e) { twice = true; }
        check(twice, "addChild rejects a node that already has a parent");
        leaf.setUserID(77);
        check(root.find(77) == leaf, "find(userID) locates a descendant");
        Group copy = (Group) root.duplicate();
        check(copy != root && copy.getChild(0) != child && copy.getChildCount() == 1 && copy.getChild(0).getParent() == copy, "duplicate() deep-copies the node tree");
        Mesh shared = new Mesh(vb(TRI, new int[]{0xFFFFFFFF}, null), one(), plain());
        Mesh sharedCopy = (Mesh) shared.duplicate();
        check(sharedCopy.getVertexBuffer() == shared.getVertexBuffer() && sharedCopy.getAppearance(0) == shared.getAppearance(0), "duplicate() shares vertex data and appearance, as the spec requires");
        check(root.getReferences(new Object3D[16]) == 1, "getReferences reports the direct children");

        // ================================================================== camera
        Camera pc = camera(90); Transform pm = new Transform(); int type = pc.getProjection(pm); float[] f = new float[16]; pm.get(f);
        check(type == Camera.PERSPECTIVE && near(f[0], 1f, 1e-4f) && near(f[5], 1f, 1e-4f) && near(f[14], -1f, 1e-5f), "perspective projection: 90 degree fov gives f=1, w = -z");
        boolean badFov = false; try { camera(0); } catch (IllegalArgumentException e) { badFov = true; }
        check(badFov, "setPerspective rejects fov <= 0");

        // ================================================================== animation
        KeyframeSequence ks = new KeyframeSequence(3, 3, KeyframeSequence.LINEAR);
        ks.setKeyframe(0, 0, new float[]{0, 0, 0}); ks.setKeyframe(1, 1000, new float[]{10, 0, 0}); ks.setKeyframe(2, 2000, new float[]{10, 20, 0});
        ks.setDuration(2000); ks.setRepeatMode(KeyframeSequence.LOOP); ks.setValidRange(0, 2);
        AnimationController ctrl = new AnimationController();
        AnimationTrack track = new AnimationTrack(ks, AnimationTrack.TRANSLATION); track.setController(ctrl);
        Group mover = new Group(); mover.addAnimationTrack(track);
        mover.animate(500); float[] tr = new float[3]; mover.getTranslation(tr);
        check(near(tr[0], 5, 1e-3f) && near(tr[1], 0, 1e-3f), "animation: LINEAR interpolation at 500 ms = (5,0,0)");
        mover.animate(1500); mover.getTranslation(tr);
        check(near(tr[0], 10, 1e-3f) && near(tr[1], 10, 1e-3f), "animation: at 1500 ms = (10,10,0)");
        mover.animate(2500); mover.getTranslation(tr);
        check(near(tr[0], 5, 1e-3f), "animation: LOOP wraps 2500 ms to 500 ms");
        ctrl.setSpeed(2f, 0); mover.animate(250); mover.getTranslation(tr);
        check(near(tr[0], 5, 1e-3f), "animation: controller speed 2x doubles the sequence position");
        ctrl.setSpeed(1f, 0); ctrl.setActiveInterval(0, 100); mover.animate(5000); mover.getTranslation(tr);
        check(near(tr[0], 5, 1e-3f), "animation: outside the active interval nothing changes");
        KeyframeSequence rot = new KeyframeSequence(2, 4, KeyframeSequence.SLERP);
        float s2 = (float) Math.sqrt(0.5);
        rot.setKeyframe(0, 0, new float[]{0, 0, 0, 1}); rot.setKeyframe(1, 1000, new float[]{0, 0, s2, s2});       // 0 -> 90 degrees about Z
        rot.setDuration(1000); rot.setValidRange(0, 1);
        AnimationController c2 = new AnimationController(); AnimationTrack rt = new AnimationTrack(rot, AnimationTrack.ORIENTATION); rt.setController(c2);
        Group spinner = new Group(); spinner.addAnimationTrack(rt); spinner.animate(500);
        float[] ang = new float[4]; spinner.getOrientation(ang);
        check(near(ang[0], 45, 0.5f) && near(ang[3], 1, 1e-3f), "animation: SLERP halfway = 45 degrees about Z (" + ang[0] + ")");
        boolean incompatible = false; try { new Group().addAnimationTrack(new AnimationTrack(ks, AnimationTrack.SHININESS == 0 ? 0 : AnimationTrack.SHININESS == 271 ? 275 : 275)); } catch (Exception e) { incompatible = true; }
        boolean badTrack = false; try { Material mt = new Material(); mt.addAnimationTrack(track); } catch (IllegalArgumentException e) { badTrack = true; }
        check(badTrack, "addAnimationTrack rejects a track whose property the target doesn't have");

        // ================================================================== renderer
        Image red = shot((g3, cam) -> g3.render(vb(TRI, new int[]{0xFFFF0000}, null), one(), plain(), null));
        check(px(red, 50, 56) == 0xFF0000 && px(red, 3, 3) == 0xFFFFFF, "render: red triangle appears at the centre; the 2D background is preserved");

        short[] rev = {-1, -1, 0, 0, 1, 0, 1, -1, 0};                              // clockwise from +Z
        Image culled = shot((g3, cam) -> g3.render(vb(rev, new int[]{0xFFFF0000}, null), one(), plain(), null));
        check(px(culled, 50, 56) == 0xFFFFFF, "render: back face is culled by default");
        PolygonMode both = new PolygonMode(); both.setCulling(PolygonMode.CULL_NONE);
        Appearance apBoth = plain(); apBoth.setPolygonMode(both);
        Image shown = shot((g3, cam) -> g3.render(vb(rev, new int[]{0xFFFF0000}, null), one(), apBoth, null));
        check(px(shown, 50, 56) == 0xFF0000, "render: CULL_NONE shows the back face");
        PolygonMode cw = new PolygonMode(); cw.setWinding(PolygonMode.WINDING_CW);
        Appearance apCw = plain(); apCw.setPolygonMode(cw);
        Image cwImg = shot((g3, cam) -> g3.render(vb(rev, new int[]{0xFFFF0000}, null), one(), apCw, null));
        check(px(cwImg, 50, 56) == 0xFF0000, "render: WINDING_CW makes the clockwise triangle the front face");

        // depth: the near (red) triangle must win whichever is drawn first
        for (int order = 0; order < 2; order++) {
            final int ord = order;
            Image d = shot((g3, cam) -> {
                VertexBuffer nearV = vb(TRI, new int[]{0xFFFF0000}, null), farV = vb(TRI, new int[]{0xFF00FF00}, null);
                if (ord == 0) { g3.render(nearV, one(), plain(), at(0, 0, 1)); g3.render(farV, one(), plain(), at(0, 0, -1)); }
                else { g3.render(farV, one(), plain(), at(0, 0, -1)); g3.render(nearV, one(), plain(), at(0, 0, 1)); }
            });
            check(px(d, 50, 56) == 0xFF0000, "depth buffer: nearer red triangle wins when drawn " + (order == 0 ? "first" : "last"));
        }

        // alpha blending: 50% green over white
        CompositingMode cm = new CompositingMode(); cm.setBlending(CompositingMode.ALPHA);
        Appearance apA = plain(); apA.setCompositingMode(cm);
        Image al = shot((g3, cam) -> g3.render(vb(TRI, new int[]{0x8000FF00}, null), one(), apA, null));
        int mixed = px(al, 50, 56);
        check(((mixed >> 16) & 255) > 100 && ((mixed >> 16) & 255) < 160 && ((mixed >> 8) & 255) > 240, "alpha blending: 50% green over white = pale green (" + Integer.toHexString(mixed) + ")");

        // alpha test
        CompositingMode at = new CompositingMode(); at.setAlphaThreshold(0.9f);
        Appearance apT = plain(); apT.setCompositingMode(at);
        Image atI = shot((g3, cam) -> g3.render(vb(TRI, new int[]{0x4000FF00}, null), one(), apT, null));
        check(px(atI, 50, 56) == 0xFFFFFF, "alpha test: fragments below the threshold are discarded");

        // lighting: directional light along -Z (from the camera) lights a +Z-facing triangle; from behind it does not
        Material mat = new Material(); mat.setColor(Material.DIFFUSE, 0xFFFFFFFF); mat.setColor(Material.AMBIENT, 0xFF000000);
        Appearance apL = plain(); apL.setMaterial(mat);
        byte[] up = {0, 0, 127, 0, 0, 127, 0, 0, 127};
        Light sun = new Light(); sun.setMode(Light.DIRECTIONAL); sun.setColor(0xFF0000);
        Image lit = shot((g3, cam) -> { g3.addLight(sun, at(0, 0, 5)); g3.render(vb(TRI, null, up), one(), apL, null); });
        Image dark = shot((g3, cam) -> { Transform back = at(0, 0, 5); back.postRotate(180, 0, 1, 0); g3.addLight(sun, back); g3.render(vb(TRI, null, up), one(), apL, null); });
        check(px(lit, 50, 56) == 0xFF0000, "lighting: red directional light on a facing triangle = pure red (" + Integer.toHexString(px(lit, 50, 56)) + ")");
        check(px(dark, 50, 56) == 0x000000, "lighting: light from behind leaves the triangle black (" + Integer.toHexString(px(dark, 50, 56)) + ")");
        Light amb = new Light(); amb.setMode(Light.AMBIENT); amb.setColor(0xFFFFFF); amb.setIntensity(0.5f);
        Material am = new Material(); am.setColor(Material.AMBIENT, 0xFF0000FF); am.setColor(Material.DIFFUSE, 0xFF000000); Appearance apAm = plain(); apAm.setMaterial(am);
        Image ai = shot((g3, cam) -> { g3.addLight(amb, null); g3.render(vb(TRI, null, up), one(), apAm, null); });
        int ab = px(ai, 50, 56) & 255;
        check(ab > 120 && ab < 135, "lighting: ambient light 0.5 on a blue-ambient material = half blue (" + ab + ")");
        Light spot = new Light(); spot.setMode(Light.SPOT); spot.setSpotAngle(10);
        Image sp = shot((g3, cam) -> { Transform lt = at(0, 0, 5); lt.postRotate(40, 0, 1, 0); g3.addLight(spot, lt); g3.render(vb(TRI, null, up), one(), apL, null); });
        check(px(sp, 50, 56) == 0x000000, "lighting: a spot light pointing 40 degrees away (cone 10) doesn't light the triangle");

        // fog: fully fogged at distance >= far -> fog colour
        Fog fog = new Fog(); fog.setMode(Fog.LINEAR); fog.setLinear(0.1f, 2f); fog.setColor(0x0000FF);
        Appearance apF = plain(); apF.setFog(fog);
        Image fg = shot((g3, cam) -> g3.render(vb(TRI, new int[]{0xFFFF0000}, null), one(), apF, null));
        check(px(fg, 50, 56) == 0x0000FF, "fog: a triangle 5 units away with far=2 is entirely fog-coloured");

        // texture: 2x2 RGB image, left column red, right column blue; REPLACE function, U maps across the triangle
        byte[] texData = {(byte) 255, 0, 0, 0, 0, (byte) 255, (byte) 255, 0, 0, 0, 0, (byte) 255};
        Image2D im = new Image2D(Image2D.RGB, 2, 2, texData);
        Texture2D tex = new Texture2D(im); tex.setBlending(Texture2D.FUNC_REPLACE); tex.setWrapping(Texture2D.WRAP_CLAMP, Texture2D.WRAP_CLAMP);
        Appearance apTex = plain(); apTex.setTexture(0, tex);
        short[] quad = {-2, -2, 0, 2, -2, 0, -2, 2, 0, 2, 2, 0};
        VertexBuffer qv = vb(quad, new int[]{0xFFFFFFFF}, null);
        VertexArray uv = new VertexArray(4, 2, 2); uv.set(0, 4, new short[]{0, 1, 1, 1, 0, 0, 1, 0});
        qv.setTexCoords(0, uv, 1f, null);
        Image ti = shot((g3, cam) -> g3.render(qv, new TriangleStripArray(0, new int[]{4}), apTex, null));
        check(px(ti, 35, 50) == 0xFF0000 && px(ti, 65, 50) == 0x0000FF, "texture: left half red, right half blue (REPLACE, perspective-correct UV)");
        Texture2D mod = new Texture2D(im); mod.setBlending(Texture2D.FUNC_MODULATE);
        Appearance apMod = plain(); apMod.setTexture(0, mod);
        VertexBuffer gv = vb(quad, new int[]{0xFF00FF00}, null); gv.setTexCoords(0, uv, 1f, null);
        Image mi = shot((g3, cam) -> g3.render(gv, new TriangleStripArray(0, new int[]{4}), apMod, null));
        check(px(mi, 35, 50) == 0x000000, "texture: MODULATE of a red texel by a green vertex colour = black");

        // near-plane clipping: a triangle that crosses behind the camera must not crash or smear
        short[] huge = {-1, -1, 0, 1, -1, 0, 0, 1, 40};
        Image cl = shot((g3, cam) -> g3.render(vb(huge, new int[]{0xFFFF0000}, null), one(), apBoth, null));
        check(cl != null, "clipping: geometry crossing the camera plane renders without error");

        // ================================================================== World rendering
        World world = new World();
        Camera cam = camera(60); cam.setTranslation(0, 0, 5); world.addChild(cam); world.setActiveCamera(cam);
        Background bg = new Background(); bg.setColor(0x336699); world.setBackground(bg);
        Mesh mesh = new Mesh(vb(TRI, new int[]{0xFFFF0000}, null), one(), plain());
        world.addChild(mesh);
        Image wi = blank(100, 100, 0xFFFFFF);
        Graphics3D g3 = Graphics3D.getInstance();
        g3.bindTarget(wi.getGraphics()); g3.render(world); g3.releaseTarget();
        check(px(wi, 50, 56) == 0xFF0000 && px(wi, 3, 3) == 0x336699, "render(World): background colour fills the viewport and the mesh is drawn");
        mesh.setRenderingEnable(false);
        wi = blank(100, 100, 0xFFFFFF); g3.bindTarget(wi.getGraphics()); g3.render(world); g3.releaseTarget();
        check(px(wi, 50, 56) == 0x336699, "render(World): rendering-disabled nodes are skipped");
        mesh.setRenderingEnable(true); mesh.setAlphaFactor(0f);
        Appearance blendAp = plain(); CompositingMode ca = new CompositingMode(); ca.setBlending(CompositingMode.ALPHA); blendAp.setCompositingMode(ca); mesh.setAppearance(0, blendAp);
        wi = blank(100, 100, 0xFFFFFF); g3.bindTarget(wi.getGraphics()); g3.render(world); g3.releaseTarget();
        check(px(wi, 50, 56) == 0x336699, "render(World): node alpha factor 0 makes a blended mesh invisible");
        boolean noCam = false; try { World w0 = new World(); g3.bindTarget(wi.getGraphics()); try { g3.render(w0); } finally { g3.releaseTarget(); } } catch (IllegalStateException e) { noCam = true; }
        check(noCam, "render(World) without an active camera throws IllegalStateException");
        boolean notBound = false; try { g3.render(world); } catch (IllegalStateException e) { notBound = true; }
        check(notBound, "rendering without a bound target throws IllegalStateException");

        // translated Graphics + clip are honoured
        Image tc = blank(100, 100, 0xFFFFFF); Graphics tg = tc.getGraphics(); tg.translate(50, 0); tg.setClip(0, 0, 40, 100);
        g3.bindTarget(tg); g3.setCamera(camera(60), at(0, 0, 5)); g3.clear(null); g3.render(vb(TRI, new int[]{0xFFFF0000}, null), one(), plain(), null); g3.releaseTarget();
        check(px(tc, 10, 50) == 0xFFFFFF && px(tc, 49, 50) == 0xFFFFFF && px(tc, 90, 50) == 0xFFFFFF && px(tc, 50, 50) == 0x000000 && px(tc, 70, 50) == 0xFF0000, "Graphics translate/clip: only the clipped, translated area (x 50..89) is touched; inside it is cleared and the triangle drawn");

        // ================================================================== skinning and morphing
        Group skeleton = new Group(); Group bone = new Group(); skeleton.addChild(bone);
        VertexBuffer sv = vb(TRI, new int[]{0xFFFF0000}, null);
        SkinnedMesh sm = new SkinnedMesh(sv, one(), plain(), skeleton);
        sm.addTransform(bone, 1, 0, 3);
        bone.setTranslation(100, 0, 0);          // move the bone far off screen after binding: all vertices follow
        Image sk = shot((g3b, c) -> g3b.render(sm, null));
        check(px(sk, 50, 56) == 0xFFFFFF, "SkinnedMesh: moving the only bone moves every vertex (triangle leaves the screen)");
        bone.setTranslation(0, 0, 0);
        Image sk2 = shot((g3b, c) -> g3b.render(sm, null));
        check(px(sk2, 50, 56) == 0xFF0000, "SkinnedMesh: bone at its bind pose leaves the mesh unchanged");
        int[] vi = new int[3]; float[] wt = new float[3];
        check(sm.getBoneVertices(bone, vi, wt) == 3 && near(wt[0], 1, 1e-5f), "SkinnedMesh.getBoneVertices reports 3 vertices with weight 1");

        short[] moved = {-1, 99, 0, 1, 99, 0, 0, 101, 0};
        MorphingMesh mm = new MorphingMesh(vb(TRI, new int[]{0xFFFF0000}, null), new VertexBuffer[]{vb(moved, new int[]{0xFFFF0000}, null)}, one(), plain());
        mm.setWeights(new float[]{0f});
        Image m0 = shot((g3b, c) -> g3b.render(mm, null));
        mm.setWeights(new float[]{1f});
        Image m1 = shot((g3b, c) -> g3b.render(mm, null));
        check(px(m0, 50, 56) == 0xFF0000 && px(m1, 50, 56) == 0xFFFFFF, "MorphingMesh: weight 0 = base shape, weight 1 = morph target (moved off screen)");

        // ================================================================== picking
        World pw = new World(); Camera pcm = camera(60); pcm.setTranslation(0, 0, 5); pw.addChild(pcm); pw.setActiveCamera(pcm);
        Mesh target = new Mesh(vb(TRI, new int[]{0xFFFF0000}, null), one(), plain()); pw.addChild(target);
        RayIntersection ri = new RayIntersection();
        boolean hit = pw.pick(-1, 0f, 0f, 3f, 0f, 0f, -1f, ri);
        check(hit && ri.getIntersected() == target && near(ri.getDistance(), 3f, 1e-4f) && near(ri.getNormalZ(), 1f, 1e-4f), "pick(ray): hits the triangle at distance 3 with a +Z normal");
        check(!pw.pick(-1, 5f, 5f, 3f, 0f, 0f, -1f, ri), "pick(ray): a ray beside the triangle misses");
        boolean cameraHit = pw.pick(-1, 0.5f, 0.55f, pcm, ri);
        check(cameraHit && ri.getIntersected() == target, "pick(camera): the centre of the viewport hits the triangle");
        target.setPickingEnable(false);
        check(!pw.pick(-1, 0f, 0f, 3f, 0f, 0f, -1f, ri), "pick: nodes with picking disabled are ignored");

        // ================================================================== loader
        byte[] file = buildFile(true);
        Object3D[] loaded = Loader.load(file, 0);
        check(loaded.length == 1 && loaded[0] instanceof World, "Loader: a compressed .m3g returns the World as the only root object");
        World lw = (World) loaded[0];
        check(lw.getChildCount() == 2 && lw.getActiveCamera() != null && lw.getBackground() != null && lw.find(4242) instanceof Mesh, "Loader: scene graph, active camera, background and user ids restored");
        Image li = blank(100, 100, 0xFFFFFF);
        g3.bindTarget(li.getGraphics()); g3.render(lw); g3.releaseTarget();
        check(px(li, 50, 56) == 0xFF0000 && px(li, 3, 3) == 0x102030, "Loader: the loaded world renders the triangle in the colour it was saved with");
        Object3D[] plainLoad = Loader.load(buildFile(false), 0);
        check(plainLoad.length == 1 && plainLoad[0] instanceof World, "Loader: an uncompressed section loads too");
        boolean bad = false; try { byte[] cut = java.util.Arrays.copyOf(file, file.length - 30); Loader.load(cut, 0); } catch (java.io.IOException e) { bad = true; }
        check(bad, "Loader: a truncated file raises IOException");
        java.io.ByteArrayOutputStream png = new java.io.ByteArrayOutputStream();
        java.awt.image.BufferedImage bi = new java.awt.image.BufferedImage(4, 2, java.awt.image.BufferedImage.TYPE_INT_RGB); bi.setRGB(0, 0, 0xFF8000);
        javax.imageio.ImageIO.write(bi, "png", png);
        Object3D[] img = Loader.load(png.toByteArray(), 0);
        check(img.length == 1 && img[0] instanceof Image2D && ((Image2D) img[0]).getWidth() == 4 && ((Image2D) img[0]).getFormat() == Image2D.RGB, "Loader: a PNG loads as an RGB Image2D");

        // properties
        check(Graphics3D.getProperties().get("maxLights").equals(8) && Graphics3D.getProperties().get("numTextureUnits") != null, "Graphics3D.getProperties() reports the capabilities games query");

        System.out.println(fails == 0 ? "ALL PASSED" : fails + " FAILED");
        System.exit(fails == 0 ? 0 : 1);
    }

    // ------------------------------------------------------------------ minimal .m3g writer (the layout follows the JSR-184 file format)
    static final class W {
        final ByteBuffer b = ByteBuffer.allocate(8192).order(ByteOrder.LITTLE_ENDIAN);
        W u8(int v) { b.put((byte) v); return this; } W u16(int v) { b.putShort((short) v); return this; } W i32(long v) { b.putInt((int) v); return this; }
        W f(float v) { b.putFloat(v); return this; } W bool(boolean v) { b.put((byte) (v ? 1 : 0)); return this; }
        W rgb(int c) { u8(c >> 16).u8(c >> 8).u8(c); return this; } W rgba(int argb) { u8(argb >> 16).u8(argb >> 8).u8(argb).u8(argb >>> 24); return this; }
        W str(String s) { b.put(s.getBytes()); b.put((byte) 0); return this; }
        W obj3d(int userId) { return i32(userId).i32(0).i32(0); }              // user id, no tracks, no user parameters
        byte[] bytes() { byte[] r = new byte[b.position()]; System.arraycopy(b.array(), 0, r, 0, r.length); return r; }
    }
    static void object(ByteArrayOutputStream out, int type, W body) {
        byte[] d = body.bytes();
        out.write(type); ByteBuffer l = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN); l.putInt(d.length); out.write(l.array(), 0, 4); out.write(d, 0, d.length);
    }
    static void transformable(W w) { w.bool(false).bool(false); }
    static void node(W w) { w.bool(true).bool(true).u8(255).i32(-1).bool(false); }

    static byte[] buildFile(boolean compress) throws Exception {
        ByteArrayOutputStream objs = new ByteArrayOutputStream();
        object(objs, 0, new W().u8(1).u8(0).bool(false).i32(0).i32(0).str("test"));                                  // 1  header
        W va = new W().obj3d(0).u8(2).u8(3).u8(0).u16(3);                                                            // 2  VertexArray: 16-bit, 3 components, raw, 3 vertices
        for (int v : new int[]{-1, -1, 0, 1, -1, 0, 0, 1, 0}) va.u16(v);
        object(objs, 20, va);
        object(objs, 21, new W().obj3d(0).rgba(0xFFFF0000).i32(2).f(0).f(0).f(0).f(1f).i32(0).i32(0).i32(0));       // 3  VertexBuffer: red default colour, positions = object 2
        object(objs, 11, new W().obj3d(0).u8(0).i32(0).i32(1).i32(3));                                              // 4  TriangleStripArray: implicit indices from 0, one strip of 3
        object(objs, 3, new W().obj3d(0).u8(0).i32(0).i32(0).i32(0).i32(0).i32(0));                                 // 5  Appearance: all defaults
        W mesh = new W().obj3d(4242); transformable(mesh); node(mesh); mesh.i32(3).i32(1).i32(4).i32(5);            // 6  Mesh (user id 4242)
        object(objs, 14, mesh);
        W cam = new W().obj3d(0).bool(true).f(0).f(0).f(5).f(1).f(1).f(1).f(0).f(0).f(0).f(0).bool(false);          // 7  Camera at z=5 (component transform)
        node(cam); cam.u8(50).f(60).f(1).f(0.1f).f(100);
        object(objs, 5, cam);
        object(objs, 4, new W().obj3d(0).rgba(0xFF102030).i32(0).u8(32).u8(32).i32(0).i32(0).i32(0).i32(0).bool(true).bool(true));   // 8  Background 0x102030
        W world = new W().obj3d(0); transformable(world); node(world); world.i32(2).i32(6).i32(7).i32(7).i32(8);    // 9  World: children = mesh, camera; active camera 7; background 8
        object(objs, 22, world);

        byte[] data = objs.toByteArray();
        byte[] payload = data;
        if (compress) {
            Deflater d = new Deflater(); d.setInput(data); d.finish();
            byte[] buf = new byte[data.length + 64]; int n = d.deflate(buf); d.end();
            payload = java.util.Arrays.copyOf(buf, n);
        }
        ByteBuffer sec = ByteBuffer.allocate(13 + payload.length).order(ByteOrder.LITTLE_ENDIAN);
        sec.put((byte) (compress ? 1 : 0)).putInt(13 + payload.length).putInt(data.length).put(payload).putInt(0);     // checksum field is not verified by the loader
        ByteArrayOutputStream file = new ByteArrayOutputStream();
        file.write(new byte[]{(byte) 0xAB, 'J', 'S', 'R', '1', '8', '4', (byte) 0xBB, 0x0D, 0x0A, 0x1A, 0x0A});
        file.write(sec.array());
        return file.toByteArray();
    }
}
