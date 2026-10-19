// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 J2ME Deck contributors
package javax.microedition.m3g;

public class Fog extends Object3D {
    public static final int EXPONENTIAL = 80, LINEAR = 81;
    private int color, mode = LINEAR;
    private float density = 1, near, far = 1;

    public Fog() {}

    public void setMode(int m) { if (m != EXPONENTIAL && m != LINEAR) throw new IllegalArgumentException("invalid fog mode"); mode = m; }
    public int getMode() { return mode; }
    public void setLinear(float near, float far) { this.near = near; this.far = far; }
    public float getNearDistance() { return near; }
    public float getFarDistance() { return far; }
    public void setDensity(float d) { if (d < 0) throw new IllegalArgumentException("density must be >= 0"); density = d; }
    public float getDensity() { return density; }
    public void setColor(int rgb) { color = rgb & 0xFFFFFF; }
    public int getColor() { return color; }

    @Override boolean isCompatibleProperty(int p) { return p == AnimationTrack.COLOR || p == AnimationTrack.DENSITY || p == AnimationTrack.NEAR_DISTANCE || p == AnimationTrack.FAR_DISTANCE; }
    @Override void updateProperty(int p, float[] v) {
        if (v.length < 1) return;
        switch (p) {
            case AnimationTrack.COLOR: if (v.length >= 3) color = Light.pack(v[0], v[1], v[2]); break;
            case AnimationTrack.DENSITY: density = Math.max(0, v[0]); break;
            case AnimationTrack.NEAR_DISTANCE: near = v[0]; break;
            case AnimationTrack.FAR_DISTANCE: far = v[0]; break;
            default: super.updateProperty(p, v);
        }
    }
}
