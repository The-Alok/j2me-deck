// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 J2ME Deck contributors
package javax.microedition.lcdui;
public abstract class Screen extends Displayable {
    /** Marks a change that came from the UI itself (typing etc.): no rebuild needed. */
    boolean fromUi;
}
