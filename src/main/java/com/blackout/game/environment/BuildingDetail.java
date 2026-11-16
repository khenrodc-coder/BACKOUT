package com.blackout.game.environment;

import com.blackout.game.core.Gfx;
import com.blackout.game.core.Mats;

import java.util.Random;

import javafx.scene.Group;
import javafx.scene.paint.Color;
import javafx.scene.paint.PhongMaterial;
import javafx.scene.shape.Box;
import javafx.scene.transform.Rotate;

/** Architectural extras for houses: foundation, porch + balcony, chimney, shutters, boarded windows, fake ambient occlusion. */
public final class BuildingDetail {

    private BuildingDetail() {
    }

    public static void foundation(Group g, double bx, double bz, double w, double d, double gy) {
        g.getChildren().add(Gfx.box(w + 5, 9, d + 5, Mats.CONCRETE, bx, gy - 3.5, bz));
        g.getChildren().add(Gfx.box(w + 40, 0.3, d + 40, Gfx.mat(Color.rgb(0, 0, 0, 0.32), Color.BLACK, 1), bx, gy - 0.8, bz));
    }

    public static void porch(Group g, double bx, double fz, int front, double wallH, double gy, Random rn) {
        double pw = 96, pd = 46, py = gy - 9, out = front * (pd / 2 + 3);
        g.getChildren().add(Gfx.box(pw, 9, pd, Mats.CONCRETE, bx, gy - 4.5, fz + out));
        g.getChildren().add(Gfx.box(pw - 30, 4.5, 14, Mats.CONCRETE, bx, gy - 2.2, fz + front * (pd + 12)));
        double cy = gy - wallH * 0.82;
        for (int sx = -1; sx <= 1; sx += 2) {
            g.getChildren().add(Gfx.box(5, wallH * 0.82 - 9, 5, Mats.WOOD_DARK, bx + sx * (pw / 2 - 5), (py + cy) / 2, fz + front * (pd - 2)));
        }
        g.getChildren().add(Gfx.box(pw + 8, 4, pd + 6, Mats.WOOD_DARK, bx, cy, fz + out));
        for (int i = 0; i <= 8; i++) {
            if (rn.nextDouble() < 0.85) {
                g.getChildren().add(Gfx.box(2, 16, 2, Mats.WOOD, bx - pw / 2 - 2 + i * (pw + 4) / 8, cy - 10, fz + front * (pd + 2)));
            }
        }
        g.getChildren().add(Gfx.box(pw + 4, 2, 2, Mats.WOOD, bx, cy - 18, fz + front * (pd + 2)));
    }

    public static void chimney(Group roof, double x, double z, double topY) {
        roof.getChildren().add(Gfx.box(14, 44, 14, Mats.BRICK_RED, x, topY - 12, z));
        roof.getChildren().add(Gfx.box(18, 4, 18, Mats.CONCRETE, x, topY - 36, z));
    }

    public static void shutters(Group g, double x, double y, double z, boolean alongX, double w, double h, Random rn) {
        for (int s = -1; s <= 1; s += 2) {
            Box b = alongX ? Gfx.box(w * 0.5, h, 1.6, Mats.WOOD_DARK, x + s * (w * 0.75), y, z + 4) : Gfx.box(1.6, h, w * 0.5, Mats.WOOD_DARK, x + 4, y, z + s * (w * 0.75));
            if (rn.nextDouble() < 0.4) {
                b.setRotationAxis(Rotate.Z_AXIS);
                b.setRotate(s * (8 + rn.nextDouble() * 14));
            }
            g.getChildren().add(b);
        }
    }

    public static void boards(Group g, double x, double y, double z, boolean alongX, double w, double h, Random rn) {
        for (int i = 0; i < 3; i++) {
            double yy = y - h / 2 + (i + 0.5) * h / 3 + (rn.nextDouble() - 0.5) * 3;
            Box b = alongX ? Gfx.box(w + 8, 6, 1.6, Mats.WOOD, x, yy, z + 3.5) : Gfx.box(1.6, 6, w + 8, Mats.WOOD, x + 3.5, yy, z);
            b.setRotationAxis(alongX ? Rotate.Z_AXIS : Rotate.X_AXIS);
            b.setRotate((rn.nextDouble() - 0.5) * 12);
            g.getChildren().add(b);
        }
    }

    public static void gutter(Group roof, double x, double z, double w, double y) {
        roof.getChildren().add(Gfx.box(w + 12, 2.5, 2.5, Mats.BLACK_METAL, x, y, z));
    }
}
