// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 J2ME Deck contributors
package javax.microedition.m3g;

import java.util.List;

public class Appearance extends Object3D {
    private int layer;
    private CompositingMode compositing;
    private Fog fog;
    private PolygonMode polygon;
    private Material material;
    private final Texture2D[] textures = new Texture2D[8];

    public Appearance() {}

    public void setLayer(int l) { if (l < -63 || l > 63) throw new IllegalArgumentException("layer must be in [-63, 63]"); layer = l; }
    public int getLayer() { return layer; }
    public void setCompositingMode(CompositingMode c) { compositing = c; }
    public CompositingMode getCompositingMode() { return compositing; }
    public void setFog(Fog f) { fog = f; }
    public Fog getFog() { return fog; }
    public void setPolygonMode(PolygonMode p) { polygon = p; }
    public PolygonMode getPolygonMode() { return polygon; }
    public void setMaterial(Material m) { material = m; }
    public Material getMaterial() { return material; }
    public void setTexture(int unit, Texture2D t) {
        if (unit < 0 || unit >= textures.length) throw new IndexOutOfBoundsException("texture unit out of range");
        textures[unit] = t;
    }
    public Texture2D getTexture(int unit) {
        if (unit < 0 || unit >= textures.length) throw new IndexOutOfBoundsException("texture unit out of range");
        return textures[unit];
    }

    @Override void references(List<Object3D> out) {
        super.references(out);
        if (compositing != null) out.add(compositing);
        if (fog != null) out.add(fog);
        if (polygon != null) out.add(polygon);
        if (material != null) out.add(material);
        for (Texture2D t : textures) if (t != null) out.add(t);
    }
}
