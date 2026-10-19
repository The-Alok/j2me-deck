// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 J2ME Deck contributors
package j2medeck.ui;

import j2medeck.*;

import javax.microedition.lcdui.Canvas;
import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.text.JTextComponent;
import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.event.KeyEvent;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The game library, laid out like a console home screen: tabs along the top, a hero block and a shelf of covers, a full page per game,
 * and bottom sheets instead of dialogs. Works with a gamepad, the keyboard, the mouse and the touchscreen.
 */
public final class LibraryWindow implements ShelfView.Host, DetailView.Host {
    private static final String[] TABS = {"Library", "Recent", "Favorites", "Settings"};
    private static final int TAB_SETTINGS = 3;

    private final JLayeredPane root = new JLayeredPane();
    private final JPanel main = new JPanel(new BorderLayout());
    private final JPanel stage = new JPanel(new CardLayout());
    private final JPanel home = new JPanel(null) {
        @Override public void doLayout() {
            int w = getWidth(), h = getHeight();
            shelf.setBounds(0, 0, w, h);
            settings.setBounds(0, 0, w, h);
            topBar.setBounds(0, 0, w, TopBar.heightFor(Math.max(13, Math.min(Theme.base(), h / 28))));
        }
    };
    private final TopBar topBar = new TopBar(TABS);
    private final ShelfView shelf = new ShelfView(this);
    private final SettingsPage settings = new SettingsPage();
    private final DetailView detail = new DetailView(this);
    private final HintBar hints = new HintBar();
    private final Sheet sheet = new Sheet();

    private final List<GameInfo> all = new ArrayList<>();
    private final Map<Path, ApiScanner.Report> compat = new ConcurrentHashMap<>();
    private final ExecutorService scanner = Executors.newSingleThreadExecutor(r -> { Thread t = new Thread(r, "compat-scan"); t.setDaemon(true); return t; });
    private JFrame frame;
    private volatile Process running;
    private boolean tabsFocused, detailShown;

    // ------------------------------------------------------------------ entry
    public static void open(boolean fullscreen) {
        SwingUtilities.invokeLater(() -> {
            Theme.install();
            LibraryWindow w = new LibraryWindow();
            w.frame = new JFrame("J2ME Deck");
            w.frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
            w.frame.setContentPane(w.root);
            w.frame.setMinimumSize(new Dimension(760, 480));
            Dimension scr = Toolkit.getDefaultToolkit().getScreenSize();
            w.frame.setSize(Math.min(1280, scr.width - 40), Math.min(800, scr.height - 60));
            w.frame.setLocationRelativeTo(null);
            if (fullscreen) {
                w.frame.dispose(); w.frame.setUndecorated(true);
                GraphicsDevice dev = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice();
                if (dev.isFullScreenSupported()) dev.setFullScreenWindow(w.frame); else { w.frame.setBounds(dev.getDefaultConfiguration().getBounds()); w.frame.setVisible(true); }
            } else w.frame.setVisible(true);
            w.root.setFocusable(true);
            w.root.requestFocusInWindow();       // so the window has a focus owner and key events reach the keyboard dispatcher
            GamepadReader.start();
            w.attachGamepad();
            w.installKeyboard();
        });
    }

    /** Builds the window content without showing a frame (used by tests and screenshots). */
    public static LibraryWindow createForTest() {
        Theme.install();
        return new LibraryWindow();
    }
    public JComponent content() { return root; }

    // test hooks: drive the UI the way a user would and jump animations to their end
    public List<GameInfo> games() { return List.copyOf(all); }
    public void selectTab(int i) { topBar.select(i, true); }
    public void selectGame(int i) { shelf.select(i, false); shelf.settle(); }
    public void openDetail() { GameInfo g = shelf.selectedGame(); if (g != null) details(g); detail.settle(); }
    public void openOptionsSheet() { GameInfo g = detailShown ? detail.game() : shelf.selectedGame(); if (g != null) options(g); }
    public void openSheetForTest(String what) {
        GameInfo g = detailShown ? detail.game() : shelf.selectedGame();
        switch (what) {
            case "screen": detail.key(Canvas.KEY_DOWN); detail.key(Canvas.KEY_FIRE); break;
            case "quit": askQuit(); break;
            case "compat": if (g != null) showCompat(g); break;
            default: if (g != null) options(g);
        }
    }
    public void closeSheet() { sheet.close(); }
    public boolean isSheetOpen() { return sheet.isOpen(); }
    public boolean isDetailShown() { return detailShown; }
    public int currentTab() { return topBar.selected(); }
    public String selectedName() { GameInfo g = shelf.selectedGame(); return g == null ? (shelf.addTileSelected() ? "<add>" : null) : g.name(); }
    /** Lay out and settle all animations so a paint reflects the final state. */
    public void settle() {
        for (int i = 0; i < 3; i++) layoutAll(root);
        shelf.settle(); detail.settle(); sheet.settle();
    }
    /** Feed one MIDP key code as if it came from the gamepad (tests). */
    public void key(int code) { onKey(code); }
    private static void layoutAll(Component c) { if (c instanceof Container) { Container k = (Container) c; k.doLayout(); for (Component ch : k.getComponents()) layoutAll(ch); } }

    // ------------------------------------------------------------------ UI
    private LibraryWindow() {
        root.setOpaque(true);
        root.setBackground(Theme.BG);
        home.setBackground(Theme.BG);
        home.add(topBar);          // first child paints on top
        home.add(shelf);
        home.add(settings);
        stage.add(home, "home");
        stage.add(detail, "detail");
        main.add(stage, BorderLayout.CENTER);
        main.add(hints, BorderLayout.SOUTH);
        root.add(main, JLayeredPane.DEFAULT_LAYER);
        root.add(sheet, JLayeredPane.MODAL_LAYER);
        root.setLayout(new LayoutManager() {
            @Override public void addLayoutComponent(String n, Component c) {}
            @Override public void removeLayoutComponent(Component c) {}
            @Override public Dimension preferredLayoutSize(Container c) { return new Dimension(1280, 800); }
            @Override public Dimension minimumLayoutSize(Container c) { return new Dimension(760, 480); }
            @Override public void layoutContainer(Container c) { for (Component k : c.getComponents()) k.setBounds(0, 0, c.getWidth(), c.getHeight()); }
        });
        topBar.setOnSelect(i -> applyTab(null));
        sheet.setOnClose(this::updateHints);
        shelf.onSelectionChanged = this::updateHints;

        TransferHandler dnd = new TransferHandler() {
            @Override public boolean canImport(TransferSupport s) { return s.isDataFlavorSupported(DataFlavor.javaFileListFlavor); }
            @Override @SuppressWarnings("unchecked") public boolean importData(TransferSupport s) {
                try { importFiles((List<File>) s.getTransferable().getTransferData(DataFlavor.javaFileListFlavor)); return true; }
                catch (Exception e) { return false; }
            }
        };
        root.setTransferHandler(dnd);
        shelf.setTransferHandler(dnd);
        settings.setTransferHandler(dnd);
        buildSettingsRows();
        reload(null);
    }

    private void buildSettingsRows() {
        List<RowList.Row> rows = new ArrayList<>();
        rows.add(RowList.Row.header("Library"));
        rows.add(new RowList.Row("Add games\u2026", "Import .jar / .jad files, or a whole folder of them", this::chooseFiles).chevron());
        rows.add(RowList.Row.header("Defaults for every game"));
        rows.add(new RowList.Row("Global settings", "Screen size, scaling, compatibility tweaks and performance", this::openGlobalSettings).chevron());
        rows.add(new RowList.Row("Global key mapping", "Keyboard and controller bindings used unless a game has its own", () -> openGlobalKeyMapper(root)).chevron());
        rows.add(RowList.Row.header("Application"));
        rows.add(new RowList.Row("Quit J2ME Deck", null, () -> System.exit(0)).danger());
        settings.list.setRows(rows);
    }

    // ------------------------------------------------------------------ tabs and data
    private List<GameInfo> forTab(int tab) {
        List<GameInfo> out = new ArrayList<>();
        switch (tab) {
            case 1:
                for (GameInfo g : all) if (LibraryState.lastPlayed(g) > 0) out.add(g);
                out.sort(Comparator.comparingLong((GameInfo g) -> LibraryState.lastPlayed(g)).reversed());
                break;
            case 2:
                for (GameInfo g : all) if (LibraryState.isFavorite(g)) out.add(g);
                break;
            default: out.addAll(all);
        }
        return out;
    }

    private void applyTab(GameInfo keep) {
        int tab = topBar.selected();
        boolean isSettings = tab == TAB_SETTINGS;
        shelf.setVisible(!isSettings);
        settings.setVisible(isSettings);
        if (!isSettings) {
            String t, h;
            if (tab == 1) { t = "Nothing played yet"; h = "Games you play show up here, most recent first."; }
            else if (tab == 2) { t = "No favorites yet"; h = "Press Y on a game, or tap the star on its page, to pin it here."; }
            else { t = "Your library is empty"; h = "Add J2ME games (.jar / .jad), or drop files or a whole folder onto this window."; }
            shelf.setGames(forTab(tab), tab == 0, t, h, keep);
        }
        setZone(false);
        refreshStatus();
        updateHints();
        root.repaint();
    }

    private void reload(Path select) {
        all.clear();
        all.addAll(Library.list());
        for (GameInfo g : all) {
            if (!compat.containsKey(g.jar)) scanner.submit(() -> {
                try { compat.put(g.jar, ApiScanner.scan(g.jar.toFile())); } catch (Exception e) { return; }
                SwingUtilities.invokeLater(() -> { shelf.refreshInfo(); detail.refresh(); });
            });
        }
        GameInfo keep = null;
        if (select != null) for (GameInfo g : all) if (g.jar.equals(select)) keep = g;
        applyTab(keep);
    }

    private void refreshStatus() {
        hints.setStatus(all.size() + (all.size() == 1 ? " game" : " games"));
    }

    private void setZone(boolean tabs) {
        tabsFocused = tabs;
        topBar.setFocused(tabs);
        boolean settingsTab = topBar.selected() == TAB_SETTINGS;
        shelf.setFocused(!tabs);
        settings.list.setActive(!tabs && settingsTab);
    }

    private void updateHints() {
        if (sheet.isOpen()) hints.setHints("Tap an option  \u00B7  Tap outside to close", "A", "Select", "B", "Close");
        else if (detailShown) hints.setHints("Tap a card to change it  \u00B7  Drag to scroll", "A", "Select", "X", "Options", "Y", "Favorite", "B", "Back");
        else if (topBar.selected() == TAB_SETTINGS) hints.setHints("Tap a row", "A", "Select", "LB", "", "RB", "Tabs", "B", "Back");
        else if (shelf.addTileSelected() || shelf.isEmpty())
            hints.setHints("Swipe to browse  \u00B7  Tap the tile to add games", "A", "Add games", "LB", "", "RB", "Tabs", "B", topBar.selected() == 0 ? "Quit" : "Back");
        else hints.setHints("Swipe to browse  \u00B7  Tap a game to focus, tap again to play  \u00B7  Hold for options",
                "A", "Play", "X", "Details", "Y", "Favorite", "LB", "", "RB", "Tabs", "B", topBar.selected() == 0 ? "Quit" : "Back");
    }

    // ------------------------------------------------------------------ host callbacks
    @Override public ApiScanner.Report compat(GameInfo g) { return compat.get(g.jar); }

    @Override public void details(GameInfo g) {
        detailShown = true;
        detail.show(g);
        ((CardLayout) stage.getLayout()).show(stage, "detail");
        updateHints();
        root.repaint();
    }

    @Override public void back() {
        detailShown = false;
        ((CardLayout) stage.getLayout()).show(stage, "home");
        shelf.refreshInfo();
        updateHints();
        root.repaint();
    }

    @Override public void toggleFavorite(GameInfo g) {
        LibraryState.toggleFavorite(g);
        if (!detailShown && topBar.selected() == 2) applyTab(g); else { shelf.refreshInfo(); detail.refresh(); }
        root.repaint();
    }

    @Override public void sheet(String title, String message, List<RowList.Row> rows) { sheet.showRows(title, message, rows); updateHints(); }
    @Override public void fullSettings(GameInfo g) { GameSettingsDialog.show(root, g, this::refreshAfterSettings); }
    @Override public void keyMapping(GameInfo g) { openKeyMapper(root, g); }
    private void refreshAfterSettings() { shelf.refreshInfo(); detail.refresh(); }

    @Override public void options(GameInfo g) {
        List<RowList.Row> rows = new ArrayList<>();
        rows.add(new RowList.Row("Settings\u2026", "Every option for this game", () -> GameSettingsDialog.show(root, g, this::refreshAfterSettings)).chevron());
        rows.add(new RowList.Row("Key mapping\u2026", "Controller and keyboard bindings for this game", () -> openKeyMapper(root, g)).chevron());
        rows.add(new RowList.Row("Compatibility report", "Which phone features it uses and what is missing", () -> showCompat(g)).chevron());
        rows.add(new RowList.Row("Show log", "Output from the last run", () -> showLog(g)).chevron());
        rows.add(new RowList.Row(LibraryState.isFavorite(g) ? "Remove from favorites" : "Add to favorites", null, () -> toggleFavorite(g)));
        rows.add(new RowList.Row("Remove from library\u2026", null, () -> remove(g)).danger());
        sheet.showRows(g.name(), "", rows);
        updateHints();
    }

    @Override public void addGames() { chooseFiles(); }

    // ------------------------------------------------------------------ actions
    private void chooseFiles() {
        JFileChooser fc = new JFileChooser(new File(System.getProperty("user.home"), "Downloads").isDirectory()
                ? new File(System.getProperty("user.home"), "Downloads") : new File(System.getProperty("user.home")));
        fc.setDialogTitle("Add J2ME games (.jar / .jad, or a folder)");
        fc.setMultiSelectionEnabled(true);
        fc.setFileSelectionMode(JFileChooser.FILES_AND_DIRECTORIES);
        fc.setFileFilter(new FileNameExtensionFilter("J2ME games (*.jar, *.jad)", "jar", "jad"));
        if (fc.showOpenDialog(root) == JFileChooser.APPROVE_OPTION) importFiles(java.util.Arrays.asList(fc.getSelectedFiles()));
    }

    private void importFiles(List<File> files) {
        int ok = 0; Path last = null; StringBuilder err = new StringBuilder();
        for (File f : files) {
            try {
                for (GameInfo g : Library.addAll(f.toPath())) { ok++; last = g.jar; }
            } catch (IOException e) { err.append(f.getName()).append(": ").append(e.getMessage()).append('\n'); }
        }
        if (topBar.selected() == TAB_SETTINGS) topBar.select(0, false);
        reload(last);
        hints.setStatus(ok + " game(s) imported" + (err.length() > 0 ? ", some failed" : ""));
        if (err.length() > 0) { sheet.showText("Import problems", err.toString()); updateHints(); }
    }

    private void remove(GameInfo g) {
        List<RowList.Row> rows = new ArrayList<>();
        rows.add(new RowList.Row("Remove game only", "Keeps its saves and settings", () -> doRemove(g, false)));
        rows.add(new RowList.Row("Remove game and saves", "Deletes its save data too", () -> doRemove(g, true)).danger());
        sheet.showRows("Remove \u201C" + g.name() + "\u201D?", "It will disappear from the library.", rows);
        updateHints();
    }

    private void doRemove(GameInfo g, boolean saves) {
        try { Library.remove(g, saves); Covers.forget(g); } catch (IOException e) { sheet.showMessage("Could not remove the game", e.getMessage()); updateHints(); }
        if (detailShown) back();
        reload(null);
    }

    private void showLog(GameInfo g) {
        String text;
        try {
            Path log = Library.logFile(g);
            if (!java.nio.file.Files.exists(log)) text = "No log yet. Start the game once and it will appear here.\n\n" + log;
            else {
                List<String> lines = java.nio.file.Files.readAllLines(log, java.nio.charset.StandardCharsets.UTF_8);
                text = String.join("\n", lines.subList(Math.max(0, lines.size() - 400), lines.size())) + "\n\n[" + log + "]";
            }
        } catch (IOException e) { text = "Could not read the log: " + e.getMessage(); }
        sheet.showText("Log \u2013 " + g.name(), text);
        sheet.textToEnd();
        updateHints();
    }

    private void showCompat(GameInfo g) {
        String text;
        try { text = ApiScanner.scan(g.jar.toFile()).details(); } catch (IOException e) { text = "Could not scan: " + e.getMessage(); }
        sheet.showText("Compatibility \u2013 " + g.name(), text);
        updateHints();
    }

    /** Kept for the settings dialog, which is itself a Swing dialog and cannot show a sheet. */
    static void showCompatibility(Component parent, GameInfo g) {
        String text;
        try { text = ApiScanner.scan(g.jar.toFile()).details(); } catch (IOException e) { text = "Could not scan: " + e.getMessage(); }
        JTextArea a = new JTextArea(text, 18, 56);
        a.setEditable(false);
        a.setFont(new Font(Font.MONOSPACED, Font.PLAIN, Math.round(Theme.base() * 0.85f)));
        JOptionPane.showMessageDialog(parent, new JScrollPane(a), "Compatibility - " + g.name(), JOptionPane.PLAIN_MESSAGE);
    }

    private void openGlobalSettings() { GameSettingsDialog.showGlobal(root, this::refreshAfterSettings); }

    /** The key map every game starts from (a game's own map only stores what it changes). */
    static void openGlobalKeyMapper(Component parent) {
        try { KeyMapper.showGlobal(parent); }
        finally { GamepadReader.reloadMappings(); InputManager.get().reloadMappings(); }
    }

    static void openKeyMapper(Component parent, GameInfo g) {
        System.setProperty("j2me.title", g.name());
        System.setProperty("j2me.profileid", g.id());
        try { KeyMapper.show(parent); }
        finally { System.clearProperty("j2me.title"); System.clearProperty("j2me.profileid"); GamepadReader.reloadMappings(); InputManager.get().reloadMappings(); }
    }

    private void askQuit() {
        List<RowList.Row> rows = new ArrayList<>();
        rows.add(new RowList.Row("Quit J2ME Deck", null, () -> System.exit(0)).danger());
        sheet.showRows("Quit?", "", rows);
        updateHints();
    }

    // ------------------------------------------------------------------ launching
    @Override public void play(GameInfo g) {
        if (running != null && running.isAlive()) return;
        if (g.midlets.size() > 1) {            // a suite with several MIDlets: ask which one to run
            List<RowList.Row> rows = new ArrayList<>();
            for (int i = 0; i < g.midlets.size(); i++) {
                String[] m = g.midlets.get(i);
                final int idx = i + 1;
                rows.add(new RowList.Row(m[0].isEmpty() ? m[2] : m[0], "Program " + idx, () -> launch(g, idx)));
            }
            sheet.showRows(g.name(), "This suite contains several programs. Which one do you want to run?", rows);
            updateHints();
            return;
        }
        launch(g, 1);
    }

    private void launch(GameInfo g, int midlet) {
        if (running != null && running.isAlive()) return;
        try {
            ProcessBuilder pb = new ProcessBuilder(command(g, midlet));
            pb.redirectErrorStream(true).redirectOutput(Library.rotateLog(g).toFile());
            hints.setStatus("Running " + g.name() + "\u2026");
            running = pb.start();
            LibraryState.markPlayed(g);
            InputManager.setSink(null);
            GamepadReader.setUiButtonSink(null);
            if (frame != null) frame.setVisible(false);
            Thread w = new Thread(() -> {
                int code = -1;
                try { code = running.waitFor(); } catch (InterruptedException ignored) {}
                final int exit = code;
                SwingUtilities.invokeLater(() -> {
                    running = null;
                    attachGamepad();
                    if (frame != null) { frame.setVisible(true); frame.toFront(); }
                    if (topBar.selected() == 1) applyTab(g); else { shelf.refreshInfo(); detail.refresh(); }
                    hints.setStatus(exit == 0 ? all.size() + (all.size() == 1 ? " game" : " games") : g.name() + " exited with code " + exit + " (see Options \u203A Show log)");
                });
            }, "game-watch");
            w.setDaemon(true);
            w.start();
        } catch (IOException e) {
            sheet.showMessage("Could not start the game", e.getMessage());
            updateHints();
        }
    }

    /** The command line that starts g in its own JVM (so the library survives crashes and "Exit" in the game). */
    public static java.util.List<String> command(GameInfo g) { return command(g, 1); }

    public static java.util.List<String> command(GameInfo g, int midlet) {
        java.util.List<String> cmd = new java.util.ArrayList<>();
        cmd.add(Path.of(System.getProperty("java.home"), "bin", "java").toString());
        cmd.addAll(java.util.List.of("-Xmx512m", "-XX:+UseSerialGC", "-Dsun.java2d.opengl=false"));
        cmd.addAll(j2medeck.GlBackend.jvmFlags());      // native access / preview flags for the OpenGL ES output
        // MIDI loudness leveling (SharedMidi.openLeveled) reads the synthesizer through com.sun.media.sound; without this flag quiet music
        // tracks (e.g. Medal of Honor Airborne's title theme) stay about 19 dB too soft to hear. run.sh passes the same flag.
        cmd.add("--add-exports"); cmd.add("java.desktop/com.sun.media.sound=ALL-UNNAMED");
        // same class path as this process, so optional jars in lib/ (e.g. an MP3 decoder) are available to the game too
        cmd.add("-cp"); cmd.add(System.getProperty("java.class.path")); cmd.add("j2medeck.LinuxLoader");
        if (midlet > 1) { cmd.add("--midlet"); cmd.add(String.valueOf(midlet)); }
        cmd.add(g.jar.toString());
        return cmd;
    }

    // ------------------------------------------------------------------ gamepad and keyboard
    private void attachGamepad() {
        InputManager.setSink(this::onKey); // d-pad and sticks still provide navigation directions
        GamepadReader.setUiButtonSink(this::onPhysicalButton);
    }

    /** Fixed Steam Deck library controls, deliberately independent from the Nokia game keymap. */
    private void onPhysicalButton(int button) {
        SwingUtilities.invokeLater(() -> {
        switch (button) {
            case 0: onKey(Canvas.KEY_FIRE); break;        // A: Play / Select
            case 1: onKey(Canvas.KEY_SOFT_RIGHT); break;  // B: Back / Quit
            case 2: onKey(Canvas.KEY_SOFT_LEFT); break;   // X: Details
            case 3: onKey(Canvas.KEY_NUM5); break;        // Y: Favorite
            case 4: onKey(Canvas.KEY_STAR); break;        // LB: Previous tab
            case 5: onKey(Canvas.KEY_POUND); break;       // RB: Next tab
            case 6: onKey(Canvas.KEY_CLEAR); break;       // View: Back
            case 7: onKey(Canvas.KEY_FIRE); break;        // Menu/Start: Select
            default: break;
        }
        });
    }

    /** Arrow keys, Enter, Esc and friends use the same path as the gamepad (mapped through the user's key map). */
    private void installKeyboard() {
        KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(e -> {
            if (e.getID() != KeyEvent.KEY_PRESSED || running != null || frame == null || !frame.isActive()) return false;
            if (JOptionPaneOpen.isModalShowing()) return false;
            if (KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner() instanceof JTextComponent) return false;
            int code = InputManager.mapKey(e);
            if (code == 0) return false;
            onKey(code);
            return true;
        });
    }

    private void onKey(int code) {
        if (JOptionPaneOpen.isModalShowing()) return;
        Gesture.pointerMode = false;
        hints.repaint();
        if (sheet.isOpen()) { sheet.key(code); root.repaint(); return; }
        if (detailShown) { detail.key(code); return; }
        boolean settingsTab = topBar.selected() == TAB_SETTINGS;
        switch (code) {
            case Canvas.KEY_STAR: topBar.select(topBar.selected() - 1, true); return;      // LB
            case Canvas.KEY_POUND: topBar.select(topBar.selected() + 1, true); return;     // RB
            case Canvas.KEY_LEFT: if (tabsFocused) topBar.select(topBar.selected() - 1, true); else if (!settingsTab) shelf.move(-1); return;
            case Canvas.KEY_RIGHT: if (tabsFocused) topBar.select(topBar.selected() + 1, true); else if (!settingsTab) shelf.move(1); return;
            case Canvas.KEY_UP: if (!tabsFocused && (!settingsTab || !settings.list.move(-1))) setZone(true); return;
            case Canvas.KEY_DOWN: if (tabsFocused) setZone(false); else if (settingsTab) settings.list.move(1); return;
            case Canvas.KEY_FIRE:
                if (tabsFocused) setZone(false);
                else if (settingsTab) settings.list.activate();
                else activateSelected();
                return;
            case Canvas.KEY_SOFT_LEFT: { GameInfo g = shelf.selectedGame(); if (!settingsTab && !tabsFocused && g != null) details(g); return; }       // X / Menu
            case Canvas.KEY_NUM5: { GameInfo g = shelf.selectedGame(); if (!settingsTab && g != null) toggleFavorite(g); return; }                          // Y
            case Canvas.KEY_SOFT_RIGHT: case Canvas.KEY_CLEAR:                                                                                           // B
                if (topBar.selected() != 0) { topBar.select(0, true); }
                else askQuit();
                return;
            default:
        }
    }

    private void activateSelected() {
        GameInfo g = shelf.selectedGame();
        if (g != null) play(g); else if (shelf.addTileSelected() || shelf.isEmpty()) chooseFiles();
    }

    /** Ignore the gamepad while a modal dialog (settings, key mapper, file chooser) has the focus. */
    private static final class JOptionPaneOpen {
        static boolean isModalShowing() {
            for (Window w : Window.getWindows()) if (w.isShowing() && w instanceof Dialog && ((Dialog) w).isModal()) return true;
            return false;
        }
    }
}
