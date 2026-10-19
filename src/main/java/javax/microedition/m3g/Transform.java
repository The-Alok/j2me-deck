// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 J2ME Deck contributors
package javax.microedition.m3g;

/** 4x4 matrix, row-major: m[row * 4 + col]. */
public class Transform {
    final float[] m = new float[16];

    public Transform() { setIdentity(); }
    public Transform(Transform t) { if (t == null) throw new NullPointerException(); set(t); }

    public void setIdentity() { java.util.Arrays.fill(m, 0); m[0] = m[5] = m[10] = m[15] = 1; }
    public void set(Transform t) { if (t == null) throw new NullPointerException(); System.arraycopy(t.m, 0, m, 0, 16); }
    public void set(float[] a) {
        if (a == null) throw new NullPointerException();
        if (a.length < 16) throw new IllegalArgumentException("matrix needs 16 values");
        System.arraycopy(a, 0, m, 0, 16);
    }
    public void get(float[] a) {
        if (a == null) throw new NullPointerException();
        if (a.length < 16) throw new IllegalArgumentException("matrix needs 16 values");
        System.arraycopy(m, 0, a, 0, 16);
    }

    public void invert() {
        float[] inv = new float[16];
        float[] a = m;
        inv[0] = a[5]*a[10]*a[15] - a[5]*a[11]*a[14] - a[9]*a[6]*a[15] + a[9]*a[7]*a[14] + a[13]*a[6]*a[11] - a[13]*a[7]*a[10];
        inv[4] = -a[4]*a[10]*a[15] + a[4]*a[11]*a[14] + a[8]*a[6]*a[15] - a[8]*a[7]*a[14] - a[12]*a[6]*a[11] + a[12]*a[7]*a[10];
        inv[8] = a[4]*a[9]*a[15] - a[4]*a[11]*a[13] - a[8]*a[5]*a[15] + a[8]*a[7]*a[13] + a[12]*a[5]*a[11] - a[12]*a[7]*a[9];
        inv[12] = -a[4]*a[9]*a[14] + a[4]*a[10]*a[13] + a[8]*a[5]*a[14] - a[8]*a[6]*a[13] - a[12]*a[5]*a[10] + a[12]*a[6]*a[9];
        inv[1] = -a[1]*a[10]*a[15] + a[1]*a[11]*a[14] + a[9]*a[2]*a[15] - a[9]*a[3]*a[14] - a[13]*a[2]*a[11] + a[13]*a[3]*a[10];
        inv[5] = a[0]*a[10]*a[15] - a[0]*a[11]*a[14] - a[8]*a[2]*a[15] + a[8]*a[3]*a[14] + a[12]*a[2]*a[11] - a[12]*a[3]*a[10];
        inv[9] = -a[0]*a[9]*a[15] + a[0]*a[11]*a[13] + a[8]*a[1]*a[15] - a[8]*a[3]*a[13] - a[12]*a[1]*a[11] + a[12]*a[3]*a[9];
        inv[13] = a[0]*a[9]*a[14] - a[0]*a[10]*a[13] - a[8]*a[1]*a[14] + a[8]*a[2]*a[13] + a[12]*a[1]*a[10] - a[12]*a[2]*a[9];
        inv[2] = a[1]*a[6]*a[15] - a[1]*a[7]*a[14] - a[5]*a[2]*a[15] + a[5]*a[3]*a[14] + a[13]*a[2]*a[7] - a[13]*a[3]*a[6];
        inv[6] = -a[0]*a[6]*a[15] + a[0]*a[7]*a[14] + a[4]*a[2]*a[15] - a[4]*a[3]*a[14] - a[12]*a[2]*a[7] + a[12]*a[3]*a[6];
        inv[10] = a[0]*a[5]*a[15] - a[0]*a[7]*a[13] - a[4]*a[1]*a[15] + a[4]*a[3]*a[13] + a[12]*a[1]*a[7] - a[12]*a[3]*a[5];
        inv[14] = -a[0]*a[5]*a[14] + a[0]*a[6]*a[13] + a[4]*a[1]*a[14] - a[4]*a[2]*a[13] - a[12]*a[1]*a[6] + a[12]*a[2]*a[5];
        inv[3] = -a[1]*a[6]*a[11] + a[1]*a[7]*a[10] + a[5]*a[2]*a[11] - a[5]*a[3]*a[10] - a[9]*a[2]*a[7] + a[9]*a[3]*a[6];
        inv[7] = a[0]*a[6]*a[11] - a[0]*a[7]*a[10] - a[4]*a[2]*a[11] + a[4]*a[3]*a[10] + a[8]*a[2]*a[7] - a[8]*a[3]*a[6];
        inv[11] = -a[0]*a[5]*a[11] + a[0]*a[7]*a[9] + a[4]*a[1]*a[11] - a[4]*a[3]*a[9] - a[8]*a[1]*a[7] + a[8]*a[3]*a[5];
        inv[15] = a[0]*a[5]*a[10] - a[0]*a[6]*a[9] - a[4]*a[1]*a[10] + a[4]*a[2]*a[9] + a[8]*a[1]*a[6] - a[8]*a[2]*a[5];
        float det = a[0]*inv[0] + a[1]*inv[4] + a[2]*inv[8] + a[3]*inv[12];
        if (det == 0 || Float.isNaN(det)) throw new ArithmeticException("matrix is singular");
        float id = 1f / det;
        for (int i = 0; i < 16; i++) m[i] = inv[i] * id;
    }

    public void transpose() {
        for (int r = 0; r < 4; r++) for (int c = r + 1; c < 4; c++) { float t = m[r*4+c]; m[r*4+c] = m[c*4+r]; m[c*4+r] = t; }
    }

    /** this = this * t */
    public void postMultiply(Transform t) {
        if (t == null) throw new NullPointerException();
        float[] r = new float[16], b = t.m;
        for (int i = 0; i < 4; i++) for (int j = 0; j < 4; j++) {
            r[i*4+j] = m[i*4]*b[j] + m[i*4+1]*b[4+j] + m[i*4+2]*b[8+j] + m[i*4+3]*b[12+j];
        }
        System.arraycopy(r, 0, m, 0, 16);
    }
    public void postScale(float sx, float sy, float sz) {
        for (int i = 0; i < 4; i++) { m[i*4] *= sx; m[i*4+1] *= sy; m[i*4+2] *= sz; }
    }
    public void postTranslate(float tx, float ty, float tz) {
        for (int i = 0; i < 4; i++) m[i*4+3] += m[i*4]*tx + m[i*4+1]*ty + m[i*4+2]*tz;
    }
    public void postRotate(float angle, float ax, float ay, float az) {
        float len = (float) Math.sqrt(ax*ax + ay*ay + az*az);
        if (len == 0) { if (angle == 0) return; throw new IllegalArgumentException("zero rotation axis"); }
        double a = Math.toRadians(angle) * 0.5, s = Math.sin(a) / len;
        postRotateQuat((float) (ax * s), (float) (ay * s), (float) (az * s), (float) Math.cos(a));
    }
    public void postRotateQuat(float qx, float qy, float qz, float qw) {
        float n = (float) Math.sqrt(qx*qx + qy*qy + qz*qz + qw*qw);
        if (n == 0) throw new IllegalArgumentException("zero quaternion");
        qx /= n; qy /= n; qz /= n; qw /= n;
        Transform r = new Transform();
        r.m[0] = 1 - 2*(qy*qy + qz*qz); r.m[1] = 2*(qx*qy - qz*qw);     r.m[2] = 2*(qx*qz + qy*qw);
        r.m[4] = 2*(qx*qy + qz*qw);     r.m[5] = 1 - 2*(qx*qx + qz*qz); r.m[6] = 2*(qy*qz - qx*qw);
        r.m[8] = 2*(qx*qz - qy*qw);     r.m[9] = 2*(qy*qz + qx*qw);     r.m[10] = 1 - 2*(qx*qx + qy*qy);
        postMultiply(r);
    }

    /** Transforms vertices; w=true treats them as (x,y,z,1) and writes 4 floats per vertex, else 3. */
    public void transform(VertexArray in, float[] out, boolean w) {
        if (in == null || out == null) throw new NullPointerException();
        int n = in.getVertexCount(), comps = in.getComponentCount();
        if (out.length < n * 4) throw new IllegalArgumentException("output array too small");
        float[] v = new float[4];
        for (int i = 0; i < n; i++) {
            v[0] = in.value(i, 0); v[1] = in.value(i, 1); v[2] = comps > 2 ? in.value(i, 2) : 0; v[3] = comps > 3 ? in.value(i, 3) : (w ? 1 : 1);
            if (comps < 4) v[3] = w ? 1 : 1;
            float[] o = apply(v);
            out[i*4] = o[0]; out[i*4+1] = o[1]; out[i*4+2] = o[2]; out[i*4+3] = o[3];
        }
    }
    public void transform(float[] vectors) {
        if (vectors == null) throw new NullPointerException();
        if (vectors.length % 4 != 0) throw new IllegalArgumentException("length must be a multiple of 4");
        float[] v = new float[4];
        for (int i = 0; i < vectors.length; i += 4) {
            v[0] = vectors[i]; v[1] = vectors[i+1]; v[2] = vectors[i+2]; v[3] = vectors[i+3];
            float[] o = apply(v);
            System.arraycopy(o, 0, vectors, i, 4);
        }
    }

    float[] apply(float[] v) {
        return new float[]{
                m[0]*v[0] + m[1]*v[1] + m[2]*v[2] + m[3]*v[3],
                m[4]*v[0] + m[5]*v[1] + m[6]*v[2] + m[7]*v[3],
                m[8]*v[0] + m[9]*v[1] + m[10]*v[2] + m[11]*v[3],
                m[12]*v[0] + m[13]*v[1] + m[14]*v[2] + m[15]*v[3]};
    }

    static Transform mul(Transform a, Transform b) { Transform r = new Transform(a); r.postMultiply(b); return r; }
    static Transform inverse(Transform a) { Transform r = new Transform(a); r.invert(); return r; }
}
