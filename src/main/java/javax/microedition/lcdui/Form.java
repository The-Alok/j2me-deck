// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 J2ME Deck contributors
package javax.microedition.lcdui;

import java.util.ArrayList;
import java.util.List;

public class Form extends Screen {
    private final List<Item> items = new ArrayList<>();
    private ItemStateListener isl;

    public Form(String title) { setTitle(title); }
    public Form(String title, Item[] items) { setTitle(title); if (items != null) for (Item i : items) append(i); }

    private void adopt(Item i) {
        if (i == null) throw new NullPointerException("item");
        if (i.owner != null) throw new IllegalStateException("Item already belongs to a Form");
        i.owner = this;
    }
    public int append(Item i) { adopt(i); items.add(i); $changed(); return items.size() - 1; }
    public int append(String s) { return append(new StringItem(null, s)); }
    public int append(Image img) { return append(new ImageItem(null, img, Item.LAYOUT_DEFAULT, null)); }
    public void insert(int pos, Item i) { adopt(i); items.add(pos, i); $changed(); }
    public void set(int pos, Item i) { adopt(i); Item old = items.set(pos, i); old.owner = null; $changed(); }
    public void delete(int pos) { Item old = items.remove(pos); old.owner = null; $changed(); }
    public void deleteAll() { for (Item i : items) i.owner = null; items.clear(); $changed(); }
    public Item get(int pos) { return items.get(pos); }
    public int size() { return items.size(); }
    public void setItemStateListener(ItemStateListener l) { isl = l; }

    final void $itemStateChanged(Item i) {
        ItemStateListener l = isl;
        if (l != null) try { l.itemStateChanged(i); } catch (Throwable t) { t.printStackTrace(); }
    }
    public final List<Item> $items() { return items; }
}
