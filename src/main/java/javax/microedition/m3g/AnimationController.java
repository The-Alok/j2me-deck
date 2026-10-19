// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 J2ME Deck contributors
package javax.microedition.m3g;

public class AnimationController extends Object3D {
    private float speed = 1, weight = 1, refSeq;
    private int start, end, refWorld;

    public AnimationController() {}

    public void setActiveInterval(int start, int end) {
        if (start > end) throw new IllegalArgumentException("start must not be after end");
        this.start = start; this.end = end;
    }
    public int getActiveIntervalStart() { return start; }
    public int getActiveIntervalEnd() { return end; }
    public void setSpeed(float speed, int worldTime) {
        refSeq = getPosition(worldTime); refWorld = worldTime; this.speed = speed;     // keep the sequence position continuous
    }
    public float getSpeed() { return speed; }
    public void setPosition(float sequenceTime, int worldTime) { refSeq = sequenceTime; refWorld = worldTime; }
    public float getPosition(int worldTime) { return refSeq + speed * (worldTime - refWorld); }
    public void setWeight(float w) { if (w < 0) throw new IllegalArgumentException("weight must be >= 0"); weight = w; }
    public float getWeight() { return weight; }
    public int getRefWorldTime() { return refWorld; }

    /** start == end means "always active". */
    boolean isActive(int worldTime) { return start == end || (worldTime >= start && worldTime < end); }
    float sequenceTime(int worldTime) { return getPosition(worldTime); }
    int validity(int worldTime, KeyframeSequence ks) { return end > start && end > worldTime ? end - worldTime : Integer.MAX_VALUE; }
}
