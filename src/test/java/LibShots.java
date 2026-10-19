// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 J2ME Deck contributors
import j2medeck.*;
import j2medeck.ui.*;
import javax.imageio.ImageIO;
import javax.microedition.lcdui.Canvas;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Path;

/**
 * Renders the console-style library offscreen into /tmp/lib_*.png: home shelf, game page, sheets, settings tab and the empty state.
 * Run headless with -Duser.home=&lt;scratch dir&gt; and a few .jar paths as arguments.
 */
public class LibShots {
    static void shot(LibraryWindow w, int cw, int ch, String file) throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            try {
                JComponent c = w.content();
                c.setSize(cw, ch);
                w.settle();
                BufferedImage img = new BufferedImage(cw, ch, BufferedImage.TYPE_INT_RGB);
                Graphics2D g = img.createGraphics();
                c.paint(g);
                g.dispose();
                ImageIO.write(img, "png", new File(file));
                System.out.println("saved " + file);
            } catch (Exception e) { throw new RuntimeException(e); }
        });
    }
    static void run(Runnable r) throws Exception { SwingUtilities.invokeAndWait(r); }

    public static void main(String[] a) throws Exception {
        final LibraryWindow[] w = new LibraryWindow[1];
        // empty library first
        run(() -> w[0] = LibraryWindow.createForTest());
        shot(w[0], 1280, 800, "/tmp/lib_empty.png");

        for (String j : a) { var g = Library.add(Path.of(j)); System.out.println("imported: " + g.name() + " | " + g.vendor() + " | v" + g.version() + " | midlets=" + g.midlets.size() + " icon=" + (g.iconBytes != null)); }
        System.out.println("library size: " + Library.list().size());
        Library.add(Path.of(a[0]));                 // importing the same file again must not duplicate it
        System.out.println("after re-import: " + Library.list().size());
        var games = Library.list();
        LibraryState.toggleFavorite(games.get(1));
        LibraryState.toggleFavorite(games.get(3));
        LibraryState.markPlayed(games.get(2));
        LibraryState.markPlayed(games.get(2));
        run(() -> w[0] = LibraryWindow.createForTest());
        Thread.sleep(1500);                         // let the background compatibility scans finish

        run(() -> w[0].selectGame(2));
        shot(w[0], 1280, 800, "/tmp/lib_home.png");
        run(() -> w[0].selectGame(w[0].games().size()));                 // the "Add games" tile
        shot(w[0], 1280, 800, "/tmp/lib_home_add.png");
        run(() -> w[0].selectGame(4));
        shot(w[0], 1280, 720, "/tmp/lib_home_720.png");
        shot(w[0], 800, 500, "/tmp/lib_home_small.png");
        shot(w[0], 760, 480, "/tmp/lib_home_min.png");

        run(() -> w[0].selectGame(2));
        run(() -> w[0].openDetail());
        shot(w[0], 1280, 800, "/tmp/lib_detail.png");
        shot(w[0], 1280, 720, "/tmp/lib_detail_720.png");
        shot(w[0], 760, 480, "/tmp/lib_detail_min.png");
        run(() -> w[0].openOptionsSheet());
        shot(w[0], 1280, 800, "/tmp/lib_sheet_options.png");
        run(() -> w[0].closeSheet());
        run(() -> w[0].openSheetForTest("screen"));
        shot(w[0], 1280, 800, "/tmp/lib_sheet_screen.png");
        run(() -> w[0].closeSheet());
        run(() -> w[0].openSheetForTest("compat"));
        shot(w[0], 1280, 800, "/tmp/lib_sheet_compat.png");
        run(() -> w[0].closeSheet());
        run(() -> w[0].key(Canvas.KEY_SOFT_RIGHT));                     // B: back to the shelf
        run(() -> w[0].selectTab(1));
        shot(w[0], 1280, 800, "/tmp/lib_recent.png");
        run(() -> w[0].selectTab(2));
        shot(w[0], 1280, 800, "/tmp/lib_favorites.png");
        run(() -> w[0].selectTab(3));
        shot(w[0], 1280, 800, "/tmp/lib_settings.png");
        run(() -> w[0].selectTab(0));
        run(() -> w[0].openSheetForTest("quit"));
        shot(w[0], 1280, 800, "/tmp/lib_sheet_quit.png");

        // d-pad flow: right, right, up (tabs), right (next tab), down
        run(() -> w[0].closeSheet());
        run(() -> { w[0].selectGame(0); w[0].key(Canvas.KEY_UP); });
        shot(w[0], 1280, 800, "/tmp/lib_tabs_focus.png");

        var info = games.get(0);
        var form = GameSettingsDialog.form(info);
        System.out.println("settings form tabs: " + form.panel.getTabCount());
        System.out.println("launch command: " + String.join(" ", LibraryWindow.command(info)));
        System.exit(0);
    }
}
