// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 J2ME Deck contributors
import javax.microedition.lcdui.*;
import javax.imageio.ImageIO;
import java.awt.EventQueue;
import java.awt.image.BufferedImage;
import java.io.File;

/** Renders LCDUI high-level screens offscreen (no window needed) into /tmp/ui_*.png. */
public class UiShots {
    static void shot(String name, Displayable d, int key) throws Exception {
        Display disp = Display.getDisplay(null);
        EventQueue.invokeAndWait(() -> disp.setCurrent(d));
        EventQueue.invokeAndWait(() -> { for (int i = 0; i < key; i++) Display.$routeKey(0, Canvas.KEY_DOWN); });
        EventQueue.invokeAndWait(() -> {});
        BufferedImage img = Display.$snapshotWindow(480, 640);
        ImageIO.write(img, "png", new File("/tmp/ui_" + name + ".png"));
        System.out.println("saved ui_" + name);
    }
    public static void main(String[] a) throws Exception {
        Display.initDisplay();
        Display disp = Display.getDisplay(null);
        final String[] log = new String[1];

        List menu = new List("Main Menu", Choice.IMPLICIT, new String[]{"New Game", "Continue", "Options", "High Scores", "Help", "Exit"}, null);
        Command exit = new Command("Exit", Command.EXIT, 1), sel = new Command("Select", Command.OK, 1);
        menu.addCommand(exit); menu.addCommand(sel);
        menu.setCommandListener((c, d) -> log[0] = c.getLabel());
        shot("list", menu, 2);
        EventQueue.invokeAndWait(() -> Display.$routeKey(0, Canvas.KEY_FIRE));
        System.out.println("list FIRE -> command: [" + log[0] + "] selectedIndex=" + menu.getSelectedIndex());
        EventQueue.invokeAndWait(() -> Display.$routeKey(0, Canvas.KEY_SOFT_RIGHT));
        System.out.println("soft right -> command: [" + log[0] + "]");

        Form f = new Form("Options");
        f.append(new StringItem("Player", "Agent Fisher"));
        TextField name = new TextField("Name", "Sam", 12, TextField.ANY);
        f.append(name);
        ChoiceGroup diff = new ChoiceGroup("Difficulty", Choice.EXCLUSIVE, new String[]{"Easy", "Normal", "Hard"}, null);
        diff.setSelectedIndex(1, true);
        f.append(diff);
        ChoiceGroup fx = new ChoiceGroup("Effects", Choice.MULTIPLE, new String[]{"Music", "Sound", "Vibration"}, null);
        fx.setSelectedIndex(0, true);
        f.append(fx);
        f.append(new Gauge("Volume", true, 10, 6));
        f.append(new ChoiceGroup("Language", Choice.POPUP, new String[]{"English", "Deutsch", "Francais"}, null));
        StringItem btn = new StringItem(null, "Reset progress", Item.BUTTON);
        btn.setDefaultCommand(new Command("Reset", Command.ITEM, 1));
        btn.setItemCommandListener((c, item) -> log[0] = "item:" + c.getLabel());
        f.append(btn);
        f.setItemStateListener(item -> log[0] = "state:" + item.getLabel());
        f.addCommand(new Command("Back", Command.BACK, 1));
        f.addCommand(new Command("Save", Command.OK, 1));
        f.setTicker(new Ticker("Remember to save your changes"));
        shot("form", f, 3);
        EventQueue.invokeAndWait(() -> Display.$routeKey(0, Canvas.KEY_FIRE));
        System.out.println("form FIRE on choice -> " + log[0] + ", Hard selected=" + diff.isSelected(2));

        Alert al = new Alert("Warning", "Low battery!\nPlease save your game.", null, AlertType.WARNING);
        al.setTimeout(Alert.FOREVER);
        EventQueue.invokeAndWait(() -> disp.setCurrent(al, f));
        EventQueue.invokeAndWait(() -> {});
        ImageIO.write(Display.$snapshotWindow(480, 640), "png", new File("/tmp/ui_alert.png"));
        EventQueue.invokeAndWait(() -> Display.$routeKey(0, Canvas.KEY_FIRE));
        System.out.println("alert dismissed -> back to Form: " + (disp.getCurrent() == f));

        TextBox tb = new TextBox("Enter your name", "", 20, TextField.ANY);
        tb.addCommand(new Command("OK", Command.OK, 1));
        tb.addCommand(new Command("Cancel", Command.CANCEL, 2));
        shot("textbox", tb, 0);
        System.exit(0);
    }
}
