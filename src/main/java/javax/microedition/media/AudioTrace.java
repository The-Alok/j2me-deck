// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 J2ME Deck contributors
package javax.microedition.media;

/** Optional audio tracing: start the emulator with the environment variable J2ME_DEBUG_AUDIO=1 to log player activity to stderr. */
final class AudioTrace {
    static final boolean ON = System.getenv("J2ME_DEBUG_AUDIO") != null;
    private static final long T0 = System.currentTimeMillis();
    private AudioTrace() {}
    static void log(Object player, String msg) {
        if (ON) System.err.println("[audio] +" + (System.currentTimeMillis() - T0) + "ms " + player + " " + msg);
    }
}
