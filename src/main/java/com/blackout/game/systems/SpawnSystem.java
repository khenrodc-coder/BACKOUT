package com.blackout.game.systems;

import com.blackout.game.core.Gfx;
import com.blackout.game.core.Constants;
import com.blackout.game.entities.Enemy;
import com.blackout.game.entities.Pickup;
import com.blackout.game.environment.Building;
import com.blackout.game.environment.Car;

import java.util.List;
import java.util.Random;

import javafx.scene.Group;

/**
 * Handles spawning of zombies, pickups, animals, and world objects.
 * Extracted from Main.java to reduce god-class size.
 */
public final class SpawnSystem {

    private final Group world;
        private final List<Enemy> enemies;
    private final List<Building> buildings;
    private final List<Car> cars;
    private final List<double[]> roadRects;
    private final List<Pickup> pickups;
    private final java.util.function.Consumer<Enemy> onEnemySpawned;
    private final java.util.function.Consumer<Pickup> onPickupSpawned;

    public SpawnSystem(Group world, Random rng,
            List<Enemy> enemies, List<Building> buildings,
            List<Car> cars, List<double[]> roadRects,
            List<Pickup> pickups,
            java.util.function.Consumer<Enemy> onEnemySpawned,
            java.util.function.Consumer<Pickup> onPickupSpawned) {
        this.world = world;
        this.rng = rng;
        this.enemies = enemies;
        this.buildings = buildings;
        this.cars = cars;
        this.roadRects = roadRects;
        this.pickups = pickups;
        this.onEnemySpawned = onEnemySpawned;
        this.onPickupSpawned = onPickupSpawned;
    }

    // Zombie spawning
    public void spawnZombie(double x, double z, int type) {
        Enemy e = new Enemy(x, z, type);
        e.model.setTranslateY(Gfx.GROUND_Y);
        e.spawnTimer = 0;
        e.idleTimer = 1 + Constants.RNG.nextDouble() * 4;
        enemies.add(e);
        world.getChildren().add(e.model);
        onEnemySpawned.accept(e);
    }

    public void spawnZombieInBuilding(Building b, int type) {
        if (b.spawn.isEmpty()) return;
        double[] sp = b.spawn.get(Constants.RNG.nextInt(b.spawn.size()));
        spawnZombie(sp[0], sp[1], type);
    }

    public void spawnZombieNearCar(Car c, int type) {
        double sgn = Constants.RNG.nextBoolean() ? 1 : -1;
        double sx = c.x + (c.turned ? 0 : 46 * sgn);
        double sz = c.z + (c.turned ? 46 * sgn : 0);
        Enemy e = new Enemy(sx, sz, type);
        e.car = c;
        e.hiddenTimer = 1.8;
        e.model.setVisible(false);
        enemies.add(e);
        world.getChildren().add(e.model);
        onEnemySpawned.accept(e);
    }

    public void spawnZombieOnRoad(double minDist, double maxDist, double playerX, double playerZ, int type) {
        for (int attempt = 0; attempt < 10; attempt++) {
            double[] rr = roadRects.get(Constants.RNG.nextInt(roadRects.size()));
            double sx = rr[0] + (Constants.RNG.nextDouble() - 0.5) * (rr[2] - 40);
            double sz = rr[1] + (Constants.RNG.nextDouble() - 0.5) * (rr[3] - 40);
            double d = dist(playerX, playerZ, sx, sz);
            if (d < minDist || d > maxDist) continue;
            if (Math.abs(sx) > Gfx.WORLD_BOUNDARY || sz < -Gfx.WORLD_BOUNDARY || sz > Gfx.WORLD_MAX_Z) continue;
            spawnZombie(sx, sz, type);
            return;
        }
    }

    // Pickup spawning
    public void spawnPickup(int kind, double x, double z) {
        if (pickups.size() > 160) {
            Pickup old = pickups.remove(0);
            world.getChildren().remove(old.node);
        }
        Pickup pk = new Pickup();
        pk.kind = kind;
        pk.x = x;
        pk.z = z;
        pk.phase = Constants.RNG.nextDouble() * 6;
        pk.node = createPickupModel(kind, x, z);
        pickups.add(pk);
        world.getChildren().add(pk.node);
        onPickupSpawned.accept(pk);
    }

    public void spawnPickupInBuilding(Building b, int maxPickups) {
        if (b.loot.isEmpty()) return;
        int found = 0;
        for (double[] l : b.loot) {
            if (found >= maxPickups) break;
            if (Constants.RNG.nextDouble() < 0.7) {
                spawnPickup(Constants.RNG.nextInt(3), l[0], l[1]);
                found++;
            }
        }
    }

    public void spawnLootDrop(double x, double z, int type, String id) {
        Pickup pk = new Pickup();
        pk.kind = type;
        pk.x = x;
        pk.z = z;
        pk.id = id;
        pk.phase = Constants.RNG.nextDouble() * 6;
        pk.node = createPickupModel(type, x, z);
        pickups.add(pk);
        world.getChildren().add(pk.node);
    }

    private Pickup.Node createPickupModel(int kind, double x, double z) {
        // Simplified - actual model creation in Main
        return null;
    }

    // Animal/wildlife spawning (for future phases)
    public void spawnDeer(double x, double z) {
        // TODO: Phase 3
    }

    public void spawnWolf(double x, double z) {
        // TODO: Phase 3
    }

    public void spawnBird(double x, double z) {
        // TODO: Phase 3
    }

    // Vehicle spawning
    public void spawnWreckedCar(double x, double z, double rotY) {
        // TODO: Uses VehicleFactory
    }

    // Environmental spawning
    public void spawnFire(double x, double y, double z, double scale) {
        // TODO: Uses Fire class
    }

    public void spawnSmokeCloud(double x, double z, double radius, double life) {
        // TODO: Uses SmokeCloud class
    }

    private double dist(double x1, double z1, double x2, double z2) {
        double dx = x1 - x2, dz = z1 - z2;
        return Math.sqrt(dx * dx + dz * dz);
    }
}