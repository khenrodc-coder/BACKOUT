package com.blackout.game.environment;

import com.blackout.game.core.Gfx;
import com.blackout.game.core.Mats;

import java.util.Random;

import javafx.scene.Group;
import javafx.scene.paint.Color;
import javafx.scene.paint.PhongMaterial;
import javafx.scene.shape.Box;
import javafx.scene.transform.Rotate;

/** Detailed, weathered car models. Local origin = ground centre, front (headlights) faces +Z. */
public final class VehicleFactory {

    public static final int SEDAN = 0, SUV = 1, PICKUP = 2, VAN = 3;

    private VehicleFactory() {
    }

    private static Box tilted(Box b, double axisDeg, javafx.geometry.Point3D axis) {
        b.setRotationAxis(axis);
        b.setRotate(axisDeg);
        return b;
    }

    public static Group create(int kind, Color paintColor, boolean wreck, boolean burnt, Random rn) {
        Group g = new Group();
        PhongMaterial body = burnt ? Gfx.mat(Color.rgb(22, 20, 19), Color.rgb(30, 30, 30), 10) : wreck ? Mats.RUST : Mats.paint(paintColor);
        PhongMaterial paint = burnt ? body : Mats.paint(paintColor);
        PhongMaterial glass = burnt ? Mats.GLASS_DARK : Mats.GLASS;
        double len = kind == PICKUP ? 132 : kind == VAN ? 136 : kind == SUV ? 124 : 118;
        double wid = kind == SUV || kind == VAN ? 60 : 56;
        double cabH = kind == SUV ? 24 : kind == VAN ? 36 : 17;
        double hl = len / 2;

        g.getChildren().add(Gfx.box(wid - 6, 6, len - 8, Mats.BLACK_METAL, 0, -9, 0));
        g.getChildren().add(Gfx.box(wid, 14, len, body, 0, -16, 0));
        Box hood = Gfx.box(wid - 3, 4, len * 0.3, paint, 0, -24.5, hl - len * 0.18);
        Box trunk = Gfx.box(wid - 3, 4, len * 0.22, paint, 0, -24.5, -hl + len * 0.12);
        if (wreck && rn.nextDouble() < 0.5) {
            hood.setTranslateY(-30);
            tilted(hood, -28, Rotate.X_AXIS);
            hood.setTranslateZ(hl - len * 0.1);
            g.getChildren().add(Gfx.box(wid - 12, 8, len * 0.24, Mats.RUST, 0, -22, hl - len * 0.2));
        }
        g.getChildren().addAll(hood, trunk);

        double cabLen = kind == PICKUP ? len * 0.32 : kind == VAN ? len * 0.8 : len * 0.5, cabZ = kind == PICKUP ? hl * 0.18 : kind == VAN ? -4 : -hl * 0.05;
        double cabY = -23 - cabH / 2;
        g.getChildren().add(Gfx.box(wid - 8, 3, cabLen - 2, paint, 0, -23 - cabH - 1, cabZ));
        for (int sx = -1; sx <= 1; sx += 2) {
            for (int sz = -1; sz <= 1; sz += 2) {
                g.getChildren().add(Gfx.box(3, cabH, 3, body, sx * (wid / 2 - 5), cabY, cabZ + sz * (cabLen / 2 - 2)));
            }
        }
        boolean brokenFront = wreck && rn.nextDouble() < 0.6, brokenRear = wreck && rn.nextDouble() < 0.5;
        Box ws = Gfx.box(wid - 12, cabH - 2, 2, glass, 0, cabY, cabZ + cabLen / 2 - 1);
        tilted(ws, -18, Rotate.X_AXIS);
        Box rw = Gfx.box(wid - 12, cabH - 2, 2, glass, 0, cabY, cabZ - cabLen / 2 + 1);
        tilted(rw, 18, Rotate.X_AXIS);
        if (!brokenFront && !burnt) {
            g.getChildren().add(ws);
        } else {
            for (int i = 0; i < 4; i++) {
                g.getChildren().add(Gfx.box(2 + rn.nextDouble() * 3, 5, 0.8, Mats.GLASS, (rn.nextDouble() - 0.5) * (wid - 16), cabY + cabH * 0.35, cabZ + cabLen / 2 - 1));
            }
        }
        if (!brokenRear && !burnt) {
            g.getChildren().add(rw);
        }
        for (int sx = -1; sx <= 1; sx += 2) {
            if (!(wreck && rn.nextDouble() < 0.4) && !burnt) {
                g.getChildren().add(Gfx.box(1, cabH - 5, cabLen * 0.38, glass, sx * (wid / 2 - 4.5), cabY + 1, cabZ + cabLen * 0.2));
                g.getChildren().add(Gfx.box(1, cabH - 5, cabLen * 0.3, glass, sx * (wid / 2 - 4.5), cabY + 1, cabZ - cabLen * 0.22));
            }
            g.getChildren().add(Gfx.box(0.6, 12, 0.6, Mats.BLACK_METAL, sx * (wid / 2 + 0.1), -16, cabZ));
            g.getChildren().add(Gfx.box(1, 1.4, 5, Mats.CHROME, sx * (wid / 2 + 0.3), -18, cabZ + 8));
            g.getChildren().add(Gfx.box(4, 3.5, 5, Mats.BLACK_METAL, sx * (wid / 2 + 3.5), -cabH - 17, cabZ + cabLen / 2 - 5));
        }
        if (kind == PICKUP) {
            g.getChildren().add(Gfx.box(wid - 4, 2, len * 0.46, body, 0, -19, -hl * 0.45));
            for (int sx = -1; sx <= 1; sx += 2) {
                g.getChildren().add(Gfx.box(2, 12, len * 0.46, body, sx * (wid / 2 - 1), -25, -hl * 0.45));
            }
            g.getChildren().add(Gfx.box(wid - 4, 12, 2, body, 0, -25, -hl + 6));
        }
        if (kind == SUV) {
            g.getChildren().add(Gfx.box(1.5, 1.5, cabLen * 0.8, Mats.CHROME, -wid / 2 + 8, -23 - cabH - 3, cabZ));
            g.getChildren().add(Gfx.box(1.5, 1.5, cabLen * 0.8, Mats.CHROME, wid / 2 - 8, -23 - cabH - 3, cabZ));
        }
        // bumpers, grille, lights, plate
        g.getChildren().add(Gfx.box(wid + 2, 5, 5, wreck ? Mats.RUST : Mats.CHROME, 0, -10, hl + 1));
        g.getChildren().add(Gfx.box(wid + 2, 5, 5, wreck ? Mats.RUST : Mats.CHROME, 0, -10, -hl - 1));
        g.getChildren().add(Gfx.box(wid * 0.5, 7, 1, Mats.BLACK_METAL, 0, -18, hl + 0.2));
        PhongMaterial headL = wreck ? Mats.GLASS_DARK : Gfx.glow(Color.rgb(120, 120, 96));
        PhongMaterial tailL = Gfx.glow(wreck ? Color.rgb(90, 16, 14) : Color.rgb(150, 20, 18));
        for (int sx = -1; sx <= 1; sx += 2) {
            g.getChildren().add(Gfx.box(11, 6, 2, headL, sx * (wid / 2 - 9), -19, hl + 0.3));
            g.getChildren().add(Gfx.box(9, 5, 2, tailL, sx * (wid / 2 - 8), -19, -hl - 0.3));
        }
        g.getChildren().add(Gfx.box(14, 6, 0.8, Gfx.mat(Color.rgb(200, 200, 190), Color.BLACK, 1), 0, -12, -hl - 0.6));
        // wheels
        double[] wz = {hl - 20, -hl + 20};
        int bad = wreck ? rn.nextInt(4) : -1, n = 0;
        for (double z : wz) {
            for (int sx = -1; sx <= 1; sx += 2) {
                boolean flat = n++ == bad;
                double r = flat ? 8.2 : 10.5;
                g.getChildren().add(Gfx.cylX(r, 9, Mats.TIRE, sx * (wid / 2 - 3), -r, z));
                g.getChildren().add(Gfx.cylX(r * 0.62, 9.6, wreck ? Mats.RUST : Mats.CHROME, sx * (wid / 2 - 3), -r, z));
                g.getChildren().add(Gfx.cylX(2, 10.2, Mats.BLACK_METAL, sx * (wid / 2 - 3), -r, z));
            }
        }
        // weathering: dents, grime skirt
        if (wreck) {
            for (int i = 0; i < 3; i++) {
                Box d = Gfx.box(8 + rn.nextDouble() * 10, 4, 6, Mats.RUST, (rn.nextDouble() - 0.5) * wid, -20 - rn.nextDouble() * 6, (rn.nextDouble() - 0.5) * len);
                d.setRotate(rn.nextDouble() * 30);
                d.setRotationAxis(Rotate.Y_AXIS);
                g.getChildren().add(d);
            }
            g.getChildren().add(Gfx.box(wid + 0.6, 4, len + 0.6, Gfx.mat(Color.rgb(50, 40, 30, 0.55), Color.BLACK, 1), 0, -5, 0));
        }
        return g;
    }
}
