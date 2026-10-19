// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 J2ME Deck contributors
package javax.microedition.m3g;

import java.util.List;

public class Background extends Object3D {
    public static final int BORDER = 32, REPEAT = 33;
    private int color;                               // 0xAARRGGBB
    private Image2D image;
    private int modeX = BORDER, modeY = BORDER, cropX, cropY, cropW, cropH;
    private boolean colorClear = true, depthClear = true;

    public Background() {}

    public void setColor(int argb) { color = argb; }
    public int getColor() { return color; }
    public void setImage(Image2D img) {
        image = img;
        if (img != null) { cropX = 0; cropY = 0; cropW = img.getWidth(); cropH = img.getHeight(); }
    }
    public Image2D getImage() { return image; }
    public void setImageMode(int x, int y) {
        if ((x != BORDER && x != REPEAT) || (y != BORDER && y != REPEAT)) throw new IllegalArgumentException("invalid image mode");
        modeX = x; modeY = y;
    }
    public int getImageModeX() { return modeX; }
    public int getImageModeY() { return modeY; }
    public void setCrop(int x, int y, int w, int h) {
        if (w < 0 || h < 0) throw new IllegalArgumentException("crop size must be >= 0");
        cropX = x; cropY = y; cropW = w; cropH = h;
    }
    public int getCropX() { return cropX; }
    public int getCropY() { return cropY; }
    public int getCropWidth() { return cropW; }
    public int getCropHeight() { return cropH; }
    public void setColorClearEnable(boolean b) { colorClear = b; }
    public boolean isColorClearEnabled() { return colorClear; }
    public void setDepthClearEnable(boolean b) { depthClear = b; }
    public boolean isDepthClearEnabled() { return depthClear; }

    @Override void references(List<Object3D> out) { super.references(out); if (image != null) out.add(image); }
    @Override boolean isCompatibleProperty(int p) { return p == AnimationTrack.COLOR || p == AnimationTrack.CROP; }
    @Override void updateProperty(int p, float[] v) {
        if (p == AnimationTrack.COLOR && v.length >= 3) color = (color & 0xFF000000) | Light.pack(v[0], v[1], v[2]);
        else if (p == AnimationTrack.CROP && v.length >= 2) { cropX = Math.round(v[0]); cropY = Math.round(v[1]); if (v.length >= 4) { cropW = Math.round(v[2]); cropH = Math.round(v[3]); } }
        else super.updateProperty(p, v);
    }
}
