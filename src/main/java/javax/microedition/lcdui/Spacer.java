// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 J2ME Deck contributors
package javax.microedition.lcdui;
public class Spacer extends Item {
    private int w, h;
    public Spacer(int minWidth, int minHeight) { super(null); w = minWidth; h = minHeight; }
    public void setMinimumSize(int minWidth, int minHeight) { w = minWidth; h = minHeight; changed(); }
    @Override public int getMinimumWidth() { return w; }
    @Override public int getMinimumHeight() { return h; }
    @Override public void addCommand(Command c) { throw new IllegalStateException("Spacer cannot have commands"); }
    @Override public void setLabel(String l) { throw new IllegalStateException("Spacer has no label"); }
}
