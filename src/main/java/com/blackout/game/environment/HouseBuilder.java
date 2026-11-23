package com.blackout.game.environment;

import static com.blackout.game.core.Constants.GROUND_Y;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import com.blackout.game.core.Dims;
import com.blackout.game.core.Gfx;
import com.blackout.game.core.Mats;
import com.blackout.game.environment.HouseLayout.Opening;
import com.blackout.game.environment.HouseLayout.Prop;
import com.blackout.game.environment.HouseLayout.Room;
import com.blackout.game.environment.HouseLayout.Side;

import javafx.scene.Group;
import javafx.scene.paint.Color;
import javafx.scene.paint.PhongMaterial;
import javafx.scene.transform.Rotate;

/**
 * Turns a {@link HouseLayout} into 3D geometry and collision: exterior walls with a door and windows, interior partitions with
 * doorways, per-room floors, ceiling lights and all furniture. The layout is in local coordinates (front = +z); this class
 * mirrors z for houses that face the other way.
 */
public final class HouseBuilder {

    /** Registers a collision box; implemented by the game (Main.addObs). */
    public interface Collision {

        Obstacle add(double x, double z, double w, double d, double hAbove, boolean zombiePass);
    }

    /** World-space points produced while building. */
    public static final class Result {

        public final List<double[]> beds = new ArrayList<>(), spawns = new ArrayList<>(), loot = new ArrayList<>();
    }

    private static final PhongMaterial PLASTER = Gfx.mat(Color.rgb(178, 170, 154), Color.rgb(20, 20, 20), 6);
    private static final PhongMaterial PLASTER_BURNT = Gfx.mat(Color.rgb(48, 44, 42), Color.rgb(8, 8, 8), 2);
    private static final PhongMaterial LIT_GLASS = Gfx.glow(Color.rgb(255, 205, 110));
    private static final PhongMaterial FIRE_GLASS = Gfx.glow(Color.rgb(255, 110, 30));
    private static final PhongMaterial LAMP_GLOW = Gfx.glow(Color.rgb(255, 214, 140));
    private static final PhongMaterial[] CURTAINS = {Gfx.mat(Color.rgb(140, 60, 56), Color.BLACK, 1), Gfx.mat(Color.rgb(196, 188, 164), Color.BLACK, 1),
        Gfx.mat(Color.rgb(70, 92, 112), Color.BLACK, 1)};

    private final Building b;
    private final HouseLayout L;
    private final Collision col;
    private final Random rn;
    private final boolean burning;
    private final Group g;
    private final double bx, bz, T = Dims.HOUSE_WALL_T, H = Dims.HOUSE_WALL_H;
    private final int fr;

    private HouseBuilder(Building b, HouseLayout L, Collision col, Random rn, boolean burning) {
        this.b = b;
        this.L = L;
        this.col = col;
        this.rn = rn;
        this.burning = burning;
        this.g = b.group;
        this.bx = b.x;
        this.bz = b.z;
        this.fr = b.front;
    }

    public static Result build(Building b, HouseLayout L, PhongMaterial wall, boolean burning, Collision col, Random rn) {
        b.layout = L;
        HouseBuilder hb = new HouseBuilder(b, L, col, rn, burning);
        Result res = new Result();
        hb.floors();
        hb.exteriorWalls(wall);
        hb.interiorWalls();
        hb.frontDoor();
        hb.props(res);
        hb.lights();
        for (double[] p : L.spawnPoints) {
            res.spawns.add(new double[]{hb.bx + p[0], hb.wz(p[1])});
        }
        for (double[] p : L.lootSpots) {
            res.loot.add(new double[]{hb.bx + p[0], hb.wz(p[1])});
        }
        return res;
    }

    private double wz(double lz) {
        return bz + fr * lz;
    }

    // ------------------------------------------------------------------ walls

    /**
     * A wall made of solid pieces with openings. {@code ops} are {centre, width, kind} with kind 0 = door, 1 = window, 2 = doorway,
     * in local coordinates along the wall.
     */
    private void wall(boolean alongX, double fixedLocal, double from, double to, double thick, List<double[]> ops, PhongMaterial m) {
        double fixedWorld = alongX ? wz(fixedLocal) : bx + fixedLocal;
        ops.sort((a, c) -> Double.compare(a[0], c[0]));
        double cur = from;
        for (double[] op : ops) {
            double a = op[0] - op[1] / 2, e = op[0] + op[1] / 2;
            piece(alongX, fixedWorld, cur, a, thick, 0, H, m, true);
            int kind = (int) op[2];
            if (kind == 0) {
                piece(alongX, fixedWorld, a, e, thick, Dims.HOUSE_DOOR_H, H - Dims.HOUSE_DOOR_H, m, false);
            } else if (kind == 2) {
                piece(alongX, fixedWorld, a, e, thick, Dims.DOORWAY_H, H - Dims.DOORWAY_H, m, false);
            } else {
                piece(alongX, fixedWorld, a, e, thick, 0, Dims.WINDOW_SILL, m, true);
                piece(alongX, fixedWorld, a, e, thick, Dims.WINDOW_HEAD, H - Dims.WINDOW_HEAD, m, false);
                windowDressing(alongX, fixedWorld, op[0], op[1], thick);
            }
            cur = e;
        }
        piece(alongX, fixedWorld, cur, to, thick, 0, H, m, true);
    }

    /** One solid piece between local positions {@code from}..{@code to}, from height yBot up for hh. */
    private void piece(boolean alongX, double fixedWorld, double from, double to, double thick, double yBot, double hh, PhongMaterial m, boolean collide) {
        double len = to - from;
        if (len < 0.5 || hh < 0.5) {
            return;
        }
        double mid = (from + to) / 2, cx = alongX ? bx + mid : fixedWorld, cz = alongX ? fixedWorld : wz(mid);
        double sx = alongX ? len : thick, sz = alongX ? thick : len;
        g.getChildren().add(Gfx.box(sx, hh, sz, m, cx, GROUND_Y - yBot - hh / 2, cz));
        if (collide) {
            col.add(cx, cz, sx, sz, yBot + hh, false);
        }
    }

    private void windowDressing(boolean alongX, double fixedWorld, double center, double width, double thick) {
        double cx = alongX ? bx + center : fixedWorld, cz = alongX ? fixedWorld : wz(center);
        double hh = Dims.WINDOW_HEAD - Dims.WINDOW_SILL, mid = GROUND_Y - Dims.WINDOW_SILL - hh / 2;
        PhongMaterial glass = burning ? FIRE_GLASS : rn.nextDouble() < 0.28 ? LIT_GLASS : Mats.GLASS_DARK;
        g.getChildren().add(Gfx.box(alongX ? width - 2 : 1.5, hh - 2, alongX ? 1.5 : width - 2, glass, cx, mid, cz));
        // frame: sill plus a cross of muntins
        g.getChildren().add(Gfx.box(alongX ? width + 6 : thick + 5, 3, alongX ? thick + 5 : width + 6, Mats.WOOD_DARK, cx, GROUND_Y - Dims.WINDOW_SILL - 1.5, cz));
        g.getChildren().add(Gfx.box(alongX ? 2 : 2.4, hh, alongX ? 2.4 : 2, Mats.WOOD_DARK, cx, mid, cz));
        g.getChildren().add(Gfx.box(alongX ? width : 2.4, 2, alongX ? 2.4 : width, Mats.WOOD_DARK, cx, mid, cz));
        if (!burning && rn.nextDouble() < 0.4) {                      // curtains hang just inside the glass
            PhongMaterial cm = CURTAINS[rn.nextInt(CURTAINS.length)];
            double inward = thick / 2 + 2.2;
            double toCentre = alongX ? (bz > fixedWorld ? 1 : -1) : (bx > fixedWorld ? 1 : -1);
            for (int s = -1; s <= 1; s += 2) {
                double off = s * (width / 2 - 2);
                double px = alongX ? cx + off : fixedWorld + toCentre * inward;
                double pz = alongX ? fixedWorld + toCentre * inward : cz + off;
                g.getChildren().add(Gfx.box(alongX ? 10 : 2, hh + 16, alongX ? 2 : 10, cm, px, mid - 4, pz));
            }
        }
    }

    private void exteriorWalls(PhongMaterial wall) {
        List<double[]> front = new ArrayList<>(), back = new ArrayList<>(), left = new ArrayList<>(), right = new ArrayList<>();
        for (Opening o : L.openings) {
            double[] op = {o.center, o.width, o.door ? 0 : 1};
            switch (o.side) {
                case FRONT: front.add(op); break;
                case BACK: back.add(op); break;
                case LEFT: left.add(op); break;
                default: right.add(op); break;
            }
        }
        double fz = L.innerD / 2 + T / 2, sx = L.innerW / 2 + T / 2;
        wall(true, fz, -b.w / 2, b.w / 2, T, front, wall);
        wall(true, -fz, -b.w / 2, b.w / 2, T, back, wall);
        wall(false, -sx, -L.innerD / 2, L.innerD / 2, T, left, wall);
        wall(false, sx, -L.innerD / 2, L.innerD / 2, T, right, wall);
    }

    private void interiorWalls() {
        PhongMaterial m = burning ? PLASTER_BURNT : PLASTER;
        for (HouseLayout.Wall w : L.walls) {
            List<double[]> ops = new ArrayList<>();
            for (double[] o : w.openings) {
                ops.add(new double[]{o[0], o[1], 2});
            }
            wall(w.alongX, w.fixed, w.from, w.to, L.P, ops, m);
        }
    }

    private void frontDoor() {
        double dx = bx + L.doorX, fz = wz(L.innerD / 2 + T / 2), dw = Dims.HOUSE_DOOR_W, dh = Dims.HOUSE_DOOR_H;
        b.doorX = dx;
        b.doorZ = fz;
        b.outX = dx;
        b.outZ = fz + fr * 38;
        b.inX = dx;
        b.inZ = fz - fr * 38;
        // taller than the climb limit so a closed door cannot be climbed over
        b.doorObs = col.add(dx, fz, dw, T, dh + 12, false);
        g.getChildren().add(Gfx.box(3, dh, T + 2, Mats.WOOD_DARK, dx - dw / 2 - 1.5, GROUND_Y - dh / 2, fz));
        g.getChildren().add(Gfx.box(3, dh, T + 2, Mats.WOOD_DARK, dx + dw / 2 + 1.5, GROUND_Y - dh / 2, fz));
        g.getChildren().add(Gfx.box(dw + 6, 4, T + 2, Mats.WOOD_DARK, dx, GROUND_Y - dh - 2, fz));
        Group leaf = new Group();
        leaf.setTranslateX(dx - dw / 2 + 1);
        leaf.setTranslateY(GROUND_Y);
        leaf.setTranslateZ(fz);
        b.doorRot = new Rotate(0, Rotate.Y_AXIS);
        leaf.getTransforms().add(b.doorRot);
        leaf.getChildren().add(Gfx.box(dw - 2, dh - 2, 3, Mats.WOOD, (dw - 2) / 2, -(dh - 2) / 2, 0));
        leaf.getChildren().add(Gfx.box(dw - 14, dh * 0.38, 3.6, Mats.WOOD_DARK, (dw - 2) / 2, -dh * 0.7, 0));
        leaf.getChildren().add(Gfx.box(3, 3, 6, Mats.CHROME, dw - 9, -dh * 0.46, 0));
        g.getChildren().add(leaf);
    }

    // ------------------------------------------------------------------ floors, lights, props

    private void floors() {
        for (Room r : L.rooms) {
            PhongMaterial m;
            double tile;
            switch (r.type) {
                case BATH:
                case WC:
                    m = Mats.CONCRETE;
                    tile = 48;
                    break;
                case BEDROOM:
                    m = Mats.WOOD_DARK;
                    tile = 64;
                    break;
                default:
                    m = Mats.WOOD;
                    tile = 64;
                    break;
            }
            g.getChildren().add(Gfx.plane(bx + r.cx(), wz(r.cz()), r.x1 - r.x0, r.z1 - r.z0, tile, GROUND_Y - 1.0, m));
        }
        for (HouseLayout.Doorway dw : L.doorways) {           // thresholds, so no grass shows through the doorway
            g.getChildren().add(Gfx.box(dw.width, 1, L.P + 0.6, Mats.WOOD_DARK, bx + dw.cx, GROUND_Y - 0.5, wz(dw.z)));
        }
    }

    private void lights() {
        if (burning) {
            return;
        }
        for (Room r : L.rooms) {
            g.getChildren().add(Gfx.sph(3, LAMP_GLOW, bx + r.cx(), GROUND_Y - H + 8, wz(r.cz()), 1, 1, 1));
        }
    }

    private void props(Result res) {
        for (Prop p : L.props) {
            Group m = HouseProps.create(p, rn);
            m.setTranslateX(bx + p.x);
            m.setTranslateY(GROUND_Y - 1.0);
            m.setTranslateZ(wz(p.z));
            double yaw = fr > 0 ? p.yaw : 180 - p.yaw;            // mirroring z flips the facing direction
            m.getTransforms().add(new Rotate(yaw, Rotate.Y_AXIS));
            g.getChildren().add(m);
            if (p.collide) {
                double[] a = p.aabb();
                col.add(bx + (a[0] + a[2]) / 2, wz((a[1] + a[3]) / 2), a[2] - a[0], a[3] - a[1], p.h, true);
            }
            if (p.kind == HouseLayout.Kind.BED) {
                res.beds.add(new double[]{bx + p.x, wz(p.z)});
            }
        }
    }
}
