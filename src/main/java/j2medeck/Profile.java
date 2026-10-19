// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 J2ME Deck contributors
package j2medeck;

import java.io.*;
import java.nio.file.*;
import java.util.*;

/**
 * Per-game configuration, stored next to the key-mapping files in
 * ~/.config/j2me-deck/profiles/&lt;game-id&gt;/game.properties (id = sanitized MIDlet-Name, same as KeyMapper).
 *
 * Global defaults live in ~/.config/j2me-deck/global/game.properties (same keys). A game profile only stores what it
 * changes; everything else falls back to the global file (see {@link #global}), then to the built-in default.
 *
 * Keys: width, height (virtual screen; 0 = default), scale (sharp-bilinear|nearest|integer|stretch),
 * fullscreen (default|true|false), windowscale, lcdgrid (off|low|medium|high), renderer (auto|gles|swing), autopause, network, softkeybar (auto|always|never), mute, midiboost (default true), midiboost.target (dBFS, default -18),
 * font.small/font.medium/font.large (pixel sizes), font.family, sysprop.NAME=value, appprop.NAME=value.
 */
public final class Profile {
    public static final Path CONFIG = Paths.get(System.getProperty("user.home"), ".config", "j2me-deck");

    public static final Path GLOBAL_DIR = CONFIG.resolve("global");

    /** Game id, or null for the global defaults profile. */
    public final String id;
    /** Global defaults this profile falls back to (null for the global profile itself). */
    public final Properties global;
    /** This profile's own values; lookups fall back to {@link #global} automatically. */
    public final Properties p;

    private Profile(String id, Properties global) { this.id = id; this.global = global; this.p = global == null ? new Properties() : new Properties(global); }

    public boolean isGlobal() { return id == null; }

    public static String idFor(String title) {
        return (title == null || title.isBlank() ? "default" : title).replaceAll("[^A-Za-z0-9._-]", "_");
    }
    public static Path dir(String id) { return CONFIG.resolve("profiles").resolve(id); }
    public Path file() { return isGlobal() ? GLOBAL_DIR.resolve("game.properties") : dir(id).resolve("game.properties"); }

    /** The global defaults (what every game uses unless its own profile says otherwise). */
    public static Profile loadGlobal() {
        Profile pr = new Profile(null, null);
        try (InputStream in = Files.newInputStream(pr.file())) { pr.p.load(in); } catch (IOException ignored) {}
        return pr;
    }

    /** A game's profile: its own values layered over the global defaults. */
    public static Profile load(String id) {
        Profile pr = new Profile(id, loadGlobal().p);
        try (InputStream in = Files.newInputStream(pr.file())) { pr.p.load(in); } catch (IOException ignored) {}
        return pr;
    }

    public void save() throws IOException {
        Files.createDirectories(file().getParent());
        try (OutputStream out = Files.newOutputStream(file())) { p.store(out, isGlobal() ? "J2ME Deck global settings (defaults for every game)" : "J2ME Deck per-game settings"); }
    }

    /** True when the global defaults define this key (a per-game profile then inherits it unless it overrides it). */
    public boolean inheritsGlobal(String k) { return global != null && global.getProperty(k) != null; }
    /** The value this key would have without this profile's own entry: global default, else {@code def}. */
    public String baseValue(String k, String def) { String v = global == null ? null : global.getProperty(k); return v == null ? def : v; }

    public void reset() throws IOException { p.clear(); Files.deleteIfExists(file()); }

    public int getInt(String k, int def) {
        try { return Integer.parseInt(p.getProperty(k, "").trim()); } catch (NumberFormatException e) { return def; }
    }
    public String get(String k, String def) { String v = p.getProperty(k); return v == null || v.isBlank() ? def : v.trim(); }
    public Boolean getBool(String k) {
        String v = p.getProperty(k);
        if (v == null) return null;
        v = v.trim().toLowerCase();
        return v.equals("true") || v.equals("yes") || v.equals("1") ? Boolean.TRUE
                : v.equals("false") || v.equals("no") || v.equals("0") ? Boolean.FALSE : null;
    }
    public void set(String k, Object v) { if (v == null || v.toString().isBlank()) p.remove(k); else p.setProperty(k, v.toString()); }

    public Map<String, String> prefixed(String prefix) {
        Map<String, String> m = new LinkedHashMap<>();
        for (String k : new TreeSet<>(p.stringPropertyNames())) if (k.startsWith(prefix)) m.put(k.substring(prefix.length()), p.getProperty(k));
        return m;
    }

    /** The device preset chosen for this game, or null. */
    public DevicePresets.Preset preset() { return DevicePresets.byName(get("preset", null)); }

    /** Screen size: explicit width/height, else the preset's, else 0 (loader default). */
    public int screenWidth() { return getInt("width", preset() != null ? preset().width : 0); }
    public int screenHeight() { return getInt("height", preset() != null ? preset().height : 0); }

    private boolean flag(String key, boolean def) { Boolean b = getBool(key); return b == null ? def : b; }

    /** Push this profile into the process-wide runtime settings (call before the game starts). */
    public void applyTo() {
        Settings.activeProfile = this;
        Settings.resetTweaks();
        DevicePresets.Preset pre = preset();
        KeyLayout lay = KeyLayout.byName(get("layout", null));
        KeyLayout.current = lay != null ? lay : pre != null ? pre.layout : KeyLayout.NOKIA;
        if (pre != null) {
            if (pre.platform != null) System.setProperty("microedition.platform", pre.platform);
            if (pre.forceFullscreen) Settings.ignoreFullScreen = true;
        }
        if (get("locale", null) != null) System.setProperty("microedition.locale", get("locale", null));
        if (get("encoding", null) != null) System.setProperty("microedition.encoding", get("encoding", null));
        Settings.keyRepeat = flag("tweak.keyrepeat", true);
        Settings.keyPressOnRepeat = flag("tweak.keypressonrepeat", false);
        Settings.ignoreFullScreen |= flag("tweak.ignorefullscreen", false);
        Settings.ignoreRegionRepaint = flag("tweak.ignoreregionrepaint", false);
        Settings.ignoreServiceRepaints = flag("tweak.ignoreservicerepaints", false);
        Settings.noRepaintOnSetCurrent = flag("tweak.norepaintonsetcurrent", false);
        Settings.syncFlush = flag("tweak.syncflush", false);
        Settings.softKeyFix = flag("tweak.softkeyfix", false);
        Settings.swapSoftKeys = flag("tweak.swapsoftkeys", false);
        Boolean hp = getBool("tweak.haspointer");
        Settings.hasPointer = hp == null ? -1 : hp ? 1 : 0;
        Settings.fpsLimit = Math.max(0, getInt("fps", 0));
        Settings.mascotNo2DMixing = flag("mascot.no2dmixing", false);
        Settings.mascotIgnoreBackground = flag("mascot.ignorebackground", false);
        Compat.ignoreGc = flag("patch.ignoregc", false);
        Compat.ignoreSleep = flag("patch.ignoresleep", false);
        Compat.yieldSleeps = flag("patch.yieldsleep", false);
        Compat.heapMb = Math.max(0, getInt("heapmb", 0));
        try { String sp = get("speed", null); if (sp != null) Compat.setSpeed(Double.parseDouble(sp)); } catch (NumberFormatException ignored) {}
        String sc = get("scale", null);
        if (sc != null) for (int i = 0; i < Settings.SCALE_NAMES.length; i++) if (Settings.SCALE_NAMES[i].equals(sc)) Settings.scaleMode = i;
        int lg = Settings.parseLcdLevel(p.getProperty("lcdgrid"));
        if (lg >= 0) Settings.lcdLevel = lg;
        String rd = Settings.parseRenderer(p.getProperty("renderer"));
        if (rd != null) Settings.renderer = rd;
        Boolean fs = getBool("fullscreen");
        if (fs != null) Settings.fullscreen = fs;
        Boolean ap = getBool("autopause");
        if (ap != null) Settings.autoPause = ap;
        int ws = getInt("windowscale", 0);
        if (ws > 0) Settings.windowScale = ws;
        Settings.network = Boolean.TRUE.equals(getBool("network"));
        Settings.mute = Boolean.TRUE.equals(getBool("mute"));
        Settings.midiBoost = !Boolean.FALSE.equals(getBool("midiboost"));
        Settings.midiTargetDb = Math.max(-40, Math.min(-6, getInt("midiboost.target", -18)));
        Settings.softKeyBar = get("softkeybar", "auto");
        Settings.fontSmall = getInt("font.small", Settings.fontSmall);
        Settings.fontMedium = getInt("font.medium", Settings.fontMedium);
        Settings.fontLarge = getInt("font.large", Settings.fontLarge);
        Settings.fontFamily = get("font.family", Settings.fontFamily);
        for (Map.Entry<String, String> e : prefixed("sysprop.").entrySet()) System.setProperty(e.getKey(), e.getValue());
    }
}
