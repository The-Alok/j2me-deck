// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 J2ME Deck contributors
package javax.microedition.m3g;

public class AnimationTrack extends Object3D {
    public static final int ALPHA = 256, AMBIENT_COLOR = 257, COLOR = 258, CROP = 259, DENSITY = 260, DIFFUSE_COLOR = 261, EMISSIVE_COLOR = 262,
            FAR_DISTANCE = 263, FIELD_OF_VIEW = 264, INTENSITY = 265, MORPH_WEIGHTS = 266, NEAR_DISTANCE = 267, ORIENTATION = 268,
            PICKABILITY = 269, SCALE = 270, SHININESS = 271, SPECULAR_COLOR = 272, SPOT_ANGLE = 273, SPOT_EXPONENT = 274,
            TRANSLATION = 275, VISIBILITY = 276;

    private final KeyframeSequence sequence;
    private final int property;
    private AnimationController controller;

    public AnimationTrack(KeyframeSequence sequence, int property) {
        if (sequence == null) throw new NullPointerException();
        if (property < ALPHA || property > VISIBILITY) throw new IllegalArgumentException("invalid target property");
        int c = sequence.getComponentCount();
        boolean ok;
        switch (property) {
            case ALPHA: case DENSITY: case FAR_DISTANCE: case FIELD_OF_VIEW: case INTENSITY: case NEAR_DISTANCE: case PICKABILITY:
            case SHININESS: case SPOT_ANGLE: case SPOT_EXPONENT: case VISIBILITY: ok = c == 1; break;
            case AMBIENT_COLOR: case COLOR: case EMISSIVE_COLOR: case SPECULAR_COLOR: case TRANSLATION: ok = c == 3; break;
            case DIFFUSE_COLOR: ok = c == 3 || c == 4; break;
            case CROP: ok = c == 2 || c == 4; break;
            case ORIENTATION: ok = c == 4; break;
            case SCALE: ok = c == 1 || c == 3; break;
            default: ok = c >= 1;
        }
        if (!ok) throw new IllegalArgumentException("keyframe component count does not fit the target property");
        this.sequence = sequence; this.property = property;
    }

    public KeyframeSequence getKeyframeSequence() { return sequence; }
    public int getTargetProperty() { return property; }
    public void setController(AnimationController c) { controller = c; }
    public AnimationController getController() { return controller; }

    boolean isCompatible(Object3D target) { return target.isCompatibleProperty(property); }

    @Override void references(java.util.List<Object3D> out) { super.references(out); out.add(sequence); if (controller != null) out.add(controller); }
}
