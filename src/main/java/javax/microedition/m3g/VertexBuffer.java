// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 J2ME Deck contributors
package javax.microedition.m3g;

import java.util.List;

public class VertexBuffer extends Object3D {
    private VertexArray positions, normals, colors;
    private float posScale = 1; private final float[] posBias = new float[3];
    private final VertexArray[] tex = new VertexArray[8];
    private final float[] texScale = new float[8]; private final float[][] texBias = new float[8][3];
    private int defaultColor = 0xFFFFFFFF;

    public VertexBuffer() {}

    public int getVertexCount() { return positions != null ? positions.getVertexCount() : normals != null ? normals.getVertexCount() : colors != null ? colors.getVertexCount() : 0; }

    public void setPositions(VertexArray a, float scale, float[] bias) {
        if (a == null) { positions = null; return; }
        if (a.getComponentCount() != 3) throw new IllegalArgumentException("positions need 3 components");
        if (bias != null && bias.length < 3) throw new IllegalArgumentException("bias needs 3 values");
        checkCount(a);
        positions = a; posScale = scale;
        for (int i = 0; i < 3; i++) posBias[i] = bias == null ? 0 : bias[i];
    }
    public VertexArray getPositions(float[] scaleBias) {
        if (scaleBias != null) { if (scaleBias.length < 4) throw new IllegalArgumentException("need 4 values"); scaleBias[0] = posScale; System.arraycopy(posBias, 0, scaleBias, 1, 3); }
        return positions;
    }
    public void setNormals(VertexArray a) {
        if (a != null) { if (a.getComponentCount() != 3) throw new IllegalArgumentException("normals need 3 components"); checkCount(a); }
        normals = a;
    }
    public VertexArray getNormals() { return normals; }
    public void setColors(VertexArray a) {
        if (a != null) {
            if (a.getComponentType() != 1 || (a.getComponentCount() != 3 && a.getComponentCount() != 4)) throw new IllegalArgumentException("colors need 3 or 4 byte components");
            checkCount(a);
        }
        colors = a;
    }
    public VertexArray getColors() { return colors; }
    public void setTexCoords(int index, VertexArray a, float scale, float[] bias) {
        if (index < 0 || index >= tex.length) throw new IndexOutOfBoundsException("texture unit out of range");
        if (a == null) { tex[index] = null; return; }
        if (a.getComponentCount() != 2 && a.getComponentCount() != 3) throw new IllegalArgumentException("texture coordinates need 2 or 3 components");
        if (bias != null && bias.length < a.getComponentCount()) throw new IllegalArgumentException("bias too short");
        checkCount(a);
        tex[index] = a; texScale[index] = scale;
        for (int i = 0; i < 3; i++) texBias[index][i] = bias == null || i >= bias.length ? 0 : bias[i];
    }
    public VertexArray getTexCoords(int index, float[] scaleBias) {
        if (index < 0 || index >= tex.length) throw new IndexOutOfBoundsException("texture unit out of range");
        if (scaleBias != null && tex[index] != null) {
            if (scaleBias.length < 1 + tex[index].getComponentCount()) throw new IllegalArgumentException("array too short");
            scaleBias[0] = texScale[index];
            for (int i = 0; i < tex[index].getComponentCount(); i++) scaleBias[1 + i] = texBias[index][i];
        }
        return tex[index];
    }
    public void setDefaultColor(int argb) { defaultColor = argb; }
    public int getDefaultColor() { return defaultColor; }

    private void checkCount(VertexArray a) {
        int c = getVertexCount();
        if (c != 0 && a.getVertexCount() != c && !onlyHolder(a)) throw new IllegalArgumentException("vertex count differs from the other arrays");
    }
    private boolean onlyHolder(VertexArray a) { return (positions == null || positions == a) && (normals == null || normals == a) && (colors == null || colors == a); }

    float posScale() { return posScale; }
    float[] posBias() { return posBias; }
    float texScale(int i) { return texScale[i]; }
    float[] texBias(int i) { return texBias[i]; }
    VertexArray tex(int i) { return tex[i]; }

    @Override void references(List<Object3D> out) {
        super.references(out);
        if (positions != null) out.add(positions);
        if (normals != null) out.add(normals);
        if (colors != null) out.add(colors);
        for (VertexArray t : tex) if (t != null) out.add(t);
    }
    @Override boolean isCompatibleProperty(int p) { return p == AnimationTrack.COLOR; }
    @Override void updateProperty(int p, float[] v) {
        if (p == AnimationTrack.COLOR && v.length >= 3) defaultColor = (defaultColor & 0xFF000000) | Light.pack(v[0], v[1], v[2]);
        else super.updateProperty(p, v);
    }
}
