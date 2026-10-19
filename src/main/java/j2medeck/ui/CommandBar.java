// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 J2ME Deck contributors
package j2medeck.ui;

import javax.microedition.lcdui.Command;
import javax.microedition.lcdui.Displayable;
import javax.swing.*;
import java.awt.BorderLayout;
import java.awt.Cursor;
import java.awt.Font;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;

/** Phone-style soft-key bar: left key, right key, and a pop-up menu when there are more than two commands. */
public final class CommandBar extends JPanel {
    private static final Command MENU = new Command("Menu", Command.SCREEN, 0);

    /** How a command list maps onto the two soft keys (MIDP implementations differ; this is the usual layout). */
    public static final class Layout {
        public Command left, right;
        public final List<Command> menu = new ArrayList<>();
    }

    public static Layout layout(Command[] all) {
        Layout l = new Layout();
        List<Command> rest = new ArrayList<>();
        for (Command c : all) {
            int t = c.getCommandType();
            if (l.right == null && (t == Command.BACK || t == Command.CANCEL || t == Command.EXIT || t == Command.STOP)) l.right = c;
            else rest.add(c);
        }
        if (l.right != null) {
            if (rest.size() == 1) l.left = rest.get(0);
            else if (rest.size() > 1) { l.left = MENU; l.menu.addAll(rest); }
        } else if (rest.size() == 1) l.left = rest.get(0);
        else if (rest.size() == 2) { l.left = rest.get(0); l.right = rest.get(1); }
        else if (rest.size() > 2) { l.left = MENU; l.menu.addAll(rest); }
        return l;
    }

    private final JLabel left = new JLabel(" "), right = new JLabel(" ");
    private Displayable displayable;
    private Layout current = new Layout();
    private Command extraLeft;   // e.g. an implicit "OK" on alerts
    /** Lets the owner consume a command before the application's listener sees it (Alert dismissal). */
    public java.util.function.Predicate<Command> interceptor;

    public CommandBar() {
        super(new BorderLayout());
        setBackground(Theme.PANEL_ALT);
        setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, Theme.BORDER));
        for (JLabel b : new JLabel[]{left, right}) {
            b.setFont(Theme.font(Font.BOLD, 1.0f));
            b.setForeground(Theme.ACCENT);
            b.setBorder(BorderFactory.createEmptyBorder(8, 16, 8, 16));
            b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        }
        right.setHorizontalAlignment(SwingConstants.RIGHT);
        add(left, BorderLayout.WEST);
        add(right, BorderLayout.EAST);
        left.addMouseListener(new MouseAdapter() { @Override public void mousePressed(MouseEvent e) { softKey(true); } });
        right.addMouseListener(new MouseAdapter() { @Override public void mousePressed(MouseEvent e) { softKey(false); } });
    }

    public void bind(Displayable d, Command implicitLeft) {
        displayable = d;
        extraLeft = implicitLeft;
        update();
    }

    public void update() {
        Displayable d = displayable;
        Command[] cmds = d == null ? new Command[0] : d.getCommands();
        if (extraLeft != null) {
            Command[] n = new Command[cmds.length + 1];
            System.arraycopy(cmds, 0, n, 0, cmds.length);
            n[cmds.length] = extraLeft;
            cmds = n;
        }
        current = layout(cmds);
        left.setText(current.left == null ? " " : current.left.getLabel());
        right.setText(current.right == null ? " " : current.right.getLabel());
        revalidate(); repaint();
    }

    /** True when the app attached its own commands to an Alert (then it must be dismissed explicitly). */
    public boolean hasCommandsBeyondDismiss() { return displayable != null && displayable.getCommands().length > 0; }

    public boolean hasCommands() { return current.left != null || current.right != null; }

    /** @return true if a command was available and has been fired. */
    public boolean softKey(boolean isLeft) {
        Command c = isLeft ? current.left : current.right;
        if (c == null) return false;
        if (c == MENU) { showMenu(); return true; }
        fire(c);
        return true;
    }

    private void showMenu() {
        JPopupMenu m = new JPopupMenu();
        for (Command c : current.menu) {
            JMenuItem mi = new JMenuItem(c.getLabel());
            mi.setFont(Theme.font(Font.PLAIN, 1.0f));
            mi.addActionListener(e -> fire(c));
            m.add(mi);
        }
        m.show(left, 0, -m.getPreferredSize().height);
    }

    private void fire(Command c) {
        if (interceptor != null && interceptor.test(c)) return;
        Displayable d = displayable;
        if (d != null) d.$fireCommand(c);
    }
}
