package com.blackout.game.environment;

import com.blackout.game.core.Gfx;
import com.blackout.game.core.Mats;

import java.util.Random;

import javafx.scene.Group;
import javafx.scene.paint.Color;
import javafx.scene.paint.PhongMaterial;
import javafx.scene.transform.Rotate;

/** Builds a wide, weathered steel-truss road bridge in world coordinates (river runs along X, bridge spans Z). */
public final class BridgeFactory {

    private BridgeFactory() {
    }

    public static Group build(double cx, double cz, double wid, double len, double groundY, double deckTop, boolean truss, Random rn) {
        Group g = new Group();
        double hw = wid / 2, hl = len / 2;
        PhongMaterial rust = Mats.RUST, conc = Mats.CONCRETE;
        g.getChildren().add(Gfx.box(wid, 5, len, conc, cx, deckTop + 2.5, cz));
        g.getChildren().add(Gfx.plane(cx, cz, wid - 24, len, 60, deckTop - 0.6, Mats.ASPHALT));
        for (int sx = -1; sx <= 1; sx += 2) {
            g.getChildren().add(Gfx.box(12, 4, len, conc, cx + sx * (hw - 6), deckTop - 1.5, cz));
        }
        PhongMaterial paint = Gfx.mat(Color.rgb(210, 195, 130), Color.WHITE, 40);
        for (double z = cz - hl + 10; z < cz + hl - 6; z += 36) {
            g.getChildren().add(Gfx.box(3, 0.8, 16, paint, cx, deckTop - 1.2, z));
        }
        for (int i = -2; i <= 2; i++) {
            g.getChildren().add(Gfx.box(6, 14, len, rust, cx + i * wid * 0.22, deckTop + 12, cz));
        }
        for (double z = cz - hl + 10; z <= cz + hl - 10; z += 28) {
            g.getChildren().add(Gfx.box(wid, 4, 3, rust, cx, deckTop + 6, z));
        }
        double pierH = groundY + 16 - (deckTop + 5);
        for (int s = -1; s <= 1; s += 2) {
            double pz = cz + s * hl * 0.5;
            g.getChildren().add(Gfx.box(wid - 10, 8, 14, conc, cx, deckTop + 9, pz));
            for (int i = -1; i <= 1; i++) {
                double px = cx + i * wid * 0.3;
                g.getChildren().add(Gfx.box(13, pierH, 24, conc, px, deckTop + 5 + pierH / 2, pz));
                javafx.scene.shape.Box w = Gfx.box(9, pierH * 0.7, 9, conc, px, deckTop + 5 + pierH * 0.65, pz - s * 12);
                w.setRotationAxis(Rotate.Y_AXIS);
                w.setRotate(45);
                g.getChildren().add(w);
            }
        }
        for (int s = -1; s <= 1; s += 2) {
            double az = cz + s * (hl + 12);
            g.getChildren().add(Gfx.box(wid + 36, groundY + 2 - deckTop, 26, conc, cx, (deckTop + groundY + 2) / 2, az));
            for (int sx = -1; sx <= 1; sx += 2) {
                g.getChildren().add(Gfx.box(10, 16, 50, conc, cx + sx * (hw + 22), groundY - 8, az + s * 14));
            }
        }
        double th = truss ? 130 : 0, topY = deckTop - th;
        for (int sx = -1; sx <= 1; sx += 2) {
            double x = cx + sx * (hw - 3);
            if (truss) {
                g.getChildren().add(Gfx.box(4, 5, len, rust, x, topY, cz));
                g.getChildren().add(Gfx.box(4, 5, len, rust, x, deckTop - 3, cz));
                int n = (int) (len / 26);
                double step = len / n;
                for (int i = 0; i <= n; i++) {
                    double z = cz - hl + i * step;
                    if (rn.nextDouble() > 0.08) {
                        g.getChildren().add(Gfx.box(3.4, th, 3.4, rust, x, deckTop - th / 2, z));
                    }
                    if (i < n && rn.nextDouble() > 0.1) {
                        boolean flip = (i % 2 == 0) ^ (sx > 0);
                        g.getChildren().add(Gfx.strut(x, flip ? deckTop - 3 : topY, z, x, flip ? topY : deckTop - 3, z + step, 2.6, rust));
                    }
                }
                for (double z = cz - hl + 4; z <= cz + hl - 4; z += 52) {
                    g.getChildren().add(Gfx.box(wid, 4, 4, rust, cx, topY + 2, z));
                    g.getChildren().add(Gfx.strut(cx - hw + 3, topY + 2, z, cx + hw - 3, topY + 2, z + 52, 2, rust));
                }
            }
            double rx = cx + sx * (hw - 14);
            for (double z = cz - hl + 4; z <= cz + hl - 4; z += 14) {
                if (rn.nextDouble() > 0.1) {
                    g.getChildren().add(Gfx.box(1.8, 20, 1.8, Mats.STEEL_GREY, rx, deckTop - 12, z));
                }
            }
            for (double z0 = cz - hl; z0 < cz + hl - 1; z0 += 42) {
                if (rn.nextDouble() > 0.14) {
                    g.getChildren().add(Gfx.box(1.6, 1.6, 42, Mats.STEEL_GREY, rx, deckTop - 21, z0 + 21));
                    g.getChildren().add(Gfx.box(1.6, 1.6, 42, Mats.STEEL_GREY, rx, deckTop - 12, z0 + 21));
                }
            }
            for (int e = -1; e <= 1; e += 2) {
                double lz = cz + e * (hl - 8);
                g.getChildren().add(Gfx.box(3, 80, 3, Mats.BLACK_METAL, rx - sx * 4, deckTop - 40, lz));
                g.getChildren().add(Gfx.box(14, 3, 6, Mats.BLACK_METAL, rx - sx * 10, deckTop - 80, lz));
                if (rn.nextDouble() < 0.4) {
                    g.getChildren().add(Gfx.box(10, 2.5, 5, Gfx.glow(Color.rgb(200, 180, 120)), rx - sx * 10, deckTop - 78, lz));
                }
            }
        }
        for (int i = 0; i < 7; i++) {
            double s = 3 + rn.nextDouble() * 6;
            javafx.scene.shape.Box c = Gfx.box(s * 1.5, s, s, conc, cx + (rn.nextDouble() - 0.5) * wid * 0.8, deckTop - s / 2, cz + (rn.nextDouble() - 0.5) * len * 0.8);
            c.setRotationAxis(Rotate.Y_AXIS);
            c.setRotate(rn.nextDouble() * 90);
            g.getChildren().add(c);
        }
        return g;
    }
}
