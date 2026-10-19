// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 J2ME Deck contributors
package javax.microedition.m3g;

import java.util.Map;

public class Camera extends Node {
    public static final int GENERIC = 48, PARALLEL = 49, PERSPECTIVE = 50;

    private int type = GENERIC;
    private float fovy, height, aspect = 1, near = 0.1f, far = 100;
    private final Transform generic = new Transform();

    public Camera() {}

    public void setParallel(float height, float aspect, float near, float far) {
        if (height <= 0 || aspect <= 0) throw new IllegalArgumentException("height and aspect ratio must be positive");
        if (near == far) throw new IllegalArgumentException("near and far must differ");
        type = PARALLEL; this.height = height; this.aspect = aspect; this.near = near; this.far = far;
    }
    public void setPerspective(float fovy, float aspect, float near, float far) {
        if (fovy <= 0 || fovy >= 180 || aspect <= 0) throw new IllegalArgumentException("invalid field of view or aspect ratio");
        if (near <= 0 || far <= 0 || near >= far) throw new IllegalArgumentException("require 0 < near < far");
        type = PERSPECTIVE; this.fovy = fovy; this.aspect = aspect; this.near = near; this.far = far;
    }
    public void setGeneric(Transform t) {
        if (t == null) throw new NullPointerException();
        type = GENERIC; generic.set(t);
    }

    public int getProjection(Transform t) {
        if (t == null) throw new NullPointerException();
        t.set(projection());
        return type;
    }
    public int getProjection(float[] p) {
        if (p == null) throw new NullPointerException();
        if (p.length < 4) throw new IllegalArgumentException("array too short");
        if (type == GENERIC) return GENERIC;
        if (type == PARALLEL) { p[0] = height; p[1] = aspect; p[2] = near; p[3] = far; }
        else { p[0] = fovy; p[1] = aspect; p[2] = near; p[3] = far; }
        return type;
    }

    /** OpenGL-style projection: the camera looks down -Z with +Y up. */
    Transform projection() {
        Transform t = new Transform();
        if (type == GENERIC) { t.set(generic); return t; }
        java.util.Arrays.fill(t.m, 0);
        if (type == PERSPECTIVE) {
            float f = (float) (1.0 / Math.tan(Math.toRadians(fovy) * 0.5));
            t.m[0] = f / aspect; t.m[5] = f;
            t.m[10] = -(far + near) / (far - near); t.m[11] = -2 * far * near / (far - near);
            t.m[14] = -1;
        } else {
            t.m[0] = 2 / (height * aspect); t.m[5] = 2 / height;
            t.m[10] = -2 / (far - near); t.m[11] = -(far + near) / (far - near);
            t.m[15] = 1;
        }
        return t;
    }

    @Override boolean isCompatibleProperty(int p) {
        return p == AnimationTrack.FIELD_OF_VIEW || p == AnimationTrack.NEAR_DISTANCE || p == AnimationTrack.FAR_DISTANCE || super.isCompatibleProperty(p);
    }
    @Override void updateProperty(int property, float[] v) {
        if (v.length < 1) return;
        switch (property) {
            case AnimationTrack.FIELD_OF_VIEW: if (type == PERSPECTIVE && v[0] > 0 && v[0] < 180) fovy = v[0]; break;
            case AnimationTrack.NEAR_DISTANCE: near = v[0]; break;
            case AnimationTrack.FAR_DISTANCE: far = v[0]; break;
            default: super.updateProperty(property, v);
        }
    }
    @Override Object3D newInstance() { return new Camera(); }
    @Override void copyInto(Object3D target, Map<Object3D, Object3D> map) {
        super.copyInto(target, map);
        Camera c = (Camera) target;
        c.type = type; c.fovy = fovy; c.height = height; c.aspect = aspect; c.near = near; c.far = far; c.generic.set(generic);
    }
}
