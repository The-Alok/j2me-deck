// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 J2ME Deck contributors
package javax.microedition.lcdui;

import java.util.ArrayList;
import java.util.List;

/** Shared storage and MIDP selection rules for List and ChoiceGroup. */
final class ChoiceModel {
    final int type;
    final List<String> text = new ArrayList<>();
    final List<Image> images = new ArrayList<>();
    final List<Boolean> selected = new ArrayList<>();
    final List<Font> fonts = new ArrayList<>();
    int fit = Choice.TEXT_WRAP_DEFAULT;
    int focus;   // IMPLICIT lists: highlighted row

    ChoiceModel(int type) {
        if (type < Choice.EXCLUSIVE || type > Choice.POPUP) throw new IllegalArgumentException("choiceType");
        this.type = type;
    }
    private void check(int i) { if (i < 0 || i >= text.size()) throw new IndexOutOfBoundsException("element " + i); }
    int size() { return text.size(); }
    String getString(int i) { check(i); return text.get(i); }
    Image getImage(int i) { check(i); return images.get(i); }
    int append(String s, Image img) { insert(text.size(), s, img); return text.size() - 1; }
    void insert(int i, String s, Image img) {
        if (s == null) throw new NullPointerException("stringPart");
        if (i < 0 || i > text.size()) throw new IndexOutOfBoundsException();
        boolean first = text.isEmpty();
        text.add(i, s); images.add(i, img); fonts.add(i, null);
        selected.add(i, first && type != Choice.MULTIPLE && type != Choice.IMPLICIT);   // exclusive/popup: first row selected
        if (type == Choice.IMPLICIT && first) focus = 0;
    }
    void delete(int i) {
        check(i);
        boolean was = selected.get(i);
        text.remove(i); images.remove(i); fonts.remove(i); selected.remove(i);
        if (!text.isEmpty() && was && (type == Choice.EXCLUSIVE || type == Choice.POPUP)) selected.set(Math.min(i, text.size() - 1), true);
        if (focus >= text.size()) focus = Math.max(0, text.size() - 1);
    }
    void deleteAll() { text.clear(); images.clear(); fonts.clear(); selected.clear(); focus = 0; }
    void set(int i, String s, Image img) { check(i); if (s == null) throw new NullPointerException(); text.set(i, s); images.set(i, img); }
    boolean isSelected(int i) { check(i); return type == Choice.IMPLICIT ? i == focus : selected.get(i); }
    int getSelectedIndex() {
        if (type == Choice.MULTIPLE || text.isEmpty()) return -1;
        if (type == Choice.IMPLICIT) return focus;
        for (int i = 0; i < selected.size(); i++) if (selected.get(i)) return i;
        return -1;
    }
    int getSelectedFlags(boolean[] flags) {
        if (flags.length < text.size()) throw new IllegalArgumentException("array too short");
        int n = 0;
        for (int i = 0; i < flags.length; i++) { flags[i] = i < text.size() && isSelected(i); if (flags[i]) n++; }
        return n;
    }
    void setSelectedIndex(int i, boolean sel) {
        check(i);
        if (type == Choice.IMPLICIT) { if (sel) focus = i; return; }
        if (type == Choice.MULTIPLE) { selected.set(i, sel); return; }
        if (sel) for (int k = 0; k < selected.size(); k++) selected.set(k, k == i);
    }
    void setSelectedFlags(boolean[] f) {
        if (f.length < text.size()) throw new IllegalArgumentException();
        if (type == Choice.MULTIPLE) { for (int i = 0; i < text.size(); i++) selected.set(i, f[i]); return; }
        for (int i = 0; i < text.size(); i++) if (f[i]) { setSelectedIndex(i, true); return; }
    }
    /** UI activation of row i (FIRE / click): toggles or selects according to the type. */
    void activate(int i) {
        if (type == Choice.MULTIPLE) selected.set(i, !selected.get(i));
        else setSelectedIndex(i, true);
        focus = i;
    }
}
