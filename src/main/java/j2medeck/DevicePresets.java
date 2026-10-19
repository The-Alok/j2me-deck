// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 J2ME Deck contributors
package j2medeck;

import java.util.ArrayList;
import java.util.List;

/** Handset presets: screen size, key layout and the identity strings games sniff (platform, locale, encoding). */
public final class DevicePresets {
    public static final class Preset {
        public final String name, platform;
        public final int width, height;
        public final KeyLayout layout;
        public final boolean forceFullscreen;

        Preset(String name, int w, int h, KeyLayout layout, String platform, boolean forceFullscreen) {
            this.name = name; this.width = w; this.height = h; this.layout = layout; this.platform = platform; this.forceFullscreen = forceFullscreen;
        }
        @Override public String toString() { return name; }
    }

    public static final List<Preset> ALL = new ArrayList<>();
    private static void add(String n, int w, int h, KeyLayout l, String platform, boolean ff) { ALL.add(new Preset(n, w, h, l, platform, ff)); }

    static {
        add("Sony Ericsson K800i (240x320)", 240, 320, KeyLayout.NOKIA, "SonyEricssonK800i/R1CA", false);
        add("Sony Ericsson K500i (128x160)", 128, 160, KeyLayout.NOKIA, "SonyEricssonK500i/R2A", false);
        add("Sony Ericsson W810i (176x220)", 176, 220, KeyLayout.NOKIA, "SonyEricssonW810i/R4EA", false);
        add("Nokia 6230 (128x160)", 128, 160, KeyLayout.NOKIA, "Nokia6230/2.0", false);
        add("Nokia 6300 (240x320)", 240, 320, KeyLayout.NOKIA, "Nokia6300/2.0", false);
        add("Nokia N73 (240x320)", 240, 320, KeyLayout.NOKIA, "Nokia N73/3.0", false);
        add("Nokia E72 (320x240)", 320, 240, KeyLayout.NOKIA, "Nokia E72/1.0", false);
        add("Nokia 5800 XpressMusic (360x640)", 360, 640, KeyLayout.NOKIA, "Nokia5800d-1/52.0", true);
        add("Nokia N97 (640x360)", 640, 360, KeyLayout.NOKIA, "Nokia N97/1.0", true);
        add("Motorola RAZR V3 (176x220)", 176, 220, KeyLayout.MOTOROLA, "V3", false);
        add("Motorola V300 (176x220)", 176, 220, KeyLayout.MOTOROLA_V300, "V300", false);
        add("Motorola V8 (240x320)", 240, 320, KeyLayout.MOTOROLA_V8, "MotoV8", false);
        add("Motorola Triplets E398 (176x220)", 176, 220, KeyLayout.MOTOROLA_TRIPLETS, "E398", false);
        add("Motorola i670 (130x130)", 130, 130, KeyLayout.MOTOROLA_I670, "i670", false);
        add("Siemens CX65 (132x176)", 132, 176, KeyLayout.SIEMENS, "SIEMENS-CX65", false);
        add("Siemens S65 (132x176)", 132, 176, KeyLayout.SIEMENS, "SIEMENS-S65", false);
        add("LG KG800 (176x220)", 176, 220, KeyLayout.LG, "LG-KG800", false);
        add("Sagem my401X (128x160)", 128, 160, KeyLayout.SAGEM, "Sagem my401X", false);
        add("Sharp GX30 (240x320)", 240, 320, KeyLayout.SHARP, "GX30", false);
        add("Samsung SGH-D500 (176x220)", 176, 220, KeyLayout.NOKIA, "SGH-D500", false);
        add("Generic 128x128", 128, 128, KeyLayout.NOKIA, null, false);
        add("Generic 176x208", 176, 208, KeyLayout.NOKIA, null, false);
        add("Generic 240x400", 240, 400, KeyLayout.NOKIA, null, false);
        add("Generic 320x480", 320, 480, KeyLayout.NOKIA, null, false);
        add("Generic 480x800", 480, 800, KeyLayout.NOKIA, null, true);
    }

    public static Preset byName(String n) {
        if (n == null) return null;
        for (Preset p : ALL) if (p.name.equalsIgnoreCase(n.trim())) return p;
        return null;
    }
}
