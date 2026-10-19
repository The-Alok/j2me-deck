// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 J2ME Deck contributors
package j2medeck;

import javax.microedition.lcdui.Canvas;
import java.io.*;
import java.nio.file.*;
import java.util.*;

/**
 * Reads gamepads through the Linux joystick interface (/dev/input/js*), no native code needed.
 * Steam Input's virtual Xbox controller (Steam Deck Game Mode) shows up there when the kernel's joydev
 * module is loaded. D-pad and left stick map to the arrow keys; buttons are configurable in
 * ~/.config/j2me-deck/gamepad.properties (button.N=NAME, dpad=arrows|digits|both).
 */
public final class GamepadReader {
    private static final int T_BUTTON = 1, T_AXIS = 2, T_INIT = 0x80;
    /** 0..15 are physical joystick buttons; 16 = LT, 17 = RT, 18..21 = right stick left/right/up/down (virtual buttons). */
    public static final int BUTTON_COUNT = 22;
    private static final String[] BUTTON_NAMES = {"A", "B", "X", "Y", "LB", "RB", "View / Back", "Menu / Start", "Guide", "L3 (left stick click)", "R3 (right stick click)"};

    public static String buttonLabel(int i) {
        if (i < BUTTON_NAMES.length) return "Controller button " + i + "  (" + BUTTON_NAMES[i] + ")";
        if (i < 16) return "Controller button " + i + "  (extra / back paddle)";
        String[] v = {"Left trigger (LT)", "Right trigger (RT)", "Right stick left", "Right stick right", "Right stick up", "Right stick down"};
        return v[i - 16];
    }

    // Xbox-style layout as exposed by Steam's virtual controller / xpad.
    private static final int[] DEFAULT_BUTTONS = new int[BUTTON_COUNT];
    static {
        Arrays.fill(DEFAULT_BUTTONS, 0);
        DEFAULT_BUTTONS[0] = Canvas.KEY_FIRE;        // A
        DEFAULT_BUTTONS[1] = Canvas.KEY_SOFT_RIGHT;  // B
        DEFAULT_BUTTONS[2] = Canvas.KEY_SOFT_LEFT;   // X
        DEFAULT_BUTTONS[3] = Canvas.KEY_NUM5;        // Y
        DEFAULT_BUTTONS[4] = Canvas.KEY_STAR;        // LB
        DEFAULT_BUTTONS[5] = Canvas.KEY_POUND;       // RB
        DEFAULT_BUTTONS[6] = Canvas.KEY_CLEAR;      // View / Back
        DEFAULT_BUTTONS[7] = Canvas.KEY_FIRE;       // Menu / Start (activate / select)
    }

    private static volatile GamepadReader active;
    /** When non-null, raw physical buttons are routed to the library UI, bypassing game key mappings. */
    private static volatile java.util.function.IntConsumer uiButtonSink;
    public static void setUiButtonSink(java.util.function.IntConsumer sink) { uiButtonSink = sink; }
    private final int[] buttons = DEFAULT_BUTTONS.clone();
    private boolean dpadArrows = true, dpadDigits = false;
    // tunables (gamepad.properties: stick.threshold, trigger.threshold, stick.invertx, stick.inverty), fractions of full travel
    private volatile int stickOn = 16000, stickOff = 9000, trigOn = 16000, trigOff = 8000;
    private volatile boolean invertX, invertY, rInvertX, rInvertY;      // left / right stick
    private final Set<String> open = Collections.synchronizedSet(new HashSet<>());
    private final InputManager input = InputManager.get();

    /** True while at least one controller is open (shown as an indicator in the library's top bar). */
    public static boolean connected() { GamepadReader r = active; return r != null && !r.open.isEmpty(); }

    public static void start() {
        GamepadReader r = new GamepadReader();
        active = r;
        r.loadConfig();
        Thread t = new Thread(r::scanLoop, "gamepad-scan");
        t.setDaemon(true);
        t.start();
    }

    private void loadConfig() {
        Arrays.fill(buttons, 0);
        invertX = invertY = rInvertX = rInvertY = false; stickOn = 16000; stickOff = 9000; trigOn = 16000; trigOff = 8000;
        buttons[0]=Canvas.KEY_FIRE; buttons[1]=Canvas.KEY_SOFT_RIGHT; buttons[2]=Canvas.KEY_SOFT_LEFT; buttons[3]=Canvas.KEY_NUM5; buttons[4]=Canvas.KEY_STAR; buttons[5]=Canvas.KEY_POUND; buttons[6]=Canvas.KEY_CLEAR; buttons[7]=Canvas.KEY_FIRE;
        Properties pr = KeyMapper.loadMerged("gamepad.properties");     // global defaults, overridden by this game's own entries
        for (String k : pr.stringPropertyNames()) {
            String v = pr.getProperty(k).trim();
            if (k.startsWith("button.")) {
                try {
                    int n = Integer.parseInt(k.substring(7));
                    int code = parseKey(v);
                    if (n >= 0 && n < buttons.length && code != Integer.MIN_VALUE) buttons[n] = code;
                } catch (NumberFormatException ignored) {}
            } else if (k.equals("stick.threshold")) {
                try { double f = Math.max(0.1, Math.min(0.9, Double.parseDouble(v))); stickOn = (int) (f * 32767); stickOff = (int) (f * 0.55 * 32767); } catch (NumberFormatException ignored) {}
            } else if (k.equals("trigger.threshold")) {
                try { double f = Math.max(0.1, Math.min(0.9, Double.parseDouble(v))); trigOn = (int) (f * 65534); trigOff = (int) (f * 0.5 * 65534); } catch (NumberFormatException ignored) {}
            } else if (k.equals("stick.invertx")) { invertX = v.equalsIgnoreCase("true");
            } else if (k.equals("stick.inverty")) { invertY = v.equalsIgnoreCase("true");
            } else if (k.equals("rstick.invertx")) { rInvertX = v.equalsIgnoreCase("true");
            } else if (k.equals("rstick.inverty")) { rInvertY = v.equalsIgnoreCase("true");
            } else if (k.equals("dpad")) {
                dpadArrows = v.equals("arrows") || v.equals("both");
                dpadDigits = v.equals("digits") || v.equals("both");
            }
        }
    }

    public static void reloadMappings() { GamepadReader r = active; if (r != null) synchronized (r) { r.loadConfig(); } }

    static int parseKey(String v) {
        int mapped = KeyMapper.code(v.toUpperCase()); if (mapped != Integer.MIN_VALUE) return mapped;
        switch (v.toUpperCase()) {
            case "UP": return Canvas.KEY_UP; case "DOWN": return Canvas.KEY_DOWN;
            case "LEFT": return Canvas.KEY_LEFT; case "RIGHT": return Canvas.KEY_RIGHT;
            case "FIRE": return Canvas.KEY_FIRE; case "SOFT_LEFT": return Canvas.KEY_SOFT_LEFT;
            case "SOFT_RIGHT": return Canvas.KEY_SOFT_RIGHT; case "CLEAR": return Canvas.KEY_CLEAR;
            case "STAR": return Canvas.KEY_STAR; case "POUND": return Canvas.KEY_POUND;
            case "NONE": return 0;
            default:
                if (v.length() == 1 && v.charAt(0) >= '0' && v.charAt(0) <= '9') return Canvas.KEY_NUM0 + (v.charAt(0) - '0');
                return Integer.MIN_VALUE;
        }
    }

    private void scanLoop() {
        while (true) {
            List<File> candidates = new ArrayList<>();
            for (int i = 0; i < 16; i++) candidates.add(new File("/dev/input/js" + i));
            // J2ME_JOYSTICK=/dev/input/by-id/usb-...-joystick[:other] adds devices with unusual names
            String extra = System.getenv("J2ME_JOYSTICK");
            if (extra != null) for (String x : extra.split(":")) if (!x.isEmpty()) candidates.add(new File(x));
            for (File f : candidates) {
                if (f.exists() && f.canRead() && open.add(f.getPath())) {
                    Thread t = new Thread(() -> readDevice(f), "gamepad-" + f.getName());
                    t.setDaemon(true);
                    t.start();
                }
            }
            try { Thread.sleep(2000); } catch (InterruptedException e) { return; }
        }
    }

    // direction state per source so stick and d-pad can be combined
    private static final class DevState {
        boolean[] stick = new boolean[4], hat = new boolean[4], sent = new boolean[4];   // L R U D
        boolean[] rstick = new boolean[4];                                              // right stick, same order
        boolean[] btn = new boolean[BUTTON_COUNT];
        int[] btnCode = new int[BUTTON_COUNT];
        int[] rest = new int[8];                                                        // resting value of axes 0..7
        boolean[] restSet = new boolean[8];
    }
    private static final int L = 0, R = 1, U = 2, D = 3;

    /** Test hook: feed raw 8-byte joystick events from any stream (as if read from /dev/input/jsN). */
    public static void feedForTest(java.io.InputStream events) {
        GamepadReader r = active != null ? active : new GamepadReader();
        if (active == null) r.loadConfig();
        r.pump(new DevState(), events);
    }
    private void pump(DevState st, java.io.InputStream raw) {
        try (DataInputStream in = new DataInputStream(raw)) {
            byte[] b = new byte[8];
            while (true) {
                in.readFully(b);
                int value = (short) ((b[4] & 0xFF) | (b[5] << 8));
                int type = b[6] & 0xFF & ~T_INIT;
                int num = b[7] & 0xFF;
                if (type == T_BUTTON) button(st, num, value != 0);
                else if (type == T_AXIS) axis(st, num, value);
            }
        } catch (IOException e) { /* end of stream */ }
    }

    private void readDevice(File f) {
        DevState st = new DevState();
        System.err.println("[j2me] gamepad connected: " + f);
        try (DataInputStream in = new DataInputStream(new BufferedInputStream(new FileInputStream(f), 64))) {
            byte[] b = new byte[8];
            while (true) {
                in.readFully(b);
                int value = (short) ((b[4] & 0xFF) | (b[5] << 8));
                int type = b[6] & 0xFF & ~T_INIT;
                int num = b[7] & 0xFF;
                if (type == T_BUTTON) button(st, num, value != 0);
                else if (type == T_AXIS) axis(st, num, value);
            }
        } catch (IOException e) {
            // device unplugged
        } finally {
            for (int i = 0; i < 4; i++) if (st.sent[i]) sendDir(i, false);
            for (int i = 0; i < st.btn.length; i++) if (st.btn[i] && st.btnCode[i] != 0) input.release(st.btnCode[i]);
            open.remove(f.getPath());
            System.err.println("[j2me] gamepad disconnected: " + f);
        }
    }

    private void button(DevState st, int n, boolean down) {
        if (n < 0 || n >= buttons.length) return;
        if (st.btn[n] == down) return;
        st.btn[n] = down;
        java.util.function.IntConsumer ui = uiButtonSink;
        if (ui != null) {
            // UI controls are physical Steam Deck buttons, not configurable Nokia game keys.
            if (down) ui.accept(n);
            st.btnCode[n] = 0;
            return;
        }
        int code = down ? buttons[n] : st.btnCode[n];
        if (down) st.btnCode[n] = code;
        if (code == 0) return;
        if (down) input.press(code); else input.release(code);
    }

    private void axis(DevState st, int n, int v) {
        switch (n) {
            case 0: stickAxis(st, L, R, invertX ? -v : v); break;      // left stick X
            case 1: stickAxis(st, U, D, invertY ? -v : v); break;      // left stick Y
            case 2: trigger(st, 16, 2, v); break;       // left trigger
            case 5: trigger(st, 17, 5, v); break;       // right trigger
            case 3: rightStick(st, L, R, rInvertX ? -v : v); break;    // right stick X
            case 4: rightStick(st, U, D, rInvertY ? -v : v); break;    // right stick Y
            case 6: hatAxis(st, L, R, v); break;        // d-pad X
            case 7: hatAxis(st, U, D, v); break;        // d-pad Y
            default:
        }
    }

    private void stickAxis(DevState st, int neg, int pos, int v) {
        st.stick[neg] = st.stick[neg] ? v < -stickOff : v < -stickOn;
        st.stick[pos] = st.stick[pos] ? v > stickOff : v > stickOn;
        update(st, neg); update(st, pos);
    }
    private void rightStick(DevState st, int neg, int pos, int v) {
        boolean n = st.rstick[neg] ? v < -stickOff : v < -stickOn, p = st.rstick[pos] ? v > stickOff : v > stickOn;
        if (n != st.rstick[neg]) { st.rstick[neg] = n; button(st, 18 + neg, n); }
        if (p != st.rstick[pos]) { st.rstick[pos] = p; button(st, 18 + pos, p); }
    }
    /** Triggers rest at -32767 (xpad) or 0 (some pads): the first value seen is the resting position. */
    private void trigger(DevState st, int virtualButton, int axisNum, int v) {
        if (!st.restSet[axisNum]) { st.rest[axisNum] = v; st.restSet[axisNum] = true; }
        int pull = v - st.rest[axisNum];
        boolean down = st.btn[virtualButton] ? pull > trigOff : pull > trigOn;
        button(st, virtualButton, down);
    }
    private void hatAxis(DevState st, int neg, int pos, int v) {
        st.hat[neg] = v < -stickOff;
        st.hat[pos] = v > stickOff;
        update(st, neg); update(st, pos);
    }
    private void update(DevState st, int dir) {
        boolean want = st.stick[dir] || st.hat[dir];
        if (want != st.sent[dir]) { st.sent[dir] = want; sendDir(dir, want); }
    }
    private void sendDir(int dir, boolean down) {
        int arrow = dir == L ? Canvas.KEY_LEFT : dir == R ? Canvas.KEY_RIGHT : dir == U ? Canvas.KEY_UP : Canvas.KEY_DOWN;
        int digit = dir == L ? Canvas.KEY_NUM4 : dir == R ? Canvas.KEY_NUM6 : dir == U ? Canvas.KEY_NUM2 : Canvas.KEY_NUM8;
        if (dpadArrows) { if (down) input.press(arrow); else input.release(arrow); }
        if (dpadDigits) { if (down) input.press(digit); else input.release(digit); }
    }
}
