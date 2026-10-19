// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 J2ME Deck contributors
package javax.microedition.m3g;

public class Material extends Object3D {
    public static final int AMBIENT = 1024, DIFFUSE = 2048, EMISSIVE = 4096, SPECULAR = 8192;

    private int ambient = 0x00333333, diffuse = 0xFFCCCCCC, emissive = 0, specular = 0;
    private float shininess;
    private boolean tracking;

    public Material() {}

    public void setColor(int target, int argb) {
        if (target == 0 || (target & ~(AMBIENT | DIFFUSE | EMISSIVE | SPECULAR)) != 0) throw new IllegalArgumentException("invalid colour target");
        if ((target & AMBIENT) != 0) ambient = argb & 0xFFFFFF;
        if ((target & DIFFUSE) != 0) diffuse = argb;
        if ((target & EMISSIVE) != 0) emissive = argb & 0xFFFFFF;
        if ((target & SPECULAR) != 0) specular = argb & 0xFFFFFF;
    }
    public int getColor(int target) {
        switch (target) {
            case AMBIENT: return ambient;
            case DIFFUSE: return diffuse;
            case EMISSIVE: return emissive;
            case SPECULAR: return specular;
            default: throw new IllegalArgumentException("target must be a single colour");
        }
    }
    public void setShininess(float s) { if (s < 0 || s > 128) throw new IllegalArgumentException("shininess must be in [0, 128]"); shininess = s; }
    public float getShininess() { return shininess; }
    public void setVertexColorTrackingEnable(boolean b) { tracking = b; }
    public boolean isVertexColorTrackingEnabled() { return tracking; }

    @Override boolean isCompatibleProperty(int p) {
        return p == AnimationTrack.AMBIENT_COLOR || p == AnimationTrack.DIFFUSE_COLOR || p == AnimationTrack.EMISSIVE_COLOR || p == AnimationTrack.SPECULAR_COLOR || p == AnimationTrack.SHININESS;
    }
    @Override void updateProperty(int p, float[] v) {
        if (v.length < 1) return;
        switch (p) {
            case AnimationTrack.AMBIENT_COLOR: if (v.length >= 3) ambient = Light.pack(v[0], v[1], v[2]); break;
            case AnimationTrack.DIFFUSE_COLOR: if (v.length >= 3) diffuse = (v.length >= 4 ? Light.clamp(v[3]) << 24 : diffuse & 0xFF000000) | Light.pack(v[0], v[1], v[2]); break;
            case AnimationTrack.EMISSIVE_COLOR: if (v.length >= 3) emissive = Light.pack(v[0], v[1], v[2]); break;
            case AnimationTrack.SPECULAR_COLOR: if (v.length >= 3) specular = Light.pack(v[0], v[1], v[2]); break;
            case AnimationTrack.SHININESS: shininess = Math.max(0, Math.min(128, v[0])); break;
            default: super.updateProperty(p, v);
        }
    }
}
