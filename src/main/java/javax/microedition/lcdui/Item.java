// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 J2ME Deck contributors
package javax.microedition.lcdui;

import java.util.ArrayList;
import java.util.List;

public abstract class Item {
    public static final int LAYOUT_DEFAULT = 0, LAYOUT_LEFT = 1, LAYOUT_RIGHT = 2, LAYOUT_CENTER = 3, LAYOUT_TOP = 0x10,
            LAYOUT_BOTTOM = 0x20, LAYOUT_VCENTER = 0x30, LAYOUT_NEWLINE_BEFORE = 0x100, LAYOUT_NEWLINE_AFTER = 0x200,
            LAYOUT_SHRINK = 0x400, LAYOUT_EXPAND = 0x800, LAYOUT_VSHRINK = 0x1000, LAYOUT_VEXPAND = 0x2000, LAYOUT_2 = 0x4000;
    public static final int PLAIN = 0, HYPERLINK = 1, BUTTON = 2;

    private String label;
    private int layout;
    Form owner;
    final List<Command> commands = new ArrayList<>();
    private Command defaultCommand;
    private ItemCommandListener icl;

    Item(String label) { this.label = label; }

    public void setLabel(String l) { label = l; changed(); }
    public String getLabel() { return label; }
    public int getLayout() { return layout; }
    public void setLayout(int l) { layout = l; changed(); }
    public void addCommand(Command c) { if (c == null) throw new NullPointerException(); if (!commands.contains(c)) { commands.add(c); changed(); } }
    public void removeCommand(Command c) { commands.remove(c); if (c == defaultCommand) defaultCommand = null; changed(); }
    public void setDefaultCommand(Command c) { defaultCommand = c; if (c != null) addCommand(c); }
    public void setItemCommandListener(ItemCommandListener l) { icl = l; }
    public void setPreferredSize(int w, int h) {}
    public int getPreferredWidth() { return 100; }
    public int getPreferredHeight() { return 20; }
    public int getMinimumWidth() { return 10; }
    public int getMinimumHeight() { return 10; }
    public void notifyStateChanged() { if (owner != null) owner.$itemStateChanged(this); }

    public final List<Command> $commands() { return commands; }
    public final Command $defaultCommand() { return defaultCommand; }
    public final void $fireCommand(Command c) {
        ItemCommandListener l = icl;
        if (l != null) try { l.commandAction(c, this); } catch (Throwable t) { t.printStackTrace(); }
    }
    final void changed() { if (owner != null) owner.$changed(); }
}
