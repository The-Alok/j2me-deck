// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 J2ME Deck contributors
package j2medeck.gl;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.invoke.MethodHandle;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_INT;
import static java.lang.foreign.ValueLayout.JAVA_LONG;

/** EGL 1.4/1.5 entry points used by the renderer. */
final class Egl {
    static final int EGL_ALPHA_SIZE = 0x3021, EGL_BLUE_SIZE = 0x3022, EGL_GREEN_SIZE = 0x3023, EGL_RED_SIZE = 0x3024,
            EGL_SURFACE_TYPE = 0x3033, EGL_NONE = 0x3038, EGL_NATIVE_VISUAL_ID = 0x302E, EGL_RENDERABLE_TYPE = 0x3040,
            EGL_WINDOW_BIT = 0x0004, EGL_OPENGL_ES2_BIT = 0x0004, EGL_CONTEXT_CLIENT_VERSION = 0x3098,
            EGL_OPENGL_ES_API = 0x30A0, EGL_VENDOR = 0x3053, EGL_VERSION = 0x3054, EGL_WIDTH = 0x3057, EGL_HEIGHT = 0x3056,
            EGL_PLATFORM_X11_KHR = 0x31D5;

    private static final SymbolLookup LIB = Ffm.lib("libEGL.so.1", "libEGL.so");
    private static final MethodHandle GET_ERROR = Ffm.fn(LIB, "eglGetError", FunctionDescriptor.of(JAVA_INT));
    private static final MethodHandle GET_DISPLAY = Ffm.fn(LIB, "eglGetDisplay", FunctionDescriptor.of(ADDRESS, ADDRESS));
    private static final MethodHandle GET_PLATFORM_DISPLAY = optional("eglGetPlatformDisplay", FunctionDescriptor.of(ADDRESS, JAVA_INT, ADDRESS, ADDRESS));
    private static final MethodHandle INITIALIZE = Ffm.fn(LIB, "eglInitialize", FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, ADDRESS));
    private static final MethodHandle TERMINATE = Ffm.fn(LIB, "eglTerminate", FunctionDescriptor.of(JAVA_INT, ADDRESS));
    private static final MethodHandle BIND_API = Ffm.fn(LIB, "eglBindAPI", FunctionDescriptor.of(JAVA_INT, JAVA_INT));
    private static final MethodHandle CHOOSE_CONFIG = Ffm.fn(LIB, "eglChooseConfig", FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, ADDRESS, JAVA_INT, ADDRESS));
    private static final MethodHandle GET_CONFIG_ATTRIB = Ffm.fn(LIB, "eglGetConfigAttrib", FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, JAVA_INT, ADDRESS));
    private static final MethodHandle CREATE_CONTEXT = Ffm.fn(LIB, "eglCreateContext", FunctionDescriptor.of(ADDRESS, ADDRESS, ADDRESS, ADDRESS, ADDRESS));
    private static final MethodHandle DESTROY_CONTEXT = Ffm.fn(LIB, "eglDestroyContext", FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS));
    private static final MethodHandle CREATE_WINDOW_SURFACE = Ffm.fn(LIB, "eglCreateWindowSurface", FunctionDescriptor.of(ADDRESS, ADDRESS, ADDRESS, JAVA_LONG, ADDRESS));
    private static final MethodHandle DESTROY_SURFACE = Ffm.fn(LIB, "eglDestroySurface", FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS));
    private static final MethodHandle MAKE_CURRENT = Ffm.fn(LIB, "eglMakeCurrent", FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, ADDRESS, ADDRESS));
    private static final MethodHandle SWAP_BUFFERS = Ffm.fn(LIB, "eglSwapBuffers", FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS));
    private static final MethodHandle SWAP_INTERVAL = Ffm.fn(LIB, "eglSwapInterval", FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_INT));
    private static final MethodHandle QUERY_STRING = Ffm.fn(LIB, "eglQueryString", FunctionDescriptor.of(ADDRESS, ADDRESS, JAVA_INT));
    private static final MethodHandle QUERY_SURFACE = Ffm.fn(LIB, "eglQuerySurface", FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, JAVA_INT, ADDRESS));

    private Egl() {}

    private static MethodHandle optional(String name, FunctionDescriptor fd) {
        try { return Ffm.fn(LIB, name, fd); } catch (RuntimeException e) { return null; }
    }

    static int getError() { try { return (int) GET_ERROR.invokeExact(); } catch (Throwable t) { throw Ffm.rethrow(t); } }

    /** The EGL display for an Xlib connection, or null. */
    static MemorySegment getX11Display(MemorySegment xdpy) {
        try {
            MemorySegment d = MemorySegment.NULL;
            if (GET_PLATFORM_DISPLAY != null) d = (MemorySegment) GET_PLATFORM_DISPLAY.invokeExact(EGL_PLATFORM_X11_KHR, xdpy, MemorySegment.NULL);
            if (Ffm.isNull(d)) d = (MemorySegment) GET_DISPLAY.invokeExact(xdpy);
            return Ffm.isNull(d) ? null : d;
        } catch (Throwable t) { throw Ffm.rethrow(t); }
    }

    /** Initialises the display; returns "major.minor". */
    static String initialize(MemorySegment dpy) {
        try (Arena a = Arena.ofConfined()) {
            MemorySegment ma = a.allocate(8, 4);
            int ok = (int) INITIALIZE.invokeExact(dpy, ma, ma.asSlice(4));
            if (ok == 0) throw new IllegalStateException("eglInitialize failed (0x" + Integer.toHexString(getError()) + ")");
            return ma.get(JAVA_INT, 0) + "." + ma.get(JAVA_INT, 4);
        } catch (Throwable t) { throw Ffm.rethrow(t); }
    }

    static void terminate(MemorySegment dpy) { try { int r = (int) TERMINATE.invokeExact(dpy); } catch (Throwable t) { throw Ffm.rethrow(t); } }

    static boolean bindEs() { try { return (int) BIND_API.invokeExact(EGL_OPENGL_ES_API) != 0; } catch (Throwable t) { throw Ffm.rethrow(t); } }

    /** All window-capable OpenGL ES 2 RGB configs (best first, as EGL sorts them). */
    static MemorySegment[] chooseConfigs(MemorySegment dpy) {
        try (Arena a = Arena.ofConfined()) {
            int[] attr = {EGL_SURFACE_TYPE, EGL_WINDOW_BIT, EGL_RENDERABLE_TYPE, EGL_OPENGL_ES2_BIT,
                    EGL_RED_SIZE, 8, EGL_GREEN_SIZE, 8, EGL_BLUE_SIZE, 8, EGL_NONE};
            MemorySegment at = a.allocate(attr.length * 4L, 4);
            MemorySegment.copy(attr, 0, at, JAVA_INT, 0, attr.length);
            MemorySegment cfgs = a.allocate(64 * 8L, 8), num = a.allocate(4, 4);
            int ok = (int) CHOOSE_CONFIG.invokeExact(dpy, at, cfgs, 64, num);
            int n = ok == 0 ? 0 : num.get(JAVA_INT, 0);
            MemorySegment[] out = new MemorySegment[n];
            for (int i = 0; i < n; i++) out[i] = cfgs.getAtIndex(ADDRESS, i);
            return out;
        } catch (Throwable t) { throw Ffm.rethrow(t); }
    }

    static int configAttrib(MemorySegment dpy, MemorySegment cfg, int attr) {
        try (Arena a = Arena.ofConfined()) {
            MemorySegment v = a.allocate(4, 4);
            int ok = (int) GET_CONFIG_ATTRIB.invokeExact(dpy, cfg, attr, v);
            return ok == 0 ? -1 : v.get(JAVA_INT, 0);
        } catch (Throwable t) { throw Ffm.rethrow(t); }
    }

    static MemorySegment createContext(MemorySegment dpy, MemorySegment cfg) {
        try (Arena a = Arena.ofConfined()) {
            int[] attr = {EGL_CONTEXT_CLIENT_VERSION, 2, EGL_NONE};
            MemorySegment at = a.allocate(attr.length * 4L, 4);
            MemorySegment.copy(attr, 0, at, JAVA_INT, 0, attr.length);
            MemorySegment c = (MemorySegment) CREATE_CONTEXT.invokeExact(dpy, cfg, MemorySegment.NULL, at);
            return Ffm.isNull(c) ? null : c;
        } catch (Throwable t) { throw Ffm.rethrow(t); }
    }

    static void destroyContext(MemorySegment dpy, MemorySegment ctx) { try { int r = (int) DESTROY_CONTEXT.invokeExact(dpy, ctx); } catch (Throwable t) { throw Ffm.rethrow(t); } }

    static MemorySegment createWindowSurface(MemorySegment dpy, MemorySegment cfg, long xid) {
        try {
            MemorySegment s = (MemorySegment) CREATE_WINDOW_SURFACE.invokeExact(dpy, cfg, xid, MemorySegment.NULL);
            return Ffm.isNull(s) ? null : s;
        } catch (Throwable t) { throw Ffm.rethrow(t); }
    }

    static void destroySurface(MemorySegment dpy, MemorySegment s) { try { int r = (int) DESTROY_SURFACE.invokeExact(dpy, s); } catch (Throwable t) { throw Ffm.rethrow(t); } }

    static boolean makeCurrent(MemorySegment dpy, MemorySegment draw, MemorySegment read, MemorySegment ctx) {
        try { return (int) MAKE_CURRENT.invokeExact(dpy, draw, read, ctx) != 0; } catch (Throwable t) { throw Ffm.rethrow(t); }
    }

    static boolean swapBuffers(MemorySegment dpy, MemorySegment s) {
        try { return (int) SWAP_BUFFERS.invokeExact(dpy, s) != 0; } catch (Throwable t) { throw Ffm.rethrow(t); }
    }

    static boolean swapInterval(MemorySegment dpy, int n) {
        try { return (int) SWAP_INTERVAL.invokeExact(dpy, n) != 0; } catch (Throwable t) { throw Ffm.rethrow(t); }
    }

    static String queryString(MemorySegment dpy, int name) {
        try { return Ffm.str((MemorySegment) QUERY_STRING.invokeExact(dpy, name), 256); } catch (Throwable t) { throw Ffm.rethrow(t); }
    }

    /** Surface size as EGL sees it, or {0,0}. */
    static int[] surfaceSize(MemorySegment dpy, MemorySegment s) {
        try (Arena a = Arena.ofConfined()) {
            MemorySegment v = a.allocate(8, 4);
            int ok1 = (int) QUERY_SURFACE.invokeExact(dpy, s, EGL_WIDTH, v);
            int ok2 = (int) QUERY_SURFACE.invokeExact(dpy, s, EGL_HEIGHT, v.asSlice(4));
            return new int[]{ok1 == 0 ? 0 : v.get(JAVA_INT, 0), ok2 == 0 ? 0 : v.get(JAVA_INT, 4)};
        } catch (Throwable t) { throw Ffm.rethrow(t); }
    }
}
