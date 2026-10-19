// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 J2ME Deck contributors
package javax.microedition.lcdui;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public abstract class Displayable {
    private static int virtualWidth = 240, virtualHeight = 320;
    private final List<Command> commands = new ArrayList<>();
    private CommandListener listener;
    private String title;
    private Ticker ticker;
    volatile boolean shown;

    public static void setVirtualSize(int w, int h) { virtualWidth = w; virtualHeight = h; }
    public static int getVirtualWidth() { return virtualWidth; }
    public static int getVirtualHeight() { return virtualHeight; }

    public int getWidth() { return virtualWidth; }
    public int getHeight() { return virtualHeight; }
    public boolean isShown() { return shown; }

    public void addCommand(Command c) {
        if (c == null) throw new NullPointerException();
        synchronized (commands) { if (commands.contains(c)) return; commands.add(c); commands.sort(Comparator.comparingInt(Command::getPriority)); }
        $changed();
    }
    public void removeCommand(Command c) { synchronized (commands) { commands.remove(c); } $changed(); }
    public void setCommandListener(CommandListener l) { listener = l; }
    public CommandListener getCommandListener() { return listener; }
    public Command[] getCommands() { synchronized (commands) { return commands.toArray(new Command[0]); } }
    public void setTitle(String t) { title = t; $changed(); }
    public String getTitle() { return title; }
    public void setTicker(Ticker t) { ticker = t; $changed(); }
    public Ticker getTicker() { return ticker; }

    /** Invoke the application's CommandListener (used by the on-screen command bar / soft keys). */
    public final void $fireCommand(Command c) {
        CommandListener l = listener;
        if (l == null) return;
        try { l.commandAction(c, this); } catch (Throwable t) { t.printStackTrace(); }
    }

    protected void sizeChanged(int w, int h) {}
    protected void showNotify() {}
    protected void hideNotify() {}

    /** Model changed: let the window rebuild/redraw this screen (no-op unless it is showing). */
    void $changed() { if (shown) Display.$refresh(this); }

    void $becameCurrent() { shown = true; }
    void $noLongerCurrent() { shown = false; }
}
