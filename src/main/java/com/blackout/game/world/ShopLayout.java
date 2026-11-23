package com.blackout.game.world;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Interior of a shop or supermarket (pure data). Local coordinates: origin at the building centre, +z toward the front door.
 * The entrance area and a central cross aisle stay free, so every aisle can be reached from the door.
 */
public final class ShopLayout {

    public enum Kind { SHELF, COUNTER, FRIDGE, CRATES }

    public static final class Fixture {

        public final Kind kind;
        public final double x, z, yaw, w, d, h;

        Fixture(Kind kind, double x, double z, double yaw, double w, double d, double h) {
            this.kind = kind;
            this.x = x;
            this.z = z;
            this.yaw = yaw;
            this.w = w;
            this.d = d;
            this.h = h;
        }

        /** {x0, z0, x1, z1} of the rotated footprint. */
        public double[] aabb() {
            double r = Math.toRadians(yaw), c = Math.abs(Math.cos(r)), s = Math.abs(Math.sin(r));
            double hx = (w * c + d * s) / 2, hz = (w * s + d * c) / 2;
            return new double[]{x - hx, z - hz, x + hx, z + hz};
        }
    }

    public static final double T = 8, ENTRANCE = 84;

    public final double w, d, innerW, innerD;
    /** Depth of the free entrance area (smaller in small kiosks). */
    public final double entrance;
    public final boolean supermarket;
    public final List<Fixture> fixtures = new ArrayList<>();
    public final List<double[]> spawnPoints = new ArrayList<>(), lootSpots = new ArrayList<>();

    private ShopLayout(double w, double d, boolean supermarket) {
        this.w = w;
        this.d = d;
        this.supermarket = supermarket;
        this.innerW = w - 2 * T;
        this.innerD = d - 2 * T;
        this.entrance = Math.min(ENTRANCE, innerD * 0.45);
    }

    public static ShopLayout generate(double w, double d, Random rn, boolean supermarket) {
        ShopLayout L = new ShopLayout(w, d, supermarket);
        double iw = L.innerW, id = L.innerD, zf = id / 2, zb = -id / 2, len = 70;
        if (supermarket) {
            // checkouts flank the entrance lane, fridges line the back wall, display islands stand in the middle
            for (int s = -1; s <= 1; s += 2) {
                L.fixtures.add(new Fixture(Kind.COUNTER, s * (iw / 2 - 40), zf - 24, 0, 60, 40, 44));
            }
            int n = (int) Math.floor((iw - 64) / 64);          // fridges stay between the side-wall shelves
            for (int i = 0; i < n; i++) {
                L.fixtures.add(new Fixture(Kind.FRIDGE, -n * 32 + 32 + i * 64, zb + 17, 0, 60, 32, 110));
            }
            shelvesAlongWalls(L, zf - 24 - 20 - 4 - 30, zb + 4 + 30, 60);
            for (int s = -1; s <= 1; s += 2) {
                L.fixtures.add(new Fixture(Kind.CRATES, s * 62, (zf + zb) / 2 + (rn.nextDouble() - 0.5) * 20, rn.nextInt(30) - 15, 40, 40, 40));
            }
        } else {
            // shelves along both side walls; a small service counter at the back, between the shelves
            shelvesAlongWalls(L, zf - 14 - len / 2, zb + 4 + len / 2, len);
            double cw = iw - 2 * (iw >= 128 ? 28 : 22) - 28;
            if (cw >= 30 && id >= 120) {                 // kiosks are too small for a service counter
                L.fixtures.add(new Fixture(Kind.COUNTER, 0, zb + 22, 0, Math.min(110, cw), 40, 44));
            }
        }
        // free floor (checked on a grid) to start zombies on and to leave supplies on: well clear of fixtures and the entrance
        List<double[]> pool = new ArrayList<>();
        for (double z = zb + 30; z <= zf - L.entrance + 6; z += 14) {
            for (double x = -iw / 2 + 30; x <= iw / 2 - 30; x += 4) {
                if (L.clearance(x, z) >= (id < 120 ? 24 : 30)) {     // tiny kiosks have little free floor
                    pool.add(new double[]{x, z});
                }
            }
        }
        for (int i = 0; i < 5 && !pool.isEmpty(); i++) {
            L.spawnPoints.add(pool.remove(rn.nextInt(pool.size())));
        }
        for (Fixture f : L.fixtures) {
            if (L.lootSpots.size() >= 4) {
                break;
            }
            double[] a = f.aabb();
            for (double[] c : new double[][]{{(a[0] + a[2]) / 2, a[3] + 28}, {(a[0] + a[2]) / 2, a[1] - 28}, {a[0] - 28, (a[1] + a[3]) / 2}, {a[2] + 28, (a[1] + a[3]) / 2}}) {
                if (c[1] < zf - L.entrance && L.clearance(c[0], c[1]) >= 24) {
                    L.lootSpots.add(c);
                    break;
                }
            }
        }
        return L;
    }

    /** Shelves standing against both side walls, from {@code zStart} (nearest the door) back to {@code zEnd}. */
    private static void shelvesAlongWalls(ShopLayout L, double zStart, double zEnd, double len) {
        double depth = L.innerW >= 128 ? 28 : 22;                 // slimmer shelves in narrow shops keep the middle aisle open
        for (double z = zStart; z >= zEnd - 0.01; z -= len + 2) {
            L.fixtures.add(new Fixture(Kind.SHELF, -L.innerW / 2 + depth / 2, z, 90, len, depth, 84));
            L.fixtures.add(new Fixture(Kind.SHELF, L.innerW / 2 - depth / 2, z, 270, len, depth, 84));
        }
    }

    /** Distance from a point to the nearest fixture or wall. */
    public double clearance(double x, double z) {
        double m = Math.min(innerW / 2 - Math.abs(x), innerD / 2 - Math.abs(z));
        for (Fixture f : fixtures) {
            double[] a = f.aabb();
            double dx = Math.max(Math.max(a[0] - x, x - a[2]), 0), dz = Math.max(Math.max(a[1] - z, z - a[3]), 0);
            m = Math.min(m, Math.hypot(dx, dz));
        }
        return m;
    }
}
