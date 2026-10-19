// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 J2ME Deck contributors
package javax.microedition.m3g;

import java.util.Map;

public class Light extends Node {
    public static final int AMBIENT = 128, DIRECTIONAL = 129, OMNI = 130, SPOT = 131;

    private int mode = DIRECTIONAL, color = 0xFFFFFF;
    private float intensity = 1, spotAngle = 45, spotExponent = 0, attC = 1, attL = 0, attQ = 0;

    public Light() {}

    public void setMode(int m) { if (m < AMBIENT || m > SPOT) throw new IllegalArgumentException("invalid light mode"); mode = m; }
    public int getMode() { return mode; }
    public void setColor(int rgb) { color = rgb & 0xFFFFFF; }
    public int getColor() { return color; }
    public void setIntensity(float i) { intensity = i; }
    public float getIntensity() { return intensity; }
    public void setSpotAngle(float a) { if (a < 0 || a > 90) throw new IllegalArgumentException("spot angle must be in [0, 90]"); spotAngle = a; }
    public float getSpotAngle() { return spotAngle; }
    public void setSpotExponent(float e) { if (e < 0 || e > 128) throw new IllegalArgumentException("spot exponent must be in [0, 128]"); spotExponent = e; }
    public float getSpotExponent() { return spotExponent; }
    public void setAttenuation(float c, float l, float q) {
        if (c < 0 || l < 0 || q < 0 || (c == 0 && l == 0 && q == 0)) throw new IllegalArgumentException("invalid attenuation");
        attC = c; attL = l; attQ = q;
    }
    public float getConstantAttenuation() { return attC; }
    public float getLinearAttenuation() { return attL; }
    public float getQuadraticAttenuation() { return attQ; }

    @Override boolean isCompatibleProperty(int p) {
        return p == AnimationTrack.COLOR || p == AnimationTrack.INTENSITY || p == AnimationTrack.SPOT_ANGLE || p == AnimationTrack.SPOT_EXPONENT || super.isCompatibleProperty(p);
    }
    @Override void updateProperty(int property, float[] v) {
        if (v.length < 1) return;
        switch (property) {
            case AnimationTrack.COLOR: if (v.length >= 3) color = pack(v[0], v[1], v[2]); break;
            case AnimationTrack.INTENSITY: intensity = v[0]; break;
            case AnimationTrack.SPOT_ANGLE: spotAngle = Math.max(0, Math.min(90, v[0])); break;
            case AnimationTrack.SPOT_EXPONENT: spotExponent = Math.max(0, Math.min(128, v[0])); break;
            default: super.updateProperty(property, v);
        }
    }
    static int pack(float r, float g, float b) {
        return (clamp(r) << 16) | (clamp(g) << 8) | clamp(b);
    }
    static int clamp(float f) { return Math.max(0, Math.min(255, Math.round(f * 255))); }

    @Override Object3D newInstance() { return new Light(); }
    @Override void copyInto(Object3D target, Map<Object3D, Object3D> map) {
        super.copyInto(target, map);
        Light l = (Light) target;
        l.mode = mode; l.color = color; l.intensity = intensity; l.spotAngle = spotAngle; l.spotExponent = spotExponent; l.attC = attC; l.attL = attL; l.attQ = attQ;
    }
}
