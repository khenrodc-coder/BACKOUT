package com.blackout.game.environment;

import com.blackout.game.core.Gfx;
import com.blackout.game.core.Mats;

import java.util.Random;
import java.util.function.BiPredicate;

import javafx.scene.Group;
import javafx.scene.paint.Color;
import javafx.scene.paint.PhongMaterial;
import javafx.scene.shape.Box;
import javafx.scene.transform.Rotate;

/** Grass chunks, bushes, reeds and scattered debris. */
public final class VegetationFactory {

    /** A square patch of vegetation that sways in the wind as one unit (pivot at ground level). */
    public static final class Chunk {

        public final Group node = new Group();
        public final Rotate sway;
        public final double cx, cz, phase;

        Chunk(double cx, double cz, double gy, double phase) {
            this.cx = cx;
            this.cz = cz;
            this.phase = phase;
            sway = new Rotate(0, cx, gy, cz, new javafx.geometry.Point3D(1, 0, 0.7));
            node.getTransforms().add(sway);
        }

        public void update(double t) {
            sway.setAngle(Math.sin(t * 1.3 + phase) * 1.1 + Math.sin(t * 3.1 + phase * 2) * 0.35);
        }
    }

    private VegetationFactory() {
    }

    public static Chunk grassChunk(double cx, double cz, double size, double gy, Random rn, BiPredicate<Double, Double> ok, int clumps, int bushes, boolean wild) {
        Chunk c = new Chunk(cx, cz, gy, rn.nextDouble() * 6.28);
        for (int i = 0; i < clumps; i++) {
            double x = cx + (rn.nextDouble() - 0.5) * size, z = cz + (rn.nextDouble() - 0.5) * size;
            if (!ok.test(x, z)) {
                continue;
            }
            double r = rn.nextDouble();
            int variant = r < 0.45 ? 0 : r < 0.7 ? 1 : r < 0.88 ? 2 : 3;
            double h = variant == 3 ? 20 + rn.nextDouble() * 18 : 8 + rn.nextDouble() * 14;
            if (wild) {
                h *= 1.5;
            }
            javafx.scene.shape.MeshView mv = Gfx.crossQuads(14 + rn.nextDouble() * 12, h, 3, Mats.blades(variant));
            mv.setTranslateX(x);
            mv.setTranslateY(gy + 0.5);
            mv.setTranslateZ(z);
            mv.setRotationAxis(Rotate.Y_AXIS);
            mv.setRotate(rn.nextDouble() * 180);
            c.node.getChildren().add(mv);
        }
        for (int i = 0; i < bushes; i++) {
            double x = cx + (rn.nextDouble() - 0.5) * size, z = cz + (rn.nextDouble() - 0.5) * size;
            if (!ok.test(x, z)) {
                continue;
            }
            PhongMaterial m = rn.nextDouble() < 0.25 ? Mats.LEAF_AUTUMN : rn.nextDouble() < 0.3 ? Mats.LEAF_BIRCH : Mats.LEAF_OAK;
            double s = 0.7 + rn.nextDouble() * 0.9;
            for (int k = 0; k < 3; k++) {
                c.node.getChildren().add(Gfx.sph((6 + rn.nextDouble() * 4) * s, m, x + (rn.nextDouble() - 0.5) * 10 * s, gy - 5 * s, z + (rn.nextDouble() - 0.5) * 10 * s, 1, 0.8, 1, 7));
            }
        }
        return c;
    }

    /** Ground clutter: 0 rock, 1 log, 2 branches, 3 trash pile, 4 tire, 5 rusty barrel, 6 scrap sheets, 7 papers. */
    public static Group debris(int kind, Random rn, double gy) {
        Group g = new Group();
        PhongMaterial bag = Gfx.mat(Color.rgb(20, 22, 22), Color.rgb(80, 80, 80), 30);
        switch (kind) {
            case 0: {
                double s = 4 + rn.nextDouble() * 12;
                g.getChildren().add(Gfx.sph(s, Mats.ROCK, 0, gy - s * 0.35, 0, 1.2, 0.7, 1, 8));
                if (rn.nextBoolean()) {
                    g.getChildren().add(Gfx.sph(s * 0.55, Mats.ROCK, s * 1.1, gy - s * 0.2, s * 0.3, 1, 0.7, 1.1, 7));
                }
                break;
            }
            case 1: {
                double l = 40 + rn.nextDouble() * 40;
                g.getChildren().add(Gfx.cylZ(4 + rn.nextDouble() * 2, l, Mats.BARK_DEAD, 0, gy - 4, 0));
                g.getChildren().add(Gfx.cylY(4.4, 1, Mats.WOOD, 0, gy - 4, l / 2));
                break;
            }
            case 2: {
                for (int i = 0; i < 5; i++) {
                    double a = rn.nextDouble() * 6.28, l = 14 + rn.nextDouble() * 22;
                    g.getChildren().add(Gfx.strut(0, gy - 1.5, 0, Math.cos(a) * l, gy - 1.5 - rn.nextDouble() * 4, Math.sin(a) * l, 1.4, Mats.BARK_DEAD));
                }
                break;
            }
            case 3: {
                for (int i = 0; i < 6; i++) {
                    double s = 4 + rn.nextDouble() * 5;
                    PhongMaterial m = i % 3 == 0 ? Mats.WOOD : bag;
                    Box b = Gfx.box(s * 1.3, s, s, m, (rn.nextDouble() - 0.5) * 16, gy - s / 2, (rn.nextDouble() - 0.5) * 16);
                    b.setRotationAxis(Rotate.Y_AXIS);
                    b.setRotate(rn.nextDouble() * 90);
                    g.getChildren().add(b);
                }
                for (int i = 0; i < 4; i++) {
                    g.getChildren().add(Gfx.cylY(1.1, 3, Mats.CHROME, (rn.nextDouble() - 0.5) * 22, gy - 1.5, (rn.nextDouble() - 0.5) * 22));
                }
                break;
            }
            case 4: {
                g.getChildren().add(Gfx.cylY(9, 5, Mats.TIRE, 0, gy - 2.5, 0));
                g.getChildren().add(Gfx.cylY(4.5, 5.2, Mats.RUST, 0, gy - 2.5, 0));
                break;
            }
            case 5: {
                g.getChildren().add(Gfx.cylZ(8, 22, Mats.RUST, 0, gy - 8, 0));
                break;
            }
            case 6: {
                for (int i = 0; i < 3; i++) {
                    Box b = Gfx.box(18 + rn.nextDouble() * 10, 0.8, 14, Mats.RUST, (rn.nextDouble() - 0.5) * 12, gy - 1 - i * 1.2, (rn.nextDouble() - 0.5) * 12);
                    b.setRotationAxis(Rotate.Y_AXIS);
                    b.setRotate(rn.nextDouble() * 180);
                    g.getChildren().add(b);
                }
                break;
            }
            default: {
                PhongMaterial paper = Gfx.mat(Color.rgb(180, 176, 160), Color.BLACK, 1);
                for (int i = 0; i < 6; i++) {
                    Box b = Gfx.box(6, 0.3, 8, paper, (rn.nextDouble() - 0.5) * 24, gy - 0.5, (rn.nextDouble() - 0.5) * 24);
                    b.setRotationAxis(Rotate.Y_AXIS);
                    b.setRotate(rn.nextDouble() * 180);
                    g.getChildren().add(b);
                }
            }
        }
        return g;
    }
}
