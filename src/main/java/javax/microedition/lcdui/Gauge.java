// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 J2ME Deck contributors
package javax.microedition.lcdui;
public class Gauge extends Item {
    public static final int INDEFINITE = -1, CONTINUOUS_IDLE = 0, INCREMENTAL_IDLE = 1, CONTINUOUS_RUNNING = 2, INCREMENTAL_UPDATING = 3;
    private final boolean interactive; private int max, value;
    public Gauge(String label, boolean interactive, int maxValue, int initialValue) {
        super(label); this.interactive = interactive;
        if (maxValue <= 0 && !(maxValue == INDEFINITE && !interactive)) throw new IllegalArgumentException("maxValue");
        max = maxValue; value = initialValue; clamp();
    }
    private void clamp() { if (max > 0) value = Math.max(0, Math.min(max, value)); }
    public void setValue(int v) { value = v; clamp(); changed(); }
    public int getValue() { return value; }
    public void setMaxValue(int m) { max = m; clamp(); changed(); }
    public int getMaxValue() { return max; }
    public boolean isInteractive() { return interactive; }
    public final void $uiSet(int v) { value = v; clamp(); notifyStateChanged(); }
}
