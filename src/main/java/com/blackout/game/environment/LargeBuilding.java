package com.blackout.game.environment;

import com.blackout.game.core.Dims;
import com.blackout.game.core.Constants;

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
 * Phase 4: Larger buildings with proper scale validation.
 * Buildings are roughly 2x the current house footprint/volume with validated player clearance.
 * Includes residential, commercial, and institutional building types.
 */
public final class LargeBuilding {

    private LargeBuilding() {}

    // ============================================================
    // Scale Constants (validated against player rig)
    // ============================================================

    // Player dimensions (from Dims): height 118, collision radius 16
    // Door requirements: min height 118+14=132, min width 64
    // Ceiling height: 168 (2.6m) - validated
    // Window sill: 62, Window head: 122

    // Larger building dimensions (2x footprint)
    public static final double LARGE_WALL_T = 12;           // Thicker walls for larger buildings
    public static final double LARGE_PARTITION_T = 8;
    public static final double LARGE_WALL_H = 200;          // Higher ceilings (3m)
    public static final double LARGE_DOOR_W = 80;           // Wider doors
    public static final double LARGE_DOOR_H = 160;          // Taller doors (clears player + gear)
    public static final double LARGE_WINDOW_W = 60;
    public static final double LARGE_WINDOW_SILL = 80;
    public static final double LARGE_WINDOW_HEAD = 160;
    public static final double LARGE_DOORWAY_W = 80;
    public static final double LARGE_DOORWAY_H = 160;
    public static final double LARGE_PATH_RADIUS = 24;      // Larger clearance for furniture

    // Building type presets
    public enum Type {
        RESIDENTIAL_LARGE("Large Residential", 240, 200, 2, 4),
        APARTMENT_BLOCK("Apartment Block", 320, 240, 3, 6),
        COMMERCIAL_OFFICE("Commercial Office", 280, 220, 2, 4),
        RETAIL_STORE("Retail Store", 200, 180, 1, 2),
        WAREHOUSE("Warehouse", 400, 300, 1, 2),
        HOSPITAL_WING("Hospital Wing", 240, 200, 2, 3),
        POLICE_STATION("Police Station", 220, 180, 2, 3),
        FIRE_STATION("Fire Station", 180, 160, 1, 2),
        SCHOOL_BLOCK("School Block", 260, 220, 2, 3),
        INDUSTRIAL("Industrial", 360, 280, 1, 2),
        MALL_ANCHOR("Mall Anchor", 300, 250, 1, 2);

        public final String displayName;
        public final double width, depth;
        public final int minFloors, maxFloors;

        Type(String displayName, double width, double depth, int minFloors, int maxFloors) {
            this.displayName = displayName;
            this.width = width;
            this.depth = depth;
            this.minFloors = minFloors;
            this.maxFloors = maxFloors;
        }
    }

    // ============================================================
    // Building Data
    // ============================================================

    public static final class BuildingData {
        public final Type type;
        public final double x, z;
        public final int floors;
        public final int style;           // Visual style variant
        public final int front;           // Door facing: +1 = +z, -1 = -z, 0 = none
        public final Group group = new Group();
        public final Group roofGroup = new Group();
        public final List<double[]> spawnPoints = new ArrayList<>();
        public final List<double[]> lootPoints = new ArrayList<>();
        public final List<Doorway> doorways = new ArrayList<>();

        public BuildingData(Type type, double x, double z, int floors, int style, int front) {
            this.type = type;
            this.x = x;
            this.z = z;
            this.floors = floors;
            this.style = style;
            this.front = front;
        }
    }

    public static final class Doorway {
        public final double x, z, width;
        public final int connectsFloor;   // -1 = exterior, 0+ = interior floor

        public Doorway(double x, double z, double width, int connectsFloor) {
            this.x = x; this.z = z; this.width = width; this.connectsFloor = connectsFloor;
        }
    }

    // ============================================================
    // Materials
    // ============================================================

    private static final PhongMaterial EXT_WALL_MAT = new PhongMaterial(Color.rgb(160, 150, 140));
    private static final PhongMaterial EXT_WALL_DARK = new PhongMaterial(Color.rgb(120, 110, 100));
    private static final PhongMaterial INT_WALL_MAT = new PhongMaterial(Color.rgb(190, 185, 175));
    private static final PhongMaterial INT_WALL_DARK = new PhongMaterial(Color.rgb(100, 95, 90));
    private static final PhongMaterial FLOOR_WOOD = new PhongMaterial(Color.rgb(140, 110, 80));
    private static final PhongMaterial FLOOR_CONCRETE = new PhongMaterial(Color.rgb(130, 130, 135));
    private static final PhongMaterial FLOOR_TILE = new PhongMaterial(Color.rgb(180, 175, 170));
    private static final PhongMaterial ROOF_MAT = new PhongMaterial(Color.rgb(60, 55, 50));
    private static final PhongMaterial ROOF_MAT_DARK = new PhongMaterial(Color.rgb(40, 35, 30));
    private static final PhongMaterial GLASS_DARK = new PhongMaterial(Color.rgb(40, 50, 70, 0.4));
    private static final PhongMaterial GLASS_LIT = new PhongMaterial(Color.rgb(255, 210, 140, 0.6));
    private static final PhongMaterial DOOR_MAT = new PhongMaterial(Color.rgb(90, 60, 40));
    private static final PhongMaterial WINDOW_FRAME = new PhongMaterial(Color.rgb(80, 70, 60));

    static {
        EXT_WALL_MAT.setSpecularColor(Color.rgb(20, 20, 20));
        EXT_WALL_DARK.setSpecularColor(Color.rgb(10, 10, 10));
        INT_WALL_MAT.setSpecularColor(Color.rgb(15, 15, 15));
        INT_WALL_DARK.setSpecularColor(Color.rgb(8, 8, 8));
        FLOOR_WOOD.setSpecularColor(Color.rgb(30, 30, 30));
        FLOOR_CONCRETE.setSpecularColor(Color.rgb(20, 20, 20));
        FLOOR_TILE.setSpecularColor(Color.rgb(40, 40, 40));
        ROOF_MAT.setSpecularColor(Color.rgb(10, 10, 10));
        ROOF_MAT_DARK.setSpecularColor(Color.BLACK);
        GLASS_DARK.setSpecularColor(Color.rgb(80, 100, 140));
        GLASS_LIT.setSpecularColor(Color.rgb(200, 180, 120));
        DOOR_MAT.setSpecularColor(Color.rgb(40, 30, 20));
        WINDOW_FRAME.setSpecularColor(Color.rgb(20, 20, 20));
    }

    // ============================================================
    // Public API
    // ============================================================

    /**
     * Create a large building with proper scale validation.
     */
    public static BuildingData create(Type type, double x, double z, int floors, int style, int front, Random rn) {
        // Validate scale
        validateScale(type, floors);

        BuildingData data = new BuildingData(type, x, z, floors, style, front);

        // Build each floor
        for (int f = 0; f < floors; f++) {
            double floorY = Constants.GROUND_Y + f * LARGE_WALL_H;
            buildFloor(data, f, floorY, rn);
        }

        // Build roof
        buildRoof(data, rn);

        // Add exterior details
        addExteriorDetails(data, rn);

        return data;
    }

    /**
     * Create a building with randomized parameters appropriate for its type.
     */
    public static BuildingData createRandom(Type type, double x, double z, Random rn) {
        int floors = type.minFloors + rn.nextInt(type.maxFloors - type.minFloors + 1);
        int style = rn.nextInt(4);
        int front = (rn.nextInt(4) - 1) * 90; // -90, 0, 90, 180 (but we use +1/-1 for door facing)
        // Convert to door facing convention
        int doorFacing = front == 0 ? 1 : front == 90 ? 1 : front == 180 ? -1 : 1;
        return create(type, x, z, floors, style, doorFacing, rn);
    }

    // ============================================================
    // Floor Construction
    // ============================================================

    private static void buildFloor(BuildingData data, int floorIndex, double floorY, Random rn) {
        double w = data.type.width;
        double d = data.type.depth;
        double hw = w / 2, hd = d / 2;

        // Floor slab
        PhongMaterial floorMat = getFloorMaterial(data.type, floorIndex);
        Box floor = new Box(w, 4, d);
        floor.setMaterial(floorMat);
        floor.setTranslateX(data.x);
        floor.setTranslateY(floorY + 2);
        floor.setTranslateZ(data.z);
        data.group.getChildren().add(floor);

        // Exterior walls with openings
        buildExteriorWalls(data, floorIndex, floorY, hw, hd, rn);

        // Interior layout based on building type
        switch (data.type) {
            case RESIDENTIAL_LARGE, APARTMENT_BLOCK -> buildResidentialInterior(data, floorIndex, floorY, hw, hd, rn);
            case COMMERCIAL_OFFICE -> buildOfficeInterior(data, floorIndex, floorY, hw, hd, rn);
            case RETAIL_STORE -> buildRetailInterior(data, floorIndex, floorY, hw, hd, rn);
            case WAREHOUSE -> buildWarehouseInterior(data, floorIndex, floorY, hw, hd, rn);
            case HOSPITAL_WING -> buildHospitalInterior(data, floorIndex, floorY, hw, hd, rn);
            case POLICE_STATION -> buildPoliceInterior(data, floorIndex, floorY, hw, hd, rn);
            case FIRE_STATION -> buildFireStationInterior(data, floorIndex, floorY, hw, hd, rn);
            case SCHOOL_BLOCK -> buildSchoolInterior(data, floorIndex, floorY, hw, hd, rn);
            case INDUSTRIAL -> buildIndustrialInterior(data, floorIndex, floorY, hw, hd, rn);
            case MALL_ANCHOR -> buildMallAnchorInterior(data, floorIndex, floorY, hw, hd, rn);
        }

        // Interior lighting
        addFloorLights(data, floorIndex, floorY, hw, hd, rn);
    }

    private static void buildExteriorWalls(BuildingData data, int floorIndex, double floorY,
                                           double hw, double hd, Random rn) {
        PhongMaterial wallMat = (data.style % 2 == 0) ? EXT_WALL_MAT : EXT_WALL_DARK;
        double w = data.type.width;
        double d = data.type.depth;

        // Front wall (+z) - main entrance
        buildWallWithOpenings(data, floorY, data.x, data.z + hd, w, LARGE_WALL_T,
                getFrontWallOpenings(data, floorIndex, hw), wallMat, true, rn);

        // Back wall (-z)
        buildWallWithOpenings(data, floorY, data.x, data.z - hd, w, LARGE_WALL_T,
                getBackWallOpenings(data, floorIndex, hw, rn), wallMat, true, rn);

        // Left wall (-x)
        buildWallWithOpenings(data, floorY, data.x - hw, data.z, LARGE_WALL_T, d,
                getSideWallOpenings(data, floorIndex, hd, rn), wallMat, true, rn);

        // Right wall (+x)
        buildWallWithOpenings(data, floorY, data.x + hw, data.z, LARGE_WALL_T, d,
                getSideWallOpenings(data, floorIndex, hd, rn), wallMat, true, rn);
    }

    private static void buildWallWithOpenings(BuildingData data, double floorY,
                                              double cx, double cz, double w, double d,
                                              List<Opening> openings, PhongMaterial mat,
                                              boolean collidable, Random rn) {
        // Sort openings by center position
        openings.sort((a, b) -> Double.compare(a.center, b.center));

        double cur = -w / 2;
        for (Opening op : openings) {
            double a = op.center - op.width / 2;
            double e = op.center + op.width / 2;

            // Solid piece before opening
            if (a > cur + 0.5) {
                addWallPiece(data, cx, cz, cur, a, floorY, LARGE_WALL_H, mat, collidable);
            }

            // Opening: door or window
            if (op.isDoor) {
                // Door opening - leave space for door frame
                addWallPiece(data, cx, cz, a, e, floorY, LARGE_DOOR_H, mat, false);
                addWallPiece(data, cx, cz, a, e, floorY + LARGE_DOOR_H,
                        LARGE_WALL_H - LARGE_DOOR_H, mat, collidable);
                // Add door frame
                addDoorFrame(data, cx, cz, a, e, floorY, op.isExterior);
            } else {
                // Window
                addWallPiece(data, cx, cz, a, e, floorY, LARGE_WINDOW_SILL, mat, collidable);
                addWallPiece(data, cx, cz, a, e, floorY + LARGE_WINDOW_HEAD,
                        LARGE_WALL_H - LARGE_WINDOW_HEAD, mat, collidable);
                // Window glass
                addWindowGlass(data, cx, cz, a, e, floorY, rn);
                // Window frame
                addWindowFrame(data, cx, cz, a, e, floorY);
            }

            cur = e;
        }

        // Solid piece after last opening
        if (cur < w / 2 - 0.5) {
            addWallPiece(data, cx, cz, cur, w / 2, floorY, LARGE_WALL_H, mat, collidable);
        }
    }

    private static void addWallPiece(BuildingData data, double cx, double cz,
                                     double from, double to, double floorY, double height,
                                     PhongMaterial mat, boolean collidable) {
        double len = to - from;
        if (len < 0.5 || height < 0.5) return;

        double mid = (from + to) / 2;
        double wx = (cx == data.x ? data.x + mid : cx);
        double wz = (cz == data.z ? data.z + mid : cz);

        Box wall = new Box(
                cx == data.x ? len : LARGE_WALL_T,
                height,
                cz == data.z ? len : LARGE_WALL_T
        );
        wall.setMaterial(mat);
        wall.setTranslateX(wx);
        wall.setTranslateY(floorY + height / 2);
        wall.setTranslateZ(wz);
        data.group.getChildren().add(wall);
    }

    // ============================================================
    // Opening Definitions
    // ============================================================

    private static class Opening {
        final double center, width;
        final boolean isDoor, isExterior;

        Opening(double center, double width, boolean isDoor, boolean isExterior) {
            this.center = center; this.width = width; this.isDoor = isDoor; this.isExterior = isExterior;
        }
    }

    private static List<Opening> getFrontWallOpenings(BuildingData data, int floorIndex, double hw) {
        List<Opening> ops = new ArrayList<>();

        // Main entrance on ground floor
        if (floorIndex == 0) {
            ops.add(new Opening(0, LARGE_DOOR_W, true, true));
        }

        // Windows on either side
        double winSpace = hw - LARGE_DOOR_W / 2 - 20;
        if (winSpace > LARGE_WINDOW_W + 10) {
            ops.add(new Opening(-LARGE_DOOR_W / 2 - 10 - winSpace / 2, LARGE_WINDOW_W, false, true));
            ops.add(new Opening(LARGE_DOOR_W / 2 + 10 + winSpace / 2, LARGE_WINDOW_W, false, true));
        }

        return ops;
    }

    private static List<Opening> getBackWallOpenings(BuildingData data, int floorIndex, double hw, Random rn) {
        List<Opening> ops = new ArrayList<>();

        // Back door on ground floor (sometimes)
        if (floorIndex == 0 && rn.nextDouble() < 0.4) {
            ops.add(new Opening(0, LARGE_DOOR_W, true, true));
        }

        // Windows
        int numWindows = 2 + rn.nextInt(2);
        for (int i = 0; i < numWindows; i++) {
            double pos = -hw / 2 + 20 + (i + 0.5) * (hw - 40) / numWindows;
            ops.add(new Opening(pos, LARGE_WINDOW_W, false, true));
        }

        return ops;
    }

    private static List<Opening> getSideWallOpenings(BuildingData data, int floorIndex, double hd, Random rn) {
        List<Opening> ops = new ArrayList<>();

        // Windows along the side
        int numWindows = 2 + rn.nextInt(2);
        for (int i = 0; i < numWindows; i++) {
            double pos = -hd / 2 + 20 + (i + 0.5) * (hd - 40) / numWindows;
            ops.add(new Opening(pos, LARGE_WINDOW_W, false, true));
        }

        return ops;
    }

    // ============================================================
    // Door/Window Details
    // ============================================================

    private static void addDoorFrame(BuildingData data, double cx, double cz,
                                     double from, double to, double floorY, boolean exterior) {
        double mid = (from + to) / 2;
        double wx = (cx == data.x ? data.x + mid : cx);
        double wz = (cz == data.z ? data.z + mid : cz);
        double len = to - from;

        // Frame sides
        Box left = new Box(4, LARGE_DOOR_H, LARGE_WALL_T + 4);
        left.setMaterial(DOOR_MAT);
        left.setTranslateX(wx - len / 2 - 2);
        left.setTranslateY(floorY + LARGE_DOOR_H / 2);
        left.setTranslateZ(wz);
        data.group.getChildren().add(left);

        Box right = new Box(4, LARGE_DOOR_H, LARGE_WALL_T + 4);
        right.setMaterial(DOOR_MAT);
        right.setTranslateX(wx + len / 2 + 2);
        right.setTranslateY(floorY + LARGE_DOOR_H / 2);
        right.setTranslateZ(wz);
        data.group.getChildren().add(right);

        // Header
        Box header = new Box(len + 4, 4, LARGE_WALL_T + 4);
        header.setMaterial(DOOR_MAT);
        header.setTranslateX(wx);
        header.setTranslateY(floorY + LARGE_DOOR_H + 2);
        header.setTranslateZ(wz);
        data.group.getChildren().add(header);
    }

    private static void addWindowGlass(BuildingData data, double cx, double cz,
                                       double from, double to, double floorY, Random rn) {
        double mid = (from + to) / 2;
        double wx = (cx == data.x ? data.x + mid : cx);
        double wz = (cz == data.z ? data.z + mid : cz);
        double len = to - from;
        double hh = LARGE_WINDOW_HEAD - LARGE_WINDOW_SILL;

        PhongMaterial glass = rn.nextDouble() < 0.3 ? GLASS_LIT : GLASS_DARK;

        Box glassPane = new Box(
                cx == data.x ? len - 4 : 2,
                hh - 4,
                cz == data.z ? len - 4 : 2
        );
        glassPane.setMaterial(glass);
        glassPane.setTranslateX(wx);
        glassPane.setTranslateY(floorY + LARGE_WINDOW_SILL + hh / 2);
        glassPane.setTranslateZ(wz);
        data.group.getChildren().add(glassPane);
    }

    private static void addWindowFrame(BuildingData data, double cx, double cz,
                                       double from, double to, double floorY) {
        double mid = (from + to) / 2;
        double wx = (cx == data.x ? data.x + mid : cx);
        double wz = (cz == data.z ? data.z + mid : cz);
        double len = to - from;
        double hh = LARGE_WINDOW_HEAD - LARGE_WINDOW_SILL;

        // Sill
        Box sill = new Box(
                cx == data.x ? len + 6 : LARGE_WALL_T + 6,
                4,
                cz == data.z ? len + 6 : LARGE_WALL_T + 6
        );
        sill.setMaterial(WINDOW_FRAME);
        sill.setTranslateX(wx);
        sill.setTranslateY(floorY + LARGE_WINDOW_SILL - 2);
        sill.setTranslateZ(wz);
        data.group.getChildren().add(sill);

        // Head
        Box head = new Box(
                cx == data.x ? len + 6 : LARGE_WALL_T + 6,
                4,
                cz == data.z ? len + 6 : LARGE_WALL_T + 6
        );
        head.setMaterial(WINDOW_FRAME);
        head.setTranslateX(wx);
        head.setTranslateY(floorY + LARGE_WINDOW_HEAD + 2);
        head.setTranslateZ(wz);
        data.group.getChildren().add(head);

        // Mullion (vertical)
        Box mullion = new Box(
                cx == data.x ? 2 : LARGE_WALL_T + 4,
                hh,
                cz == data.z ? 2 : LARGE_WALL_T + 4
        );
        mullion.setMaterial(WINDOW_FRAME);
        mullion.setTranslateX(wx);
        mullion.setTranslateY(floorY + LARGE_WINDOW_SILL + hh / 2);
        mullion.setTranslateZ(wz);
        data.group.getChildren().add(mullion);
    }

    // ============================================================
    // Interior Layouts by Building Type
    // ============================================================

    private static void buildResidentialInterior(BuildingData data, int floorIndex, double floorY,
                                                 double hw, double hd, Random rn) {
        PhongMaterial intMat = (data.style % 2 == 0) ? INT_WALL_MAT : INT_WALL_DARK;

        // Partition wall down the middle (x-axis)
        List<Opening> partOps = new ArrayList<>();
        partOps.add(new Opening(0, LARGE_DOORWAY_W, false, false)); // Central doorway

        buildWallWithOpenings(data, floorY, data.x, data.z, 0, 2 * hw,
                partOps, intMat, true, rn);

        // Rooms on each side get furniture (would use HouseProps)
        // Spawn points in bedrooms
        data.spawnPoints.add(new double[]{data.x - hw / 2, data.z, floorY});
        data.spawnPoints.add(new double[]{data.x + hw / 2, data.z, floorY});

        // Loot spots near furniture
        data.lootPoints.add(new double[]{data.x - hw / 2, data.z - hd / 3, floorY});
        data.lootPoints.add(new double[]{data.x + hw / 2, data.z + hd / 3, floorY});
    }

    private static void buildOfficeInterior(BuildingData data, int floorIndex, double floorY,
                                            double hw, double hd, Random rn) {
        PhongMaterial intMat = INT_WALL_MAT;

        // Grid of cubicles/offices
        int cols = 3, rows = 2;
        double cw = hw * 2 / cols;
        double cd = hd * 2 / rows;

        for (int cx = 0; cx < cols; cx++) {
            for (int cz = 0; cz < rows; cz++) {
                double ox = data.x - hw + (cx + 0.5) * cw;
                double oz = data.z - hd + (cz + 0.5) * cd;

                // Small office walls
                List<Opening> ops = new ArrayList<>();
                ops.add(new Opening(0, LARGE_DOORWAY_W, false, false));

                // Only build walls between offices
                if (cx < cols - 1) {
                    buildWallWithOpenings(data, floorY, ox + cw / 2, oz, 0, cd,
                            ops, intMat, true, rn);
                }
                if (cz < rows - 1) {
                    buildWallWithOpenings(data, floorY, ox, oz + cd / 2, cw, 0,
                            ops, intMat, true, rn);
                }
            }
        }

        // Spawn points
        for (int cx = 0; cx < cols; cx++) {
            for (int cz = 0; cz < rows; cz++) {
                if (rn.nextDouble() < 0.3) {
                    data.spawnPoints.add(new double[]{
                            data.x - hw + (cx + 0.5) * cw,
                            data.z - hd + (cz + 0.5) * cd,
                            floorY
                    });
                }
            }
        }
    }

    private static void buildRetailInterior(BuildingData data, int floorIndex, double floorY,
                                            double hw, double hd, Random rn) {
        // Open floor plan with shelf rows
        PhongMaterial intMat = INT_WALL_MAT;

        // Counter at front
        if (floorIndex == 0) {
            Box counter = new Box(hw * 1.5, 80, 40);
            counter.setMaterial(new PhongMaterial(Color.rgb(120, 90, 60)));
            counter.setTranslateX(data.x);
            counter.setTranslateY(floorY + 40);
            counter.setTranslateZ(data.z - hd / 2 + 30);
            data.group.getChildren().add(counter);
        }

        // Shelf rows
        int rows = 4;
        for (int i = 0; i < rows; i++) {
            double oz = data.z - hd / 2 + 60 + i * (hd - 120) / Math.max(1, rows - 1);
            Box shelf = new Box(hw * 1.8, 120, 30);
            shelf.setMaterial(new PhongMaterial(Color.rgb(100, 80, 60)));
            shelf.setTranslateX(data.x);
            shelf.setTranslateY(floorY + 60);
            shelf.setTranslateZ(oz);
            data.group.getChildren().add(shelf);
        }

        // Spawn points
        data.spawnPoints.add(new double[]{data.x, data.z + hd / 3, floorY});
        data.lootPoints.add(new double[]{data.x, data.z - hd / 3, floorY});
    }

    private static void buildWarehouseInterior(BuildingData data, int floorIndex, double floorY,
                                               double hw, double hd, Random rn) {
        // Open space with pallet racking
        int aisles = 3;
        for (int i = 0; i < aisles; i++) {
            double ox = data.x - hw + (i + 0.5) * hw * 2 / aisles;

            // Rack on each side of aisle
            Box rack1 = new Box(40, 200, hd * 1.5);
            rack1.setMaterial(new PhongMaterial(Color.rgb(140, 120, 100)));
            rack1.setTranslateX(ox - 50);
            rack1.setTranslateY(floorY + 100);
            rack1.setTranslateZ(data.z);
            data.group.getChildren().add(rack1);

            Box rack2 = new Box(40, 200, hd * 1.5);
            rack2.setMaterial(new PhongMaterial(Color.rgb(140, 120, 100)));
            rack2.setTranslateX(ox + 50);
            rack2.setTranslateY(floorY + 100);
            rack2.setTranslateZ(data.z);
            data.group.getChildren().add(rack2);
        }

        data.spawnPoints.add(new double[]{data.x, data.z, floorY});
    }

    private static void buildHospitalInterior(BuildingData data, int floorIndex, double floorY,
                                              double hw, double hd, Random rn) {
        PhongMaterial intMat = FLOOR_TILE;

        // Corridor down the middle
        // Patient rooms on each side
        int roomsPerSide = 4;
        for (int side = -1; side <= 1; side += 2) {
            for (int i = 0; i < roomsPerSide; i++) {
                double rx = data.x + side * (hw / 2 - 60);
                double rz = data.z - hd / 2 + 40 + i * (hd - 80) / roomsPerSide;

                // Room walls
                List<Opening> ops = new ArrayList<>();
                ops.add(new Opening(0, LARGE_DOORWAY_W, false, false)); // Door to corridor

                buildWallWithOpenings(data, floorY, rx + side * 60, rz, 120, 40,
                        ops, intMat, true, rn);
            }
        }

        // Nurses station at corridor center
        if (floorIndex == 0) {
            Box station = new Box(80, 60, 40);
            station.setMaterial(new PhongMaterial(Color.rgb(200, 200, 210)));
            station.setTranslateX(data.x);
            station.setTranslateY(floorY + 30);
            station.setTranslateZ(data.z);
            data.group.getChildren().add(station);
        }

        data.spawnPoints.add(new double[]{data.x, data.z, floorY});
        data.lootPoints.add(new double[]{data.x, data.z - hd / 2 + 30, floorY}); // Medical supplies
    }

    private static void buildPoliceInterior(BuildingData data, int floorIndex, double floorY,
                                            double hw, double hd, Random rn) {
        // Front desk, cells, offices
        if (floorIndex == 0) {
            // Front desk
            Box desk = new Box(100, 60, 50);
            desk.setMaterial(new PhongMaterial(Color.rgb(120, 110, 100)));
            desk.setTranslateX(data.x);
            desk.setTranslateY(floorY + 30);
            desk.setTranslateZ(data.z - hd / 2 + 40);
            data.group.getChildren().add(desk);

            // Holding cells (back)
            for (int i = 0; i < 3; i++) {
                double cx = data.x - hw / 2 + 40 + i * 80;
                Box cell = new Box(60, 180, 40);
                cell.setMaterial(new PhongMaterial(Color.rgb(80, 80, 90)));
                cell.setTranslateX(cx);
                cell.setTranslateY(floorY + 90);
                cell.setTranslateZ(data.z + hd / 2 - 40);
                data.group.getChildren().add(cell);

                // Bars
                Box bars = new Box(56, 170, 4);
                bars.setMaterial(new PhongMaterial(Color.rgb(60, 60, 70)));
                bars.setTranslateX(cx);
                bars.setTranslateY(floorY + 85);
                bars.setTranslateZ(data.z + hd / 2 - 40);
                data.group.getChildren().add(bars);
            }
        }

        data.spawnPoints.add(new double[]{data.x, data.z + hd / 3, floorY});
        data.lootPoints.add(new double[]{data.x - hw / 2 + 30, data.z + hd / 2 - 30, floorY}); // Armory
    }

    private static void buildFireStationInterior(BuildingData data, int floorIndex, double floorY,
                                                 double hw, double hd, Random rn) {
        // Apparatus bay (front), dorms (back)
        if (floorIndex == 0) {
            // Large bay doors
            for (int i = 0; i < 3; i++) {
                double bx = data.x - hw / 2 + 60 + i * (hw - 120) / 2;
                Box door = new Box(80, 160, 12);
                door.setMaterial(new PhongMaterial(Color.rgb(180, 60, 60)));
                door.setTranslateX(bx);
                door.setTranslateY(floorY + 80);
                door.setTranslateZ(data.z - hd / 2 + 6);
                data.group.getChildren().add(door);
            }
        }

        data.spawnPoints.add(new double[]{data.x, data.z, floorY});
    }

    private static void buildSchoolInterior(BuildingData data, int floorIndex, double floorY,
                                            double hw, double hd, Random rn) {
        // Classrooms along corridor
        int roomsPerSide = 5;
        for (int side = -1; side <= 1; side += 2) {
            for (int i = 0; i < roomsPerSide; i++) {
                double rx = data.x + side * (hw / 2 - 50);
                double rz = data.z - hd / 2 + 40 + i * (hd - 80) / roomsPerSide;

                List<Opening> ops = new ArrayList<>();
                ops.add(new Opening(0, LARGE_DOORWAY_W, false, false));

                buildWallWithOpenings(data, floorY, rx + side * 50, rz, 100, 40,
                        ops, INT_WALL_MAT, true, rn);
            }
        }

        data.spawnPoints.add(new double[]{data.x, data.z, floorY});
    }

    private static void buildIndustrialInterior(BuildingData data, int floorIndex, double floorY,
                                                double hw, double hd, Random rn) {
        // Machinery, conveyor belts, storage
        for (int i = 0; i < 4; i++) {
            double mx = data.x - hw / 2 + 60 + i * (hw - 120) / 3;
            double mz = data.z + (rn.nextDouble() - 0.5) * hd * 0.6;

            Box machine = new Box(80, 140, 80);
            machine.setMaterial(new PhongMaterial(Color.rgb(100, 100, 110)));
            machine.setTranslateX(mx);
            machine.setTranslateY(floorY + 70);
            machine.setTranslateZ(mz);
            data.group.getChildren().add(machine);
        }

        data.spawnPoints.add(new double[]{data.x, data.z, floorY});
    }

    private static void buildMallAnchorInterior(BuildingData data, int floorIndex, double floorY,
                                                double hw, double hd, Random rn) {
        // Department store layout
        if (floorIndex == 0) {
            // Escalators
            for (int s = -1; s <= 1; s += 2) {
                Box esc = new Box(30, 60, 120);
                esc.setMaterial(new PhongMaterial(Color.rgb(150, 150, 160)));
                esc.setTranslateX(data.x + s * hw / 3);
                esc.setTranslateY(floorY + 30);
                esc.setTranslateZ(data.z);
                data.group.getChildren().add(esc);
            }
        }

        // Clothing racks, shelves
        for (int i = 0; i < 10; i++) {
            double rx = data.x + (rn.nextDouble() - 0.5) * hw * 0.8;
            double rz = data.z + (rn.nextDouble() - 0.5) * hd * 0.8;

            Box rack = new Box(120, 140, 40);
            rack.setMaterial(new PhongMaterial(Color.rgb(180, 170, 160)));
            rack.setTranslateX(rx);
            rack.setTranslateY(floorY + 70);
            rack.setTranslateZ(rz);
            data.group.getChildren().add(rack);
        }

        data.spawnPoints.add(new double[]{data.x, data.z, floorY});
        data.lootPoints.add(new double[]{data.x, data.z - hd / 3, floorY});
    }

    // ============================================================
    // Roof
    // ============================================================

    private static void buildRoof(BuildingData data, Random rn) {
        double w = data.type.width;
        double d = data.type.depth;
        double roofY = Constants.GROUND_Y + data.floors * LARGE_WALL_H;

        // Flat roof with parapet
        Box roof = new Box(w + LARGE_WALL_T * 2, 20, d + LARGE_WALL_T * 2);
        roof.setMaterial(ROOF_MAT);
        roof.setTranslateX(data.x);
        roof.setTranslateY(roofY + 10);
        roof.setTranslateZ(data.z);
        data.roofGroup.getChildren().add(roof);

        // Parapet walls
        Box para1 = new Box(w + LARGE_WALL_T * 2, 80, LARGE_WALL_T);
        para1.setMaterial(ROOF_MAT_DARK);
        para1.setTranslateX(data.x);
        para1.setTranslateY(roofY + 60);
        para1.setTranslateZ(data.z - d / 2 - LARGE_WALL_T / 2);
        data.roofGroup.getChildren().add(para1);

        Box para2 = new Box(w + LARGE_WALL_T * 2, 80, LARGE_WALL_T);
        para2.setMaterial(ROOF_MAT_DARK);
        para2.setTranslateX(data.x);
        para2.setTranslateY(roofY + 60);
        para2.setTranslateZ(data.z + d / 2 + LARGE_WALL_T / 2);
        data.roofGroup.getChildren().add(para2);

        Box para3 = new Box(LARGE_WALL_T, 80, d);
        para3.setMaterial(ROOF_MAT_DARK);
        para3.setTranslateX(data.x - w / 2 - LARGE_WALL_T / 2);
        para3.setTranslateY(roofY + 60);
        para3.setTranslateZ(data.z);
        data.roofGroup.getChildren().add(para3);

        Box para4 = new Box(LARGE_WALL_T, 80, d);
        para4.setMaterial(ROOF_MAT_DARK);
        para4.setTranslateX(data.x + w / 2 + LARGE_WALL_T / 2);
        para4.setTranslateY(roofY + 60);
        para4.setTranslateZ(data.z);
        data.roofGroup.getChildren().add(para4);

        // Roof access hatch
        Box hatch = new Box(60, 40, 60);
        hatch.setMaterial(new PhongMaterial(Color.rgb(80, 70, 60)));
        hatch.setTranslateX(data.x);
        hatch.setTranslateY(roofY + 20);
        hatch.setTranslateZ(data.z);
        data.roofGroup.getChildren().add(hatch);

        // HVAC units
        for (int i = 0; i < 2 + rn.nextInt(3); i++) {
            Box hvac = new Box(80, 60, 60);
            hvac.setMaterial(new PhongMaterial(Color.rgb(140, 140, 150)));
            hvac.setTranslateX(data.x + (rn.nextDouble() - 0.5) * w * 0.6);
            hvac.setTranslateY(roofY + 30);
            hvac.setTranslateZ(data.z + (rn.nextDouble() - 0.5) * d * 0.6);
            data.roofGroup.getChildren().add(hvac);
        }
    }

    // ============================================================
    // Exterior Details
    // ============================================================

    private static void addExteriorDetails(BuildingData data, Random rn) {
        // Downspouts, vents, signage based on type
        double h = data.floors * LARGE_WALL_H;
        double w = data.type.width;
        double d = data.type.depth;

        // Corner downspouts
        for (int cx = -1; cx <= 1; cx += 2) {
            for (int cz = -1; cz <= 1; cz += 2) {
                Box spout = new Box(8, h + 100, 8);
                spout.setMaterial(new PhongMaterial(Color.rgb(100, 90, 80)));
                spout.setTranslateX(data.x + cx * (w / 2 + LARGE_WALL_T / 2 + 4));
                spout.setTranslateY(Constants.GROUND_Y + h / 2 + 50);
                spout.setTranslateZ(data.z + cz * (d / 2 + LARGE_WALL_T / 2 + 4));
                data.group.getChildren().add(spout);
            }
        }

        // Type-specific details
        switch (data.type) {
            case HOSPITAL_WING -> {
                // Red cross sign
                Box cross = new Box(40, 40, 8);
                cross.setMaterial(new PhongMaterial(Color.rgb(200, 30, 30)));
                cross.setTranslateX(data.x);
                cross.setTranslateY(Constants.GROUND_Y + h + 30);
                cross.setTranslateZ(data.z - d / 2 - 20);
                cross.getTransforms().add(new Rotate(90, Rotate.X_AXIS));
                data.group.getChildren().add(cross);
            }
            case POLICE_STATION -> {
                // Blue light
                Box light = new Box(20, 20, 20);
                light.setMaterial(new PhongMaterial(Color.rgb(30, 80, 200)));
                light.setTranslateX(data.x);
                light.setTranslateY(Constants.GROUND_Y + h + 40);
                light.setTranslateZ(data.z - d / 2 - 15);
                data.group.getChildren().add(light);
            }
            case FIRE_STATION -> {
                // Hose tower
                Box tower = new Box(16, h + 200, 16);
                tower.setMaterial(new PhongMaterial(Color.rgb(180, 60, 60)));
                tower.setTranslateX(data.x + w / 2 + 30);
                tower.setTranslateY(Constants.GROUND_Y + h / 2 + 100);
                tower.setTranslateZ(data.z);
                data.group.getChildren().add(tower);
            }
        }
    }

    // ============================================================
    // Floor Materials
    // ============================================================

    private static PhongMaterial getFloorMaterial(Type type, int floorIndex) {
        return switch (type) {
            case RESIDENTIAL_LARGE, APARTMENT_BLOCK -> floorIndex == 0 ? FLOOR_WOOD : FLOOR_WOOD;
            case COMMERCIAL_OFFICE, RETAIL_STORE, MALL_ANCHOR -> FLOOR_TILE;
            case HOSPITAL_WING, POLICE_STATION, FIRE_STATION, SCHOOL_BLOCK -> FLOOR_TILE;
            case WAREHOUSE, INDUSTRIAL -> FLOOR_CONCRETE;
        };
    }

    // ============================================================
    // Lighting
    // ============================================================

    private static void addFloorLights(BuildingData data, int floorIndex, double floorY,
                                       double hw, double hd, Random rn) {
        // Ceiling lights per room/area
        int lightsPerFloor = switch (data.type) {
            case RESIDENTIAL_LARGE, APARTMENT_BLOCK -> 4;
            case COMMERCIAL_OFFICE -> 8;
            case RETAIL_STORE, MALL_ANCHOR -> 6;
            case WAREHOUSE, INDUSTRIAL -> 4;
            case HOSPITAL_WING, POLICE_STATION, FIRE_STATION, SCHOOL_BLOCK -> 6;
        };

        PhongMaterial lightMat = new PhongMaterial(Color.rgb(255, 240, 220));
        lightMat.setSelfIlluminationMap(null);

        for (int i = 0; i < lightsPerFloor; i++) {
            double lx = data.x + (rn.nextDouble() - 0.5) * hw * 1.5;
            double lz = data.z + (rn.nextDouble() - 0.5) * hd * 1.5;

            Sphere light = new Sphere(4, 12);
            light.setMaterial(lightMat);
            light.setTranslateX(lx);
            light.setTranslateY(floorY + LARGE_WALL_H - 10);
            light.setTranslateZ(lz);
            data.group.getChildren().add(light);
        }
    }

    // ============================================================
    // Scale Validation
    // ============================================================

    private static void validateScale(Type type, int floors) {
        // Validate door clearance
        if (LARGE_DOOR_H < Dims.PLAYER_HEIGHT + 14) {
            throw new IllegalStateException("Door height " + LARGE_DOOR_H +
                    " too low for player height " + Dims.PLAYER_HEIGHT);
        }
        if (LARGE_DOOR_W < 64) {
            throw new IllegalStateException("Door width " + LARGE_DOOR_W + " too narrow");
        }
        if (LARGE_WALL_H < Dims.PLAYER_HEIGHT + 50) {
            throw new IllegalStateException("Ceiling height " + LARGE_WALL_H + " too low");
        }
        if (LARGE_DOORWAY_H < Dims.PLAYER_HEIGHT + 10) {
            throw new IllegalStateException("Interior doorway height " + LARGE_DOORWAY_H + " too low");
        }
        if (LARGE_DOORWAY_W < 60) {
            throw new IllegalStateException("Interior doorway width " + LARGE_DOORWAY_W + " too narrow");
        }

        // Validate floor height allows player to stand
        double floorToCeiling = LARGE_WALL_H;
        if (floorToCeiling < Dims.PLAYER_HEIGHT + 30) {
            throw new IllegalStateException("Floor-to-ceiling " + floorToCeiling + " insufficient");
        }
    }

    // ============================================================
    // Integration Helper
    // ============================================================

    /**
     * Convert BuildingData to the existing Building class for compatibility.
     */
    public static Building toBuilding(BuildingData data) {
        Building b = new Building(data.x, data.z, data.type.width, data.type.depth,
                data.floors * LARGE_WALL_H, data.front);
        b.group.getChildren().addAll(data.group);
        b.roof.getChildren().addAll(data.roofGroup);
        b.id = "large_" + data.type.name().toLowerCase() + "_" + data.x + "_" + data.z;

        // Transfer spawn/loot points
        for (double[] sp : data.spawnPoints) {
            b.spawn.add(new double[]{sp[0], sp[1]});
        }

        return b;
    }
}