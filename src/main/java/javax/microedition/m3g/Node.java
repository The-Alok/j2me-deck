// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 J2ME Deck contributors
package javax.microedition.m3g;

import java.util.Map;

public abstract class Node extends Transformable {
    public static final int NONE = 144, ORIGIN = 145, X_AXIS = 146, Y_AXIS = 147, Z_AXIS = 148;

    Node parent;
    private boolean rendering = true, picking = true;
    private float alpha = 1f;
    private int scope = -1;
    private Node zRef, yRef;
    private int zTarget = NONE, yTarget = NONE;

    Node() {}

    public Node getParent() { return parent; }
    public void setRenderingEnable(boolean b) { rendering = b; }
    public boolean isRenderingEnabled() { return rendering; }
    public void setPickingEnable(boolean b) { picking = b; }
    public boolean isPickingEnabled() { return picking; }
    public void setAlphaFactor(float a) {
        if (a < 0 || a > 1) throw new IllegalArgumentException("alpha factor must be in [0, 1]");
        alpha = a;
    }
    public float getAlphaFactor() { return alpha; }
    public void setScope(int s) { scope = s; }
    public int getScope() { return scope; }

    /** Transform from this node's coordinates to the root of its tree. */
    Transform toRoot() {
        Transform t = composite();
        for (Node n = parent; n != null; n = n.parent) t = Transform.mul(n.composite(), t);
        return t;
    }
    Node root() { Node n = this; while (n.parent != null) n = n.parent; return n; }

    /** Transform from this node's coordinates to target's. False if the nodes are in different trees. */
    public boolean getTransformTo(Node target, Transform out) {
        if (target == null || out == null) throw new NullPointerException();
        if (root() != target.root()) return false;
        Transform r = Transform.mul(Transform.inverse(target.toRoot()), toRoot());
        out.set(r);
        return true;
    }

    public void setAlignment(Node zReference, int zTargetAxis, Node yReference, int yTargetAxis) {
        if (zTargetAxis < NONE || zTargetAxis > Z_AXIS || yTargetAxis < NONE || yTargetAxis > Z_AXIS) throw new IllegalArgumentException("bad alignment target");
        if (zTargetAxis == yTargetAxis && zTargetAxis != NONE) throw new IllegalArgumentException("z and y targets must differ");
        if (zReference == this || yReference == this) throw new IllegalArgumentException("a node cannot align to itself");
        zRef = zReference; zTarget = zTargetAxis; yRef = yReference; yTarget = yTargetAxis;
    }
    public int getAlignmentTarget(int axis) {
        if (axis == Z_AXIS) return zTarget;
        if (axis == Y_AXIS) return yTarget;
        throw new IllegalArgumentException("axis must be Y_AXIS or Z_AXIS");
    }
    public Node getAlignmentReference(int axis) {
        if (axis == Z_AXIS) return zRef;
        if (axis == Y_AXIS) return yRef;
        throw new IllegalArgumentException("axis must be Y_AXIS or Z_AXIS");
    }

    /**
     * Re-orients this node so that its Z axis (and Y axis) point at the references' origins or axes.
     * Simplified: the Z alignment is applied exactly, the Y alignment then removes any roll around Z.
     */
    public void align(Node reference) {
        if (zTarget == NONE && yTarget == NONE) return;
        Node ref = reference == null ? root() : reference;
        if (ref == this) throw new IllegalArgumentException("cannot align a node to itself");
        float[] z = direction(zTarget, zRef == null ? ref : zRef), y = direction(yTarget, yRef == null ? ref : yRef);
        if (z == null && y == null) return;
        if (z == null) { z = cross(new float[]{1, 0, 0}, y); }
        norm(z);
        if (y == null) y = Math.abs(z[1]) < 0.99f ? new float[]{0, 1, 0} : new float[]{1, 0, 0};
        float[] x = cross(y, z); norm(x);
        y = cross(z, x); norm(y);
        // rotation matrix with columns x, y, z -> quaternion
        float tr = x[0] + y[1] + z[2], w, qx, qy, qz;
        if (tr > 0) { float s = (float) Math.sqrt(tr + 1) * 2; w = s / 4; qx = (y[2] - z[1]) / s; qy = (z[0] - x[2]) / s; qz = (x[1] - y[0]) / s; }
        else if (x[0] > y[1] && x[0] > z[2]) { float s = (float) Math.sqrt(1 + x[0] - y[1] - z[2]) * 2; w = (y[2] - z[1]) / s; qx = s / 4; qy = (y[0] + x[1]) / s; qz = (z[0] + x[2]) / s; }
        else if (y[1] > z[2]) { float s = (float) Math.sqrt(1 + y[1] - x[0] - z[2]) * 2; w = (z[0] - x[2]) / s; qx = (y[0] + x[1]) / s; qy = s / 4; qz = (z[1] + y[2]) / s; }
        else { float s = (float) Math.sqrt(1 + z[2] - x[0] - y[1]) * 2; w = (x[1] - y[0]) / s; qx = (z[0] + x[2]) / s; qy = (z[1] + y[2]) / s; qz = s / 4; }
        this.qx = qx; this.qy = qy; this.qz = qz; this.qw = w;
    }

    /** Direction, in this node's parent coordinates, for an alignment target. */
    private float[] direction(int target, Node ref) {
        if (target == NONE) return null;
        Transform t = new Transform();
        if (!ref.getTransformTo(parent != null ? parent : this, t)) return null;
        float[] origin = t.apply(new float[]{0, 0, 0, 1});
        if (target == ORIGIN) return new float[]{origin[0] - tx, origin[1] - ty, origin[2] - tz};
        float[] ax = t.apply(new float[]{target == X_AXIS ? 1 : 0, target == Y_AXIS ? 1 : 0, target == Z_AXIS ? 1 : 0, 0});
        return new float[]{ax[0], ax[1], ax[2]};
    }
    private static float[] cross(float[] a, float[] b) { return new float[]{a[1]*b[2] - a[2]*b[1], a[2]*b[0] - a[0]*b[2], a[0]*b[1] - a[1]*b[0]}; }
    private static void norm(float[] v) { float n = (float) Math.sqrt(v[0]*v[0] + v[1]*v[1] + v[2]*v[2]); if (n > 0) { v[0] /= n; v[1] /= n; v[2] /= n; } }

    @Override boolean isCompatibleProperty(int p) {
        return p == AnimationTrack.ALPHA || p == AnimationTrack.VISIBILITY || p == AnimationTrack.PICKABILITY || super.isCompatibleProperty(p);
    }
    @Override void updateProperty(int property, float[] v) {
        switch (property) {
            case AnimationTrack.ALPHA: if (v.length >= 1) alpha = Math.max(0, Math.min(1, v[0])); break;
            case AnimationTrack.VISIBILITY: if (v.length >= 1) rendering = v[0] >= 0.5f; break;
            case AnimationTrack.PICKABILITY: if (v.length >= 1) picking = v[0] >= 0.5f; break;
            default: super.updateProperty(property, v);
        }
    }

    @Override void references(java.util.List<Object3D> out) {
        super.references(out);
        if (zRef != null) out.add(zRef);
        if (yRef != null) out.add(yRef);
    }
    @Override void copyInto(Object3D target, Map<Object3D, Object3D> map) {
        super.copyInto(target, map);
        Node n = (Node) target;
        n.rendering = rendering; n.picking = picking; n.alpha = alpha; n.scope = scope;
        n.zTarget = zTarget; n.yTarget = yTarget; n.zRef = zRef; n.yRef = yRef;
    }
}
