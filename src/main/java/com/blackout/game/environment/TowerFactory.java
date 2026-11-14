package com.blackout.game.environment;

import com.blackout.game.core.Gfx;
import com.blackout.game.core.Mats;

import java.util.List;
import java.util.Random;

import javafx.scene.Group;
import javafx.scene.paint.Color;
import javafx.scene.paint.PhongMaterial;
import javafx.scene.shape.Sphere;
import javafx.scene.transform.Rotate;

/** Communication / radio towers and power pylons. Local origin = ground centre; Y negative is up. */
public final class TowerFactory {

    private TowerFactory() {
    }

    private static Sphere beacon(Group g, double y, boolean dead, List<Sphere> beacons) {
        Sphere s = Gfx.sph(3.2, dead ? Gfx.mat(Color.rgb(60, 12, 10), Color.BLACK, 1) : Gfx.glow(Color.rgb(255, 30, 20)), 0, y, 0, 1, 1, 1, 8);
        g.getChildren().add(s);
        if (!dead) {
            beacons.add(s);
        }
        return s;
    }

    private static void base(Group g, double size, Random rn) {
        g.getChildren().add(Gfx.box(size * 2.4, 3, size * 2.4, Mats.CONCRETE, 0, -1.5, 0));
        g.getChildren().add(Gfx.box(26, 30, 36, Mats.CONCRETE, size * 1.6 + 12, -15, 0));
        g.getChildren().add(Gfx.box(27, 3, 37, Mats.TAR_ROOF, size * 1.6 + 12, -31, 0));
        g.getChildren().add(Gfx.box(2, 22, 11, Mats.RUST, size * 1.6 - 1, -12, 8));
        g.getChildren().add(Gfx.box(12, 10, 12, Mats.STEEL_GREY, size * 1.6 + 12, -37, -8));
        g.getChildren().add(Gfx.cylY(4.5, 2, Mats.BLACK_METAL, size * 1.6 + 12, -39, -8));
    }

    /** Tall four-legged lattice tower. */
    public static Group lattice(double h, boolean damaged, List<Sphere> beacons, Random rn) {
        Group g = new Group();
        int segs = damaged ? 5 : 7;
        double H = h * (damaged ? 0.78 : 1.0), b = h * 0.085, t = h * 0.016, th = Math.max(2.2, h * 0.007);
        PhongMaterial m = Mats.RUST;
        for (int sx = -1; sx <= 1; sx += 2) {
            for (int sz = -1; sz <= 1; sz += 2) {
                g.getChildren().add(Gfx.strut(sx * b, 0, sz * b, sx * t, -H, sz * t, th, m));
            }
        }
        for (int i = 0; i <= segs; i++) {
            double w = b + (t - b) * i / segs, y = -H * i / segs;
            if (i > 0) {
                g.getChildren().add(Gfx.strut(-w, y, -w, w, y, -w, th * 0.7, m));
                g.getChildren().add(Gfx.strut(-w, y, w, w, y, w, th * 0.7, m));
                g.getChildren().add(Gfx.strut(-w, y, -w, -w, y, w, th * 0.7, m));
                g.getChildren().add(Gfx.strut(w, y, -w, w, y, w, th * 0.7, m));
            }
            if (i < segs) {
                double w2 = b + (t - b) * (i + 1) / segs, y2 = -H * (i + 1) / segs;
                boolean flip = i % 2 == 0;
                g.getChildren().add(Gfx.strut(flip ? -w : w, y, -w, flip ? w2 : -w2, y2, -w2, th * 0.6, m));
                g.getChildren().add(Gfx.strut(flip ? -w : w, y, w, flip ? w2 : -w2, y2, w2, th * 0.6, m));
                g.getChildren().add(Gfx.strut(-w, y, flip ? -w : w, -w2, y2, flip ? w2 : -w2, th * 0.6, m));
                g.getChildren().add(Gfx.strut(w, y, flip ? -w : w, w2, y2, flip ? w2 : -w2, th * 0.6, m));
            }
        }
        g.getChildren().add(Gfx.box(t * 2 + 10, 3, t * 2 + 10, Mats.STEEL_GREY, 0, -H, 0));
        for (int i = 0; i < 3; i++) {
            double a = i * 2.094 + rn.nextDouble() * 0.4, rr = t + 6;
            Group panel = new Group();
            panel.setTranslateX(Math.cos(a) * rr);
            panel.setTranslateZ(Math.sin(a) * rr);
            panel.setTranslateY(-H + 8);
            panel.getTransforms().add(new Rotate(-Math.toDegrees(a) + 90, Rotate.Y_AXIS));
            panel.getChildren().add(Gfx.box(4, 26, 7, Mats.STEEL_GREY, 0, 0, 5));
            if (i == 1) {
                panel.getChildren().add(Gfx.sph(9, Mats.STEEL_GREY, 0, 8, 10, 1, 1, 0.25, 10));
                panel.getChildren().add(Gfx.cylZ(1, 12, Mats.BLACK_METAL, 0, 8, 6));
            }
            g.getChildren().add(panel);
        }
        g.getChildren().add(Gfx.cylY(1.4, 40, Mats.STEEL_GREY, 0, -H - 20, 0));
        g.getChildren().add(Gfx.cylY(0.8, 22, Mats.STEEL_GREY, 6, -H - 11, 3));
        beacon(g, -H - 41, damaged, beacons);
        for (int k = 0; k < 4; k++) {
            double a = k * 1.5708 + 0.785, ax = Math.cos(a) * h * 0.55, az = Math.sin(a) * h * 0.55;
            if (!damaged || k != 2) {
                Gfx.cable(g, 0, -H * 0.62, 0, ax, -2, az, 4, 0.8, Mats.BLACK_METAL);
                g.getChildren().add(Gfx.box(4, 3, 4, Mats.CONCRETE, ax, -1.5, az));
            }
        }
        if (damaged) {
            Gfx.cable(g, t, -H, t, t + 40, -H * 0.35, t + 16, 3, 0.8, Mats.BLACK_METAL);
            Rotate lean = new Rotate(6 + rn.nextDouble() * 4, 0, 0, 0, Rotate.Z_AXIS);
            g.getTransforms().add(lean);
        }
        base(g, b, rn);
        return g;
    }

    /** Slim cellular monopole with sector antennas. */
    public static Group monopole(double h, boolean damaged, List<Sphere> beacons, Random rn) {
        Group g = new Group();
        double[] rad = {6.5, 4.8, 3.4}, hh = {h * 0.42, h * 0.34, h * 0.28};
        double y = 0;
        for (int i = 0; i < 3; i++) {
            g.getChildren().add(Gfx.cylY(rad[i], hh[i], i % 2 == 0 ? Mats.CONCRETE : Mats.STEEL_GREY, 0, y - hh[i] / 2, 0));
            y -= hh[i];
        }
        g.getChildren().add(Gfx.cylY(11, 2.5, Mats.STEEL_GREY, 0, y + 8, 0));
        for (int i = 0; i < 3; i++) {
            Group sec = new Group();
            sec.getTransforms().add(new Rotate(i * 120 + rn.nextDouble() * 12, Rotate.Y_AXIS));
            sec.setTranslateY(y + 4);
            for (int k = 0; k < 3; k++) {
                sec.getChildren().add(Gfx.box(5, 28, 3, Mats.STEEL_GREY, (k - 1) * 6, 0, 13));
            }
            sec.getChildren().add(Gfx.strut(0, -4, 0, 0, -4, 12, 1.4, Mats.BLACK_METAL));
            if (damaged && i == 2) {
                sec.getTransforms().add(new Rotate(35, Rotate.X_AXIS));
            }
            g.getChildren().add(sec);
        }
        g.getChildren().add(Gfx.sph(8, Mats.STEEL_GREY, 10, y + 18, 6, 1, 1, 0.25, 10));
        g.getChildren().add(Gfx.cylY(0.9, 18, Mats.STEEL_GREY, 0, y - 9, 0));
        beacon(g, y - 20, damaged, beacons);
        base(g, 14, rn);
        return g;
    }

    /** High-voltage transmission pylon; cables are added by the caller between consecutive pylons. */
    public static Group pylon(double h, Random rn) {
        Group g = new Group();
        double b = h * 0.17, t = h * 0.03;
        for (int sx = -1; sx <= 1; sx += 2) {
            for (int sz = -1; sz <= 1; sz += 2) {
                g.getChildren().add(Gfx.strut(sx * b, 0, sz * b * 0.6, sx * t, -h * 0.72, sz * t, 2.4, Mats.RUST));
            }
        }
        for (int i = 1; i <= 4; i++) {
            double f = i / 5.0, w = b + (t - b) * f * 1.35, y = -h * 0.72 * f;
            g.getChildren().add(Gfx.strut(-w, y, 0, w, y, 0, 1.6, Mats.RUST));
            g.getChildren().add(Gfx.strut(-w, y, 0, w * 0.4, y - h * 0.1, 0, 1.4, Mats.RUST));
        }
        for (int i = 0; i < 3; i++) {
            double y = -h * (0.62 + i * 0.11), arm = h * (0.26 - i * 0.04);
            g.getChildren().add(Gfx.strut(-arm, y, 0, arm, y, 0, 2.2, Mats.RUST));
            g.getChildren().add(Gfx.strut(-arm * 0.3, y - 8, 0, -arm, y, 0, 1.2, Mats.RUST));
            g.getChildren().add(Gfx.strut(arm * 0.3, y - 8, 0, arm, y, 0, 1.2, Mats.RUST));
            for (int sx = -1; sx <= 1; sx += 2) {
                g.getChildren().add(Gfx.cylY(1.6, 9, Gfx.mat(Color.rgb(150, 140, 120), Color.WHITE, 40), sx * arm, y + 5, 0));
            }
        }
        g.getChildren().add(Gfx.strut(0, -h * 0.9, 0, 0, -h, 0, 1.6, Mats.RUST));
        g.getChildren().add(Gfx.box(2, 2, 2, Mats.RUST, 0, -h, 0));
        return g;
    }
}
