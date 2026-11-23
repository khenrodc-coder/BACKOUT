package com.blackout.game.environment;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import com.blackout.game.core.Dims;

/**
 * Pure floor-plan generator for one house (no JavaFX). It decides the rooms, interior walls, doorways, windows and furniture, and
 * guarantees that furniture never blocks the way from the front door to every doorway.
 * <p>
 * Local coordinates: origin at the house centre, +z is toward the front door. The 3D builder mirrors z for houses that face the
 * other way. Layout (back to front): a back zone with a bedroom and a bathroom/WC, an interior partition with one doorway per room,
 * and a big open front zone (living room + kitchen/dining) that contains the front door.
 */
public final class HouseLayout {

    public enum RoomType { LIVING, BEDROOM, BATH, WC }

    public enum Kind {
        BED, NIGHTSTAND, WARDROBE, DRESSER, SOFA, ARMCHAIR, COFFEE_TABLE, TV_STAND, BOOKSHELF, TABLE, CHAIR,
        COUNTER, SINK_COUNTER, STOVE_COUNTER, FRIDGE, TOILET, SINK, TUB, LAMP,
        RUG, BOX, CLOTHES, BOOKS, STAIN, SHOES
    }

    public enum Side { BACK, FRONT, LEFT, RIGHT }

    public static final class Room {

        public final RoomType type;
        public final double x0, z0, x1, z1;

        Room(RoomType type, double x0, double z0, double x1, double z1) {
            this.type = type;
            this.x0 = x0;
            this.z0 = z0;
            this.x1 = x1;
            this.z1 = z1;
        }

        public boolean contains(double x, double z) {
            return x >= x0 && x <= x1 && z >= z0 && z <= z1;
        }

        public double cx() {
            return (x0 + x1) / 2;
        }

        public double cz() {
            return (z0 + z1) / 2;
        }
    }

    /** Interior wall segment. {@code openings} are {centre, width} pairs along the wall. */
    public static final class Wall {

        public final boolean alongX;
        public final double fixed, from, to;
        public final List<double[]> openings = new ArrayList<>();

        Wall(boolean alongX, double fixed, double from, double to) {
            this.alongX = alongX;
            this.fixed = fixed;
            this.from = from;
            this.to = to;
        }
    }

    /** Opening in an exterior wall: the front door or a window. */
    public static final class Opening {

        public final Side side;
        public final double center, width;
        public final boolean door;

        Opening(Side side, double center, double width, boolean door) {
            this.side = side;
            this.center = center;
            this.width = width;
            this.door = door;
        }
    }

    /** Connection through the partition: {@code zone} is the back room it serves. */
    public static final class Doorway {

        public final double cx, z, width;
        public final int zone;

        Doorway(double cx, double z, double width, int zone) {
            this.cx = cx;
            this.z = z;
            this.width = width;
            this.zone = zone;
        }
    }

    public static final class Prop {

        public final Kind kind;
        public double x, z, yaw;
        public final double w, d, h;
        public final boolean collide;
        /** True for furniture that was knocked over (ruined houses). */
        public boolean fallen;

        Prop(Kind kind, double x, double z, double yaw) {
            this.kind = kind;
            this.x = x;
            this.z = z;
            this.yaw = yaw;
            double[] s = size(kind);
            this.w = s[0];
            this.d = s[1];
            this.h = s[2];
            this.collide = s[2] > 6;
        }

        /** Axis-aligned bounds {x0, z0, x1, z1} of the rotated footprint. */
        public double[] aabb() {
            double r = Math.toRadians(yaw), c = Math.abs(Math.cos(r)), s = Math.abs(Math.sin(r));
            double hx = (w * c + d * s) / 2, hz = (w * s + d * c) / 2;
            return new double[]{x - hx, z - hz, x + hx, z + hz};
        }
    }

    public static double[] size(Kind k) {
        switch (k) {
            case BED: return new double[]{92, 124, 32};
            case NIGHTSTAND: return new double[]{26, 26, 30};
            case WARDROBE: return new double[]{76, 30, 118};
            case DRESSER: return new double[]{62, 28, 56};
            case SOFA: return new double[]{128, 54, 40};
            case ARMCHAIR: return new double[]{50, 50, 40};
            case COFFEE_TABLE: return new double[]{66, 36, 24};
            case TV_STAND: return new double[]{84, 28, 34};
            case BOOKSHELF: return new double[]{64, 24, 112};
            case TABLE: return new double[]{80, 60, 48};
            case CHAIR: return new double[]{26, 26, 30};
            case COUNTER: return new double[]{56, 38, 56};
            case SINK_COUNTER: return new double[]{72, 38, 56};
            case STOVE_COUNTER: return new double[]{54, 38, 56};
            case FRIDGE: return new double[]{42, 40, 118};
            case TOILET: return new double[]{28, 38, 34};
            case SINK: return new double[]{36, 28, 54};
            case TUB: return new double[]{52, 108, 30};
            case LAMP: return new double[]{16, 16, 100};
            case RUG: return new double[]{110, 80, 0.8};
            case BOX: return new double[]{26, 22, 22};
            case CLOTHES: return new double[]{28, 20, 4};
            case BOOKS: return new double[]{18, 14, 5};
            case STAIN: return new double[]{34, 30, 0.4};
            default: return new double[]{12, 8, 5};     // SHOES
        }
    }

    // ------------------------------------------------------------------ result

    public final double w, d, T = Dims.HOUSE_WALL_T, P = Dims.PARTITION_T;
    public final double innerW, innerD, zp;
    public final Room front, bedroom, bath;
    public final List<Room> rooms = new ArrayList<>();
    public final List<Wall> walls = new ArrayList<>();
    public final List<Opening> openings = new ArrayList<>();
    public final List<Doorway> doorways = new ArrayList<>();
    public final List<Prop> props = new ArrayList<>();
    /** Free floor points (local coordinates) where zombies may start. */
    public final List<double[]> spawnPoints = new ArrayList<>();
    /** Free floor points next to furniture where loot may lie. */
    public final List<double[]> lootSpots = new ArrayList<>();
    public double doorX;
    public final boolean ruined;

    private final Random rn;
    private final List<double[]> solids = new ArrayList<>();      // wall rectangles {x0,z0,x1,z1}
    private final List<double[]> keepClear = new ArrayList<>();   // rectangles furniture may not enter
    private static final double CELL = 6;
    private boolean bedLeft;

    private HouseLayout(double w, double d, Random rn, boolean ruined) {
        this.w = w;
        this.d = d;
        this.rn = rn;
        this.ruined = ruined;
        innerW = w - 2 * T;
        innerD = d - 2 * T;
        double zb = -innerD / 2, zf = innerD / 2;
        double bd = Math.max(136, Math.min(160, innerD * 0.44));
        zp = zb + bd + P / 2;
        double wx0 = -innerW / 2, wx1 = innerW / 2;

        // back zone: one bedroom plus a bathroom (tub) or, when narrow, a WC
        double sideW = Math.max(92, Math.min(170, innerW - P - 178));
        bedLeft = rn.nextBoolean();
        double divX = bedLeft ? wx0 + (innerW - P - sideW) + P / 2 : wx1 - (innerW - P - sideW) - P / 2;
        double bedW = innerW - P - sideW;
        Room bed = bedLeft ? new Room(RoomType.BEDROOM, wx0, zb, wx0 + bedW, zb + bd) : new Room(RoomType.BEDROOM, wx1 - bedW, zb, wx1, zb + bd);
        Room sub = bedLeft ? new Room(sideW >= 128 ? RoomType.BATH : RoomType.WC, wx1 - sideW, zb, wx1, zb + bd)
                : new Room(sideW >= 128 ? RoomType.BATH : RoomType.WC, wx0, zb, wx0 + sideW, zb + bd);
        front = new Room(RoomType.LIVING, wx0, zb + bd + P, wx1, zf);
        bedroom = bed;
        bath = sub;
        rooms.add(front);
        rooms.add(bed);
        rooms.add(sub);

        // interior walls: the partition (full width) and the divider between the two back rooms
        Wall part = new Wall(true, zp, wx0, wx1);
        double bedDoor = bedLeft ? bed.x1 - 14 - Dims.DOORWAY_W / 2 : bed.x0 + 14 + Dims.DOORWAY_W / 2;
        double subDoor = bedLeft ? sub.x0 + 8 + 27 : sub.x1 - 8 - 27;
        part.openings.add(new double[]{bedDoor, Dims.DOORWAY_W});
        part.openings.add(new double[]{subDoor, 54});
        walls.add(part);
        walls.add(new Wall(false, divX, zb, zb + bd + P / 2));
        doorways.add(new Doorway(bedDoor, zp, Dims.DOORWAY_W, 1));
        doorways.add(new Doorway(subDoor, zp, 54, 2));

        // wall rectangles used for path finding (opening gaps left free)
        List<double[]> cuts = new ArrayList<>(part.openings);
        cuts.sort((a, b) -> Double.compare(a[0], b[0]));
        double cur = wx0;
        for (double[] c : cuts) {
            double a = c[0] - c[1] / 2, e = c[0] + c[1] / 2;
            solids.add(new double[]{cur, zp - P / 2, a, zp + P / 2});
            cur = e;
        }
        solids.add(new double[]{cur, zp - P / 2, wx1, zp + P / 2});
        solids.add(new double[]{divX - P / 2, zb, divX + P / 2, zp});

        // front door and the areas that must stay free of furniture
        double lim = Math.max(0, innerW * 0.5 - Dims.HOUSE_DOOR_W / 2 - 40);
        doorX = Math.round((rn.nextDouble() * 2 - 1) * Math.min(lim, innerW * 0.18));
        openings.add(new Opening(Side.FRONT, doorX, Dims.HOUSE_DOOR_W, true));
        keepClear.add(new double[]{doorX - 38, zf - 62, doorX + 38, zf});
        for (Doorway dw : doorways) {
            keepClear.add(new double[]{dw.cx - dw.width / 2 - 4, zp - 46, dw.cx + dw.width / 2 + 4, zp + 46});
        }

        furnish();
        addWindows();
        pickPoints();
    }

    public static HouseLayout generate(double w, double d, Random rn, boolean ruined) {
        return new HouseLayout(w, d, rn, ruined);
    }

    // ------------------------------------------------------------------ furniture

    private Prop make(Kind k, double x, double z, double yaw) {
        return new Prop(k, x, z, yaw);
    }

    /** Places a prop against a wall of {@code r}; its front faces into the room. {@code frac} is the position along the wall. */
    private Prop atWall(Kind k, Room r, Side side, double frac, double gap) {
        double[] s = size(k);
        double w0 = s[0], d0 = s[1];
        switch (side) {
            case BACK: {
                double lo = r.x0 + w0 / 2, hi = r.x1 - w0 / 2;
                return make(k, lo + (hi - lo) * frac, r.z0 + d0 / 2 + gap, 0);
            }
            case FRONT: {
                double lo = r.x0 + w0 / 2, hi = r.x1 - w0 / 2;
                return make(k, lo + (hi - lo) * frac, r.z1 - d0 / 2 - gap, 180);
            }
            case LEFT: {
                double lo = r.z0 + w0 / 2, hi = r.z1 - w0 / 2;
                return make(k, r.x0 + d0 / 2 + gap, lo + (hi - lo) * frac, 90);
            }
            default: {
                double lo = r.z0 + w0 / 2, hi = r.z1 - w0 / 2;
                return make(k, r.x1 - d0 / 2 - gap, lo + (hi - lo) * frac, 270);
            }
        }
    }

    private static boolean overlap(double[] a, double[] b, double gap) {
        return a[0] < b[2] + gap && a[2] > b[0] - gap && a[1] < b[3] + gap && a[3] > b[1] - gap;
    }

    private boolean fits(Prop p, Room r, double gap) {
        double[] a = p.aabb();
        if (a[0] < r.x0 - 0.5 || a[2] > r.x1 + 0.5 || a[1] < r.z0 - 0.5 || a[3] > r.z1 + 0.5) {
            return false;
        }
        if (p.collide) {
            for (double[] k : keepClear) {
                if (overlap(a, k, 0)) {
                    return false;
                }
            }
        }
        for (Prop o : props) {
            if (o.collide && p.collide && overlap(a, o.aabb(), gap)) {
                return false;
            }
        }
        return true;
    }

    /** Adds the prop if it fits and the player can still walk from the front door to every doorway. */
    private boolean add(Prop p, Room r, double gap) {
        if (p == null || !fits(p, r, gap)) {
            return false;
        }
        props.add(p);
        if (p.collide && !pathsOk()) {
            props.remove(p);
            return false;
        }
        return true;
    }

    /** Tries up to {@code tries} random wall positions on the allowed sides. */
    private Prop wallProp(Kind k, Room r, Side[] sides, int tries, double gap) {
        for (int i = 0; i < tries; i++) {
            Prop p = atWall(k, r, sides[rn.nextInt(sides.length)], rn.nextDouble(), rn.nextInt(3) * 0.8);
            if (fits(p, r, gap)) {
                props.add(p);
                boolean ok = !p.collide || pathsOk();
                if (ok) {
                    return p;
                }
                props.remove(p);
            }
        }
        return null;
    }

    private void furnish() {
        Side[] all = {Side.BACK, Side.FRONT, Side.LEFT, Side.RIGHT};
        // ---- bedroom: bed head against the back wall, on the side away from the doorway
        Side bedSide = bedLeft ? Side.LEFT : Side.RIGHT;
        Prop bed = atWall(Kind.BED, bedroom, Side.BACK, bedLeft ? 0 : 1, 1);
        bed.yaw = 0;
        if (!add(bed, bedroom, 2)) {
            bed = wallProp(Kind.BED, bedroom, new Side[]{Side.BACK}, 30, 2);
        }
        if (bed != null) {
            double[] a = bed.aabb();
            Prop n1 = make(Kind.NIGHTSTAND, bedLeft ? a[2] + 3 + 13 : a[0] - 3 - 13, bedroom.z0 + 13 + 1, 0);
            if (!add(n1, bedroom, 1)) {
                add(make(Kind.NIGHTSTAND, bedLeft ? a[0] - 3 - 13 : a[2] + 3 + 13, bedroom.z0 + 14, 0), bedroom, 1);
            }
        }
        wallProp(Kind.WARDROBE, bedroom, new Side[]{bedLeft ? Side.RIGHT : Side.LEFT, Side.BACK}, 40, 4);
        wallProp(Kind.DRESSER, bedroom, new Side[]{bedSide, Side.BACK, bedLeft ? Side.RIGHT : Side.LEFT}, 30, 4);
        if (bed != null && !ruined) {
            Prop rug = make(Kind.RUG, bed.x + (bedLeft ? 20 : -20), bed.z + 30, 0);
            if (fits(rug, bedroom, 0)) {
                props.add(rug);
            }
        }

        // ---- bathroom / WC
        Side outer = bedLeft ? Side.RIGHT : Side.LEFT;
        if (bath.type == RoomType.BATH) {
            // the tub lies along the outer side wall with its far end in the back corner
            add(make(Kind.TUB, outer == Side.LEFT ? bath.x0 + 26 + 1 : bath.x1 - 26 - 1, bath.z0 + 54 + 1, 0), bath, 2);
        }
        wallProp(Kind.TOILET, bath, new Side[]{Side.BACK}, 30, 6);
        wallProp(Kind.SINK, bath, new Side[]{outer, Side.BACK}, 30, 6);

        // ---- front zone: kitchen on the side with more room, living on the other
        boolean kitchenRight = doorX < 0;
        Room fz = front;
        Side kSide = kitchenRight ? Side.RIGHT : Side.LEFT;
        // kitchen run along the partition wall, starting in the corner
        double runX = kitchenRight ? fz.x1 : fz.x0;
        Kind[] run = rn.nextBoolean() ? new Kind[]{Kind.FRIDGE, Kind.SINK_COUNTER, Kind.STOVE_COUNTER} : new Kind[]{Kind.FRIDGE, Kind.STOVE_COUNTER, Kind.SINK_COUNTER, Kind.COUNTER};
        double cursor = 0;
        for (Kind k : run) {
            double[] s = size(k);
            double cx = kitchenRight ? runX - cursor - s[0] / 2 : runX + cursor + s[0] / 2;
            Prop p = make(k, cx, fz.z0 + s[1] / 2 + 1, 0);
            if (!add(p, fz, 0)) {
                break;
            }
            cursor += s[0];
        }
        // dining table with chairs, in the kitchen half
        Prop table = null;
        for (int i = 0; i < 40 && table == null; i++) {
            double tx = kitchenRight ? fz.x1 - 60 - rn.nextDouble() * 40 : fz.x0 + 60 + rn.nextDouble() * 40;
            double tz = fz.z0 + 78 + rn.nextDouble() * Math.max(1, fz.z1 - fz.z0 - 160);
            Prop t = make(Kind.TABLE, tx, tz, rn.nextBoolean() ? 0 : 90);
            if (add(t, fz, 6)) {
                table = t;
            }
        }
        if (table != null) {
            double[] ta = table.aabb();
            double[][] seats = {{(ta[0] + ta[2]) / 2, ta[1] - 15, 0}, {(ta[0] + ta[2]) / 2, ta[3] + 15, 180}, {ta[0] - 15, (ta[1] + ta[3]) / 2, 90}, {ta[2] + 15, (ta[1] + ta[3]) / 2, 270}};
            for (double[] s : seats) {
                if (rn.nextDouble() < (ruined ? 0.5 : 0.85)) {
                    // slightly pulled out and turned, never perfectly aligned
                    double pull = rn.nextDouble() * 9, turn = (rn.nextDouble() - 0.5) * 36;
                    double rad = Math.toRadians(s[2]);                 // the chair faces the table; pulling out goes the other way
                    add(make(Kind.CHAIR, s[0] - Math.sin(rad) * pull, s[1] - Math.cos(rad) * pull, s[2] + turn), fz, 0);
                }
            }
        }
        // living area: sofa against the partition wall, coffee table in front, TV facing it
        Side livingBack = Side.BACK;
        Prop sofa = null;
        for (int i = 0; i < 30 && sofa == null; i++) {
            double frac = kitchenRight ? rn.nextDouble() * 0.35 : 0.65 + rn.nextDouble() * 0.35;
            Prop s = atWall(Kind.SOFA, fz, livingBack, frac, 1);
            if (add(s, fz, 4)) {
                sofa = s;
            }
        }
        if (sofa == null) {
            sofa = wallProp(Kind.SOFA, fz, new Side[]{kitchenRight ? Side.LEFT : Side.RIGHT, Side.FRONT}, 40, 4);
        }
        if (sofa != null) {
            double fx = Math.sin(Math.toRadians(sofa.yaw)), fzz = Math.cos(Math.toRadians(sofa.yaw));
            Prop coffee = make(Kind.COFFEE_TABLE, sofa.x + fx * (27 + 22 + 18), sofa.z + fzz * (27 + 22 + 18), sofa.yaw);
            if (add(coffee, fz, 3)) {
                Prop rug = make(Kind.RUG, coffee.x, coffee.z, sofa.yaw);
                if (!ruined && fits(rug, fz, 0)) {
                    props.add(rug);
                }
            }
        }
        wallProp(Kind.TV_STAND, fz, new Side[]{Side.FRONT, kitchenRight ? Side.LEFT : Side.RIGHT}, 30, 5);
        wallProp(Kind.ARMCHAIR, fz, new Side[]{kitchenRight ? Side.LEFT : Side.RIGHT, Side.FRONT}, 25, 6);
        wallProp(Kind.BOOKSHELF, fz, new Side[]{kitchenRight ? Side.LEFT : Side.RIGHT, Side.FRONT}, 30, 4);
        wallProp(Kind.LAMP, fz, all, 20, 4);
        wallProp(Kind.BOX, fz, all, 12, 4);

        // ---- imperfections: nothing is perfectly tidy
        double mess = ruined ? 1.0 : 0.35;
        for (Prop p : new ArrayList<>(props)) {
            if (p.kind == Kind.CHAIR && ruined && rn.nextDouble() < 0.5) {
                p.fallen = true;                                    // knocked over
            }
        }
        for (Room r : rooms) {
            int n = (int) Math.round((1 + rn.nextInt(3)) * mess * 2);
            for (int i = 0; i < n; i++) {
                Kind k = ruined && rn.nextDouble() < 0.4 ? Kind.STAIN : new Kind[]{Kind.CLOTHES, Kind.BOOKS, Kind.SHOES, Kind.BOX}[rn.nextInt(4)];
                for (int a = 0; a < 12; a++) {
                    Prop p = make(k, r.x0 + 20 + rn.nextDouble() * (r.x1 - r.x0 - 40), r.z0 + 20 + rn.nextDouble() * (r.z1 - r.z0 - 40), rn.nextDouble() * 360);
                    if (fits(p, r, 4) && clearOfWays(p)) {
                        props.add(p);
                        if (p.collide && !pathsOk()) {
                            props.remove(p);
                            continue;
                        }
                        break;
                    }
                }
            }
        }
    }

    private boolean clearOfWays(Prop p) {
        double[] a = p.aabb();
        for (double[] k : keepClear) {
            if (overlap(a, k, 0)) {
                return false;
            }
        }
        return true;
    }

    // ------------------------------------------------------------------ windows

    private boolean tallNear(Side side, double lo, double hi) {
        for (Prop p : props) {
            if (!p.collide || p.h < 50) {
                continue;
            }
            double[] a = p.aabb();
            boolean touches;
            double pl, ph;
            switch (side) {
                case FRONT: touches = a[3] > innerD / 2 - 14; pl = a[0]; ph = a[2]; break;
                case BACK: touches = a[1] < -innerD / 2 + 14; pl = a[0]; ph = a[2]; break;
                case LEFT: touches = a[0] < -innerW / 2 + 14; pl = a[1]; ph = a[3]; break;
                default: touches = a[2] > innerW / 2 - 14; pl = a[1]; ph = a[3]; break;
            }
            if (touches && pl < hi + 4 && ph > lo - 4) {
                return true;
            }
        }
        return false;
    }

    private void window(Side side, double lo, double hi, double width) {
        if (hi - lo < width + 8) {
            return;
        }
        for (int i = 0; i < 8; i++) {
            double c = lo + width / 2 + 4 + rn.nextDouble() * (hi - lo - width - 8);
            if (!tallNear(side, c - width / 2, c + width / 2)) {
                openings.add(new Opening(side, c, width, false));
                return;
            }
        }
    }

    private void addWindows() {
        double zf = innerD / 2, zb = -innerD / 2, wx0 = -innerW / 2, wx1 = innerW / 2;
        // front wall: either side of the door
        window(Side.FRONT, wx0 + 6, doorX - Dims.HOUSE_DOOR_W / 2 - 8, Dims.WINDOW_W);
        window(Side.FRONT, doorX + Dims.HOUSE_DOOR_W / 2 + 8, wx1 - 6, Dims.WINDOW_W);
        // back wall: one per back room
        window(Side.BACK, bedroom.x0 + 6, bedroom.x1 - 6, Dims.WINDOW_W);
        window(Side.BACK, bath.x0 + 6, bath.x1 - 6, bath.type == RoomType.BATH ? 36 : 30);
        // side walls (along z): one for the front zone, one for the back zone
        for (Side s : new Side[]{Side.LEFT, Side.RIGHT}) {
            window(s, front.z0 + 6, front.z1 - 6, Dims.WINDOW_W);
            Room r = (s == Side.LEFT) == (bedLeft) ? bedroom : bath;
            window(s, zb + 6, r.z1 - 6, r.type == RoomType.BEDROOM ? Dims.WINDOW_W : 30);
        }
    }

    // ------------------------------------------------------------------ path finding

    private boolean[][] freeGrid() {
        int nx = (int) Math.ceil(innerW / CELL), nz = (int) Math.ceil(innerD / CELL);
        boolean[][] g = new boolean[nx][nz];
        double r = Dims.PATH_RADIUS;
        List<double[]> rects = new ArrayList<>(solids);
        for (Prop p : props) {
            if (p.collide) {
                rects.add(p.aabb());
            }
        }
        for (int i = 0; i < nx; i++) {
            double x = -innerW / 2 + (i + 0.5) * CELL;
            for (int j = 0; j < nz; j++) {
                double z = -innerD / 2 + (j + 0.5) * CELL;
                boolean ok = Math.abs(x) <= innerW / 2 - r && Math.abs(z) <= innerD / 2 - r;
                for (int k = 0; ok && k < rects.size(); k++) {
                    double[] q = rects.get(k);
                    double dx = Math.max(Math.max(q[0] - x, x - q[2]), 0), dz = Math.max(Math.max(q[1] - z, z - q[3]), 0);
                    if (dx * dx + dz * dz < r * r) {
                        ok = false;
                    }
                }
                g[i][j] = ok;
            }
        }
        return g;
    }

    private int cx(double x) {
        return (int) Math.floor((x + innerW / 2) / CELL);
    }

    private int cz(double z) {
        return (int) Math.floor((z + innerD / 2) / CELL);
    }

    private boolean[][] reach(boolean[][] g) {
        int nx = g.length, nz = g[0].length;
        boolean[][] seen = new boolean[nx][nz];
        int sx = Math.max(0, Math.min(nx - 1, cx(doorX))), sz = Math.max(0, Math.min(nz - 1, cz(innerD / 2 - Dims.PATH_RADIUS - 8)));
        if (!g[sx][sz]) {
            return seen;
        }
        java.util.ArrayDeque<int[]> q = new java.util.ArrayDeque<>();
        q.add(new int[]{sx, sz});
        seen[sx][sz] = true;
        int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        while (!q.isEmpty()) {
            int[] c = q.poll();
            for (int[] dd : dirs) {
                int x = c[0] + dd[0], z = c[1] + dd[1];
                if (x >= 0 && z >= 0 && x < nx && z < nz && g[x][z] && !seen[x][z]) {
                    seen[x][z] = true;
                    q.add(new int[]{x, z});
                }
            }
        }
        return seen;
    }

    /** True when the front door and both doorways (both sides) are mutually reachable. */
    private boolean pathsOk() {
        boolean[][] g = freeGrid(), seen = reach(g);
        for (Doorway dw : doorways) {
            for (double dz : new double[]{28, -28}) {
                int x = cx(dw.cx), z = cz(dw.z + dz);
                if (x < 0 || z < 0 || x >= seen.length || z >= seen[0].length || !seen[x][z]) {
                    return false;
                }
            }
        }
        return true;
    }

    /** Public check used by tests and the preview tool: every free spawn/loot point is reachable from the front door. */
    public boolean isReachable(double x, double z) {
        boolean[][] seen = reach(freeGrid());
        int i = cx(x), j = cz(z);
        return i >= 0 && j >= 0 && i < seen.length && j < seen[0].length && seen[i][j];
    }

    public boolean allDoorwaysReachable() {
        return pathsOk();
    }

    /** Distance from a point to the nearest wall or solid furniture (0 inside one). */
    private double clearance(double x, double z) {
        double m = Math.min(innerW / 2 - Math.abs(x), innerD / 2 - Math.abs(z));
        List<double[]> rects = new ArrayList<>(solids);
        for (Prop p : props) {
            if (p.collide) {
                rects.add(p.aabb());
            }
        }
        for (double[] q : rects) {
            double dx = Math.max(Math.max(q[0] - x, x - q[2]), 0), dz = Math.max(Math.max(q[1] - z, z - q[3]), 0);
            m = Math.min(m, Math.hypot(dx, dz));
        }
        return m;
    }

    private void pickPoints() {
        boolean[][] g = freeGrid(), seen = reach(g);
        List<double[]> pool = new ArrayList<>();
        for (int i = 0; i < seen.length; i++) {
            for (int j = 0; j < seen[0].length; j++) {
                if (!seen[i][j]) {
                    continue;
                }
                double x = -innerW / 2 + (i + 0.5) * CELL, z = -innerD / 2 + (j + 0.5) * CELL;
                boolean nearPartition = Math.abs(z - zp) < P / 2 + 30;      // never start in a doorway
                if (!nearPartition && Math.hypot(x - doorX, z - innerD / 2) > 80 && clearance(x, z) >= 34) {
                    pool.add(new double[]{x, z});
                }
            }
        }
        for (int n = 0; n < 5 && !pool.isEmpty(); n++) {
            double[] p = pool.remove(rn.nextInt(pool.size()));
            spawnPoints.add(p);
        }
        for (Prop pr : props) {
            if (lootSpots.size() >= 4) {
                break;
            }
            if (pr.collide && pr.h > 20 && pr.h < 60) {
                double[] a = pr.aabb();
                for (double[] c : new double[][]{{(a[0] + a[2]) / 2, a[3] + 30}, {(a[0] + a[2]) / 2, a[1] - 30}, {a[0] - 30, (a[1] + a[3]) / 2}, {a[2] + 30, (a[1] + a[3]) / 2}}) {
                    if (Math.abs(c[1] - zp) > P / 2 + 30 && clearance(c[0], c[1]) >= 24 && isReachable(c[0], c[1])) {
                        lootSpots.add(c);
                        break;
                    }
                }
            }
        }
    }

    // ------------------------------------------------------------------ routing for zombies

    /** Zone index: 0 = front zone, 1 = bedroom, 2 = bathroom / WC. */
    private int zoneOf(double x, double z, int hint) {
        if (z > zp + P / 2 + 2) {
            return 0;
        }
        if (z < zp - P / 2 - 2) {
            return roomIndex(x);
        }
        for (Doorway dw : doorways) {               // inside the partition band: the doorway being crossed
            if (Math.abs(x - dw.cx) <= dw.width / 2 + 4) {
                return hint == dw.zone ? dw.zone : 0;
            }
        }
        return 0;
    }

    private int roomIndex(double x) {
        return x >= bedroom.x0 - 1 && x <= bedroom.x1 + 1 ? 1 : 2;
    }

    /**
     * Next point a walker should head for when going from one place inside the house to another, so it uses the doorways instead
     * of pressing against the partition wall. Returns the final target when both points share a room.
     */
    public double[] route(double fx, double fz, double tx, double tz) {
        int to = zoneOf(tx, tz, -1);
        int from = zoneOf(fx, fz, to);
        if (from == to) {
            return new double[]{tx, tz};
        }
        // line up with the doorway's centre line first, then go straight through; deciding by alignment (not by distance to the
        // approach point) means the walker commits and never turns back halfway
        double inset = P / 2 + 22;
        if (from == 0) {
            Doorway dw = doorways.get(to - 1);
            return Math.abs(fx - dw.cx) > 12 ? new double[]{dw.cx, zp + inset} : new double[]{dw.cx, zp - inset};
        }
        Doorway dw = doorways.get(from - 1);
        return Math.abs(fx - dw.cx) > 12 ? new double[]{dw.cx, zp - inset} : new double[]{dw.cx, zp + inset};
    }

    public Room roomAt(double x, double z) {
        for (Room r : rooms) {
            if (r.contains(x, z)) {
                return r;
            }
        }
        return null;
    }
}
