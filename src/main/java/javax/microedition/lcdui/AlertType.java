// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 J2ME Deck contributors
package javax.microedition.lcdui;
public class AlertType {
    public static final AlertType INFO = new AlertType(), WARNING = new AlertType(), ERROR = new AlertType(),
            ALARM = new AlertType(), CONFIRMATION = new AlertType();
    protected AlertType() {}
    public boolean playSound(Display d) { return false; }
}
