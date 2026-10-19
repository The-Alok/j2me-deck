// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 J2ME Deck contributors
package javax.microedition.m3g;

import java.util.List;
import java.util.Map;

public class Mesh extends Node {
    VertexBuffer vertices;
    IndexBuffer[] indices;
    Appearance[] appearances;

    public Mesh(VertexBuffer vertices, IndexBuffer[] submeshes, Appearance[] appearances) { init(vertices, submeshes, appearances); }
    public Mesh(VertexBuffer vertices, IndexBuffer submesh, Appearance appearance) { init(vertices, new IndexBuffer[]{submesh}, new Appearance[]{appearance}); }

    final void init(VertexBuffer v, IndexBuffer[] ib, Appearance[] ap) {
        if (v == null || ib == null) throw new NullPointerException();
        if (ib.length == 0) throw new IllegalArgumentException("at least one submesh is required");
        for (IndexBuffer i : ib) if (i == null) throw new NullPointerException("null index buffer");
        if (ap != null && ap.length < ib.length) throw new IllegalArgumentException("need one appearance per submesh");
        if (v.getPositions(null) == null) throw new IllegalArgumentException("vertex buffer has no positions");
        vertices = v; indices = ib.clone(); appearances = new Appearance[ib.length];
        if (ap != null) System.arraycopy(ap, 0, appearances, 0, ib.length);
    }

    public VertexBuffer getVertexBuffer() { return vertices; }
    public int getSubmeshCount() { return indices.length; }
    public IndexBuffer getIndexBuffer(int i) { return indices[i]; }
    public Appearance getAppearance(int i) { return appearances[i]; }
    public void setAppearance(int i, Appearance a) { appearances[i] = a; }

    @Override void references(List<Object3D> out) {
        super.references(out);
        out.add(vertices);
        for (IndexBuffer i : indices) out.add(i);
        for (Appearance a : appearances) if (a != null) out.add(a);
    }

    @Override Object3D newInstance() { return new Mesh(vertices, indices, appearances); }
    @Override void copyInto(Object3D t, Map<Object3D, Object3D> m) {
        super.copyInto(t, m);
        System.arraycopy(appearances, 0, ((Mesh) t).appearances, 0, appearances.length);
    }
}
