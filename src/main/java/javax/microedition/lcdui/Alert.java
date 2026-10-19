// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 J2ME Deck contributors
package javax.microedition.lcdui;

public class Alert extends Screen {
    public static final int FOREVER = -2;
    public static final Command DISMISS_COMMAND = new Command("OK", Command.OK, 0);
    private String text; private Image image; private AlertType type; private int timeout = 3000; private Gauge indicator;

    public Alert(String title) { this(title, null, null, null); }
    public Alert(String title, String alertText, Image alertImage, AlertType alertType) {
        setTitle(title); text = alertText; image = alertImage; type = alertType;
    }
    public int getDefaultTimeout() { return 3000; }
    public int getTimeout() { return timeout; }
    public void setTimeout(int t) { if (t != FOREVER && t <= 0) throw new IllegalArgumentException("timeout"); timeout = t; }
    public AlertType getType() { return type; }
    public void setType(AlertType t) { type = t; $changed(); }
    public String getString() { return text; }
    public void setString(String s) { text = s; $changed(); }
    public Image getImage() { return image; }
    public void setImage(Image i) { image = i; $changed(); }
    public void setIndicator(Gauge g) { indicator = g; $changed(); }
    public Gauge getIndicator() { return indicator; }
}
