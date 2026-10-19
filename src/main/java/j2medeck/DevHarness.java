// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 J2ME Deck contributors
package j2medeck;

import javax.imageio.ImageIO;
import javax.microedition.lcdui.Canvas;
import javax.microedition.lcdui.Display;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.io.File;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Scriptable test driver (--script "wait 3000;shot a.png;tap FIRE;..."), used for headless regression runs.
 * Commands: wait MS | shot FILE | tap KEY [MS] | press KEY | release KEY | fps MS | exit
 * KEY is a MIDP key code or a name (UP DOWN LEFT RIGHT FIRE SOFT_LEFT SOFT_RIGHT CLEAR STAR POUND 0-9).
 */
final class DevHarness {
    private DevHarness() {}

    static void run(String script) {
        Thread t = new Thread(() -> {
            try {
                for (String raw : script.split("[;\\n]")) {
                    String cmd = raw.trim();
                    if (cmd.isEmpty() || cmd.startsWith("#")) continue;
                    String[] p = cmd.split("\\s+");
                    switch (p[0]) {
                        case "wait": Thread.sleep(Long.parseLong(p[1])); break;
                        case "shot": shot(p[1]); break;
                        case "tap": {
                            int k = key(p[1]);
                            InputManager.get().press(k);
                            Thread.sleep(p.length > 2 ? Long.parseLong(p[2]) : 120);
                            InputManager.get().release(k);
                            Thread.sleep(80);
                            break;
                        }
                        case "press": InputManager.get().press(key(p[1])); break;
                        case "release": InputManager.get().release(key(p[1])); break;
                        case "fps": fps(Long.parseLong(p[1])); break;
                        case "exit": LinuxLoader.exit(0); return;
                        default: System.err.println("[script] unknown command: " + cmd);
                    }
                }
            } catch (Throwable e) { e.printStackTrace(); }
            LinuxLoader.exit(0);
        }, "dev-script");
        t.setDaemon(true);
        t.start();
    }

    private static int key(String s) {
        int k = GamepadReader.parseKey(s);
        return k != Integer.MIN_VALUE ? k : Integer.parseInt(s);
    }

    private static Canvas canvas() {
        Display d = Display.getInstance();
        return d != null && d.getCurrent() instanceof Canvas ? (Canvas) d.getCurrent() : null;
    }

    private static void shot(String file) throws Exception {
        Canvas c = canvas();
        if (c == null) { System.err.println("[script] no canvas to capture"); return; }
        BufferedImage img = new BufferedImage(c.getWidth(), c.getHeight(), BufferedImage.TYPE_INT_RGB);
        c.$copyFrame(((DataBufferInt) img.getRaster().getDataBuffer()).getData());
        ImageIO.write(img, "png", new File(file));
        System.err.println("[script] saved " + file);
    }

    private static void fps(long ms) throws InterruptedException {
        Canvas c = canvas();
        if (c == null) return;
        AtomicLong n = new AtomicLong();
        c.$setFrameListener(n::incrementAndGet);
        Thread.sleep(ms);
        c.$setFrameListener(null);
        System.err.printf("[script] %.1f frames/s%n", n.get() * 1000.0 / ms);
    }
}
