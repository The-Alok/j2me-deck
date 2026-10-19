// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 J2ME Deck contributors
package javax.microedition.lcdui;
public class ChoiceGroup extends Item implements Choice {
    final ChoiceModel m;
    public ChoiceGroup(String label, int choiceType) { this(label, choiceType, new String[0], null); }
    public ChoiceGroup(String label, int choiceType, String[] strings, Image[] images) {
        super(label);
        if (choiceType == IMPLICIT) throw new IllegalArgumentException("IMPLICIT is not valid for ChoiceGroup");
        m = new ChoiceModel(choiceType);
        if (strings == null) throw new NullPointerException();
        for (int i = 0; i < strings.length; i++) m.append(strings[i], images != null && i < images.length ? images[i] : null);
    }
    public int getChoiceType() { return m.type; }
    public int size() { return m.size(); }
    public String getString(int i) { return m.getString(i); }
    public Image getImage(int i) { return m.getImage(i); }
    public int append(String s, Image img) { int r = m.append(s, img); changed(); return r; }
    public void insert(int i, String s, Image img) { m.insert(i, s, img); changed(); }
    public void delete(int i) { m.delete(i); changed(); }
    public void deleteAll() { m.deleteAll(); changed(); }
    public void set(int i, String s, Image img) { m.set(i, s, img); changed(); }
    public boolean isSelected(int i) { return m.isSelected(i); }
    public int getSelectedIndex() { return m.getSelectedIndex(); }
    public int getSelectedFlags(boolean[] f) { return m.getSelectedFlags(f); }
    public void setSelectedIndex(int i, boolean s) { m.setSelectedIndex(i, s); changed(); }
    public void setSelectedFlags(boolean[] f) { m.setSelectedFlags(f); changed(); }
    public void setFitPolicy(int p) { m.fit = p; }
    public int getFitPolicy() { return m.fit; }
    public void setFont(int i, Font f) { m.fonts.set(i, f); }
    public Font getFont(int i) { return m.fonts.get(i); }
    public final void $activate(int i) { m.activate(i); notifyStateChanged(); }
}
