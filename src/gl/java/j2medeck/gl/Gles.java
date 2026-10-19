// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 J2ME Deck contributors
package j2medeck.gl;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.invoke.MethodHandle;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_BYTE;
import static java.lang.foreign.ValueLayout.JAVA_FLOAT;
import static java.lang.foreign.ValueLayout.JAVA_INT;
import static java.lang.foreign.ValueLayout.JAVA_LONG;

/** The OpenGL ES 2.0 subset the renderer uses (the functions libGLESv2 exports directly). */
final class Gles {
    static final int GL_FALSE = 0, GL_TRIANGLE_STRIP = 5, GL_COLOR_BUFFER_BIT = 0x4000, GL_BLEND = 0x0BE2, GL_SRC_ALPHA = 0x0302,
            GL_ONE_MINUS_SRC_ALPHA = 0x0303, GL_TEXTURE_2D = 0x0DE1, GL_TEXTURE0 = 0x84C0, GL_UNSIGNED_BYTE = 0x1401, GL_FLOAT = 0x1406,
            GL_RGBA = 0x1908, GL_NEAREST = 0x2600, GL_LINEAR = 0x2601, GL_TEXTURE_MAG_FILTER = 0x2800, GL_TEXTURE_MIN_FILTER = 0x2801,
            GL_TEXTURE_WRAP_S = 0x2802, GL_TEXTURE_WRAP_T = 0x2803, GL_CLAMP_TO_EDGE = 0x812F, GL_ARRAY_BUFFER = 0x8892,
            GL_STREAM_DRAW = 0x88E0, GL_FRAGMENT_SHADER = 0x8B30, GL_VERTEX_SHADER = 0x8B31, GL_COMPILE_STATUS = 0x8B81,
            GL_LINK_STATUS = 0x8B82, GL_UNPACK_ALIGNMENT = 0x0CF5, GL_VENDOR = 0x1F00, GL_RENDERER = 0x1F01, GL_VERSION = 0x1F02,
            GL_DEPTH_TEST = 0x0B71;

    private static final SymbolLookup LIB = Ffm.lib("libGLESv2.so.2", "libGLESv2.so");

    private static MethodHandle f(String n, FunctionDescriptor d) { return Ffm.fn(LIB, n, d); }

    private static final MethodHandle CREATE_SHADER = f("glCreateShader", FunctionDescriptor.of(JAVA_INT, JAVA_INT));
    private static final MethodHandle SHADER_SOURCE = f("glShaderSource", FunctionDescriptor.ofVoid(JAVA_INT, JAVA_INT, ADDRESS, ADDRESS));
    private static final MethodHandle COMPILE_SHADER = f("glCompileShader", FunctionDescriptor.ofVoid(JAVA_INT));
    private static final MethodHandle GET_SHADERIV = f("glGetShaderiv", FunctionDescriptor.ofVoid(JAVA_INT, JAVA_INT, ADDRESS));
    private static final MethodHandle GET_SHADER_LOG = f("glGetShaderInfoLog", FunctionDescriptor.ofVoid(JAVA_INT, JAVA_INT, ADDRESS, ADDRESS));
    private static final MethodHandle DELETE_SHADER = f("glDeleteShader", FunctionDescriptor.ofVoid(JAVA_INT));
    private static final MethodHandle CREATE_PROGRAM = f("glCreateProgram", FunctionDescriptor.of(JAVA_INT));
    private static final MethodHandle ATTACH_SHADER = f("glAttachShader", FunctionDescriptor.ofVoid(JAVA_INT, JAVA_INT));
    private static final MethodHandle BIND_ATTRIB = f("glBindAttribLocation", FunctionDescriptor.ofVoid(JAVA_INT, JAVA_INT, ADDRESS));
    private static final MethodHandle LINK_PROGRAM = f("glLinkProgram", FunctionDescriptor.ofVoid(JAVA_INT));
    private static final MethodHandle GET_PROGRAMIV = f("glGetProgramiv", FunctionDescriptor.ofVoid(JAVA_INT, JAVA_INT, ADDRESS));
    private static final MethodHandle GET_PROGRAM_LOG = f("glGetProgramInfoLog", FunctionDescriptor.ofVoid(JAVA_INT, JAVA_INT, ADDRESS, ADDRESS));
    private static final MethodHandle USE_PROGRAM = f("glUseProgram", FunctionDescriptor.ofVoid(JAVA_INT));
    private static final MethodHandle GET_UNIFORM = f("glGetUniformLocation", FunctionDescriptor.of(JAVA_INT, JAVA_INT, ADDRESS));
    private static final MethodHandle UNIFORM1I = f("glUniform1i", FunctionDescriptor.ofVoid(JAVA_INT, JAVA_INT));
    private static final MethodHandle UNIFORM1F = f("glUniform1f", FunctionDescriptor.ofVoid(JAVA_INT, JAVA_FLOAT));
    private static final MethodHandle UNIFORM2F = f("glUniform2f", FunctionDescriptor.ofVoid(JAVA_INT, JAVA_FLOAT, JAVA_FLOAT));
    private static final MethodHandle GEN_TEXTURES = f("glGenTextures", FunctionDescriptor.ofVoid(JAVA_INT, ADDRESS));
    private static final MethodHandle DELETE_TEXTURES = f("glDeleteTextures", FunctionDescriptor.ofVoid(JAVA_INT, ADDRESS));
    private static final MethodHandle BIND_TEXTURE = f("glBindTexture", FunctionDescriptor.ofVoid(JAVA_INT, JAVA_INT));
    private static final MethodHandle ACTIVE_TEXTURE = f("glActiveTexture", FunctionDescriptor.ofVoid(JAVA_INT));
    private static final MethodHandle TEX_PARAMETERI = f("glTexParameteri", FunctionDescriptor.ofVoid(JAVA_INT, JAVA_INT, JAVA_INT));
    private static final MethodHandle TEX_IMAGE2D = f("glTexImage2D", FunctionDescriptor.ofVoid(JAVA_INT, JAVA_INT, JAVA_INT, JAVA_INT, JAVA_INT, JAVA_INT, JAVA_INT, JAVA_INT, ADDRESS));
    private static final MethodHandle TEX_SUBIMAGE2D = f("glTexSubImage2D", FunctionDescriptor.ofVoid(JAVA_INT, JAVA_INT, JAVA_INT, JAVA_INT, JAVA_INT, JAVA_INT, JAVA_INT, JAVA_INT, ADDRESS));
    private static final MethodHandle PIXEL_STOREI = f("glPixelStorei", FunctionDescriptor.ofVoid(JAVA_INT, JAVA_INT));
    private static final MethodHandle GEN_BUFFERS = f("glGenBuffers", FunctionDescriptor.ofVoid(JAVA_INT, ADDRESS));
    private static final MethodHandle BIND_BUFFER = f("glBindBuffer", FunctionDescriptor.ofVoid(JAVA_INT, JAVA_INT));
    private static final MethodHandle BUFFER_DATA = f("glBufferData", FunctionDescriptor.ofVoid(JAVA_INT, JAVA_LONG, ADDRESS, JAVA_INT));
    private static final MethodHandle ENABLE_VERTEX_ATTRIB = f("glEnableVertexAttribArray", FunctionDescriptor.ofVoid(JAVA_INT));
    private static final MethodHandle VERTEX_ATTRIB_POINTER = f("glVertexAttribPointer", FunctionDescriptor.ofVoid(JAVA_INT, JAVA_INT, JAVA_INT, JAVA_BYTE, JAVA_INT, ADDRESS));
    private static final MethodHandle DRAW_ARRAYS = f("glDrawArrays", FunctionDescriptor.ofVoid(JAVA_INT, JAVA_INT, JAVA_INT));
    private static final MethodHandle VIEWPORT = f("glViewport", FunctionDescriptor.ofVoid(JAVA_INT, JAVA_INT, JAVA_INT, JAVA_INT));
    private static final MethodHandle CLEAR_COLOR = f("glClearColor", FunctionDescriptor.ofVoid(JAVA_FLOAT, JAVA_FLOAT, JAVA_FLOAT, JAVA_FLOAT));
    private static final MethodHandle CLEAR = f("glClear", FunctionDescriptor.ofVoid(JAVA_INT));
    private static final MethodHandle ENABLE = f("glEnable", FunctionDescriptor.ofVoid(JAVA_INT));
    private static final MethodHandle DISABLE = f("glDisable", FunctionDescriptor.ofVoid(JAVA_INT));
    private static final MethodHandle BLEND_FUNC = f("glBlendFunc", FunctionDescriptor.ofVoid(JAVA_INT, JAVA_INT));
    private static final MethodHandle GET_ERROR = f("glGetError", FunctionDescriptor.of(JAVA_INT));
    private static final MethodHandle READ_PIXELS = f("glReadPixels", FunctionDescriptor.ofVoid(JAVA_INT, JAVA_INT, JAVA_INT, JAVA_INT, JAVA_INT, JAVA_INT, ADDRESS));
    private static final MethodHandle GET_STRING = f("glGetString", FunctionDescriptor.of(ADDRESS, JAVA_INT));
    private static final MethodHandle FLUSH = f("glFlush", FunctionDescriptor.ofVoid());

    private Gles() {}

    // ---- shaders and programs
    static int createProgram(String vertexSrc, String fragmentSrc, String[] attribs) {
        int vs = compile(GL_VERTEX_SHADER, vertexSrc), fs = compile(GL_FRAGMENT_SHADER, fragmentSrc);
        try (Arena a = Arena.ofConfined()) {
            int p = (int) CREATE_PROGRAM.invokeExact();
            ATTACH_SHADER.invokeExact(p, vs);
            ATTACH_SHADER.invokeExact(p, fs);
            for (int i = 0; i < attribs.length; i++) BIND_ATTRIB.invokeExact(p, i, Ffm.cstr(a, attribs[i]));
            LINK_PROGRAM.invokeExact(p);
            MemorySegment ok = a.allocate(4, 4);
            GET_PROGRAMIV.invokeExact(p, GL_LINK_STATUS, ok);
            if (ok.get(JAVA_INT, 0) == 0) {
                MemorySegment log = a.allocate(2048, 1);
                GET_PROGRAM_LOG.invokeExact(p, 2048, MemorySegment.NULL, log);
                throw new IllegalStateException("GL program link failed: " + Ffm.str(log, 2048));
            }
            DELETE_SHADER.invokeExact(vs);
            DELETE_SHADER.invokeExact(fs);
            return p;
        } catch (Throwable t) { throw Ffm.rethrow(t); }
    }

    private static int compile(int type, String src) {
        try (Arena a = Arena.ofConfined()) {
            int s = (int) CREATE_SHADER.invokeExact(type);
            MemorySegment arr = a.allocate(8, 8);
            arr.set(ADDRESS, 0, Ffm.cstr(a, src));
            SHADER_SOURCE.invokeExact(s, 1, arr, MemorySegment.NULL);
            COMPILE_SHADER.invokeExact(s);
            MemorySegment ok = a.allocate(4, 4);
            GET_SHADERIV.invokeExact(s, GL_COMPILE_STATUS, ok);
            if (ok.get(JAVA_INT, 0) == 0) {
                MemorySegment log = a.allocate(2048, 1);
                GET_SHADER_LOG.invokeExact(s, 2048, MemorySegment.NULL, log);
                throw new IllegalStateException((type == GL_VERTEX_SHADER ? "vertex" : "fragment") + " shader failed: " + Ffm.str(log, 2048));
            }
            return s;
        } catch (Throwable t) { throw Ffm.rethrow(t); }
    }

    static void useProgram(int p) { try { USE_PROGRAM.invokeExact(p); } catch (Throwable t) { throw Ffm.rethrow(t); } }

    static int uniform(int prog, String name) {
        try (Arena a = Arena.ofConfined()) { return (int) GET_UNIFORM.invokeExact(prog, Ffm.cstr(a, name)); } catch (Throwable t) { throw Ffm.rethrow(t); }
    }
    static void uniform1i(int loc, int v) { try { UNIFORM1I.invokeExact(loc, v); } catch (Throwable t) { throw Ffm.rethrow(t); } }
    static void uniform1f(int loc, float v) { try { UNIFORM1F.invokeExact(loc, v); } catch (Throwable t) { throw Ffm.rethrow(t); } }
    static void uniform2f(int loc, float x, float y) { try { UNIFORM2F.invokeExact(loc, x, y); } catch (Throwable t) { throw Ffm.rethrow(t); } }

    // ---- textures and buffers
    static int genTexture() {
        try (Arena a = Arena.ofConfined()) { MemorySegment v = a.allocate(4, 4); GEN_TEXTURES.invokeExact(1, v); return v.get(JAVA_INT, 0); } catch (Throwable t) { throw Ffm.rethrow(t); }
    }
    static void deleteTexture(int id) {
        try (Arena a = Arena.ofConfined()) { MemorySegment v = a.allocate(4, 4); v.set(JAVA_INT, 0, id); DELETE_TEXTURES.invokeExact(1, v); } catch (Throwable t) { throw Ffm.rethrow(t); }
    }
    static int genBuffer() {
        try (Arena a = Arena.ofConfined()) { MemorySegment v = a.allocate(4, 4); GEN_BUFFERS.invokeExact(1, v); return v.get(JAVA_INT, 0); } catch (Throwable t) { throw Ffm.rethrow(t); }
    }
    static void bindTexture(int id) { try { BIND_TEXTURE.invokeExact(GL_TEXTURE_2D, id); } catch (Throwable t) { throw Ffm.rethrow(t); } }
    static void activeTexture(int unit) { try { ACTIVE_TEXTURE.invokeExact(GL_TEXTURE0 + unit); } catch (Throwable t) { throw Ffm.rethrow(t); } }
    static void texParameter(int pname, int v) { try { TEX_PARAMETERI.invokeExact(GL_TEXTURE_2D, pname, v); } catch (Throwable t) { throw Ffm.rethrow(t); } }
    static void texImage(int w, int h, MemorySegment data) {
        MemorySegment p = data;
        if (p == null) p = MemorySegment.NULL;
        try { TEX_IMAGE2D.invokeExact(GL_TEXTURE_2D, 0, GL_RGBA, w, h, 0, GL_RGBA, GL_UNSIGNED_BYTE, p); } catch (Throwable t) { throw Ffm.rethrow(t); }
    }
    static void texSubImage(int w, int h, MemorySegment data) {
        try { TEX_SUBIMAGE2D.invokeExact(GL_TEXTURE_2D, 0, 0, 0, w, h, GL_RGBA, GL_UNSIGNED_BYTE, data); } catch (Throwable t) { throw Ffm.rethrow(t); }
    }
    static void pixelStore(int pname, int v) { try { PIXEL_STOREI.invokeExact(pname, v); } catch (Throwable t) { throw Ffm.rethrow(t); } }
    static void bindArrayBuffer(int id) { try { BIND_BUFFER.invokeExact(GL_ARRAY_BUFFER, id); } catch (Throwable t) { throw Ffm.rethrow(t); } }
    static void bufferData(long bytes, MemorySegment data) { try { BUFFER_DATA.invokeExact(GL_ARRAY_BUFFER, bytes, data, GL_STREAM_DRAW); } catch (Throwable t) { throw Ffm.rethrow(t); } }
    static void vertexAttrib(int idx, int size, int strideBytes, long offsetBytes) {
        try {
            ENABLE_VERTEX_ATTRIB.invokeExact(idx);
            VERTEX_ATTRIB_POINTER.invokeExact(idx, size, GL_FLOAT, (byte) 0, strideBytes, MemorySegment.ofAddress(offsetBytes));
        } catch (Throwable t) { throw Ffm.rethrow(t); }
    }

    // ---- state and drawing
    static void drawStrip(int vertices) { try { DRAW_ARRAYS.invokeExact(GL_TRIANGLE_STRIP, 0, vertices); } catch (Throwable t) { throw Ffm.rethrow(t); } }
    static void viewport(int x, int y, int w, int h) { try { VIEWPORT.invokeExact(x, y, w, h); } catch (Throwable t) { throw Ffm.rethrow(t); } }
    static void clearColor(float r, float g, float b, float a) { try { CLEAR_COLOR.invokeExact(r, g, b, a); } catch (Throwable t) { throw Ffm.rethrow(t); } }
    static void clear() { try { CLEAR.invokeExact(GL_COLOR_BUFFER_BIT); } catch (Throwable t) { throw Ffm.rethrow(t); } }
    static void enable(int cap) { try { ENABLE.invokeExact(cap); } catch (Throwable t) { throw Ffm.rethrow(t); } }
    static void disable(int cap) { try { DISABLE.invokeExact(cap); } catch (Throwable t) { throw Ffm.rethrow(t); } }
    static void blendFunc(int s, int d) { try { BLEND_FUNC.invokeExact(s, d); } catch (Throwable t) { throw Ffm.rethrow(t); } }
    static int getError() { try { return (int) GET_ERROR.invokeExact(); } catch (Throwable t) { throw Ffm.rethrow(t); } }
    static void flush() { try { FLUSH.invokeExact(); } catch (Throwable t) { throw Ffm.rethrow(t); } }
    static String getString(int name) { try { return Ffm.str((MemorySegment) GET_STRING.invokeExact(name), 256); } catch (Throwable t) { throw Ffm.rethrow(t); } }

    /** Reads RGBA bytes of the bottom-left-origin framebuffer region (tests and screenshots). */
    static void readPixels(int x, int y, int w, int h, MemorySegment dst) {
        try { READ_PIXELS.invokeExact(x, y, w, h, GL_RGBA, GL_UNSIGNED_BYTE, dst); } catch (Throwable t) { throw Ffm.rethrow(t); }
    }
}
