// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 J2ME Deck contributors
package javax.microedition.m3g;

public class TriangleStripArray extends IndexBuffer {
    private final int[] indices, strips;
    private int[] tris;

    public TriangleStripArray(int firstIndex, int[] stripLengths) {
        this(sequence(firstIndex, stripLengths), stripLengths);
    }
    public TriangleStripArray(int[] indices, int[] stripLengths) {
        if (indices == null || stripLengths == null) throw new NullPointerException();
        if (stripLengths.length < 1) throw new IllegalArgumentException("at least one strip is required");
        int total = 0;
        for (int s : stripLengths) { if (s < 3) throw new IllegalArgumentException("each strip needs at least 3 indices"); total += s; }
        if (indices.length < total) throw new IllegalArgumentException("not enough indices for the strips");
        for (int i : indices) if (i < 0 || i > 65535) throw new IllegalArgumentException("index out of range");
        this.indices = indices.clone(); this.strips = stripLengths.clone();
    }
    private static int[] sequence(int first, int[] lengths) {
        if (lengths == null) throw new NullPointerException();
        int total = 0; for (int s : lengths) total += s;
        int[] r = new int[total];
        for (int i = 0; i < total; i++) r[i] = first + i;
        return r;
    }

    @Override public int getIndexCount() { int n = 0; for (int s : strips) n += s; return n; }
    @Override public void getIndices(int[] out) {
        if (out == null) throw new NullPointerException();
        int n = getIndexCount();
        if (out.length < n) throw new IllegalArgumentException("array too short");
        System.arraycopy(indices, 0, out, 0, n);
    }

    @Override int[] triangles() {
        if (tris != null) return tris;
        int count = 0; for (int s : strips) count += s - 2;
        int[] t = new int[count * 3];
        int o = 0, base = 0;
        for (int s : strips) {
            for (int i = 0; i < s - 2; i++) {
                int a = indices[base + i], b = indices[base + i + 1], c = indices[base + i + 2];
                if ((i & 1) == 0) { t[o++] = a; t[o++] = b; t[o++] = c; } else { t[o++] = b; t[o++] = a; t[o++] = c; }   // keep winding consistent
            }
            base += s;
        }
        return tris = t;
    }

    @Override Object3D newInstance() { return new TriangleStripArray(indices, strips); }
}
