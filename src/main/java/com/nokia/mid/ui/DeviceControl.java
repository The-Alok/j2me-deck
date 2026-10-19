// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 J2ME Deck contributors
package com.nokia.mid.ui;
/** Backlight and vibration: accepted and ignored on a desktop. */
public class DeviceControl {
    private DeviceControl() {}
    public static void setLights(int num, int level) {}
    public static void flashLights(long duration) {}
    public static void startVibra(int freq, long duration) {}
    public static void stopVibra() {}
}
