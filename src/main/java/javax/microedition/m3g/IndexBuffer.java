// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 J2ME Deck contributors
package javax.microedition.m3g;

public abstract class IndexBuffer extends Object3D {
    IndexBuffer() {}
    public abstract int getIndexCount();
    public abstract void getIndices(int[] out);
    /** Triangles as consecutive index triples (strips are expanded with alternating winding). */
    abstract int[] triangles();
}
