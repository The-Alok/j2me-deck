// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 J2ME Deck contributors
package j2medeck;

import javax.microedition.lcdui.Canvas;
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;

/**
 * Configuration UI for mapping physical keyboard keys and gamepad buttons to MIDP/Nokia keys.
 *
 * There are two scopes. The <b>global</b> key map (~/.config/j2me-deck/global/) is the default for every game; a
 * <b>per-game</b> map (~/.config/j2me-deck/profiles/&lt;game&gt;/) only stores the bindings that differ from it. At run
 * time the two are merged (per-game wins), see {@link #loadMerged(String)}.
 */
public final class KeyMapper {
    private static final String[] NAMES = {"UP", "DOWN", "LEFT", "RIGHT", "FIRE", "SOFT_LEFT", "SOFT_RIGHT", "CLEAR", "SEND", "END", "STAR", "POUND", "0", "1", "2", "3", "4", "5", "6", "7", "8", "9", "NONE"};
    private static final int[] CODES = {Canvas.KEY_UP, Canvas.KEY_DOWN, Canvas.KEY_LEFT, Canvas.KEY_RIGHT, Canvas.KEY_FIRE, Canvas.KEY_SOFT_LEFT, Canvas.KEY_SOFT_RIGHT, Canvas.KEY_CLEAR, Canvas.KEY_SEND, Canvas.KEY_END, Canvas.KEY_STAR, Canvas.KEY_POUND, 48, 49, 50, 51, 52, 53, 54, 55, 56, 57, 0};
    private static final Path DIR = Paths.get(System.getProperty("user.home"), ".config", "j2me-deck");
    private KeyMapper() {}

    /** Key mapper for the running / selected game (its own bindings on top of the global ones). */
    public static void show(Component parent) { show(parent, false); }
    /** Key mapper for the global defaults used by every game. */
    public static void showGlobal(Component parent) { show(parent, true); }

    public static void show(Component parent, boolean global) {
        final String kbFile = "keyboard.properties", padFile = "gamepad.properties";
        // what this scope stores, and what it falls back to when it stores nothing
        final Properties keyboard = load(global ? globalFile(kbFile) : profileFile(kbFile));
        final Properties pad = load(global ? globalFile(padFile) : profileFile(padFile));
        final Properties baseKeyboard = global ? new Properties() : load(globalFile(kbFile));
        final Properties basePad = global ? new Properties() : load(globalFile(padFile));

        Window owner = SwingUtilities.getWindowAncestor(parent);
        JDialog dialog = new JDialog(owner, global ? "J2ME Key Mapper - global (all games)" : "J2ME Key Mapper", Dialog.ModalityType.APPLICATION_MODAL);
        JPanel root = new JPanel(new BorderLayout(8, 8));
        root.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        JLabel status = new JLabel(global
                ? "Global key map: the default for every game. Select a binding, then press the physical key."
                : "Select a binding, then press the physical key. Unchanged bindings follow the global key map.");
        JTabbedPane tabs = new JTabbedPane();

        // ---- keyboard tab
        JPanel keys = new JPanel(new GridLayout(0, 2, 8, 6));
        final Map<String, JButton> capture = new LinkedHashMap<>();
        for (String name : NAMES) {
            if (name.equals("NONE")) continue;
            keys.add(new JLabel(label(name)));
            JButton b = new JButton(effectiveKey(keyboard, baseKeyboard, name));
            capture.put(name, b);
            keys.add(b);
            b.addActionListener(e -> {
                status.setText("Press a key for " + label(name) + " (Esc cancels)");
                final String before = b.getText();
                b.setText("Press a key...");
                b.requestFocusInWindow();
                b.addKeyListener(new KeyAdapter() {
                    boolean done;
                    @Override public void keyPressed(KeyEvent ev) {
                        if (done) return;
                        done = true;
                        if (ev.getKeyCode() == KeyEvent.VK_ESCAPE) {
                            b.setText(before);
                            status.setText("Cancelled.");
                        } else {
                            String val = KeyEvent.getKeyText(ev.getKeyCode());
                            if (val.equals("Unknown keyCode: 0x0")) val = "";
                            b.setText(val);
                            String clash = clashWith(capture, name, val);
                            status.setText(clash == null ? "Ready. Save to apply." : "Warning: " + val + " is also bound to " + label(clash) + ". Only one of them will work.");
                        }
                        b.removeKeyListener(this);
                        ev.consume();
                    }
                });
            });
        }
        JScrollPane keyScroll = new JScrollPane(keys);
        keyScroll.setPreferredSize(new Dimension(420, 400));
        tabs.addTab("Keyboard", keyScroll);

        // ---- controller tab
        JPanel pads = new JPanel(new GridLayout(0, 2, 8, 6));
        final Map<Integer, JComboBox<String>> combos = new LinkedHashMap<>();
        for (int i = 0; i < GamepadReader.BUTTON_COUNT; i++) {
            pads.add(new JLabel(GamepadReader.buttonLabel(i)));
            JComboBox<String> cb = new JComboBox<>(NAMES);
            cb.setSelectedItem(effectivePad(pad, basePad, i));
            combos.put(i, cb);
            pads.add(cb);
        }
        JScrollPane padScroll = new JScrollPane(pads);
        padScroll.setPreferredSize(new Dimension(420, 400));
        tabs.addTab("Controller", padScroll);

        root.add(tabs, BorderLayout.CENTER);
        root.add(status, BorderLayout.NORTH);
        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton reset = new JButton(global ? "Reset defaults" : "Use global map"), cancel = new JButton("Cancel"), save = new JButton("Save");
        bottom.add(reset); bottom.add(cancel); bottom.add(save);
        root.add(bottom, BorderLayout.SOUTH);

        reset.addActionListener(e -> {
            // back to what this scope falls back to: the built-in layout (global) or the global map (per game)
            for (Map.Entry<String, JButton> x : capture.entrySet()) x.getValue().setText(effectiveKey(new Properties(), baseKeyboard, x.getKey()));
            for (int i = 0; i < GamepadReader.BUTTON_COUNT; i++) combos.get(i).setSelectedItem(effectivePad(new Properties(), basePad, i));
            status.setText("Reset. Save to apply.");
        });
        cancel.addActionListener(e -> dialog.dispose());
        save.addActionListener(e -> {
            try {
                for (Map.Entry<String, JButton> x : capture.entrySet()) {
                    String v = x.getValue().getText();
                    if (v.equals("Press a key...")) continue;
                    storeDiff(keyboard, "key." + x.getKey(), v, baseKeyboard.getProperty("key." + x.getKey(), defaultKey(x.getKey())));
                }
                for (int i = 0; i < GamepadReader.BUTTON_COUNT; i++)
                    storeDiff(pad, "button." + i, (String) combos.get(i).getSelectedItem(), basePad.getProperty("button." + i, defaultPad(i)));
                store(keyboard, global ? globalFile(kbFile) : profileFile(kbFile));
                store(pad, global ? globalFile(padFile) : profileFile(padFile));
                InputManager.get().reloadMappings();
                GamepadReader.reloadMappings();
                dialog.dispose();
            } catch (IOException ex) {
                JOptionPane.showMessageDialog(dialog, "Could not save mappings: " + ex.getMessage(), "Save failed", JOptionPane.ERROR_MESSAGE);
            }
        });
        dialog.setContentPane(root);
        dialog.pack();
        dialog.setLocationRelativeTo(parent);
        dialog.setVisible(true);
    }

    // ---- pure helpers (also used by the tests)

    /** Binding shown for {@code name}: this scope's own entry, else the fallback scope's, else the built-in key. */
    static String effectiveKey(Properties own, Properties base, String name) {
        return own.getProperty("key." + name, base.getProperty("key." + name, defaultKey(name)));
    }
    static String effectivePad(Properties own, Properties base, int button) {
        return own.getProperty("button." + button, base.getProperty("button." + button, defaultPad(button)));
    }
    /** Store {@code value} only if it differs from what the scope would have anyway, so unchanged bindings keep following the fallback. */
    static void storeDiff(Properties target, String key, String value, String fallback) {
        if (value == null || value.equalsIgnoreCase(fallback)) target.remove(key); else target.setProperty(key, value);
    }
    /** Another action bound to the same physical key, or null. */
    static String clashWith(Map<String, JButton> bindings, String name, String keyText) {
        if (keyText == null || keyText.isEmpty()) return null;
        for (Map.Entry<String, JButton> x : bindings.entrySet())
            if (!x.getKey().equals(name) && x.getValue().getText().equalsIgnoreCase(keyText)) return x.getKey();
        return null;
    }

    /** The per-game file (falls back to the "default" folder when no game is selected). */
    static Path profileFile(String name) {
        String title = System.getProperty("j2me.profileid");
        if (title == null) title = System.getProperty("j2me.title", "default").replaceAll("[^A-Za-z0-9._-]", "_");
        return DIR.resolve("profiles").resolve(title).resolve(name);
    }
    /** The global file: defaults for every game. */
    static Path globalFile(String name) { return DIR.resolve("global").resolve(name); }

    static Properties load(Path p) { Properties x = new Properties(); try (InputStream in = Files.newInputStream(p)) { x.load(in); } catch (IOException ignored) {} return x; }
    /** Global entries overridden by the current game's own entries. */
    static Properties loadMerged(String name) {
        Properties m = load(globalFile(name));
        m.putAll(load(profileFile(name)));
        return m;
    }
    static void store(Properties p, Path path) throws IOException {
        Files.createDirectories(path.getParent());
        try (OutputStream out = Files.newOutputStream(path)) { p.store(out, "J2ME Deck key mappings"); }
    }
    static int code(String name) { for (int i = 0; i < NAMES.length; i++) if (NAMES[i].equals(name)) return CODES[i]; return Integer.MIN_VALUE; }
    static String name(int code) { for (int i = 0; i < CODES.length; i++) if (CODES[i] == code) return NAMES[i]; return "NONE"; }
    private static String label(String n) { return n.replace('_', ' '); }
    static String defaultKey(String n) {
        switch (n) {
            case "UP": return "Up"; case "DOWN": return "Down"; case "LEFT": return "Left"; case "RIGHT": return "Right";
            case "FIRE": return "Enter"; case "SOFT_LEFT": return "Q"; case "SOFT_RIGHT": return "E"; case "CLEAR": return "Escape";
            case "SEND": return "F3"; case "END": return "F4"; case "STAR": return "*"; case "POUND": return "#"; default: return n;
        }
    }
    static String defaultPad(int i) {
        switch (i) {
            case 0: return "FIRE"; case 1: return "SOFT_RIGHT"; case 2: return "SOFT_LEFT"; case 3: return "5"; case 4: return "STAR";
            case 5: return "POUND"; case 6: return "SOFT_RIGHT"; case 7: return "SOFT_LEFT"; default: return "NONE";
        }
    }
}
