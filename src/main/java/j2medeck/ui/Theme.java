// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 J2ME Deck contributors
package j2medeck.ui;

import javax.swing.UIManager;
import java.awt.*;

/** Colours and fonts shared by the library window, key mapper and LCDUI screen renderer. */
public final class Theme {
    public static final Color BG = new Color(0x0F1626), PANEL = new Color(0x17233B), PANEL_ALT = new Color(0x1C2B49),
            TEXT = new Color(0xE8EEFC), MUTED = new Color(0x93A3C4), ACCENT = new Color(0xFFC000),
            FOCUS = new Color(0x2B4C8C), BORDER = new Color(0x2C3E66), OK = new Color(0x4CD38A), WARN = new Color(0xFFB347), BAD = new Color(0xFF6B6B);

    private Theme() {}

    private static boolean installed;

    /** Dark Nimbus look and feel with fonts scaled for a handheld screen. Safe to call more than once. */
    public static synchronized void install() {
        if (installed) return;
        installed = true;
        try {
            for (UIManager.LookAndFeelInfo i : UIManager.getInstalledLookAndFeels())
                if ("Nimbus".equals(i.getName())) { UIManager.setLookAndFeel(i.getClassName()); break; }
        } catch (Exception ignored) {}
        UIManager.put("control", PANEL);
        UIManager.put("info", PANEL_ALT);
        UIManager.put("nimbusBase", new Color(0x24407A));
        UIManager.put("nimbusBlueGrey", new Color(0x3A4F7C));
        UIManager.put("nimbusLightBackground", BG);
        UIManager.put("nimbusSelectionBackground", FOCUS);
        UIManager.put("nimbusFocus", ACCENT);
        UIManager.put("nimbusOrange", ACCENT);
        UIManager.put("text", TEXT);
        UIManager.put("textForeground", TEXT);
        UIManager.put("nimbusDisabledText", MUTED);
        UIManager.put("controlText", TEXT);
        // Nimbus paints tab labels from its own state-specific defaults rather than always
        // honoring textForeground. Set the standard tabbed-pane keys too, so labels remain
        // legible when selection/focus changes (notably in the settings and key-mapper tabs).
        UIManager.put("TabbedPane.foreground", new javax.swing.plaf.ColorUIResource(TEXT));
        UIManager.put("TabbedPane.selectedForeground", new javax.swing.plaf.ColorUIResource(TEXT));
        UIManager.put("TabbedPane.disabledForeground", new javax.swing.plaf.ColorUIResource(MUTED));
        UIManager.put("TabbedPane.background", new javax.swing.plaf.ColorUIResource(PANEL));
        UIManager.put("TabbedPane.selected", new javax.swing.plaf.ColorUIResource(PANEL_ALT));
        UIManager.put("TabbedPane.contentAreaColor", new javax.swing.plaf.ColorUIResource(BG));
        UIManager.put("TabbedPane.focus", new javax.swing.plaf.ColorUIResource(ACCENT));
        Font f = font(Font.PLAIN, 1.0f);
        for (Object k : java.util.Collections.list(UIManager.getDefaults().keys()))
            if (k instanceof String && ((String) k).endsWith("font")) UIManager.put(k, new javax.swing.plaf.FontUIResource(f));
    }

    /** Base font size scales with the screen so the UI stays readable on a 1280x800 handheld. */
    public static int base() {
        int h = GraphicsEnvironment.isHeadless() ? 800 : Toolkit.getDefaultToolkit().getScreenSize().height;
        return Math.max(14, Math.min(24, h / 36));
    }
    public static Font font(int style, float factor) { return new Font(Font.SANS_SERIF, style, Math.round(base() * factor)); }
}
