// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 J2ME Deck contributors
package javax.microedition.m3g;

import java.util.ArrayList;
import java.util.List;

public abstract class Object3D {
    private int userID;
    private Object userObject;
    private final List<AnimationTrack> tracks = new ArrayList<>();

    Object3D() {}

    public final int getUserID() { return userID; }
    public final void setUserID(int id) { userID = id; }
    public final Object getUserObject() { return userObject; }
    public final void setUserObject(Object o) { userObject = o; }

    public final void addAnimationTrack(AnimationTrack t) {
        if (t == null) throw new NullPointerException();
        if (!t.isCompatible(this)) throw new IllegalArgumentException("track is not compatible with this object");
        if (!tracks.contains(t)) {
            tracks.add(t);
            tracks.sort((a, b) -> Integer.compare(a.getTargetProperty(), b.getTargetProperty()));   // spec: ordered by property
        }
    }
    public final void removeAnimationTrack(AnimationTrack t) { tracks.remove(t); }
    public final int getAnimationTrackCount() { return tracks.size(); }
    public final AnimationTrack getAnimationTrack(int i) { return tracks.get(i); }

    /** Applies all animation tracks of this object and everything it references at world time t. Returns the validity. */
    public final int animate(int worldTime) {
        int validity = Integer.MAX_VALUE;
        for (Object3D o : reachable()) validity = Math.min(validity, o.animateSelf(worldTime));
        return validity;
    }

    private int animateSelf(int worldTime) {
        int validity = Integer.MAX_VALUE;
        // each property is the weighted blend of the active tracks that target it
        java.util.Map<Integer, float[]> sum = new java.util.LinkedHashMap<>();
        java.util.Map<Integer, Float> weight = new java.util.HashMap<>();
        for (AnimationTrack t : tracks) {
            AnimationController c = t.getController();
            if (c == null || !c.isActive(worldTime)) continue;
            KeyframeSequence ks = t.getKeyframeSequence();
            float[] v = new float[ks.getComponentCount()];
            float seqTime = c.sequenceTime(worldTime);
            ks.sample(seqTime, v);
            float w = c.getWeight();
            if (w <= 0) continue;
            int prop = t.getTargetProperty();
            float[] acc = sum.get(prop);
            if (acc == null) { sum.put(prop, acc = new float[v.length]); weight.put(prop, 0f); }
            for (int i = 0; i < v.length && i < acc.length; i++) acc[i] += v[i] * w;
            weight.put(prop, weight.get(prop) + w);
            validity = Math.min(validity, c.validity(worldTime, ks));
        }
        for (java.util.Map.Entry<Integer, float[]> e : sum.entrySet()) {
            float[] v = e.getValue();
            float w = weight.get(e.getKey());
            if (e.getKey() != AnimationTrack.ORIENTATION && w != 0 && w != 1) for (int i = 0; i < v.length; i++) v[i] /= w;
            if (e.getKey() == AnimationTrack.ORIENTATION) normalizeQuat(v);
            updateProperty(e.getKey(), v);
        }
        return validity;
    }
    private static void normalizeQuat(float[] q) {
        if (q.length < 4) return;
        float n = (float) Math.sqrt(q[0]*q[0] + q[1]*q[1] + q[2]*q[2] + q[3]*q[3]);
        if (n > 0) for (int i = 0; i < 4; i++) q[i] /= n;
    }

    /** Subclasses apply an animated property value; unknown properties are ignored. */
    void updateProperty(int property, float[] value) {}
    boolean isCompatibleProperty(int property) { return false; }

    /** Objects directly referenced by this one (children, appearance, textures...). */
    void references(List<Object3D> out) {
        for (AnimationTrack t : tracks) { out.add(t); }
    }

    List<Object3D> reachable() {
        List<Object3D> all = new ArrayList<>();
        java.util.Set<Object3D> seen = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
        java.util.ArrayDeque<Object3D> q = new java.util.ArrayDeque<>();
        q.add(this); seen.add(this);
        while (!q.isEmpty()) {
            Object3D o = q.poll();
            all.add(o);
            List<Object3D> refs = new ArrayList<>();
            o.references(refs);
            for (Object3D r : refs) if (r != null && seen.add(r)) q.add(r);
        }
        return all;
    }

    public final int getReferences(Object3D[] refs) {
        List<Object3D> l = new ArrayList<>();
        references(l);
        java.util.LinkedHashSet<Object3D> uniq = new java.util.LinkedHashSet<>();
        for (Object3D o : l) if (o != null) uniq.add(o);
        if (refs != null) {
            if (refs.length < uniq.size()) throw new IllegalArgumentException("array too short");
            int i = 0; for (Object3D o : uniq) refs[i++] = o;
        }
        return uniq.size();
    }

    public final Object3D find(int id) {
        for (Object3D o : reachable()) if (o.userID == id) return o;
        return null;
    }

    /** Deep copy of this object and the scene graph below it (immutable data such as vertex arrays is shared). */
    public Object3D duplicate() {
        java.util.Map<Object3D, Object3D> map = new java.util.IdentityHashMap<>();
        return copyRec(map);
    }

    /** Nodes are deep-copied; every other object (appearance, vertex data, animation tracks) is shared, as the spec says. */
    final Object3D copyRec(java.util.Map<Object3D, Object3D> map) {
        if (!(this instanceof Node)) return this;
        Object3D c = map.get(this);
        if (c != null) return c;
        c = newInstance();
        map.put(this, c);
        c.userID = userID; c.userObject = userObject;
        copyInto(c, map);
        c.tracks.addAll(tracks);
        return c;
    }
    Object3D newInstance() { throw new UnsupportedOperationException(getClass().getName() + " cannot be duplicated"); }
    void copyInto(Object3D target, java.util.Map<Object3D, Object3D> map) {}

    @SuppressWarnings("unchecked") static <T extends Object3D> T dup(T o, java.util.Map<Object3D, Object3D> map) { return o == null ? null : (T) o.copyRec(map); }
}
