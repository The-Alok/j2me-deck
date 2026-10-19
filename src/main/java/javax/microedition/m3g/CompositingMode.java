// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 J2ME Deck contributors
package javax.microedition.m3g;

public class CompositingMode extends Object3D {
    public static final int ALPHA = 64, ALPHA_ADD = 65, MODULATE = 66, MODULATE_X2 = 67, REPLACE = 68;
    private boolean depthTest = true, depthWrite = true, colorWrite = true, alphaWrite = true;
    private int blending = REPLACE;
    private float threshold, offFactor, offUnits;

    public CompositingMode() {}

    public void setBlending(int b) { if (b < ALPHA || b > REPLACE) throw new IllegalArgumentException("invalid blending mode"); blending = b; }
    public int getBlending() { return blending; }
    public void setAlphaThreshold(float t) { if (t < 0 || t > 1) throw new IllegalArgumentException("threshold must be in [0, 1]"); threshold = t; }
    public float getAlphaThreshold() { return threshold; }
    public void setDepthTestEnable(boolean b) { depthTest = b; }
    public boolean isDepthTestEnabled() { return depthTest; }
    public void setDepthWriteEnable(boolean b) { depthWrite = b; }
    public boolean isDepthWriteEnabled() { return depthWrite; }
    public void setColorWriteEnable(boolean b) { colorWrite = b; }
    public boolean isColorWriteEnabled() { return colorWrite; }
    public void setAlphaWriteEnable(boolean b) { alphaWrite = b; }
    public boolean isAlphaWriteEnabled() { return alphaWrite; }
    public void setDepthOffset(float factor, float units) { offFactor = factor; offUnits = units; }
    public float getDepthOffsetFactor() { return offFactor; }
    public float getDepthOffsetUnits() { return offUnits; }
}
