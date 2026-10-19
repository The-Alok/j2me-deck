// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 J2ME Deck contributors
import j2medeck.*;
import javax.microedition.lcdui.Canvas;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;

/** Feeds raw joystick events through GamepadReader and checks the MIDP key codes produced. Run with -Duser.home=<scratch>. */
public class PadTest {
    static int fails;
    static void check(boolean ok, String what) { System.out.println((ok ? "PASS  " : "FAIL  ") + what); if (!ok) fails++; }

    static final ByteArrayOutputStream buf = new ByteArrayOutputStream();
    static void ev(int value, int type, int num) { buf.write(0); buf.write(0); buf.write(0); buf.write(0); buf.write(value & 0xFF); buf.write((value >> 8) & 0xFF); buf.write(type); buf.write(num); }
    static final BlockingQueue<String> out = new LinkedBlockingQueue<>();

    static List<String> run() throws Exception {
        out.clear();
        GamepadReader.feedForTest(new ByteArrayInputStream(buf.toByteArray()));
        Thread.sleep(300);                      // sink events hop through the UI thread
        List<String> l = new ArrayList<>(out);
        buf.reset();
        return l;
    }

    public static void main(String[] a) throws Exception {
        Path cfg = Paths.get(System.getProperty("user.home"), ".config/j2me-deck/profiles/default");
        Files.createDirectories(cfg);
        Files.writeString(cfg.resolve("gamepad.properties"), "button.16=1\nbutton.17=3\nbutton.18=STAR\nbutton.19=POUND\nbutton.20=7\nbutton.21=9\nstick.invertx=true\n");
        // the dispatch path hands codes to the library sink; collect them here
        InputManager.setSink(code -> out.add(String.valueOf(code)));

        // left trigger: rests at -32767 (xpad style), pulled fully, released
        ev(-32767, 0x82, 2); ev(-32767, 2, 2); ev(32767, 2, 2); ev(-32767, 2, 2);
        List<String> r = run();
        check(r.equals(List.of("49")), "LT pull on a -32767-rest trigger presses '1' once: " + r);

        // right trigger on a pad whose triggers rest at 0
        ev(0, 0x82, 5); ev(0, 2, 5); ev(30000, 2, 5); ev(0, 2, 5);
        r = run();
        check(r.equals(List.of("51")), "RT pull on a 0-rest trigger presses '3': " + r);

        // a light touch below the threshold does nothing
        ev(-32767, 0x82, 2); ev(-30000, 2, 2); ev(-32767, 2, 2);
        r = run();
        check(r.isEmpty(), "small trigger movement is ignored: " + r);

        // right stick: left, right, up, down map to the four virtual buttons
        ev(-30000, 2, 3); ev(0, 2, 3); ev(30000, 2, 3); ev(0, 2, 3); ev(-30000, 2, 4); ev(0, 2, 4); ev(30000, 2, 4); ev(0, 2, 4);
        r = run();
        check(r.equals(List.of("42", "35", "55", "57")), "right stick left/right/up/down -> * # 7 9: " + r);

        // hysteresis: dithering just above the release threshold doesn't chatter
        ev(30000, 2, 3); ev(12000, 2, 3); ev(11000, 2, 3); ev(30000, 2, 3); ev(0, 2, 3);
        r = run();
        check(r.equals(List.of("35")), "stick hysteresis: one press while held near the edge: " + r);

        // left stick with stick.invertx=true: pushing right yields LEFT
        ev(30000, 2, 0); ev(0, 2, 0);
        r = run();
        check(r.equals(List.of(String.valueOf(Canvas.KEY_LEFT))), "stick.invertx swaps left/right: " + r);

        // normal buttons and d-pad still work
        ev(1, 1, 0); ev(0, 1, 0); ev(32767, 2, 7); ev(0, 2, 7);
        r = run();
        check(r.equals(List.of(String.valueOf(Canvas.KEY_FIRE), String.valueOf(Canvas.KEY_DOWN))), "A -> FIRE, d-pad down -> DOWN: " + r);

        // Steam Deck / Xbox-style View and Menu buttons should behave like Back and Select
        // in the library UI, rather than opening game details or doing nothing.
        ev(1, 1, 6); ev(0, 1, 6); ev(1, 1, 7); ev(0, 1, 7);
        r = run();
        check(r.equals(List.of(String.valueOf(Canvas.KEY_CLEAR), String.valueOf(Canvas.KEY_FIRE))), "View -> Back and Menu/Start -> Select: " + r);

        check(GamepadReader.BUTTON_COUNT == 22 && GamepadReader.buttonLabel(17).startsWith("Right trigger") && GamepadReader.buttonLabel(0).contains("A"), "mapper labels exist for all " + GamepadReader.BUTTON_COUNT + " inputs");

        System.out.println(fails == 0 ? "ALL PASSED" : fails + " FAILED");
        System.exit(fails == 0 ? 0 : 1);
    }
}
