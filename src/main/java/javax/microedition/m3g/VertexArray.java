// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 J2ME Deck contributors
package javax.microedition.m3g;

import java.util.Map;

public class VertexArray extends Object3D {
    private final int n, comps, size;
    final short[] data;       // bytes are stored sign-extended

    public VertexArray(int numVertices, int numComponents, int componentSize) {
        if (numVertices < 1 || numVertices > 65535) throw new IllegalArgumentException("numVertices must be in [1, 65535]");
        if (numComponents < 2 || numComponents > 4) throw new IllegalArgumentException("numComponents must be in [2, 4]");
        if (componentSize < 1 || componentSize > 2) throw new IllegalArgumentException("componentSize must be 1 or 2");
        n = numVertices; comps = numComponents; size = componentSize;
        data = new short[n * comps];
    }

    public int getVertexCount() { return n; }
    public int getComponentCount() { return comps; }
    public int getComponentType() { return size; }

    public void set(int first, int count, byte[] values) {
        if (values == null) throw new NullPointerException();
        if (size != 1) throw new IllegalStateException("this array holds 16-bit components");
        range(first, count, values.length);
        for (int i = 0; i < count * comps; i++) data[first * comps + i] = values[i];
    }
    public void set(int first, int count, short[] values) {
        if (values == null) throw new NullPointerException();
        if (size != 2) throw new IllegalStateException("this array holds 8-bit components");
        range(first, count, values.length);
        System.arraycopy(values, 0, data, first * comps, count * comps);
    }
    public void get(int first, int count, byte[] values) {
        if (values == null) throw new NullPointerException();
        if (size != 1) throw new IllegalStateException("this array holds 16-bit components");
        range(first, count, values.length);
        for (int i = 0; i < count * comps; i++) values[i] = (byte) data[first * comps + i];
    }
    public void get(int first, int count, short[] values) {
        if (values == null) throw new NullPointerException();
        if (size != 2) throw new IllegalStateException("this array holds 8-bit components");
        range(first, count, values.length);
        System.arraycopy(data, first * comps, values, 0, count * comps);
    }
    private void range(int first, int count, int len) {
        if (count < 0 || first < 0 || first + count > n) throw new IndexOutOfBoundsException("vertex range out of bounds");
        if (len < count * comps) throw new IllegalArgumentException("value array too short");
    }

    float value(int v, int c) { return data[v * comps + c]; }

    @Override Object3D newInstance() { VertexArray a = new VertexArray(n, comps, size); return a; }
    @Override void copyInto(Object3D t, Map<Object3D, Object3D> m) { System.arraycopy(data, 0, ((VertexArray) t).data, 0, data.length); }
}
