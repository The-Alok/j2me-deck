// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 J2ME Deck contributors
package javax.microedition.m3g;

public class RayIntersection {
    Node intersected;
    float distance, nx, ny, nz;
    int submesh = -1;
    final float[] s = new float[8], t = new float[8];
    final float[] ray = new float[6];

    public RayIntersection() {}

    public Node getIntersected() { return intersected; }
    public float getDistance() { return distance; }
    public int getSubmeshIndex() { return submesh; }
    public float getNormalX() { return nx; }
    public float getNormalY() { return ny; }
    public float getNormalZ() { return nz; }
    public float getTextureS(int unit) { if (unit < 0 || unit >= s.length) throw new IndexOutOfBoundsException("texture unit"); return s[unit]; }
    public float getTextureT(int unit) { if (unit < 0 || unit >= t.length) throw new IndexOutOfBoundsException("texture unit"); return t[unit]; }
    public void getRay(float[] r) {
        if (r == null) throw new NullPointerException();
        if (r.length < 6) throw new IllegalArgumentException("array too short");
        System.arraycopy(ray, 0, r, 0, 6);
    }
}
