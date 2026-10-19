// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 J2ME Deck contributors
package j2medeck;

import javax.microedition.lcdui.Canvas;
import javax.microedition.lcdui.Display;
import javax.microedition.lcdui.Displayable;
import javax.microedition.midlet.MIDlet;
import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.jar.*;

public final class LinuxLoader {
    private static volatile MIDlet midlet;
    private static final AtomicBoolean exiting = new AtomicBoolean();
    private static volatile boolean skipDestroy;

    public static void main(String[] args) throws Exception {
        String jarArg = null, midletClass = null, script = null, size = null;
        Boolean cliFullscreen = null;
        Integer cliScale = null;
        String cliRenderer = null;
        boolean noAutoPause = false, library = false;
        int midletIndex = 1;
        for (int i = 0; i < args.length; i++) {
            String a = args[i];
            switch (a) {
                case "-h": case "--help": usage(); return;
                case "--version": System.out.println("J2ME Deck " + version()); return;
                case "--library": library = true; break;
                case "--fullscreen": cliFullscreen = true; break;
                case "--windowed": cliFullscreen = false; break;
                case "--no-autopause": noAutoPause = true; break;
                case "--renderer":
                    cliRenderer = Settings.parseRenderer(args[++i]);
                    if (cliRenderer == null) { System.err.println("--renderer takes auto, gles or swing"); System.exit(2); }
                    break;
                case "--scale": cliScale = Integer.parseInt(args[++i]); break;
                case "--size": size = args[++i]; break;
                case "--midlet": midletIndex = Integer.parseInt(args[++i]); break;
                case "--script": script = args[++i]; break;
                case "--script-file": script = new String(Files.readAllBytes(Paths.get(args[++i]))); break;
                default:
                    if (a.startsWith("--")) { System.err.println("Unknown option " + a); usage(); System.exit(2); }
                    if (jarArg == null) jarArg = a; else midletClass = a;
            }
        }
        Settings.load();
        if (jarArg == null || library) {
            if (java.awt.GraphicsEnvironment.isHeadless()) { usage(); System.exit(jarArg == null ? 2 : 0); }
            if (cliFullscreen != null) Settings.fullscreen = cliFullscreen;
            j2medeck.ui.LibraryWindow.open(Settings.fullscreen);
            return;
        }

        Path jar = Paths.get(jarArg).toAbsolutePath().normalize();
        if (!Files.isRegularFile(jar)) throw new FileNotFoundException(jar.toString());

        GameInfo info = GameInfo.read(jar);
        Map<String, String> props = new LinkedHashMap<>(info.props);
        String main = info.mainClass(midletIndex);
        String title = info.name();
        if (midletClass != null) main = midletClass;
        if (main == null) throw new IOException("No MIDlet-" + midletIndex + " class in manifest. Pass the class name as an argument.");

        // per-game profile (resolution, scaling, network, fonts, extra properties...), then command-line overrides
        Profile profile = Profile.load(info.id());
        System.setProperty("j2me.profileid", info.id());
        profile.applyTo();
        props.putAll(profile.prefixed("appprop."));
        int pw = profile.screenWidth(), ph = profile.screenHeight();
        if (Boolean.TRUE.equals(profile.getBool("autosize"))) {           // match the display's aspect ratio at a fixed height
            int h = Math.max(64, profile.getInt("autosizeheight", 320));
            double aspect = 1280.0 / 800.0;
            if (!java.awt.GraphicsEnvironment.isHeadless()) { java.awt.Dimension d = java.awt.Toolkit.getDefaultToolkit().getScreenSize(); aspect = d.width / (double) d.height; }
            ph = h; pw = (int) Math.round(h * aspect / 2.0) * 2;
        }
        if (size != null) {
            String[] p = size.toLowerCase().split("x");
            Displayable.setVirtualSize(Integer.parseInt(p[0]), Integer.parseInt(p[1]));
        } else if (pw >= 32 && ph >= 32) Displayable.setVirtualSize(pw, ph);
        if (cliFullscreen != null) Settings.fullscreen = cliFullscreen;
        if (cliScale != null) Settings.windowScale = cliScale;
        if (cliRenderer != null) Settings.renderer = cliRenderer;
        if (noAutoPause) Settings.autoPause = false;

        String vendor = props.getOrDefault("MIDlet-Vendor", "unknown");
        System.err.println("[j2me] " + title + " (" + vendor + ") profile=" + info.id() + " screen=" + Displayable.getVirtualWidth() + "x" + Displayable.getVirtualHeight()
                + " layout=" + KeyLayout.current + " java=" + System.getProperty("java.version") + " network=" + Settings.network);
        System.setProperty("j2me.jar", jar.toString());
        System.setProperty("j2me.title", title);
        System.setProperty("j2me.suite", vendor + "_" + title);     // RMS stores are per suite
        setPlatformProperties();
        profile.applyTo();                                          // profile sysprop.* may override platform defaults
        if (cliRenderer != null) Settings.renderer = cliRenderer;   // applyTo() reloads profile values; the command line still wins
        MIDlet.initProps(props);
        Thread.setDefaultUncaughtExceptionHandler((t, e) -> { System.err.println("[j2me] uncaught in " + t.getName() + ":"); e.printStackTrace(); });

        ResourceClassLoader loader = new ResourceClassLoader(jar, LinuxLoader.class.getClassLoader());
        ResourceBridge.setLoader(loader);
        Thread.currentThread().setContextClassLoader(loader);

        Display.initDisplay();
        System.err.println("[j2me] session: gameMode=" + Settings.gameMode + " fullscreen=" + Settings.fullscreen + " renderer=" + Settings.renderer
                + " autoPause=" + Settings.autoPause + " headless=" + java.awt.GraphicsEnvironment.isHeadless()
                + " DISPLAY=" + System.getenv("DISPLAY") + " WAYLAND=" + System.getenv("GAMESCOPE_WAYLAND_DISPLAY") + " XDG_RUNTIME_DIR=" + System.getenv("XDG_RUNTIME_DIR"));
        Diag.start();
        GamepadReader.start();
        if (!Settings.mute) javax.microedition.media.Manager.warmup();

        Class<?> c = Class.forName(main, true, loader);
        if (!MIDlet.class.isAssignableFrom(c)) throw new IllegalArgumentException(main + " is not a MIDlet");
        final MIDlet m = (MIDlet) c.getDeclaredConstructor().newInstance();
        midlet = m;

        Display.setCloseHandler(LinuxLoader::windowClosed);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            if (!skipDestroy) { try { m.$destroyApp(true); } catch (Throwable ignored) {} }
        }));

        // MIDP calls startApp on the application-management thread, not on the UI thread.
        Thread starter = new Thread(() -> {
            try { m.$startApp(); }
            catch (Throwable t) { System.err.println("[j2me] startApp failed:"); t.printStackTrace(); exit(1); }
        }, "MIDlet-startApp");
        starter.start();

        if (script != null) DevHarness.run(script);

        Display.awaitClosed();
        exit(0);
    }

    private static void setPlatformProperties() {
        String[][] p = {
                {"microedition.platform", "J2ME-Deck"}, {"microedition.encoding", "ISO-8859-1"},
                {"microedition.locale", "en-US"}, {"microedition.configuration", "CLDC-1.1"},
                {"microedition.profiles", "MIDP-2.0"}, {"microedition.media.version", "1.1"},
                {"supports.mixing", "true"}, {"supports.audio.capture", "false"}, {"supports.video.capture", "false"},
                {"supports.recording", "false"}, {"audio.encodings", "encoding=audio/x-wav"},
                {"microedition.m3g.version", ""}, {"microedition.io.file.FileConnection.version", "1.0"},
        };
        for (String[] kv : p) if (!kv[1].isEmpty() && System.getProperty(kv[0]) == null) System.setProperty(kv[0], kv[1]);
    }

    /** Called when the player closes the window. */
    private static void windowClosed() {
        MIDlet m = midlet;
        if (m != null) { try { m.$destroyApp(true); } catch (Throwable ignored) {} }
        skipDestroy = true;
        exit(0);
    }

    /** Called by MIDlet.notifyDestroyed(). */
    public static void midletDestroyed() {
        skipDestroy = true;
        Display.shutdown();
        exit(0);
    }

    public static boolean platformRequest(String url) {
        System.err.println("[j2me] platformRequest ignored: " + url);
        return false;
    }

    public static void exit(int code) {
        if (!exiting.compareAndSet(false, true)) return;
        Thread t = new Thread(() -> {
            try { Thread.sleep(100); } catch (InterruptedException ignored) {}
            Display.shutdown();
            System.out.flush(); System.err.flush();
            Runtime.getRuntime().halt(code);
        }, "exit");
        t.setDaemon(true);
        t.start();
    }

    /** The version written into the jar manifest by build.sh ("dev" when run from loose classes). */
    public static String version() {
        String v = LinuxLoader.class.getPackage() == null ? null : LinuxLoader.class.getPackage().getImplementationVersion();
        return v == null ? "dev" : v;
    }

    private static void usage() {
        System.out.println("J2ME Deck " + version() + "\nUsage: j2me-deck [options] [game.jar] [midlet.class]\n"
                + "  (no game given)             open the game library window\n"
                + "  --library                   open the library even if a game is given\n"
                + "  --fullscreen | --windowed   start fullscreen (default in Steam Game Mode) or windowed\n"
                + "  --renderer auto|gles|swing  how the picture reaches the screen: OpenGL ES (low power) with automatic fallback, OpenGL ES only, or Swing\n"
                + "  --scale N                   initial window scale (integer)\n"
                + "  --size WxH                  screen size reported to the game (default 240x320; overrides the game profile)\n"
                + "  --midlet N                  run MIDlet-N from the manifest (default 1)\n"
                + "  --no-autopause              don't call hideNotify when the window loses focus\n"
                + "  --version                   print the version and exit\n"
                + "In-game: F8 key mapper, F10 cycles scaling, F11 / Alt+Enter fullscreen, F12 screenshot.");
    }

    /** Desktop equivalent of J2ME-Loader's AppClassLoader: the whole suite is served from memory. */
    private static final class ResourceClassLoader extends URLClassLoader {
        private final Map<String, byte[]> resources = new HashMap<>();

        ResourceClassLoader(Path jar, ClassLoader parent) throws IOException {
            super(new URL[]{jar.toUri().toURL()}, parent);
            try (JarFile jf = new JarFile(jar.toFile())) {
                Enumeration<JarEntry> entries = jf.entries();
                while (entries.hasMoreElements()) {
                    JarEntry e = entries.nextElement();
                    if (!e.isDirectory()) try (InputStream in = jf.getInputStream(e)) { resources.put(e.getName(), in.readAllBytes()); }
                }
            }
        }

        private static String normalize(String name) {
            if (name == null) return null;
            name = name.replace('\\', '/');
            while (name.startsWith("/")) name = name.substring(1);
            return name;
        }

        @Override public URL getResource(String name) {
            String key = normalize(name);
            byte[] data = resources.get(key);
            if (data == null) return super.getResource(name);
            try {
                return new URL(null, "j2meres:" + key, new URLStreamHandler() {
                    @Override protected URLConnection openConnection(URL u) {
                        return new URLConnection(u) {
                            @Override public void connect() { connected = true; }
                            @Override public InputStream getInputStream() { return new ByteArrayInputStream(data); }
                            @Override public int getContentLength() { return data.length; }
                            @Override public long getContentLengthLong() { return data.length; }
                        };
                    }
                });
            } catch (MalformedURLException e) { return super.getResource(name); }
        }

        @Override protected Class<?> findClass(String name) throws ClassNotFoundException {
            byte[] original = resources.get(name.replace('.', '/') + ".class");
            if (original != null) {
                byte[] b = ResourceRewriter.rewrite(original);
                return defineClass(name, b, 0, b.length);
            }
            return super.findClass(name);
        }

        @Override public InputStream getResourceAsStream(String name) {
            byte[] data = resources.get(normalize(name));
            return data != null ? new ByteArrayInputStream(data) : super.getResourceAsStream(name);
        }
    }
}
