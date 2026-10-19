// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 J2ME Deck contributors
package javax.microedition.lcdui;
import java.util.Date;
public class DateField extends Item {
    public static final int DATE = 1, TIME = 2, DATE_TIME = 3;
    private Date date; private int mode;
    public DateField(String label, int mode) { super(label); if (mode < 1 || mode > 3) throw new IllegalArgumentException(); this.mode = mode; }
    public Date getDate() { return date; }
    public void setDate(Date d) { date = d; changed(); }
    public int getInputMode() { return mode; }
    public void setInputMode(int m) { mode = m; changed(); }
}
