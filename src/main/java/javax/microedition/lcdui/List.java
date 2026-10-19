// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 J2ME Deck contributors
package javax.microedition.lcdui;

public class List extends Screen implements Choice {
    public static final Command SELECT_COMMAND = new Command("", Command.SCREEN, 0);
    final ChoiceModel m;
    private Command selectCommand = SELECT_COMMAND;

    public List(String title, int listType) { this(title, listType, new String[0], null); }
    public List(String title, int listType, String[] strings, Image[] images) {
        m = new ChoiceModel(listType == POPUP ? EXCLUSIVE : listType);
        if (listType == POPUP) throw new IllegalArgumentException("POPUP is not valid for List");
        setTitle(title);
        if (strings == null) throw new NullPointerException();
        for (int i = 0; i < strings.length; i++) m.append(strings[i], images != null && i < images.length ? images[i] : null);
    }
    public int getListType() { return m.type; }
    public void setSelectCommand(Command c) { selectCommand = c; $changed(); }
    public Command getSelectCommand() { return selectCommand; }
    public int size() { return m.size(); }
    public String getString(int i) { return m.getString(i); }
    public Image getImage(int i) { return m.getImage(i); }
    public int append(String s, Image img) { int r = m.append(s, img); $changed(); return r; }
    public void insert(int i, String s, Image img) { m.insert(i, s, img); $changed(); }
    public void delete(int i) { m.delete(i); $changed(); }
    public void deleteAll() { m.deleteAll(); $changed(); }
    public void set(int i, String s, Image img) { m.set(i, s, img); $changed(); }
    public boolean isSelected(int i) { return m.isSelected(i); }
    public int getSelectedIndex() { return m.getSelectedIndex(); }
    public int getSelectedFlags(boolean[] f) { return m.getSelectedFlags(f); }
    public void setSelectedIndex(int i, boolean s) { m.setSelectedIndex(i, s); $changed(); }
    public void setSelectedFlags(boolean[] f) { m.setSelectedFlags(f); $changed(); }
    public void setFitPolicy(int p) { m.fit = p; }
    public int getFitPolicy() { return m.fit; }
    public void setFont(int i, Font f) { m.fonts.set(i, f); }
    public Font getFont(int i) { return m.fonts.get(i); }

    /** Called by the renderer when the user activates a row. */
    public final void $activate(int index) {
        if (index < 0 || index >= m.size()) return;
        m.activate(index);
        if (m.type == IMPLICIT) { if (selectCommand != null) $fireCommand(selectCommand); }
        else $changed();
    }
    public final void $focus(int index) { if (m.type == IMPLICIT && index >= 0 && index < m.size()) m.focus = index; }
}
