// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 J2ME Deck contributors
package j2medeck.gl;

import j2medeck.GlControl;
import j2medeck.GlHost;
import j2medeck.GlOverlay;
import j2medeck.Settings;

import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.image.BufferedImage;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static java.lang.foreign.ValueLayout.JAVA_FLOAT;
import static java.lang.foreign.ValueLayout.JAVA_INT;

/**
 * A heavyweight AWT canvas that is drawn with OpenGL ES 2 instead of Java2D. It lives inside the normal Swing window (so keyboard, mouse,
 * focus, fullscreen and the soft-key bar keep working unchanged); only the pixels of this one rectangle come from the GPU.
 *
 * A dedicated render thread owns the EGL context. It wakes only when a redraw was requested (a new game frame, an overlay, a resize), uploads the
 * game framebuffer as a texture, draws one quad with the scaling shader and swaps with vsync. A static picture therefore costs no CPU or GPU.
 */
public final class GlSurface extends java.awt.Canvas implements GlControl {
    private static final String VERTEX = String.join("\n",
            "attribute vec2 aPos;",
            "attribute vec2 aUv;",
            "uniform vec2 uSurf;",
            "varying vec2 vUv;",
            "void main() {",
            "  gl_Position = vec4(aPos.x / uSurf.x * 2.0 - 1.0, 1.0 - aPos.y / uSurf.y * 2.0, 0.0, 1.0);",
            "  vUv = aUv;",
            "}");

    /** Game picture: optional sharp-bilinear sampling and LCD grid. The framebuffer arrives as BGRX bytes, hence the swizzle. */
    private static final String FRAG_GAME = String.join("\n",
            "#ifdef GL_FRAGMENT_PRECISION_HIGH",
            "precision highp float;",
            "#else",
            "precision mediump float;",
            "#endif",
            "uniform sampler2D uTex;",
            "uniform vec2 uTexSize;",
            "uniform vec2 uScale;",
            "uniform float uSharp;",
            "uniform vec2 uGrid;",
            "varying vec2 vUv;",
            "void main() {",
            "  vec2 uv = vUv;",
            "  vec2 texel = uv * uTexSize;",
            "  if (uSharp > 0.5) {",
            "    vec2 sc = max(uScale, vec2(1.0));",
            "    vec2 fl = floor(texel);",
            "    vec2 cd = (texel - fl) - 0.5;",
            "    vec2 range = 0.5 - 0.5 / sc;",
            "    vec2 f = (cd - clamp(cd, -range, range)) * sc + 0.5;",
            "    uv = (fl + f) / uTexSize;",
            "  }",
            "  vec3 c = texture2D(uTex, uv).bgr;",
            "  if (uGrid.x > 0.0) {",
            "    vec2 fr = fract(texel) * uScale;",
            "    if (fr.x < uGrid.y || fr.y < uGrid.y) c *= (1.0 - uGrid.x);",
            "  }",
            "  gl_FragColor = vec4(c, 1.0);",
            "}");

    /** Overlays: straight-alpha ARGB images (BGRA bytes). */
    private static final String FRAG_OVERLAY = String.join("\n",
            "precision mediump float;",
            "uniform sampler2D uTex;",
            "varying vec2 vUv;",
            "void main() { gl_FragColor = texture2D(uTex, vUv).bgra; }");

    private static final boolean DEBUG = System.getenv("J2ME_GL_DEBUG") != null;
    /** Test hook: makes the render thread fail after a few frames so the fall-back to Swing can be exercised. */
    private static final boolean TEST_FAIL = System.getenv("J2ME_GL_TEST_FAIL") != null;

    /** One X connection and one initialised EGL display for the whole process: a game may create several canvases, each with its own surface. */
    private static final class Shared {
        final MemorySegment xdpy, edpy;
        final MemorySegment[] configs;
        final String info;
        Shared(MemorySegment xdpy, MemorySegment edpy, MemorySegment[] configs, String info) { this.xdpy = xdpy; this.edpy = edpy; this.configs = configs; this.info = info; }
    }
    private static Shared shared;
    /** X11 calls are serialised: Xlib is only safe across threads when XInitThreads ran, and we do not control that. */
    private static final Object X_LOCK = new Object();
    private static boolean infoPrinted;

    private static synchronized Shared shared() {
        if (shared != null) return shared;
        MemorySegment xdpy = X11.openDisplay();
        if (Ffm.isNull(xdpy)) throw new IllegalStateException("cannot open the X display");
        MemorySegment edpy = null;
        try {
            edpy = Egl.getX11Display(xdpy);
            if (edpy == null) throw new IllegalStateException("no EGL display for X11");
            String ver = Egl.initialize(edpy);
            if (!Egl.bindEs()) throw new IllegalStateException("eglBindAPI(OpenGL ES) failed");
            MemorySegment[] configs = Egl.chooseConfigs(edpy);
            if (configs.length == 0) throw new IllegalStateException("no OpenGL ES 2 window configuration");
            shared = new Shared(xdpy, edpy, configs, "EGL " + ver + " " + Egl.queryString(edpy, Egl.EGL_VENDOR));
            return shared;
        } catch (RuntimeException | Error e) {
            try { if (edpy != null) Egl.terminate(edpy); } catch (Throwable ignored) {}
            X11.closeDisplay(xdpy);
            throw e;
        }
    }

    private final GlHost host;
    private final int fw, fh;
    private final Object lock = new Object();
    private final Thread thread;

    private final MemorySegment xdpy, edpy;
    private final MemorySegment[] configs;
    private final String eglInfo;

    // state guarded by lock
    private boolean dirty = true, closed, wantSurface;
    private long requestedSeq, presentedSeq;
    private volatile boolean surfaceUp;
    /** Start time (System.nanoTime) of the eglSwapBuffers call in progress, 0 when none. Watched by the watchdog thread. */
    private volatile long swapStartNanos;
    /** The render thread is stuck inside EGL and cannot be waited for any more. */
    private volatile boolean stalled;
    private static final long SWAP_STALL_NANOS = 2_000_000_000L;

    // render-thread only
    private MemorySegment ctx, surface, surfaceConfig;
    private long surfaceXid;
    private int failures;
    private boolean glReady;
    private int gameProg, overlayProg, vbo, gameTex;
    private int uGameSurf, uGameTexSize, uGameScale, uGameSharp, uGameGrid, uGameTex, uOvSurf, uOvTex;
    private int texFilter = -1;
    private Arena res;
    private MemorySegment frameSeg, vertSeg;
    private int[] frameInts;
    private final Map<String, int[]> overlayTex = new HashMap<>();   // key -> {texture, w, h, lastUsedFrame}
    private long frameNo;
    private String glInfoLine;

    /** Loads the libraries, opens an X connection and initialises EGL. Throws when any of that is not possible (the caller then keeps Swing). */
    public GlSurface(GlHost host) {
        this.host = host;
        this.fw = host.frameWidth();
        this.fh = host.frameHeight();
        setBackground(Color.BLACK);
        setIgnoreRepaint(true);
        setFocusable(true);
        setFocusTraversalKeysEnabled(false);
        Shared sh = shared();
        xdpy = sh.xdpy; edpy = sh.edpy; configs = sh.configs; eglInfo = sh.info;
        addComponentListener(new ComponentAdapter() {
            @Override public void componentResized(ComponentEvent e) { requestFrame(); }
            @Override public void componentShown(ComponentEvent e) { requestFrame(); }
        });
        thread = new Thread(this::run, "gles-render");
        thread.setDaemon(true);
        thread.start();
        Thread wd = new Thread(this::watchdog, "gles-watchdog");
        wd.setDaemon(true);
        wd.start();
    }

    /**
     * eglSwapBuffers can block for good when the compositor never completes the frame (seen as a game that freezes on its first screen under
     * XWayland/gamescope). The render thread cannot be interrupted inside EGL, so this thread notices and hands the window back to Swing.
     */
    private void watchdog() {
        try {
            while (true) {
                Thread.sleep(250);
                synchronized (lock) { if (closed) return; }
                long s = swapStartNanos;
                if (s != 0 && System.nanoTime() - s > SWAP_STALL_NANOS) {
                    stalled = true;
                    synchronized (lock) { closed = true; lock.notifyAll(); }
                    host.glFailed("eglSwapBuffers did not return for " + (SWAP_STALL_NANOS / 1_000_000_000L) + " s (the compositor is not completing frames)");
                    return;
                }
            }
        } catch (InterruptedException ignored) {
        }
    }

    // ------------------------------------------------------------------ AWT side
    @Override public void addNotify() {
        super.addNotify();
        synchronized (lock) { wantSurface = true; dirty = true; requestedSeq++; lock.notifyAll(); }
    }

    /** The native window goes away right after this returns, so the EGL surface must be gone first. */
    @Override public void removeNotify() {
        synchronized (lock) {
            wantSurface = false;
            lock.notifyAll();
            long deadline = System.nanoTime() + 2_000_000_000L;
            while (surfaceUp && !closed && !stalled) {
                long left = deadline - System.nanoTime();
                if (left <= 0) break;
                try { lock.wait(Math.max(1, left / 1_000_000)); } catch (InterruptedException e) { Thread.currentThread().interrupt(); break; }
            }
        }
        super.removeNotify();
    }

    @Override public void paint(Graphics g) { requestFrame(); }
    @Override public void update(Graphics g) { requestFrame(); }

    @Override public void requestFrame() {
        synchronized (lock) { dirty = true; requestedSeq++; lock.notifyAll(); }
    }

    @Override public boolean awaitPresented(long ms) {
        long deadline = System.nanoTime() + ms * 1_000_000L;
        synchronized (lock) {
            long target = requestedSeq;
            while (presentedSeq < target && !closed && wantSurface) {
                long left = deadline - System.nanoTime();
                if (left <= 0) return false;
                try { lock.wait(Math.max(1, left / 1_000_000)); } catch (InterruptedException e) { Thread.currentThread().interrupt(); return false; }
            }
            return presentedSeq >= target;
        }
    }

    @Override public void shutdown() {
        synchronized (lock) { closed = true; lock.notifyAll(); }
        if (Thread.currentThread() != thread && !stalled) { try { thread.join(1500); } catch (InterruptedException e) { Thread.currentThread().interrupt(); } }
    }

    /** Native X window id of this canvas, or 0 while it has no peer yet. */
    private long xid() throws ReflectiveOperationException {
        Field f = Component.class.getDeclaredField("peer");
        f.setAccessible(true);
        Object peer = f.get(this);
        if (peer == null) return 0;
        Method m = peer.getClass().getMethod("getWindow");
        m.setAccessible(true);
        return ((Number) m.invoke(peer)).longValue();
    }

    // ------------------------------------------------------------------ render thread
    private void run() {
        try {
            while (true) {
                boolean draw;
                synchronized (lock) {
                    while (!closed && !dirty && wantSurface == surfaceUp) {
                        try { lock.wait(); } catch (InterruptedException e) { return; }
                    }
                    if (closed) break;
                    draw = dirty;
                }
                boolean want;
                synchronized (lock) { want = wantSurface; }
                if (!want) {
                    if (surfaceUp) releaseSurface();
                    synchronized (lock) { dirty = false; presentedSeq = requestedSeq; lock.notifyAll(); }
                    continue;
                }
                long id = xid();
                if (id == 0) { Thread.sleep(10); continue; }                  // the window does not exist yet
                if (!surfaceUp || id != surfaceXid) {
                    if (surfaceUp) releaseSurface();
                    if (!createSurface(id)) { Thread.sleep(20); if (++failures > 50) throw new IllegalStateException("cannot create an EGL window surface"); continue; }
                    failures = 0;
                    draw = true;
                }
                if (draw) {
                    long seq;
                    synchronized (lock) { dirty = false; seq = requestedSeq; }
                    if (!drawFrame()) {
                        if (++failures > 5) throw new IllegalStateException("eglSwapBuffers keeps failing (0x" + Integer.toHexString(Egl.getError()) + ")");
                        releaseSurface();                                        // window probably recreated; try again
                        requestFrame();
                        continue;
                    }
                    failures = 0;
                    synchronized (lock) { if (seq > presentedSeq) presentedSeq = seq; lock.notifyAll(); }
                }
            }
        } catch (InterruptedException ignored) {
        } catch (Throwable t) {
            if (DEBUG) t.printStackTrace();
            synchronized (lock) { closed = true; lock.notifyAll(); }
            try { releaseSurface(); } catch (Throwable ignored) {}
            host.glFailed(t.toString());
        } finally {
            try { cleanup(); } catch (Throwable ignored) {}
        }
    }

    private boolean createSurface(long id) {
        int[] size = new int[2];
        long visual;
        synchronized (X_LOCK) { visual = X11.windowVisualId(xdpy, id, size); }
        MemorySegment cfg = configs[0];
        if (visual != 0) for (MemorySegment c : configs) if (Egl.configAttrib(edpy, c, Egl.EGL_NATIVE_VISUAL_ID) == (int) visual) { cfg = c; break; }
        MemorySegment s = Egl.createWindowSurface(edpy, cfg, id);
        if (s == null) { if (DEBUG) System.err.println("[gles] eglCreateWindowSurface failed 0x" + Integer.toHexString(Egl.getError()) + " visual=" + visual); return false; }
        if (ctx == null) {
            ctx = Egl.createContext(edpy, cfg);
            if (ctx == null) { Egl.destroySurface(edpy, s); throw new IllegalStateException("eglCreateContext failed (0x" + Integer.toHexString(Egl.getError()) + ")"); }
        }
        if (!Egl.makeCurrent(edpy, s, s, ctx)) {
            int e = Egl.getError();
            Egl.destroySurface(edpy, s);
            throw new IllegalStateException("eglMakeCurrent failed (0x" + Integer.toHexString(e) + ")");
        }
        Egl.swapInterval(edpy, Settings.gameMode ? 0 : 1);          // gamescope composites and paces frames itself; waiting for vblank here can block
        surface = s; surfaceConfig = cfg; surfaceXid = id;
        if (!glReady) initGl();
        synchronized (lock) { surfaceUp = true; lock.notifyAll(); }
        return true;
    }

    private void releaseSurface() {
        if (surface != null) {
            try { Egl.makeCurrent(edpy, MemorySegment.NULL, MemorySegment.NULL, MemorySegment.NULL); } catch (Throwable ignored) {}
            try { Egl.destroySurface(edpy, surface); } catch (Throwable ignored) {}
            surface = null;
        }
        synchronized (lock) { surfaceUp = false; lock.notifyAll(); }
        // the context keeps its textures; makeCurrent is redone for the next surface
    }

    private void initGl() {
        res = Arena.ofShared();
        glInfoLine = Gles.getString(Gles.GL_RENDERER) + " / " + Gles.getString(Gles.GL_VERSION);
        synchronized (GlSurface.class) {
            if (!infoPrinted) { infoPrinted = true; System.err.println("[j2me] OpenGL ES output: " + eglInfo + ", " + glInfoLine); }
        }
        gameProg = Gles.createProgram(VERTEX, FRAG_GAME, new String[]{"aPos", "aUv"});
        overlayProg = Gles.createProgram(VERTEX, FRAG_OVERLAY, new String[]{"aPos", "aUv"});
        uGameSurf = Gles.uniform(gameProg, "uSurf"); uGameTexSize = Gles.uniform(gameProg, "uTexSize"); uGameScale = Gles.uniform(gameProg, "uScale");
        uGameSharp = Gles.uniform(gameProg, "uSharp"); uGameGrid = Gles.uniform(gameProg, "uGrid"); uGameTex = Gles.uniform(gameProg, "uTex");
        uOvSurf = Gles.uniform(overlayProg, "uSurf"); uOvTex = Gles.uniform(overlayProg, "uTex");
        vbo = Gles.genBuffer();
        vertSeg = res.allocate(16 * 4L, 4);
        Gles.disable(Gles.GL_DEPTH_TEST);
        Gles.pixelStore(Gles.GL_UNPACK_ALIGNMENT, 4);
        gameTex = Gles.genTexture();
        Gles.bindTexture(gameTex);
        Gles.texParameter(Gles.GL_TEXTURE_WRAP_S, Gles.GL_CLAMP_TO_EDGE);
        Gles.texParameter(Gles.GL_TEXTURE_WRAP_T, Gles.GL_CLAMP_TO_EDGE);
        Gles.texParameter(Gles.GL_TEXTURE_MIN_FILTER, Gles.GL_NEAREST);
        Gles.texParameter(Gles.GL_TEXTURE_MAG_FILTER, Gles.GL_NEAREST);
        Gles.texImage(fw, fh, null);
        frameSeg = res.allocate((long) fw * fh * 4, 4);
        frameInts = new int[fw * fh];
        glReady = true;
        checkError("init");
    }

    private void checkError(String where) {
        if (!DEBUG) return;
        int e;
        while ((e = Gles.getError()) != 0) System.err.println("[gles] GL error 0x" + Integer.toHexString(e) + " after " + where);
    }

    /** Destination rectangle of the game picture inside a surface of the given size (same rules as the Swing view). */
    static int[] destRect(int cw, int ch, int w, int h, int scaleMode) {
        if (scaleMode == Settings.SCALE_STRETCH) return new int[]{0, 0, cw, ch};
        double s = Math.min(cw / (double) w, ch / (double) h);
        if (scaleMode == Settings.SCALE_INTEGER) s = Math.max(1, Math.floor(s));
        s = Math.max(0.01, s);
        int rw = (int) Math.round(w * s), rh = (int) Math.round(h * s);
        return new int[]{(cw - rw) / 2, (ch - rh) / 2, rw, rh};
    }

    private void setFilter(int f) {
        if (f == texFilter) return;
        Gles.texParameter(Gles.GL_TEXTURE_MIN_FILTER, f);
        Gles.texParameter(Gles.GL_TEXTURE_MAG_FILTER, f);
        texFilter = f;
    }

    private void quad(float x, float y, float w, float h, float v0, float v1) {
        // triangle strip: top-left, top-right, bottom-left, bottom-right; v0 at the top edge
        float[] v = {x, y, 0f, v0, x + w, y, 1f, v0, x, y + h, 0f, v1, x + w, y + h, 1f, v1};
        MemorySegment.copy(v, 0, vertSeg, JAVA_FLOAT, 0, 16);
        Gles.bindArrayBuffer(vbo);
        Gles.bufferData(16 * 4L, vertSeg);
        Gles.vertexAttrib(0, 2, 16, 0);
        Gles.vertexAttrib(1, 2, 16, 8);
        Gles.drawStrip(4);
    }

    /** Draws one frame and swaps. Returns false when the swap failed. */
    private boolean drawFrame() {
        if (TEST_FAIL && frameNo >= 5) throw new IllegalStateException("simulated GL failure (J2ME_GL_TEST_FAIL)");
        int cw = Math.max(1, getWidth()), ch = Math.max(1, getHeight());
        host.copyFrame(frameInts);
        MemorySegment.copy(frameInts, 0, frameSeg, JAVA_INT, 0, frameInts.length);

        Gles.viewport(0, 0, cw, ch);
        Gles.clearColor(0f, 0f, 0f, 1f);
        Gles.clear();

        int mode = Settings.scaleMode;
        int[] r = destRect(cw, ch, fw, fh, mode);
        float sx = r[2] / (float) fw, sy = r[3] / (float) fh;
        boolean sharp = mode == Settings.SCALE_SHARP || mode == Settings.SCALE_STRETCH;
        boolean integerScale = Math.abs(sx - Math.rint(sx)) < 0.001 && Math.abs(sy - Math.rint(sy)) < 0.001;
        boolean doSharp = sharp && !integerScale && Math.min(sx, sy) > 1f;

        Gles.useProgram(gameProg);
        Gles.activeTexture(0);
        Gles.bindTexture(gameTex);
        Gles.texSubImage(fw, fh, frameSeg);
        // plain bilinear when shrinking (or sharp-bilinear picks it internally); nearest for the pixel-exact modes
        boolean smooth = doSharp || Math.min(sx, sy) < 1f || (sharp && !integerScale);
        setFilter(smooth ? Gles.GL_LINEAR : Gles.GL_NEAREST);
        Gles.uniform1i(uGameTex, 0);
        Gles.uniform2f(uGameSurf, cw, ch);
        Gles.uniform2f(uGameTexSize, fw, fh);
        Gles.uniform2f(uGameScale, sx, sy);
        Gles.uniform1f(uGameSharp, doSharp ? 1f : 0f);
        float gridAlpha = 0f, gridThick = 1f;
        int lvl = Settings.lcdLevel;
        if (lvl > 0 && sx >= 2 && sy >= 2) {
            int[] base = {0, 40, 85, 135};
            double m = Math.min(sx, sy);
            gridAlpha = (float) (Math.min(200, base[Math.min(lvl, 3)] + 6 * m) / 255.0);
            gridThick = ((lvl == 3 && m >= 3) || m >= 6) ? 2f : 1f;
        }
        Gles.uniform2f(uGameGrid, gridAlpha, gridThick);
        Gles.disable(Gles.GL_BLEND);
        quad(r[0], r[1], r[2], r[3], 0f, 1f);

        // overlays (toast, pause/speed tag)
        List<GlOverlay> ovs = new ArrayList<>(2);
        host.overlays(cw, ch, ovs);
        frameNo++;
        if (!ovs.isEmpty()) {
            Gles.useProgram(overlayProg);
            Gles.uniform1i(uOvTex, 0);
            Gles.uniform2f(uOvSurf, cw, ch);
            Gles.enable(Gles.GL_BLEND);
            Gles.blendFunc(Gles.GL_SRC_ALPHA, Gles.GL_ONE_MINUS_SRC_ALPHA);
            for (GlOverlay o : ovs) {
                int[] t = overlayTexture(o);
                Gles.activeTexture(0);
                Gles.bindTexture(t[0]);
                Gles.texParameter(Gles.GL_TEXTURE_MIN_FILTER, Gles.GL_NEAREST);
                Gles.texParameter(Gles.GL_TEXTURE_MAG_FILTER, Gles.GL_NEAREST);
                quad(o.x, o.y, t[1], t[2], 0f, 1f);
            }
            Gles.disable(Gles.GL_BLEND);
            evictOverlays();
        }
        checkError("frame");
        if (capture) doCapture(cw, ch);
        swapStartNanos = System.nanoTime();
        try { return Egl.swapBuffers(edpy, surface); } finally { swapStartNanos = 0; }
    }

    private int[] overlayTexture(GlOverlay o) {
        int[] t = overlayTex.get(o.key);
        if (t == null) {
            BufferedImage img = o.image;
            int w = img.getWidth(), h = img.getHeight();
            int[] px = img.getRGB(0, 0, w, h, null, 0, w);
            try (Arena a = Arena.ofConfined()) {
                MemorySegment seg = a.allocate((long) w * h * 4, 4);
                MemorySegment.copy(px, 0, seg, JAVA_INT, 0, px.length);
                int id = Gles.genTexture();
                Gles.activeTexture(0);
                Gles.bindTexture(id);
                Gles.texParameter(Gles.GL_TEXTURE_WRAP_S, Gles.GL_CLAMP_TO_EDGE);
                Gles.texParameter(Gles.GL_TEXTURE_WRAP_T, Gles.GL_CLAMP_TO_EDGE);
                Gles.texImage(w, h, seg);
                t = new int[]{id, w, h, 0};
            }
            overlayTex.put(o.key, t);
        }
        t[3] = (int) frameNo;
        return t;
    }

    /** Drop overlay textures that were not used for a while so a long run does not accumulate one per toast. */
    private void evictOverlays() {
        if (overlayTex.size() < 6) return;
        overlayTex.entrySet().removeIf(e -> {
            if (frameNo - e.getValue()[3] > 3) { Gles.deleteTexture(e.getValue()[0]); return true; }
            return false;
        });
    }

    private void cleanup() {
        try {
            if (surface != null) releaseSurface();
            if (ctx != null) { Egl.makeCurrent(edpy, MemorySegment.NULL, MemorySegment.NULL, MemorySegment.NULL); Egl.destroyContext(edpy, ctx); ctx = null; }
            if (res != null) { res.close(); res = null; }
        } finally {
            synchronized (lock) { surfaceUp = false; lock.notifyAll(); }
        }
    }

    // ------------------------------------------------------------------ test hook
    private final Object capLock = new Object();
    private boolean capture;
    private int[] captured;
    private int capW, capH;

    /**
     * Renders one frame and returns the surface contents before the swap as 0xAARRGGBB ints, top row first (null on timeout).
     * Size is available from {@link #capturedWidth()}/{@link #capturedHeight()}. For tests and screenshots of the GPU path.
     */
    public int[] captureFrame(int timeoutMs) throws InterruptedException {
        synchronized (capLock) { capture = true; captured = null; }
        requestFrame();
        long deadline = System.nanoTime() + timeoutMs * 1_000_000L;
        synchronized (capLock) {
            while (captured == null) {
                long left = deadline - System.nanoTime();
                if (left <= 0) { capture = false; return null; }
                capLock.wait(Math.max(1, left / 1_000_000));
            }
            return captured;
        }
    }
    public int capturedWidth() { return capW; }
    public int capturedHeight() { return capH; }

    private void doCapture(int w, int h) {
        try (Arena a = Arena.ofConfined()) {
            MemorySegment seg = a.allocate((long) w * h * 4, 4);
            Gles.readPixels(0, 0, w, h, seg);
            int[] raw = new int[w * h];
            MemorySegment.copy(seg, JAVA_INT, 0, raw, 0, raw.length);
            int[] out = new int[w * h];
            for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
                int v = raw[y * w + x];
                int r = v & 0xFF, g = (v >> 8) & 0xFF, b = (v >> 16) & 0xFF, al = (v >>> 24) & 0xFF;
                out[(h - 1 - y) * w + x] = (al << 24) | (r << 16) | (g << 8) | b;
            }
            synchronized (capLock) { captured = out; capW = w; capH = h; capture = false; capLock.notifyAll(); }
        }
    }

    /** The GL renderer string (after the first frame), e.g. "llvmpipe". */
    public String rendererInfo() { return glInfoLine; }
}
