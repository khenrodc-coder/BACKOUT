package com.blackout.game.core;

import java.util.HashMap;
import java.util.Map;
import java.util.function.BiPredicate;

import javafx.scene.Group;
import javafx.scene.image.Image;
import javafx.scene.image.PixelWriter;
import javafx.scene.image.WritableImage;
import javafx.scene.paint.Color;
import javafx.scene.paint.PhongMaterial;
import javafx.scene.shape.Box;
import javafx.scene.shape.CullFace;
import javafx.scene.shape.Cylinder;
import javafx.scene.shape.MeshView;
import javafx.scene.shape.Sphere;
import javafx.scene.shape.TriangleMesh;
import javafx.scene.transform.Rotate;

/** Small reusable 3D building blocks shared by all environment factories. */
public final class Gfx {

    private static final Map<Color, PhongMaterial> GLOW = new HashMap<>();
    private static final Map<Integer, TriangleMesh> CONES = new HashMap<>();

    private Gfx() {
    }

    /** Shared soft blob-shadow material (alpha is driven by the day/night cycle in Main). */
    public static final PhongMaterial SHADOW_MAT = mat(Color.rgb(0, 0, 0, 0.4), Color.BLACK, 1);

    public static PhongMaterial mat(Color d, Color s, double p) {
        PhongMaterial m = new PhongMaterial(d);
        m.setSpecularColor(s);
        m.setSpecularPower(p);
        return m;
    }

    public static PhongMaterial tex(Image img, Color spec, double p) {
        PhongMaterial m = new PhongMaterial(Color.WHITE);
        m.setDiffuseMap(img);
        m.setSpecularColor(spec);
        m.setSpecularPower(p);
        return m;
    }

    public static Image solid(Color c) {
        WritableImage img = new WritableImage(2, 2);
        PixelWriter pw = img.getPixelWriter();
        for (int y = 0; y < 2; y++) {
            for (int x = 0; x < 2; x++) {
                pw.setColor(x, y, c);
            }
        }
        return img;
    }

    public static PhongMaterial glow(Color c) {
        return GLOW.computeIfAbsent(c, col -> {
            PhongMaterial m = new PhongMaterial(Color.BLACK);
            m.setSelfIlluminationMap(solid(col));
            m.setSpecularColor(Color.BLACK);
            return m;
        });
    }

    public static Box box(double w, double h, double d, PhongMaterial m, double x, double y, double z) {
        Box b = new Box(w, h, d);
        b.setMaterial(m);
        b.setTranslateX(x);
        b.setTranslateY(y);
        b.setTranslateZ(z);
        return b;
    }

    public static Cylinder cylY(double r, double len, PhongMaterial m, double x, double y, double z) {
        return cylY(r, len, m, x, y, z, 12);
    }

    /** Cylinder with an explicit number of divisions (use more for big or scaled discs such as lakes and shadows). */
    public static Cylinder cylY(double r, double len, PhongMaterial m, double x, double y, double z, int div) {
        Cylinder c = new Cylinder(r, len, div);
        c.setMaterial(m);
        c.setTranslateX(x);
        c.setTranslateY(y);
        c.setTranslateZ(z);
        return c;
    }

    public static Cylinder cylX(double r, double len, PhongMaterial m, double x, double y, double z) {
        Cylinder c = cylY(r, len, m, x, y, z);
        c.setRotationAxis(Rotate.Z_AXIS);
        c.setRotate(90);
        return c;
    }

    public static Cylinder cylZ(double r, double len, PhongMaterial m, double x, double y, double z) {
        Cylinder c = cylY(r, len, m, x, y, z);
        c.setRotationAxis(Rotate.X_AXIS);
        c.setRotate(90);
        return c;
    }

    /** Cylinder lying along Z (alias of {@link #cylZ}, kept for the weapon/character code). */
    public static Cylinder cyl(double r, double len, PhongMaterial m, double x, double y, double z) {
        return cylZ(r, len, m, x, y, z);
    }

    /** Sphere with the default 20 divisions. */
    public static Sphere sph(double r, PhongMaterial m, double x, double y, double z, double sx, double sy, double sz) {
        return sph(r, m, x, y, z, sx, sy, sz, 20);
    }

    public static Sphere sph(double r, PhongMaterial m, double x, double y, double z, double sx, double sy, double sz, int div) {
        Sphere s = new Sphere(r, div);
        s.setMaterial(m);
        s.setTranslateX(x);
        s.setTranslateY(y);
        s.setTranslateZ(z);
        s.setScaleX(sx);
        s.setScaleY(sy);
        s.setScaleZ(sz);
        return s;
    }

    /** A beam (square section) from point 1 to point 2. */
    public static Box strut(double x1, double y1, double z1, double x2, double y2, double z2, double t, PhongMaterial m) {
        double dx = x2 - x1, dy = y2 - y1, dz = z2 - z1, len = Math.sqrt(dx * dx + dy * dy + dz * dz);
        Box b = new Box(t, t, Math.max(0.1, len));
        b.setMaterial(m);
        b.setTranslateX((x1 + x2) / 2);
        b.setTranslateY((y1 + y2) / 2);
        b.setTranslateZ((z1 + z2) / 2);
        b.getTransforms().setAll(new Rotate(Math.toDegrees(Math.atan2(dx, dz)), Rotate.Y_AXIS),
                new Rotate(Math.toDegrees(Math.asin(-dy / Math.max(0.1, len))), Rotate.X_AXIS));
        return b;
    }

    /** A sagging cable made of several struts. */
    public static void cable(Group g, double x1, double y1, double z1, double x2, double y2, double z2, double sag, double t, PhongMaterial m) {
        int n = 6;
        double px = x1, py = y1, pz = z1;
        for (int i = 1; i <= n; i++) {
            double f = i / (double) n;
            double nx = x1 + (x2 - x1) * f, nz = z1 + (z2 - z1) * f, ny = y1 + (y2 - y1) * f + sag * 4 * f * (1 - f);
            g.getChildren().add(strut(px, py, pz, nx, ny, nz, t, m));
            px = nx;
            py = ny;
            pz = nz;
        }
    }

    public static TriangleMesh cone(int sides) {
        return CONES.computeIfAbsent(sides, n -> {
            TriangleMesh m = new TriangleMesh();
            m.getPoints().addAll(0f, -1f, 0f, 0f, 0f, 0f);
            for (int i = 0; i < n; i++) {
                double a = Math.PI * 2 * i / n;
                m.getPoints().addAll((float) Math.cos(a), 0f, (float) Math.sin(a));
            }
            m.getTexCoords().addAll(0.5f, 0f, 0.5f, 1f);
            for (int i = 0; i < n; i++) {
                int p = 2 + i, q = 2 + (i + 1) % n;
                m.getFaces().addAll(0, 0, q, 1, p, 1);
                m.getFaces().addAll(1, 0, p, 1, q, 1);
            }
            return m;
        });
    }

    public static MeshView coneView(int sides, PhongMaterial m, double rx, double h, double rz, double x, double y, double z) {
        MeshView mv = new MeshView(cone(sides));
        mv.setMaterial(m);
        mv.setCullFace(CullFace.NONE);
        mv.getTransforms().add(new javafx.scene.transform.Scale(rx, h, rz));
        mv.setTranslateX(x);
        mv.setTranslateY(y);
        mv.setTranslateZ(z);
        return mv;
    }

    private static TriangleMesh planeMesh(double x0, double z0, int nx, int nz, double tile, BiPredicate<Integer, Integer> include, boolean flip) {
        TriangleMesh m = new TriangleMesh();
        m.getTexCoords().addAll(0f, 0f, 1f, 0f, 1f, 1f, 0f, 1f);
        int v = 0;
        for (int i = 0; i < nx; i++) {
            for (int j = 0; j < nz; j++) {
                if (include != null && !include.test(i, j)) {
                    continue;
                }
                float ax = (float) (x0 + i * tile), az = (float) (z0 + j * tile), bx = (float) (ax + tile), bz = (float) (az + tile);
                m.getPoints().addAll(ax, 0f, az, bx, 0f, az, bx, 0f, bz, ax, 0f, bz);
                if (!flip) {
                    m.getFaces().addAll(v, 0, v + 1, 1, v + 2, 2, v, 0, v + 2, 2, v + 3, 3);
                } else {
                    m.getFaces().addAll(v, 0, v + 2, 2, v + 1, 1, v, 0, v + 3, 3, v + 2, 2);
                }
                v += 4;
            }
        }
        return m;
    }

    /**
     * A flat textured plane made of tile x tile squares (texture repeats per square). Two meshes with opposite winding are
     * added so lighting is correct whichever way JavaFX treats the front face; only one is ever visible from above.
     */
    public static Group tilesPlane(double x0, double z0, int nx, int nz, double tile, double y, BiPredicate<Integer, Integer> include, PhongMaterial m) {
        Group g = new Group();
        for (int k = 0; k < 2; k++) {
            MeshView mv = new MeshView(planeMesh(x0, z0, nx, nz, tile, include, k == 1));
            mv.setMaterial(m);
            mv.setCullFace(CullFace.BACK);
            g.getChildren().add(mv);
        }
        g.setTranslateY(y);
        return g;
    }

    /**
     * A flat textured quad grid (any orientation) of nx x ny tiles: {@code (ux,uy,uz)} spans the whole width, {@code (vx,vy,vz)} the whole
     * height (pointing UP the texture). The texture repeats per tile. Both windings are added so it is lit correctly from the front.
     */
    public static Group tiledQuad(double ox, double oy, double oz, double ux, double uy, double uz, double vx, double vy, double vz, int nx, int ny, PhongMaterial m) {
        Group g = new Group();
        for (int k = 0; k < 2; k++) {
            TriangleMesh t = new TriangleMesh();
            t.getTexCoords().addAll(0f, 1f, 1f, 1f, 1f, 0f, 0f, 0f);
            int v = 0;
            for (int i = 0; i < nx; i++) {
                for (int j = 0; j < ny; j++) {
                    double a = i / (double) nx, b = (i + 1) / (double) nx, c = j / (double) ny, d = (j + 1) / (double) ny;
                    t.getPoints().addAll((float) (ox + ux * a + vx * c), (float) (oy + uy * a + vy * c), (float) (oz + uz * a + vz * c),
                            (float) (ox + ux * b + vx * c), (float) (oy + uy * b + vy * c), (float) (oz + uz * b + vz * c),
                            (float) (ox + ux * b + vx * d), (float) (oy + uy * b + vy * d), (float) (oz + uz * b + vz * d),
                            (float) (ox + ux * a + vx * d), (float) (oy + uy * a + vy * d), (float) (oz + uz * a + vz * d));
                    if (k == 0) {
                        t.getFaces().addAll(v, 0, v + 1, 1, v + 2, 2, v, 0, v + 2, 2, v + 3, 3);
                    } else {
                        t.getFaces().addAll(v, 0, v + 2, 2, v + 1, 1, v, 0, v + 3, 3, v + 2, 2);
                    }
                    v += 4;
                }
            }
            MeshView mv = new MeshView(t);
            mv.setMaterial(m);
            mv.setCullFace(CullFace.BACK);
            g.getChildren().add(mv);
        }
        return g;
    }

    /** A plane centred on (cx, cz) covering w x d, tiled with the given tile size. */
    public static Group plane(double cx, double cz, double w, double d, double tile, double y, PhongMaterial m) {
        int nx = Math.max(1, (int) Math.round(w / tile)), nz = Math.max(1, (int) Math.round(d / tile));
        return tilesPlane(cx - nx * tile / 2, cz - nz * tile / 2, nx, nz, tile, y, null, m);
    }

    /** Several crossed vertical quads (grass clump). Both windings are included so it is lit correctly from any side. */
    public static MeshView crossQuads(double w, double h, int planes, PhongMaterial m) {
        TriangleMesh t = new TriangleMesh();
        t.getTexCoords().addAll(0f, 1f, 1f, 1f, 1f, 0f, 0f, 0f);
        for (int p = 0; p < planes; p++) {
            double a = Math.PI * p / planes, dx = Math.cos(a) * w / 2, dz = Math.sin(a) * w / 2;
            t.getPoints().addAll((float) -dx, 0f, (float) -dz, (float) dx, 0f, (float) dz, (float) dx, (float) -h, (float) dz, (float) -dx, (float) -h, (float) -dz);
            int v = p * 4;
            t.getFaces().addAll(v, 0, v + 1, 1, v + 2, 2, v, 0, v + 2, 2, v + 3, 3);
            t.getFaces().addAll(v, 0, v + 2, 2, v + 1, 1, v, 0, v + 3, 3, v + 2, 2);
        }
        MeshView mv = new MeshView(t);
        mv.setMaterial(m);
        mv.setCullFace(CullFace.BACK);
        return mv;
    }
}
