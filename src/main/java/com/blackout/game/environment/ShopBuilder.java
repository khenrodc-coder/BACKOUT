package com.blackout.game.environment;

import static com.blackout.game.core.Constants.GROUND_Y;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import com.blackout.game.core.Dims;
import com.blackout.game.core.Gfx;
import com.blackout.game.core.Mats;
import com.blackout.game.world.ShopLayout;

import javafx.scene.Group;
import javafx.scene.paint.Color;
import javafx.scene.paint.PhongMaterial;
import javafx.scene.transform.Rotate;

/**
 * Builds an enterable shop (convenience store, supermarket or fuel-station kiosk): walls with a shopfront door and windows, a flat
 * roof, a lit interior with stocked shelves, counters and fridges, and collision for all of it. Like houses, the layout is local
 * (front = +z) and z is mirrored for buildings that face the other way.
 */
public final class ShopBuilder {

    public static final double FLOOR_Y = 108.0;

    private static final PhongMaterial FLOOR_LIGHT = Mats.CONCRETE, ROOF = Mats.TAR_ROOF, LIT = Gfx.glow(Color.rgb(255, 214, 140)),
            LIT_GLASS = Gfx.glow(Color.rgb(255, 205, 110)), CEILING_LIGHT = Gfx.glow(Color.rgb(235, 240, 255)), TRIM = Mats.BLACK_METAL;

    private final Building b;
    private final ShopLayout L;
    private final HouseBuilder.Collision col;
    private final Random rn;
    private final Group g;
    private final double bx, bz, T = ShopLayout.T, H;
    private final int fr;

    private ShopBuilder(Building b, ShopLayout L, HouseBuilder.Collision col, Random rn) {
        this.b = b;
        this.L = L;
        this.col = col;
        this.rn = rn;
        this.g = b.group;
        this.bx = b.x;
        this.bz = b.z;
        this.fr = b.front;
        this.H = b.h;
    }

    public static HouseBuilder.Result build(Building b, ShopLayout L, PhongMaterial wall, PhongMaterial signGlow, HouseBuilder.Collision col, Random rn) {
        ShopBuilder sb = new ShopBuilder(b, L, col, rn);
        HouseBuilder.Result res = new HouseBuilder.Result();
        sb.floor();
        sb.walls(wall);
        sb.frontDoor(signGlow);
        sb.roof();
        sb.fixtures();
        sb.lights();
        for (double[] p : L.spawnPoints) {
            res.spawns.add(new double[]{sb.bx + p[0], sb.wz(p[1])});
        }
        for (double[] p : L.lootSpots) {
            res.loot.add(new double[]{sb.bx + p[0], sb.wz(p[1])});
        }
        return res;
    }

    private double wz(double lz) {
        return bz + fr * lz;
    }

    /** ops: {centre, width, kind (0 door, 1 window), sill, head}. */
    private void wall(boolean alongX, double fixedLocal, double from, double to, List<double[]> ops, PhongMaterial m) {
        double fixedWorld = alongX ? wz(fixedLocal) : bx + fixedLocal;
        ops.sort((a, c) -> Double.compare(a[0], c[0]));
        double cur = from;
        for (double[] op : ops) {
            double a = op[0] - op[1] / 2, e = op[0] + op[1] / 2;
            piece(alongX, fixedWorld, cur, a, 0, H, m, true);
            if (op[2] == 0) {
                piece(alongX, fixedWorld, a, e, Dims.HOUSE_DOOR_H, H - Dims.HOUSE_DOOR_H, m, false);
            } else {
                piece(alongX, fixedWorld, a, e, 0, op[3], m, true);
                piece(alongX, fixedWorld, a, e, op[4], H - op[4], m, false);
                glass(alongX, fixedWorld, op);
            }
            cur = e;
        }
        piece(alongX, fixedWorld, cur, to, 0, H, m, true);
    }

    private void piece(boolean alongX, double fixedWorld, double from, double to, double yBot, double hh, PhongMaterial m, boolean collide) {
        double len = to - from;
        if (len < 0.5 || hh < 0.5) {
            return;
        }
        double mid = (from + to) / 2, cx = alongX ? bx + mid : fixedWorld, cz = alongX ? fixedWorld : wz(mid);
        double sx = alongX ? len : T, sz = alongX ? T : len;
        g.getChildren().add(Gfx.box(sx, hh, sz, m, cx, GROUND_Y - yBot - hh / 2, cz));
        if (collide) {
            col.add(cx, cz, sx, sz, yBot + hh, false);
        }
    }

    private void glass(boolean alongX, double fixedWorld, double[] op) {
        double cx = alongX ? bx + op[0] : fixedWorld, cz = alongX ? fixedWorld : wz(op[0]), hh = op[4] - op[3], mid = GROUND_Y - op[3] - hh / 2;
        PhongMaterial gm = rn.nextDouble() < 0.3 ? LIT_GLASS : Mats.GLASS_DARK;
        g.getChildren().add(Gfx.box(alongX ? op[1] - 2 : 1.5, hh - 2, alongX ? 1.5 : op[1] - 2, gm, cx, mid, cz));
        g.getChildren().add(Gfx.box(alongX ? op[1] + 6 : T + 3, 3, alongX ? T + 3 : op[1] + 6, TRIM, cx, GROUND_Y - op[3] - 1.5, cz));
        g.getChildren().add(Gfx.box(alongX ? 2 : 2.4, hh, alongX ? 2.4 : 2, TRIM, cx, mid, cz));
    }

    private void walls(PhongMaterial m) {
        double zf = L.innerD / 2 + T / 2, sx = L.innerW / 2 + T / 2;
        List<double[]> front = new ArrayList<>(), side = new ArrayList<>();
        front.add(new double[]{0, Dims.HOUSE_DOOR_W, 0, 0, 0});
        if (b.w >= 200) {
            for (int s = -1; s <= 1; s += 2) {
                front.add(new double[]{s * (32 + 10 + 45), 90, 1, 24, 112});
            }
        }
        if (b.d >= 150) {
            side.add(new double[]{L.innerD / 2 - 60, 56, 1, 40, 112});
        }
        wall(true, zf, -b.w / 2, b.w / 2, front, m);
        wall(true, -zf, -b.w / 2, b.w / 2, new ArrayList<>(), m);
        wall(false, -sx, -L.innerD / 2, L.innerD / 2, new ArrayList<>(side), m);
        wall(false, sx, -L.innerD / 2, L.innerD / 2, new ArrayList<>(side), m);
    }

    private void frontDoor(PhongMaterial signGlow) {
        double dx = bx, fz = wz(L.innerD / 2 + T / 2), dw = Dims.HOUSE_DOOR_W, dh = Dims.HOUSE_DOOR_H;
        b.doorX = dx;
        b.doorZ = fz;
        b.outX = dx;
        b.outZ = fz + fr * 38;
        b.inX = dx;
        b.inZ = fz - fr * 38;
        b.doorObs = col.add(dx, fz, dw, T, dh + 12, false);
        g.getChildren().add(Gfx.box(3, dh, T + 2, TRIM, dx - dw / 2 - 1.5, GROUND_Y - dh / 2, fz));
        g.getChildren().add(Gfx.box(3, dh, T + 2, TRIM, dx + dw / 2 + 1.5, GROUND_Y - dh / 2, fz));
        g.getChildren().add(Gfx.box(dw + 6, 4, T + 2, TRIM, dx, GROUND_Y - dh - 2, fz));
        Group leaf = new Group();
        leaf.setTranslateX(dx - dw / 2 + 1);
        leaf.setTranslateY(GROUND_Y);
        leaf.setTranslateZ(fz);
        b.doorRot = new Rotate(0, Rotate.Y_AXIS);
        leaf.getTransforms().add(b.doorRot);
        leaf.getChildren().add(Gfx.box(dw - 2, dh - 2, 3, TRIM, (dw - 2) / 2, -(dh - 2) / 2, 0));
        leaf.getChildren().add(Gfx.box(dw - 12, dh - 30, 3.6, Mats.GLASS_DARK, (dw - 2) / 2, -(dh - 2) / 2 - 6, 0));
        leaf.getChildren().add(Gfx.box(3, 24, 6, Mats.CHROME, dw - 9, -dh * 0.5, 0));
        g.getChildren().add(leaf);
        // sign above the door and a small awning over the pavement
        double outward = fr * (T / 2 + 2);
        g.getChildren().add(Gfx.box(Math.min(b.w - 20, 110), 22, 4, signGlow, dx, GROUND_Y - dh - 22, fz + outward));
        PhongMaterial awning = Gfx.mat(Color.rgb(150 + rn.nextInt(60), 50 + rn.nextInt(60), 50 + rn.nextInt(60)), Color.BLACK, 1);
        g.getChildren().add(Gfx.box(dw + 40, 3, 26, awning, dx, GROUND_Y - dh - 6, fz + fr * (T / 2 + 13)));
    }

    private void floor() {
        g.getChildren().add(Gfx.plane(bx, bz, L.innerW, L.innerD, 48, FLOOR_Y, FLOOR_LIGHT));
    }

    private void roof() {
        double w = b.w + 8, d = b.d + 8;
        b.roof.getChildren().add(Gfx.box(w, 6, d, ROOF, bx, GROUND_Y - H - 3, bz));
        b.roof.getChildren().add(Gfx.box(w, 8, 3, Mats.CONCRETE, bx, GROUND_Y - H - 10, bz + d / 2 - 1.5));
        b.roof.getChildren().add(Gfx.box(w, 8, 3, Mats.CONCRETE, bx, GROUND_Y - H - 10, bz - d / 2 + 1.5));
        b.roof.getChildren().add(Gfx.box(3, 8, d, Mats.CONCRETE, bx + w / 2 - 1.5, GROUND_Y - H - 10, bz));
        b.roof.getChildren().add(Gfx.box(3, 8, d, Mats.CONCRETE, bx - w / 2 + 1.5, GROUND_Y - H - 10, bz));
        int units = 1 + rn.nextInt(3);
        for (int i = 0; i < units; i++) {
            b.roof.getChildren().add(Gfx.box(26, 14, 26, Mats.STEEL_GREY, bx + (rn.nextDouble() - 0.5) * (w - 60), GROUND_Y - H - 13, bz + (rn.nextDouble() - 0.5) * (d - 60)));
        }
        g.getChildren().add(b.roof);
    }

    private void fixtures() {
        for (ShopLayout.Fixture f : L.fixtures) {
            Group m = ShopProps.create(f, rn);
            m.setTranslateX(bx + f.x);
            m.setTranslateY(FLOOR_Y);
            m.setTranslateZ(wz(f.z));
            double yaw = fr > 0 ? f.yaw : 180 - f.yaw;
            m.getTransforms().add(new Rotate(yaw, Rotate.Y_AXIS));
            g.getChildren().add(m);
            double[] a = f.aabb();
            col.add(bx + (a[0] + a[2]) / 2, wz((a[1] + a[3]) / 2), a[2] - a[0], a[3] - a[1], f.h, true);
        }
    }

    private void lights() {
        for (double z = -L.innerD / 2 + 36; z < L.innerD / 2 - 20; z += 70) {
            g.getChildren().add(Gfx.box(Math.max(30, L.innerW - 60), 2, 8, CEILING_LIGHT, bx, GROUND_Y - H + 3, wz(z)));
        }
    }
}
