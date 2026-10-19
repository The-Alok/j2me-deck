// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 J2ME Deck contributors
package j2medeck;

import java.io.*;
import java.nio.file.*;
import java.util.Properties;

/** Runtime options. Persisted in ~/.config/j2me-deck/settings.properties (only the scale mode is saved). */
public final class Settings {
    public static final int SCALE_SHARP = 0, SCALE_NEAREST = 1, SCALE_INTEGER = 2, SCALE_STRETCH = 3;
    public static final String[] SCALE_NAMES = {"sharp-bilinear", "nearest", "integer", "stretch"};

    /** True when started from Steam Game Mode (gamescope). */
    public static final boolean gameMode = detectGameMode();
    public static volatile boolean fullscreen = gameMode;
    /** Pause the game when the window loses focus. Off by default in Game Mode: gamescope focus changes there are not a reliable "the player left" signal. */
    public static volatile boolean autoPause = !gameMode;
    public static volatile int windowScale = 0;      // 0 = automatic
    public static volatile int scaleMode = SCALE_SHARP;
    /** LCD grid shader level 0..3 (off, low, medium, high): dark lines between the game's pixels, drawn when scaled 2x or more. */
    public static final String[] LCD_NAMES = {"off", "low", "medium", "high"};
    public static volatile int lcdLevel = 0;
    /** Game picture output: "auto" tries OpenGL ES and falls back to Swing, "gles" is the same but says so loudly, "swing" never uses GL. */
    public static volatile String renderer = "auto";
    public static final String[] RENDERERS = {"auto", "gles", "swing"};

    /** Accepts off/low/medium/high, 0-3, or the older true/false. Returns -1 when unrecognised. */
    public static int parseLcdLevel(String v) {
        if (v == null) return -1;
        v = v.trim().toLowerCase();
        for (int i = 0; i < LCD_NAMES.length; i++) if (LCD_NAMES[i].equals(v) || String.valueOf(i).equals(v)) return i;
        if (v.equals("true") || v.equals("on")) return 2;
        if (v.equals("false")) return 0;
        return -1;
    }
    /** Per-game options (see Profile). Defaults are what a plain J2ME phone would do. */
    public static volatile Profile activeProfile;
    public static volatile boolean network = false;        // HttpConnection / sockets: off unless the game's profile enables it
    public static volatile boolean mute = false;
    /** Raise quiet MIDI music towards a common loudness (see MidiLeveler). Profile keys: midiboost, midiboost.target (dBFS RMS). */
    public static volatile boolean midiBoost = true;
    public static volatile int midiTargetDb = -18;

    // ---- compatibility tweaks (per game, see Profile "tweak.*")
    public static volatile boolean keyRepeat = true;            // deliver keyRepeated events
    public static volatile boolean keyPressOnRepeat = false;    // ...as keyPressed instead (games that ignore keyRepeated)
    public static volatile boolean ignoreFullScreen = false;    // always behave as setFullScreenMode(true)
    public static volatile boolean ignoreRegionRepaint = false; // repaint(x,y,w,h) repaints everything
    public static volatile boolean ignoreServiceRepaints = false;
    public static volatile boolean noRepaintOnSetCurrent = false;
    public static volatile boolean syncFlush = false;           // flushGraphics waits until the frame is on screen
    public static volatile boolean softKeyFix = false;          // soft key also reaches keyPressed when it fires a Command
    public static volatile boolean swapSoftKeys = false;
    public static volatile int hasPointer = -1;                 // -1 default (true), 0 false, 1 true
    public static volatile int fpsLimit = 0;                    // 0 = unlimited
    public static volatile boolean mascotNo2DMixing = false;
    public static volatile boolean mascotIgnoreBackground = false;

    public static void resetTweaks() {
        keyRepeat = true; keyPressOnRepeat = false; ignoreFullScreen = false; ignoreRegionRepaint = false;
        ignoreServiceRepaints = false; noRepaintOnSetCurrent = false; syncFlush = false; softKeyFix = false;
        swapSoftKeys = false; hasPointer = -1; fpsLimit = 0; mascotNo2DMixing = false; mascotIgnoreBackground = false;
        KeyLayout.current = KeyLayout.NOKIA;
        Compat.reset();
    }
    public static volatile String softKeyBar = "auto";     // auto | always | never
    public static volatile int fontSmall = 10, fontMedium = 12, fontLarge = 16;
    public static volatile String fontFamily = "SansSerif";

    private static final Path FILE = Paths.get(System.getProperty("user.home"), ".config", "j2me-deck", "settings.properties");

    private Settings() {}

    /** Steam launches games with SteamGameId set; Game Mode also runs inside gamescope. */
    private static boolean detectGameMode() {
        String xdg = System.getenv("XDG_CURRENT_DESKTOP");
        return System.getenv("SteamGameId") != null || System.getenv("GAMESCOPE_WAYLAND_DISPLAY") != null
                || (xdg != null && xdg.toLowerCase().contains("gamescope"));
    }

    public static void load() {
        Properties p = new Properties();
        try (InputStream in = Files.newInputStream(FILE)) { p.load(in); } catch (IOException ignored) { return; }
        String s = p.getProperty("scale");
        if (s != null) for (int i = 0; i < SCALE_NAMES.length; i++) if (SCALE_NAMES[i].equals(s.trim())) scaleMode = i;
        int lv = parseLcdLevel(p.getProperty("lcdgrid"));
        if (lv >= 0) lcdLevel = lv;
        String rd = parseRenderer(p.getProperty("renderer"));
        if (rd != null) renderer = rd;
    }

    /** Accepts auto / gles (opengl, gl) / swing (software, java2d); null when unrecognised. */
    public static String parseRenderer(String v) {
        if (v == null) return null;
        v = v.trim().toLowerCase();
        switch (v) {
            case "auto": return "auto";
            case "gles": case "opengl": case "opengles": case "gl": return "gles";
            case "swing": case "software": case "java2d": return "swing";
            default: return null;
        }
    }

    public static void save() {
        try {
            Files.createDirectories(FILE.getParent());
            Properties p = new Properties();
            p.setProperty("scale", SCALE_NAMES[scaleMode]);
            p.setProperty("lcdgrid", LCD_NAMES[lcdLevel]);
            p.setProperty("renderer", renderer);
            try (OutputStream out = Files.newOutputStream(FILE)) { p.store(out, "J2ME Deck"); }
        } catch (IOException ignored) {}
    }

    /** F9: off -> low -> medium -> high -> off. */
    public static void cycleLcdGrid() {
        lcdLevel = (lcdLevel + 1) % LCD_NAMES.length;
        Profile pr = activeProfile;
        if (pr != null) { pr.set("lcdgrid", LCD_NAMES[lcdLevel]); try { pr.save(); } catch (IOException ignored) {} }
        else save();
    }

    public static void cycleScaleMode() {
        scaleMode = (scaleMode + 1) % SCALE_NAMES.length;
        Profile pr = activeProfile;
        if (pr != null) { pr.set("scale", SCALE_NAMES[scaleMode]); try { pr.save(); } catch (IOException ignored) {} }
        else save();
    }
}
