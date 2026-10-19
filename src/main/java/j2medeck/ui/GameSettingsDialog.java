// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 J2ME Deck contributors
package j2medeck.ui;

import j2medeck.*;

import javax.swing.*;
import java.awt.*;
import java.io.IOException;
import java.util.Map;

/** Per-game settings in three tabs: Display, Device and compatibility, Performance and patches. */
public final class GameSettingsDialog {
    private static final String[] PRESETS = {
            "Game default (240 x 320)", "128 x 128", "128 x 160", "176 x 208", "176 x 220", "208 x 208", "240 x 240",
            "240 x 320", "240 x 400", "320 x 240", "320 x 480", "360 x 640", "480 x 640", "480 x 800", "640 x 360", "Custom..."};
    private static final int CUSTOM = PRESETS.length - 1;
    private static final String[] SPEEDS = {"0.25", "0.5", "1", "2", "4", "8"};

    private GameSettingsDialog() {}

    public static final class Form {
        /** Tabbed content (a JComponent so callers and tests can lay it out and paint it). */
        public final JTabbedPane panel = new JTabbedPane();
        final Profile profile;
        // display
        final JComboBox<String> preset = new JComboBox<>(PRESETS);
        final JSpinner width = new JSpinner(new SpinnerNumberModel(240, 32, 2048, 1));
        final JSpinner height = new JSpinner(new SpinnerNumberModel(320, 32, 2048, 1));
        final JCheckBox autosize = new JCheckBox("Match the display's aspect ratio at height");
        final JSpinner autoHeight = new JSpinner(new SpinnerNumberModel(320, 64, 1080, 8));
        final JComboBox<String> scale = new JComboBox<>(new String[]{"Default", "sharp-bilinear", "nearest", "integer", "stretch"});
        final JComboBox<String> renderer = new JComboBox<>(new String[]{"Default", "auto", "gles", "swing"});
        final JComboBox<String> lcdgrid = new JComboBox<>(new String[]{"Default", "Off", "Low", "Medium", "High"});
        final JComboBox<String> fullscreen = new JComboBox<>(new String[]{"Default", "Always", "Never"});
        final JSpinner winScale = new JSpinner(new SpinnerNumberModel(0, 0, 8, 1));
        final JComboBox<String> softkeys = new JComboBox<>(new String[]{"auto", "always", "never"});
        final JSpinner fSmall = new JSpinner(new SpinnerNumberModel(10, 6, 48, 1));
        final JSpinner fMedium = new JSpinner(new SpinnerNumberModel(12, 6, 48, 1));
        final JSpinner fLarge = new JSpinner(new SpinnerNumberModel(16, 6, 64, 1));
        final JTextField fontFamily = new JTextField("SansSerif");
        // device & compatibility
        final JComboBox<String> devicePreset = new JComboBox<>();
        final JComboBox<String> layout = new JComboBox<>();
        final JTextField platform = new JTextField(), locale = new JTextField(), encoding = new JTextField();
        final JCheckBox keyRepeat = new JCheckBox("Send keyRepeated events"), keyPressOnRepeat = new JCheckBox("Send keyPressed on key repeat"),
                ignoreFull = new JCheckBox("Ignore setFullScreenMode (always full screen)"), ignoreRegion = new JCheckBox("Always repaint the whole screen"),
                ignoreService = new JCheckBox("Ignore serviceRepaints()"), noRepaintOnShow = new JCheckBox("No automatic repaint when a screen is shown"),
                syncFlush = new JCheckBox("Wait for each frame to reach the screen (sync flush)"), softKeyFix = new JCheckBox("Soft keys also reach keyPressed when they fire a Command"),
                swapSoft = new JCheckBox("Swap left and right soft keys");
        final JComboBox<String> hasPointer = new JComboBox<>(new String[]{"Default", "Yes", "No"});
        final JCheckBox network = new JCheckBox("Allow network access (HTTP / sockets)"), mute = new JCheckBox("Mute this game"),
                autopause = new JCheckBox("Pause when the window loses focus");
        // performance & patches
        final JComboBox<String> speed = new JComboBox<>(SPEEDS);
        final JSpinner fps = new JSpinner(new SpinnerNumberModel(0, 0, 240, 5));
        final JCheckBox ignoreGc = new JCheckBox("Ignore System.gc()"), ignoreSleep = new JCheckBox("Ignore Thread.sleep() (no frame delays)"),
                yieldSleep = new JCheckBox("Thread.yield() sleeps 1 ms (saves CPU in busy loops)");
        final JSpinner heap = new JSpinner(new SpinnerNumberModel(0, 0, 512, 1));
        final JCheckBox mascotNoMix = new JCheckBox("Mascot 3D: don't mix with 2D drawing"), mascotNoBg = new JCheckBox("Mascot 3D: ignore the background");
        final JTextArea sysprops = new JTextArea(4, 30), appprops = new JTextArea(3, 30);

        Form(Profile p) {
            profile = p;
            devicePreset.addItem("None");
            for (DevicePresets.Preset d : DevicePresets.ALL) devicePreset.addItem(d.name);
            layout.addItem("Auto (from device preset)");
            for (KeyLayout l : KeyLayout.ALL.values()) layout.addItem(l.name);

            JPanel display = grid(), device = grid(), perf = grid();
            int r = 0;
            r = add(display, r, "Screen size", box(preset, new JLabel("W"), width, new JLabel("H"), height));
            r = add(display, r, "", box(autosize, autoHeight));
            r = add(display, r, "Scaling", scale);
            r = add(display, r, "LCD grid shader", lcdgrid);
            r = add(display, r, "Renderer", box(renderer, new JLabel("auto = OpenGL ES (low power) on the desktop, Swing in Steam Game Mode")));
            r = add(display, r, "Window", box(fullscreen, new JLabel("window scale"), winScale, new JLabel("(0 = auto)")));
            r = add(display, r, "Soft-key bar", softkeys);
            r = add(display, r, "Font sizes (px)", box(new JLabel("small"), fSmall, new JLabel("medium"), fMedium, new JLabel("large"), fLarge));
            r = add(display, r, "Font family", fontFamily);

            r = 0;
            r = add(device, r, "Device preset", devicePreset);
            r = add(device, r, "", note("Sets the screen size, key layout and platform name below when chosen."));
            r = add(device, r, "Key layout", layout);
            r = add(device, r, "Platform", platform);
            r = add(device, r, "Locale", locale);
            r = add(device, r, "Encoding", encoding);
            r = add(device, r, "Input quirks", column(keyRepeat, keyPressOnRepeat, swapSoft, softKeyFix));
            r = add(device, r, "Pointer events", hasPointer);
            r = add(device, r, "Drawing quirks", column(ignoreFull, ignoreRegion, ignoreService, noRepaintOnShow, syncFlush));
            r = add(device, r, "Options", column(network, mute, autopause));
            r = add(device, r, "System properties", scroll(sysprops));
            r = add(device, r, "", note("name=value, e.g.  microedition.platform=Nokia6230"));
            if (!p.isGlobal()) r = add(device, r, "JAD overrides", scroll(appprops));

            r = 0;
            r = add(perf, r, "Game speed", box(speed, new JLabel("x   (F3 / F4 change it while playing, F5 pauses)")));
            r = add(perf, r, "Frame rate limit", box(fps, new JLabel("fps  (0 = unlimited)")));
            r = add(perf, r, "Timing patches", column(ignoreGc, ignoreSleep, yieldSleep));
            r = add(perf, r, "Reported heap", box(heap, new JLabel("MB  (0 = real; some games refuse to run if they see too much or too little)")));
            r = add(perf, r, "3D", column(mascotNoMix, mascotNoBg));

            panel.addTab("Display", wrap(display));
            panel.addTab("Device & compatibility", wrap(device));
            panel.addTab("Performance & patches", wrap(perf));

            preset.addActionListener(e -> syncSpinners());
            devicePreset.addActionListener(e -> applyDevice());
            sysprops.setRows(4); appprops.setRows(3);
            applyFonts(panel);
            load();
        }

        private static JPanel grid() { JPanel p = new JPanel(new GridBagLayout()); p.setBackground(Theme.BG); return p; }
        private static JComponent wrap(JPanel p) {
            JPanel top = new JPanel(new BorderLayout()); top.setBackground(Theme.BG); top.add(p, BorderLayout.NORTH);
            JScrollPane sp = new JScrollPane(top); sp.setBorder(null); sp.getVerticalScrollBar().setUnitIncrement(16);
            return sp;
        }
        private static JPanel box(Component... cs) {
            JPanel b = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
            b.setOpaque(false);
            for (Component c : cs) b.add(c);
            return b;
        }
        private static JPanel column(Component... cs) {
            JPanel b = new JPanel();
            b.setLayout(new BoxLayout(b, BoxLayout.Y_AXIS));
            b.setOpaque(false);
            for (Component c : cs) { ((JComponent) c).setAlignmentX(Component.LEFT_ALIGNMENT); b.add(c); }
            return b;
        }
        private static JScrollPane scroll(JTextArea a) { JScrollPane s = new JScrollPane(a); s.setPreferredSize(new Dimension(Theme.base() * 22, Theme.base() * 5)); return s; }
        private static JLabel note(String t) { JLabel l = new JLabel(t); l.setForeground(Theme.MUTED); l.setFont(Theme.font(Font.ITALIC, 0.8f)); return l; }

        private static void applyFonts(Component c) {
            if (c instanceof JComboBox || c instanceof JCheckBox || c instanceof javax.swing.text.JTextComponent || c instanceof JSpinner.DefaultEditor)
                c.setFont(Theme.font(Font.PLAIN, 1.0f));
            if (c instanceof JSpinner) ((JSpinner) c).setPreferredSize(new Dimension(Theme.base() * 4 + 20, Theme.base() * 2 + 8));
            if (c instanceof JComboBox) c.setPreferredSize(new Dimension(Math.max(c.getPreferredSize().width, Theme.base() * 9), Theme.base() * 2 + 8));
            if (c instanceof Container) for (Component ch : ((Container) c).getComponents()) applyFonts(ch);
        }

        private int add(JPanel target, int row, String label, Component c) {
            GridBagConstraints g = new GridBagConstraints();
            g.gridy = row; g.insets = new Insets(5, 8, 5, 8); g.anchor = GridBagConstraints.NORTHWEST;
            g.gridx = 0;
            JLabel l = new JLabel(label);
            l.setForeground(Theme.MUTED);
            target.add(l, g);
            g.gridx = 1; g.weightx = 1; g.fill = GridBagConstraints.HORIZONTAL;
            target.add(c, g);
            return row + 1;
        }

        private void syncSpinners() {
            int i = preset.getSelectedIndex();
            boolean custom = i == CUSTOM;
            width.setEnabled(custom); height.setEnabled(custom);
            if (!custom) {
                if (i == 0) { width.setValue(240); height.setValue(320); }
                else { String[] wh = PRESETS[i].split(" x "); width.setValue(Integer.parseInt(wh[0])); height.setValue(Integer.parseInt(wh[1])); }
            }
        }

        /** Choosing a handset fills in its screen size, key layout and platform name. */
        private void applyDevice() {
            DevicePresets.Preset d = DevicePresets.byName((String) devicePreset.getSelectedItem());
            if (d == null) return;
            int sel = CUSTOM;
            for (int i = 1; i < CUSTOM; i++) if (PRESETS[i].equals(d.width + " x " + d.height)) sel = i;
            preset.setSelectedIndex(sel);
            if (sel == CUSTOM) { width.setValue(d.width); height.setValue(d.height); }
            syncSpinners();
            layout.setSelectedIndex(0);
            platform.setText(d.platform == null ? "" : d.platform);
        }

        void load() {
            int w = profile.screenWidth(), h = profile.screenHeight();
            int sel = 0;
            if (w > 0 && h > 0) { sel = CUSTOM; for (int i = 1; i < CUSTOM; i++) if (PRESETS[i].equals(w + " x " + h)) sel = i; }
            preset.setSelectedIndex(sel);
            if (sel == CUSTOM) { width.setValue(w); height.setValue(h); }
            syncSpinners();
            autosize.setSelected(Boolean.TRUE.equals(profile.getBool("autosize")));
            autoHeight.setValue(profile.getInt("autosizeheight", 320));
            scale.setSelectedItem(profile.get("scale", "Default"));
            renderer.setSelectedItem(profile.get("renderer", "Default"));
            int lg = Settings.parseLcdLevel(profile.p.getProperty("lcdgrid"));
            lcdgrid.setSelectedIndex(lg < 0 ? 0 : lg + 1);
            Boolean fs = profile.getBool("fullscreen");
            fullscreen.setSelectedIndex(fs == null ? 0 : fs ? 1 : 2);
            winScale.setValue(profile.getInt("windowscale", 0));
            softkeys.setSelectedItem(profile.get("softkeybar", "auto"));
            fSmall.setValue(profile.getInt("font.small", 10));
            fMedium.setValue(profile.getInt("font.medium", 12));
            fLarge.setValue(profile.getInt("font.large", 16));
            fontFamily.setText(profile.get("font.family", "SansSerif"));

            DevicePresets.Preset d = profile.preset();
            devicePreset.setSelectedItem(d == null ? "None" : d.name);
            KeyLayout kl = KeyLayout.byName(profile.get("layout", null));
            layout.setSelectedItem(kl == null ? "Auto (from device preset)" : kl.name);
            platform.setText(profile.get("platform", d != null && d.platform != null ? d.platform : ""));
            locale.setText(profile.get("locale", ""));
            encoding.setText(profile.get("encoding", ""));
            keyRepeat.setSelected(!Boolean.FALSE.equals(profile.getBool("tweak.keyrepeat")));
            keyPressOnRepeat.setSelected(Boolean.TRUE.equals(profile.getBool("tweak.keypressonrepeat")));
            swapSoft.setSelected(Boolean.TRUE.equals(profile.getBool("tweak.swapsoftkeys")));
            softKeyFix.setSelected(Boolean.TRUE.equals(profile.getBool("tweak.softkeyfix")));
            Boolean hp = profile.getBool("tweak.haspointer");
            hasPointer.setSelectedIndex(hp == null ? 0 : hp ? 1 : 2);
            ignoreFull.setSelected(Boolean.TRUE.equals(profile.getBool("tweak.ignorefullscreen")));
            ignoreRegion.setSelected(Boolean.TRUE.equals(profile.getBool("tweak.ignoreregionrepaint")));
            ignoreService.setSelected(Boolean.TRUE.equals(profile.getBool("tweak.ignoreservicerepaints")));
            noRepaintOnShow.setSelected(Boolean.TRUE.equals(profile.getBool("tweak.norepaintonsetcurrent")));
            syncFlush.setSelected(Boolean.TRUE.equals(profile.getBool("tweak.syncflush")));
            network.setSelected(Boolean.TRUE.equals(profile.getBool("network")));
            mute.setSelected(Boolean.TRUE.equals(profile.getBool("mute")));
            Boolean ap = profile.getBool("autopause");
            autopause.setSelected(ap == null || ap);
            sysprops.setText(join(profile.prefixed("sysprop.")));
            appprops.setText(join(profile.prefixed("appprop.")));

            String sp = profile.get("speed", "1");
            speed.setSelectedItem(sp.equals("1.0") ? "1" : sp);
            if (speed.getSelectedItem() == null) speed.setSelectedItem("1");
            fps.setValue(profile.getInt("fps", 0));
            ignoreGc.setSelected(Boolean.TRUE.equals(profile.getBool("patch.ignoregc")));
            ignoreSleep.setSelected(Boolean.TRUE.equals(profile.getBool("patch.ignoresleep")));
            yieldSleep.setSelected(Boolean.TRUE.equals(profile.getBool("patch.yieldsleep")));
            heap.setValue(profile.getInt("heapmb", 0));
            mascotNoMix.setSelected(Boolean.TRUE.equals(profile.getBool("mascot.no2dmixing")));
            mascotNoBg.setSelected(Boolean.TRUE.equals(profile.getBool("mascot.ignorebackground")));
        }

        private static String join(Map<String, String> m) {
            StringBuilder sb = new StringBuilder();
            for (Map.Entry<String, String> e : m.entrySet()) sb.append(e.getKey()).append('=').append(e.getValue()).append('\n');
            return sb.toString();
        }

        private static String onlyIf(boolean b) { return b ? "true" : null; }

        /** True when two stored values mean the same ("true"/"TRUE", "1" / "1.0"). */
        static boolean same(String a, String b) {
            if (a == null || b == null) return a == b;
            a = a.trim(); b = b.trim();
            if (a.equalsIgnoreCase(b)) return true;
            try { return Double.parseDouble(a) == Double.parseDouble(b); } catch (NumberFormatException e) { return false; }
        }

        /**
         * Store {@code value} only if it differs from what the profile would have without its own entry: the global
         * default (per-game profiles) or the built-in default {@code def}. That way untouched values keep following the
         * global settings, while a game can still override a global value back to the built-in one.
         */
        private static void put(Profile p, String key, Object value, Object def) {
            String v = String.valueOf(value), base = p.baseValue(key, String.valueOf(def));
            p.set(key, same(v, base) ? null : v);
        }
        private static void flag(Profile p, String key, boolean value, boolean def) { put(p, key, value, def); }

        /** Write the form back into the profile and save it. Only values that differ from the defaults are stored. */
        public void save() throws IOException {
            Profile p = profile;
            for (String k : new java.util.ArrayList<>(p.p.stringPropertyNames()))
                if (k.startsWith("sysprop.") || k.startsWith("appprop.")) p.p.remove(k);
            int i = preset.getSelectedIndex();
            if (i == 0) {
                // "Game default": nothing stored, or an explicit 0 (= loader default) when the global settings define a size
                p.set("width", p.inheritsGlobal("width") ? "0" : null);
                p.set("height", p.inheritsGlobal("height") ? "0" : null);
            } else { p.set("width", width.getValue()); p.set("height", height.getValue()); }
            flag(p, "autosize", autosize.isSelected(), false);
            p.set("autosizeheight", autosize.isSelected() ? autoHeight.getValue() : null);
            String sc = (String) scale.getSelectedItem();
            p.set("scale", "Default".equals(sc) ? null : sc);
            String rd = (String) renderer.getSelectedItem();
            p.set("renderer", "Default".equals(rd) ? null : rd);
            int lgi = lcdgrid.getSelectedIndex();
            p.set("lcdgrid", lgi == 0 ? null : Settings.LCD_NAMES[lgi - 1]);
            int f = fullscreen.getSelectedIndex();
            p.set("fullscreen", f == 0 ? null : f == 1 ? "true" : "false");
            put(p, "windowscale", winScale.getValue(), 0);
            put(p, "softkeybar", softkeys.getSelectedItem(), "auto");
            put(p, "font.small", fSmall.getValue(), 10);
            put(p, "font.medium", fMedium.getValue(), 12);
            put(p, "font.large", fLarge.getValue(), 16);
            String fam = fontFamily.getText().trim();
            put(p, "font.family", fam.isEmpty() ? "SansSerif" : fam, "SansSerif");

            String dev = (String) devicePreset.getSelectedItem();
            p.set("preset", "None".equals(dev) ? null : dev);
            int li = layout.getSelectedIndex();
            p.set("layout", li <= 0 ? null : layout.getSelectedItem());
            DevicePresets.Preset dp = DevicePresets.byName(dev);
            String plat = platform.getText().trim();
            p.set("platform", plat.isEmpty() || (dp != null && plat.equals(dp.platform)) ? null : plat);
            if (!plat.isEmpty() && (dp == null || !plat.equals(dp.platform))) p.p.setProperty("sysprop.microedition.platform", plat);
            p.set("locale", locale.getText().trim());
            p.set("encoding", encoding.getText().trim());
            flag(p, "tweak.keyrepeat", keyRepeat.isSelected(), true);
            flag(p, "tweak.keypressonrepeat", keyPressOnRepeat.isSelected(), false);
            flag(p, "tweak.swapsoftkeys", swapSoft.isSelected(), false);
            flag(p, "tweak.softkeyfix", softKeyFix.isSelected(), false);
            int hp = hasPointer.getSelectedIndex();
            p.set("tweak.haspointer", hp == 0 ? null : hp == 1 ? "true" : "false");
            flag(p, "tweak.ignorefullscreen", ignoreFull.isSelected(), false);
            flag(p, "tweak.ignoreregionrepaint", ignoreRegion.isSelected(), false);
            flag(p, "tweak.ignoreservicerepaints", ignoreService.isSelected(), false);
            flag(p, "tweak.norepaintonsetcurrent", noRepaintOnShow.isSelected(), false);
            flag(p, "tweak.syncflush", syncFlush.isSelected(), false);
            flag(p, "network", network.isSelected(), false);
            flag(p, "mute", mute.isSelected(), false);
            flag(p, "autopause", autopause.isSelected(), true);
            put(p, "sysprop.", sysprops.getText());
            if (!p.isGlobal()) put(p, "appprop.", appprops.getText());

            put(p, "speed", speed.getSelectedItem(), "1");
            put(p, "fps", fps.getValue(), 0);
            flag(p, "patch.ignoregc", ignoreGc.isSelected(), false);
            flag(p, "patch.ignoresleep", ignoreSleep.isSelected(), false);
            flag(p, "patch.yieldsleep", yieldSleep.isSelected(), false);
            put(p, "heapmb", heap.getValue(), 0);
            flag(p, "mascot.no2dmixing", mascotNoMix.isSelected(), false);
            flag(p, "mascot.ignorebackground", mascotNoBg.isSelected(), false);
            p.save();
        }

        private static void put(Profile p, String prefix, String text) {
            for (String line : text.split("\\R")) {
                int eq = line.indexOf('=');
                if (eq <= 0 || line.trim().startsWith("#")) continue;
                String k = prefix + line.substring(0, eq).trim(), v = line.substring(eq + 1).trim();
                if (p.inheritsGlobal(k) && v.equals(p.global.getProperty(k))) continue;   // unchanged global entry: keep inheriting it
                p.p.setProperty(k, v);
            }
        }
    }

    public static Form form(GameInfo g) { return new Form(Profile.load(g.id())); }
    /** The settings form for an already loaded profile (a game's, or the global defaults). */
    public static Form form(Profile p) { return new Form(p); }
    /** The settings form for the global defaults (no game). */
    public static Form globalForm() { return new Form(Profile.loadGlobal()); }

    public static void show(Component parent, GameInfo g, Runnable afterSave) { show(parent, g, afterSave, false); }

    /** Global settings: the defaults every game starts from. A game's own settings override them. */
    public static void showGlobal(Component parent, Runnable afterSave) { show(parent, null, afterSave, true); }

    private static void show(Component parent, GameInfo g, Runnable afterSave, boolean global) {
        Form f = global ? globalForm() : form(g);
        Window owner = SwingUtilities.getWindowAncestor(parent);
        JDialog d = new JDialog(owner, global ? "Global settings - defaults for all games" : "Settings - " + g.name(), Dialog.ModalityType.APPLICATION_MODAL);
        JPanel root = new JPanel(new BorderLayout(8, 8));
        root.setBackground(Theme.BG);
        root.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        JLabel hint = new JLabel(global
                ? "<html>These are the defaults for <b>every</b> game. A game's own settings override them. Applies the next time a game starts.</html>"
                : "<html>Applies the next time you start the game. Values you leave alone follow the <b>global settings</b>. "
                + "Screen size changes what the game <b>sees</b>; most games are built for one size, so try the size in the file name "
                + "or pick the handset it was made for.</html>");
        hint.setForeground(Theme.MUTED);
        root.add(hint, BorderLayout.NORTH);
        root.add(f.panel, BorderLayout.CENTER);
        JButton keys = new JButton(global ? "Global key mapping..." : "Key mapping...");
        JButton compat = new JButton("Compatibility...");
        JButton reset = new JButton(global ? "Reset" : "Use global settings");
        JButton cancel = new JButton("Cancel");
        JButton save = new JButton("Save");
        JPanel bottom = new JPanel(new BorderLayout());
        bottom.setOpaque(false);
        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT)); left.setOpaque(false); left.add(keys); if (!global) left.add(compat); left.add(reset);
        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT)); right.setOpaque(false); right.add(cancel); right.add(save);
        bottom.add(left, BorderLayout.WEST); bottom.add(right, BorderLayout.EAST);
        root.add(bottom, BorderLayout.SOUTH);
        keys.addActionListener(e -> { if (global) LibraryWindow.openGlobalKeyMapper(d); else LibraryWindow.openKeyMapper(d, g); });
        compat.addActionListener(e -> { if (!global) LibraryWindow.showCompatibility(d, g); });
        reset.addActionListener(e -> { try { f.profile.reset(); } catch (IOException ex) { /* ignore */ } f.load(); });
        cancel.addActionListener(e -> d.dispose());
        save.addActionListener(e -> {
            try { f.save(); d.dispose(); if (afterSave != null) afterSave.run(); }
            catch (IOException ex) { JOptionPane.showMessageDialog(d, "Could not save: " + ex.getMessage(), "Save failed", JOptionPane.ERROR_MESSAGE); }
        });
        d.setContentPane(root);
        Dimension scr = Toolkit.getDefaultToolkit().getScreenSize();
        d.setSize(Math.min(820, scr.width - 40), Math.min(760, scr.height - 60));
        d.setLocationRelativeTo(owner);
        d.setVisible(true);
    }
}
