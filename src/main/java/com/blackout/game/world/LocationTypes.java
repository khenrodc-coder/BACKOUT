package com.blackout.game.world;

import com.blackout.game.core.Constants;
import com.blackout.game.environment.Building;
import com.blackout.game.environment.Car;
import com.blackout.game.environment.TreeFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Extended location types for open world expansion (Phase 3).
 * Defines new POI types beyond the downtown district: residential neighborhoods,
 * hospital complex, police station, military checkpoint, industrial zone,
 * suburban sprawl, highway rest stop, and more.
 */
public final class LocationTypes {

    private LocationTypes() {}

    // ============================================================
    // Location Kind Enum - Extended POI Types
    // ============================================================

    public enum Kind {
        // Downtown district (existing)
        OFFICE, APARTMENT, SHOP, SUPERMARKET, GAS_SHOP, CANOPY, PARKING, PLAZA, PARK,

        // Residential neighborhoods
        SUBURBAN_HOUSE, DUPLEX, ROWHOUSE, CUL_DE_SAC, TRAILER_PARK, GATED_COMMUNITY,

        // Commercial / industrial
        STRIP_MALL, WAREHOUSE, FACTORY, JUNKYARD, REFINERY, POWER_PLANT, SUBSTATION,

        // Civic / institutional
        HOSPITAL, CLINIC, POLICE_STATION, FIRE_STATION, SCHOOL, LIBRARY, CITY_HALL, COURTHOUSE,

        // Military / government
        MILITARY_CHECKPOINT, MILITARY_BASE, BUNKER, GUARD_TOWER, RADAR_STATION, MISSILE_SILO,

        // Transportation
        HIGHWAY_REST_STOP, TRUCK_STOP, TRAIN_STATION, SUBWAY_ENTRANCE, AIRPORT_HANGAR, HELIPAD,
        RAILWAY_DEPOT, DINER, MOTEL,

        // Outdoor / wilderness
        CAMPGROUND, HUNTING_LODGE, RANGER_STATION, LOOKOUT_TOWER, CAVE_ENTRANCE, MINE_SHAFT,

        // Special / unique
        MALL, CASINO, STADIUM, ZOO, PRISON, ASYLUM, SEWER_ACCESS, UNDERGROUND_BUNKER, HOTEL,

        // Environmental
        LAKE_DOCK, RIVER_BRIDGE, DAM, WATER_TOWER, WINDFARM, SOLAR_FARM, CELL_TOWER
    }

    // ============================================================
    // Location Data Structures
    // ============================================================

    public static final class Location {
        public final Kind kind;
        public final String name;
        public final double centerX, centerZ;
        public final double radius;           // approximate radius
        public final int chunkX, chunkZ;      // chunk coordinates
        public final long seed;               // deterministic seed for this location
        public final int tier;                // 1 = common, 2 = uncommon, 3 = rare, 4 = unique
        public final String[] tags;           // loot tags, danger level, etc.

        public Location(Kind kind, String name, double centerX, double centerZ,
                        double radius, int chunkX, int chunkZ, long seed, int tier, String[] tags) {
            this.kind = kind;
            this.name = name;
            this.centerX = centerX;
            this.centerZ = centerZ;
            this.radius = radius;
            this.chunkX = chunkX;
            this.chunkZ = chunkZ;
            this.seed = seed;
            this.tier = tier;
            this.tags = tags;
        }
    }

    public static final class Layout {
        public final List<Building> buildings = new ArrayList<>();
        public final List<Car> vehicles = new ArrayList<>();
        public final List<TreeFactory.Tree> trees = new ArrayList<>();
        public final List<LootSpawn> lootSpawns = new ArrayList<>();
        public final List<EnemySpawn> enemySpawns = new ArrayList<>();
        public final List<LightSource> lights = new ArrayList<>();
        public final List<Prop> props = new ArrayList<>();
        public final double[] bounds = new double[4]; // x0, z0, x1, z1

        public void computeBounds() {
            if (buildings.isEmpty()) return;
            double x0 = Double.MAX_VALUE, z0 = Double.MAX_VALUE;
            double x1 = -Double.MAX_VALUE, z1 = -Double.MAX_VALUE;
            for (Building b : buildings) {
                x0 = Math.min(x0, b.x - b.w / 2);
                x1 = Math.max(x1, b.x + b.w / 2);
                z0 = Math.min(z0, b.z - b.d / 2);
                z1 = Math.max(z1, b.z + b.d / 2);
            }
            bounds[0] = x0; bounds[1] = z0; bounds[2] = x1; bounds[3] = z1;
        }
    }

    public static final class LootSpawn {
        public final double x, y, z;
        public final String[] lootTables;
        public final int minCount, maxCount;
        public final double respawnHours;

        public LootSpawn(double x, double y, double z, String[] lootTables,
                         int minCount, int maxCount, double respawnHours) {
            this.x = x; this.y = y; this.z = z;
            this.lootTables = lootTables;
            this.minCount = minCount; this.maxCount = maxCount;
            this.respawnHours = respawnHours;
        }
    }

    public static final class EnemySpawn {
        public final double x, z;
        public final int[] enemyTypes;    // indices from Constants
        public final int minCount, maxCount;
        public final double patrolRadius;
        public final boolean isPatrol;    // true = wandering patrol, false = static guard
        public final String spawnCondition; // "night", "bloodmoon", "always", "triggered"

        public EnemySpawn(double x, double z, int[] enemyTypes,
                          int minCount, int maxCount, double patrolRadius,
                          boolean isPatrol, String spawnCondition) {
            this.x = x; this.z = z;
            this.enemyTypes = enemyTypes;
            this.minCount = minCount; this.maxCount = maxCount;
            this.patrolRadius = patrolRadius;
            this.isPatrol = isPatrol;
            this.spawnCondition = spawnCondition;
        }
    }

    public static final class LightSource {
        public final double x, y, z;
        public final double radius, intensity;
        public final int color;           // 0=warm, 1=cold, 2=red, 3=green, 4=blue
        public final boolean flicker;
        public final boolean powered;     // requires power grid

        public LightSource(double x, double y, double z, double radius, double intensity,
                           int color, boolean flicker, boolean powered) {
            this.x = x; this.y = y; this.z = z;
            this.radius = radius; this.intensity = intensity;
            this.color = color; this.flicker = flicker; this.powered = powered;
        }
    }

    public static final class Prop {
        public final double x, z, yaw, scale;
        public final int variant;
        public final String modelType;    // "bench", "trashcan", "hydrant", "dumpster", "generator", etc.

        public Prop(double x, double z, double yaw, double scale, int variant, String modelType) {
            this.x = x; this.z = z; this.yaw = yaw; this.scale = scale;
            this.variant = variant; this.modelType = modelType;
        }
    }

    // ============================================================
    // Location Generation Registry
    // ============================================================

    public interface Generator {
        Layout generate(Kind kind, double cx, double cz, double size, Random rn);
        String getName();
        int getTier();
        String[] getTags();
    }

    // ============================================================
    // Built-in Generators for Each Location Type
    // ============================================================

    private static final class ResidentialGenerator implements Generator {
        @Override
        public Layout generate(Kind kind, double cx, double cz, double size, Random rn) {
            Layout layout = new Layout();
            double half = size / 2;

            switch (kind) {
                case SUBURBAN_HOUSE -> {
                    // Single family home with yard
                    int style = rn.nextInt(4);
                    Building house = new Building(cx, cz,
                            30 + rn.nextInt(20), 30 + rn.nextInt(20), 8 + rn.nextInt(6), rn.nextInt(4) * 90);
                    house.id = "house_" + cx + "_" + cz;
                    layout.buildings.add(house);

                    // Garage
                    if (rn.nextDouble() < 0.7) {
                        Building garage = new Building(cx + (rn.nextDouble() - 0.5) * 20, cz + 25,
                                12, 12, 4, 0);
                        garage.id = "garage_" + cx + "_" + cz;
                        layout.buildings.add(garage);
                    }

                    // Yard props
                    for (int i = 0; i < 3 + rn.nextInt(4); i++) {
                        layout.props.add(new Prop(
                                cx + (rn.nextDouble() - 0.5) * 40,
                                cz + (rn.nextDouble() - 0.5) * 40,
                                rn.nextDouble() * 360, 1.0, rn.nextInt(3),
                                rn.nextDouble() < 0.5 ? "mailbox" : "trashcan"));
                    }
                }
                case ROWHOUSE -> {
                    // Row of connected townhouses
                    int units = 4 + rn.nextInt(3);
                    double unitW = 16, unitD = 28;
                    double startX = cx - (units * unitW) / 2 + unitW / 2;
                    for (int i = 0; i < units; i++) {
                        Building unit = new Building(startX + i * unitW, cz, unitW, unitD, 10 + rn.nextInt(4), 0);
                        unit.id = "rowhouse_" + cx + "_" + cz + "_" + i;
                        layout.buildings.add(unit);
                    }
                    // Backyard fences/props
                    for (int i = 0; i < units; i++) {
                        layout.props.add(new Prop(
                                startX + i * unitW, cz + unitD / 2 + 5,
                                0, 1.0, 0, "fence"));
                    }
                }
                case CUL_DE_SAC -> {
                    // Circular cul-de-sac with houses around
                    int houses = 6 + rn.nextInt(4);
                    double circleR = 50;
                    for (int i = 0; i < houses; i++) {
                        double angle = (i / (double) houses) * Math.PI * 2;
                        double hx = cx + Math.cos(angle) * circleR;
                        double hz = cz + Math.sin(angle) * circleR;
                        Building house = new Building(hx, hz, 28, 28, 8, (int) Math.toDegrees(angle) + 90);
                        house.id = "culdesac_" + cx + "_" + cz + "_" + i;
                        layout.buildings.add(house);
                    }
                    // Center island
                    layout.props.add(new Prop(cx, cz, 0, 2.0, 0, "tree"));
                }
                case GATED_COMMUNITY -> {
                    // Fenced area with multiple houses
                    int houses = 8 + rn.nextInt(6);
                    for (int i = 0; i < houses; i++) {
                        double hx = cx + (rn.nextDouble() - 0.5) * size * 0.8;
                        double hz = cz + (rn.nextDouble() - 0.5) * size * 0.8;
                        Building house = new Building(hx, hz, 35 + rn.nextInt(15), 35 + rn.nextInt(15),
                                10 + rn.nextInt(6), rn.nextInt(4) * 90);
                        house.id = "gated_" + cx + "_" + cz + "_" + i;
                        layout.buildings.add(house);
                    }
                    // Gate house
                    Building gate = new Building(cx - size * 0.4, cz, 10, 10, 5, 90);
                    gate.id = "gatehouse_" + cx + "_" + cz;
                    layout.buildings.add(gate);
                }
                case TRAILER_PARK -> {
                    // Mobile homes in rows
                    int rows = 3, cols = 5;
                    for (int r = 0; r < rows; r++) {
                        for (int c = 0; c < cols; c++) {
                            double tx = cx - size * 0.4 + c * 18 + 9;
                            double tz = cz - size * 0.3 + r * 22 + 11;
                            Building trailer = new Building(tx, tz, 14, 24, 4, 0);
                            trailer.id = "trailer_" + cx + "_" + cz + "_" + r + "_" + c;
                            layout.buildings.add(trailer);
                        }
                    }
                }
            }
            layout.computeBounds();
            return layout;
        }

        @Override public String getName() { return "Residential"; }
        @Override public int getTier() { return 1; }
        @Override public String[] getTags() { return new String[]{"loot_residential", "zombies_low"}; }
    }

    private static final class CommercialGenerator implements Generator {
        @Override
        public Layout generate(Kind kind, double cx, double cz, double size, Random rn) {
            Layout layout = new Layout();

            switch (kind) {
                case STRIP_MALL -> {
                    // Long building with multiple storefronts
                    int stores = 5 + rn.nextInt(4);
                    double totalW = stores * 24;
                    double startX = cx - totalW / 2 + 12;
                    for (int i = 0; i < stores; i++) {
                        Building store = new Building(startX + i * 24, cz, 22, 50, 6, 90);
                        store.id = "strip_" + cx + "_" + cz + "_" + i;
                        layout.buildings.add(store);
                    }
                    // Parking lot
                    layout.props.add(new Prop(cx, cz + 40, 0, 10.0, 0, "parking_lines"));
                }
                case WAREHOUSE -> {
                    // Large industrial building
                    Building wh = new Building(cx, cz, 80 + rn.nextInt(40), 60 + rn.nextInt(30), 14, 0);
                    wh.id = "warehouse_" + cx + "_" + cz;
                    layout.buildings.add(wh);

                    // Loading dock
                    Building dock = new Building(cx, cz + wh.d / 2 + 8, 20, 16, 6, 0);
                    dock.id = "dock_" + cx + "_" + cz;
                    layout.buildings.add(dock);

                    // Office section
                    Building office = new Building(cx + wh.w / 2 + 10, cz - wh.d / 2 - 10, 20, 20, 8, 0);
                    office.id = "wh_office_" + cx + "_" + cz;
                    layout.buildings.add(office);
                }
                case FACTORY -> {
                    // Multiple connected buildings with smokestacks
                    int blocks = 3 + rn.nextInt(2);
                    for (int i = 0; i < blocks; i++) {
                        double bx = cx + (i - 1) * 50;
                        Building b = new Building(bx, cz, 50, 40, 12 + rn.nextInt(6), 0);
                        b.id = "factory_" + cx + "_" + cz + "_" + i;
                        layout.buildings.add(b);
                    }
                    // Smokestacks
                    for (int i = 0; i < 2 + rn.nextInt(2); i++) {
                        layout.props.add(new Prop(
                                cx + (rn.nextDouble() - 0.5) * 60, cz + (rn.nextDouble() - 0.5) * 40,
                                0, 0.5, 0, "smokestack"));
                    }
                }
                case JUNKYARD -> {
                    // Piles of cars and scrap
                    for (int i = 0; i < 20 + rn.nextInt(15); i++) {
                        Car car = createCar(
                                cx + (rn.nextDouble() - 0.5) * size * 0.9,
                                cz + (rn.nextDouble() - 0.5) * size * 0.9,
                                rn.nextDouble() * 360, rn.nextInt(4), rn.nextInt(6), true);
                        layout.vehicles.add(car);
                    }
                    // Small office
                    Building office = new Building(cx + size * 0.3, cz - size * 0.3, 12, 12, 4, 0);
                    office.id = "junk_office_" + cx + "_" + cz;
                    layout.buildings.add(office);
                }
                case REFINERY -> {
                    // Complex of tanks and pipes
                    int tanks = 4 + rn.nextInt(4);
                    for (int i = 0; i < tanks; i++) {
                        double angle = (i / (double) tanks) * Math.PI * 2;
                        double tx = cx + Math.cos(angle) * 40;
                        double tz = cz + Math.sin(angle) * 40;
                        Building tank = new Building(tx, tz, 16, 16, 30 + rn.nextInt(20), 0);
                        tank.id = "tank_" + cx + "_" + cz + "_" + i;
                        layout.buildings.add(tank);
                    }
                    // Control building
                    Building ctrl = new Building(cx, cz, 20, 20, 8, 0);
                    ctrl.id = "refinery_ctrl_" + cx + "_" + cz;
                    layout.buildings.add(ctrl);
                }
            }
            layout.computeBounds();
            return layout;
        }

        @Override public String getName() { return "Commercial/Industrial"; }
        @Override public int getTier() { return 2; }
        @Override public String[] getTags() { return new String[]{"loot_industrial", "zombies_medium", "hazmat_risk"}; }
    }

    private static final class CivicGenerator implements Generator {
        @Override
        public Layout generate(Kind kind, double cx, double cz, double size, Random rn) {
            Layout layout = new Layout();

            switch (kind) {
                case HOSPITAL -> {
                    // Main building
                    Building main = new Building(cx, cz, 100, 80, 16, 0);
                    main.id = "hospital_main_" + cx + "_" + cz;
                    layout.buildings.add(main);

                    // ER entrance
                    Building er = new Building(cx, cz + 50, 40, 30, 8, 90);
                    er.id = "hospital_er_" + cx + "_" + cz;
                    layout.buildings.add(er);

                    // Helipad on roof
                    layout.props.add(new Prop(cx, cz, 0, 5.0, 0, "helipad"));

                    // Parking
                    layout.props.add(new Prop(cx - 60, cz, 0, 12.0, 0, "parking_lines"));

                    // Ambulances
                    for (int i = 0; i < 3; i++) {
                        layout.vehicles.add(createCar(cx + 50 + i * 8, cz + 55, 90, 2, 0, false));
                    }
                }
                case POLICE_STATION -> {
                    Building station = new Building(cx, cz, 60, 50, 10, 0);
                    station.id = "police_" + cx + "_" + cz;
                    layout.buildings.add(station);

                    // Impound / parking
                    for (int i = 0; i < 6; i++) {
                        layout.vehicles.add(createCar(cx - 25 + i * 10, cz - 30, 90, 0, 4, false));
                    }

                    // Guard tower
                    Building tower = new Building(cx + 30, cz + 25, 8, 8, 12, 0);
                    tower.id = "police_tower_" + cx + "_" + cz;
                    layout.buildings.add(tower);
                }
                case FIRE_STATION -> {
                    Building station = new Building(cx, cz, 50, 40, 10, 0);
                    station.id = "fire_" + cx + "_" + cz;
                    layout.buildings.add(station);

                    // Garage doors face street
                    for (int i = 0; i < 4; i++) {
                        layout.vehicles.add(createCar(cx - 15 + i * 10, cz + 25, 90, 3, 2, false));
                    }

                    // Hose tower
                    Building tower = new Building(cx + 25, cz - 20, 8, 8, 20, 0);
                    tower.id = "fire_tower_" + cx + "_" + cz;
                    layout.buildings.add(tower);
                }
                case SCHOOL -> {
                    Building main = new Building(cx, cz, 80, 60, 10, 0);
                    main.id = "school_" + cx + "_" + cz;
                    layout.buildings.add(main);

                    // Gymnasium
                    Building gym = new Building(cx + 50, cz, 40, 40, 10, 0);
                    gym.id = "gym_" + cx + "_" + cz;
                    layout.buildings.add(gym);

                    // Playground
                    layout.props.add(new Prop(cx - 50, cz, 0, 8.0, 0, "playground"));

                    // Buses
                    for (int i = 0; i < 4; i++) {
                        layout.vehicles.add(createCar(cx - 40 + i * 12, cz - 35, 90, 4, 5, false));
                    }
                }
                case LIBRARY -> {
                    Building lib = new Building(cx, cz, 50, 40, 12, 0);
                    lib.id = "library_" + cx + "_" + cz;
                    layout.buildings.add(lib);

                    // Reading garden
                    layout.props.add(new Prop(cx, cz - 30, 0, 6.0, 0, "garden"));
                }
            }
            layout.computeBounds();
            return layout;
        }

        @Override public String getName() { return "Civic/Institutional"; }
        @Override public int getTier() { return 2; }
        @Override public String[] getTags() { return new String[]{"loot_medical", "loot_police", "loot_books", "zombies_high"}; }
    }

    private static final class MilitaryGenerator implements Generator {
        @Override
        public Layout generate(Kind kind, double cx, double cz, double size, Random rn) {
            Layout layout = new Layout();

            switch (kind) {
                case MILITARY_CHECKPOINT -> {
                    // Guard booths
                    Building booth1 = new Building(cx - 15, cz, 6, 6, 4, 0);
                    booth1.id = "checkpoint_booth1_" + cx + "_" + cz;
                    layout.buildings.add(booth1);

                    Building booth2 = new Building(cx + 15, cz, 6, 6, 4, 0);
                    booth2.id = "checkpoint_booth2_" + cx + "_" + cz;
                    layout.buildings.add(booth2);

                    // Barrier
                    layout.props.add(new Prop(cx, cz, 0, 1.0, 0, "barrier"));

                    // Sandbag positions
                    for (int i = 0; i < 6; i++) {
                        double angle = (i / 6.0) * Math.PI * 2;
                        layout.props.add(new Prop(cx + Math.cos(angle) * 12, cz + Math.sin(angle) * 12,
                                (float) Math.toDegrees(angle) + 90, 1.0, 0, "sandbags"));
                    }

                    // Military vehicles
                    layout.vehicles.add(createCar(cx - 25, cz, 0, 5, 0, false));
                    layout.vehicles.add(createCar(cx + 25, cz, 180, 5, 0, false));
                }
                case MILITARY_BASE -> {
                    // Perimeter fence posts
                    for (int i = 0; i < 20; i++) {
                        double angle = (i / 20.0) * Math.PI * 2;
                        layout.props.add(new Prop(cx + Math.cos(angle) * 100, cz + Math.sin(angle) * 100,
                                (float) Math.toDegrees(angle) + 90, 1.0, 0, "fence_post"));
                    }

                    // Barracks
                    for (int i = 0; i < 4; i++) {
                        Building bar = new Building(cx - 60 + i * 40, cz - 60, 30, 80, 8, 0);
                        bar.id = "barracks_" + cx + "_" + cz + "_" + i;
                        layout.buildings.add(bar);
                    }

                    // Hangar
                    Building hangar = new Building(cx, cz + 50, 80, 60, 15, 0);
                    hangar.id = "hangar_" + cx + "_" + cz;
                    layout.buildings.add(hangar);

                    // Admin
                    Building admin = new Building(cx - 80, cz + 80, 40, 30, 8, 0);
                    admin.id = "admin_" + cx + "_" + cz;
                    layout.buildings.add(admin);

                    // Vehicles
                    for (int i = 0; i < 8; i++) {
                        layout.vehicles.add(createCar(cx + (rn.nextDouble() - 0.5) * 120, cz + (rn.nextDouble() - 0.5) * 120,
                                rn.nextDouble() * 360, 5, 0, false));
                    }
                }
                case BUNKER -> {
                    // Heavy blast doors
                    Building entrance = new Building(cx, cz, 20, 15, 8, 0);
                    entrance.id = "bunker_entrance_" + cx + "_" + cz;
                    layout.buildings.add(entrance);

                    // Ventilation shafts
                    for (int i = 0; i < 4; i++) {
                        layout.props.add(new Prop(cx + (i - 1.5) * 8, cz - 20, 0, 0.5, 0, "vent_shaft"));
                    }

                    // Antenna
                    layout.props.add(new Prop(cx, cz + 20, 0, 2.0, 0, "antenna"));
                }
                case GUARD_TOWER -> {
                    Building tower = new Building(cx, cz, 8, 8, 25, 0);
                    tower.id = "guard_tower_" + cx + "_" + cz;
                    layout.buildings.add(tower);

                    // Spotlight
                    layout.lights.add(new LightSource(cx, 25, cz, 50, 2.0, 1, true, true));
                }
            }
            layout.computeBounds();
            return layout;
        }

        @Override public String getName() { return "Military/Government"; }
        @Override public int getTier() { return 3; }
        @Override public String[] getTags() { return new String[]{"loot_military", "loot_weapons", "zombies_high", "guards_present"}; }
    }

    private static final class TransportGenerator implements Generator {
        @Override
        public Layout generate(Kind kind, double cx, double cz, double size, Random rn) {
            Layout layout = new Layout();

            switch (kind) {
                case HIGHWAY_REST_STOP -> {
                    Building building = new Building(cx, cz, 50, 40, 6, 0);
                    building.id = "reststop_" + cx + "_" + cz;
                    layout.buildings.add(building);

                    // Gas pumps
                    layout.props.add(new Prop(cx - 20, cz + 30, 90, 1.5, 0, "gas_pump"));
                    layout.props.add(new Prop(cx + 20, cz + 30, 90, 1.5, 0, "gas_pump"));

                    // Vending machines
                    layout.props.add(new Prop(cx, cz - 10, 0, 1.0, 0, "vending"));

                    // Parked trucks
                    for (int i = 0; i < 5 + rn.nextInt(5); i++) {
                        layout.vehicles.add(createCar(cx + (rn.nextDouble() - 0.5) * 60, cz + 40 + rn.nextDouble() * 40,
                                rn.nextBoolean() ? 90 : 270, 1, rn.nextInt(6), rn.nextDouble() < 0.2));
                    }
                }
                case TRUCK_STOP -> {
                    Building main = new Building(cx, cz, 60, 50, 8, 0);
                    main.id = "truckstop_" + cx + "_" + cz;
                    layout.buildings.add(main);

                    // Large parking
                    layout.props.add(new Prop(cx, cz + 50, 0, 20.0, 0, "parking_lines"));

                    // Showers/laundry
                    Building svc = new Building(cx + 40, cz - 30, 20, 20, 5, 0);
                    svc.id = "services_" + cx + "_" + cz;
                    layout.buildings.add(svc);

                    // Many trucks
                    for (int i = 0; i < 15 + rn.nextInt(10); i++) {
                        layout.vehicles.add(createCar(cx + (rn.nextDouble() - 0.5) * 100, cz + (rn.nextDouble() - 0.5) * 100,
                                rn.nextDouble() * 360, 1, rn.nextInt(6), rn.nextDouble() < 0.1));
                    }
                }
                case TRAIN_STATION -> {
                    Building station = new Building(cx, cz, 80, 30, 12, 0);
                    station.id = "train_station_" + cx + "_" + cz;
                    layout.buildings.add(station);

                    // Platform
                    layout.props.add(new Prop(cx, cz + 20, 0, 15.0, 0, "platform"));

                    // Tracks (visual)
                    layout.props.add(new Prop(cx, cz + 25, 0, 1.0, 0, "tracks"));

                    // Train cars (wrecked)
                    for (int i = 0; i < 5; i++) {
                        layout.vehicles.add(createCar(cx - 60 + i * 30, cz + 25, 90, 6, 0, true));
                    }
                }
                case HELIPAD -> {
                    Building control = new Building(cx, cz - 20, 12, 12, 5, 0);
                    control.id = "helipad_ctrl_" + cx + "_" + cz;
                    layout.buildings.add(control);

                    // Landing pad
                    layout.props.add(new Prop(cx, cz, 0, 4.0, 0, "helipad"));

                    // Helicopter (maybe)
                    if (rn.nextDouble() < 0.3) {
                        layout.vehicles.add(createCar(cx, cz, rn.nextDouble() * 360, 7, 0, rn.nextDouble() < 0.5));
                    }

                    // Fuel tanks
                    for (int i = 0; i < 2; i++) {
                        Building tank = new Building(cx + (i == 0 ? -15 : 15), cz + 20, 8, 8, 10, 0);
                        tank.id = "heli_fuel_" + cx + "_" + cz + "_" + i;
                        layout.buildings.add(tank);
                    }
                }
                case RAILWAY_DEPOT -> {
                    Building depot = new Building(cx, cz, 60, 40, 10, 0);
                    depot.id = "rail_depot_" + cx + "_" + cz;
                    layout.buildings.add(depot);

                    // Tracks
                    layout.props.add(new Prop(cx, cz + 25, 0, 20.0, 0, "tracks"));

                    // Warehouse
                    Building wh = new Building(cx + 40, cz, 30, 30, 8, 0);
                    wh.id = "rail_wh_" + cx + "_" + cz;
                    layout.buildings.add(wh);
                }
                case DINER -> {
                    Building diner = new Building(cx, cz, 30, 25, 6, 0);
                    diner.id = "diner_" + cx + "_" + cz;
                    layout.buildings.add(diner);

                    // Parking
                    layout.props.add(new Prop(cx, cz + 20, 0, 6.0, 0, "parking_lines"));

                    // Sign
                    layout.props.add(new Prop(cx, cz - 15, 0, 3.0, 0, "neon_sign"));
                }
                case MOTEL -> {
                    Building office = new Building(cx - 20, cz - 20, 16, 12, 4, 0);
                    office.id = "motel_office_" + cx + "_" + cz;
                    layout.buildings.add(office);

                    // Room blocks
                    for (int i = 0; i < 2; i++) {
                        Building block = new Building(cx + (i == 0 ? -30 : 30), cz + 10, 40, 20, 6, 0);
                        block.id = "motel_block_" + cx + "_" + cz + "_" + i;
                        layout.buildings.add(block);
                    }

                    // Pool
                    layout.props.add(new Prop(cx, cz - 10, 0, 4.0, 0, "pool"));

                    // Vending/ice
                    layout.props.add(new Prop(cx - 20, cz - 20, 0, 1.0, 0, "vending"));
                    layout.props.add(new Prop(cx - 15, cz - 20, 0, 1.0, 0, "ice_machine"));
                }
            }
            layout.computeBounds();
            return layout;
        }

        @Override public String getName() { return "Transportation"; }
        @Override public int getTier() { return 2; }
        @Override public String[] getTags() { return new String[]{"loot_travel", "vehicles", "zombies_medium"}; }
    }

    private static final class WildernessGenerator implements Generator {
        @Override
        public Layout generate(Kind kind, double cx, double cz, double size, Random rn) {
            Layout layout = new Layout();

            switch (kind) {
                case CAMPGROUND -> {
                    // Campsites in a circle
                    int sites = 8 + rn.nextInt(6);
                    for (int i = 0; i < sites; i++) {
                        double angle = (i / (double) sites) * Math.PI * 2;
                        double sx = cx + Math.cos(angle) * 30;
                        double sz = cz + Math.sin(angle) * 30;
                        layout.props.add(new Prop(sx, sz, angle + Math.PI / 2, 1.0, 0, "tent"));
                        layout.props.add(new Prop(sx + Math.cos(angle) * 3, sz + Math.sin(angle) * 3,
                                angle + Math.PI / 2, 1.0, 0, "campfire"));
                    }

                    // Ranger station
                    Building ranger = new Building(cx, cz - 40, 16, 12, 5, 0);
                    ranger.id = "ranger_" + cx + "_" + cz;
                    layout.buildings.add(ranger);

                    // Outhouses
                    for (int i = 0; i < 3; i++) {
                        double angle = (i / 3.0) * Math.PI * 2;
                        Building out = new Building(cx + Math.cos(angle) * 45, cz + Math.sin(angle) * 45, 4, 4, 3, 0);
                        out.id = "outhouse_" + cx + "_" + cz + "_" + i;
                        layout.buildings.add(out);
                    }
                }
                case HUNTING_LODGE -> {
                    Building lodge = new Building(cx, cz, 30, 25, 10, 0);
                    lodge.id = "lodge_" + cx + "_" + cz;
                    layout.buildings.add(lodge);

                    // Trophy room (special loot)
                    layout.lootSpawns.add(new LootSpawn(cx, Constants.GROUND_Y + 2, cz,
                            new String[]{"hunting_trophies", "firearms"}, 1, 3, 168));

                    // Outbuildings
                    Building shed = new Building(cx + 20, cz + 15, 10, 10, 4, 0);
                    shed.id = "shed_" + cx + "_" + cz;
                    layout.buildings.add(shed);
                }
                case RANGER_STATION -> {
                    Building station = new Building(cx, cz, 20, 20, 8, 0);
                    station.id = "ranger_station_" + cx + "_" + cz;
                    layout.buildings.add(station);

                    // Tower
                    Building tower = new Building(cx, cz - 15, 6, 6, 20, 0);
                    tower.id = "lookout_" + cx + "_" + cz;
                    layout.buildings.add(tower);

                    // Radio antenna
                    layout.props.add(new Prop(cx, cz - 15, 0, 2.0, 0, "antenna"));

                    // Vehicle
                    layout.vehicles.add(createCar(cx + 15, cz, 0, 1, 3, false));
                }
                case LOOKOUT_TOWER -> {
                    Building tower = new Building(cx, cz, 8, 8, 30, 0);
                    tower.id = "fire_lookout_" + cx + "_" + cz;
                    layout.buildings.add(tower);

                    // Cab at top
                    Building cab = new Building(cx, cz, 6, 6, 4, 0);
                    cab.id = "lookout_cab_" + cx + "_" + cz;
                    layout.buildings.add(cab);

                    // Radio
                    layout.lights.add(new LightSource(cx, 32, cz, 5, 1.0, 2, true, true));
                }
                case CAVE_ENTRANCE -> {
                    // Cave mouth
                    layout.props.add(new Prop(cx, cz, 0, 5.0, 0, "cave_mouth"));

                    // Mining equipment
                    Building shed = new Building(cx + 10, cz + 10, 8, 8, 4, 0);
                    shed.id = "mine_shed_" + cx + "_" + cz;
                    layout.buildings.add(shed);

                    // Mine carts
                    for (int i = 0; i < 3; i++) {
                        layout.props.add(new Prop(cx + 12 + i * 3, cz + 15, 90, 1.0, 0, "mine_cart"));
                    }
                }
            }
            layout.computeBounds();
            return layout;
        }

        @Override public String getName() { return "Wilderness/Outdoor"; }
        @Override public int getTier() { return 2; }
        @Override public String[] getTags() { return new String[]{"loot_survival", "loot_outdoor", "animals", "zombies_low"}; }
    }

    private static final class SpecialGenerator implements Generator {
        @Override
        public Layout generate(Kind kind, double cx, double cz, double size, Random rn) {
            Layout layout = new Layout();

            switch (kind) {
                case MALL -> {
                    // Large enclosed mall
                    Building main = new Building(cx, cz, 150, 120, 14, 0);
                    main.id = "mall_" + cx + "_" + cz;
                    layout.buildings.add(main);

                    // Anchor stores
                    Building anchor1 = new Building(cx - 85, cz, 50, 60, 10, 0);
                    anchor1.id = "mall_anchor1_" + cx + "_" + cz;
                    layout.buildings.add(anchor1);

                    Building anchor2 = new Building(cx + 85, cz, 50, 60, 10, 0);
                    anchor2.id = "mall_anchor2_" + cx + "_" + cz;
                    layout.buildings.add(anchor2);

                    // Massive parking
                    layout.props.add(new Prop(cx, cz + 90, 0, 25.0, 0, "parking_lines"));

                    // Fountain in center
                    layout.props.add(new Prop(cx, cz, 0, 3.0, 0, "fountain"));
                }
                case PRISON -> {
                    // Cell blocks
                    for (int i = 0; i < 4; i++) {
                        Building block = new Building(cx - 60 + i * 40, cz, 35, 60, 10, 0);
                        block.id = "cellblock_" + cx + "_" + cz + "_" + i;
                        layout.buildings.add(block);
                    }

                    // Admin
                    Building admin = new Building(cx, cz - 50, 50, 30, 8, 0);
                    admin.id = "prison_admin_" + cx + "_" + cz;
                    layout.buildings.add(admin);

                    // Guard towers
                    for (int i = 0; i < 4; i++) {
                        double x = (i % 2 == 0 ? -1 : 1) * 80;
                        double z = (i < 2 ? -1 : 1) * 60;
                        Building tower = new Building(cx + x, cz + z, 8, 8, 15, 0);
                        tower.id = "prison_tower_" + cx + "_" + cz + "_" + i;
                        layout.buildings.add(tower);
                    }

                    // Perimeter fence
                    for (int i = 0; i < 30; i++) {
                        double angle = (i / 30.0) * Math.PI * 2;
                        layout.props.add(new Prop(cx + Math.cos(angle) * 100, cz + Math.sin(angle) * 100,
                                (float) Math.toDegrees(angle) + 90, 1.0, 0, "prison_fence"));
                    }
                }
                case ASYLUM -> {
                    Building main = new Building(cx, cz, 100, 80, 12, 0);
                    main.id = "asylum_" + cx + "_" + cz;
                    layout.buildings.add(main);

                    // Wings
                    Building wing1 = new Building(cx - 60, cz, 40, 60, 10, 0);
                    wing1.id = "asylum_wing1_" + cx + "_" + cz;
                    layout.buildings.add(wing1);

                    Building wing2 = new Building(cx + 60, cz, 40, 60, 10, 0);
                    wing2.id = "asylum_wing2_" + cx + "_" + cz;
                    layout.buildings.add(wing2);

                    // Courtyard
                    layout.props.add(new Prop(cx, cz, 0, 8.0, 0, "courtyard"));

                    // Special loot: medical/chemical
                    layout.lootSpawns.add(new LootSpawn(cx, Constants.GROUND_Y + 2, cz - 20,
                            new String[]{"medical", "chemicals", "rare_meds"}, 2, 5, 168));
                }
                case SEWER_ACCESS -> {
                    // Manhole cover
                    layout.props.add(new Prop(cx, cz, 0, 1.0, 0, "manhole"));

                    // Ventilation
                    layout.props.add(new Prop(cx, cz - 5, 0, 0.5, 0, "sewer_vent"));

                    // Underground entrance marker
                    layout.lootSpawns.add(new LootSpawn(cx, Constants.GROUND_Y - 5, cz,
                            new String[]{"sewer_loot", "scrap", "ammo"}, 1, 3, 72));
                }
                case UNDERGROUND_BUNKER -> {
                    // Surface hatch
                    layout.props.add(new Prop(cx, cz, 0, 2.0, 0, "bunker_hatch"));

                    // Vent shafts
                    for (int i = 0; i < 4; i++) {
                        double angle = (i / 4.0) * Math.PI * 2;
                        layout.props.add(new Prop(cx + Math.cos(angle) * 15, cz + Math.sin(angle) * 15,
                                0, 0.5, 0, "vent_shaft"));
                    }

                    // Antenna
                    layout.props.add(new Prop(cx, cz, 0, 3.0, 0, "antenna"));

                    // High-value loot
                    layout.lootSpawns.add(new LootSpawn(cx, Constants.GROUND_Y - 10, cz,
                            new String[]{"prepper_loot", "weapons", "ammo", "food_long"}, 3, 6, 336));
                }
                case HOTEL -> {
                    // Large hotel building
                    Building main = new Building(cx, cz, 80, 60, 20, 0);
                    main.id = "hotel_" + cx + "_" + cz;
                    layout.buildings.add(main);

                    // Lobby entrance
                    layout.props.add(new Prop(cx, cz + 35, 0, 3.0, 0, "awning"));

                    // Parking
                    layout.props.add(new Prop(cx - 50, cz - 40, 0, 12.0, 0, "parking_lines"));

                    // Pool area
                    layout.props.add(new Prop(cx + 50, cz, 0, 6.0, 0, "pool"));
                }
            }
            layout.computeBounds();
            return layout;
        }

        @Override public String getName() { return "Special/Unique"; }
        @Override public int getTier() { return 4; }
        @Override public String[] getTags() { return new String[]{"loot_unique", "loot_high_value", "zombies_very_high", "boss_chance"}; }
    }

    private static final class EnvironmentalGenerator implements Generator {
        @Override
        public Layout generate(Kind kind, double cx, double cz, double size, Random rn) {
            Layout layout = new Layout();

            switch (kind) {
                case WATER_TOWER -> {
                    Building tower = new Building(cx, cz, 12, 12, 40, 0);
                    tower.id = "water_tower_" + cx + "_" + cz;
                    layout.buildings.add(tower);

                    // Tank on top
                    Building tank = new Building(cx, cz, 20, 20, 10, 0);
                    tank.id = "water_tank_" + cx + "_" + cz;
                    layout.buildings.add(tank);

                    // Ladder
                    layout.props.add(new Prop(cx + 6, cz, 0, 1.0, 0, "ladder"));
                }
                case WINDFARM -> {
                    int turbines = 5 + rn.nextInt(5);
                    for (int i = 0; i < turbines; i++) {
                        double tx = cx + (rn.nextDouble() - 0.5) * size * 0.9;
                        double tz = cz + (rn.nextDouble() - 0.5) * size * 0.9;
                        Building turbine = new Building(tx, tz, 6, 6, 60, 0);
                        turbine.id = "wind_turbine_" + cx + "_" + cz + "_" + i;
                        layout.buildings.add(turbine);

                        // Blades (prop)
                        layout.props.add(new Prop(tx, tz, rn.nextDouble() * 360, 1.0, 0, "turbine_blades"));
                    }

                    // Substation
                    Building sub = new Building(cx, cz + size * 0.4, 16, 12, 6, 0);
                    sub.id = "wind_substation_" + cx + "_" + cz;
                    layout.buildings.add(sub);
                }
                case SOLAR_FARM -> {
                    int rows = 6, cols = 8;
                    for (int r = 0; r < rows; r++) {
                        for (int c = 0; c < cols; c++) {
                            double px = cx - size * 0.4 + c * 12 + 6;
                            double pz = cz - size * 0.3 + r * 10 + 5;
                            layout.props.add(new Prop(px, pz, 0, 1.0, 0, "solar_panel"));
                        }
                    }

                    // Inverter station
                    Building inv = new Building(cx, cz + size * 0.3, 12, 10, 4, 0);
                    inv.id = "solar_inverter_" + cx + "_" + cz;
                    layout.buildings.add(inv);
                }
                case CELL_TOWER -> {
                    Building tower = new Building(cx, cz, 4, 4, 80, 0);
                    tower.id = "cell_tower_" + cx + "_" + cz;
                    layout.buildings.add(tower);

                    // Antennas at various heights
                    for (int h = 20; h < 80; h += 15) {
                        layout.props.add(new Prop(cx, cz, rn.nextDouble() * 360, 0.5, 0, "cell_antenna"));
                    }

                    // Equipment shelter
                    Building shelter = new Building(cx + 10, cz + 10, 8, 8, 4, 0);
                    shelter.id = "cell_shelter_" + cx + "_" + cz;
                    layout.buildings.add(shelter);

                    // Backup generator
                    layout.props.add(new Prop(cx + 10, cz + 18, 0, 1.0, 0, "generator"));
                }
                case DAM -> {
                    // Dam wall
                    Building dam = new Building(cx, cz, 100, 10, 50, 0);
                    dam.id = "dam_" + cx + "_" + cz;
                    layout.buildings.add(dam);

                    // Powerhouse
                    Building ph = new Building(cx, cz + 15, 30, 20, 12, 0);
                    ph.id = "powerhouse_" + cx + "_" + cz;
                    layout.buildings.add(ph);

                    // Spillway
                    layout.props.add(new Prop(cx, cz - 5, 0, 5.0, 0, "spillway"));
                }
            }
            layout.computeBounds();
            return layout;
        }

        @Override public String getName() { return "Environmental/Infrastructure"; }
        @Override public int getTier() { return 2; }
        @Override public String[] getTags() { return new String[]{"loot_tech", "power_grid", "zombies_low"}; }
    }

    // ============================================================
    // Registry and Access
    // ============================================================

    private static final java.util.Map<Kind, Generator> GENERATORS = initializeGenerators();

    private static java.util.Map<Kind, Generator> initializeGenerators() {
        java.util.Map<Kind, Generator> map = new java.util.HashMap<>();

        // Register generators by kind
        Generator residential = new ResidentialGenerator();
        for (Kind k : new Kind[]{Kind.SUBURBAN_HOUSE, Kind.DUPLEX, Kind.ROWHOUSE, Kind.CUL_DE_SAC, Kind.TRAILER_PARK, Kind.GATED_COMMUNITY}) {
            map.put(k, residential);
        }

        Generator commercial = new CommercialGenerator();
        for (Kind k : new Kind[]{Kind.STRIP_MALL, Kind.WAREHOUSE, Kind.FACTORY, Kind.JUNKYARD, Kind.REFINERY, Kind.POWER_PLANT, Kind.SUBSTATION}) {
            map.put(k, commercial);
        }

        Generator civic = new CivicGenerator();
        for (Kind k : new Kind[]{Kind.HOSPITAL, Kind.CLINIC, Kind.POLICE_STATION, Kind.FIRE_STATION, Kind.SCHOOL, Kind.LIBRARY, Kind.CITY_HALL, Kind.COURTHOUSE}) {
            map.put(k, civic);
        }

        Generator military = new MilitaryGenerator();
        for (Kind k : new Kind[]{Kind.MILITARY_CHECKPOINT, Kind.MILITARY_BASE, Kind.BUNKER, Kind.GUARD_TOWER, Kind.RADAR_STATION, Kind.MISSILE_SILO}) {
            map.put(k, military);
        }

        Generator transport = new TransportGenerator();
        for (Kind k : new Kind[]{Kind.HIGHWAY_REST_STOP, Kind.TRUCK_STOP, Kind.TRAIN_STATION, Kind.SUBWAY_ENTRANCE, Kind.AIRPORT_HANGAR, Kind.HELIPAD, Kind.RAILWAY_DEPOT, Kind.DINER, Kind.MOTEL}) {
            map.put(k, transport);
        }

        Generator wilderness = new WildernessGenerator();
        for (Kind k : new Kind[]{Kind.CAMPGROUND, Kind.HUNTING_LODGE, Kind.RANGER_STATION, Kind.LOOKOUT_TOWER, Kind.CAVE_ENTRANCE, Kind.MINE_SHAFT}) {
            map.put(k, wilderness);
        }

        Generator special = new SpecialGenerator();
        for (Kind k : new Kind[]{Kind.MALL, Kind.CASINO, Kind.STADIUM, Kind.ZOO, Kind.PRISON, Kind.ASYLUM, Kind.SEWER_ACCESS, Kind.UNDERGROUND_BUNKER, Kind.HOTEL}) {
            map.put(k, special);
        }

        Generator environmental = new EnvironmentalGenerator();
        for (Kind k : new Kind[]{Kind.LAKE_DOCK, Kind.RIVER_BRIDGE, Kind.DAM, Kind.WATER_TOWER, Kind.WINDFARM, Kind.SOLAR_FARM, Kind.CELL_TOWER}) {
            map.put(k, environmental);
        }

        return map;
    }

    public static Generator getGenerator(Kind kind) {
        return GENERATORS.get(kind);
    }

    public static Layout generate(Kind kind, double cx, double cz, double size, long seed) {
        Generator gen = GENERATORS.get(kind);
        if (gen == null) return new Layout();
        Random rn = new Random(seed ^ kind.ordinal() * 0x9E3779B9L);
        return gen.generate(kind, cx, cz, size, rn);
    }

    public static String getName(Kind kind) {
        Generator gen = GENERATORS.get(kind);
        return gen != null ? gen.getName() : "Unknown";
    }

    public static int getTier(Kind kind) {
        Generator gen = GENERATORS.get(kind);
        return gen != null ? gen.getTier() : 1;
    }

    public static String[] getTags(Kind kind) {
        Generator gen = GENERATORS.get(kind);
        return gen != null ? gen.getTags() : new String[0];
    }

    // ============================================================
    // World Map Landmark Generation
    // ============================================================

    public static List<Location> generateWorldLocations(long worldSeed, int regionCx, int regionCz, double regionSize) {
        List<Location> locations = new ArrayList<>();
        Random rn = new Random(worldSeed ^ regionCx * 0x1234567L ^ regionCz * 0x89ABCDEF);

        // Determine how many locations in this region
        int count = 3 + rn.nextInt(6); // 3-8 locations per region

        // Weighted selection by tier
        List<Kind> weightedKinds = new ArrayList<>();
        for (Kind k : Kind.values()) {
            int tier = getTier(k);
            int weight = switch (tier) {
                case 1 -> 10; case 2 -> 6; case 3 -> 3; case 4 -> 1; default -> 1;
            };
            for (int i = 0; i < weight; i++) weightedKinds.add(k);
        }

        for (int i = 0; i < count; i++) {
            Kind kind = weightedKinds.get(rn.nextInt(weightedKinds.size()));
            double lx = regionCx * regionSize + rn.nextDouble() * regionSize;
            double lz = regionCz * regionSize + rn.nextDouble() * regionSize;
            int lcx = (int) Math.floor(lx / DistrictPlan.CHUNK);
            int lcz = (int) Math.floor(lz / DistrictPlan.CHUNK);
            long locSeed = mix(worldSeed, lcx, lcz, kind.ordinal());
            locations.add(new Location(kind, getName(kind), lx, lz,
                    100 + rn.nextDouble() * 200, lcx, lcz, locSeed, getTier(kind), getTags(kind)));
        }

        return locations;
    }

    private static long mix(long seed, int cx, int cz, int kindOrd) {
        long h = seed * 0x9E3779B97F4A7C15L;
        h ^= cx * 0xC2B2AE3D27D4EB4FL;
        h = Long.rotateLeft(h, 31) * 0x165667B19E3779F9L;
        h ^= cz * 0x27D4EB2F165667C5L;
        h ^= kindOrd * 0x517CC1B727220A95L;
        h ^= h >>> 29;
        return h * 0xBF58476D1CE4E5B9L;
    }

    private static Car createCar(double x, double z, double yaw, int kind, int color, boolean wreck) {
        Car c = new Car();
        c.x = x;
        c.z = z;
        c.baseYaw = yaw;
        return c;
    }

    private static Car createCar(double x, double z, int yaw, int kind, int color, boolean wreck) {
        Car c = new Car();
        c.x = x;
        c.z = z;
        c.baseYaw = yaw;
        return c;
    }
}