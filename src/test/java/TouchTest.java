// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 J2ME Deck contributors
import j2medeck.*;
import j2medeck.ui.*;
import javax.microedition.lcdui.Canvas;
import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.nio.file.Path;

/** Drives the library with synthetic touch/mouse events and d-pad keys and checks the resulting state. Run headless with -Duser.home=&lt;scratch&gt;. */
public class TouchTest {
    static int fails;
    static void check(String what, boolean ok) { System.out.println((ok ? "PASS  " : "FAIL  ") + what); if (!ok) fails++; }
    static void edt(Runnable r) throws Exception { SwingUtilities.invokeAndWait(r); }

    /** Hit areas are computed while painting, exactly as on screen, so paint before every touch. */
    static void paint(Component root) throws Exception {
        edt(() -> {
            java.awt.image.BufferedImage img = new java.awt.image.BufferedImage(root.getWidth(), root.getHeight(), java.awt.image.BufferedImage.TYPE_INT_RGB);
            Graphics2D g = img.createGraphics(); root.paint(g); g.dispose();
        });
    }
    static void mouse(Component root, int id, int x, int y, long when) {
        Component c = SwingUtilities.getDeepestComponentAt(root, x, y);
        Point p = SwingUtilities.convertPoint(root, x, y, c);
        int mods = id == MouseEvent.MOUSE_RELEASED ? 0 : MouseEvent.BUTTON1_DOWN_MASK;
        c.dispatchEvent(new MouseEvent(c, id, when, mods, p.x, p.y, 1, false, MouseEvent.BUTTON1));
    }
    static void tap(Component root, int x, int y) throws Exception {
        paint(root);
        long t = System.currentTimeMillis();
        edt(() -> { mouse(root, MouseEvent.MOUSE_PRESSED, x, y, t); mouse(root, MouseEvent.MOUSE_RELEASED, x, y, t + 60); });
    }
    static void swipe(Component root, int x0, int y0, int x1, int y1, int steps, long ms) throws Exception {
        paint(root);
        long t = System.currentTimeMillis();
        edt(() -> mouse(root, MouseEvent.MOUSE_PRESSED, x0, y0, t));
        for (int i = 1; i <= steps; i++) {
            int x = x0 + (x1 - x0) * i / steps, y = y0 + (y1 - y0) * i / steps;
            long when = t + ms * i / steps;
            edt(() -> mouse(root, MouseEvent.MOUSE_DRAGGED, x, y, when));
        }
        edt(() -> mouse(root, MouseEvent.MOUSE_RELEASED, x1, y1, t + ms));
    }

    public static void main(String[] a) throws Exception {
        for (String j : a) Library.add(Path.of(j));
        final LibraryWindow[] w = new LibraryWindow[1];
        edt(() -> w[0] = LibraryWindow.createForTest());
        Thread.sleep(800);
        edt(() -> { w[0].content().setSize(1280, 800); w[0].settle(); });
        Component root = w[0].content();
        int n = w[0].games().size();
        check("library holds the imported games (" + n + ")", n == a.length);
        check("first game is focused", "Asphalt 4: Elite Racing".equals(w[0].selectedName()));

        // d-pad
        edt(() -> { w[0].key(Canvas.KEY_RIGHT); w[0].key(Canvas.KEY_RIGHT); });
        check("two RIGHT presses focus the third game", "DiamondRush".equals(w[0].selectedName()));
        edt(() -> w[0].key(Canvas.KEY_LEFT));
        check("LEFT goes back one", "Bounce Tales".equals(w[0].selectedName()));
        edt(() -> { for (int i = 0; i < 20; i++) w[0].key(Canvas.KEY_RIGHT); });
        check("browsing stops at the Add games tile", "<add>".equals(w[0].selectedName()));
        edt(() -> { for (int i = 0; i < 30; i++) w[0].key(Canvas.KEY_LEFT); });
        check("and at the first game on the other side", "Asphalt 4: Elite Racing".equals(w[0].selectedName()));

        // tabs via LB / RB and the top row
        edt(() -> w[0].key(Canvas.KEY_POUND));
        check("RB switches to Recent", w[0].currentTab() == 1);
        edt(() -> w[0].key(Canvas.KEY_POUND));
        edt(() -> w[0].key(Canvas.KEY_POUND));
        check("RB x3 reaches Settings", w[0].currentTab() == 3);
        edt(() -> w[0].key(Canvas.KEY_STAR));
        edt(() -> w[0].key(Canvas.KEY_STAR));
        edt(() -> w[0].key(Canvas.KEY_STAR));
        check("LB x3 is back at Library", w[0].currentTab() == 0);
        edt(() -> { w[0].key(Canvas.KEY_UP); w[0].key(Canvas.KEY_RIGHT); });
        check("UP moves focus to the tabs and RIGHT changes tab", w[0].currentTab() == 1);
        edt(() -> { w[0].key(Canvas.KEY_SOFT_RIGHT); });
        check("B from another tab returns to Library", w[0].currentTab() == 0);
        edt(() -> w[0].key(Canvas.KEY_SOFT_RIGHT));
        check("B on Library asks before quitting", w[0].isSheetOpen());
        edt(() -> w[0].key(Canvas.KEY_SOFT_RIGHT));
        check("B closes that sheet", !w[0].isSheetOpen());

        // details page and back
        edt(() -> { w[0].key(Canvas.KEY_DOWN); w[0].key(Canvas.KEY_SOFT_LEFT); });
        check("X opens the game page", w[0].isDetailShown());
        edt(() -> w[0].key(Canvas.KEY_SOFT_LEFT));
        check("X on the game page opens Options", w[0].isSheetOpen());
        edt(() -> w[0].key(Canvas.KEY_SOFT_RIGHT));
        check("B closes the sheet first", !w[0].isSheetOpen() && w[0].isDetailShown());
        edt(() -> w[0].key(Canvas.KEY_SOFT_RIGHT));
        check("B again goes back to the shelf", !w[0].isDetailShown());

        // Y toggles favourite
        GameInfo first = w[0].games().get(0);
        boolean before = LibraryState.isFavorite(first);
        edt(() -> w[0].key(Canvas.KEY_NUM5));
        check("Y toggles the favourite flag", LibraryState.isFavorite(first) != before);
        edt(() -> w[0].key(Canvas.KEY_NUM5));

        // ---- touch ----
        edt(() -> { w[0].selectGame(2); w[0].settle(); });
        tap(root, 550, 560);
        Thread.sleep(500);
        check("tapping another tile focuses it (no launch)", "Medal of Honor Airborne".equals(w[0].selectedName()) || "Prince of Persia: The Sands of Time".equals(w[0].selectedName()));
        System.out.println("      focused after tap: " + w[0].selectedName());
        edt(() -> { w[0].selectGame(2); w[0].settle(); });
        swipe(root, 1000, 560, 300, 560, 12, 160);
        Thread.sleep(1200);
        String afterSwipe = w[0].selectedName();
        System.out.println("      focused after swipe left: " + afterSwipe);
        check("a swipe left scrolls the shelf forward", afterSwipe != null && !afterSwipe.equals("DiamondRush"));
        swipe(root, 300, 560, 1000, 560, 12, 160);
        Thread.sleep(1200);
        System.out.println("      focused after swipe right: " + w[0].selectedName());
        check("and a swipe right brings it back", w[0].selectedName() != null && !w[0].selectedName().equals(afterSwipe));

        // top-bar tap
        tap(root, 580, 40);
        check("tapping the Favorites tab selects it", w[0].currentTab() == 2);
        tap(root, 270, 40);
        check("tapping Library returns", w[0].currentTab() == 0);

        // long press opens options
        edt(() -> { w[0].selectGame(2); w[0].settle(); });
        paint(root);
        long t = System.currentTimeMillis();
        edt(() -> mouse(root, MouseEvent.MOUSE_PRESSED, 135, 560, t));
        Thread.sleep(800);
        edt(() -> mouse(root, MouseEvent.MOUSE_RELEASED, 135, 560, System.currentTimeMillis()));
        check("long-pressing a tile opens its options sheet", w[0].isSheetOpen());
        // tap outside closes it
        tap(root, 40, 400);
        check("tapping outside the sheet closes it", !w[0].isSheetOpen());

        // details via the Details button
        tap(root, 316, 403);
        check("tapping Details opens the game page", w[0].isDetailShown());
        tap(root, 120, 52);
        check("tapping the back chip returns to the shelf", !w[0].isDetailShown());

        System.out.println(fails == 0 ? "ALL PASSED" : fails + " FAILED");
        System.exit(fails == 0 ? 0 : 1);
    }
}
