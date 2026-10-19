// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 J2ME Deck contributors
package javax.microedition.lcdui;
public class StringItem extends Item {
    private String text; private int mode; private Font font;
    public StringItem(String label, String text) { this(label, text, PLAIN); }
    public StringItem(String label, String text, int appearanceMode) { super(label); this.text = text; mode = appearanceMode; }
    public String getText() { return text; }
    public void setText(String t) { text = t; changed(); }
    public int getAppearanceMode() { return mode; }
    public void setFont(Font f) { font = f; changed(); }
    public Font getFont() { return font; }
}
