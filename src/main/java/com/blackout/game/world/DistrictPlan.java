package com.blackout.game.world;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Deterministic city-block generator for the downtown district. A chunk is one block (400 x 400 units); roads run along its
 * edges (the central avenue at x = 0 is wider). The plan for a chunk depends only on (world seed, chunk coordinates), so a chunk
 * looks identical every time it is loaded, in any order, and the plan is plain data (no JavaFX).
 */
public final class DistrictPlan {

    public static final double CHUNK = 400;
    /** The district covers x in [-1200, 1200) and z in [1600, 3200): 6 x 4 blocks. */
    public static final int MIN_CX = -3, MAX_CX = 2, MIN_CZ = 4, MAX_CZ = 7;
    public static final double ASPHALT_HALF = 32, SIDEWALK = 14, AVENUE_HALF = 80;

    public enum Kind { OFFICE, APARTMENT, SHOP, SUPERMARKET, GAS_SHOP, CANOPY, PARKING, PLAZA, PARK }

    public static final class Lot {

        public final Kind kind;
        public final double x, z, w, d;
        public final int floors, style;
        /** +1: the door faces +z, -1: it faces -z, 0: no door. */
        public final int front;

        Lot(Kind kind, double x, double z, double w, double d, int floors, int style, int front) {
            this.kind = kind;
            this.x = x;
            this.z = z;
            this.w = w;
            this.d = d;
            this.floors = floors;
            this.style = style;
            this.front = front;
        }

        public boolean solid() {
            return kind == Kind.OFFICE || kind == Kind.APARTMENT || kind == Kind.SHOP || kind == Kind.SUPERMARKET || kind == Kind.GAS_SHOP
                    || kind == Kind.CANOPY;
        }

        public boolean enterable() {
            return kind == Kind.SHOP || kind == Kind.SUPERMARKET || kind == Kind.GAS_SHOP;
        }

        public double[] rect() {
            return new double[]{x - w / 2, z - d / 2, x + w / 2, z + d / 2};
        }
    }

    public static final class Car {

        public final double x, z, yaw;
        public final int kind, color;
        public final boolean wreck;

        Car(double x, double z, double yaw, int kind, int color, boolean wreck) {
            this.x = x;
            this.z = z;
            this.yaw = yaw;
            this.kind = kind;
            this.color = color;
            this.wreck = wreck;
        }
    }

    /** A tree or bench position. */
    public static final class Spot {

        public final double x, z, scale, yaw;
        public final int variant;

        Spot(double x, double z, double scale, double yaw, int variant) {
            this.x = x;
            this.z = z;
            this.scale = scale;
            this.yaw = yaw;
            this.variant = variant;
        }
    }

    public static final class Plan {

        public final long seed;
        public final int cx, cz;
        public final double x0, z0, x1, z1, ix0, iz0, ix1, iz1;
        public final String pattern;
        public final List<Lot> lots = new ArrayList<>();
        public final List<Car> cars = new ArrayList<>();
        public final List<Spot> trees = new ArrayList<>(), benches = new ArrayList<>();
        public final List<double[]> lights = new ArrayList<>();

        Plan(long seed, int cx, int cz, String pattern) {
            this.seed = seed;
            this.cx = cx;
            this.cz = cz;
            this.pattern = pattern;
            x0 = cx * CHUNK;
            x1 = x0 + CHUNK;
            z0 = cz * CHUNK;
            z1 = z0 + CHUNK;
            ix0 = x0 + (cx == 0 ? AVENUE_HALF + SIDEWALK : ASPHALT_HALF + SIDEWALK);
            ix1 = x1 - (cx == -1 ? AVENUE_HALF + SIDEWALK : ASPHALT_HALF + SIDEWALK);
            iz0 = z0 + ASPHALT_HALF + SIDEWALK;
            iz1 = z1 - ASPHALT_HALF - SIDEWALK;
        }
    }

    private DistrictPlan() {
    }

    public static boolean exists(int cx, int cz) {
        return cx >= MIN_CX && cx <= MAX_CX && cz >= MIN_CZ && cz <= MAX_CZ;
    }

    /** Seed for everything random about one chunk (plan and look), independent of load order. */
    public static long chunkSeed(long seed, int cx, int cz) {
        return mix(seed, cx, cz);
    }

    public static long mix(long seed, int cx, int cz) {
        long h = seed * 0x9E3779B97F4A7C15L;
        h ^= cx * 0xC2B2AE3D27D4EB4FL;
        h = Long.rotateLeft(h, 31) * 0x165667B19E3779F9L;
        h ^= cz * 0x27D4EB2F165667C5L;
        h ^= h >>> 29;
        return h * 0xBF58476D1CE4E5B9L;
    }

    /** Chunk that always holds the supermarket (salt 0) or the gas station (salt 1). */
    public static int[] anchor(long seed, int salt) {
        Random r = new Random(seed ^ (salt == 0 ? 0x5151L : 0x6262L));
        int cx = MIN_CX + r.nextInt(MAX_CX - MIN_CX + 1), cz = MIN_CZ + r.nextInt(MAX_CZ - MIN_CZ + 1);
        if (salt == 1) {
            int[] s = anchor(seed, 0);
            if (s[0] == cx && s[1] == cz) {
                cz = MIN_CZ + (cz - MIN_CZ + 1) % (MAX_CZ - MIN_CZ + 1);
            }
        }
        return new int[]{cx, cz};
    }

    /** Named places for the world map: {name, x, z}. */
    public static List<Object[]> landmarks(long seed) {
        List<Object[]> out = new ArrayList<>();
        out.add(new Object[]{"Downtown", 0.0, (MIN_CZ + MAX_CZ + 1) * CHUNK / 2});
        int[] s = anchor(seed, 0), g = anchor(seed, 1);
        out.add(new Object[]{"Supermarket", (s[0] + 0.5) * CHUNK, (s[1] + 0.5) * CHUNK});
        out.add(new Object[]{"Gas Station", (g[0] + 0.5) * CHUNK, (g[1] + 0.5) * CHUNK});
        return out;
    }

    public static Plan generate(long seed, int cx, int cz) {
        Random rn = new Random(mix(seed, cx, cz));
        int[] sup = anchor(seed, 0), gas = anchor(seed, 1);
        double row = (cz - MIN_CZ) / (double) (MAX_CZ - MIN_CZ);       // 0 near the town, 1 at the far end
        String pattern;
        if (cx == sup[0] && cz == sup[1]) {
            pattern = "supermarket";
        } else if (cx == gas[0] && cz == gas[1]) {
            pattern = "gas";
        } else {
            double[] w = {1 + 3 * row, 3 - row, 1.2, 2 - row};          // office, shops, park, apartments
            double t = rn.nextDouble() * (w[0] + w[1] + w[2] + w[3]);
            pattern = t < w[0] ? "office" : t < w[0] + w[1] ? "shops" : t < w[0] + w[1] + w[2] ? "park" : "apartments";
        }
        Plan p = new Plan(seed, cx, cz, pattern);
        double W = p.ix1 - p.ix0, D = p.iz1 - p.iz0, mx = (p.ix0 + p.ix1) / 2, mz = (p.iz0 + p.iz1) / 2;
        switch (pattern) {
            case "office": {
                p.lots.add(new Lot(Kind.PLAZA, mx, mz, W, D, 0, 0, 0));
                double tw = 170 + rn.nextInt(50), td = 170 + rn.nextInt(50);
                p.lots.add(new Lot(Kind.OFFICE, mx + (rn.nextDouble() - 0.5) * 30, mz + (rn.nextDouble() - 0.5) * 30, tw, td, 8 + rn.nextInt(9), rn.nextInt(3), 0));
                scatterTrees(p, rn, 5);
                scatterBenches(p, rn, 3);
                break;
            }
            case "shops": {
                boolean north = rn.nextBoolean();
                double sd = 200, gap = 10, sw = (W - gap) / 2;
                double sz = north ? p.iz1 - sd / 2 : p.iz0 + sd / 2;
                for (int i = 0; i < 2; i++) {
                    p.lots.add(new Lot(Kind.SHOP, p.ix0 + sw / 2 + i * (sw + gap), sz, sw, sd, 1, rn.nextInt(8), north ? 1 : -1));
                }
                double ad = D - sd - 14;
                double az = north ? p.iz0 + ad / 2 : p.iz1 - ad / 2;
                p.lots.add(new Lot(Kind.APARTMENT, mx, az, W, ad, 4 + rn.nextInt(4), rn.nextInt(3), 0));
                break;
            }
            case "supermarket": {
                boolean north = rn.nextBoolean();
                double sd = 200, sw = Math.min(W, 260);
                double sz = north ? p.iz1 - sd / 2 : p.iz0 + sd / 2;
                p.lots.add(new Lot(Kind.SUPERMARKET, mx, sz, sw, sd, 1, 0, north ? 1 : -1));
                double pd = D - sd - 14;
                double pz = north ? p.iz0 + pd / 2 : p.iz1 - pd / 2;
                p.lots.add(new Lot(Kind.PARKING, mx, pz, W, pd, 0, 0, 0));
                for (double x = p.ix0 + 72; x < p.ix1 - 60; x += 136) {      // cars park crosswise in the shallow lot
                    if (rn.nextDouble() < 0.7) {
                        p.cars.add(new Car(x, pz, rn.nextBoolean() ? 90 : 270, rn.nextInt(4), rn.nextInt(6), rn.nextDouble() < 0.3));
                    }
                }
                break;
            }
            case "gas": {
                p.lots.add(new Lot(Kind.PARKING, mx, mz, W, D, 0, 0, 0));
                p.lots.add(new Lot(Kind.GAS_SHOP, p.ix0 + 90, p.iz1 - 55, 140, 110, 1, 2, 1));
                p.lots.add(new Lot(Kind.CANOPY, mx + 20, mz - 10, 180, 70, 1, 0, 0));
                break;
            }
            case "park": {
                p.lots.add(new Lot(Kind.PARK, mx, mz, W, D, 0, 0, 0));
                scatterTrees(p, rn, 12 + rn.nextInt(6));
                scatterBenches(p, rn, 4);
                break;
            }
            default: {                                                   // apartments
                p.lots.add(new Lot(Kind.PLAZA, mx, mz, W, D, 0, 0, 0));
                double b = 130;
                p.lots.add(new Lot(Kind.APARTMENT, p.ix0 + 14 + b / 2, p.iz0 + 14 + b / 2, b, b, 5 + rn.nextInt(4), rn.nextInt(3), 0));
                p.lots.add(new Lot(Kind.APARTMENT, p.ix1 - 14 - b / 2, p.iz1 - 14 - b / 2, b, b, 5 + rn.nextInt(4), rn.nextInt(3), 0));
                scatterTrees(p, rn, 4);
                break;
            }
        }
        // street lights at the block corners (never in front of a door)
        double lx0 = p.ix0 - SIDEWALK / 2, lx1 = p.ix1 + SIDEWALK / 2, lz0 = p.iz0 - SIDEWALK / 2, lz1 = p.iz1 + SIDEWALK / 2;
        p.lights.add(new double[]{lx0, lz0});
        p.lights.add(new double[]{lx1, lz0});
        p.lights.add(new double[]{lx0, lz1});
        p.lights.add(new double[]{lx1, lz1});
        // cars parked along the west and south kerbs (each chunk only does its own two, so neighbours never duplicate);
        // a car is never left where it would block a shop door
        int street = rn.nextInt(3);
        for (int i = 0; i < street; i++) {
            for (int attempt = 0; attempt < 8; attempt++) {
                boolean west = cx != 0 && rn.nextBoolean();
                double x = west ? p.x0 + 6 : p.x0 + 90 + rn.nextDouble() * (CHUNK - 180), z = west ? p.z0 + 90 + rn.nextDouble() * (CHUNK - 180) : p.z0 + 6;
                double yaw = west ? (rn.nextBoolean() ? 0 : 180) : (rn.nextBoolean() ? 90 : 270);
                int kind = rn.nextInt(4), color = rn.nextInt(6);
                boolean wreck = rn.nextDouble() < 0.35;
                if (!blocksDoor(p, x, z, yaw)) {
                    p.cars.add(new Car(x, z, yaw, kind, color, wreck));
                    break;
                }
            }
        }
        return p;
    }

    /** True if a car at this spot (long axis along its yaw) would overlap the approach to an enterable door. */
    public static boolean blocksDoor(Plan p, double x, double z, double yaw) {
        boolean sideways = Math.abs(Math.sin(Math.toRadians(yaw))) > 0.5;
        double hx = (sideways ? 124 : 66) / 2, hz = (sideways ? 66 : 124) / 2;
        for (Lot l : p.lots) {
            if (!l.enterable()) {
                continue;
            }
            double lx0 = l.x - 45, lx1 = l.x + 45, lz0 = l.front == 1 ? l.z + l.d / 2 : l.z - l.d / 2 - 90, lz1 = l.front == 1 ? l.z + l.d / 2 + 90 : l.z - l.d / 2;
            if (x - hx < lx1 && x + hx > lx0 && z - hz < lz1 && z + hz > lz0) {
                return true;
            }
        }
        return false;
    }

    private static boolean freeOfSolids(Plan p, double x, double z, double r) {
        for (Lot l : p.lots) {
            if (l.solid()) {
                double[] a = l.rect();
                if (x > a[0] - r && x < a[2] + r && z > a[1] - r && z < a[3] + r) {
                    return false;
                }
            }
        }
        return true;
    }

    private static void scatterTrees(Plan p, Random rn, int n) {
        for (int i = 0, tries = 0; i < n && tries < 80; tries++) {
            double x = p.ix0 + 20 + rn.nextDouble() * (p.ix1 - p.ix0 - 40), z = p.iz0 + 20 + rn.nextDouble() * (p.iz1 - p.iz0 - 40);
            if (freeOfSolids(p, x, z, 30)) {
                p.trees.add(new Spot(x, z, 0.7 + rn.nextDouble() * 0.5, 0, rn.nextInt(4)));
                i++;
            }
        }
    }

    private static void scatterBenches(Plan p, Random rn, int n) {
        for (int i = 0, tries = 0; i < n && tries < 80; tries++) {
            double x = p.ix0 + 30 + rn.nextDouble() * (p.ix1 - p.ix0 - 60), z = p.iz0 + 30 + rn.nextDouble() * (p.iz1 - p.iz0 - 60);
            if (freeOfSolids(p, x, z, 28)) {
                p.benches.add(new Spot(x, z, 1, rn.nextInt(4) * 90, 0));
                i++;
            }
        }
    }
}
