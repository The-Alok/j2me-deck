// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 J2ME Deck contributors
package javax.microedition.m3g;

/** Ray picking against mesh triangles (Moller-Trumbore). Skinning and morphing are ignored: the base geometry is tested. */
final class Picker {
    private Picker() {}

    static boolean pick(Group g, int scope, float x, float y, Camera cam, RayIntersection ri) {
        Transform toG = new Transform();
        if (!cam.getTransformTo(g, toG)) return false;
        Transform inv;
        try { inv = Transform.inverse(cam.projection()); } catch (ArithmeticException e) { return false; }
        float nx = 2 * x - 1, ny = 1 - 2 * y;
        float[] a = inv.apply(new float[]{nx, ny, -1, 1}), b = inv.apply(new float[]{nx, ny, 1, 1});
        for (float[] p : new float[][]{a, b}) { p[0] /= p[3]; p[1] /= p[3]; p[2] /= p[3]; p[3] = 1; }
        float[] o = toG.apply(a), f = toG.apply(b);
        return pick(g, scope, o[0], o[1], o[2], f[0] - o[0], f[1] - o[1], f[2] - o[2], ri);
    }

    static boolean pick(Group g, int scope, float ox, float oy, float oz, float dx, float dy, float dz, RayIntersection ri) {
        ri.intersected = null; ri.distance = Float.MAX_VALUE;
        walk(g, g, scope, ox, oy, oz, dx, dy, dz, ri);
        if (ri.intersected == null) return false;
        ri.ray[0] = ox; ri.ray[1] = oy; ri.ray[2] = oz; ri.ray[3] = dx; ri.ray[4] = dy; ri.ray[5] = dz;
        return true;
    }

    private static void walk(Group root, Node n, int scope, float ox, float oy, float oz, float dx, float dy, float dz, RayIntersection ri) {
        if (!n.isPickingEnabled()) return;
        if (n instanceof Mesh && (n.getScope() & scope) != 0) testMesh(root, (Mesh) n, ox, oy, oz, dx, dy, dz, ri);
        if (n instanceof Group) for (Node c : ((Group) n).children) walk(root, c, scope, ox, oy, oz, dx, dy, dz, ri);
    }

    private static void testMesh(Group root, Mesh m, float ox, float oy, float oz, float dx, float dy, float dz, RayIntersection ri) {
        Transform toRoot = new Transform();
        if (!m.getTransformTo(root, toRoot)) return;
        Transform inv;
        try { inv = Transform.inverse(toRoot); } catch (ArithmeticException e) { return; }
        float[] o = inv.apply(new float[]{ox, oy, oz, 1}), d = inv.apply(new float[]{dx, dy, dz, 0});
        VertexBuffer vb = m.getVertexBuffer();
        float[] sb = new float[4];
        VertexArray pa = vb.getPositions(sb);
        for (int s = 0; s < m.getSubmeshCount(); s++) {
            int[] tris = m.getIndexBuffer(s).triangles();
            for (int t = 0; t + 2 < tris.length; t += 3) {
                int i0 = tris[t], i1 = tris[t + 1], i2 = tris[t + 2];
                float[] p0 = pos(pa, sb, i0), p1 = pos(pa, sb, i1), p2 = pos(pa, sb, i2);
                float[] e1 = {p1[0] - p0[0], p1[1] - p0[1], p1[2] - p0[2]}, e2 = {p2[0] - p0[0], p2[1] - p0[1], p2[2] - p0[2]};
                float[] h = {d[1] * e2[2] - d[2] * e2[1], d[2] * e2[0] - d[0] * e2[2], d[0] * e2[1] - d[1] * e2[0]};
                float det = e1[0] * h[0] + e1[1] * h[1] + e1[2] * h[2];
                if (Math.abs(det) < 1e-9f) continue;
                float inv_ = 1f / det;
                float[] sv = {o[0] - p0[0], o[1] - p0[1], o[2] - p0[2]};
                float u = (sv[0] * h[0] + sv[1] * h[1] + sv[2] * h[2]) * inv_;
                if (u < 0 || u > 1) continue;
                float[] q = {sv[1] * e1[2] - sv[2] * e1[1], sv[2] * e1[0] - sv[0] * e1[2], sv[0] * e1[1] - sv[1] * e1[0]};
                float v = (d[0] * q[0] + d[1] * q[1] + d[2] * q[2]) * inv_;
                if (v < 0 || u + v > 1) continue;
                float dist = (e2[0] * q[0] + e2[1] * q[1] + e2[2] * q[2]) * inv_;
                if (dist < 0 || dist >= ri.distance) continue;
                ri.distance = dist; ri.intersected = m; ri.submesh = s;
                float w = 1 - u - v;
                VertexArray na = vb.getNormals();
                if (na != null) {
                    ri.nx = w * na.value(i0, 0) + u * na.value(i1, 0) + v * na.value(i2, 0);
                    ri.ny = w * na.value(i0, 1) + u * na.value(i1, 1) + v * na.value(i2, 1);
                    ri.nz = w * na.value(i0, 2) + u * na.value(i1, 2) + v * na.value(i2, 2);
                } else { ri.nx = e1[1] * e2[2] - e1[2] * e2[1]; ri.ny = e1[2] * e2[0] - e1[0] * e2[2]; ri.nz = e1[0] * e2[1] - e1[1] * e2[0]; }
                float nl = (float) Math.sqrt(ri.nx * ri.nx + ri.ny * ri.ny + ri.nz * ri.nz);
                if (nl > 0) { ri.nx /= nl; ri.ny /= nl; ri.nz /= nl; }
                for (int unit = 0; unit < 2; unit++) {
                    VertexArray ta = vb.tex(unit);
                    if (ta == null) continue;
                    float sc = vb.texScale(unit); float[] bi = vb.texBias(unit);
                    ri.s[unit] = (w * ta.value(i0, 0) + u * ta.value(i1, 0) + v * ta.value(i2, 0)) * sc + bi[0];
                    ri.t[unit] = (w * ta.value(i0, 1) + u * ta.value(i1, 1) + v * ta.value(i2, 1)) * sc + bi[1];
                }
            }
        }
    }
    private static float[] pos(VertexArray a, float[] sb, int i) { return new float[]{a.value(i, 0) * sb[0] + sb[1], a.value(i, 1) * sb[0] + sb[2], a.value(i, 2) * sb[0] + sb[3]}; }
}
