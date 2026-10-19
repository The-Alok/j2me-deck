// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 J2ME Deck contributors
package javax.microedition.m3g;

public class PolygonMode extends Object3D {
    public static final int CULL_BACK = 160, CULL_FRONT = 161, CULL_NONE = 162, SHADE_FLAT = 164, SHADE_SMOOTH = 165, WINDING_CCW = 168, WINDING_CW = 169;
    private int culling = CULL_BACK, shading = SHADE_SMOOTH, winding = WINDING_CCW;
    private boolean twoSided, localCamera, perspective;

    public PolygonMode() {}

    public void setCulling(int c) { if (c < CULL_BACK || c > CULL_NONE) throw new IllegalArgumentException("invalid culling mode"); culling = c; }
    public int getCulling() { return culling; }
    public void setShading(int s) { if (s != SHADE_FLAT && s != SHADE_SMOOTH) throw new IllegalArgumentException("invalid shading mode"); shading = s; }
    public int getShading() { return shading; }
    public void setWinding(int w) { if (w != WINDING_CCW && w != WINDING_CW) throw new IllegalArgumentException("invalid winding"); winding = w; }
    public int getWinding() { return winding; }
    public void setTwoSidedLightingEnable(boolean b) { twoSided = b; }
    public boolean isTwoSidedLightingEnabled() { return twoSided; }
    public void setLocalCameraLightingEnable(boolean b) { localCamera = b; }
    public boolean isLocalCameraLightingEnabled() { return localCamera; }
    public void setPerspectiveCorrectionEnable(boolean b) { perspective = b; }
    public boolean isPerspectiveCorrectionEnabled() { return perspective; }
}
