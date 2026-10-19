// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 J2ME Deck contributors
package javax.microedition.m3g;

import java.util.List;
import java.util.Map;

public class Texture2D extends Transformable {
    public static final int FILTER_BASE_LEVEL = 208, FILTER_LINEAR = 209, FILTER_NEAREST = 210;
    public static final int FUNC_ADD = 224, FUNC_BLEND = 225, FUNC_DECAL = 226, FUNC_MODULATE = 227, FUNC_REPLACE = 228;
    public static final int WRAP_CLAMP = 240, WRAP_REPEAT = 241;

    private Image2D image;
    private int blendColor, blending = FUNC_MODULATE, wrapS = WRAP_REPEAT, wrapT = WRAP_REPEAT, levelFilter = FILTER_BASE_LEVEL, imageFilter = FILTER_NEAREST;

    public Texture2D(Image2D image) { setImage(image); }

    public void setImage(Image2D img) {
        if (img == null) throw new NullPointerException();
        int w = img.getWidth(), h = img.getHeight();
        if ((w & (w - 1)) != 0 || (h & (h - 1)) != 0) throw new IllegalArgumentException("texture dimensions must be powers of two");
        image = img;
    }
    public Image2D getImage() { return image; }
    public void setBlendColor(int rgb) { blendColor = rgb & 0xFFFFFF; }
    public int getBlendColor() { return blendColor; }
    public void setBlending(int func) { if (func < FUNC_ADD || func > FUNC_REPLACE) throw new IllegalArgumentException("invalid texture function"); blending = func; }
    public int getBlending() { return blending; }
    public void setWrapping(int s, int t) {
        if ((s != WRAP_CLAMP && s != WRAP_REPEAT) || (t != WRAP_CLAMP && t != WRAP_REPEAT)) throw new IllegalArgumentException("invalid wrap mode");
        wrapS = s; wrapT = t;
    }
    public int getWrappingS() { return wrapS; }
    public int getWrappingT() { return wrapT; }
    public void setFiltering(int level, int img) {
        if ((level != FILTER_BASE_LEVEL && level != FILTER_LINEAR && level != FILTER_NEAREST) || (img != FILTER_LINEAR && img != FILTER_NEAREST)) throw new IllegalArgumentException("invalid filter");
        levelFilter = level; imageFilter = img;
    }
    public int getLevelFilter() { return levelFilter; }
    public int getImageFilter() { return imageFilter; }

    @Override void references(List<Object3D> out) { super.references(out); if (image != null) out.add(image); }
    @Override boolean isCompatibleProperty(int p) { return p == AnimationTrack.COLOR || super.isCompatibleProperty(p); }
    @Override void updateProperty(int p, float[] v) {
        if (p == AnimationTrack.COLOR && v.length >= 3) blendColor = Light.pack(v[0], v[1], v[2]); else super.updateProperty(p, v);
    }
    @Override Object3D newInstance() { return new Texture2D(image); }
    @Override void copyInto(Object3D t, Map<Object3D, Object3D> m) {
        super.copyInto(t, m);
        Texture2D o = (Texture2D) t; o.blendColor = blendColor; o.blending = blending; o.wrapS = wrapS; o.wrapT = wrapT; o.levelFilter = levelFilter; o.imageFilter = imageFilter;
    }
}
