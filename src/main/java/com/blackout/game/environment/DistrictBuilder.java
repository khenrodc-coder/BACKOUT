package com.blackout.game.environment;

import static com.blackout.game.core.Constants.GROUND_Y;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import com.blackout.game.core.Gfx;
import com.blackout.game.core.Mats;
import com.blackout.game.world.DistrictPlan;
import com.blackout.game.world.DistrictPlan.Kind;
import com.blackout.game.world.DistrictPlan.Lot;
import com.blackout.game.world.DistrictPlan.Plan;
import com.blackout.game.world.ShopLayout;
import com.blackout.game.world.WorldState;

import javafx.scene.Group;
import javafx.scene.paint.Color;
import javafx.scene.paint.PhongMaterial;
import javafx.scene.transform.Rotate;

/**
 * Turns one {@link DistrictPlan.Plan} (a city block) into scene nodes and collision. Everything a chunk needs to give back when it
 * is unloaded (nodes, obstacles, buildings, trees) is collected in {@link Content}. Must run on the JavaFX thread.
 */
public final class DistrictBuilder {

    public static final double SIDEWALK_Y = 109.0, LOT_Y = 108.4;

    /** What one loaded chunk owns. */
    public static final class Content {

        public final int cx, cz;
        public final Group root = new Group();
        public final List<Obstacle> obstacles = new ArrayList<>();
        public final List<Building> buildings = new ArrayList<>();
        /** World-state ids of the doors, parallel to {@link #buildings}. */
        public final List<String> doorIds = new ArrayList<>();
        public final List<TreeFactory.Tree> trees = new ArrayList<>();
        /** Supplies: {id, kind, x, z}. */
        public final List<Object[]> loot = new ArrayList<>();
        private int nextLoot;

        Content(int cx, int cz) {
            this.cx = cx;
            this.cz = cz;
        }
    }

    private static final Color[] CAR_COLORS = {Color.rgb(140, 40, 36), Color.rgb(40, 70, 120), Color.rgb(170, 170, 160), Color.rgb(36, 40, 44), Color.rgb(90, 110, 60), Color.rgb(190, 150, 40)};
    private static final Color[] WALLS = {Color.rgb(172, 160, 138), Color.rgb(120, 100, 90), Color.rgb(140, 150, 160), Color.rgb(96, 110, 96)};
    private static final PhongMaterial PAINT = Gfx.mat(Color.rgb(224, 224, 214), Color.WHITE, 30), POLE = Mats.STEEL_GREY, BULB = Gfx.glow(Color.rgb(255, 224, 160)),
            LIT_WINDOW = Gfx.glow(Color.rgb(255, 214, 130)), SIGN_GREEN = Gfx.glow(Color.rgb(70, 220, 110)), SIGN_ORANGE = Gfx.glow(Color.rgb(255, 150, 40)),
            BENCH = Mats.WOOD, ROOF = Mats.TAR_ROOF;
    private static final PhongMaterial[] SIGNS = {Gfx.glow(Color.rgb(90, 200, 255)), Gfx.glow(Color.rgb(255, 110, 190)), Gfx.glow(Color.rgb(255, 224, 90))};
    private static final double FLOOR_H = 48, BAY = 60;

    private DistrictBuilder() {
    }

    public static Content build(Plan p, HouseBuilder.Collision col) {
        Content c = new Content(p.cx, p.cz);
        Random rn = new Random(DistrictPlan.chunkSeed(p.seed, p.cx, p.cz) ^ 0x7F4A7C15L);
        HouseBuilder.Collision wrap = (x, z, w, d, h, zp) -> {
            Obstacle o = col.add(x, z, w, d, h, zp);
            c.obstacles.add(o);
            return o;
        };
        double mx = (p.ix0 + p.ix1) / 2, mz = (p.iz0 + p.iz1) / 2;
        c.root.getChildren().add(Gfx.plane(mx, mz, p.ix1 - p.ix0 + 28, p.iz1 - p.iz0 + 28, 56, SIDEWALK_Y, Mats.CONCRETE));
        int towerIndex = 0;
        for (Lot l : p.lots) {
            switch (l.kind) {
                case PLAZA:
                    c.root.getChildren().add(Gfx.plane(l.x, l.z, l.w, l.d, 64, LOT_Y, Mats.CONCRETE));
                    break;
                case PARK:
                    c.root.getChildren().add(Gfx.plane(l.x, l.z, l.w, l.d, 60, LOT_Y, Mats.GRASS_B));
                    break;
                case PARKING:
                    c.root.getChildren().add(Gfx.plane(l.x, l.z, l.w, l.d, 64, LOT_Y, Mats.ASPHALT));
                    for (double x = l.x - l.w / 2 + 4; x <= l.x + l.w / 2 - 4; x += 136) {
                        c.root.getChildren().add(Gfx.box(1.6, 0.5, Math.min(l.d - 8, 80), PAINT, x, LOT_Y - 0.3, l.z));
                    }
                    break;
                case OFFICE:
                case APARTMENT:
                    tower(c, l, towerIndex++, wrap, rn);
                    break;
                case CANOPY:
                    canopy(c, l, wrap, rn);
                    break;
                default:
                    shop(c, l, wrap, rn);
                    break;
            }
        }
        for (DistrictPlan.Spot t : p.trees) {
            int[] species = {TreeFactory.OAK, TreeFactory.BIRCH, TreeFactory.PINE};
            TreeFactory.Tree tree = TreeFactory.create(species[t.variant % 3], t.scale, t.x, t.z, GROUND_Y, rn);
            c.trees.add(tree);
            c.root.getChildren().add(tree.node);
            wrap.add(t.x, t.z, 10 * t.scale, 10 * t.scale, 60, false);
        }
        for (DistrictPlan.Spot s : p.benches) {
            bench(c, s, wrap);
        }
        for (DistrictPlan.Car car : p.cars) {
            car(c, car, wrap, rn);
        }
        for (double[] li : p.lights) {
            streetLight(c, li[0], li[1], wrap);
        }
        return c;
    }

    // ------------------------------------------------------------------ pieces

    private static void shop(Content c, Lot l, HouseBuilder.Collision col, Random rn) {
        double h = l.kind == Kind.SUPERMARKET ? 168 : l.kind == Kind.GAS_SHOP ? 126 : 150;
        Building b = new Building(l.x, l.z, l.w, l.d, h, l.front);
        ShopLayout layout = ShopLayout.generate(l.w, l.d, rn, l.kind == Kind.SUPERMARKET);
        Color wc = WALLS[l.style % WALLS.length];
        PhongMaterial wall = Gfx.mat(wc, Color.rgb(16, 16, 16), 6);
        PhongMaterial sign = l.kind == Kind.SUPERMARKET ? SIGN_GREEN : l.kind == Kind.GAS_SHOP ? SIGN_ORANGE : SIGNS[rn.nextInt(SIGNS.length)];
        HouseBuilder.Result r = ShopBuilder.build(b, layout, wall, sign, col, rn);
        b.spawn.addAll(r.spawns);
        c.doorIds.add(WorldState.id('d', c.cx, c.cz, c.buildings.size()));
        c.buildings.add(b);
        c.root.getChildren().add(b.group);
        for (double[] pt : r.loot) {                                   // supplies get stable ids so "taken" survives unloading
            if (c.nextLoot < 12) {
                c.loot.add(new Object[]{WorldState.id('p', c.cx, c.cz, c.nextLoot++), rn.nextInt(3), pt[0], pt[1]});
            }
        }
    }

    private static void tower(Content c, Lot l, int index, HouseBuilder.Collision col, Random rn) {
        double hf = l.floors * FLOOR_H, h = hf + 6;
        int style = l.kind == Kind.OFFICE ? (l.style % 2 == 0 ? 0 : 2) : 1;
        PhongMaterial body = Gfx.mat(Color.rgb(70, 72, 76), Color.rgb(14, 14, 14), 6), face = Mats.facade(style, (c.cx + c.cz + index) & 3);
        c.root.getChildren().add(Gfx.box(l.w, h, l.d, body, l.x, GROUND_Y - h / 2, l.z));
        int nx = Math.max(1, (int) Math.round(l.w / BAY)), nz = Math.max(1, (int) Math.round(l.d / BAY));
        double e = 0.4;
        c.root.getChildren().add(Gfx.tiledQuad(l.x - l.w / 2, GROUND_Y, l.z + l.d / 2 + e, l.w, 0, 0, 0, -hf, 0, nx, l.floors, face));
        c.root.getChildren().add(Gfx.tiledQuad(l.x + l.w / 2, GROUND_Y, l.z - l.d / 2 - e, -l.w, 0, 0, 0, -hf, 0, nx, l.floors, face));
        c.root.getChildren().add(Gfx.tiledQuad(l.x + l.w / 2 + e, GROUND_Y, l.z - l.d / 2, 0, 0, l.d, 0, -hf, 0, nz, l.floors, face));
        c.root.getChildren().add(Gfx.tiledQuad(l.x - l.w / 2 - e, GROUND_Y, l.z + l.d / 2, 0, 0, -l.d, 0, -hf, 0, nz, l.floors, face));
        // a handful of lit windows
        for (int i = 0; i < Math.min(14, l.floors * 2); i++) {
            int floor = 1 + rn.nextInt(l.floors), bay = rn.nextInt(nx);
            double y = GROUND_Y - floor * FLOOR_H + FLOOR_H * 0.46, x = l.x - l.w / 2 + (bay + 0.5) * (l.w / nx);
            boolean north = rn.nextBoolean();
            c.root.getChildren().add(Gfx.box(l.w / nx * 0.6, FLOOR_H * 0.34, 0.6, LIT_WINDOW, x, y, north ? l.z + l.d / 2 + e + 0.3 : l.z - l.d / 2 - e - 0.3));
        }
        c.root.getChildren().add(Gfx.box(l.w + 6, 5, l.d + 6, ROOF, l.x, GROUND_Y - h - 2.5, l.z));
        int units = 1 + rn.nextInt(3);
        for (int i = 0; i < units; i++) {
            c.root.getChildren().add(Gfx.box(28, 14, 28, Mats.STEEL_GREY, l.x + (rn.nextDouble() - 0.5) * (l.w - 60), GROUND_Y - h - 12, l.z + (rn.nextDouble() - 0.5) * (l.d - 60)));
        }
        if (l.kind == Kind.OFFICE) {
            c.root.getChildren().add(Gfx.cylY(2, 70, Mats.STEEL_GREY, l.x, GROUND_Y - h - 40, l.z));
            c.root.getChildren().add(Gfx.sph(3, Gfx.glow(Color.rgb(255, 60, 40)), l.x, GROUND_Y - h - 76, l.z, 1, 1, 1));
        }
        col.add(l.x, l.z, l.w, l.d, h, false);
    }

    private static void canopy(Content c, Lot l, HouseBuilder.Collision col, Random rn) {
        double top = 118;
        c.root.getChildren().add(Gfx.box(l.w, 6, l.d, Mats.STEEL_GREY, l.x, GROUND_Y - top, l.z));
        c.root.getChildren().add(Gfx.box(l.w + 2, 14, l.d + 2, SIGN_ORANGE, l.x, GROUND_Y - top + 10, l.z));
        for (int sx = -1; sx <= 1; sx += 2) {
            for (int sz = -1; sz <= 1; sz += 2) {
                double x = l.x + sx * (l.w / 2 - 14), z = l.z + sz * (l.d / 2 - 12);
                c.root.getChildren().add(Gfx.cylY(5, top, Mats.CONCRETE, x, GROUND_Y - top / 2, z));
                col.add(x, z, 10, 10, top, false);
            }
        }
        for (int i = 0; i < 4; i++) {                                    // pumps on two islands
            double x = l.x + (i % 2 == 0 ? -1 : 1) * 38, z = l.z + (i < 2 ? -1 : 1) * 12 + (i % 2 == 0 ? -6 : 6);
            Group pump = new Group();
            pump.getChildren().add(Gfx.box(16, 40, 12, Gfx.mat(Color.rgb(200, 50 + rn.nextInt(30), 40), Color.WHITE, 40), 0, -20, 0));
            pump.getChildren().add(Gfx.box(10, 8, 1, Gfx.glow(Color.rgb(120, 255, 160)), 0, -30, 6.4));
            pump.setTranslateX(x);
            pump.setTranslateY(GROUND_Y);
            pump.setTranslateZ(z);
            c.root.getChildren().add(pump);
            col.add(x, z, 16, 12, 40, false);
        }
        for (double x = l.x - l.w / 2 + 30; x < l.x + l.w / 2 - 20; x += 50) {
            c.root.getChildren().add(Gfx.box(30, 2, 6, Gfx.glow(Color.rgb(235, 240, 255)), x, GROUND_Y - top + 4, l.z));
        }
    }

    private static void car(Content c, DistrictPlan.Car car, HouseBuilder.Collision col, Random rn) {
        Group g = VehicleFactory.create(car.kind, CAR_COLORS[car.color % CAR_COLORS.length], car.wreck, car.wreck && rn.nextDouble() < 0.3, rn);
        g.setTranslateX(car.x);
        g.setTranslateY(GROUND_Y);
        g.setTranslateZ(car.z);
        g.getTransforms().add(new Rotate(car.yaw, Rotate.Y_AXIS));
        c.root.getChildren().add(g);
        double len = car.kind == VehicleFactory.PICKUP ? 132 : car.kind == VehicleFactory.VAN ? 136 : car.kind == VehicleFactory.SUV ? 124 : 118;
        boolean sideways = Math.abs(Math.sin(Math.toRadians(car.yaw))) > 0.5;
        col.add(car.x, car.z, sideways ? len : 66, sideways ? 66 : len, 38, false);
    }

    private static void streetLight(Content c, double x, double z, HouseBuilder.Collision col) {
        c.root.getChildren().add(Gfx.cylY(2.5, 150, POLE, x, GROUND_Y - 75, z));
        c.root.getChildren().add(Gfx.box(22, 3, 4, POLE, x + 9, GROUND_Y - 150, z));
        c.root.getChildren().add(Gfx.sph(4, BULB, x + 18, GROUND_Y - 147, z, 1.4, 0.7, 1));
        col.add(x, z, 7, 7, 150, false);
    }

    private static void bench(Content c, DistrictPlan.Spot s, HouseBuilder.Collision col) {
        Group g = new Group();
        g.getChildren().add(Gfx.box(40, 3, 14, BENCH, 0, -17, 0));
        g.getChildren().add(Gfx.box(40, 12, 2.5, BENCH, 0, -25, -6));
        g.getChildren().add(Gfx.box(3, 15, 12, Mats.BLACK_METAL, -17, -7.5, 0));
        g.getChildren().add(Gfx.box(3, 15, 12, Mats.BLACK_METAL, 17, -7.5, 0));
        g.setTranslateX(s.x);
        g.setTranslateY(GROUND_Y);
        g.setTranslateZ(s.z);
        g.getTransforms().add(new Rotate(s.yaw, Rotate.Y_AXIS));
        c.root.getChildren().add(g);
        boolean sideways = Math.abs(Math.sin(Math.toRadians(s.yaw))) > 0.5;
        col.add(s.x, s.z, sideways ? 14 : 40, sideways ? 40 : 14, 22, true);
    }
}
