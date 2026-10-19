// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 J2ME Deck contributors
package javax.microedition.m3g;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class SkinnedMesh extends Mesh {
    /** One bone's influence on a range of vertices. bind = inverse of the bone-to-skin transform at the time it was added. */
    static final class Influence { Node bone; int weight, first, count; Transform bind; }

    private Group skeleton;
    final List<Influence> influences = new ArrayList<>();

    public SkinnedMesh(VertexBuffer vertices, IndexBuffer[] submeshes, Appearance[] appearances, Group skeleton) {
        super(vertices, submeshes, appearances); setSkeleton(skeleton);
    }
    public SkinnedMesh(VertexBuffer vertices, IndexBuffer submesh, Appearance appearance, Group skeleton) {
        super(vertices, submesh, appearance); setSkeleton(skeleton);
    }
    private void setSkeleton(Group g) {
        if (g == null) throw new NullPointerException();
        if (g instanceof World) throw new IllegalArgumentException("skeleton must not be a World");
        if (g.parent != null) throw new IllegalArgumentException("skeleton must not have a parent");
        skeleton = g;
        // the skeleton is a child of the skinned mesh in the scene graph
        g.parent = this;
    }

    public Group getSkeleton() { return skeleton; }

    public void addTransform(Node bone, int weight, int firstVertex, int numVertices) {
        if (bone == null) throw new NullPointerException();
        if (weight <= 0) throw new IllegalArgumentException("weight must be positive");
        if (numVertices <= 0 || firstVertex < 0 || firstVertex + numVertices > vertices.getVertexCount()) throw new IndexOutOfBoundsException("vertex range out of bounds");
        Transform t = new Transform();
        if (!bone.getTransformTo(this, t)) throw new IllegalArgumentException("bone is not part of this skinned mesh's skeleton");
        Influence i = new Influence();
        i.bone = bone; i.weight = weight; i.first = firstVertex; i.count = numVertices;
        try { t.invert(); } catch (ArithmeticException e) { throw new ArithmeticException("bone transform is not invertible"); }
        i.bind = t;
        influences.add(i);
    }

    public void getBoneTransform(Node bone, Transform out) {
        if (bone == null || out == null) throw new NullPointerException();
        for (Influence i : influences) if (i.bone == bone) { out.set(Transform.inverse(i.bind)); return; }
        throw new IllegalArgumentException("bone has no influence on this mesh");
    }
    public int getBoneVertices(Node bone, int[] indices, float[] weights) {
        if (bone == null) throw new NullPointerException();
        int[] total = new int[vertices.getVertexCount()];
        for (Influence i : influences) for (int v = i.first; v < i.first + i.count; v++) total[v] += i.weight;
        int found = 0;
        for (Influence i : influences) if (i.bone == bone) {
            for (int v = i.first; v < i.first + i.count; v++) {
                if (indices != null && found < indices.length) indices[found] = v;
                if (weights != null && found < weights.length) weights[found] = total[v] == 0 ? 0 : (float) i.weight / total[v];
                found++;
            }
        }
        return found;
    }

    @Override void references(List<Object3D> out) { super.references(out); out.add(skeleton); for (Influence i : influences) out.add(i.bone); }

    @Override Object3D newInstance() { return new SkinnedMesh(vertices, indices[0], appearances[0], new Group()); }
}
