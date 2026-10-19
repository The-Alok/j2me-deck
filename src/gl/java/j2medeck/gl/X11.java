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

/** The three Xlib calls EGL needs: open a display connection, read a window's visual, close. (LP64 layouts: x86_64 and aarch64.) */
final class X11 {
    private static final SymbolLookup LIB = Ffm.lib("libX11.so.6", "libX11.so");
    private static final MethodHandle OPEN = Ffm.fn(LIB, "XOpenDisplay", FunctionDescriptor.of(ADDRESS, ADDRESS));
    private static final MethodHandle CLOSE = Ffm.fn(LIB, "XCloseDisplay", FunctionDescriptor.of(JAVA_INT, ADDRESS));
    private static final MethodHandle GET_ATTRS = Ffm.fn(LIB, "XGetWindowAttributes", FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_LONG, ADDRESS));
    private static final MethodHandle VISUAL_ID = Ffm.fn(LIB, "XVisualIDFromVisual", FunctionDescriptor.of(JAVA_LONG, ADDRESS));

    private X11() {}

    static MemorySegment openDisplay() {
        try { return (MemorySegment) OPEN.invokeExact(MemorySegment.NULL); } catch (Throwable t) { throw Ffm.rethrow(t); }
    }

    static void closeDisplay(MemorySegment dpy) {
        try { int r = (int) CLOSE.invokeExact(dpy); } catch (Throwable t) { throw Ffm.rethrow(t); }
    }

    /** Visual id of a window, or 0 when it cannot be read. {@code size[0]}/{@code size[1]} receive its width and height when non-null. */
    static long windowVisualId(MemorySegment dpy, long window, int[] size) {
        try (Arena a = Arena.ofConfined()) {
            MemorySegment attrs = a.allocate(256, 8);
            int ok = (int) GET_ATTRS.invokeExact(dpy, window, attrs);
            if (ok == 0) return 0;
            if (size != null && size.length >= 2) { size[0] = attrs.get(JAVA_INT, 8); size[1] = attrs.get(JAVA_INT, 12); }
            MemorySegment visual = attrs.get(ADDRESS, 24);
            if (Ffm.isNull(visual)) return 0;
            return (long) VISUAL_ID.invokeExact(visual);
        } catch (Throwable t) { throw Ffm.rethrow(t); }
    }
}
