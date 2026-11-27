package com.blackout.game.world;

import com.blackout.game.core.Constants;
import com.blackout.game.core.Dims;
import com.blackout.game.core.Gfx;
import com.blackout.game.core.Mats;
import com.blackout.game.environment.Building;
import com.blackout.game.environment.Car;
import com.blackout.game.environment.DistrictBuilder;
import com.blackout.game.environment.HouseBuilder;
import com.blackout.game.environment.LargeBuilding;
import com.blackout.game.environment.Obstacle;
import com.blackout.game.environment.TreeFactory;
import com.blackout.game.environment.VehicleFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import javafx.scene.Group;
import javafx.scene.paint.Color;
import javafx.scene.paint.PhongMaterial;
import javafx.scene.shape.Box;
import javafx.scene.shape.Cylinder;
import javafx.scene.shape.Sphere;
import javafx.scene.transform.Rotate;

/**
 * Phase 5: City Prototype - Complete district blocks with roads, sidewalks,
 * buildings, props, cars, and chunk loading integration.
 *
 * Builds on DistrictPlan and LargeBuilding to create cohesive urban districts
 * with proper infrastructure.
 */
public final class CityPrototype {

    private CityPrototype() {}

    // ============================================================
    // District Block Definition
    // ============================================================

    public static final class DistrictBlock {
        public final int cx, cz;                    // Chunk coordinates
        public final DistrictPlan.Plan plan;        // Base district plan
        public final List<LargeBuilding.BuildingData> largeBuildings = new ArrayList<>();
        public final List<Group> roadNodes = new ArrayList<>();
        public final List<Group> sidewalkNodes = new ArrayList<>();
        public final List<Group> propNodes = new ArrayList<>();
        public final List<Car> parkedCars = new ArrayList<>();
        public final List<TreeFactory.Tree> trees = new ArrayList<>();
        public final List<Obstacle> obstacles = new ArrayList<>();
        public final Group root = new Group();

        public DistrictBlock(int cx, int cz, DistrictPlan.Plan plan) {
            this.cx = cx;
            this.cz = cz;
            this.plan = plan;
        }
    }

    // ============================================================
    // Street/Infrastructure Constants
    // ============================================================

    public static final double ROAD_WIDTH = 64;           // Two-lane road
    public static final double AVENUE_WIDTH = 160;        // Major avenue
    public static final double SIDEWALK_WIDTH = 12;
    public static final double CURB_HEIGHT = 0.4;
    public static final double LANE_WIDTH = 16;
    public static final double CENTER_LINE_SPACING = 24;
    public static final double STREET_LIGHT_SPACING = 48;
    public static final double TREE_SPACING = 32;
    public static final double PARKING_LANE_WIDTH = 24;

    // ============================================================
    // Materials
    // ============================================================

    private static final PhongMaterial ASPHALT = Gfx.mat(Color.rgb(50, 50, 55), Color.rgb(10, 10, 12), 4);
    private static final PhongMaterial CONCRETE = Gfx.mat(Color.rgb(180, 175, 170), Color.rgb(30, 30, 30), 8);
    private static final PhongMaterial CONCRETE_DARK = Gfx.mat(Color.rgb(140, 135, 130), Color.rgb(20, 20, 20), 6);
    private static final PhongMaterial PAINT_YELLOW = Gfx.mat(Color.rgb(230, 200, 40), Color.WHITE, 60);
    private static final PhongMaterial PAINT_WHITE = Gfx.mat(Color.rgb(240, 240, 240), Color.WHITE, 60);
    private static final PhongMaterial PAINT_RED = Gfx.mat(Color.rgb(200, 40, 40), Color.WHITE, 60);
    private static final PhongMaterial STEEL_POLE = Mats.STEEL_GREY;
    private static final PhongMaterial LIGHT_BULB = Gfx.glow(Color.rgb(255, 230, 180));
    private static final PhongMaterial GRASS_VERGE = Mats.GRASS_A;
    private static final PhongMaterial CURB_MAT = Gfx.mat(Color.rgb(120, 115, 110), Color.BLACK, 4);
    private static final Color[] CAR_COLORS = {Color.rgb(140, 40, 36), Color.rgb(40, 70, 120), Color.rgb(170, 170, 160), Color.rgb(36, 40, 44), Color.rgb(90, 110, 60), Color.rgb(190, 150, 40)};

    // ============================================================
    // Public API
    // ============================================================

    /**
     * Generate a complete district block with all infrastructure.
     */
    public static DistrictBlock generateBlock(long worldSeed, int cx, int cz) {
        DistrictPlan.Plan plan = DistrictPlan.generate(worldSeed, cx, cz);
        DistrictBlock block = new DistrictBlock(cx, cz, plan);

        Random rn = new Random(DistrictPlan.chunkSeed(worldSeed, cx, cz) ^ 0xC17FB10C);

        // Build infrastructure layers
        buildRoads(block, rn);
        buildSidewalks(block, rn);
        buildStreetLights(block, rn);
        buildStreetTrees(block, rn);
        buildParkingLanes(block, rn);
        buildCrosswalks(block, rn);

        // Build buildings from district plan
        buildDistrictBuildings(block, worldSeed, rn);

        // Add props and details
        buildStreetProps(block, rn);
        buildParkedCars(block, rn);

        // Add district plan trees/benches
        addDistrictPlanFeatures(block, worldSeed, rn);

        return block;
    }

    /**
     * Generate a district block and return its content for ChunkManager.
     */
    public static DistrictBuilder.Content buildChunkContent(long worldSeed, int cx, int cz) {
        DistrictBlock block = generateBlock(worldSeed, cx, cz);
        return convertToChunkContent(block);
    }

    // ============================================================
    // Road Network
    // ============================================================

    private static void buildRoads(DistrictBlock block, Random rn) {
        double x0 = cxToX(block.cx);
        double z0 = czToZ(block.cz);
        double x1 = x0 + DistrictPlan.CHUNK;
        double z1 = z0 + DistrictPlan.CHUNK;

        // Check for central avenue (x=0)
        boolean hasAvenue = block.cx == 0 || block.cx == -1;

        // North-South roads
        if (hasAvenue) {
            // Central avenue
            double cx = 0;
            addRoadNS(block.root, cx, z0, z1, AVENUE_WIDTH, true, rn);
        } else {
            // Regular road at chunk boundary
            double cx = x0;
            addRoadNS(block.root, cx, z0, z1, ROAD_WIDTH, false, rn);
        }

        // East-West roads
        for (int k = DistrictPlan.MIN_CZ; k <= DistrictPlan.MAX_CZ + 1; k++) {
            double cz = k * DistrictPlan.CHUNK;
            if (cz >= z0 && cz <= z1) {
                addRoadEW(block.root, x0, x1, cz, ROAD_WIDTH, false, rn);
            }
        }

        // Internal access roads for larger blocks
        if (!hasAvenue && block.plan.pattern.equals("office")) {
            // Cross street through office plaza
            double mz = (block.plan.iz0 + block.plan.iz1) / 2;
            addRoadEW(block.root, x0, x1, mz, ROAD_WIDTH * 0.75, false, rn);
        }
    }

    private static void addRoadNS(Group root, double x, double z0, double z1, double width, boolean isAvenue, Random rn) {
        double cx = x;
        double cz = (z0 + z1) / 2;
        double len = z1 - z0;

        // Base asphalt
        Box road = new Box(width, 3, len);
        road.setMaterial(ASPHALT);
        road.setTranslateX(cx);
        road.setTranslateY(Constants.GROUND_Y + 1.5);
        road.setTranslateZ(cz);
        root.getChildren().add(road);

        // Center lines
        int lanes = isAvenue ? 4 : 2;
        double laneW = width / lanes;
        for (int i = 1; i < lanes; i++) {
            double offset = -width / 2 + i * laneW;
            if (isAvenue && i == lanes / 2) {
                // Double yellow for avenue center
                addCenterLine(root, cx + offset, cz, len, PAINT_YELLOW, true);
            } else {
                // Dashed white for lane separation
                addCenterLine(root, cx + offset, cz, len, PAINT_WHITE, false);
            }
        }

        // Edge lines
        addEdgeLine(root, cx - width / 2, cz, len, PAINT_WHITE);
        addEdgeLine(root, cx + width / 2, cz, len, PAINT_WHITE);

        // Crosswalk at intersections (placeholder - would need intersection logic)
    }

    private static void addRoadEW(Group root, double x0, double x1, double z, double width, boolean isAvenue, Random rn) {
        double cx = (x0 + x1) / 2;
        double cz = z;
        double len = x1 - x0;

        Box road = new Box(len, 3, width);
        road.setMaterial(ASPHALT);
        road.setTranslateX(cx);
        road.setTranslateY(Constants.GROUND_Y + 1.5);
        road.setTranslateZ(cz);
        root.getChildren().add(road);

        int lanes = 2;
        double laneW = width / lanes;
        for (int i = 1; i < lanes; i++) {
            double offset = -width / 2 + i * laneW;
            addCenterLine(root, cx, cz + offset, len, PAINT_WHITE, false);
        }

        addEdgeLine(root, cx, cz - width / 2, len, PAINT_WHITE);
        addEdgeLine(root, cx, cz + width / 2, len, PAINT_WHITE);
    }

    private static void addCenterLine(Group root, double cx, double cz, double len, PhongMaterial mat, boolean doubleYellow) {
        double dash = 12, gap = 8;
        int count = (int) (len / (dash + gap)) + 1;
        for (int i = 0; i < count; i++) {
            double z = cz - len / 2 + i * (dash + gap);
            Box line = new Box(doubleYellow ? 8 : 4, 2, dash);
            line.setMaterial(mat);
            line.setTranslateX(cx);
            line.setTranslateY(Constants.GROUND_Y + 3.1);
            line.setTranslateZ(z + dash / 2);
            root.getChildren().add(line);
            if (doubleYellow) {
                Box line2 = new Box(8, 2, dash);
                line2.setMaterial(PAINT_YELLOW);
                line2.setTranslateX(cx + 12);
                line2.setTranslateY(Constants.GROUND_Y + 3.1);
                line2.setTranslateZ(z + dash / 2);
                root.getChildren().add(line2);
                Box line3 = new Box(8, 2, dash);
                line3.setMaterial(PAINT_YELLOW);
                line3.setTranslateX(cx - 12);
                line3.setTranslateY(Constants.GROUND_Y + 3.1);
                line3.setTranslateZ(z + dash / 2);
                root.getChildren().add(line3);
            }
        }
    }

    private static void addEdgeLine(Group root, double cx, double cz, double len, PhongMaterial mat) {
        Box line = new Box(4, 2, len);
        line.setMaterial(mat);
        line.setTranslateX(cx);
        line.setTranslateY(Constants.GROUND_Y + 3.1);
        line.setTranslateZ(cz);
        root.getChildren().add(line);
    }

    // ============================================================
    // Sidewalks
    // ============================================================

    private static void buildSidewalks(DistrictBlock block, Random rn) {
        double x0 = cxToX(block.cx);
        double z0 = czToZ(block.cz);
        double x1 = x0 + DistrictPlan.CHUNK;
        double z1 = z0 + DistrictPlan.CHUNK;

        // Sidewalks along all roads
        double roadOffset = ROAD_WIDTH / 2 + SIDEWALK_WIDTH / 2;

        // North-South sidewalks
        double cx = x0;
        addSidewalk(block.root, cx, cx, 0, z0, z1, roadOffset, true, rn);
        if (block.cx != 0) { // Not central avenue
            addSidewalk(block.root, cx, cx, 0, z0, z1, -roadOffset, true, rn);
        }

        // East-West sidewalks
        for (int k = DistrictPlan.MIN_CZ; k <= DistrictPlan.MAX_CZ + 1; k++) {
            double cz = k * DistrictPlan.CHUNK;
            if (cz >= z0 && cz <= z1) {
                addSidewalk(block.root, x0, x1, cz, z0, z1, roadOffset, false, rn);
                addSidewalk(block.root, x0, x1, cz, z0, z1, -roadOffset, false, rn);
            }
        }

        // Curbs
        addCurbs(block.root, block, rn);
    }

    private static void addSidewalk(Group root, double x0, double x1, double z, double z0, double z1, double offset, boolean isNS, Random rn) {
        double cx = isNS ? x0 + offset : (x0 + x1) / 2;
        double cz = isNS ? (z0 + z1) / 2 : z + offset;
        double w = isNS ? SIDEWALK_WIDTH : x1 - x0;
        double d = isNS ? z1 - z0 : SIDEWALK_WIDTH;

        Box walk = new Box(w, 2, d);
        walk.setMaterial(CONCRETE);
        walk.setTranslateX(cx);
        walk.setTranslateY(Constants.GROUND_Y + 1);
        walk.setTranslateZ(cz);
        root.getChildren().add(walk);

        // Expansion joints
        double jointSpacing = 2.4;
        int count = (int) (isNS ? d / jointSpacing : w / jointSpacing) + 1;
        for (int i = 0; i < count; i++) {
            double pos = (isNS ? d : w) / (double) count * i;
            Box joint = new Box(isNS ? w : 2, 2, isNS ? 2 : d);
            joint.setMaterial(CONCRETE_DARK);
            joint.setTranslateX(isNS ? cx : cx - w / 2 + pos);
            joint.setTranslateY(Constants.GROUND_Y + 2);
            joint.setTranslateZ(isNS ? cz - d / 2 + pos : cz);
            root.getChildren().add(joint);
        }
    }

    private static void addCurbs(Group root, DistrictBlock block, Random rn) {
        // Curb along road edges
        double x0 = cxToX(block.cx);
        double z0 = czToZ(block.cz);
        double roadEdge = ROAD_WIDTH / 2;

        // Simple curb boxes at corners and intervals
        // Would be more sophisticated in production
    }

    // ============================================================
    // Street Lights
    // ============================================================

    private static void buildStreetLights(DistrictBlock block, Random rn) {
        double x0 = cxToX(block.cx);
        double z0 = czToZ(block.cz);
        double x1 = x0 + DistrictPlan.CHUNK;
        double z1 = z0 + DistrictPlan.CHUNK;

        // Street lights along roads at regular intervals
        double spacing = STREET_LIGHT_SPACING;
        double offset = ROAD_WIDTH / 2 + SIDEWALK_WIDTH + 2;

        // North-South roads
        for (double cz = z0; cz <= z1; cz += spacing) {
            addStreetLight(block.root, x0 + offset, cz, rn);
            addStreetLight(block.root, x0 - offset, cz, rn);
        }

        // East-West roads
        for (int k = DistrictPlan.MIN_CZ; k <= DistrictPlan.MAX_CZ + 1; k++) {
            double cz = k * DistrictPlan.CHUNK;
            if (cz >= z0 && cz <= z1) {
                for (double cx = x0; cx <= x1; cx += spacing) {
                    addStreetLight(block.root, cx, cz + offset, rn);
                    addStreetLight(block.root, cx, cz - offset, rn);
                }
            }
        }
    }

    private static void addStreetLight(Group root, double x, double z, Random rn) {
        // Pole
        Cylinder pole = new Cylinder(2.5, 150);
        pole.setMaterial(STEEL_POLE);
        pole.setTranslateX(x);
        pole.setTranslateY(Constants.GROUND_Y + 75);
        pole.setTranslateZ(z);
        root.getChildren().add(pole);

        // Arm
        Box arm = new Box(22, 3, 4);
        arm.setMaterial(STEEL_POLE);
        arm.setTranslateX(x + (rn.nextBoolean() ? 11 : -11));
        arm.setTranslateY(Constants.GROUND_Y - 150);
        arm.setTranslateZ(z);
        root.getChildren().add(arm);

        // Light fixture
        Sphere bulb = new Sphere(4, 12);
        bulb.setMaterial(LIGHT_BULB);
        bulb.setTranslateX(x + (rn.nextBoolean() ? 18 : -18));
        bulb.setTranslateY(Constants.GROUND_Y - 147);
        bulb.setTranslateZ(z);
        root.getChildren().add(bulb);
    }

    // ============================================================
    // Street Trees
    // ============================================================

    private static void buildStreetTrees(DistrictBlock block, Random rn) {
        double x0 = cxToX(block.cx);
        double z0 = czToZ(block.cz);
        double spacing = TREE_SPACING;
        double offset = ROAD_WIDTH / 2 + SIDEWALK_WIDTH / 2;

        for (double cz = z0; cz <= z0 + DistrictPlan.CHUNK; cz += spacing) {
            // Alternate sides
            int side = (int) (cz / spacing) % 2;
            double cx = x0 + (side == 0 ? offset : -offset);
            TreeFactory.Tree tree = TreeFactory.create(TreeFactory.OAK, 1.0 + rn.nextDouble() * 0.3, cx, cz, Constants.GROUND_Y, rn);
            block.trees.add(tree);
            block.root.getChildren().add(tree.node);
        }
    }

    // ============================================================
    // Parking Lanes
    // ============================================================

    private static void buildParkingLanes(DistrictBlock block, Random rn) {
        // Parallel parking along commercial streets
        if (block.plan.pattern.equals("shops") || block.plan.pattern.equals("supermarket")) {
            double x0 = cxToX(block.cx);
            double z0 = czToZ(block.cz);
            double offset = ROAD_WIDTH / 2 + PARKING_LANE_WIDTH / 2;

            for (double cz = z0 + 20; cz < z0 + DistrictPlan.CHUNK - 20; cz += 8) {
                if (rn.nextDouble() < 0.7) {
                    double cx = x0 + offset;
                    int kind = rn.nextInt(4);
                    int colorIdx = rn.nextInt(CAR_COLORS.length);
                    Group g = VehicleFactory.create(kind, CAR_COLORS[colorIdx], false, false, rn);
                    g.setTranslateX(cx);
                    g.setTranslateY(Constants.GROUND_Y);
                    g.setTranslateZ(cz);
                    g.getTransforms().add(new Rotate(90, Rotate.Y_AXIS));
                    block.root.getChildren().add(g);

                    // Create a Car object to track
                    Car car = new Car();
                    car.x = cx;
                    car.z = cz;
                    car.baseYaw = 90;
                    block.parkedCars.add(car);
                }
            }
        }
    }

    // ============================================================
    // Crosswalks
    // ============================================================

    private static void buildCrosswalks(DistrictBlock block, Random rn) {
        double x0 = cxToX(block.cx);
        double z0 = czToZ(block.cz);

        // Crosswalks at intersections
        for (int k = DistrictPlan.MIN_CZ; k <= DistrictPlan.MAX_CZ + 1; k++) {
            double cz = k * DistrictPlan.CHUNK;
            if (cz > z0 && cz < z0 + DistrictPlan.CHUNK) {
                addCrosswalk(block.root, x0 - ROAD_WIDTH / 2, x0 + ROAD_WIDTH / 2, cz, rn);
            }
        }
    }

    private static void addCrosswalk(Group root, double x0, double x1, double z, Random rn) {
        double stripeW = 2.4, stripeGap = 2.4, stripeLen = 36;
        int count = (int) ((x1 - x0) / (stripeW + stripeGap)) + 1;
        for (int i = 0; i < count; i++) {
            double cx = x0 + i * (stripeW + stripeGap) + stripeW / 2;
            Box stripe = new Box(stripeW, 2, stripeLen);
            stripe.setMaterial(PAINT_WHITE);
            stripe.setTranslateX(cx);
            stripe.setTranslateY(Constants.GROUND_Y + 3.1);
            stripe.setTranslateZ(z);
            root.getChildren().add(stripe);
        }
    }

    // ============================================================
    // District Plan Buildings
    // ============================================================

    private static void buildDistrictBuildings(DistrictBlock block, long worldSeed, Random rn) {
        for (DistrictPlan.Lot lot : block.plan.lots) {
            switch (lot.kind) {
                case OFFICE, APARTMENT -> {
                    // Use LargeBuilding for taller structures
                    LargeBuilding.Type type = lot.kind == DistrictPlan.Kind.OFFICE ?
                            LargeBuilding.Type.COMMERCIAL_OFFICE : LargeBuilding.Type.APARTMENT_BLOCK;
                    int floors = Math.min(lot.floors, type.maxFloors);
                    LargeBuilding.BuildingData bd = LargeBuilding.create(
                            type, lot.x, lot.z, floors, lot.style, lot.front, rn);
                    block.largeBuildings.add(bd);
                    block.root.getChildren().add(bd.group);
                    block.root.getChildren().add(bd.roofGroup);

                    // Transfer spawn/loot
                    for (double[] sp : bd.spawnPoints) {
                        block.plan.trees.add(new DistrictPlan.Spot(sp[0], sp[1], 1, 0, 0)); // reuse spot
                    }
                }
                case SHOP, SUPERMARKET, GAS_SHOP -> {
                    // Use existing shop builder (handled by DistrictBuilder)
                }
                case PLAZA, PARK, PARKING, CANOPY -> {
                    // Open space - already handled by DistrictBuilder ground planes
                }
            }
        }
    }

    // ============================================================
    // Street Props
    // ============================================================

    private static void buildStreetProps(DistrictBlock block, Random rn) {
        double x0 = cxToX(block.cx);
        double z0 = czToZ(block.cz);

        // Trash cans, hydrants, mailboxes, benches along sidewalks
        double spacing = 40;
        double offset = ROAD_WIDTH / 2 + SIDEWALK_WIDTH / 2 + 3;

        for (double cz = z0 + 20; cz < z0 + DistrictPlan.CHUNK - 20; cz += spacing) {
            if (rn.nextDouble() < 0.3) {
                double cx = x0 + (rn.nextBoolean() ? offset : -offset);
                addProp(block.root, cx, cz, "trashcan", rn);
            }
            if (rn.nextDouble() < 0.15) {
                double cx = x0 + (rn.nextBoolean() ? offset : -offset);
                addProp(block.root, cx, cz, "hydrant", rn);
            }
            if (rn.nextDouble() < 0.1) {
                double cx = x0 + (rn.nextBoolean() ? offset : -offset);
                addProp(block.root, cx, cz, "mailbox", rn);
            }
        }

        // Bus stops
        if (rn.nextDouble() < 0.4) {
            double cz = z0 + 80 + rn.nextDouble() * (DistrictPlan.CHUNK - 160);
            addBusStop(block.root, x0 + offset, cz, rn);
        }
    }

    private static void addProp(Group root, double x, double z, String type, Random rn) {
        Group g = new Group();
        switch (type) {
            case "trashcan" -> {
                Cylinder can = new Cylinder(12, 40, 12);
                can.setMaterial(new PhongMaterial(Color.rgb(60, 60, 65)));
                can.setTranslateY(20);
                g.getChildren().add(can);
                Box lid = new Box(18, 4, 18);
                lid.setMaterial(new PhongMaterial(Color.rgb(40, 40, 45)));
                lid.setTranslateY(40);
                g.getChildren().add(lid);
            }
            case "hydrant" -> {
                Cylinder base = new Cylinder(8, 60, 8);
                base.setMaterial(new PhongMaterial(Color.rgb(200, 40, 40)));
                base.setTranslateY(30);
                g.getChildren().add(base);
                Cylinder top = new Cylinder(12, 20, 12);
                top.setMaterial(new PhongMaterial(Color.rgb(200, 40, 40)));
                top.setTranslateY(65);
                g.getChildren().add(top);
            }
            case "mailbox" -> {
                Box post = new Box(6, 80, 6);
                post.setMaterial(new PhongMaterial(Color.rgb(80, 60, 40)));
                post.setTranslateY(40);
                g.getChildren().add(post);
                Box box = new Box(18, 24, 14);
                box.setMaterial(new PhongMaterial(Color.rgb(30, 80, 180)));
                box.setTranslateY(85);
                g.getChildren().add(box);
            }
        }
        g.setTranslateX(x);
        g.setTranslateY(Constants.GROUND_Y);
        g.setTranslateZ(z);
        g.getTransforms().add(new Rotate(rn.nextDouble() * 360, Rotate.Y_AXIS));
        root.getChildren().add(g);
    }

    private static void addBusStop(Group root, double x, double z, Random rn) {
        Group stop = new Group();
        // Shelter
        Box roof = new Box(60, 4, 24);
        roof.setMaterial(new PhongMaterial(Color.rgb(180, 180, 190)));
        roof.setTranslateY(120);
        stop.getChildren().add(roof);

        Box back = new Box(60, 80, 3);
        back.setMaterial(new PhongMaterial(Color.rgb(200, 200, 210, 0.6)));
        back.setTranslateY(80);
        back.setTranslateZ(10.5);
        stop.getChildren().add(back);

        Box bench = new Box(48, 3, 14);
        bench.setMaterial(Mats.WOOD);
        bench.setTranslateY(40);
        bench.setTranslateZ(2);
        stop.getChildren().add(bench);

        stop.setTranslateX(x);
        stop.setTranslateY(Constants.GROUND_Y);
        stop.setTranslateZ(z);
        root.getChildren().add(stop);
    }

    // ============================================================
    // Parked Cars
    // ============================================================

    private static void buildParkedCars(DistrictBlock block, Random rn) {
        // Additional parked cars in parking lots
        for (DistrictPlan.Lot lot : block.plan.lots) {
            if (lot.kind == DistrictPlan.Kind.PARKING) {
                for (double x = lot.x - lot.w / 2 + 20; x < lot.x + lot.w / 2 - 20; x += 24) {
                    for (double z = lot.z - lot.d / 2 + 20; z < lot.z + lot.d / 2 - 20; z += 16) {
                        if (rn.nextDouble() < 0.6) {
                            int kind = rn.nextInt(4);
                            int colorIdx = rn.nextInt(CAR_COLORS.length);
                            boolean wreck = rn.nextDouble() < 0.2;
                            Group g = VehicleFactory.create(kind, CAR_COLORS[colorIdx], wreck, false, rn);
                            double cx = x + (rn.nextDouble() - 0.5) * 4;
                            double cz = z + (rn.nextDouble() - 0.5) * 4;
                            int yaw = rn.nextBoolean() ? 0 : 90;
                            g.setTranslateX(cx);
                            g.setTranslateY(Constants.GROUND_Y);
                            g.setTranslateZ(cz);
                            g.getTransforms().add(new Rotate(yaw, Rotate.Y_AXIS));
                            block.root.getChildren().add(g);

                            // Create a Car object to track
                            Car car = new Car();
                            car.x = cx;
                            car.z = cz;
                            car.baseYaw = yaw;
                            block.parkedCars.add(car);
                        }
                    }
                }
            }
        }
    }

    private static void addCarToRoot(Group root, Car car) {
        // Car class has: x, z, baseYaw, turned, g, rot
        // Need to create vehicle based on stored data
        // Since Car doesn't store kind/color, we'll use baseYaw to determine
        int kind = (int) (Math.abs(car.baseYaw) % 4);
        int colorIdx = (int) (Math.abs(car.x + car.z) % CAR_COLORS.length);
        Group g = VehicleFactory.create(kind, CAR_COLORS[colorIdx], false, false, new Random());
        g.setTranslateX(car.x);
        g.setTranslateY(Constants.GROUND_Y);
        g.setTranslateZ(car.z);
        g.getTransforms().add(new Rotate(car.baseYaw, Rotate.Y_AXIS));
        root.getChildren().add(g);
    }

    // ============================================================
    // District Plan Features
    // ============================================================

    private static void addDistrictPlanFeatures(DistrictBlock block, long worldSeed, Random rn) {
        // Trees from district plan
        for (DistrictPlan.Spot t : block.plan.trees) {
            int[] species = {TreeFactory.OAK, TreeFactory.BIRCH, TreeFactory.PINE};
            TreeFactory.Tree tree = TreeFactory.create(species[t.variant % 3], t.scale, t.x, t.z, Constants.GROUND_Y, rn);
            block.trees.add(tree);
            block.root.getChildren().add(tree.node);
        }

        // Benches from district plan
        for (DistrictPlan.Spot s : block.plan.benches) {
            addBench(block.root, s.x, s.z, s.yaw);
        }
    }

    private static void addBench(Group root, double x, double z, double yaw) {
        Group g = new Group();
        g.getChildren().add(Gfx.box(40, 3, 14, Mats.WOOD, 0, -17, 0));
        g.getChildren().add(Gfx.box(40, 12, 2.5, Mats.WOOD, 0, -25, -6));
        g.getChildren().add(Gfx.box(3, 15, 12, Mats.BLACK_METAL, -17, -7.5, 0));
        g.getChildren().add(Gfx.box(3, 15, 12, Mats.BLACK_METAL, 17, -7.5, 0));
        g.setTranslateX(x);
        g.setTranslateY(Constants.GROUND_Y);
        g.setTranslateZ(z);
        g.getTransforms().add(new Rotate(yaw, Rotate.Y_AXIS));
        root.getChildren().add(g);
    }

    // ============================================================
    // Convert to ChunkManager Content
    // ============================================================

    private static DistrictBuilder.Content convertToChunkContent(DistrictBlock block) {
        // Use DistrictBuilder.build to create proper content
        DistrictPlan.Plan plan = block.plan;
        // We need a collision callback - use a simple one
        HouseBuilder.Collision col = (x, z, w, d, h, zp) -> {
            Obstacle o = new Obstacle(x, z, w, d, h, zp);
            return o;
        };
        DistrictBuilder.Content c = DistrictBuilder.build(plan, col);

        // Add large buildings
        for (LargeBuilding.BuildingData bd : block.largeBuildings) {
            Building b = LargeBuilding.toBuilding(bd);
            c.buildings.add(b);
            c.doorIds.add("lb_" + block.cx + "_" + block.cz + "_" + c.buildings.size());
            c.root.getChildren().add(b.group);
            c.root.getChildren().add(b.roof);
        }

        // Add extra trees
        c.trees.addAll(block.trees);

        return c;
    }

    // ============================================================
    // Helper Methods
    // ============================================================

    private static double cxToX(int cx) {
        return cx * DistrictPlan.CHUNK;
    }

    private static double czToZ(int cz) {
        return cz * DistrictPlan.CHUNK;
    }

    // ============================================================
    // Performance Profiling
    // ============================================================

    public static class ProfileResult {
        public long buildTimeNanos;
        public int nodeCount;
        public int buildingCount;
        public int treeCount;
        public int carCount;
        public int lightCount;
    }

    /**
     * Generate a block and profile performance.
     */
    public static ProfileResult profileBlock(long worldSeed, int cx, int cz) {
        long start = System.nanoTime();
        DistrictBlock block = generateBlock(worldSeed, cx, cz);
        long elapsed = System.nanoTime() - start;

        ProfileResult r = new ProfileResult();
        r.buildTimeNanos = elapsed;
        r.nodeCount = countNodes(block.root);
        r.buildingCount = block.largeBuildings.size();
        r.treeCount = block.trees.size();
        r.carCount = block.parkedCars.size();
        r.lightCount = countStreetLights(block.root);
        return r;
    }

    private static int countNodes(Group g) {
        int count = 1;
        for (var child : g.getChildren()) {
            if (child instanceof Group gr) {
                count += countNodes(gr);
            } else {
                count++;
            }
        }
        return count;
    }

    private static int countStreetLights(Group g) {
        int count = 0;
        for (var child : g.getChildren()) {
            if (child instanceof Sphere) {
                count++;
            } else if (child instanceof Group gr) {
                count += countStreetLights(gr);
            }
        }
        return count;
    }
}