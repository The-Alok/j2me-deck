// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 J2ME Deck contributors
import j2medeck.*;
import j2medeck.ui.*;
import javax.microedition.lcdui.*;
import javax.microedition.lcdui.game.GameCanvas;
import java.awt.EventQueue;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;
import java.util.List;
import java.util.jar.*;

/** Device layouts, compatibility tweaks, settings round trip, profile ids. Run with -Duser.home=<scratch>. */
public class CompatTest {
    static int fails;
    static void check(boolean ok, String what) { System.out.println((ok ? "PASS  " : "FAIL  ") + what); if (!ok) fails++; }

    static final List<String> events = Collections.synchronizedList(new ArrayList<>());
    static int paints, lastClipW;

    static class TestCanvas extends GameCanvas {
        TestCanvas() { super(false); }
        protected void keyPressed(int k) { events.add("P" + k); }
        protected void keyReleased(int k) { events.add("R" + k); }
        protected void keyRepeated(int k) { events.add("T" + k); }
        public void paint(Graphics g) { paints++; lastClipW = g.getClipWidth(); super.paint(g); }
    }

    static void route(int kind, int code) throws Exception { EventQueue.invokeAndWait(() -> Display.$routeKey(kind, code)); }
    static void show(Displayable d) throws Exception { EventQueue.invokeAndWait(() -> Display.getDisplay(null).setCurrent(d)); EventQueue.invokeAndWait(() -> {}); }

    static Object get(Object o, String f) throws Exception { Field x = o.getClass().getDeclaredField(f); x.setAccessible(true); return x.get(o); }
    static void sel(Object form, String f, Object v) throws Exception {
        Object c = get(form, f);
        if (c instanceof JComboBoxLike) return;
        if (c instanceof javax.swing.JCheckBox) ((javax.swing.JCheckBox) c).setSelected((Boolean) v);
        else if (c instanceof javax.swing.JComboBox) ((javax.swing.JComboBox<?>) c).setSelectedItem(v);
        else if (c instanceof javax.swing.JSpinner) ((javax.swing.JSpinner) c).setValue(v);
        else if (c instanceof javax.swing.text.JTextComponent) ((javax.swing.text.JTextComponent) c).setText((String) v);
    }
    interface JComboBoxLike {}

    public static void main(String[] a) throws Exception {
        Display.initDisplay();
        Settings.resetTweaks();

        // ---- Motorola layout: the game sees Motorola codes, and the canvas API agrees
        KeyLayout.current = KeyLayout.MOTOROLA;
        TestCanvas c = new TestCanvas();
        show(c);
        check(c.getKeyCode(Canvas.DOWN) == -6 && c.getKeyCode(Canvas.FIRE) == -20 && c.getKeyCode(Canvas.UP) == -1, "Motorola: getKeyCode(DOWN/FIRE/UP) = -6/-20/-1");
        check(c.getGameAction(-6) == Canvas.DOWN && c.getGameAction(-20) == Canvas.FIRE && c.getGameAction(-2) == Canvas.LEFT, "Motorola: getGameAction maps device codes (-6 DOWN, -20 FIRE, -2 LEFT)");
        events.clear();
        route(0, Canvas.KEY_DOWN); route(1, Canvas.KEY_DOWN); route(0, Canvas.KEY_FIRE); route(1, Canvas.KEY_FIRE); route(0, Canvas.KEY_SOFT_LEFT);
        check(events.equals(List.of("P-6", "R-6", "P-20", "R-20", "P-21")), "Motorola: canonical DOWN/FIRE/SOFT_LEFT arrive as -6/-20/-21: " + events);
        route(1, Canvas.KEY_SOFT_LEFT);
        route(0, Canvas.KEY_DOWN);
        check((c.getKeyStates() & GameCanvas.DOWN_PRESSED) != 0, "Motorola: getKeyStates reports DOWN for the translated key");
        route(1, Canvas.KEY_DOWN);

        KeyLayout.current = KeyLayout.SIEMENS;
        events.clear(); route(0, Canvas.KEY_UP); route(0, Canvas.KEY_SOFT_RIGHT);
        check(events.equals(List.of("P-59", "P-4")), "Siemens: UP -> -59, right soft key -> -4: " + events);
        route(1, Canvas.KEY_UP); route(1, Canvas.KEY_SOFT_RIGHT);
        KeyLayout.current = KeyLayout.NOKIA;

        // ---- input tweaks
        events.clear(); route(2, Canvas.KEY_LEFT);
        check(events.equals(List.of("T-3")), "default: key repeat reaches keyRepeated");
        Settings.keyRepeat = false; events.clear(); route(2, Canvas.KEY_LEFT);
        check(events.isEmpty(), "tweak keyrepeat=false: repeats are dropped");
        Settings.keyRepeat = true; Settings.keyPressOnRepeat = true; events.clear(); route(2, Canvas.KEY_LEFT);
        check(events.equals(List.of("P-3")), "tweak keypressonrepeat: repeat arrives as keyPressed");
        Settings.keyPressOnRepeat = false;
        Settings.swapSoftKeys = true; events.clear(); route(0, Canvas.KEY_SOFT_LEFT); route(1, Canvas.KEY_SOFT_LEFT);
        check(events.equals(List.of("P-7", "R-7")), "tweak swapsoftkeys: left soft key arrives as -7");
        Settings.swapSoftKeys = false;

        // ---- soft key bar + softKeyFix
        TestCanvas withCmd = new TestCanvas();
        final List<String> cmds = Collections.synchronizedList(new ArrayList<>());
        withCmd.addCommand(new Command("Menu", Command.OK, 1));
        withCmd.setCommandListener((cmd, d) -> cmds.add(cmd.getLabel()));
        show(withCmd);
        events.clear(); route(0, Canvas.KEY_SOFT_LEFT); route(1, Canvas.KEY_SOFT_LEFT);
        check(cmds.equals(List.of("Menu")) && events.isEmpty(), "canvas with a Command: left soft key fires the command, not keyPressed");
        Settings.softKeyFix = true; cmds.clear(); events.clear(); route(0, Canvas.KEY_SOFT_LEFT); route(1, Canvas.KEY_SOFT_LEFT);
        check(cmds.equals(List.of("Menu")) && events.equals(List.of("P-6", "R-6")), "tweak softkeyfix: command AND keyPressed/Released: " + events);
        Settings.softKeyFix = false;
        withCmd.setFullScreenMode(true); EventQueue.invokeAndWait(() -> {}); EventQueue.invokeAndWait(() -> {});
        events.clear(); cmds.clear(); route(0, Canvas.KEY_SOFT_LEFT); route(1, Canvas.KEY_SOFT_LEFT);
        check(cmds.isEmpty() && events.equals(List.of("P-6", "R-6")), "full-screen canvas: soft key goes to the game again");

        // ---- drawing tweaks
        show(c);
        for (int i = 0; i < 3; i++) EventQueue.invokeAndWait(() -> {});
        paints = 0; c.repaint(0, 0, 10, 10); c.serviceRepaints();
        check(paints == 1 && lastClipW == 10, "default: repaint(x,y,w,h) paints just that region (clip " + lastClipW + ")");
        Settings.ignoreRegionRepaint = true; paints = 0; c.repaint(0, 0, 10, 10); c.serviceRepaints();
        check(paints == 1 && lastClipW == c.getWidth(), "tweak ignoreregionrepaint: whole screen repainted (clip " + lastClipW + ")");
        Settings.ignoreRegionRepaint = false;
        Settings.ignoreServiceRepaints = true;
        // Run on the event thread so the asynchronous repaint cannot slip in before we read the counter (this made the test flaky).
        final int[] directBox = new int[1];
        EventQueue.invokeAndWait(() -> { paints = 0; c.repaint(); c.serviceRepaints(); directBox[0] = paints; });
        int direct = directBox[0];
        EventQueue.invokeAndWait(() -> {}); EventQueue.invokeAndWait(() -> {});
        check(direct == 0 && paints == 1, "tweak ignoreservicerepaints: serviceRepaints() does nothing, the normal repaint still happens");
        Settings.ignoreServiceRepaints = false;
        Settings.ignoreFullScreen = true; c.setFullScreenMode(false);
        check(c.isFullScreenMode(), "tweak ignorefullscreen: setFullScreenMode(false) stays full screen");
        Settings.ignoreFullScreen = false;
        Settings.hasPointer = 0;
        check(!c.hasPointerEvents() && !c.hasPointerMotionEvents(), "tweak haspointer=No: hasPointerEvents() is false");
        Settings.hasPointer = -1;
        Settings.noRepaintOnSetCurrent = true; paints = 0;
        show(new TestCanvas() {}); for (int i = 0; i < 3; i++) EventQueue.invokeAndWait(() -> {});
        check(paints == 0, "tweak norepaintonsetcurrent: showing a canvas doesn't paint it automatically");
        Settings.noRepaintOnSetCurrent = false;

        // ---- frame rate limit
        TestCanvas fc = new TestCanvas(); show(fc);
        Settings.fpsLimit = 20;
        long t0 = System.nanoTime();
        for (int i = 0; i < 11; i++) fc.flushGraphics();
        long ms = (System.nanoTime() - t0) / 1_000_000;
        check(ms >= 450 && ms < 800, "fps limit 20: 11 flushes take ~500 ms (" + ms + " ms)");
        Settings.fpsLimit = 0;
        t0 = System.nanoTime(); for (int i = 0; i < 11; i++) fc.flushGraphics();
        check((System.nanoTime() - t0) / 1_000_000 < 150, "no limit: flushes are not delayed");

        // ---- settings form round trip (fields poked by reflection, then saved and applied)
        Path cfgHome = Paths.get(System.getProperty("user.home"));
        Path jar = cfgHome.resolve("Game A.jar");
        makeJar(jar, "Same Name", "VendorA", "A");
        GameInfo gi = GameInfo.read(jar);
        Object form = GameSettingsDialog.form(gi);
        javax.swing.JComboBox<?> dev = (javax.swing.JComboBox<?>) get(form, "devicePreset");
        EventQueue.invokeAndWait(() -> dev.setSelectedItem("Motorola RAZR V3 (176x220)"));
        sel(form, "keyPressOnRepeat", true); sel(form, "swapSoft", true); sel(form, "ignoreRegion", true);
        sel(form, "speed", "2"); sel(form, "fps", 30); sel(form, "heap", 24); sel(form, "ignoreGc", true); sel(form, "network", true);
        sel(form, "hasPointer", "No"); sel(form, "lcdgrid", "High"); sel(form, "mascotNoMix", true);
        form.getClass().getMethod("save").invoke(form);
        Profile p = Profile.load(gi.id());
        check(p.get("preset", "").startsWith("Motorola RAZR V3") && p.screenWidth() == 176 && p.screenHeight() == 220, "form saves the device preset and its screen size");
        p.applyTo();
        check(KeyLayout.current == KeyLayout.MOTOROLA, "applied profile selects the Motorola key layout from the preset");
        check("V3".equals(System.getProperty("microedition.platform")), "applied profile sets microedition.platform from the preset");
        check(Settings.keyPressOnRepeat && Settings.swapSoftKeys && Settings.ignoreRegionRepaint && Settings.hasPointer == 0 && Settings.fpsLimit == 30 && Settings.mascotNo2DMixing, "applied profile sets the tweak flags and fps limit");
        check(Compat.speed() == 2.0 && Compat.ignoreGc && Compat.heapMb == 24 && Settings.network && Settings.lcdLevel == 3, "applied profile sets speed 2x, ignore gc, 24 MB heap, network, LCD grid high");
        check(!p.p.containsKey("tweak.keyrepeat") && !p.p.containsKey("patch.ignoresleep") && !p.p.containsKey("font.small"), "defaults are not written to the file");
        Settings.resetTweaks();
        check(KeyLayout.current == KeyLayout.NOKIA && Compat.speed() == 1.0 && !Compat.ignoreGc, "resetTweaks restores every default");

        // ---- two different games with the same MIDlet-Name get separate profiles
        Library.add(jar);
        Path jar2 = cfgHome.resolve("Game B.jar");
        makeJar(jar2, "Same Name", "VendorB", "B-different-content");
        GameInfo b = Library.add(jar2);
        List<GameInfo> lib = Library.list();
        Set<String> ids = new HashSet<>(); for (GameInfo g : lib) ids.add(g.id());
        check(lib.size() == 2 && ids.size() == 2, "library holds two games with one MIDlet-Name and gives them different profile ids: " + ids);
        Library.remove(b, false);
        check(!Files.exists(b.jar.resolveSibling(b.jar.getFileName() + ".id")), "removing a game removes its id file");

        // ---- log rotation
        GameInfo g1 = Library.list().get(0);
        Path log = Library.rotateLog(g1);
        Files.writeString(log, "first run\n");
        Path log2 = Library.rotateLog(g1);
        check(!Files.exists(log2) && Files.readString(Library.LOGS.resolve(g1.id() + ".old.log")).equals("first run\n"), "log rotation keeps the previous run as .old.log");

        System.out.println(fails == 0 ? "ALL PASSED" : fails + " FAILED");
        System.exit(fails == 0 ? 0 : 1);
    }

    static void makeJar(Path jar, String name, String vendor, String payload) throws Exception {
        Manifest mf = new Manifest(); mf.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
        mf.getMainAttributes().putValue("MIDlet-Name", name); mf.getMainAttributes().putValue("MIDlet-Vendor", vendor);
        mf.getMainAttributes().putValue("MIDlet-1", name + ", , Main");
        try (JarOutputStream jo = new JarOutputStream(Files.newOutputStream(jar), mf)) { jo.putNextEntry(new JarEntry("payload.txt")); jo.write(payload.getBytes()); jo.closeEntry(); }
    }
}
