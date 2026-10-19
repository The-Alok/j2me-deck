// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 J2ME Deck contributors
package javax.microedition.lcdui;
public class TextBox extends Screen {
    private StringBuilder text; private int max, constraints;
    public TextBox(String title, String text, int maxSize, int constraints) {
        if (maxSize <= 0) throw new IllegalArgumentException("maxSize");
        setTitle(title); max = maxSize; this.constraints = constraints;
        this.text = new StringBuilder(text == null ? "" : text);
        if (this.text.length() > max) throw new IllegalArgumentException("text longer than maxSize");
    }
    public String getString() { return text.toString(); }
    public void setString(String s) { text = new StringBuilder(s == null ? "" : s.length() > max ? s.substring(0, max) : s); $changed(); }
    public int getChars(char[] d) { int n = text.length(); text.getChars(0, n, d, 0); return n; }
    public void setChars(char[] d, int off, int len) { setString(new String(d, off, len)); }
    public void insert(String s, int pos) { text.insert(pos, s); if (text.length() > max) text.setLength(max); $changed(); }
    public void insert(char[] d, int off, int len, int pos) { insert(new String(d, off, len), pos); }
    public void delete(int off, int len) { text.delete(off, off + len); $changed(); }
    public int getMaxSize() { return max; }
    public int setMaxSize(int m) { max = m; if (text.length() > m) text.setLength(m); return max; }
    public int size() { return text.length(); }
    public int getCaretPosition() { return text.length(); }
    public void setConstraints(int c) { constraints = c; }
    public int getConstraints() { return constraints; }
    public void setInitialInputMode(String s) {}
    public final void $uiText(String s) { text = new StringBuilder(s); }
}
