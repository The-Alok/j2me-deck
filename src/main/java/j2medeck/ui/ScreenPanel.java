// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 J2ME Deck contributors
package j2medeck.ui;

import javax.microedition.lcdui.Alert;
import javax.microedition.lcdui.Canvas;
import javax.microedition.lcdui.Choice;
import javax.microedition.lcdui.ChoiceGroup;
import javax.microedition.lcdui.Command;
import javax.microedition.lcdui.DateField;
import javax.microedition.lcdui.Displayable;
import javax.microedition.lcdui.Form;
import javax.microedition.lcdui.Gauge;
import javax.microedition.lcdui.Image;
import javax.microedition.lcdui.ImageItem;
import javax.microedition.lcdui.Item;
import javax.microedition.lcdui.List;
import javax.microedition.lcdui.Spacer;
import javax.microedition.lcdui.StringItem;
import javax.microedition.lcdui.TextBox;
import javax.microedition.lcdui.TextField;
import javax.microedition.lcdui.Ticker;
import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Rectangle;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.util.ArrayList;

/**
 * Renders MIDP high-level screens (List, Form, TextBox, Alert) with Swing and handles navigation by
 * MIDP key codes, so a gamepad or the soft keys work without a mouse.
 */
public final class ScreenPanel extends JPanel {
    /** One thing the cursor can stop on. */
    private static final class Stop {
        JComponent comp; Runnable fire; Runnable left, right; JComponent focus;
    }

    private final Displayable screen;
    private final CommandBar bar = new CommandBar();
    private final JPanel content = new JPanel();
    private final JScrollPane scroll;
    private final java.util.List<Stop> stops = new ArrayList<>();
    private int cursor;
    private javax.swing.Timer alertTimer;
    private Runnable onAlertDone;

    private static final Border IDLE = BorderFactory.createEmptyBorder(2, 2, 2, 2);
    private static final Border HOT = BorderFactory.createLineBorder(Theme.ACCENT, 2);

    public ScreenPanel(Displayable d) {
        super(new BorderLayout());
        screen = d;
        setBackground(Theme.BG);
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBackground(Theme.BG);
        content.setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));
        scroll = new JScrollPane(content, ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED, ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setBorder(null);
        scroll.getViewport().setBackground(Theme.BG);
        scroll.getVerticalScrollBar().setUnitIncrement(Theme.base() * 2);
        add(scroll, BorderLayout.CENTER);
        add(bar, BorderLayout.SOUTH);
        rebuild();
    }

    public Displayable displayable() { return screen; }
    public CommandBar commandBar() { return bar; }
    public void onAlertDismissed(Runnable r) { onAlertDone = r; }

    // ------------------------------------------------------------------ building
    public void rebuild() {
        int keep = cursor;
        content.removeAll();
        stops.clear();
        removeHeader();
        add(header(), BorderLayout.NORTH);
        if (screen instanceof List) buildList((List) screen);
        else if (screen instanceof Form) buildForm((Form) screen);
        else if (screen instanceof TextBox) buildTextBox((TextBox) screen);
        else if (screen instanceof Alert) buildAlert((Alert) screen);
        content.add(Box.createVerticalGlue());
        bar.bind(screen, screen instanceof Alert ? Alert.DISMISS_COMMAND : null);
        if (screen instanceof Alert) {
            bar.interceptor = c -> { if (c == Alert.DISMISS_COMMAND) { dismissAlert(); return true; } return false; };
            startAlertTimer();
        }
        cursor = Math.max(0, Math.min(keep, stops.size() - 1));
        if (screen instanceof List && ((List) screen).getListType() == Choice.IMPLICIT && !stops.isEmpty())
            cursor = Math.max(0, Math.min(((List) screen).getSelectedIndex(), stops.size() - 1));
        highlight();
        content.revalidate(); content.repaint();
    }

    private JComponent headerComp;
    private void removeHeader() { if (headerComp != null) remove(headerComp); }
    private JComponent header() {
        JPanel h = new JPanel(new BorderLayout());
        h.setBackground(Theme.PANEL_ALT);
        h.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, Theme.BORDER));
        String t = screen.getTitle();
        JLabel title = new JLabel(t == null || t.isEmpty() ? " " : t);
        title.setFont(Theme.font(Font.BOLD, 1.15f));
        title.setForeground(Theme.ACCENT);
        title.setBorder(BorderFactory.createEmptyBorder(8, 12, 8, 12));
        h.add(title, BorderLayout.NORTH);
        Ticker tk = screen.getTicker();
        if (tk != null) {
            JLabel tl = new JLabel(tk.getString());
            tl.setFont(Theme.font(Font.ITALIC, 0.85f));
            tl.setForeground(Theme.MUTED);
            tl.setBorder(BorderFactory.createEmptyBorder(0, 12, 6, 12));
            h.add(tl, BorderLayout.SOUTH);
        }
        headerComp = h;
        return h;
    }

    private JLabel text(String s, float size, int style, Color c) {
        JLabel l = new JLabel("<html>" + esc(s) + "</html>");
        l.setFont(Theme.font(style, size));
        l.setForeground(c);
        l.setAlignmentX(LEFT_ALIGNMENT);
        return l;
    }
    private static String esc(String s) {
        return s == null ? "" : s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\n", "<br>");
    }
    private static Icon icon(Image img, int max) {
        if (img == null) return null;
        int w = img.getWidth(), h = img.getHeight();
        BufferedImage b = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        int[] px = new int[w * h];
        img.getRGB(px, 0, w, 0, 0, w, h);
        b.setRGB(0, 0, w, h, px, 0, w);
        double s = Math.min(1.0, max / (double) Math.max(w, h));
        java.awt.Image scaled = s < 1.0 ? b.getScaledInstance((int) (w * s), (int) (h * s), java.awt.Image.SCALE_SMOOTH) : b;
        return new ImageIcon(scaled);
    }

    private void addStop(JComponent comp, Runnable fire, JComponent focus) {
        final Stop s = new Stop();
        s.comp = comp; s.fire = fire; s.focus = focus;
        final int index = stops.size();
        stops.add(s);
        comp.addMouseListener(new MouseAdapter() {
            @Override public void mousePressed(MouseEvent e) {
                cursor = index; highlight();
                if (focus == null && fire != null) fire.run();
            }
        });
    }

    // ---- List
    private void buildList(List l) {
        int type = l.getListType();
        for (int i = 0; i < l.size(); i++) {
            final int idx = i;
            JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
            row.setBackground(Theme.PANEL);
            row.setAlignmentX(LEFT_ALIGNMENT);
            row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 4 * Theme.base()));
            if (type != Choice.IMPLICIT) row.add(mark(l.isSelected(i), type == Choice.MULTIPLE));
            Icon ic = icon(l.getImage(i), 2 * Theme.base());
            if (ic != null) row.add(new JLabel(ic));
            row.add(text(l.getString(i), 1.1f, Font.PLAIN, Theme.TEXT));
            row.setBorder(IDLE);
            content.add(row);
            content.add(Box.createVerticalStrut(3));
            addStop(row, () -> l.$activate(idx), null);
        }
        if (l.size() == 0) content.add(text("(empty)", 1.0f, Font.ITALIC, Theme.MUTED));
    }

    private JComponent mark(boolean on, boolean multiple) {
        JLabel m = new JLabel(multiple ? (on ? "\u2611" : "\u2610") : (on ? "\u25C9" : "\u25CB"));
        m.setFont(Theme.font(Font.PLAIN, 1.2f));
        m.setForeground(on ? Theme.ACCENT : Theme.MUTED);
        return m;
    }

    // ---- TextBox
    private void buildTextBox(TextBox tb) {
        JTextArea a = new JTextArea(tb.getString());
        styleText(a);
        a.setLineWrap(true); a.setWrapStyleWord(true);
        a.setEditable((tb.getConstraints() & TextField.UNEDITABLE) == 0);
        a.getDocument().addDocumentListener(simple(() -> { String s = a.getText(); if (s.length() > tb.getMaxSize()) return; tb.$uiText(s); }));
        a.setAlignmentX(LEFT_ALIGNMENT);
        content.add(a);
        addStop(a, null, a);
    }

    private void styleText(javax.swing.text.JTextComponent c) {
        c.setFont(Theme.font(Font.PLAIN, 1.1f));
        c.setForeground(Theme.TEXT);
        c.setBackground(Theme.PANEL);
        c.setCaretColor(Theme.ACCENT);
        c.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(Theme.BORDER), BorderFactory.createEmptyBorder(6, 8, 6, 8)));
    }

    private static DocumentListener simple(Runnable r) {
        return new DocumentListener() {
            public void insertUpdate(DocumentEvent e) { r.run(); }
            public void removeUpdate(DocumentEvent e) { r.run(); }
            public void changedUpdate(DocumentEvent e) { r.run(); }
        };
    }

    // ---- Alert
    private void buildAlert(Alert a) {
        JPanel box = new JPanel();
        box.setLayout(new BoxLayout(box, BoxLayout.Y_AXIS));
        box.setBackground(Theme.BG);
        box.setAlignmentX(LEFT_ALIGNMENT);
        Icon ic = icon(a.getImage(), 6 * Theme.base());
        if (ic != null) { JLabel i = new JLabel(ic); i.setAlignmentX(LEFT_ALIGNMENT); box.add(i); box.add(Box.createVerticalStrut(8)); }
        if (a.getString() != null) box.add(text(a.getString(), 1.2f, Font.PLAIN, Theme.TEXT));
        Gauge g = a.getIndicator();
        if (g != null) { box.add(Box.createVerticalStrut(10)); box.add(progress(g, null)); }
        content.add(Box.createVerticalStrut(Theme.base()));
        content.add(box);
    }
    private void startAlertTimer() {
        if (alertTimer != null) alertTimer.stop();
        int t = ((Alert) screen).getTimeout();
        if (t == Alert.FOREVER || bar.hasCommandsBeyondDismiss()) return;
        alertTimer = new javax.swing.Timer(t, e -> dismissAlert());
        alertTimer.setRepeats(false);
        alertTimer.start();
    }
    private void dismissAlert() {
        if (alertTimer != null) alertTimer.stop();
        screen.$fireCommand(Alert.DISMISS_COMMAND);
        if (onAlertDone != null) { Runnable r = onAlertDone; onAlertDone = null; r.run(); }
    }

    // ---- Form
    private void buildForm(Form f) {
        for (Item item : f.$items()) {
            JPanel p = new JPanel();
            p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
            p.setBackground(Theme.BG);
            p.setAlignmentX(LEFT_ALIGNMENT);
            p.setBorder(BorderFactory.createEmptyBorder(4, 0, 4, 0));
            if (item.getLabel() != null && !item.getLabel().isEmpty() && !(item instanceof StringItem && ((StringItem) item).getAppearanceMode() == Item.BUTTON))
                p.add(text(item.getLabel(), 0.9f, Font.BOLD, Theme.MUTED));
            boolean before = stops.size() == 0;
            int firstStop = stops.size();
            if (item instanceof StringItem) buildStringItem((StringItem) item, p);
            else if (item instanceof TextField) buildTextField((TextField) item, p);
            else if (item instanceof ChoiceGroup) buildChoiceGroup((ChoiceGroup) item, p);
            else if (item instanceof Gauge) buildGauge((Gauge) item, p);
            else if (item instanceof ImageItem) {
                ImageItem ii = (ImageItem) item;
                Icon ic = icon(ii.getImage(), 10 * Theme.base());
                JLabel l = ic != null ? new JLabel(ic) : text(ii.getAltText(), 1.0f, Font.ITALIC, Theme.MUTED);
                l.setAlignmentX(LEFT_ALIGNMENT);
                p.add(l);
            } else if (item instanceof DateField) {
                DateField df = (DateField) item;
                String s = df.getDate() == null ? "(not set)" : df.getDate().toString();
                p.add(text(s, 1.0f, Font.PLAIN, Theme.TEXT));
            } else if (item instanceof Spacer) {
                p.add(Box.createRigidArea(new Dimension(item.getMinimumWidth(), Math.max(4, item.getMinimumHeight()))));
            }
            // an item with commands is focusable even if it is otherwise static
            if (stops.size() == firstStop && !item.$commands().isEmpty()) addStop(p, () -> itemCommands(item), null);
            else if (stops.size() > firstStop && !item.$commands().isEmpty()) {
                Stop st = stops.get(firstStop);
                Runnable old = st.fire;
                if (item.$defaultCommand() != null && old == null) st.fire = () -> item.$fireCommand(item.$defaultCommand());
            }
            content.add(p);
        }
        if (f.size() == 0) content.add(text(" ", 1.0f, Font.PLAIN, Theme.MUTED));
    }

    private void itemCommands(Item item) {
        if (item.$defaultCommand() != null) { item.$fireCommand(item.$defaultCommand()); return; }
        JPopupMenu m = new JPopupMenu();
        for (Command c : item.$commands()) {
            JMenuItem mi = new JMenuItem(c.getLabel());
            mi.setFont(Theme.font(Font.PLAIN, 1.0f));
            mi.addActionListener(e -> item.$fireCommand(c));
            m.add(mi);
        }
        Stop s = stops.isEmpty() ? null : stops.get(Math.min(cursor, stops.size() - 1));
        if (s != null) m.show(s.comp, 10, s.comp.getHeight());
    }

    private void buildStringItem(StringItem si, JPanel p) {
        String t = si.getText() == null ? "" : si.getText();
        if (si.getAppearanceMode() == Item.BUTTON || si.getAppearanceMode() == Item.HYPERLINK) {
            boolean button = si.getAppearanceMode() == Item.BUTTON;
            String label = button && si.getLabel() != null && !si.getLabel().isEmpty() ? si.getLabel() + (t.isEmpty() ? "" : ": " + t) : t;
            JLabel b = text(label, 1.1f, Font.BOLD, button ? Theme.TEXT : Theme.ACCENT);
            if (button) { b.setOpaque(true); b.setBackground(Theme.PANEL_ALT); b.setBorder(BorderFactory.createEmptyBorder(8, 14, 8, 14)); }
            p.add(b);
            Runnable fire = () -> { if (si.$defaultCommand() != null) si.$fireCommand(si.$defaultCommand()); else if (!si.$commands().isEmpty()) itemCommands(si); };
            addStop(p, fire, null);
        } else p.add(text(t, 1.05f, Font.PLAIN, Theme.TEXT));
    }

    private void buildTextField(TextField tf, JPanel p) {
        int c = tf.getConstraints();
        javax.swing.text.JTextComponent field = (c & TextField.PASSWORD) != 0 ? new JPasswordField(tf.getString()) : new JTextField(tf.getString());
        styleText(field);
        field.setEditable((c & TextField.UNEDITABLE) == 0);
        field.setAlignmentX(LEFT_ALIGNMENT);
        field.setMaximumSize(new Dimension(Integer.MAX_VALUE, field.getPreferredSize().height + 4));
        int kind = c & TextField.CONSTRAINT_MASK;
        ((javax.swing.text.AbstractDocument) field.getDocument()).setDocumentFilter(new javax.swing.text.DocumentFilter() {
            private boolean ok(String s, String cur) {
                if (cur.length() + s.length() > tf.getMaxSize() + 0 && !s.isEmpty() && cur.length() >= tf.getMaxSize()) return false;
                for (char ch : s.toCharArray()) {
                    if (kind == TextField.NUMERIC && !(Character.isDigit(ch) || ch == '-')) return false;
                    if (kind == TextField.DECIMAL && !(Character.isDigit(ch) || ch == '-' || ch == '.')) return false;
                    if (kind == TextField.PHONENUMBER && !(Character.isDigit(ch) || "+*#pw".indexOf(ch) >= 0)) return false;
                }
                return true;
            }
            @Override public void insertString(FilterBypass fb, int off, String s, javax.swing.text.AttributeSet a) throws javax.swing.text.BadLocationException {
                if (ok(s, fb.getDocument().getText(0, fb.getDocument().getLength()))) super.insertString(fb, off, s, a);
            }
            @Override public void replace(FilterBypass fb, int off, int len, String s, javax.swing.text.AttributeSet a) throws javax.swing.text.BadLocationException {
                String cur = fb.getDocument().getText(0, fb.getDocument().getLength());
                if (s == null || ok(s, cur.substring(0, off) + cur.substring(off + len))) super.replace(fb, off, len, s, a);
            }
        });
        field.getDocument().addDocumentListener(simple(() -> tf.$uiText(field.getText())));
        p.add(field);
        addStop(p, null, field);
    }

    private void buildChoiceGroup(ChoiceGroup cg, JPanel p) {
        int type = cg.getChoiceType();
        if (type == Choice.POPUP) {
            String[] items = new String[cg.size()];
            for (int i = 0; i < items.length; i++) items[i] = cg.getString(i);
            JComboBox<String> box = new JComboBox<>(items);
            box.setFont(Theme.font(Font.PLAIN, 1.05f));
            box.setSelectedIndex(Math.max(0, cg.getSelectedIndex()));
            box.setAlignmentX(LEFT_ALIGNMENT);
            box.setMaximumSize(new Dimension(Integer.MAX_VALUE, box.getPreferredSize().height));
            box.addActionListener(e -> { if (box.getSelectedIndex() != cg.getSelectedIndex()) cg.$activate(box.getSelectedIndex()); });
            p.add(box);
            Runnable open = () -> box.showPopup();
            addStop(p, open, box);
            stops.get(stops.size() - 1).left = () -> { if (box.getSelectedIndex() > 0) box.setSelectedIndex(box.getSelectedIndex() - 1); };
            stops.get(stops.size() - 1).right = () -> { if (box.getSelectedIndex() < box.getItemCount() - 1) box.setSelectedIndex(box.getItemCount() > 0 ? box.getSelectedIndex() + 1 : 0); };
            return;
        }
        for (int i = 0; i < cg.size(); i++) {
            final int idx = i;
            JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 3));
            row.setBackground(Theme.PANEL);
            row.setAlignmentX(LEFT_ALIGNMENT);
            row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 4 * Theme.base()));
            row.add(mark(cg.isSelected(i), type == Choice.MULTIPLE));
            Icon ic = icon(cg.getImage(i), 2 * Theme.base());
            if (ic != null) row.add(new JLabel(ic));
            row.add(text(cg.getString(i), 1.05f, Font.PLAIN, Theme.TEXT));
            row.setBorder(IDLE);
            p.add(row);
            addStop(row, () -> cg.$activate(idx), null);
        }
    }

    private JComponent progress(Gauge g, Runnable onChange) {
        if (g.isInteractive() && g.getMaxValue() > 0) {
            JSlider s = new JSlider(0, g.getMaxValue(), g.getValue());
            s.setBackground(Theme.BG);
            s.setAlignmentX(LEFT_ALIGNMENT);
            s.addChangeListener(e -> { if (s.getValue() != g.getValue()) g.$uiSet(s.getValue()); });
            return s;
        }
        JProgressBar b = new JProgressBar(0, Math.max(1, g.getMaxValue() > 0 ? g.getMaxValue() : 100));
        b.setIndeterminate(g.getMaxValue() == Gauge.INDEFINITE);
        if (g.getMaxValue() > 0) b.setValue(g.getValue());
        b.setAlignmentX(LEFT_ALIGNMENT);
        return b;
    }

    private void buildGauge(Gauge g, JPanel p) {
        JComponent c = progress(g, null);
        p.add(c);
        if (c instanceof JSlider) {
            JSlider s = (JSlider) c;
            addStop(p, null, null);
            Stop st = stops.get(stops.size() - 1);
            st.left = () -> s.setValue(s.getValue() - Math.max(1, s.getMaximum() / 10));
            st.right = () -> s.setValue(s.getValue() + Math.max(1, s.getMaximum() / 10));
        }
    }

    // ------------------------------------------------------------------ navigation
    private void highlight() {
        for (int i = 0; i < stops.size(); i++) {
            Stop s = stops.get(i);
            boolean on = i == cursor;
            s.comp.setBorder(on ? HOT : (s.comp instanceof javax.swing.text.JTextComponent ? s.comp.getBorder() : IDLE));
            Color bg = s.comp.getBackground();
            if (s.comp instanceof JPanel && (bg == Theme.PANEL || bg == Theme.FOCUS)) s.comp.setBackground(on ? Theme.FOCUS : Theme.PANEL);
        }
        if (cursor >= 0 && cursor < stops.size()) {
            Stop s = stops.get(cursor);
            if (s.focus != null) s.focus.requestFocusInWindow();
            SwingUtilities.invokeLater(() -> s.comp.scrollRectToVisible(new Rectangle(0, -8, s.comp.getWidth(), s.comp.getHeight() + 16)));
            if (screen instanceof List) ((List) screen).$focus(cursor);
        }
        repaint();
    }

    private void move(int delta) {
        if (stops.isEmpty()) {
            JScrollBar sb = scroll.getVerticalScrollBar();
            sb.setValue(sb.getValue() + delta * sb.getUnitIncrement() * 2);
            return;
        }
        cursor = Math.max(0, Math.min(stops.size() - 1, cursor + delta));
        highlight();
    }

    /** MIDP key event routed here while this screen is showing (kind: 0 press, 1 release, 2 repeat). */
    public void onKey(int kind, int code) {
        if (kind == 1) return;
        if (screen instanceof Alert && (code == Canvas.KEY_FIRE || code == Canvas.KEY_NUM5)) { bar.softKey(true); return; }
        switch (code) {
            case Canvas.KEY_UP: case Canvas.KEY_NUM2: move(-1); break;
            case Canvas.KEY_DOWN: case Canvas.KEY_NUM8: move(1); break;
            case Canvas.KEY_LEFT: case Canvas.KEY_NUM4: { Stop s = current(); if (s != null && s.left != null) s.left.run(); break; }
            case Canvas.KEY_RIGHT: case Canvas.KEY_NUM6: { Stop s = current(); if (s != null && s.right != null) s.right.run(); break; }
            case Canvas.KEY_FIRE: case Canvas.KEY_NUM5: { Stop s = current(); if (s != null && s.fire != null) s.fire.run(); break; }
            case Canvas.KEY_SOFT_LEFT: bar.softKey(true); break;
            case Canvas.KEY_SOFT_RIGHT: case Canvas.KEY_CLEAR: bar.softKey(false); break;
            default:
        }
    }
    private Stop current() { return cursor >= 0 && cursor < stops.size() ? stops.get(cursor) : null; }

    public void dispose() { if (alertTimer != null) alertTimer.stop(); }
}
