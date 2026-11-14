package com.blackout.game.environment;

import com.blackout.game.core.Gfx;
import com.blackout.game.core.Mats;

import java.util.Random;

import javafx.geometry.Point3D;
import javafx.scene.Group;
import javafx.scene.paint.PhongMaterial;
import javafx.scene.transform.Rotate;

/** Builds natural-looking trees (pine, oak, birch, dead) with a wind-swaying canopy. */
public final class TreeFactory {

    public static final int PINE = 0, OAK = 1, BIRCH = 2, DEAD = 3;

    /** A tree model; call {@link #sway(double)} each frame while it is visible. */
    public static final class Tree {

        public final Group node = new Group();
        public final Rotate sway;
        public final double phase, strength;

        Tree(double px, double py, double pz, double phase, double strength) {
            sway = new Rotate(0, px, py, pz, new Point3D(1, 0, 0.6));
            this.phase = phase;
            this.strength = strength;
        }

        public void sway(double t) {
            sway.setAngle(Math.sin(t * 0.9 + phase) * strength + Math.sin(t * 2.3 + phase * 2) * strength * 0.3);
        }
    }

    private TreeFactory() {
    }

    private static Group branch(double x, double y, double z, double len, double r, double yaw, double tilt, PhongMaterial m) {
        Group b = new Group();
        b.setTranslateX(x);
        b.setTranslateY(y);
        b.setTranslateZ(z);
        b.getTransforms().addAll(new Rotate(yaw, Rotate.Y_AXIS), new Rotate(tilt, Rotate.Z_AXIS));
        b.getChildren().add(Gfx.cylY(r, len, m, 0, -len / 2, 0));
        return b;
    }

    public static Tree create(int species, double s, double x, double z, double gy, Random rn) {
        double top = species == PINE ? 76 * s : species == DEAD ? 70 * s : 62 * s;
        Tree t = new Tree(x, gy - top, z, rn.nextDouble() * 6.28, species == PINE ? 1.2 : species == DEAD ? 0.6 : 1.8);
        Group g = t.node;
        Group canopy = new Group();
        canopy.getTransforms().add(t.sway);
        g.getChildren().add(canopy);
        switch (species) {
            case PINE: {
                g.getChildren().add(Gfx.cylY(4.2 * s, 80 * s, Mats.BARK, x, gy - 40 * s, z));
                int layers = 5;
                for (int i = 0; i < layers; i++) {
                    double f = i / (double) layers, rad = (36 - 26 * f) * s * (0.9 + rn.nextDouble() * 0.2), h = 44 * s;
                    double yy = gy - (14 + 62 * f) * s;
                    canopy.getChildren().add(Gfx.coneView(9, Mats.NEEDLE, rad, h, rad * (0.9 + rn.nextDouble() * 0.2), x + (rn.nextDouble() - 0.5) * 2 * s, yy + h * 0.15, z));
                }
                canopy.getChildren().add(Gfx.coneView(7, Mats.NEEDLE, 6 * s, 24 * s, 6 * s, x, gy - 80 * s + 14 * s, z));
                break;
            }
            case OAK: {
                g.getChildren().add(Gfx.cylY(6.2 * s, 34 * s, Mats.BARK, x, gy - 17 * s, z));
                g.getChildren().add(Gfx.cylY(4.2 * s, 30 * s, Mats.BARK, x + 1.5 * s, gy - 48 * s, z));
                for (int i = 0; i < 4; i++) {
                    g.getChildren().add(branch(x, gy - (32 + rn.nextDouble() * 22) * s, z, (22 + rn.nextDouble() * 14) * s, 1.8 * s, i * 90 + rn.nextDouble() * 50, 35 + rn.nextDouble() * 25, Mats.BARK));
                }
                int blobs = 6 + rn.nextInt(3);
                for (int i = 0; i < blobs; i++) {
                    double a = rn.nextDouble() * 6.28, rr = (6 + rn.nextDouble() * 24) * s, r = (17 + rn.nextDouble() * 12) * s;
                    PhongMaterial lm = rn.nextDouble() < 0.18 ? Mats.LEAF_AUTUMN : Mats.LEAF_OAK;
                    canopy.getChildren().add(Gfx.sph(r, lm, x + Math.cos(a) * rr, gy - (56 + rn.nextDouble() * 24) * s, z + Math.sin(a) * rr, 1, 0.78, 1, 8));
                }
                break;
            }
            case BIRCH: {
                g.getChildren().add(Gfx.cylY(3.0 * s, 62 * s, Mats.BARK_BIRCH, x, gy - 31 * s, z));
                for (int i = 0; i < 3; i++) {
                    g.getChildren().add(branch(x, gy - (30 + i * 10) * s, z, 16 * s, 1.1 * s, i * 130, 40, Mats.BARK_BIRCH));
                }
                for (int i = 0; i < 5; i++) {
                    double a = rn.nextDouble() * 6.28, rr = (4 + rn.nextDouble() * 12) * s;
                    canopy.getChildren().add(Gfx.sph((10 + rn.nextDouble() * 8) * s, Mats.LEAF_BIRCH, x + Math.cos(a) * rr, gy - (48 + rn.nextDouble() * 24) * s, z + Math.sin(a) * rr, 1, 1.15, 1, 7));
                }
                break;
            }
            default: {
                g.getChildren().add(Gfx.cylY(5.0 * s, 40 * s, Mats.BARK_DEAD, x, gy - 20 * s, z));
                g.getChildren().add(Gfx.cylY(3.2 * s, 32 * s, Mats.BARK_DEAD, x + 2 * s, gy - 54 * s, z));
                for (int i = 0; i < 6; i++) {
                    double yy = gy - (26 + rn.nextDouble() * 44) * s, len = (20 + rn.nextDouble() * 22) * s, yaw = i * 62 + rn.nextDouble() * 40, tilt = 40 + rn.nextDouble() * 35;
                    canopy.getChildren().add(branch(x, yy, z, len, 1.5 * s, yaw, tilt, Mats.BARK_DEAD));
                    canopy.getChildren().add(branch(x + Math.sin(Math.toRadians(yaw)) * len * 0.5, yy - len * 0.6, z + Math.cos(Math.toRadians(yaw)) * len * 0.5, len * 0.5, 0.9 * s, yaw + 90, tilt + 20, Mats.BARK_DEAD));
                }
                break;
            }
        }
        return t;
    }
}
