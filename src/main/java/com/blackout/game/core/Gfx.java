package com.blackout.game.core;

import java.util.HashMap;
import java.util.Map;
import javafx.scene.image.PixelWriter;
import javafx.scene.image.WritableImage;
import javafx.scene.paint.Color;
import javafx.scene.paint.PhongMaterial;
import javafx.scene.shape.Box;
import javafx.scene.shape.Cylinder;
import javafx.scene.shape.Sphere;
import javafx.scene.transform.Rotate;

public final class Gfx {

    private Gfx() {
    }

    private static final Map<Color, PhongMaterial> GLOW_CACHE = new HashMap<>();
    public static final PhongMaterial SHADOW_MAT = mat(Color.rgb(0, 0, 0, 0.4), Color.BLACK, 1);

    public static PhongMaterial mat(Color d, Color s, double p) {
        PhongMaterial m = new PhongMaterial();
        m.setDiffuseColor(d);
        m.setSpecularColor(s);
        m.setSpecularPower(p);
        return m;
    }

    public static PhongMaterial glow(Color c) {
        return GLOW_CACHE.computeIfAbsent(c, col -> {
            PhongMaterial m = new PhongMaterial(Color.BLACK);
            WritableImage img = new WritableImage(2, 2);
            PixelWriter pw = img.getPixelWriter();
            for (int y = 0; y < 2; y++) {
                for (int x = 0; x < 2; x++) {
                    pw.setColor(x, y, col);
                }
            }
            m.setSelfIlluminationMap(img);
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
        Cylinder c = new Cylinder(r, len);
        c.setMaterial(m);
        c.setTranslateX(x);
        c.setTranslateY(y);
        c.setTranslateZ(z);
        return c;
    }

    public static Cylinder cyl(double r, double len, PhongMaterial m, double x, double y, double z) {
        Cylinder c = cylY(r, len, m, x, y, z);
        c.setRotationAxis(Rotate.X_AXIS);
        c.setRotate(90);
        return c;
    }

    public static Cylinder cylX(double r, double len, PhongMaterial m, double x, double y, double z) {
        Cylinder c = cylY(r, len, m, x, y, z);
        c.setRotationAxis(Rotate.Z_AXIS);
        c.setRotate(90);
        return c;
    }

    public static Sphere sph(double r, PhongMaterial m, double x, double y, double z, double sx, double sy, double sz) {
        Sphere s = new Sphere(r, 20);
        s.setMaterial(m);
        s.setTranslateX(x);
        s.setTranslateY(y);
        s.setTranslateZ(z);
        s.setScaleX(sx);
        s.setScaleY(sy);
        s.setScaleZ(sz);
        return s;
    }
}
