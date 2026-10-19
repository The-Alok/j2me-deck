// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 J2ME Deck contributors
package javax.microedition.m3g;

import java.util.List;

public class Sprite3D extends Node {
    private final boolean scaled;
    private Image2D image;
    private Appearance appearance;
    private int cropX, cropY, cropW, cropH;

    public Sprite3D(boolean scaled, Image2D image, Appearance appearance) {
        this.scaled = scaled;
        setImage(image);
        setAppearance(appearance);
    }
    public boolean isScaled() { return scaled; }
    public void setImage(Image2D img) {
        if (img == null) throw new NullPointerException();
        image = img; cropX = 0; cropY = 0; cropW = img.getWidth(); cropH = img.getHeight();
    }
    public Image2D getImage() { return image; }
    public void setAppearance(Appearance a) { appearance = a; }
    public Appearance getAppearance() { return appearance; }
    public void setCrop(int x, int y, int w, int h) { cropX = x; cropY = y; cropW = w; cropH = h; }
    public int getCropX() { return cropX; }
    public int getCropY() { return cropY; }
    public int getCropWidth() { return cropW; }
    public int getCropHeight() { return cropH; }

    @Override void references(List<Object3D> out) { super.references(out); out.add(image); if (appearance != null) out.add(appearance); }
    @Override boolean isCompatibleProperty(int p) { return p == AnimationTrack.CROP || super.isCompatibleProperty(p); }
    @Override void updateProperty(int p, float[] v) {
        if (p == AnimationTrack.CROP && v.length >= 2) { cropX = Math.round(v[0]); cropY = Math.round(v[1]); if (v.length >= 4) { cropW = Math.round(v[2]); cropH = Math.round(v[3]); } }
        else super.updateProperty(p, v);
    }
    @Override Object3D newInstance() { return new Sprite3D(scaled, image, appearance); }
    @Override void copyInto(Object3D t, java.util.Map<Object3D, Object3D> m) {
        super.copyInto(t, m);
        Sprite3D s = (Sprite3D) t; s.cropX = cropX; s.cropY = cropY; s.cropW = cropW; s.cropH = cropH;
    }
}
