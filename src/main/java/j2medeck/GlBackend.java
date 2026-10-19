// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 J2ME Deck contributors
package j2medeck;

import java.util.ArrayList;
import java.util.List;

/**
 * Optional OpenGL ES output for the game picture: the game still draws with Java2D into its framebuffer, but scaling, filtering, the LCD grid
 * and presentation happen on the GPU (EGL + GLES2 through the Java foreign-function API, no extra libraries). The classes live in
 * {@code j2medeck.gl} and are loaded reflectively so that a JVM without that support simply keeps the Swing path.
 */
public final class GlBackend {
    private static final String IMPL = "j2medeck.gl.GlSurface";
    private static volatile String unavailable;       // why GL cannot be used in this JVM (null = not known to be unavailable)

    private GlBackend() {}

    /** True when the user's renderer setting allows trying OpenGL ES. */
    public static boolean wanted() {
        if ("swing".equals(Settings.renderer) || unavailable != null || java.awt.GraphicsEnvironment.isHeadless()
                || !System.getProperty("os.name", "").toLowerCase().contains("linux")) return false;
        // OpenGL ES through XWayland/gamescope is unverified (a picture that stops updating is possible), so "auto" means Swing in Game Mode.
        // An explicit renderer=gles (settings, profile or --renderer gles) still tries it, guarded by the swap watchdog in GlSurface.
        if (Settings.gameMode && !"gles".equals(Settings.renderer)) {
            if (!gameModeNoted) {
                gameModeNoted = true;
                System.err.println("[j2me] Steam Game Mode: using the Swing renderer (renderer=auto). Use --renderer gles to try OpenGL ES here.");
            }
            return false;
        }
        return true;
    }
    private static volatile boolean gameModeNoted;

    /** Why GL is not available (for logs and the settings dialog), or null. */
    public static String unavailableReason() { return unavailable; }

    /**
     * Creates the GL canvas for a host, or returns null (reason logged, and remembered for the rest of the run) when it is not possible.
     * The returned canvas also implements {@link GlControl}.
     */
    public static java.awt.Canvas create(GlHost host) {
        if (!wanted()) return null;
        try {
            Class<?> c = Class.forName(IMPL);
            return (java.awt.Canvas) c.getConstructor(GlHost.class).newInstance(host);
        } catch (Throwable t) {
            Throwable r = t instanceof java.lang.reflect.InvocationTargetException && t.getCause() != null ? t.getCause() : t;
            disable(describe(r));
            return null;
        }
    }

    /** Record that GL cannot be used from now on and say why once. */
    public static void disable(String reason) {
        if (unavailable == null) {
            unavailable = reason;
            System.err.println("[j2me] OpenGL ES output not used (" + reason + "); using the Swing renderer."
                    + ("gles".equals(Settings.renderer) ? " (renderer=gles was requested explicitly)" : ""));
        }
    }

    private static String describe(Throwable t) {
        if (t instanceof UnsupportedClassVersionError) {
            return "this Java cannot load the GL classes (built for Java 21 with --enable-preview; run.sh passes the flags): " + t.getMessage();
        }
        if (t instanceof ClassNotFoundException || t instanceof NoClassDefFoundError) return "GL classes missing: " + t;
        return t.toString();
    }

    /**
     * JVM options the GL path needs: native access for the foreign-function API, preview enabling on Java 21 (where it is still a preview),
     * and access to the AWT peer that holds the native window id. Empty when this JVM is too old.
     */
    public static List<String> jvmFlags() {
        List<String> f = new ArrayList<>();
        int v = Runtime.version().feature();
        if (v < 21) return f;
        if (v == 21) f.add("--enable-preview");
        f.add("--enable-native-access=ALL-UNNAMED");
        f.add("--add-opens=java.desktop/java.awt=ALL-UNNAMED");
        f.add("--add-opens=java.desktop/sun.awt.X11=ALL-UNNAMED");
        return f;
    }
}
