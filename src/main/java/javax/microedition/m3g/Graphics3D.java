// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 J2ME Deck contributors
package javax.microedition.m3g;

import java.util.ArrayList;
import java.util.Hashtable;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

public final class Graphics3D {
    public static final int ANTIALIAS = 2, DITHER = 4, TRUE_COLOR = 8, OVERWRITE = 16;

    private static Graphics3D instance;
    private static Hashtable<String, Object> properties;

    private final SoftRenderer r = new SoftRenderer();
    private Object target;
    private javax.microedition.lcdui.Graphics graphics;
    private Image2D targetImage;
    private int hints, tx, ty;
    private boolean depthEnabled, viewportSet;
    private int vpX, vpY, vpW, vpH;
    private Camera camera;
    private final Transform cameraToWorld = new Transform();
    private float dnear = 0, dfar = 1;
    private final List<Light> lightNodes = new ArrayList<>();

    private Graphics3D() {}

    public static synchronized Graphics3D getInstance() {
        if (instance == null) instance = new Graphics3D();
        return instance;
    }

    public static synchronized Hashtable<String, Object> getProperties() {
        if (properties == null) {
            properties = new Hashtable<>();
            properties.put("supportAntialiasing", Boolean.FALSE);
            properties.put("supportTrueColor", Boolean.TRUE);
            properties.put("supportDithering", Boolean.FALSE);
            properties.put("supportMipmapping", Boolean.FALSE);
            properties.put("supportPerspectiveCorrection", Boolean.TRUE);
            properties.put("supportLocalCameraLighting", Boolean.TRUE);
            properties.put("maxLights", 8);
            properties.put("maxViewportWidth", 1024);
            properties.put("maxViewportHeight", 1024);
            properties.put("maxViewportDimension", 1024);
            properties.put("maxTextureDimension", 1024);
            properties.put("maxSpriteCropDimension", 1024);
            properties.put("maxTransformsPerVertex", 4);
            properties.put("numTextureUnits", 2);
        }
        return properties;
    }

    // ================================================================== target
    public void bindTarget(Object target) { bindTarget(target, true, 0); }

    public synchronized void bindTarget(Object target, boolean depthBuffer, int hints) {
        if (target == null) throw new NullPointerException();
        if (this.target != null) throw new IllegalStateException("a target is already bound");
        if (target instanceof javax.microedition.lcdui.Graphics) {
            javax.microedition.lcdui.Graphics g = (javax.microedition.lcdui.Graphics) target;
            java.awt.image.BufferedImage img = g.$target();
            tx = g.getTranslateX(); ty = g.getTranslateY();
            int w = img.getWidth(), h = img.getHeight();
            r.allocate(w, h, depthBuffer);
            int cx = Math.max(0, g.getClipX() + tx), cy = Math.max(0, g.getClipY() + ty);
            int cr = Math.min(w, g.getClipX() + tx + g.getClipWidth()), cb = Math.min(h, g.getClipY() + ty + g.getClipHeight());
            r.clipX = cx; r.clipY = cy; r.clipW = Math.max(0, cr - cx); r.clipH = Math.max(0, cb - cy);
            if (r.clipW > 0 && r.clipH > 0) {
                int[] row = new int[r.clipW * r.clipH];
                img.getRGB(cx, cy, r.clipW, r.clipH, row, 0, r.clipW);
                for (int y = 0; y < r.clipH; y++) System.arraycopy(row, y * r.clipW, r.color, (cy + y) * w + cx, r.clipW);
            }
            graphics = g; targetImage = null;
        } else if (target instanceof Image2D) {
            Image2D im = (Image2D) target;
            if (!im.isMutable()) throw new IllegalArgumentException("the target Image2D must be mutable");
            if (im.getFormat() != Image2D.RGB && im.getFormat() != Image2D.RGBA) throw new IllegalArgumentException("the target Image2D must be RGB or RGBA");
            tx = 0; ty = 0;
            r.allocate(im.getWidth(), im.getHeight(), depthBuffer);
            System.arraycopy(im.argb(), 0, r.color, 0, r.color.length);
            r.clipX = 0; r.clipY = 0; r.clipW = im.getWidth(); r.clipH = im.getHeight();
            targetImage = im; graphics = null;
        } else throw new IllegalArgumentException("target must be a javax.microedition.lcdui.Graphics or a mutable Image2D");
        java.util.Arrays.fill(r.depth, 1f);
        this.target = target; this.hints = hints; this.depthEnabled = depthBuffer;
        applyViewport();
        r.dnear = dnear; r.dfar = dfar;
    }

    public synchronized void releaseTarget() {
        if (target == null) return;
        try {
            if (graphics != null) {
                java.awt.image.BufferedImage img = graphics.$target();
                int w = r.tw;
                if (r.clipW > 0 && r.clipH > 0) {
                    int[] row = new int[r.clipW * r.clipH];
                    for (int y = 0; y < r.clipH; y++) System.arraycopy(r.color, (r.clipY + y) * w + r.clipX, row, y * r.clipW, r.clipW);
                    img.setRGB(r.clipX, r.clipY, r.clipW, r.clipH, row, 0, r.clipW);
                }
            } else if (targetImage != null) targetImage.writeArgb(r.color);
        } finally { target = null; graphics = null; targetImage = null; }
    }

    public Object getTarget() { return target; }
    public int getHints() { return hints; }
    public boolean isDepthBufferEnabled() { return depthEnabled; }

    // ================================================================== viewport and depth range
    public void setViewport(int x, int y, int w, int h) {
        if (w <= 0 || h <= 0 || w > 1024 || h > 1024) throw new IllegalArgumentException("viewport size must be in [1, 1024]");
        vpX = x; vpY = y; vpW = w; vpH = h; viewportSet = true;
        if (target != null) applyViewport();
    }
    private void applyViewport() {
        if (viewportSet) { r.vx = vpX + tx; r.vy = vpY + ty; r.vw = vpW; r.vh = vpH; }
        else { r.vx = r.clipX; r.vy = r.clipY; r.vw = Math.max(1, r.clipW); r.vh = Math.max(1, r.clipH); }
    }
    public int getViewportX() { return viewportSet ? vpX : r.clipX - tx; }
    public int getViewportY() { return viewportSet ? vpY : r.clipY - ty; }
    public int getViewportWidth() { return viewportSet ? vpW : r.clipW; }
    public int getViewportHeight() { return viewportSet ? vpH : r.clipH; }
    public void setDepthRange(float near, float far) {
        if (near < 0 || near > 1 || far < 0 || far > 1) throw new IllegalArgumentException("depth range must be in [0, 1]");
        dnear = near; dfar = far; r.dnear = near; r.dfar = far;
    }
    public float getDepthRangeNear() { return dnear; }
    public float getDepthRangeFar() { return dfar; }

    // ================================================================== camera and lights
    public void setCamera(Camera c, Transform t) {
        camera = c;
        if (c == null) { cameraToWorld.setIdentity(); r.view.setIdentity(); r.proj.setIdentity(); return; }
        cameraToWorld.set(t == null ? new Transform() : t);
        try { r.view.set(Transform.inverse(cameraToWorld)); } catch (ArithmeticException e) { throw new ArithmeticException("camera transform is not invertible"); }
        r.proj.set(c.projection());
    }
    public Camera getCamera(Transform t) { if (t != null) t.set(cameraToWorld); return camera; }

    public int addLight(Light l, Transform t) {
        if (l == null) throw new NullPointerException();
        if (r.lights.size() >= 8) throw new IllegalStateException("at most 8 lights");
        SoftRenderer.LightState s = new SoftRenderer.LightState();
        s.light = l; s.toWorld = t == null ? new Transform() : new Transform(t);
        r.lights.add(s);
        return r.lights.size() - 1;
    }
    public void setLight(int i, Light l, Transform t) {
        if (l == null) throw new NullPointerException();
        SoftRenderer.LightState s = r.lights.get(i);
        s.light = l; s.toWorld = t == null ? new Transform() : new Transform(t);
    }
    public void resetLights() { r.lights.clear(); }
    public int getLightCount() { return r.lights.size(); }
    public Light getLight(int i, Transform t) { SoftRenderer.LightState s = r.lights.get(i); if (t != null) t.set(s.toWorld); return s.light; }

    // ================================================================== rendering
    private void checkBound() { if (target == null) throw new IllegalStateException("no render target is bound"); }

    public void clear(Background bg) { checkBound(); r.clear(bg); }

    private static final class Item {
        Mesh mesh; int sub; Sprite3D sprite; Appearance ap; Transform world; float alpha; int layer; int order;
    }

    public void render(World world) {
        checkBound();
        if (world == null) throw new NullPointerException();
        Camera cam = world.getActiveCamera();
        if (cam == null) throw new IllegalStateException("the world has no active camera");
        if (cam.root() != world) throw new IllegalStateException("the active camera is not in the world");
        if (world.getBackground() != null) r.clear(world.getBackground());
        Transform camToWorld = new Transform();
        cam.getTransformTo(world, camToWorld);
        setCamera(cam, camToWorld);
        resetLights();
        collectLights(world, new Transform(), cam.getScope());
        List<Item> items = new ArrayList<>();
        // the world's own transform does not apply, only its descendants
        for (Node c : world.children) collect(c, new Transform(), 1f, cam.getScope(), items);
        draw(items);
    }

    private void collectLights(Group g, Transform parentToWorld, int camScope) {
        for (Node c : g.children) {
            if (!c.isRenderingEnabled()) continue;
            Transform w = Transform.mul(parentToWorld, c.composite());
            if (c instanceof Light && (c.getScope() & camScope) != 0 && r.lights.size() < 8) addLight((Light) c, w);
            if (c instanceof Group) collectLights((Group) c, w, camScope);
        }
    }

    public void render(Node node, Transform transform) {
        checkBound();
        if (node == null) throw new NullPointerException();
        if (node instanceof World || node instanceof Light || node instanceof Camera) throw new IllegalArgumentException("only Sprite3D, Mesh and Group nodes can be rendered");
        List<Item> items = new ArrayList<>();
        Transform base = transform == null ? new Transform() : new Transform(transform);
        collect(node, base, 1f, -1, items);
        draw(items);
    }

    public void render(VertexBuffer vertices, IndexBuffer triangles, Appearance appearance, Transform transform) { render(vertices, triangles, appearance, transform, -1); }

    public void render(VertexBuffer vertices, IndexBuffer triangles, Appearance appearance, Transform transform, int scope) {
        checkBound();
        if (vertices == null || triangles == null || appearance == null) throw new NullPointerException();
        if (vertices.getPositions(null) == null) throw new IllegalArgumentException("vertex buffer has no positions");
        SoftRenderer.MeshData d = r.prepare(null, vertices);
        r.drawSubmesh(d, triangles, appearance, transform == null ? new Transform() : transform, 1f);
    }

    /** Collects drawable items; parentToWorld maps the node's parent coordinates to world. */
    private void collect(Node n, Transform parentToWorld, float alpha, int camScope, List<Item> out) {
        if (!n.isRenderingEnabled() || (n.getScope() & camScope) == 0) return;
        float a = alpha * n.getAlphaFactor();
        Transform world = Transform.mul(parentToWorld, n.composite());
        if (n instanceof Mesh) {
            Mesh m = (Mesh) n;
            for (int i = 0; i < m.getSubmeshCount(); i++) {
                Appearance ap = m.getAppearance(i);
                if (ap == null) continue;
                Item it = new Item(); it.mesh = m; it.sub = i; it.ap = ap; it.world = world; it.alpha = a; it.layer = ap.getLayer(); it.order = out.size();
                out.add(it);
            }
        } else if (n instanceof Sprite3D) {
            Item it = new Item(); it.sprite = (Sprite3D) n; it.ap = ((Sprite3D) n).getAppearance(); it.world = world; it.alpha = a;
            it.layer = it.ap == null ? 0 : it.ap.getLayer(); it.order = out.size();
            out.add(it);
        } else if (n instanceof Group) for (Node c : ((Group) n).children) collect(c, world, a, camScope, out);
        // A SkinnedMesh's skeleton is its child in the scene graph. Renderable nodes hanging off a bone (a weapon in
        // the hand, a helmet...) are drawn together with the mesh, so descend into it as well.
        if (n instanceof SkinnedMesh) { Group sk = ((SkinnedMesh) n).getSkeleton(); if (sk != null) collect(sk, world, a, camScope, out); }
    }

    private void draw(List<Item> items) {
        items.sort((x, y) -> x.layer != y.layer ? Integer.compare(x.layer, y.layer) : Integer.compare(x.order, y.order));
        Map<Mesh, SoftRenderer.MeshData> cache = new IdentityHashMap<>();
        for (Item it : items) {
            if (it.sprite != null) { r.drawSprite(it.sprite, it.world, it.alpha); continue; }
            SoftRenderer.MeshData d = cache.get(it.mesh);
            if (d == null) { d = r.prepare(it.mesh, it.mesh.getVertexBuffer()); cache.put(it.mesh, d); }
            r.drawSubmesh(d, it.mesh.getIndexBuffer(it.sub), it.ap, it.world, it.alpha);
        }
    }
}
