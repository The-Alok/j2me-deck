// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 J2ME Deck contributors
package javax.microedition.lcdui;
public class Ticker {
    private String text;
    public Ticker(String str) { if (str == null) throw new NullPointerException(); text = str; }
    public String getString() { return text; }
    public void setString(String s) { if (s == null) throw new NullPointerException(); text = s; }
}
