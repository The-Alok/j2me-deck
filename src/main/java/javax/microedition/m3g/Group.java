// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 J2ME Deck contributors
package javax.microedition.m3g;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class Group extends Node {
    final List<Node> children = new ArrayList<>();

    public Group() {}

    public void addChild(Node child) {
        if (child == null) throw new NullPointerException();
        if (child == this) throw new IllegalArgumentException("a group cannot contain itself");
        if (child instanceof World) throw new IllegalArgumentException("a World cannot be a child");
        if (child.parent != null) throw new IllegalArgumentException("node already has a parent");
        for (Node a = this; a != null; a = a.parent) if (a == child) throw new IllegalArgumentException("would create a cycle");
        child.parent = this;
        children.add(child);
    }
    public void removeChild(Node child) {
        if (child == null) throw new NullPointerException();
        if (children.remove(child)) child.parent = null;
    }
    public int getChildCount() { return children.size(); }
    public Node getChild(int i) { return children.get(i); }

    public boolean pick(int scope, float x, float y, Camera camera, RayIntersection ri) {
        if (camera == null || ri == null) throw new NullPointerException();
        return Picker.pick(this, scope, x, y, camera, ri);
    }
    public boolean pick(int scope, float ox, float oy, float oz, float dx, float dy, float dz, RayIntersection ri) {
        if (ri == null) throw new NullPointerException();
        if (dx == 0 && dy == 0 && dz == 0) throw new IllegalArgumentException("zero ray direction");
        return Picker.pick(this, scope, ox, oy, oz, dx, dy, dz, ri);
    }

    @Override void references(List<Object3D> out) { super.references(out); out.addAll(children); }

    @Override Object3D newInstance() { return new Group(); }
    @Override void copyInto(Object3D target, Map<Object3D, Object3D> map) {
        super.copyInto(target, map);
        Group g = (Group) target;
        for (Node c : children) { Node cc = (Node) c.copyRec(map); cc.parent = g; g.children.add(cc); }
    }
}
