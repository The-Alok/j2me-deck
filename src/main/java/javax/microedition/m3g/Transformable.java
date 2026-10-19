// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 J2ME Deck contributors
package javax.microedition.m3g;

import java.util.Map;

public abstract class Transformable extends Object3D {
    float tx, ty, tz, sx = 1, sy = 1, sz = 1;
    float qx, qy, qz, qw = 1;                     // orientation quaternion
    final Transform matrix = new Transform();     // generic (user supplied) part

    Transformable() {}

    public void setTranslation(float x, float y, float z) { tx = x; ty = y; tz = z; }
    public void translate(float x, float y, float z) { tx += x; ty += y; tz += z; }
    public void getTranslation(float[] t) { check(t, 3); t[0] = tx; t[1] = ty; t[2] = tz; }
    public void setScale(float x, float y, float z) { sx = x; sy = y; sz = z; }
    public void scale(float x, float y, float z) { sx *= x; sy *= y; sz *= z; }
    public void getScale(float[] s) { check(s, 3); s[0] = sx; s[1] = sy; s[2] = sz; }

    public void setOrientation(float angle, float ax, float ay, float az) {
        float[] q = quat(angle, ax, ay, az);
        qx = q[0]; qy = q[1]; qz = q[2]; qw = q[3];
    }
    public void preRotate(float angle, float ax, float ay, float az) { mulQuat(quat(angle, ax, ay, az), true); }
    public void postRotate(float angle, float ax, float ay, float az) { mulQuat(quat(angle, ax, ay, az), false); }
    public void getOrientation(float[] a) {
        check(a, 4);
        float w = Math.max(-1f, Math.min(1f, qw));
        double half = Math.acos(w);
        double s = Math.sin(half);
        if (s < 1e-6) { a[0] = 0; a[1] = 0; a[2] = 0; a[3] = 0; return; }
        a[0] = (float) Math.toDegrees(2 * half); a[1] = (float) (qx / s); a[2] = (float) (qy / s); a[3] = (float) (qz / s);
    }

    public void setTransform(Transform t) { if (t == null) matrix.setIdentity(); else matrix.set(t); }
    public void getTransform(Transform t) { if (t == null) throw new NullPointerException(); t.set(matrix); }

    /** T * R * S * M, mapping local coordinates to the parent's. */
    public void getCompositeTransform(Transform t) {
        if (t == null) throw new NullPointerException();
        t.setIdentity();
        t.postTranslate(tx, ty, tz);
        if (qx != 0 || qy != 0 || qz != 0) t.postRotateQuat(qx, qy, qz, qw);
        t.postScale(sx, sy, sz);
        t.postMultiply(matrix);
    }

    Transform composite() { Transform t = new Transform(); getCompositeTransform(t); return t; }

    private static void check(float[] a, int n) {
        if (a == null) throw new NullPointerException();
        if (a.length < n) throw new IllegalArgumentException("array too short");
    }
    static float[] quat(float angle, float ax, float ay, float az) {
        float len = (float) Math.sqrt(ax*ax + ay*ay + az*az);
        if (len == 0) { if (angle == 0) return new float[]{0, 0, 0, 1}; throw new IllegalArgumentException("zero rotation axis"); }
        double a = Math.toRadians(angle) * 0.5, s = Math.sin(a) / len;
        return new float[]{(float) (ax * s), (float) (ay * s), (float) (az * s), (float) Math.cos(a)};
    }
    private void mulQuat(float[] r, boolean pre) {
        float[] a = pre ? r : new float[]{qx, qy, qz, qw}, b = pre ? new float[]{qx, qy, qz, qw} : r;   // result = a * b
        float x = a[3]*b[0] + a[0]*b[3] + a[1]*b[2] - a[2]*b[1];
        float y = a[3]*b[1] - a[0]*b[2] + a[1]*b[3] + a[2]*b[0];
        float z = a[3]*b[2] + a[0]*b[1] - a[1]*b[0] + a[2]*b[3];
        float w = a[3]*b[3] - a[0]*b[0] - a[1]*b[1] - a[2]*b[2];
        float n = (float) Math.sqrt(x*x + y*y + z*z + w*w);
        qx = x / n; qy = y / n; qz = z / n; qw = w / n;
    }

    @Override boolean isCompatibleProperty(int p) { return p == AnimationTrack.TRANSLATION || p == AnimationTrack.SCALE || p == AnimationTrack.ORIENTATION; }

    @Override void updateProperty(int property, float[] v) {
        switch (property) {
            case AnimationTrack.TRANSLATION: if (v.length >= 3) setTranslation(v[0], v[1], v[2]); break;
            case AnimationTrack.SCALE: if (v.length >= 3) setScale(v[0], v[1], v[2]); else if (v.length >= 1) setScale(v[0], v[0], v[0]); break;
            case AnimationTrack.ORIENTATION: if (v.length >= 4) { qx = v[0]; qy = v[1]; qz = v[2]; qw = v[3]; } break;
            default: super.updateProperty(property, v);
        }
    }

    @Override void copyInto(Object3D target, Map<Object3D, Object3D> map) {
        Transformable t = (Transformable) target;
        t.tx = tx; t.ty = ty; t.tz = tz; t.sx = sx; t.sy = sy; t.sz = sz; t.qx = qx; t.qy = qy; t.qz = qz; t.qw = qw;
        t.matrix.set(matrix);
    }
}
