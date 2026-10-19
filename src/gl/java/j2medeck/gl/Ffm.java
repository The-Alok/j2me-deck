// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 J2ME Deck contributors
package j2medeck.gl;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.nio.charset.StandardCharsets;

/**
 * Tiny helpers over the foreign-function API (Java 21 preview / 22+ final). Only calls that exist with the same shape in both are used,
 * so the GL sources compile unchanged on either.
 */
final class Ffm {
    static final Linker LINKER = Linker.nativeLinker();

    private Ffm() {}

    /** First library in {@code names} that can be loaded. */
    static SymbolLookup lib(String... names) {
        RuntimeException last = null;
        for (String n : names) {
            try { return SymbolLookup.libraryLookup(n, Arena.global()); }
            catch (RuntimeException e) { last = e; }
        }
        throw new IllegalStateException("cannot load " + String.join(" / ", names) + (last == null ? "" : ": " + last.getMessage()), last);
    }

    static MethodHandle fn(SymbolLookup lib, String name, FunctionDescriptor fd) {
        MemorySegment sym = lib.find(name).orElseThrow(() -> new IllegalStateException("missing native symbol " + name));
        return LINKER.downcallHandle(sym, fd);
    }

    static MemorySegment cstr(Arena a, String s) {
        byte[] b = s.getBytes(StandardCharsets.UTF_8);
        MemorySegment m = a.allocate(b.length + 1L);
        MemorySegment.copy(b, 0, m, ValueLayout.JAVA_BYTE, 0, b.length);
        m.set(ValueLayout.JAVA_BYTE, b.length, (byte) 0);
        return m;
    }

    /** Reads a NUL-terminated string from a pointer (at most {@code max} bytes). */
    static String str(MemorySegment p, int max) {
        if (p == null || p.equals(MemorySegment.NULL) || p.address() == 0) return null;
        MemorySegment m = p.reinterpret(max);
        int n = 0;
        while (n < max && m.get(ValueLayout.JAVA_BYTE, n) != 0) n++;
        byte[] b = new byte[n];
        MemorySegment.copy(m, ValueLayout.JAVA_BYTE, 0, b, 0, n);
        return new String(b, StandardCharsets.UTF_8);
    }

    static boolean isNull(MemorySegment p) { return p == null || p.address() == 0; }

    static RuntimeException rethrow(Throwable t) {
        if (t instanceof RuntimeException) return (RuntimeException) t;
        if (t instanceof Error) throw (Error) t;
        return new RuntimeException(t);
    }
}
