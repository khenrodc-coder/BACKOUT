package com.blackout.game.world;

import com.blackout.game.core.Constants;
import com.blackout.game.core.Gfx;
import com.blackout.game.environment.Building;
import com.blackout.game.environment.Car;
import com.blackout.game.environment.TreeFactory;
import com.blackout.game.entities.Enemy;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * World generator that integrates LocationTypes with ChunkManager and DistrictPlan.
 * Handles procedural generation of the expanded open world with diverse POIs.
 */
public final class WorldGenerator {

    private WorldGenerator() {}

    // ============================================================
    // Region Definitions
    // ============================================================

    public enum RegionType {
        DOWNTOWN("Downtown", 0),           // Existing district
        SUBURBAN_NORTH("Suburban North", 1),
        SUBURBAN_SOUTH("Suburban South", 2),
        INDUSTRIAL_ZONE("Industrial Zone", 3),
        COMMERCIAL_STRIP("Commercial Strip", 4),
        CIVIC_CENTER("Civic Center", 5),
        MILITARY_PERIMETER("Military Perimeter", 6),
        HIGHWAY_CORRIDOR("Highway Corridor", 7),
        WILDERNESS_NORTH("Wilderness North", 8),
        WILDERNESS_SOUTH("Wilderness South", 9),
        RIVER_VALLEY("River Valley", 10),
        COASTAL("Coastal", 11),
        AIRPORT_DISTRICT("Airport District", 12),
        MALL_DISTRICT("Mall District", 13),
        PRISON_COMPLEX("Prison Complex", 14);

        public final String name;
        public final int id;

        RegionType(String name, int id) {
            this.name = name;
            this.id = id;
        }

        public static RegionType byId(int id) {
            for (RegionType r : values()) if (r.id == id) return r;
            return DOWNTOWN;
        }
    }

    // Region bounds in chunks (cx, cz)
    public static final class RegionBounds {
        public final RegionType type;
        public final int minCx, maxCx, minCz, maxCz;
        public final double weight; // likelihood of generating here

        public RegionBounds(RegionType type, int minCx, int maxCx, int minCz, int maxCz, double weight) {
            this.type = type;
            this.minCx = minCx; this.maxCx = maxCx;
            this.minCz = minCz; this.maxCz = maxCz;
            this.weight = weight;
        }

        public boolean contains(int cx, int cz) {
            return cx >= minCx && cx <= maxCx && cz >= minCz && cz <= maxCz;
        }
    }

    // Extended world regions
    private static final RegionBounds[] REGIONS = {
        // Original downtown district
        new RegionBounds(RegionType.DOWNTOWN, -3, 2, 4, 7, 1.0),

        // New regions expanding the world
        new RegionBounds(RegionType.SUBURBAN_NORTH, -5, 4, 8, 15, 0.9),
        new RegionBounds(RegionType.SUBURBAN_SOUTH, -5, 4, -8, 3, 0.9),
        new RegionBounds(RegionType.INDUSTRIAL_ZONE, -8, -4, -2, 10, 0.7),
        new RegionBounds(RegionType.COMMERCIAL_STRIP, 3, 8, -2, 10, 0.7),
        new RegionBounds(RegionType.CIVIC_CENTER, -2, 3, -4, 3, 0.8),
        new RegionBounds(RegionType.MILITARY_PERIMETER, -10, -6, -10, -2, 0.5),
        new RegionBounds(RegionType.HIGHWAY_CORRIDOR, -12, 12, 16, 18, 0.6),
        new RegionBounds(RegionType.WILDERNESS_NORTH, -15, 15, 18, 30, 0.4),
        new RegionBounds(RegionType.WILDERNESS_SOUTH, -15, 15, -20, -9, 0.4),
        new RegionBounds(RegionType.RIVER_VALLEY, -4, 0, 16, 28, 0.5),
        new RegionBounds(RegionType.COASTAL, -15, -8, -5, 15, 0.3),
        new RegionBounds(RegionType.AIRPORT_DISTRICT, 8, 14, 8, 16, 0.4),
        new RegionBounds(RegionType.MALL_DISTRICT, -6, -1, 10, 16, 0.4),
        new RegionBounds(RegionType.PRISON_COMPLEX, -12, -8, -8, -2, 0.3),
    };

    // ============================================================
    // Region -> Location Kind Mapping
    // ============================================================

    private static final java.util.Map<RegionType, LocationTypes.Kind[]> REGION_KINDS = new java.util.HashMap<>();

    static {
        REGION_KINDS.put(RegionType.DOWNTOWN, new LocationTypes.Kind[]{
            LocationTypes.Kind.OFFICE, LocationTypes.Kind.APARTMENT, LocationTypes.Kind.SHOP,
            LocationTypes.Kind.SUPERMARKET, LocationTypes.Kind.GAS_SHOP, LocationTypes.Kind.CANOPY,
            LocationTypes.Kind.PARKING, LocationTypes.Kind.PLAZA, LocationTypes.Kind.PARK
        });

        REGION_KINDS.put(RegionType.SUBURBAN_NORTH, new LocationTypes.Kind[]{
            LocationTypes.Kind.SUBURBAN_HOUSE, LocationTypes.Kind.DUPLEX, LocationTypes.Kind.ROWHOUSE,
            LocationTypes.Kind.CUL_DE_SAC, LocationTypes.Kind.GATED_COMMUNITY, LocationTypes.Kind.PARK,
            LocationTypes.Kind.SCHOOL, LocationTypes.Kind.FIRE_STATION, LocationTypes.Kind.CLINIC
        });

        REGION_KINDS.put(RegionType.SUBURBAN_SOUTH, new LocationTypes.Kind[]{
            LocationTypes.Kind.SUBURBAN_HOUSE, LocationTypes.Kind.TRAILER_PARK, LocationTypes.Kind.ROWHOUSE,
            LocationTypes.Kind.CUL_DE_SAC, LocationTypes.Kind.PARK, LocationTypes.Kind.GAS_SHOP,
            LocationTypes.Kind.STRIP_MALL, LocationTypes.Kind.CLINIC
        });

        REGION_KINDS.put(RegionType.INDUSTRIAL_ZONE, new LocationTypes.Kind[]{
            LocationTypes.Kind.WAREHOUSE, LocationTypes.Kind.FACTORY, LocationTypes.Kind.JUNKYARD,
            LocationTypes.Kind.REFINERY, LocationTypes.Kind.POWER_PLANT, LocationTypes.Kind.SUBSTATION,
            LocationTypes.Kind.TRUCK_STOP, LocationTypes.Kind.RAILWAY_DEPOT
        });

        REGION_KINDS.put(RegionType.COMMERCIAL_STRIP, new LocationTypes.Kind[]{
            LocationTypes.Kind.STRIP_MALL, LocationTypes.Kind.SUPERMARKET, LocationTypes.Kind.GAS_SHOP,
            LocationTypes.Kind.HOSPITAL, LocationTypes.Kind.CLINIC, LocationTypes.Kind.POLICE_STATION,
            LocationTypes.Kind.MALL, LocationTypes.Kind.CASINO
        });

        REGION_KINDS.put(RegionType.CIVIC_CENTER, new LocationTypes.Kind[]{
            LocationTypes.Kind.CITY_HALL, LocationTypes.Kind.COURTHOUSE, LocationTypes.Kind.POLICE_STATION,
            LocationTypes.Kind.FIRE_STATION, LocationTypes.Kind.LIBRARY, LocationTypes.Kind.SCHOOL,
            LocationTypes.Kind.HOSPITAL, LocationTypes.Kind.PARK
        });

        REGION_KINDS.put(RegionType.MILITARY_PERIMETER, new LocationTypes.Kind[]{
            LocationTypes.Kind.MILITARY_CHECKPOINT, LocationTypes.Kind.MILITARY_BASE, LocationTypes.Kind.BUNKER,
            LocationTypes.Kind.GUARD_TOWER, LocationTypes.Kind.RADAR_STATION, LocationTypes.Kind.MISSILE_SILO,
            LocationTypes.Kind.UNDERGROUND_BUNKER
        });

        REGION_KINDS.put(RegionType.HIGHWAY_CORRIDOR, new LocationTypes.Kind[]{
            LocationTypes.Kind.HIGHWAY_REST_STOP, LocationTypes.Kind.TRUCK_STOP, LocationTypes.Kind.GAS_SHOP,
            LocationTypes.Kind.DINER, LocationTypes.Kind.MOTEL
        });

        REGION_KINDS.put(RegionType.WILDERNESS_NORTH, new LocationTypes.Kind[]{
            LocationTypes.Kind.CAMPGROUND, LocationTypes.Kind.HUNTING_LODGE, LocationTypes.Kind.RANGER_STATION,
            LocationTypes.Kind.LOOKOUT_TOWER, LocationTypes.Kind.CAVE_ENTRANCE, LocationTypes.Kind.MINE_SHAFT
        });

        REGION_KINDS.put(RegionType.WILDERNESS_SOUTH, new LocationTypes.Kind[]{
            LocationTypes.Kind.CAMPGROUND, LocationTypes.Kind.HUNTING_LODGE, LocationTypes.Kind.RANGER_STATION,
            LocationTypes.Kind.LOOKOUT_TOWER, LocationTypes.Kind.CAVE_ENTRANCE
        });

        REGION_KINDS.put(RegionType.RIVER_VALLEY, new LocationTypes.Kind[]{
            LocationTypes.Kind.RIVER_BRIDGE, LocationTypes.Kind.DAM, LocationTypes.Kind.HUNTING_LODGE,
            LocationTypes.Kind.CAMPGROUND, LocationTypes.Kind.POWER_PLANT, LocationTypes.Kind.WATER_TOWER
        });

        REGION_KINDS.put(RegionType.COASTAL, new LocationTypes.Kind[]{
            LocationTypes.Kind.LAKE_DOCK, LocationTypes.Kind.HUNTING_LODGE, LocationTypes.Kind.CAMPGROUND,
            LocationTypes.Kind.LOOKOUT_TOWER
        });

        REGION_KINDS.put(RegionType.AIRPORT_DISTRICT, new LocationTypes.Kind[]{
            LocationTypes.Kind.AIRPORT_HANGAR, LocationTypes.Kind.HELIPAD, LocationTypes.Kind.WAREHOUSE,
            LocationTypes.Kind.MILITARY_CHECKPOINT, LocationTypes.Kind.SUBSTATION
        });

        REGION_KINDS.put(RegionType.MALL_DISTRICT, new LocationTypes.Kind[]{
            LocationTypes.Kind.MALL, LocationTypes.Kind.STRIP_MALL, LocationTypes.Kind.PARKING,
            LocationTypes.Kind.CASINO, LocationTypes.Kind.HOTEL
        });

        REGION_KINDS.put(RegionType.PRISON_COMPLEX, new LocationTypes.Kind[]{
            LocationTypes.Kind.PRISON, LocationTypes.Kind.MILITARY_CHECKPOINT, LocationTypes.Kind.GUARD_TOWER,
            LocationTypes.Kind.BUNKER, LocationTypes.Kind.WAREHOUSE
        });
    }

    // ============================================================
    // Chunk-Level Generation
    // ============================================================

    public static final class ChunkData {
        public final int cx, cz;
        public final RegionType region;
        public final List<Building> buildings = new ArrayList<>();
        public final List<Car> vehicles = new ArrayList<>();
        public final List<TreeFactory.Tree> trees = new ArrayList<>();
        public final List<LocationTypes.LootSpawn> lootSpawns = new ArrayList<>();
        public final List<LocationTypes.EnemySpawn> enemySpawns = new ArrayList<>();
        public final List<LocationTypes.LightSource> lights = new ArrayList<>();
        public final List<LocationTypes.Prop> props = new ArrayList<>();

        public ChunkData(int cx, int cz, RegionType region) {
            this.cx = cx; this.cz = cz; this.region = region;
        }
    }

    /**
     * Generate chunk data for a given chunk coordinate.
     * Uses deterministic seeding based on world seed and chunk coordinates.
     */
    public static ChunkData generateChunk(long worldSeed, int cx, int cz) {
        // Determine region
        RegionType region = RegionType.DOWNTOWN;
        for (RegionBounds rb : REGIONS) {
            if (rb.contains(cx, cz)) {
                region = rb.type;
                break;
            }
        }

        // Check if this chunk should use DistrictPlan (downtown)
        if (region == RegionType.DOWNTOWN && DistrictPlan.exists(cx, cz)) {
            DistrictPlan.Plan plan = DistrictPlan.generate(worldSeed, cx, cz);
            return convertDistrictPlan(plan, region);
        }

        // Otherwise use LocationTypes system
        ChunkData data = new ChunkData(cx, cz, region);
        LocationTypes.Kind[] kinds = REGION_KINDS.getOrDefault(region, new LocationTypes.Kind[]{LocationTypes.Kind.PARK});

        // Deterministic seed for this chunk
        long chunkSeed = DistrictPlan.mix(worldSeed, cx, cz);
        Random rn = new Random(chunkSeed);

        // Decide if this chunk gets a location (probability based on region)
        RegionBounds bounds = getBounds(region);
        double locationChance = bounds != null ? bounds.weight * 0.3 : 0.15;

        if (rn.nextDouble() < locationChance) {
            // Pick a location kind appropriate for this region
            LocationTypes.Kind kind = kinds[rn.nextInt(kinds.length)];

            // Generate the location
            double size = DistrictPlan.CHUNK;
            double centerX = cx * size + size / 2;
            double centerZ = cz * size + size / 2;

            LocationTypes.Layout layout = LocationTypes.generate(kind, centerX, centerZ, size, chunkSeed);

            // Convert layout to chunk data
            data.buildings.addAll(layout.buildings);
            data.vehicles.addAll(layout.vehicles);

            // Add trees
            for (TreeFactory.Tree tree : layout.trees) {
                data.trees.add(tree);
            }

            // Add loot spawns
            data.lootSpawns.addAll(layout.lootSpawns);

            // Add enemy spawns
            data.enemySpawns.addAll(layout.enemySpawns);

            // Add lights
            data.lights.addAll(layout.lights);

            // Add props
            data.props.addAll(layout.props);
        } else {
            // Empty chunk - just add some trees/props occasionally
            if (rn.nextDouble() < 0.1) {
                for (int i = 0; i < 3 + rn.nextInt(5); i++) {
                    double tx = cx * DistrictPlan.CHUNK + rn.nextDouble() * DistrictPlan.CHUNK;
                    double tz = cz * DistrictPlan.CHUNK + rn.nextDouble() * DistrictPlan.CHUNK;
                    data.trees.add(TreeFactory.create(TreeFactory.PINE, 1.0, tx, tz, Constants.GROUND_Y, rn));
                }
            }
        }

        // Add street lights at chunk corners (consistent with DistrictPlan)
        double lx0 = cx * DistrictPlan.CHUNK, lz0 = cz * DistrictPlan.CHUNK;
        double lx1 = lx0 + DistrictPlan.CHUNK, lz1 = lz0 + DistrictPlan.CHUNK;
        data.lights.add(new LocationTypes.LightSource(lx0, Constants.GROUND_Y + 12, lz0, 30, 0.8, 0, true, true));
        data.lights.add(new LocationTypes.LightSource(lx1, Constants.GROUND_Y + 12, lz0, 30, 0.8, 0, true, true));
        data.lights.add(new LocationTypes.LightSource(lx0, Constants.GROUND_Y + 12, lz1, 30, 0.8, 0, true, true));
        data.lights.add(new LocationTypes.LightSource(lx1, Constants.GROUND_Y + 12, lz1, 30, 0.8, 0, true, true));

        return data;
    }

    private static RegionBounds getBounds(RegionType region) {
        for (RegionBounds rb : REGIONS) {
            if (rb.type == region) return rb;
        }
        return null;
    }

    private static ChunkData convertDistrictPlan(DistrictPlan.Plan plan, RegionType region) {
        ChunkData data = new ChunkData(plan.cx, plan.cz, region);

        // Convert lots to buildings (handled by DistrictBuilder)
        // DistrictPlan lots are processed separately in DistrictBuilder

        // Add cars (convert DistrictPlan.Car to environment.Car)
        for (DistrictPlan.Car dc : plan.cars) {
            Car c = new Car();
            c.x = dc.x;
            c.z = dc.z;
            c.baseYaw = dc.yaw;
            data.vehicles.add(c);
        }

        // Add trees
        for (DistrictPlan.Spot spot : plan.trees) {
            data.trees.add(TreeFactory.create(spot.variant, spot.scale, spot.x, spot.z, Constants.GROUND_Y,
                    new Random(spot.variant ^ (plan.cx << 16) ^ plan.cz)));
        }

        // Add benches as props
        for (DistrictPlan.Spot spot : plan.benches) {
            data.props.add(new LocationTypes.Prop(spot.x, spot.z, spot.yaw, spot.scale, spot.variant, "bench"));
        }

        // Add street lights
        for (double[] light : plan.lights) {
            data.lights.add(new LocationTypes.LightSource(light[0], Constants.GROUND_Y + 12, light[1], 30, 0.8, 0, true, true));
        }

        return data;
    }

    // ============================================================
    // World Map Generation
    // ============================================================

    public static final class WorldMap {
        public final List<LocationTypes.Location> landmarks = new ArrayList<>();
        public final List<RegionBounds> regions = new ArrayList<>();
        public final int minCx, maxCx, minCz, maxCz;

        public WorldMap(long worldSeed) {
            // Compute overall bounds
            int minCx = Integer.MAX_VALUE, maxCx = Integer.MIN_VALUE;
            int minCz = Integer.MAX_VALUE, maxCz = Integer.MIN_VALUE;
            for (RegionBounds rb : REGIONS) {
                minCx = Math.min(minCx, rb.minCx);
                maxCx = Math.max(maxCx, rb.maxCx);
                minCz = Math.min(minCz, rb.minCz);
                maxCz = Math.max(maxCz, rb.maxCz);
            }
            this.minCx = minCx; this.maxCx = maxCx;
            this.minCz = minCz; this.maxCz = maxCz;

            // Add region bounds
            for (RegionBounds rb : REGIONS) {
                regions.add(rb);
            }

            // Generate landmarks for each region
            Random rn = new Random(worldSeed ^ 0xFEEDFACE);
            for (RegionBounds rb : REGIONS) {
                int regionCx = (rb.minCx + rb.maxCx) / 2;
                int regionCz = (rb.minCz + rb.maxCz) / 2;
                double regionSize = Math.max(rb.maxCx - rb.minCx, rb.maxCz - rb.minCz) * DistrictPlan.CHUNK;

                List<LocationTypes.Location> locs = LocationTypes.generateWorldLocations(worldSeed, regionCx, regionCz, regionSize);
                for (LocationTypes.Location loc : locs) {
                    // Only add if within region bounds
                    if (rb.contains(loc.chunkX, loc.chunkZ)) {
                        landmarks.add(loc);
                    }
                }
            }

            // Add downtown landmarks
            for (Object[] o : DistrictPlan.landmarks(worldSeed)) {
                String name = (String) o[0];
                double x = (double) o[1];
                double z = (double) o[2];
                LocationTypes.Kind kind = switch (name) {
                    case "Downtown" -> LocationTypes.Kind.PLAZA;
                    case "Supermarket" -> LocationTypes.Kind.SUPERMARKET;
                    case "Gas Station" -> LocationTypes.Kind.GAS_SHOP;
                    default -> LocationTypes.Kind.PARK;
                };
                landmarks.add(new LocationTypes.Location(kind, name, x, z, 200,
                        (int) Math.floor(x / DistrictPlan.CHUNK),
                        (int) Math.floor(z / DistrictPlan.CHUNK),
                        worldSeed, 1, new String[]{"landmark"}));
            }
        }
    }

    // ============================================================
    // Spawn Point Generation
    // ============================================================

    public static final class SpawnPoint {
        public final double x, z;
        public final String name;
        public final boolean safe; // guaranteed clear of enemies
        public final String[] startingLoot;

        public SpawnPoint(double x, double z, String name, boolean safe, String[] startingLoot) {
            this.x = x; this.z = z; this.name = name;
            this.safe = safe; this.startingLoot = startingLoot;
        }
    }

    /**
     * Generate deterministic spawn points across the world.
     * Used for new game starts, fast travel, respawning.
     */
    public static List<SpawnPoint> generateSpawnPoints(long worldSeed) {
        List<SpawnPoint> spawns = new ArrayList<>();
        Random rn = new Random(worldSeed ^ 0x5EEDFACE);

        // Safe spawn in downtown (original start)
        spawns.add(new SpawnPoint(0, 2000, "Downtown Safehouse", true,
                new String[]{"starter_pistol", "ammo_9mm", "bandage", "water_bottle"}));

        // Suburban spawns
        for (RegionBounds rb : REGIONS) {
            if (rb.type == RegionType.SUBURBAN_NORTH || rb.type == RegionType.SUBURBAN_SOUTH) {
                int cx = (rb.minCx + rb.maxCx) / 2;
                int cz = (rb.minCz + rb.maxCz) / 2;
                double x = cx * DistrictPlan.CHUNK + DistrictPlan.CHUNK / 2;
                double z = cz * DistrictPlan.CHUNK + DistrictPlan.CHUNK / 2;
                spawns.add(new SpawnPoint(x, z, rb.type.name + " Spawn", true,
                        new String[]{"melee_bat", "canned_food", "water_bottle", "bandage"}));
            }
        }

        // Highway rest stops
        for (RegionBounds rb : REGIONS) {
            if (rb.type == RegionType.HIGHWAY_CORRIDOR) {
                int cx = rb.minCx + rn.nextInt(rb.maxCx - rb.minCx + 1);
                int cz = rb.minCz + rn.nextInt(rb.maxCz - rb.minCz + 1);
                double x = cx * DistrictPlan.CHUNK + DistrictPlan.CHUNK / 2;
                double z = cz * DistrictPlan.CHUNK + DistrictPlan.CHUNK / 2;
                spawns.add(new SpawnPoint(x, z, "Highway Rest Stop", false,
                        new String[]{"melee_knife", "snack", "water_bottle"}));
            }
        }

        // Wilderness camps
        for (RegionBounds rb : REGIONS) {
            if (rb.type == RegionType.WILDERNESS_NORTH || rb.type == RegionType.WILDERNESS_SOUTH) {
                int cx = rb.minCx + rn.nextInt(rb.maxCx - rb.minCx + 1);
                int cz = rb.minCz + rn.nextInt(rb.maxCz - rb.minCz + 1);
                double x = cx * DistrictPlan.CHUNK + DistrictPlan.CHUNK / 2;
                double z = cz * DistrictPlan.CHUNK + DistrictPlan.CHUNK / 2;
                spawns.add(new SpawnPoint(x, z, "Wilderness Camp", false,
                        new String[]{"bow", "arrows", "canned_food", "water_bottle", "bandage"}));
            }
        }

        return spawns;
    }

    // ============================================================
    // Enemy Population by Region
    // ============================================================

    public static void populateEnemies(ChunkData chunk, List<Enemy> enemies, Random rn) {
        for (LocationTypes.EnemySpawn spawn : chunk.enemySpawns) {
            if (!shouldSpawn(spawn, rn)) continue;

            int count = spawn.minCount + rn.nextInt(spawn.maxCount - spawn.minCount + 1);
            for (int i = 0; i < count; i++) {
                double ex = spawn.x + (rn.nextDouble() - 0.5) * spawn.patrolRadius * 2;
                double ez = spawn.z + (rn.nextDouble() - 0.5) * spawn.patrolRadius * 2;

                int type = spawn.enemyTypes[rn.nextInt(spawn.enemyTypes.length)];
                Enemy enemy = createEnemy(type, ex, ez, chunk.region, rn);

                if (spawn.isPatrol) {
                    enemy.wx = ex;
                    enemy.wz = ez;
                    // patrolRadius not directly available, could use detect range
                }

                enemies.add(enemy);
            }
        }

        // Region-specific ambient spawns
        addAmbientSpawns(chunk, enemies, rn);
    }

    private static boolean shouldSpawn(LocationTypes.EnemySpawn spawn, Random rn) {
        // Check spawn condition
        return switch (spawn.spawnCondition) {
            case "always" -> true;
            case "night" -> rn.nextDouble() < 0.7; // placeholder, actual time check in game
            case "bloodmoon" -> rn.nextDouble() < 0.5;
            case "triggered" -> false; // handled by triggers
            default -> true;
        };
    }

    private static Enemy createEnemy(int type, double x, double z, RegionType region, Random rn) {
        // Use existing Enemy factory with region-appropriate variations
        Enemy enemy = new Enemy(x, z, type);

        // Region-specific modifications
        switch (region) {
            case MILITARY_PERIMETER -> {
                // Tougher zombies, some with armor
                enemy.maxHealth *= 1.5;
                enemy.health = enemy.maxHealth;
            }
            case PRISON_COMPLEX -> {
                // Tougher zombies, some with armor
                enemy.maxHealth *= 1.5;
                enemy.health = enemy.maxHealth;
            }
            case CIVIC_CENTER -> {
                // Infected medical staff - faster
                enemy.speedMul *= 1.2;
            }
            case COMMERCIAL_STRIP -> {
                // Infected medical staff - faster
                enemy.speedMul *= 1.2;
            }
            case INDUSTRIAL_ZONE -> {
                // Hazmat zombies - could mark with a custom flag if needed
            }
            case WILDERNESS_NORTH -> {
                // Feral zombies - more health
                enemy.maxHealth *= 1.2;
                enemy.health = enemy.maxHealth;
            }
            case WILDERNESS_SOUTH -> {
                // Feral zombies - more health
                enemy.maxHealth *= 1.2;
                enemy.health = enemy.maxHealth;
            }
        }

        return enemy;
    }

    private static void addAmbientSpawns(ChunkData chunk, List<Enemy> enemies, Random rn) {
        double density = switch (chunk.region) {
            case DOWNTOWN -> 0.15;
            case SUBURBAN_NORTH, SUBURBAN_SOUTH -> 0.08;
            case INDUSTRIAL_ZONE -> 0.12;
            case COMMERCIAL_STRIP -> 0.10;
            case CIVIC_CENTER -> 0.20;
            case MILITARY_PERIMETER -> 0.05; // fewer but tougher
            case HIGHWAY_CORRIDOR -> 0.06;
            case WILDERNESS_NORTH, WILDERNESS_SOUTH -> 0.03;
            case RIVER_VALLEY -> 0.04;
            case COASTAL -> 0.03;
            case AIRPORT_DISTRICT -> 0.10;
            case MALL_DISTRICT -> 0.25;
            case PRISON_COMPLEX -> 0.15;
        };

        // Adjust for buildings
        if (!chunk.buildings.isEmpty()) density *= 1.5;

        int ambientCount = (int) (density * 20);
        for (int i = 0; i < ambientCount; i++) {
            double ex = chunk.cx * DistrictPlan.CHUNK + rn.nextDouble() * DistrictPlan.CHUNK;
            double ez = chunk.cz * DistrictPlan.CHUNK + rn.nextDouble() * DistrictPlan.CHUNK;

            // Avoid spawning inside buildings
            boolean inBuilding = false;
            for (Building b : chunk.buildings) {
                if (b.contains(ex, ez)) { inBuilding = true; break; }
            }
            if (inBuilding) continue;

            int type = rn.nextInt(4); // 0-3 walker, runner, tank, smoker
            Enemy enemy = createEnemy(type, ex, ez, chunk.region, rn);
            enemies.add(enemy);
        }
    }

    // ============================================================
    // Loot Population
    // ============================================================

    public static void populateLoot(ChunkData chunk, java.util.function.Consumer<LocationTypes.LootSpawn> lootConsumer) {
        for (LocationTypes.LootSpawn spawn : chunk.lootSpawns) {
            lootConsumer.accept(spawn);
        }
    }
}