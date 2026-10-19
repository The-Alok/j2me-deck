// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 J2ME Deck contributors
package javax.microedition.m3g;

import java.util.ArrayList;
import java.util.List;

/**
 * Software rasteriser behind Graphics3D. Colour is held as 0xAARRGGBB in a framebuffer the size of the target;
 * depth is a float buffer. The pipeline per submesh is: prepare vertex data (skinning / morphing) -> eye space ->
 * per-vertex lighting and fog -> clip space -> near/far clipping -> viewport -> cull -> perspective-correct raster.
 */
final class SoftRenderer {
    static final int ATTR = 13;      // clip xyzw, rgba, u0 v0, u1 v1, fog
    static final int X = 0, Y = 1, Z = 2, W = 3, R = 4, G = 5, B = 6, A = 7, U0 = 8, V0 = 9, U1 = 10, V1 = 11, F = 12;

    // ---- target
    int tw, th;
    int[] color;
    float[] depth;
    boolean depthBuffer;
    int clipX, clipY, clipW, clipH;           // drawable area in target coordinates
    int vx, vy, vw, vh;                       // viewport in target coordinates
    float dnear = 0, dfar = 1;

    // ---- camera and lights
    Transform view = new Transform(), proj = new Transform();
    final List<LightState> lights = new ArrayList<>();

    static final class LightState { Light light; Transform toWorld; }

    void allocate(int w, int h, boolean withDepth) {
        if (color == null || color.length != w * h) { color = new int[w * h]; depth = new float[w * h]; }
        tw = w; th = h; depthBuffer = withDepth;
    }

    // ================================================================== clear
    void clear(Background bg) {
        int x0 = Math.max(vx, clipX), y0 = Math.max(vy, clipY), x1 = Math.min(vx + vw, clipX + clipW), y1 = Math.min(vy + vh, clipY + clipH);
        boolean colorClear = bg == null || bg.isColorClearEnabled(), depthClear = bg == null || bg.isDepthClearEnabled();
        int c = bg == null ? 0 : bg.getColor() | 0xFF000000;
        Image2D img = bg == null ? null : bg.getImage();
        for (int y = y0; y < y1; y++) {
            for (int x = x0; x < x1; x++) {
                int i = y * tw + x;
                if (colorClear) {
                    int px = c;
                    if (img != null) {
                        int cw = bg.getCropWidth(), ch = bg.getCropHeight();
                        int rx = x - vx, ry = y - vy;
                        boolean inside = true;
                        int ux, uy;
                        if (bg.getImageModeX() == Background.REPEAT && cw > 0) ux = Math.floorMod(rx, cw); else { ux = rx; if (rx < 0 || rx >= cw) inside = false; }
                        if (bg.getImageModeY() == Background.REPEAT && ch > 0) uy = Math.floorMod(ry, ch); else { uy = ry; if (ry < 0 || ry >= ch) inside = false; }
                        if (inside) {
                            int sx = bg.getCropX() + ux, sy = bg.getCropY() + uy;
                            if (sx >= 0 && sy >= 0 && sx < img.getWidth() && sy < img.getHeight()) {
                                int t = img.argb()[sy * img.getWidth() + sx];
                                int ta = t >>> 24;
                                px = ta == 255 ? t : blend(t, c, ta);
                            }
                        }
                    }
                    color[i] = px | 0xFF000000;
                }
                if (depthClear) depth[i] = 1f;
            }
        }
    }

    private static int blend(int src, int dst, int a) {
        int ia = 255 - a;
        int r = (((src >> 16) & 255) * a + ((dst >> 16) & 255) * ia) / 255, g = (((src >> 8) & 255) * a + ((dst >> 8) & 255) * ia) / 255, b = ((src & 255) * a + (dst & 255) * ia) / 255;
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    // ================================================================== mesh preparation
    /** Per-vertex model-space data after applying skinning and morphing. */
    static final class MeshData {
        float[] pos, nor;
        int[] col;
        float[][] uv = new float[2][];
    }

    MeshData prepare(Mesh mesh, VertexBuffer vb) {
        MeshData d = new MeshData();
        int n = vb.getVertexCount();
        d.pos = new float[n * 3];
        d.nor = vb.getNormals() == null ? null : new float[n * 3];
        d.col = new int[n];
        float[] sb = new float[4];
        VertexArray pa = vb.getPositions(sb);
        float scale = sb[0];
        for (int i = 0; i < n; i++) for (int c = 0; c < 3; c++) d.pos[i * 3 + c] = pa.value(i, c) * scale + sb[1 + c];
        VertexArray na = vb.getNormals();
        if (na != null) for (int i = 0; i < n; i++) for (int c = 0; c < 3; c++) d.nor[i * 3 + c] = na.value(i, c);
        VertexArray ca = vb.getColors();
        for (int i = 0; i < n; i++) {
            if (ca == null) d.col[i] = vb.getDefaultColor();
            else {
                int r = (int) ca.value(i, 0) & 255, g = (int) ca.value(i, 1) & 255, b = (int) ca.value(i, 2) & 255;
                int a = ca.getComponentCount() == 4 ? (int) ca.value(i, 3) & 255 : 255;
                d.col[i] = (a << 24) | (r << 16) | (g << 8) | b;
            }
        }
        for (int u = 0; u < 2; u++) {
            VertexArray ta = vb.tex(u);
            if (ta == null) continue;
            d.uv[u] = new float[n * 2];
            float s = vb.texScale(u); float[] bi = vb.texBias(u);
            for (int i = 0; i < n; i++) { d.uv[u][i * 2] = ta.value(i, 0) * s + bi[0]; d.uv[u][i * 2 + 1] = ta.value(i, 1) * s + bi[1]; }
        }
        if (mesh instanceof MorphingMesh) morph((MorphingMesh) mesh, d, n);
        if (mesh instanceof SkinnedMesh) skin((SkinnedMesh) mesh, d, n);
        return d;
    }

    private void morph(MorphingMesh mm, MeshData d, int n) {
        float[] w = mm.weights();
        for (int t = 0; t < mm.getMorphTargetCount(); t++) {
            float wt = w[t];
            if (wt == 0) continue;
            VertexBuffer tb = mm.getMorphTarget(t);
            float[] sb = new float[4];
            VertexArray pa = tb.getPositions(sb);
            for (int i = 0; i < n; i++) for (int c = 0; c < 3; c++) d.pos[i * 3 + c] += wt * ((pa.value(i, c) * sb[0] + sb[1 + c]) - d.pos[i * 3 + c] + 0);   // base + w*(target - base)
            VertexArray na = tb.getNormals();
            if (na != null && d.nor != null) for (int i = 0; i < n; i++) for (int c = 0; c < 3; c++) d.nor[i * 3 + c] += wt * (na.value(i, c) - d.nor[i * 3 + c]);
        }
    }

    private void skin(SkinnedMesh sm, MeshData d, int n) {
        if (sm.influences.isEmpty()) return;
        float[] totalW = new float[n];
        float[] px = new float[n], py = new float[n], pz = new float[n], nx = new float[n], ny = new float[n], nz = new float[n];
        for (SkinnedMesh.Influence in : sm.influences) {
            Transform bone = new Transform();
            if (!in.bone.getTransformTo(sm, bone)) continue;
            Transform m = Transform.mul(bone, in.bind);
            for (int v = in.first; v < in.first + in.count; v++) {
                float w = in.weight;
                float[] p = m.apply(new float[]{d.pos[v * 3], d.pos[v * 3 + 1], d.pos[v * 3 + 2], 1});
                px[v] += w * p[0]; py[v] += w * p[1]; pz[v] += w * p[2]; totalW[v] += w;
                if (d.nor != null) {
                    float[] q = m.apply(new float[]{d.nor[v * 3], d.nor[v * 3 + 1], d.nor[v * 3 + 2], 0});
                    nx[v] += w * q[0]; ny[v] += w * q[1]; nz[v] += w * q[2];
                }
            }
        }
        for (int v = 0; v < n; v++) {
            if (totalW[v] == 0) continue;
            d.pos[v * 3] = px[v] / totalW[v]; d.pos[v * 3 + 1] = py[v] / totalW[v]; d.pos[v * 3 + 2] = pz[v] / totalW[v];
            if (d.nor != null) { d.nor[v * 3] = nx[v] / totalW[v]; d.nor[v * 3 + 1] = ny[v] / totalW[v]; d.nor[v * 3 + 2] = nz[v] / totalW[v]; }
        }
    }

    // ================================================================== draw
    void drawSubmesh(MeshData d, IndexBuffer ib, Appearance ap, Transform modelToWorld, float alphaFactor) {
        if (color == null) return;
        PolygonMode pm = ap.getPolygonMode();
        CompositingMode cm = ap.getCompositingMode();
        Material mat = ap.getMaterial();
        Fog fog = ap.getFog();
        int culling = pm == null ? PolygonMode.CULL_BACK : pm.getCulling();
        boolean ccwFront = pm == null || pm.getWinding() == PolygonMode.WINDING_CCW;
        boolean flat = pm != null && pm.getShading() == PolygonMode.SHADE_FLAT;
        boolean twoSided = pm != null && pm.isTwoSidedLightingEnabled();
        boolean localCam = pm != null && pm.isLocalCameraLightingEnabled();

        Transform mv = Transform.mul(view, modelToWorld);
        Transform clip = Transform.mul(proj, mv);
        // normal matrix: inverse transpose of the upper 3x3 of mv
        Transform nm = new Transform(mv); nm.m[3] = nm.m[7] = nm.m[11] = 0; nm.m[12] = nm.m[13] = nm.m[14] = 0; nm.m[15] = 1;
        boolean haveNm = true;
        try { nm.invert(); nm.transpose(); } catch (ArithmeticException e) { haveNm = false; }

        int n = d.pos.length / 3;
        float[][] pv = new float[n][ATTR];
        Texture2D[] tex = {ap.getTexture(0), ap.getTexture(1)};
        Transform[] texM = new Transform[2];
        for (int u = 0; u < 2; u++) if (tex[u] != null && d.uv[u] != null) texM[u] = tex[u].composite();
        boolean lit = mat != null;
        float[] eye = new float[4], nrm = new float[4];
        for (int i = 0; i < n; i++) {
            float[] v = pv[i];
            float[] cp = clip.apply(new float[]{d.pos[i * 3], d.pos[i * 3 + 1], d.pos[i * 3 + 2], 1});
            v[X] = cp[0]; v[Y] = cp[1]; v[Z] = cp[2]; v[W] = cp[3];
            float[] e = mv.apply(new float[]{d.pos[i * 3], d.pos[i * 3 + 1], d.pos[i * 3 + 2], 1});
            int vc = d.col[i];
            float vAlpha = ((vc >>> 24) & 255) / 255f, va = vAlpha * alphaFactor;
            if (lit) {
                float[] nn = d.nor == null ? new float[]{0, 0, 1} : haveNm ? nm.apply(new float[]{d.nor[i * 3], d.nor[i * 3 + 1], d.nor[i * 3 + 2], 0}) : new float[]{0, 0, 1};
                lightVertex(v, e, nn, mat, vc, vAlpha, alphaFactor, localCam, twoSided);
            } else {
                v[R] = ((vc >> 16) & 255) / 255f; v[G] = ((vc >> 8) & 255) / 255f; v[B] = (vc & 255) / 255f; v[A] = va;
            }
            for (int u = 0; u < 2; u++) {
                if (texM[u] == null) continue;
                float[] t = texM[u].apply(new float[]{d.uv[u][i * 2], d.uv[u][i * 2 + 1], 0, 1});
                v[u == 0 ? U0 : U1] = t[0]; v[u == 0 ? V0 : V1] = t[1];
            }
            if (fog != null) {
                float dist = Math.abs(e[2]);
                float f;
                if (fog.getMode() == Fog.LINEAR) f = fog.getFarDistance() == fog.getNearDistance() ? 1 : (fog.getFarDistance() - dist) / (fog.getFarDistance() - fog.getNearDistance());
                else f = (float) Math.exp(-fog.getDensity() * dist);
                v[F] = Math.max(0, Math.min(1, f));
            } else v[F] = 1;
        }

        Raster r = new Raster(ap, cm, fog, tex);
        int[] tris = ib.triangles();
        float[][] tri = new float[3][];
        for (int t = 0; t + 2 < tris.length; t += 3) {
            if (tris[t] >= n || tris[t + 1] >= n || tris[t + 2] >= n) continue;
            tri[0] = pv[tris[t]]; tri[1] = pv[tris[t + 1]]; tri[2] = pv[tris[t + 2]];
            if (flat) {
                float[][] f = {tri[0].clone(), tri[1].clone(), tri[2].clone()};
                for (int k = 1; k < 3; k++) { f[k][R] = f[0][R]; f[k][G] = f[0][G]; f[k][B] = f[0][B]; f[k][A] = f[0][A]; }
                clipAndDraw(f[0], f[1], f[2], r, culling, ccwFront);
            } else clipAndDraw(tri[0], tri[1], tri[2], r, culling, ccwFront);
        }
    }

    // ================================================================== lighting
    private void lightVertex(float[] out, float[] eyePos, float[] n, Material mat, int vertexColor, float vertexAlpha, float alphaFactor, boolean localCam, boolean twoSided) {
        float nl = (float) Math.sqrt(n[0] * n[0] + n[1] * n[1] + n[2] * n[2]);
        if (nl > 0) { n[0] /= nl; n[1] /= nl; n[2] /= nl; }
        float vx0, vy0, vz0;
        if (localCam) { float l = (float) Math.sqrt(eyePos[0] * eyePos[0] + eyePos[1] * eyePos[1] + eyePos[2] * eyePos[2]); vx0 = -eyePos[0] / (l == 0 ? 1 : l); vy0 = -eyePos[1] / (l == 0 ? 1 : l); vz0 = -eyePos[2] / (l == 0 ? 1 : l); }
        else { vx0 = 0; vy0 = 0; vz0 = 1; }
        if (twoSided && n[0] * vx0 + n[1] * vy0 + n[2] * vz0 < 0) { n[0] = -n[0]; n[1] = -n[1]; n[2] = -n[2]; }

        boolean track = mat.isVertexColorTrackingEnabled();
        int amb = mat.getColor(Material.AMBIENT), dif = mat.getColor(Material.DIFFUSE), emi = mat.getColor(Material.EMISSIVE), spe = mat.getColor(Material.SPECULAR);
        float ar, ag, ab, dr, dg, db, alpha;
        if (track) { ar = dr = ((vertexColor >> 16) & 255) / 255f; ag = dg = ((vertexColor >> 8) & 255) / 255f; ab = db = (vertexColor & 255) / 255f; alpha = vertexAlpha * alphaFactor; }
        else { ar = ((amb >> 16) & 255) / 255f; ag = ((amb >> 8) & 255) / 255f; ab = (amb & 255) / 255f; dr = ((dif >> 16) & 255) / 255f; dg = ((dif >> 8) & 255) / 255f; db = (dif & 255) / 255f; alpha = ((dif >>> 24) & 255) / 255f * alphaFactor; }
        float sr = ((spe >> 16) & 255) / 255f, sg = ((spe >> 8) & 255) / 255f, sb = (spe & 255) / 255f;
        float r = ((emi >> 16) & 255) / 255f, g = ((emi >> 8) & 255) / 255f, b = (emi & 255) / 255f;
        float shin = mat.getShininess();

        for (LightState ls : lights) {
            Light L = ls.light;
            Transform lt = Transform.mul(view, ls.toWorld);
            int lc = L.getColor();
            float cr = ((lc >> 16) & 255) / 255f * L.getIntensity(), cg = ((lc >> 8) & 255) / 255f * L.getIntensity(), cb = (lc & 255) / 255f * L.getIntensity();
            if (L.getMode() == Light.AMBIENT) { r += ar * cr; g += ag * cg; b += ab * cb; continue; }
            float lx, ly, lz, att = 1;
            if (L.getMode() == Light.DIRECTIONAL) {
                float[] z = lt.apply(new float[]{0, 0, 1, 0});
                float l = (float) Math.sqrt(z[0] * z[0] + z[1] * z[1] + z[2] * z[2]);
                lx = z[0] / l; ly = z[1] / l; lz = z[2] / l;
            } else {
                float[] p = lt.apply(new float[]{0, 0, 0, 1});
                lx = p[0] - eyePos[0]; ly = p[1] - eyePos[1]; lz = p[2] - eyePos[2];
                float dist = (float) Math.sqrt(lx * lx + ly * ly + lz * lz);
                if (dist > 0) { lx /= dist; ly /= dist; lz /= dist; }
                att = 1f / (L.getConstantAttenuation() + L.getLinearAttenuation() * dist + L.getQuadraticAttenuation() * dist * dist);
                if (L.getMode() == Light.SPOT) {
                    float[] sd = lt.apply(new float[]{0, 0, -1, 0});
                    float sl = (float) Math.sqrt(sd[0] * sd[0] + sd[1] * sd[1] + sd[2] * sd[2]);
                    float cosA = (-lx * sd[0] - ly * sd[1] - lz * sd[2]) / sl;
                    if (cosA < Math.cos(Math.toRadians(L.getSpotAngle()))) att = 0; else att *= (float) Math.pow(Math.max(cosA, 0), L.getSpotExponent());
                }
            }
            float ndl = n[0] * lx + n[1] * ly + n[2] * lz;
            if (ndl <= 0 || att == 0) continue;
            r += dr * cr * ndl * att; g += dg * cg * ndl * att; b += db * cb * ndl * att;
            if (shin > 0 && (sr > 0 || sg > 0 || sb > 0)) {
                float hx = lx + vx0, hy = ly + vy0, hz = lz + vz0;
                float hl = (float) Math.sqrt(hx * hx + hy * hy + hz * hz);
                if (hl > 0) {
                    float ndh = (n[0] * hx + n[1] * hy + n[2] * hz) / hl;
                    if (ndh > 0) { float sp = (float) Math.pow(ndh, shin) * att; r += sr * cr * sp; g += sg * cg * sp; b += sb * cb * sp; }
                }
            }
        }
        out[R] = Math.max(0, Math.min(1, r)); out[G] = Math.max(0, Math.min(1, g)); out[B] = Math.max(0, Math.min(1, b)); out[A] = Math.max(0, Math.min(1, alpha));
    }

    // ================================================================== clipping
    private void clipAndDraw(float[] a, float[] b, float[] c, Raster r, int culling, boolean ccwFront) {
        // trivial reject: all vertices outside the same frustum plane
        if ((a[X] > a[W] && b[X] > b[W] && c[X] > c[W]) || (a[X] < -a[W] && b[X] < -b[W] && c[X] < -c[W])
                || (a[Y] > a[W] && b[Y] > b[W] && c[Y] > c[W]) || (a[Y] < -a[W] && b[Y] < -b[W] && c[Y] < -c[W])
                || (a[Z] > a[W] && b[Z] > b[W] && c[Z] > c[W]) || (a[Z] < -a[W] && b[Z] < -b[W] && c[Z] < -c[W])) return;
        List<float[]> poly = new ArrayList<>(6);
        poly.add(a); poly.add(b); poly.add(c);
        poly = clipPlane(poly, Z, +1);      // near:  z + w >= 0
        poly = clipPlane(poly, Z, -1);      // far:  -z + w >= 0
        if (poly.size() < 3) return;
        for (int i = 1; i + 1 < poly.size(); i++) rasterize(poly.get(0), poly.get(i), poly.get(i + 1), r, culling, ccwFront);
    }

    /** Clips against w + sign*coord >= 0. */
    private static List<float[]> clipPlane(List<float[]> in, int coord, int sign) {
        List<float[]> out = new ArrayList<>(in.size() + 2);
        for (int i = 0; i < in.size(); i++) {
            float[] p = in.get(i), q = in.get((i + 1) % in.size());
            float dp = p[W] + sign * p[coord], dq = q[W] + sign * q[coord];
            if (dp >= 0) out.add(p);
            if ((dp >= 0) != (dq >= 0)) {
                float t = dp / (dp - dq);
                float[] m = new float[ATTR];
                for (int k = 0; k < ATTR; k++) m[k] = p[k] + (q[k] - p[k]) * t;
                out.add(m);
            }
        }
        return out;
    }

    // ================================================================== raster state per appearance
    static final class Tex {
        int[] argb; int w, h; boolean alpha, linear; int wrapS, wrapT, func; float br, bg, bb;
    }

    final class Raster {
        final Tex[] tex = new Tex[2];
        final boolean depthTest, depthWrite, colorWrite;
        final int blending;
        final float alphaThreshold;
        final Fog fog; final float fr, fg, fb;
        final float depthOffset;

        Raster(Appearance ap, CompositingMode cm, Fog fog, Texture2D[] t) {
            depthTest = depthBuffer && (cm == null || cm.isDepthTestEnabled());
            depthWrite = depthBuffer && (cm == null || cm.isDepthWriteEnabled());
            colorWrite = cm == null || cm.isColorWriteEnabled();
            blending = cm == null ? CompositingMode.REPLACE : cm.getBlending();
            alphaThreshold = cm == null ? 0 : cm.getAlphaThreshold();
            depthOffset = cm == null ? 0 : cm.getDepthOffsetUnits() * 1e-6f;
            this.fog = fog;
            int fc = fog == null ? 0 : fog.getColor();
            fr = ((fc >> 16) & 255) / 255f; fg = ((fc >> 8) & 255) / 255f; fb = (fc & 255) / 255f;
            for (int u = 0; u < 2; u++) {
                if (t[u] == null) continue;
                Tex x = new Tex();
                Image2D im = t[u].getImage();
                x.argb = im.argb(); x.w = im.getWidth(); x.h = im.getHeight(); x.alpha = im.hasAlpha();
                x.linear = t[u].getImageFilter() == Texture2D.FILTER_LINEAR;
                x.wrapS = t[u].getWrappingS(); x.wrapT = t[u].getWrappingT(); x.func = t[u].getBlending();
                int bc = t[u].getBlendColor();
                x.br = ((bc >> 16) & 255) / 255f; x.bg = ((bc >> 8) & 255) / 255f; x.bb = (bc & 255) / 255f;
                tex[u] = x;
            }
        }
    }

    // ================================================================== rasterisation
    private void rasterize(float[] a, float[] b, float[] c, Raster r, int culling, boolean ccwFront) {
        float[][] v = {a, b, c};
        float[] sx = new float[3], sy = new float[3], sz = new float[3], iw = new float[3];
        for (int i = 0; i < 3; i++) {
            float w = v[i][W];
            if (w <= 0) return;
            float inv = 1f / w;
            iw[i] = inv;
            sx[i] = vx + (v[i][X] * inv * 0.5f + 0.5f) * vw;
            sy[i] = vy + (1 - (v[i][Y] * inv * 0.5f + 0.5f)) * vh;
            sz[i] = dnear + (v[i][Z] * inv * 0.5f + 0.5f) * (dfar - dnear);
        }
        float area = (sx[1] - sx[0]) * (sy[2] - sy[0]) - (sx[2] - sx[0]) * (sy[1] - sy[0]);
        if (area == 0) return;
        boolean front = ccwFront ? area < 0 : area > 0;
        if (culling == PolygonMode.CULL_BACK && !front) return;
        if (culling == PolygonMode.CULL_FRONT && front) return;

        int minX = Math.max(Math.max(clipX, vx), (int) Math.floor(Math.min(sx[0], Math.min(sx[1], sx[2]))));
        int maxX = Math.min(Math.min(clipX + clipW, vx + vw), (int) Math.ceil(Math.max(sx[0], Math.max(sx[1], sx[2]))));
        int minY = Math.max(Math.max(clipY, vy), (int) Math.floor(Math.min(sy[0], Math.min(sy[1], sy[2]))));
        int maxY = Math.min(Math.min(clipY + clipH, vy + vh), (int) Math.ceil(Math.max(sy[0], Math.max(sy[1], sy[2]))));
        if (minX >= maxX || minY >= maxY) return;

        float invArea = 1f / area;
        // attributes pre-divided by w for perspective-correct interpolation
        float[][] pa = new float[3][9];
        for (int i = 0; i < 3; i++) { for (int k = 0; k < 4; k++) pa[i][k] = v[i][R + k] * iw[i]; pa[i][4] = v[i][U0] * iw[i]; pa[i][5] = v[i][V0] * iw[i]; pa[i][6] = v[i][U1] * iw[i]; pa[i][7] = v[i][V1] * iw[i]; pa[i][8] = v[i][F] * iw[i]; }
        float[] px = new float[9];
        float[] t = new float[4];

        for (int y = minY; y < maxY; y++) {
            float py = y + 0.5f;
            for (int x = minX; x < maxX; x++) {
                float pxx = x + 0.5f;
                float e0 = ((sx[1] - pxx) * (sy[2] - py) - (sx[2] - pxx) * (sy[1] - py)) * invArea;
                float e1 = ((sx[2] - pxx) * (sy[0] - py) - (sx[0] - pxx) * (sy[2] - py)) * invArea;
                float e2 = 1f - e0 - e1;
                if (e0 < 0 || e1 < 0 || e2 < 0) continue;
                int idx = y * tw + x;
                float z = e0 * sz[0] + e1 * sz[1] + e2 * sz[2] + r.depthOffset;
                if (r.depthTest && z > depth[idx]) continue;
                float w = 1f / (e0 * iw[0] + e1 * iw[1] + e2 * iw[2]);
                for (int k = 0; k < 9; k++) px[k] = (e0 * pa[0][k] + e1 * pa[1][k] + e2 * pa[2][k]) * w;
                float cr = px[0], cg = px[1], cb = px[2], ca = px[3];
                for (int u = 0; u < 2; u++) {
                    Tex tx = r.tex[u];
                    if (tx == null) continue;
                    sample(tx, px[4 + u * 2], px[5 + u * 2], t);
                    float tr = t[0], tg = t[1], tb = t[2], ta = t[3];
                    switch (tx.func) {
                        case Texture2D.FUNC_REPLACE: cr = tr; cg = tg; cb = tb; if (tx.alpha) ca = ta; break;
                        case Texture2D.FUNC_MODULATE: cr *= tr; cg *= tg; cb *= tb; ca *= ta; break;
                        case Texture2D.FUNC_DECAL: if (tx.alpha) { cr = cr * (1 - ta) + tr * ta; cg = cg * (1 - ta) + tg * ta; cb = cb * (1 - ta) + tb * ta; } else { cr = tr; cg = tg; cb = tb; } break;
                        case Texture2D.FUNC_BLEND: cr = cr * (1 - tr) + tx.br * tr; cg = cg * (1 - tg) + tx.bg * tg; cb = cb * (1 - tb) + tx.bb * tb; ca *= ta; break;
                        case Texture2D.FUNC_ADD: cr = Math.min(1, cr + tr); cg = Math.min(1, cg + tg); cb = Math.min(1, cb + tb); ca *= ta; break;
                        default:
                    }
                }
                if (r.fog != null) { float f = px[8]; if (f < 1) { cr = cr * f + r.fr * (1 - f); cg = cg * f + r.fg * (1 - f); cb = cb * f + r.fb * (1 - f); } }
                if (ca < r.alphaThreshold) continue;
                if (r.depthWrite) depth[idx] = z;
                if (!r.colorWrite) continue;
                int dst = color[idx];
                float dr = ((dst >> 16) & 255) / 255f, dg = ((dst >> 8) & 255) / 255f, db = (dst & 255) / 255f;
                float orr, og, ob;
                switch (r.blending) {
                    case CompositingMode.ALPHA: orr = cr * ca + dr * (1 - ca); og = cg * ca + dg * (1 - ca); ob = cb * ca + db * (1 - ca); break;
                    case CompositingMode.ALPHA_ADD: orr = cr * ca + dr; og = cg * ca + dg; ob = cb * ca + db; break;
                    case CompositingMode.MODULATE: orr = cr * dr; og = cg * dg; ob = cb * db; break;
                    case CompositingMode.MODULATE_X2: orr = 2 * cr * dr; og = 2 * cg * dg; ob = 2 * cb * db; break;
                    default: orr = cr; og = cg; ob = cb;
                }
                color[idx] = 0xFF000000 | (to8(orr) << 16) | (to8(og) << 8) | to8(ob);
            }
        }
    }

    private static int to8(float f) { int i = (int) (f * 255f + 0.5f); return i < 0 ? 0 : i > 255 ? 255 : i; }

    private static void sample(Tex t, float u, float v, float[] out) {
        if (!t.linear) {
            int x = wrap((int) Math.floor(u * t.w), t.w, t.wrapS), y = wrap((int) Math.floor(v * t.h), t.h, t.wrapT);
            unpack(t.argb[y * t.w + x], out);
            return;
        }
        float fx = u * t.w - 0.5f, fy = v * t.h - 0.5f;
        int x0 = (int) Math.floor(fx), y0 = (int) Math.floor(fy);
        float ax = fx - x0, ay = fy - y0;
        float[] c = new float[4]; float r = 0, g = 0, b = 0, a = 0;
        for (int j = 0; j < 2; j++) for (int i = 0; i < 2; i++) {
            float wgt = (i == 0 ? 1 - ax : ax) * (j == 0 ? 1 - ay : ay);
            unpack(t.argb[wrap(y0 + j, t.h, t.wrapT) * t.w + wrap(x0 + i, t.w, t.wrapS)], c);
            r += c[0] * wgt; g += c[1] * wgt; b += c[2] * wgt; a += c[3] * wgt;
        }
        out[0] = r; out[1] = g; out[2] = b; out[3] = a;
    }
    private static int wrap(int i, int n, int mode) { return mode == Texture2D.WRAP_REPEAT ? Math.floorMod(i, n) : Math.max(0, Math.min(n - 1, i)); }
    private static void unpack(int p, float[] o) { o[0] = ((p >> 16) & 255) / 255f; o[1] = ((p >> 8) & 255) / 255f; o[2] = (p & 255) / 255f; o[3] = (p >>> 24) / 255f; }

    // ================================================================== sprites
    void drawSprite(Sprite3D s, Transform modelToWorld, float alphaFactor) {
        Image2D img = s.getImage();
        Transform mv = Transform.mul(view, modelToWorld);
        float[] e = mv.apply(new float[]{0, 0, 0, 1});
        float[] cp = proj.apply(e);
        if (cp[3] <= 0 || cp[2] < -cp[3] || cp[2] > cp[3]) return;
        float ndcX = cp[0] / cp[3], ndcY = cp[1] / cp[3], z = dnear + (cp[2] / cp[3] * 0.5f + 0.5f) * (dfar - dnear);
        float cxs = vx + (ndcX * 0.5f + 0.5f) * vw, cys = vy + (1 - (ndcY * 0.5f + 0.5f)) * vh;
        int cw = s.getCropWidth(), ch = s.getCropHeight();
        float wpx, hpx;
        if (s.isScaled()) {
            float sxl = (float) Math.sqrt(mv.m[0] * mv.m[0] + mv.m[4] * mv.m[4] + mv.m[8] * mv.m[8]), syl = (float) Math.sqrt(mv.m[1] * mv.m[1] + mv.m[5] * mv.m[5] + mv.m[9] * mv.m[9]);
            // Scaled mode: the sprite is a unit square in model space, sized only by the node's scale; the crop rectangle
            // just selects which part of the image fills it (real games, e.g. muzzle flashes, depend on this).
            wpx = sxl * proj.m[0] / cp[3] * 0.5f * vw; hpx = syl * proj.m[5] / cp[3] * 0.5f * vh;
        } else { wpx = Math.abs(cw); hpx = Math.abs(ch); }
        Appearance ap = s.getAppearance();
        CompositingMode cm = ap == null ? null : ap.getCompositingMode();
        Fog fog = ap == null ? null : ap.getFog();
        int x0 = (int) Math.floor(cxs - wpx / 2), y0 = (int) Math.floor(cys - hpx / 2), x1 = (int) Math.ceil(cxs + wpx / 2), y1 = (int) Math.ceil(cys + hpx / 2);
        int[] px = img.argb();
        boolean depthTest = depthBuffer && (cm == null || cm.isDepthTestEnabled()), depthWrite = depthBuffer && (cm == null || cm.isDepthWriteEnabled());
        int blending = cm == null ? CompositingMode.REPLACE : cm.getBlending();
        float thr = cm == null ? 0 : cm.getAlphaThreshold();
        for (int y = Math.max(y0, Math.max(clipY, vy)); y < Math.min(y1, Math.min(clipY + clipH, vy + vh)); y++) {
            for (int x = Math.max(x0, Math.max(clipX, vx)); x < Math.min(x1, Math.min(clipX + clipW, vx + vw)); x++) {
                int idx = y * tw + x;
                if (depthTest && z > depth[idx]) continue;
                // the crop rectangle starts at (cropX, cropY) and extends by |w| x |h|; a negative size only mirrors the image
                float fx = (x - x0 + 0.5f) / (x1 - x0), fy = (y - y0 + 0.5f) / (y1 - y0);
                if (cw < 0) fx = 1 - fx;
                if (ch < 0) fy = 1 - fy;
                int sx = s.getCropX() + Math.min(Math.abs(cw) - 1, (int) (fx * Math.abs(cw))), sy = s.getCropY() + Math.min(Math.abs(ch) - 1, (int) (fy * Math.abs(ch)));
                if (sx < 0 || sy < 0 || sx >= img.getWidth() || sy >= img.getHeight()) continue;
                int t = px[sy * img.getWidth() + sx];
                float ta = (t >>> 24) / 255f * alphaFactor;
                if (ta < thr || (blending == CompositingMode.REPLACE && ta < 0.5f && img.hasAlpha())) continue;
                if (depthWrite) depth[idx] = z;
                if (cm != null && !cm.isColorWriteEnabled()) continue;
                int dst = color[idx];
                switch (blending) {
                    case CompositingMode.ALPHA:
                        color[idx] = blend((t & 0xFFFFFF) | 0xFF000000, dst, Math.round(ta * 255)); break;
                    case CompositingMode.ALPHA_ADD: case CompositingMode.MODULATE: case CompositingMode.MODULATE_X2: {
                        // same equations as the triangle path (the sprite colour is the source fragment)
                        float sr = ((t >> 16) & 255) / 255f, sg = ((t >> 8) & 255) / 255f, sb = (t & 255) / 255f;
                        float dr = ((dst >> 16) & 255) / 255f, dg = ((dst >> 8) & 255) / 255f, db = (dst & 255) / 255f;
                        float orr, og, ob;
                        if (blending == CompositingMode.ALPHA_ADD) { orr = sr * ta + dr; og = sg * ta + dg; ob = sb * ta + db; }
                        else if (blending == CompositingMode.MODULATE) { orr = sr * dr; og = sg * dg; ob = sb * db; }
                        else { orr = 2 * sr * dr; og = 2 * sg * dg; ob = 2 * sb * db; }
                        color[idx] = 0xFF000000 | (to8(orr) << 16) | (to8(og) << 8) | to8(ob);
                        break;
                    }
                    default: // REPLACE
                        if (ta < 1) color[idx] = blend((t & 0xFFFFFF) | 0xFF000000, dst, Math.round(ta * 255));
                        else color[idx] = t | 0xFF000000;
                }
            }
        }
    }
}
