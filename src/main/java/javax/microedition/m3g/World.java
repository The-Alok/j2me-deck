// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 J2ME Deck contributors
package javax.microedition.m3g;

import java.util.Map;

public class World extends Group {
    private Camera camera;
    private Background background;

    public World() {}

    public Camera getActiveCamera() { return camera; }
    public void setActiveCamera(Camera c) {
        if (c != null && c.root() != this) throw new IllegalArgumentException("camera is not in this world");
        camera = c;
    }
    public Background getBackground() { return background; }
    public void setBackground(Background b) { background = b; }

    @Override void references(java.util.List<Object3D> out) {
        super.references(out);
        if (camera != null) out.add(camera);
        if (background != null) out.add(background);
    }
    @Override Object3D newInstance() { return new World(); }
    @Override void copyInto(Object3D target, Map<Object3D, Object3D> map) {
        super.copyInto(target, map);
        World w = (World) target;
        w.camera = camera == null ? null : (Camera) camera.copyRec(map);
        w.background = background;
    }
}
