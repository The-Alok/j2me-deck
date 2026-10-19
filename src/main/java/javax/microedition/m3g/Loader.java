// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 J2ME Deck contributors
package javax.microedition.m3g;

import j2medeck.ResourceBridge;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

/** Reads M3G (.m3g) files and plain image files (PNG/JPEG/GIF) into scene-graph objects. */
public class Loader {
    private static final byte[] IDENTIFIER = {(byte) 0xAB, 'J', 'S', 'R', '1', '8', '4', (byte) 0xBB, 0x0D, 0x0A, 0x1A, 0x0A};

    private Loader() {}

    public static Object3D[] load(String name) throws IOException {
        if (name == null) throw new NullPointerException();
        InputStream in = ResourceBridge.getResourceAsStream(name);
        if (in == null) throw new IOException("Resource not found: " + name);
        byte[] data;
        try (InputStream i = in) { data = i.readAllBytes(); }
        return load(data, 0);
    }

    public static Object3D[] load(byte[] data, int offset) throws IOException {
        if (data == null) throw new NullPointerException();
        if (offset < 0 || offset >= data.length) throw new IndexOutOfBoundsException("offset outside the data");
        if (!startsWith(data, offset)) return new Object3D[]{loadImage(data, offset)};
        return new Parser(data, offset + IDENTIFIER.length).parse();
    }

    private static boolean startsWith(byte[] d, int o) {
        if (d.length - o < IDENTIFIER.length) return false;
        for (int i = 0; i < IDENTIFIER.length; i++) if (d[o + i] != IDENTIFIER[i]) return false;
        return true;
    }

    private static Image2D loadImage(byte[] data, int offset) throws IOException {
        java.awt.image.BufferedImage b = javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(data, offset, data.length - offset));
        if (b == null) throw new IOException("Not an M3G file and not a supported image format");
        int w = b.getWidth(), h = b.getHeight();
        int[] px = b.getRGB(0, 0, w, h, null, 0, w);
        boolean alpha = false;
        for (int p : px) if ((p >>> 24) != 255) { alpha = true; break; }
        byte[] out = new byte[w * h * (alpha ? 4 : 3)];
        for (int i = 0; i < px.length; i++) {
            int o = i * (alpha ? 4 : 3);
            out[o] = (byte) (px[i] >> 16); out[o + 1] = (byte) (px[i] >> 8); out[o + 2] = (byte) px[i];
            if (alpha) out[o + 3] = (byte) (px[i] >>> 24);
        }
        return new Image2D(alpha ? Image2D.RGBA : Image2D.RGB, w, h, out);
    }

    // ======================================================================== file parser
    private static final class Parser {
        final byte[] file; int pos;
        final List<Object3D> objs = new ArrayList<>();

        Parser(byte[] file, int pos) { this.file = file; this.pos = pos; }

        Object3D[] parse() throws IOException {
            objs.add(null);        // index 0 is "null"; index 1 is the header object
            while (pos < file.length) {
                if (file.length - pos < 13) break;
                ByteBuffer b = ByteBuffer.wrap(file, pos, file.length - pos).order(ByteOrder.LITTLE_ENDIAN);
                int compression = b.get() & 255;
                long total = b.getInt() & 0xFFFFFFFFL, uncompressed = b.getInt() & 0xFFFFFFFFL;
                int dataLen = (int) total - 13;
                if (dataLen < 0 || pos + 9 + dataLen > file.length) throw new IOException("truncated section");
                byte[] section = new byte[dataLen];
                System.arraycopy(file, pos + 9, section, 0, dataLen);
                if (compression == 1) section = inflate(section, (int) uncompressed);
                else if (compression != 0) throw new IOException("unknown compression scheme " + compression);
                readObjects(section);
                pos += (int) total;            // section length includes header and the 4 checksum bytes
            }
            // roots = objects not referenced by any other object
            Set<Object3D> referenced = java.util.Collections.newSetFromMap(new IdentityHashMap<>());
            for (Object3D o : objs) if (o != null) { List<Object3D> refs = new ArrayList<>(); o.references(refs); for (Object3D r : refs) if (r != null && r != o) referenced.add(r); }
            List<Object3D> roots = new ArrayList<>();
            for (Object3D o : objs) if (o != null && !referenced.contains(o)) roots.add(o);
            return roots.toArray(new Object3D[0]);
        }

        static byte[] inflate(byte[] in, int size) throws IOException {
            Inflater inf = new Inflater();
            inf.setInput(in);
            ByteArrayOutputStream out = new ByteArrayOutputStream(Math.max(size, 64));
            byte[] buf = new byte[8192];
            try {
                while (!inf.finished()) {
                    int n = inf.inflate(buf);
                    if (n == 0 && (inf.needsInput() || inf.needsDictionary())) break;
                    out.write(buf, 0, n);
                }
            } catch (DataFormatException e) { throw new IOException("corrupt compressed section: " + e.getMessage()); }
            finally { inf.end(); }
            return out.toByteArray();
        }

        void readObjects(byte[] sec) throws IOException {
            ByteBuffer b = ByteBuffer.wrap(sec).order(ByteOrder.LITTLE_ENDIAN);
            while (b.remaining() >= 5) {
                int type = b.get() & 255;
                int len = b.getInt();
                if (len < 0 || len > b.remaining()) throw new IOException("object length out of range");
                int end = b.position() + len;
                ByteBuffer body = ByteBuffer.wrap(sec, b.position(), len).order(ByteOrder.LITTLE_ENDIAN);
                Object3D o = readObject(type, body);
                objs.add(o);                    // header and external references add null placeholders to keep indices aligned
                b.position(end);
            }
        }

        // ---- primitive readers
        static int u8(ByteBuffer b) { return b.get() & 255; }
        static boolean bool(ByteBuffer b) { return b.get() != 0; }
        static long u32(ByteBuffer b) { return b.getInt() & 0xFFFFFFFFL; }
        static int rgb(ByteBuffer b) { int r = u8(b), g = u8(b), bl = u8(b); return (r << 16) | (g << 8) | bl; }
        static int rgba(ByteBuffer b) { int r = u8(b), g = u8(b), bl = u8(b), a = u8(b); return (a << 24) | (r << 16) | (g << 8) | bl; }
        static String cstr(ByteBuffer b) {
            ByteArrayOutputStream o = new ByteArrayOutputStream();
            while (b.hasRemaining()) { int c = b.get(); if (c == 0) break; o.write(c); }
            return new String(o.toByteArray(), java.nio.charset.StandardCharsets.UTF_8);
        }
        @SuppressWarnings("unchecked") <T extends Object3D> T ref(ByteBuffer b, Class<T> type) throws IOException {
            long idx = u32(b);
            if (idx == 0) return null;
            if (idx >= objs.size()) throw new IOException("reference to an object that has not been read yet: " + idx);
            Object3D o = objs.get((int) idx);
            if (o == null) return null;
            if (!type.isInstance(o)) throw new IOException("object " + idx + " has the wrong type: " + o.getClass().getSimpleName() + ", expected " + type.getSimpleName());
            return (T) o;
        }

        /** Fields common to every object: user id, animation tracks, user parameters. */
        void object3D(ByteBuffer b, Object3D o) throws IOException {
            o.setUserID((int) u32(b));
            long tracks = u32(b);
            for (long i = 0; i < tracks; i++) {
                AnimationTrack t = ref(b, AnimationTrack.class);
                if (t != null) try { o.addAnimationTrack(t); } catch (IllegalArgumentException ignored) { /* property not supported on this class */ }
            }
            long params = u32(b);
            for (long i = 0; i < params; i++) { u32(b); long l = u32(b); b.position(b.position() + (int) l); }
        }

        void transformable(ByteBuffer b, Transformable t) {
            if (bool(b)) {
                t.setTranslation(b.getFloat(), b.getFloat(), b.getFloat());
                t.setScale(b.getFloat(), b.getFloat(), b.getFloat());
                float angle = b.getFloat(), ax = b.getFloat(), ay = b.getFloat(), az = b.getFloat();
                if (angle != 0 && (ax != 0 || ay != 0 || az != 0)) t.setOrientation(angle, ax, ay, az);
            }
            if (bool(b)) { float[] m = new float[16]; for (int i = 0; i < 16; i++) m[i] = b.getFloat(); Transform tr = new Transform(); tr.set(m); t.setTransform(tr); }
        }

        void node(ByteBuffer b, Node n) throws IOException {
            n.setRenderingEnable(bool(b));
            n.setPickingEnable(bool(b));
            n.setAlphaFactor(u8(b) / 255f);
            n.setScope((int) u32(b));
            if (bool(b)) {
                int zt = u8(b), yt = u8(b);
                Node zr = ref(b, Node.class), yr = ref(b, Node.class);
                try { n.setAlignment(zr, zt, yr, yt); } catch (IllegalArgumentException ignored) { }
            }
        }

        Object3D readObject(int type, ByteBuffer b) throws IOException {
            switch (type) {
                case 0: { b.get(); b.get(); bool(b); u32(b); u32(b); cstr(b); return null; }       // header
                case 255: { cstr(b); return null; }                                                  // external reference: unsupported, left null
                case 1: { // AnimationController
                    AnimationController c = new AnimationController(); object3D(b, c);
                    float speed = b.getFloat(), weight = b.getFloat(); int s = b.getInt(), e = b.getInt(); float refSeq = b.getFloat(); int refWorld = b.getInt();
                    c.setWeight(weight); c.setActiveInterval(Math.min(s, e), Math.max(s, e)); c.setPosition(refSeq, refWorld); c.setSpeed(speed, refWorld);
                    return c;
                }
                case 2: { // AnimationTrack: Object3D, keyframe sequence, controller, property id
                    Group scratch = new Group(); object3D(b, scratch);
                    KeyframeSequence ks = ref(b, KeyframeSequence.class); AnimationController ctrl = ref(b, AnimationController.class); int property = (int) u32(b);
                    if (ks == null) throw new IOException("animation track without a keyframe sequence");
                    AnimationTrack t = new AnimationTrack(ks, property); t.setController(ctrl); t.setUserID(scratch.getUserID());
                    return t;
                }
                default: return readOther(type, b);
            }
        }

        Object3D readOther(int type, ByteBuffer b) throws IOException {
            switch (type) {
                case 3: { Appearance a = new Appearance(); object3D(b, a);
                    a.setLayer(b.get()); a.setCompositingMode(ref(b, CompositingMode.class)); a.setFog(ref(b, Fog.class)); a.setPolygonMode(ref(b, PolygonMode.class)); a.setMaterial(ref(b, Material.class));
                    long n = u32(b); for (int i = 0; i < n; i++) { Texture2D t = ref(b, Texture2D.class); if (i < 8) a.setTexture(i, t); }
                    return a; }
                case 4: { Background bg = new Background(); object3D(b, bg);
                    bg.setColor(rgba(b)); Image2D img = ref(b, Image2D.class);
                    int mx = u8(b), my = u8(b), cx = b.getInt(), cy = b.getInt(), cw = b.getInt(), ch = b.getInt();
                    boolean depth = bool(b), col = bool(b);
                    if (img != null) bg.setImage(img);
                    bg.setImageMode(mx, my); bg.setCrop(cx, cy, cw, ch); bg.setDepthClearEnable(depth); bg.setColorClearEnable(col);
                    return bg; }
                case 5: { Camera c = new Camera(); object3D(b, c); transformable(b, c); node(b, c);
                    int pt = u8(b);
                    if (pt == Camera.GENERIC) { float[] m = new float[16]; for (int i = 0; i < 16; i++) m[i] = b.getFloat(); Transform t = new Transform(); t.set(m); c.setGeneric(t); }
                    else { float p0 = b.getFloat(), asp = b.getFloat(), near = b.getFloat(), far = b.getFloat();
                        if (pt == Camera.PARALLEL) c.setParallel(p0, asp, near, far); else c.setPerspective(p0, asp, near, far); }
                    return c; }
                case 6: { CompositingMode c = new CompositingMode(); object3D(b, c);
                    c.setDepthTestEnable(bool(b)); c.setDepthWriteEnable(bool(b)); c.setColorWriteEnable(bool(b)); c.setAlphaWriteEnable(bool(b));
                    c.setBlending(u8(b)); c.setAlphaThreshold(u8(b) / 255f); c.setDepthOffset(b.getFloat(), b.getFloat());
                    return c; }
                case 7: { Fog f = new Fog(); object3D(b, f);
                    f.setColor(rgb(b)); int mode = u8(b); f.setMode(mode);
                    if (mode == Fog.EXPONENTIAL) f.setDensity(b.getFloat()); else f.setLinear(b.getFloat(), b.getFloat());
                    return f; }
                case 8: { PolygonMode p = new PolygonMode(); object3D(b, p);
                    p.setCulling(u8(b)); p.setShading(u8(b)); p.setWinding(u8(b));
                    p.setTwoSidedLightingEnable(bool(b)); p.setLocalCameraLightingEnable(bool(b)); p.setPerspectiveCorrectionEnable(bool(b));
                    return p; }
                case 9: { Group g = new Group(); object3D(b, g); transformable(b, g); node(b, g);
                    long n = u32(b); for (int i = 0; i < n; i++) { Node c = ref(b, Node.class); if (c != null) g.addChild(c); }
                    return g; }
                case 10: { // Image2D
                    Group scratch = new Group(); object3D(b, scratch);
                    int format = u8(b); boolean mutable = bool(b); int w = (int) u32(b), h = (int) u32(b);
                    Image2D im;
                    if (mutable) im = new Image2D(format, w, h);
                    else {
                        int pl = (int) u32(b); byte[] pal = new byte[pl]; b.get(pal);
                        int px = (int) u32(b); byte[] data = new byte[px]; b.get(data);
                        im = pl > 0 ? new Image2D(format, w, h, data, pal) : new Image2D(format, w, h, data);
                    }
                    im.setUserID(scratch.getUserID());
                    return im; }
                case 11: { // TriangleStripArray: encodings 0/1/2 = implicit sequence from a first index, 128/129/130 = explicit indices
                    Group scratch = new Group(); object3D(b, scratch);
                    int enc = u8(b);
                    if (enc < 128) {
                        int first = enc == 0 ? (int) u32(b) : enc == 1 ? u8(b) : b.getShort() & 0xFFFF;
                        int sl = (int) u32(b); int[] lens = new int[sl]; for (int i = 0; i < sl; i++) lens[i] = (int) u32(b);
                        TriangleStripArray t = new TriangleStripArray(first, lens); t.setUserID(scratch.getUserID()); return t;
                    }
                    int count = (int) u32(b); int[] idx = new int[count];
                    for (int i = 0; i < count; i++) idx[i] = enc == 128 ? (int) u32(b) : enc == 129 ? u8(b) : b.getShort() & 0xFFFF;
                    return strips(b, idx, scratch); }
                case 12: { Light l = new Light(); object3D(b, l); transformable(b, l); node(b, l);
                    float c = b.getFloat(), li = b.getFloat(), q = b.getFloat(); l.setAttenuation(c, li, q);
                    l.setColor(rgb(b)); l.setMode(u8(b)); l.setIntensity(b.getFloat()); l.setSpotAngle(b.getFloat()); l.setSpotExponent(b.getFloat());
                    return l; }
                case 13: { Material m = new Material(); object3D(b, m);
                    m.setColor(Material.AMBIENT, rgb(b)); m.setColor(Material.DIFFUSE, rgba(b)); m.setColor(Material.EMISSIVE, rgb(b)); m.setColor(Material.SPECULAR, rgb(b));
                    m.setShininess(b.getFloat()); m.setVertexColorTrackingEnable(bool(b));
                    return m; }
                case 14: return mesh(b, 14);
                case 15: return mesh(b, 15);
                case 16: return mesh(b, 16);
                case 17: { // Texture2D: Object3D, Transformable, image, blend colour, blending, wrapS, wrapT, levelFilter, imageFilter
                    Group scratch = new Group(); object3D(b, scratch);
                    Group tr = new Group(); transformable(b, tr);
                    Image2D img = ref(b, Image2D.class);
                    if (img == null) throw new IOException("texture without an image");
                    Texture2D tx = new Texture2D(img);
                    tx.setUserID(scratch.getUserID());
                    tx.setTranslation(tr.tx, tr.ty, tr.tz); tx.setScale(tr.sx, tr.sy, tr.sz); tx.qx = tr.qx; tx.qy = tr.qy; tx.qz = tr.qz; tx.qw = tr.qw; tx.setTransform(tr.matrix);
                    tx.setBlendColor(rgb(b)); tx.setBlending(u8(b)); int ws = u8(b), wt = u8(b); tx.setWrapping(ws, wt); int lf = u8(b), imf = u8(b); tx.setFiltering(lf, imf);
                    return tx; }
                case 18: { Group scratch = new Group(); object3D(b, scratch); Group tr = new Group(); transformable(b, tr); Group nd = new Group(); node(b, nd);
                    Image2D img = ref(b, Image2D.class); Appearance ap = ref(b, Appearance.class); boolean scaled = bool(b);
                    int cx = b.getInt(), cy = b.getInt(), cw = b.getInt(), ch = b.getInt();
                    Sprite3D s = new Sprite3D(scaled, img, ap); s.setCrop(cx, cy, cw, ch); s.setUserID(scratch.getUserID());
                    copyNode(tr, nd, s); return s; }
                case 19: return keyframes(b);
                case 20: return vertexArray(b);
                case 21: return vertexBuffer(b);
                case 22: { World w = new World(); object3D(b, w); transformable(b, w); node(b, w);
                    long n = u32(b); for (int i = 0; i < n; i++) { Node c = ref(b, Node.class); if (c != null) w.addChild(c); }
                    Camera cam = ref(b, Camera.class); Background bg = ref(b, Background.class);
                    if (cam != null) w.setActiveCamera(cam);
                    w.setBackground(bg);
                    return w; }
                default: throw new IOException("unknown object type " + type);
            }
        }

        void copyNode(Transformable t, Node nd, Node dst) {
            dst.setTranslation(t.tx, t.ty, t.tz); dst.setScale(t.sx, t.sy, t.sz); dst.qx = t.qx; dst.qy = t.qy; dst.qz = t.qz; dst.qw = t.qw; dst.setTransform(t.matrix);
            dst.setRenderingEnable(nd.isRenderingEnabled()); dst.setPickingEnable(nd.isPickingEnabled()); dst.setAlphaFactor(nd.getAlphaFactor()); dst.setScope(nd.getScope());
        }

        Object3D strips(ByteBuffer c, int[] idx, Object3D tmp) {
            int sl = (int) u32(c); int[] lens = new int[sl]; for (int i = 0; i < sl; i++) lens[i] = (int) u32(c);
            TriangleStripArray t = new TriangleStripArray(idx, lens); t.setUserID(tmp.getUserID()); return t;
        }

        Object3D mesh(ByteBuffer b, int type) throws IOException {
            Group scratch = new Group(); object3D(b, scratch);
            Group tr = new Group(); transformable(b, tr);
            Group nd = new Group(); node(b, nd);
            VertexBuffer vb = ref(b, VertexBuffer.class);
            long n = u32(b);
            IndexBuffer[] ib = new IndexBuffer[(int) n]; Appearance[] ap = new Appearance[(int) n];
            for (int i = 0; i < n; i++) { ib[i] = ref(b, IndexBuffer.class); ap[i] = ref(b, Appearance.class); }
            Mesh m;
            if (type == 14) m = new Mesh(vb, ib, ap);
            else if (type == 16) {
                Group skel = ref(b, Group.class);
                m = new SkinnedMesh(vb, ib, ap, skel);
                long tc = u32(b);
                for (int i = 0; i < tc; i++) { Node bone = ref(b, Node.class); int first = (int) u32(b), count = (int) u32(b), weight = b.getInt(); if (bone != null) try { ((SkinnedMesh) m).addTransform(bone, weight, first, count); } catch (RuntimeException ignored) { } }
            } else {
                long tc = u32(b); VertexBuffer[] targets = new VertexBuffer[(int) tc]; float[] w = new float[(int) tc];
                for (int i = 0; i < tc; i++) { targets[i] = ref(b, VertexBuffer.class); w[i] = b.getFloat(); }
                m = new MorphingMesh(vb, targets, ib, ap); ((MorphingMesh) m).setWeights(w);
            }
            m.setUserID(scratch.getUserID());
            for (int i = 0; i < scratch.getAnimationTrackCount(); i++) m.addAnimationTrack(scratch.getAnimationTrack(i));
            copyNode(tr, nd, m);
            return m;
        }

        Object3D vertexArray(ByteBuffer b) throws IOException {
            Group scratch = new Group(); object3D(b, scratch);
            int size = u8(b), comps = u8(b), enc = u8(b), n = b.getShort() & 0xFFFF;
            VertexArray va = new VertexArray(n, comps, size);
            if (size == 1) {
                byte[] d = new byte[n * comps]; b.get(d);
                if (enc == 1) for (int v = 1; v < n; v++) for (int c = 0; c < comps; c++) d[v * comps + c] += d[(v - 1) * comps + c];
                va.set(0, n, d);
            } else {
                short[] d = new short[n * comps]; for (int i = 0; i < d.length; i++) d[i] = b.getShort();
                if (enc == 1) for (int v = 1; v < n; v++) for (int c = 0; c < comps; c++) d[v * comps + c] += d[(v - 1) * comps + c];
                va.set(0, n, d);
            }
            va.setUserID(scratch.getUserID());
            return va;
        }

        Object3D vertexBuffer(ByteBuffer b) throws IOException {
            Group scratch = new Group(); object3D(b, scratch);
            VertexBuffer vb = new VertexBuffer();
            vb.setDefaultColor(rgba(b));
            VertexArray pos = ref(b, VertexArray.class);
            float[] bias = {b.getFloat(), b.getFloat(), b.getFloat()}; float scale = b.getFloat();
            VertexArray nor = ref(b, VertexArray.class), col = ref(b, VertexArray.class);
            if (pos != null) vb.setPositions(pos, scale, bias);
            if (nor != null) vb.setNormals(nor);
            if (col != null) vb.setColors(col);
            long tc = u32(b);
            for (int i = 0; i < tc; i++) {
                VertexArray t = ref(b, VertexArray.class);
                float[] tb = {b.getFloat(), b.getFloat(), b.getFloat()}; float ts = b.getFloat();
                if (t != null && i < 8) vb.setTexCoords(i, t, ts, tb);
            }
            vb.setUserID(scratch.getUserID());
            return vb;
        }

        Object3D keyframes(ByteBuffer b) throws IOException {
            Group scratch = new Group(); object3D(b, scratch);
            int interp = u8(b), repeat = u8(b), enc = u8(b);
            int duration = (int) u32(b), first = (int) u32(b), last = (int) u32(b), comps = (int) u32(b), count = (int) u32(b);
            KeyframeSequence ks = new KeyframeSequence(count, comps, interp);
            float[] bias = new float[comps], scale = new float[comps];
            if (enc != 0) { for (int i = 0; i < comps; i++) bias[i] = b.getFloat(); for (int i = 0; i < comps; i++) scale[i] = b.getFloat(); }
            float[] v = new float[comps];
            for (int k = 0; k < count; k++) {
                int time = (int) u32(b);
                for (int c = 0; c < comps; c++) {
                    if (enc == 0) v[c] = b.getFloat();
                    else if (enc == 1) v[c] = bias[c] + scale[c] * (u8(b) / 255f);
                    else v[c] = bias[c] + scale[c] * ((b.getShort() & 0xFFFF) / 65535f);
                }
                ks.setKeyframe(k, time, v);
            }
            ks.setDuration(Math.max(1, duration)); ks.setRepeatMode(repeat); ks.setValidRange(first, last);
            ks.setUserID(scratch.getUserID());
            return ks;
        }
    }
}
