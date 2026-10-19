// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 J2ME Deck contributors
import j2medeck.*;
import j2medeck.ui.GameSettingsDialog;

import javax.swing.*;
import java.awt.event.KeyEvent;
import java.lang.reflect.Field;
import java.nio.file.*;
import java.util.Properties;

/** Global settings / key map defaults and per-game overrides. */
public class GlobalConfigTest {
    static int fails;
    static void check(boolean ok, String name) { System.out.println((ok ? "ok    " : "FAIL  ") + name); if (!ok) fails++; }

    @SuppressWarnings("unchecked") static <T> T field(Object o, String name) throws Exception { Field f = o.getClass().getDeclaredField(name); f.setAccessible(true); return (T) f.get(o); }
    static String read(Path p) throws Exception { return Files.exists(p) ? Files.readString(p) : ""; }
    static boolean hasKey(Path p, String key) throws Exception { Properties x = new Properties(); if (Files.exists(p)) try (var in = Files.newInputStream(p)) { x.load(in); } return x.containsKey(key); }
    static KeyEvent key(int vk) { return new KeyEvent(new JPanel(), KeyEvent.KEY_PRESSED, 0, 0, vk, (char) vk); }

    public static void main(String[] a) throws Exception {
        Path home = Files.createTempDirectory("j2me-global-test");
        System.setProperty("user.home", home.toString());          // before any config class is loaded
        System.setProperty("java.awt.headless", "true");
        Path gameFile = Profile.dir("Game_A").resolve("game.properties"), globalFile = Profile.GLOBAL_DIR.resolve("game.properties");

        // ---- settings layering
        Profile g = Profile.loadGlobal();
        g.set("network", "true"); g.set("font.small", "14"); g.set("sysprop.microedition.locale", "de-DE"); g.set("scale", "nearest");
        g.save();
        Profile a1 = Profile.load("Game_A");
        check(Boolean.TRUE.equals(a1.getBool("network")) && a1.getInt("font.small", 0) == 14, "a game inherits global settings");
        check("de-DE".equals(a1.prefixed("sysprop.").get("microedition.locale")), "a game inherits global system properties");
        a1.set("font.small", "8"); a1.save();
        Profile a2 = Profile.load("Game_A");
        check(a2.getInt("font.small", 0) == 8 && Boolean.TRUE.equals(a2.getBool("network")), "a game's own value beats the global one, other keys still inherited");
        check(Profile.load("Game_B").getInt("font.small", 0) == 14, "other games keep the global value");
        a2.reset();
        check(Profile.load("Game_A").getInt("font.small", 0) == 14 && !Files.exists(gameFile), "resetting a game falls back to the global settings");
        check(Profile.loadGlobal().global == null && Profile.loadGlobal().isGlobal(), "global profile has no parent");

        // ---- settings forms
        GameSettingsDialog.Form gf = GameSettingsDialog.globalForm();
        ((JCheckBox) field(gf, "mute")).setSelected(true);
        ((JSpinner) field(gf, "fSmall")).setValue(12);
        ((JCheckBox) field(gf, "network")).setSelected(false);
        gf.save();
        check(hasKey(globalFile, "mute") && hasKey(globalFile, "font.small") && !hasKey(globalFile, "tweak.keyrepeat") && !hasKey(globalFile, "network"),
                "global form stores only non-default values (network back to default is removed)");
        check(!read(globalFile).contains("appprop."), "global settings have no per-game JAD overrides");

        g = Profile.loadGlobal(); g.set("network", "true"); g.save();
        GameSettingsDialog.Form f1 = GameSettingsDialog.form(Profile.load("Game_A"));
        check(((JCheckBox) field(f1, "network")).isSelected() && ((JCheckBox) field(f1, "mute")).isSelected() && (Integer) ((JSpinner) field(f1, "fSmall")).getValue() == 12,
                "a game's form shows the global values");
        f1.save();
        check(!Files.exists(gameFile) || (!hasKey(gameFile, "network") && !hasKey(gameFile, "mute") && !hasKey(gameFile, "font.small")),
                "saving an untouched game form does not copy the global values into the game");
        ((JCheckBox) field(f1, "network")).setSelected(false);
        ((JSpinner) field(f1, "fSmall")).setValue(10);
        f1.save();
        Profile a3 = Profile.load("Game_A");
        check(Boolean.FALSE.equals(a3.getBool("network")) && a3.getInt("font.small", 0) == 10 && Boolean.TRUE.equals(a3.getBool("mute")),
                "a game can switch a global option off and go back to the built-in size; the rest still follows global");
        check(Boolean.TRUE.equals(Profile.load("Game_B").getBool("network")), "...without touching other games");

        // ---- key maps
        System.setProperty("j2me.profileid", "Game_A");
        Path gk = home.resolve(".config/j2me-deck/global/keyboard.properties"), ak = home.resolve(".config/j2me-deck/profiles/Game_A/keyboard.properties");
        Properties gp = new Properties(); gp.setProperty("key.FIRE", "F"); gp.setProperty("key.SOFT_LEFT", "Z");
        Files.createDirectories(gk.getParent()); try (var out = Files.newOutputStream(gk)) { gp.store(out, null); }
        InputManager im = InputManager.get(); im.reloadMappings();
        check(InputManager.mapKey(key(KeyEvent.VK_F)) == javax.microedition.lcdui.Canvas.KEY_FIRE, "a global key binding works in a game without its own map");
        Properties ap = new Properties(); ap.setProperty("key.FIRE", "G");
        Files.createDirectories(ak.getParent()); try (var out = Files.newOutputStream(ak)) { ap.store(out, null); }
        im.reloadMappings();
        check(InputManager.mapKey(key(KeyEvent.VK_G)) == javax.microedition.lcdui.Canvas.KEY_FIRE && InputManager.mapKey(key(KeyEvent.VK_F)) != javax.microedition.lcdui.Canvas.KEY_FIRE,
                "a game's own binding replaces the global one");
        check(InputManager.mapKey(key(KeyEvent.VK_Z)) == javax.microedition.lcdui.Canvas.KEY_SOFT_LEFT, "other global bindings still apply in that game");
        System.setProperty("j2me.profileid", "Game_B"); im.reloadMappings();
        check(InputManager.mapKey(key(KeyEvent.VK_F)) == javax.microedition.lcdui.Canvas.KEY_FIRE && InputManager.mapKey(key(KeyEvent.VK_G)) != javax.microedition.lcdui.Canvas.KEY_FIRE,
                "other games use the global map");
        Properties merged = loadMerged();
        check("F".equals(merged.getProperty("key.FIRE")), "controller/keyboard merge helper returns the global entry for a game without overrides");
        System.clearProperty("j2me.profileid");

        // ---- key mapper helpers
        Properties own = new Properties(), base = new Properties(); base.setProperty("key.FIRE", "F");
        check("F".equals(call("effectiveKey", new Class<?>[]{Properties.class, Properties.class, String.class}, own, base, "FIRE")) && "Up".equals(call("effectiveKey", new Class<?>[]{Properties.class, Properties.class, String.class}, own, base, "UP")),
                "key mapper shows global binding, else the built-in key");
        call("storeDiff", new Class<?>[]{Properties.class, String.class, String.class, String.class}, own, "key.FIRE", "F", "F");
        check(!own.containsKey("key.FIRE"), "a binding equal to the global one is not stored in the game");
        call("storeDiff", new Class<?>[]{Properties.class, String.class, String.class, String.class}, own, "key.FIRE", "H", "F");
        check("H".equals(own.getProperty("key.FIRE")), "a changed binding is stored in the game");
        java.util.Map<String, JButton> bind = new java.util.LinkedHashMap<>(); bind.put("FIRE", new JButton("H")); bind.put("UP", new JButton("W"));
        check("FIRE".equals(call("clashWith", new Class<?>[]{java.util.Map.class, String.class, String.class}, bind, "UP", "h")) && call("clashWith", new Class<?>[]{java.util.Map.class, String.class, String.class}, bind, "UP", "W") == null,
                "binding one key twice is detected");

        System.out.println(fails == 0 ? "ALL PASSED" : fails + " FAILED");
        if (fails != 0) System.exit(1);
    }

    static Object call(String name, Class<?>[] types, Object... args) throws Exception {
        var m = KeyMapper.class.getDeclaredMethod(name, types); m.setAccessible(true); return m.invoke(null, args);
    }
    /** KeyMapper.loadMerged is package-private; reach it by reflection. */
    static Properties loadMerged() throws Exception {
        var m = KeyMapper.class.getDeclaredMethod("loadMerged", String.class); m.setAccessible(true);
        return (Properties) m.invoke(null, "keyboard.properties");
    }
}
