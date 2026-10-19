// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 J2ME Deck contributors
package javax.microedition.lcdui;
public class TextField extends Item {
    public static final int ANY = 0, EMAILADDR = 1, NUMERIC = 2, PHONENUMBER = 3, URL = 4, DECIMAL = 5,
            PASSWORD = 0x10000, UNEDITABLE = 0x20000, SENSITIVE = 0x40000, NON_PREDICTIVE = 0x80000,
            INITIAL_CAPS_WORD = 0x100000, INITIAL_CAPS_SENTENCE = 0x200000, CONSTRAINT_MASK = 0xFFFF;
    private StringBuilder text; private int max, constraints;
    public TextField(String label, String text, int maxSize, int constraints) {
        super(label);
        if (maxSize <= 0) throw new IllegalArgumentException("maxSize");
        max = maxSize; this.constraints = constraints;
        this.text = new StringBuilder(text == null ? "" : text);
        if (this.text.length() > max) throw new IllegalArgumentException("text longer than maxSize");
    }
    public String getString() { return text.toString(); }
    public void setString(String s) { text = new StringBuilder(s == null ? "" : s.length() > max ? s.substring(0, max) : s); changed(); }
    public int getChars(char[] d) { int n = text.length(); text.getChars(0, n, d, 0); return n; }
    public void setChars(char[] d, int off, int len) { setString(new String(d, off, len)); }
    public void insert(String s, int pos) { text.insert(pos, s); if (text.length() > max) text.setLength(max); changed(); }
    public void insert(char[] d, int off, int len, int pos) { insert(new String(d, off, len), pos); }
    public void delete(int off, int len) { text.delete(off, off + len); changed(); }
    public int getMaxSize() { return max; }
    public int setMaxSize(int m) { max = m; if (text.length() > m) text.setLength(m); return max; }
    public int size() { return text.length(); }
    public int getCaretPosition() { return text.length(); }
    public void setConstraints(int c) { constraints = c; }
    public int getConstraints() { return constraints; }
    public void setInitialInputMode(String characterSubset) {}
    /** Edits coming from the UI: update the model without forcing a rebuild. */
    public final void $uiText(String s) { text = new StringBuilder(s); notifyStateChanged(); }
}
