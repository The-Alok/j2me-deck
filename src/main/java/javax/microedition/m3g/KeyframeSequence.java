// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 J2ME Deck contributors
package javax.microedition.m3g;

public class KeyframeSequence extends Object3D {
    public static final int LINEAR = 176, SLERP = 177, SPLINE = 178, SQUAD = 179, STEP = 180, CONSTANT = 192, LOOP = 193;

    private final int interp, comps;
    private final int[] times;
    private final float[][] values;
    private int duration, first, last, repeat = CONSTANT;
    private boolean durationSet;

    public KeyframeSequence(int numKeyframes, int numComponents, int interpolation) {
        if (numKeyframes < 1 || numComponents < 1) throw new IllegalArgumentException("need at least one keyframe and one component");
        if (interpolation < LINEAR || interpolation > STEP) throw new IllegalArgumentException("invalid interpolation");
        if ((interpolation == SLERP || interpolation == SQUAD) && numComponents != 4) throw new IllegalArgumentException("quaternion interpolation needs 4 components");
        interp = interpolation; comps = numComponents;
        times = new int[numKeyframes]; values = new float[numKeyframes][numComponents];
        last = numKeyframes - 1;
    }

    public void setKeyframe(int index, int time, float[] value) {
        if (value == null) throw new NullPointerException();
        if (index < 0 || index >= times.length) throw new IndexOutOfBoundsException("keyframe index out of range");
        if (time < 0) throw new IllegalArgumentException("time must be >= 0");
        if (value.length < comps) throw new IllegalArgumentException("value too short");
        times[index] = time;
        System.arraycopy(value, 0, values[index], 0, comps);
    }
    public int getKeyframe(int index, float[] value) {
        if (index < 0 || index >= times.length) throw new IndexOutOfBoundsException("keyframe index out of range");
        if (value != null) { if (value.length < comps) throw new IllegalArgumentException("array too short"); System.arraycopy(values[index], 0, value, 0, comps); }
        return times[index];
    }
    public void setValidRange(int firstIndex, int lastIndex) {
        if (firstIndex < 0 || firstIndex >= times.length || lastIndex < 0 || lastIndex >= times.length) throw new IndexOutOfBoundsException("valid range outside the keyframes");
        first = firstIndex; last = lastIndex;
    }
    public int getValidRangeFirst() { return first; }
    public int getValidRangeLast() { return last; }
    public void setDuration(int d) { if (d <= 0) throw new IllegalArgumentException("duration must be positive"); duration = d; durationSet = true; }
    public int getDuration() { return duration; }
    public void setRepeatMode(int m) { if (m != CONSTANT && m != LOOP) throw new IllegalArgumentException("invalid repeat mode"); repeat = m; }
    public int getRepeatMode() { return repeat; }
    public int getKeyframeCount() { return times.length; }
    public int getComponentCount() { return comps; }
    public int getInterpolationType() { return interp; }

    /** Number of keyframes in the valid range (it may wrap around the array end). */
    private int validCount() { return first <= last ? last - first + 1 : times.length - first + last + 1; }
    private int idx(int k) { return (first + k) % times.length; }

    /** Evaluates the sequence at time t (in sequence time units). */
    void sample(float t, float[] out) {
        int n = validCount();
        if (n == 1) { System.arraycopy(values[idx(0)], 0, out, 0, comps); return; }
        float firstT = times[idx(0)], lastT = times[idx(n - 1)];
        int dur = Math.max(duration, 1);
        if (repeat == LOOP) {
            t = t % dur; if (t < 0) t += dur;
            if (t < firstT) t += dur;                    // before the first key: belongs to the wrap-around segment
        } else {
            if (t <= firstT) { System.arraycopy(values[idx(0)], 0, out, 0, comps); return; }
            if (t >= lastT) { System.arraycopy(values[idx(n - 1)], 0, out, 0, comps); return; }
        }
        // find segment [k, k+1] containing t; the wrap segment runs last -> first + duration
        int k = 0;
        while (k < n - 1 && times[idx(k + 1)] <= t) k++;
        float t0 = times[idx(k)], t1;
        float[] a = values[idx(k)], b;
        if (k == n - 1) {                                // wrap-around (LOOP only)
            if (repeat != LOOP) { System.arraycopy(a, 0, out, 0, comps); return; }
            t1 = firstT + dur; b = values[idx(0)];
        } else { t1 = times[idx(k + 1)]; b = values[idx(k + 1)]; }
        if (interp == STEP) { System.arraycopy(a, 0, out, 0, comps); return; }
        float s = t1 == t0 ? 0 : (t - t0) / (t1 - t0);
        if (interp == SLERP || interp == SQUAD) slerp(a, b, s, out);       // SQUAD is approximated by SLERP
        else for (int i = 0; i < comps; i++) out[i] = a[i] + (b[i] - a[i]) * s;   // SPLINE is approximated by LINEAR
    }

    private static void slerp(float[] a, float[] b, float s, float[] out) {
        float dot = a[0]*b[0] + a[1]*b[1] + a[2]*b[2] + a[3]*b[3];
        float sign = dot < 0 ? -1 : 1; dot = Math.abs(dot);
        float k0, k1;
        if (dot > 0.9995f) { k0 = 1 - s; k1 = s * sign; }
        else {
            double th = Math.acos(dot), sin = Math.sin(th);
            k0 = (float) (Math.sin((1 - s) * th) / sin); k1 = (float) (Math.sin(s * th) / sin) * sign;
        }
        float n = 0;
        for (int i = 0; i < 4; i++) { out[i] = a[i] * k0 + b[i] * k1; n += out[i] * out[i]; }
        n = (float) Math.sqrt(n);
        if (n > 0) for (int i = 0; i < 4; i++) out[i] /= n;
    }
}
