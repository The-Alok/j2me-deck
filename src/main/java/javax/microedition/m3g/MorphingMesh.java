// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 J2ME Deck contributors
package javax.microedition.m3g;

import java.util.List;

public class MorphingMesh extends Mesh {
    private final VertexBuffer[] targets;
    private final float[] weights;

    public MorphingMesh(VertexBuffer base, VertexBuffer[] targets, IndexBuffer[] submeshes, Appearance[] appearances) {
        super(base, submeshes, appearances);
        this.targets = check(base, targets); weights = new float[this.targets.length];
    }
    public MorphingMesh(VertexBuffer base, VertexBuffer[] targets, IndexBuffer submesh, Appearance appearance) {
        super(base, submesh, appearance);
        this.targets = check(base, targets); weights = new float[this.targets.length];
    }
    private static VertexBuffer[] check(VertexBuffer base, VertexBuffer[] t) {
        if (t == null) throw new NullPointerException();
        if (t.length < 1) throw new IllegalArgumentException("at least one morph target is required");
        for (VertexBuffer v : t) {
            if (v == null) throw new NullPointerException("null morph target");
            if (v.getVertexCount() != base.getVertexCount()) throw new IllegalArgumentException("morph target has a different vertex count");
            if (v.getPositions(null) == null) throw new IllegalArgumentException("morph target has no positions");
        }
        return t.clone();
    }

    public int getMorphTargetCount() { return targets.length; }
    public VertexBuffer getMorphTarget(int i) { return targets[i]; }
    public void setWeights(float[] w) {
        if (w == null) throw new NullPointerException();
        if (w.length < weights.length) throw new IllegalArgumentException("need one weight per morph target");
        System.arraycopy(w, 0, weights, 0, weights.length);
    }
    public void getWeights(float[] w) {
        if (w == null) throw new NullPointerException();
        if (w.length < weights.length) throw new IllegalArgumentException("array too short");
        System.arraycopy(weights, 0, w, 0, weights.length);
    }

    @Override void references(List<Object3D> out) { super.references(out); for (VertexBuffer v : targets) out.add(v); }
    @Override boolean isCompatibleProperty(int p) { return p == AnimationTrack.MORPH_WEIGHTS || super.isCompatibleProperty(p); }
    @Override void updateProperty(int p, float[] v) {
        if (p == AnimationTrack.MORPH_WEIGHTS) { System.arraycopy(v, 0, weights, 0, Math.min(v.length, weights.length)); }
        else super.updateProperty(p, v);
    }
    float[] weights() { return weights; }
    VertexBuffer[] targets() { return targets; }
    @Override Object3D newInstance() { return new MorphingMesh(vertices, targets, indices[0], appearances[0]); }
}
