// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 J2ME Deck contributors
package javax.microedition.lcdui;

public class Command {
    public static final int SCREEN = 1, BACK = 2, CANCEL = 3, OK = 4, HELP = 5, STOP = 6, EXIT = 7, ITEM = 8;
    private final String shortLabel, longLabel;
    private final int type, priority;

    public Command(String label, int commandType, int priority) { this(label, null, commandType, priority); }
    public Command(String shortLabel, String longLabel, int commandType, int priority) {
        if (shortLabel == null) throw new NullPointerException("label");
        if (commandType < SCREEN || commandType > ITEM) throw new IllegalArgumentException("commandType");
        this.shortLabel = shortLabel; this.longLabel = longLabel; this.type = commandType; this.priority = priority;
    }
    public String getLabel() { return shortLabel; }
    public String getLongLabel() { return longLabel; }
    public int getCommandType() { return type; }
    public int getPriority() { return priority; }
    @Override public String toString() { return shortLabel; }
}
