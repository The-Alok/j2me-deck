// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 J2ME Deck contributors
import j2medeck.*;
import com.nokia.mid.ui.*;
import javax.microedition.io.*;
import javax.microedition.io.file.*;
import javax.microedition.lcdui.*;
import javax.microedition.media.*;
import javax.microedition.media.control.ToneControl;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.jar.*;

/** Headless checks for the v8 features. Run with tools/selftest.sh (needs -Duser.home pointing at a scratch dir). */
public class FeatureTest {
    static int fails;
    static void check(boolean ok, String what) { System.out.println((ok ? "PASS  " : "FAIL  ") + what); if (!ok) fails++; }

    public static void main(String[] a) throws Exception {
        Path tmp = Files.createTempDirectory("j2me-feature");
        System.setProperty("j2me.suite", "TestVendor_TestGame");
        System.setProperty("j2me.fs.dir", tmp.resolve("fs").toString());

        // ---- network gating
        Settings.network = false;
        try { Connector.open("http://example.com/"); check(false, "http blocked when network off"); }
        catch (ConnectionNotFoundException e) { check(true, "http blocked when network off: " + e.getMessage()); }
        try { Connector.open("socket://127.0.0.1:9"); check(false, "socket blocked"); } catch (ConnectionNotFoundException e) { check(true, "socket blocked when network off"); }
        try { Connector.open("sms://+123"); check(false, "unknown protocol"); } catch (ConnectionNotFoundException e) { check(true, "unsupported protocol raises ConnectionNotFoundException"); }

        // ---- local http server proves HttpConnection works when enabled
        com.sun.net.httpserver.HttpServer srv = com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress("127.0.0.1", 0), 0);
        srv.createContext("/hello", ex -> { byte[] b = ("hi " + new String(ex.getRequestBody().readAllBytes()) + " " + ex.getRequestMethod()).getBytes(); ex.getResponseHeaders().add("X-Test", "yes"); ex.sendResponseHeaders(200, b.length); ex.getResponseBody().write(b); ex.close(); });
        srv.start();
        Settings.network = true;
        String base = "http://127.0.0.1:" + srv.getAddress().getPort();
        HttpConnection h = (HttpConnection) Connector.open(base + "/hello");
        check(h.getResponseCode() == HttpConnection.HTTP_OK && "yes".equals(h.getHeaderField("X-Test")), "HttpConnection GET with headers");
        try (InputStream in = h.openInputStream()) { check(new String(in.readAllBytes()).equals("hi  GET"), "HttpConnection body"); }
        HttpConnection p = (HttpConnection) Connector.open(base + "/hello");
        p.setRequestMethod(HttpConnection.POST);
        try (OutputStream o = p.openOutputStream()) { o.write("data".getBytes()); }
        try (InputStream in = p.openInputStream()) { check(new String(in.readAllBytes()).equals("hi data POST"), "HttpConnection POST body"); }
        srv.stop(0);
        Settings.network = false;

        // ---- sandboxed FileConnection
        FileConnection fc = (FileConnection) Connector.open("file:///root1/saves/game.dat");
        check(!fc.exists(), "file does not exist yet");
        fc.create();
        try (DataOutputStream o = fc.openDataOutputStream()) { o.writeInt(42); o.writeUTF("hello"); }
        check(fc.exists() && fc.fileSize() > 0, "file created and written");
        try (DataInputStream i = Connector.openDataInputStream("file:///root1/saves/game.dat")) { check(i.readInt() == 42 && i.readUTF().equals("hello"), "file round trip via Connector.openDataInputStream"); }
        Enumeration<?> roots = FileSystemRegistry.listRoots();
        boolean r1 = false; while (roots.hasMoreElements()) if ("root1/".equals(roots.nextElement())) r1 = true;
        check(r1, "FileSystemRegistry lists root1/");
        FileConnection dir = (FileConnection) Connector.open("file:///root1/saves/");
        Enumeration<?> l = dir.list("*.dat", false);
        check(l.hasMoreElements() && "game.dat".equals(l.nextElement()), "directory listing with filter");
        try { Connector.open("file:///root1/../../etc/passwd"); check(false, "path traversal"); } catch (IllegalArgumentException e) { check(true, "path traversal rejected"); }
        check(Files.exists(tmp.resolve("fs/TestVendor_TestGame/root1/saves/game.dat")), "files land in the per-game sandbox");

        // ---- RMS shared instance + per-suite folder
        System.setProperty("j2me.rms.dir", tmp.resolve("rms").toString());
        var rs1 = javax.microedition.rms.RecordStore.openRecordStore("scores", true);
        rs1.addRecord(new byte[]{1, 2, 3}, 0, 3);
        var rs2 = javax.microedition.rms.RecordStore.openRecordStore("scores", false);
        check(rs2.getNumRecords() == 1, "second open of a record store sees the same data");

        // ---- tone sequence -> MIDI
        byte[] tone = {ToneControl.VERSION, 1, ToneControl.TEMPO, 30, ToneControl.RESOLUTION, 64,
                ToneControl.BLOCK_START, 0, ToneControl.C4, 16, (byte) (ToneControl.C4 + 4), 16, ToneControl.BLOCK_END, 0,
                ToneControl.PLAY_BLOCK, 0, ToneControl.SILENCE, 8, ToneControl.REPEAT, 2, (byte) (ToneControl.C4 + 7), 16};
        Player tp = Manager.createPlayer(Manager.TONE_DEVICE_LOCATOR);
        tp.realize();
        ToneControl tc = (ToneControl) tp.getControl("ToneControl");
        tc.setSequence(tone);
        long dur = tp.getDuration();
        check(dur > 1_000_000 && dur < 4_000_000, "tone sequence duration is plausible: " + dur / 1000 + " ms (expected ~2 s)");
        final boolean[] ended = {false};
        tp.addPlayerListener((pl, ev, d) -> { if (PlayerListener.END_OF_MEDIA.equals(ev)) ended[0] = true; });
        tp.start();
        for (int i = 0; i < 60 && !ended[0]; i++) Thread.sleep(100);
        check(ended[0], "tone player reports END_OF_MEDIA");
        check(tp.getState() == Player.PREFETCHED, "player returns to PREFETCHED after the end");
        tp.close();

        // ---- Nokia DirectGraphics
        Image img = DirectUtils.createImage(8, 8, 0x00000000);
        Graphics g = img.getGraphics();
        DirectGraphics dg = DirectUtils.getDirectGraphics(g);
        dg.fillTriangle(0, 0, 7, 0, 0, 7, 0xFFFF0000);
        int[] px = new int[64]; img.getRGB(px, 0, 8, 0, 0, 8, 8);
        check(px[1] == 0xFFFF0000 && px[63] == 0x00000000, "fillTriangle with ARGB colour on transparent image");
        int[] block = new int[4]; Arrays.fill(block, 0xFF00FF00);
        dg.drawPixels(block, false, 0, 2, 4, 4, 2, 2, 0, DirectGraphics.TYPE_INT_888_RGB);
        int[] one = new int[1]; img.getRGB(one, 0, 1, 5, 5, 1, 1);
        check(one[0] == 0xFF00FF00, "drawPixels INT_888_RGB");
        short[] s565 = {(short) 0xF800};
        dg.drawPixels(s565, false, 0, 1, 0, 7, 1, 1, 0, DirectGraphics.TYPE_USHORT_565_RGB);
        img.getRGB(one, 0, 1, 0, 7, 1, 1);
        check(one[0] == 0xFFFF0000, "drawPixels USHORT_565 red");
        int[] back = new int[1];
        dg.getPixels(back, 0, 1, 0, 7, 1, 1, DirectGraphics.TYPE_USHORT_565_RGB == 565 ? DirectGraphics.TYPE_INT_8888_ARGB : 0);
        check(back[0] == 0xFFFF0000, "getPixels reads back what was drawn");

        // ---- LCD grid shader: 4 levels, strength increases, interiors untouched
        Canvas cv = new Canvas() { protected void paint(Graphics gg) { gg.setColor(0x80C0FF); gg.fillRect(0, 0, 240, 320); } };
        cv.$becameCurrentForTest();
        Settings.scaleMode = Settings.SCALE_STRETCH;
        int[] lineBlue = new int[4], interior = new int[4];
        for (int lvl = 0; lvl < 4; lvl++) {
            Settings.lcdLevel = lvl;
            ScreenView v = new ScreenView(cv); v.setSize(960, 1280);
            java.awt.image.BufferedImage out = new java.awt.image.BufferedImage(960, 1280, java.awt.image.BufferedImage.TYPE_INT_RGB);
            java.awt.Graphics2D g2 = out.createGraphics(); v.paint(g2); g2.dispose();
            lineBlue[lvl] = out.getRGB(8, 10) & 0xFF; interior[lvl] = out.getRGB(10, 10);
        }
        check(interior[0] == interior[1] && interior[1] == interior[3], "LCD grid leaves pixel interiors unchanged at every level");
        check(lineBlue[0] > lineBlue[1] && lineBlue[1] > lineBlue[2] && lineBlue[2] > lineBlue[3], "LCD grid lines get darker: off " + lineBlue[0] + " > low " + lineBlue[1] + " > medium " + lineBlue[2] + " > high " + lineBlue[3]);
        Settings.lcdLevel = 3; Settings.cycleLcdGrid();
        check(Settings.lcdLevel == 0, "F9 cycle wraps from high back to off");
        check(Settings.parseLcdLevel("true") == 2 && Settings.parseLcdLevel("High") == 3 && Settings.parseLcdLevel("bogus") == -1, "lcdgrid property parsing (names, numbers, legacy true/false)");
        Settings.lcdLevel = 0; Settings.scaleMode = Settings.SCALE_SHARP;

        // ---- commands -> soft-key layout
        Command ok = new Command("OK", Command.OK, 1), back2 = new Command("Back", Command.BACK, 2), help = new Command("Help", Command.HELP, 3);
        var lay = j2medeck.ui.CommandBar.layout(new Command[]{ok, back2});
        check(lay.left == ok && lay.right == back2, "two commands: OK left, Back right");
        lay = j2medeck.ui.CommandBar.layout(new Command[]{ok, help, back2});
        check(lay.right == back2 && lay.menu.size() == 2, "three commands: Back right, others in a menu");

        // ---- scanner on a synthetic game that needs Bluetooth (unsupported)
        Path stubDir = Files.createTempDirectory("stub"), gameDir = Files.createTempDirectory("game");
        Path stub = stubDir.resolve("javax/bluetooth"); Files.createDirectories(stub);
        Files.writeString(stub.resolve("LocalDevice.java"), "package javax.bluetooth; public class LocalDevice { public static LocalDevice getLocalDevice(){return null;} }");
        Files.writeString(gameDir.resolve("G3.java"), "public class G3 extends javax.microedition.midlet.MIDlet { protected void startApp(){ javax.bluetooth.LocalDevice.getLocalDevice(); } protected void pauseApp(){} protected void destroyApp(boolean b){} }");
        Files.writeString(gameDir.resolve("Ok.java"), "public class Ok extends javax.microedition.midlet.MIDlet { protected void startApp(){ javax.microedition.lcdui.Display.getDisplay(this).vibrate(100); new com.nokia.mid.ui.DirectUtils(); } protected void pauseApp(){} protected void destroyApp(boolean b){} }");
        Files.writeString(gameDir.resolve("Bad.java"), "public class Bad extends javax.microedition.midlet.MIDlet { protected void startApp(){ javax.microedition.lcdui.Display.getDisplay(this).noSuchMethodAnywhere(); } protected void pauseApp(){} protected void destroyApp(boolean b){} }");
        compile(stubDir.resolve("javax/bluetooth/LocalDevice.java"), stubDir, null);
        compile(gameDir.resolve("G3.java"), gameDir, stubDir);
        // Ok.java uses a private constructor, which is fine for scanning (compile fails, so use a stub-free variant)
        Files.writeString(gameDir.resolve("Ok.java"), "public class Ok extends javax.microedition.midlet.MIDlet { protected void startApp(){ javax.microedition.lcdui.Display.getDisplay(this).vibrate(100); com.nokia.mid.ui.DeviceControl.stopVibra(); } protected void pauseApp(){} protected void destroyApp(boolean b){} }");
        compile(gameDir.resolve("Ok.java"), gameDir, null);
        Files.writeString(gameDir.resolve("Bad.java"), "public class Bad extends javax.microedition.midlet.MIDlet { protected void startApp(){ Object o = javax.microedition.lcdui.Display.getDisplay(this); } protected void pauseApp(){} protected void destroyApp(boolean b){} }");
        String[][] cases = {{"G3", "UNSUPPORTED"}, {"Ok", "OK"}};
        for (String[] c : cases) {
            Path jar = tmp.resolve(c[0] + ".jar");
            Manifest mf = new Manifest(); mf.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
            mf.getMainAttributes().putValue("MIDlet-Name", "Test " + c[0]); mf.getMainAttributes().putValue("MIDlet-1", "Test, , " + c[0]);
            try (JarOutputStream jo = new JarOutputStream(Files.newOutputStream(jar), mf)) {
                jo.putNextEntry(new JarEntry(c[0] + ".class")); jo.write(Files.readAllBytes(gameDir.resolve(c[0] + ".class"))); jo.closeEntry();
            }
            ApiScanner.Report r = ApiScanner.scan(jar.toFile());
            check(r.status.name().equals(c[1]), "scanner: " + c[0] + " -> " + r.status + " (" + r.summary() + ")");
        }

        // ---- JAD merge + profile round trip
        Path jarSrc = tmp.resolve("Ok.jar");
        Path jarCopy = tmp.resolve("Renamed.jar");
        Files.copy(jarSrc, jarCopy);
        Files.writeString(tmp.resolve("Renamed.jad"), "MIDlet-Name: From JAD\nMIDlet-Vendor: Acme\n");
        GameInfo gi = GameInfo.read(jarCopy);
        check(gi.name().equals("From JAD") && gi.vendor().equals("Acme") && gi.mainClass(1).equals("Ok"), "JAD overrides manifest, MIDlet class still read from the jar");
        check(gi.id().equals("From_JAD"), "profile id is sanitized MIDlet-Name (same scheme as the key mapper)");

        System.out.println(fails == 0 ? "ALL PASSED" : fails + " FAILED");
        System.exit(fails == 0 ? 0 : 1);
    }

    static void compile(Path src, Path out, Path cp) throws Exception {
        java.util.List<String> args = new ArrayList<>(java.util.List.of("-nowarn", "-d", out.toString(), "-cp", System.getProperty("java.class.path") + (cp == null ? "" : File.pathSeparator + cp)));
        args.add(src.toString());
        int rc = com.sun.tools.javac.Main.compile(args.toArray(new String[0]));
        if (rc != 0) throw new IllegalStateException("javac failed for " + src);
    }
}
