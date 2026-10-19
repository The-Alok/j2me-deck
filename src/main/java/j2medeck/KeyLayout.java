// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 J2ME Deck contributors
package j2medeck;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Vendor key-code layouts. The loader works internally with the Nokia/Sony Ericsson codes (up -1, down -2, left -3,
 * right -4, fire -5, soft keys -6/-7); a layout translates those to what a given handset family really sent, so a
 * game built for a Motorola, Siemens, LG or Sharp phone sees the codes it expects.
 */
public final class KeyLayout {
    public final String name;
    public final int up, down, left, right, fire, s1, s2;

    private KeyLayout(String name, int up, int down, int left, int right, int fire, int s1, int s2) {
        this.name = name; this.up = up; this.down = down; this.left = left; this.right = right; this.fire = fire; this.s1 = s1; this.s2 = s2;
    }

    public static final Map<String, KeyLayout> ALL = new LinkedHashMap<>();
    private static KeyLayout add(KeyLayout l) { ALL.put(l.name, l); return l; }

    public static final KeyLayout NOKIA = add(new KeyLayout("Nokia", -1, -2, -3, -4, -5, -6, -7));
    public static final KeyLayout MOTOROLA = add(new KeyLayout("Motorola", -1, -6, -2, -5, -20, -21, -22));
    public static final KeyLayout MOTOROLA_TRIPLETS = add(new KeyLayout("Motorola Triplets", -1, -6, -2, -5, -8, -21, -22));
    public static final KeyLayout MOTOROLA_V8 = add(new KeyLayout("Motorola V8", -1, -2, -3, -4, -5, -21, -22));
    public static final KeyLayout MOTOROLA_I670 = add(new KeyLayout("Motorola i670", -10, -11, -13, -14, -23, -20, -21));
    public static final KeyLayout MOTOROLA_V300 = add(new KeyLayout("Motorola V300", 1, 6, 2, 5, 8, 21, 22));
    public static final KeyLayout SIEMENS = add(new KeyLayout("Siemens", -59, -60, -61, -62, -26, -1, -4));
    public static final KeyLayout LG = add(new KeyLayout("LG", -1, -2, -3, -4, -5, -202, -203));
    public static final KeyLayout SAGEM = add(new KeyLayout("Sagem", -1, -2, -3, -4, -5, -7, -6));
    public static final KeyLayout SHARP = add(new KeyLayout("Sharp", -1, -6, -2, -5, -20, -21, -22));

    public static volatile KeyLayout current = NOKIA;

    public static KeyLayout byName(String n) {
        if (n == null) return null;
        for (KeyLayout l : ALL.values()) if (l.name.equalsIgnoreCase(n.trim())) return l;
        return null;
    }

    /** Canonical (Nokia) code -> the code this handset family would send. Digits and unknown codes pass through. */
    public int toDevice(int canonical) {
        switch (canonical) {
            case -1: return up;
            case -2: return down;
            case -3: return left;
            case -4: return right;
            case -5: return fire;
            case -6: return s1;
            case -7: return s2;
            default: return canonical;
        }
    }

    /** MIDP game action (Canvas.UP...) for a device code of this layout, or 0 if it is not a direction/fire key. */
    public int actionFor(int deviceCode, int UP, int DOWN, int LEFT, int RIGHT, int FIRE) {
        if (deviceCode == up) return UP;
        if (deviceCode == down) return DOWN;
        if (deviceCode == left) return LEFT;
        if (deviceCode == right) return RIGHT;
        if (deviceCode == fire) return FIRE;
        return 0;
    }

    @Override public String toString() { return name; }
}
