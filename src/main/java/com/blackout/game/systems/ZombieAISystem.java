package com.blackout.game.systems;

import com.blackout.game.core.Gfx;
import com.blackout.game.core.Mats;
import com.blackout.game.core.Constants;
import com.blackout.game.entities.Enemy;
import com.blackout.game.environment.Building;
import com.blackout.game.environment.Car;
import com.blackout.game.environment.Obstacle;
import com.blackout.game.effects.SmokeCloud;
import com.blackout.game.effects.Particle;
import com.blackout.game.entities.Rig;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import javafx.scene.Group;
import javafx.scene.paint.Color;
import javafx.scene.paint.PhongMaterial;

/**
 * Handles all zombie AI logic: spawning, movement, state machines, attacks, blood moon.
 * Phase 6 enhancements: sound perception, search behavior, horde grouping, door breaking.
 */
public final class ZombieAISystem {

    // Sound event class
    public static final class SoundEvent {
        public final double x, z;
        public final double radius;
        public final double intensity; // 0-1
        public final long timestamp;
        public final SoundType type;

        public SoundEvent(double x, double z, double radius, double intensity, SoundType type) {
            this.x = x; this.z = z; this.radius = radius; this.intensity = intensity;
            this.timestamp = System.currentTimeMillis();
            this.type = type;
        }

        public enum SoundType {
            GUNSHOT(1200, 1.0),
            SPRINT(300, 0.6),
            DOOR_OPEN(200, 0.5),
            GLASS_BREAK(400, 0.7),
            EXPLOSION(2000, 1.0),
            FOOTSTEP(80, 0.3),
            RELOAD(150, 0.4),
            MELEE_HIT(100, 0.3),
            ZOMBIE_MOAN(500, 0.8);

            public final double baseRadius;
            public final double baseIntensity;

            SoundType(double baseRadius, double baseIntensity) {
                this.baseRadius = baseRadius;
                this.baseIntensity = baseIntensity;
            }
        }
    }

    private final Group world;
    private final List<Enemy> enemies;
    private final List<Building> buildings;
    private final List<Car> cars;
    private final List<double[]> roadRects;
    private final List<SmokeCloud> smokeClouds;
    private final List<Particle> particles;
    private final java.util.function.Consumer<Enemy> onEnemyKilled;
    private final java.util.function.DoubleConsumer damagePlayer;
    private final java.util.function.DoubleConsumer alertZombies;
    private final java.util.function.DoubleSupplier getEffThreat;
    private final java.util.function.BooleanSupplier isBloodMoon;
    private final java.util.function.DoubleSupplier getPlayerX;
    private final java.util.function.DoubleSupplier getPlayerZ;
    private final java.util.function.DoubleSupplier getFeetY;
    private final java.util.function.BooleanSupplier isCrouched;
    private final java.util.function.BooleanSupplier isSprinting;
    private final java.util.function.BooleanSupplier isGrounded;

    // Sound system
    private final List<SoundEvent> activeSounds = new ArrayList<>();
    private final List<SoundEvent> soundHistory = new ArrayList<>();

    // Search/investigation
    private final List<InvestigationPoint> investigationPoints = new ArrayList<>();

    // Horde system
    private final List<Horde> hordes = new ArrayList<>();

    // Door breaking
    private final List<DoorBreach> doorBreaches = new ArrayList<>();

    private double spawnClock = 3;
    private double survivalTime = 0;
    private double threat = 0;
    private int lastThreatInt = 0;
    private int staticBuildingCount = 0;

    public ZombieAISystem(Group world,
            List<Enemy> enemies, List<Building> buildings,
            List<Car> cars, List<double[]> roadRects,
            List<SmokeCloud> smokeClouds, List<Particle> particles,
            java.util.function.Consumer<Enemy> onEnemyKilled,
            java.util.function.DoubleConsumer damagePlayer,
            java.util.function.DoubleConsumer alertZombies,
            java.util.function.DoubleSupplier getEffThreat,
            java.util.function.BooleanSupplier isBloodMoon,
            java.util.function.DoubleSupplier getPlayerX,
            java.util.function.DoubleSupplier getPlayerZ,
            java.util.function.DoubleSupplier getFeetY,
            java.util.function.BooleanSupplier isCrouched,
            java.util.function.BooleanSupplier isSprinting,
            java.util.function.BooleanSupplier isGrounded) {
        this.world = world;
        this.enemies = enemies;
        this.buildings = buildings;
        this.cars = cars;
        this.roadRects = roadRects;
        this.smokeClouds = smokeClouds;
        this.particles = particles;
        this.onEnemyKilled = onEnemyKilled;
        this.damagePlayer = damagePlayer;
        this.alertZombies = alertZombies;
        this.getEffThreat = getEffThreat;
        this.isBloodMoon = isBloodMoon;
        this.getPlayerX = getPlayerX;
        this.getPlayerZ = getPlayerZ;
        this.getFeetY = getFeetY;
        this.isCrouched = isCrouched;
        this.isSprinting = isSprinting;
        this.isGrounded = isGrounded;
    }

    // ===================== SOUND SYSTEM =====================

    /**
     * Register a sound event that zombies can hear.
     */
    public void registerSound(double x, double z, SoundEvent.SoundType type) {
        SoundEvent se = new SoundEvent(x, z, type.baseRadius, type.baseIntensity, type);
        activeSounds.add(se);
        soundHistory.add(se);
        if (soundHistory.size() > 100) soundHistory.remove(0);
    }

    /**
     * Register a sound with custom radius/intensity.
     */
    public void registerSound(double x, double z, double radius, double intensity, SoundEvent.SoundType type) {
        SoundEvent se = new SoundEvent(x, z, radius, intensity, type);
        activeSounds.add(se);
        soundHistory.add(se);
    }

    // ===================== INVESTIGATION POINTS =====================

    public static final class InvestigationPoint {
        public final double x, z;
        public final double priority;
        public final long created;
        public int investigators = 0;
        public boolean resolved = false;

        public InvestigationPoint(double x, double z, double priority) {
            this.x = x; this.z = z; this.priority = priority;
            this.created = System.currentTimeMillis();
        }
    }

    private void addInvestigationPoint(double x, double z, double priority) {
        investigationPoints.add(new InvestigationPoint(x, z, priority));
        if (investigationPoints.size() > 50) investigationPoints.remove(0);
    }

    // ===================== HORDE SYSTEM =====================

    public static final class Horde {
        public double centerX, centerZ;
        public final List<Enemy> members = new ArrayList<>();
        public double targetX, targetZ;
        public long lastUpdate;
        public int id;

        public Horde(int id) {
            this.id = id;
            this.lastUpdate = System.currentTimeMillis();
        }

        public void updateCenter() {
            if (members.isEmpty()) return;
            double sumX = 0, sumZ = 0;
            for (Enemy e : members) {
                sumX += e.model.getTranslateX();
                sumZ += e.model.getTranslateZ();
            }
            centerX = sumX / members.size();
            centerZ = sumZ / members.size();
        }
    }

    private void updateHordes(double dt) {
        // Merge nearby zombies into hordes
        for (Enemy e : enemies) {
            if (!e.alive || e.spawnTimer > 0 || e.hiddenTimer > 0) continue;

            Horde bestHorde = null;
            double bestDist = 150; // Horde formation radius

            for (Horde h : hordes) {
                double dist = Math.hypot(e.model.getTranslateX() - h.centerX, e.model.getTranslateZ() - h.centerZ);
                if (dist < bestDist) {
                    bestDist = dist;
                    bestHorde = h;
                }
            }

            if (bestHorde != null) {
                if (!bestHorde.members.contains(e)) {
                    bestHorde.members.add(e);
                }
            } else {
                // Create new horde if enough zombies nearby
                int nearby = 0;
                for (Enemy other : enemies) {
                    if (other == e || !other.alive) continue;
                    double d = Math.hypot(e.model.getTranslateX() - other.model.getTranslateX(),
                            e.model.getTranslateZ() - other.model.getTranslateZ());
                    if (d < 150) nearby++;
                }
                if (nearby >= 2) {
                    Horde h = new Horde(hordes.size());
                    h.members.add(e);
                    h.updateCenter();
                    hordes.add(h);
                }
            }
        }

        // Clean up empty hordes
        hordes.removeIf(h -> h.members.isEmpty() || h.members.stream().allMatch(e -> !e.alive));

        // Update horde centers and apply group behavior
        for (Horde h : hordes) {
            h.updateCenter();
            if (h.members.size() < 3) continue; // Only large hordes have group behavior

            // Horde shares target if one member sees player
            Enemy leader = null;
            for (Enemy m : h.members) {
                if (m.state == com.blackout.game.core.Constants.S_CHASE) {
                    leader = m;
                    break;
                }
            }

            if (leader != null) {
                h.targetX = leader.model.getTranslateX();
                h.targetZ = leader.model.getTranslateZ();
                // Other members follow leader
                for (Enemy m : h.members) {
                    if (m != leader && m.state != com.blackout.game.core.Constants.S_CHASE) {
                        m.state = com.blackout.game.core.Constants.S_CHASE;
                        m.lostTimer = 7;
                    }
                }
            }
        }
    }

    // ===================== DOOR BREACHING =====================

    public static final class DoorBreach {
        public final Building building;
        public final double doorX, doorZ;
        public double progress = 0; // 0-100
        public final List<Enemy> attackers = new ArrayList<>();
        public long started;

        public DoorBreach(Building b, double dx, double dz) {
            this.building = b;
            this.doorX = dx;
            this.doorZ = dz;
            this.started = System.currentTimeMillis();
        }
    }

    private void updateDoorBreaches(double dt) {
        for (Iterator<DoorBreach> it = doorBreaches.iterator(); it.hasNext();) {
            DoorBreach db = it.next();
            // Remove dead attackers
            db.attackers.removeIf(e -> !e.alive);

            if (db.attackers.isEmpty()) {
                it.remove();
                continue;
            }

            // Progress based on number of attackers and zombie type
            double breachRate = db.attackers.stream().mapToDouble(e -> {
                return switch (e.type) {
                    case com.blackout.game.core.Constants.Z_TANK -> 15;
                    case com.blackout.game.core.Constants.Z_RUNNER -> 8;
                    default -> 5;
                };
            }).sum();

            db.progress += breachRate * dt;

            if (db.progress >= 100) {
                // Door breaks
                db.building.doorOpen = true;
                db.building.doorAmt = 1;
                if (db.building.doorRot != null) db.building.doorRot.setAngle(90 * db.building.front);
                if (db.building.doorObs != null) db.building.doorObs.active = false;
                it.remove();
            }
        }
    }

    // ===================== PUBLIC API =====================

    public void setStaticBuildingCount(int count) {
        this.staticBuildingCount = count;
    }

    public void spawnInitialZombies() {
        for (int i = 0; i < 22; i++) spawnRandomZombie(true);
    }

    public void updateSpawning(double dt) {
        survivalTime += dt;
        threat = Math.min(10, survivalTime / 70.0 + countKills() / 14.0);
        int ti = (int) threat;
        if (ti > lastThreatInt) lastThreatInt = ti;

        int alive = countAlive();
        int cap = 20 + (int) (getEffThreat.getAsDouble() * 3) + (isBloodMoon.getAsBoolean() ? 14 : 0);
        spawnClock -= dt;
        if (spawnClock <= 0) {
            spawnClock = Math.max(isBloodMoon.getAsBoolean() ? 0.5 : 0.8, (4.0 - getEffThreat.getAsDouble() * 0.3) * (isBloodMoon.getAsBoolean() ? 0.5 : 1.0));
            if (alive < cap) spawnRandomZombie(false);
        }
    }

    private int countKills() {
        int n = 0;
        for (Enemy e : enemies) if (!e.alive) n++;
        return n;
    }

    private int countAlive() {
        int n = 0;
        for (Enemy e : enemies) if (e.alive) n++;
        return n;
    }

    private int countType(int t) {
        int n = 0;
        for (Enemy e : enemies) if (e.alive && e.type == t) n++;
        return n;
    }

    private int pickType() {
        double tank = threat < 2 ? 0 : 1.5 + threat * 0.7;
        double smoker = threat < 1 ? 0 : 4 + threat * 1.1;
        double runner = 14 + threat * 2.5;
        if (countType(com.blackout.game.core.Constants.Z_TANK) >= 1 + (int) (threat / 3)) tank = 0;
        if (countType(com.blackout.game.core.Constants.Z_SMOKER) >= 2 + (int) (threat / 3)) smoker = 0;
        double total = 100 + runner + tank + smoker, r = Constants.RNG.nextDouble() * total;
        if (r < tank) return com.blackout.game.core.Constants.Z_TANK;
        r -= tank;
        if (r < smoker) return com.blackout.game.core.Constants.Z_SMOKER;
        r -= smoker;
        if (r < runner) return com.blackout.game.core.Constants.Z_RUNNER;
        return com.blackout.game.core.Constants.Z_WALKER;
    }

    private Enemy spawnEnemy(double x, double z, int type) {
        Enemy e = new Enemy(x, z, type);
        e.model.setTranslateY(Constants.GROUND_Y);
        e.spawnTimer = 0;
        e.idleTimer = 1 + Constants.RNG.nextDouble() * 4;
        if (isBloodMoon.getAsBoolean()) e.setBlood(true);
        enemies.add(e);
        world.getChildren().add(e.model);
        return e;
    }

    private void spawnRandomZombie(boolean initial) {
        double px = getPlayerX.getAsDouble(), pz = getPlayerZ.getAsDouble();
        double minD = initial ? 250 : 380, maxD = 1400;
        int type = pickType();
        for (int attempt = 0; attempt < 14; attempt++) {
            double r = Constants.RNG.nextDouble();
            if (r < 0.42 && type != com.blackout.game.core.Constants.Z_TANK && !buildings.isEmpty()) {
                Building b = buildings.get(Constants.RNG.nextInt(buildings.size()));
                if (b.spawn.isEmpty()) continue;
                double[] sp = b.spawn.get(Constants.RNG.nextInt(b.spawn.size()));
                double d = dist(px, pz, sp[0], sp[1]);
                if (d < minD || d > maxD) continue;
                spawnEnemy(sp[0], sp[1], type);
                return;
            } else if (r < 0.68 && type != com.blackout.game.core.Constants.Z_TANK && !cars.isEmpty()) {
                Car c = cars.get(Constants.RNG.nextInt(cars.size()));
                double d = dist(px, pz, c.x, c.z);
                if (d < minD || d > maxD) continue;
                double sgn = Constants.RNG.nextBoolean() ? 1 : -1;
                double sx = c.x + (c.turned ? 0 : 46 * sgn), sz = c.z + (c.turned ? 46 * sgn : 0);
                if (blocked(sx, sz, 22, 0, false)) continue;
                Enemy e = spawnEnemy(sx, sz, type);
                e.car = c;
                e.hiddenTimer = initial ? 0.01 : 1.8;
                e.model.setVisible(false);
                return;
            } else {
                double[] rr = roadRects.get(Constants.RNG.nextInt(roadRects.size()));
                double sx = rr[0] + (Constants.RNG.nextDouble() - 0.5) * (rr[2] - 40), sz = rr[1] + (Constants.RNG.nextDouble() - 0.5) * (rr[3] - 40);
                double d = dist(px, pz, sx, sz);
                if (d < minD || d > maxD || Math.abs(sx) > Constants.WORLD_BOUNDARY || sz < -Constants.WORLD_BOUNDARY || sz > Constants.WORLD_MAX_Z || blocked(sx, sz, 34, 0, false)) continue;
                Enemy e = spawnEnemy(sx, sz, type);
                if (!initial) {
                    e.spawnTimer = 1.2;
                    e.model.setTranslateY(Constants.GROUND_Y + 62);
                }
                return;
            }
        }
    }

    // Movement and collision
    private boolean moveEnemy(Enemy e, double tx, double tz, double speed, double dt) {
        double ex = e.model.getTranslateX(), ez = e.model.getTranslateZ();
        double dx = tx - ex, dz = tz - ez, d = Math.sqrt(dx * dx + dz * dz);
        if (d < 1) return false;
        double ux = dx / d, uz = dz / d;
        if (e.sideT > 0) {
            e.sideT -= dt;
            double sx = -uz * e.sideDir, sz = ux * e.sideDir;
            ux = ux * 0.3 + sx * 0.9;
            uz = uz * 0.3 + sz * 0.9;
            double l = Math.sqrt(ux * ux + uz * uz);
            ux /= l; uz /= l;
        }
        double nx = ex + ux * speed * dt, nz = ez + uz * speed * dt;
        boolean mxOk = !blocked(nx, ez, e.radius, 0, false);
        if (mxOk) e.model.setTranslateX(clampX(nx));
        boolean mzOk = !blocked(e.model.getTranslateX(), nz, e.radius, 0, false);
        if (mzOk) e.model.setTranslateZ(clampZ(nz));
        if (!mxOk && !mzOk) {
            e.stuck += dt;
            if (e.stuck > 0.4) {
                e.sideT = 0.7;
                e.sideDir = Constants.RNG.nextBoolean() ? 1 : -1;
                e.stuck = 0;
            }
        } else e.stuck = 0;
        return true;
    }

    private double[] steerTarget(Enemy e, Building eb, Building pb, double px, double pz) {
        double ex = e.model.getTranslateX(), ez = e.model.getTranslateZ();
        if (eb == null) e.exitStage = 0;
        if (eb == pb || pb == null) e.enterStage = 0;
        if (eb != null && eb != pb) {
            if (e.exitStage == 0) {
                if (dist(ex, ez, eb.inX, eb.inZ) < 34) {
                    e.exitStage = 1;
                    eb.doorOpen = true;
                }
                return new double[]{eb.inX, eb.inZ};
            }
            return new double[]{eb.outX, eb.outZ};
        }
        if (pb != null && eb != pb) {
            if (e.enterStage == 0) {
                if (dist(ex, ez, pb.outX, pb.outZ) < 40) {
                    e.enterStage = 1;
                    pb.doorOpen = true;
                }
                return new double[]{pb.outX, pb.outZ};
            }
            return new double[]{pb.inX, pb.inZ};
        }
        if (eb != null && eb == pb) return eb.route(ex, ez, px, pz);
        return new double[]{px, pz};
    }

    public void updateEnemies(double dt) {
        double px = getPlayerX.getAsDouble(), pz = getPlayerZ.getAsDouble();
        Building pb = findBuildingAt(px, pz);
        double baseSpeed = 62 + getEffThreat.getAsDouble() * 3.5;
        boolean reachable = getFeetY.getAsDouble() > Constants.GROUND_Y - 55;

        // Update sound system
        updateSounds(dt);
        // Update hordes
        updateHordes(dt);
        // Update door breaches
        updateDoorBreaches(dt);
        // Clean up old investigation points
        investigationPoints.removeIf(ip -> System.currentTimeMillis() - ip.created > 30000 || ip.resolved);

        for (Iterator<Enemy> it = enemies.iterator(); it.hasNext();) {
            Enemy e = it.next();
            double ex = e.model.getTranslateX(), ez = e.model.getTranslateZ();
            double dx = px - ex, dz = pz - ez, dist = Math.sqrt(dx * dx + dz * dz);

            // Sound perception
            if (e.state != com.blackout.game.core.Constants.S_CHASE) {
                double bestSoundIntensity = 0;
                SoundEvent bestSound = null;
                for (SoundEvent se : activeSounds) {
                    double sd = Math.hypot(ex - se.x, ez - se.z);
                    if (sd <= se.radius) {
                        double intensity = se.intensity * (1 - sd / se.radius);
                        if (intensity > bestSoundIntensity) {
                            bestSoundIntensity = intensity;
                            bestSound = se;
                        }
                    }
                }

                if (bestSound != null && bestSoundIntensity > 0.3) {
                    // Heard something - investigate
                    e.alert = Math.max(e.alert, 3 * bestSoundIntensity);
                    addInvestigationPoint(bestSound.x, bestSound.z, bestSoundIntensity * 10);
                }

                // Check investigation points
                if (e.state == com.blackout.game.core.Constants.S_IDLE || e.state == com.blackout.game.core.Constants.S_WANDER) {
                    for (InvestigationPoint ip : investigationPoints) {
                        if (ip.investigators >= 3) continue; // Max 3 investigators
                        double ipDist = Math.hypot(ex - ip.x, ez - ip.z);
                        if (ipDist < 200 && ip.priority > 5) {
                            ip.investigators++;
                            e.wx = ip.x;
                            e.wz = ip.z;
                            e.state = com.blackout.game.core.Constants.S_WANDER;
                            e.wanderT = 10;
                            break;
                        }
                    }
                }
            }

            if (!e.alive) {
                e.deathTime += dt;
                double fp = Math.min(1, e.deathTime / 0.55);
                e.fall.setAngle(e.deathDir * fp * fp * (3 - 2 * fp) * 88);
                double sink = e.deathTime > 9 ? (e.deathTime - 9) * 12 : 0;
                e.model.setTranslateY(Constants.GROUND_Y - 9 * e.sy * fp + sink);
                e.model.setVisible(dist < 1100);
                if (e.deathTime > 14) {
                    world.getChildren().remove(e.model);
                    it.remove();
                }
                continue;
            }
            if (e.hiddenTimer > 0) {
                e.hiddenTimer -= dt;
                if (e.car != null) e.car.rot.setAngle(e.car.baseYaw + Math.sin(e.model.getTranslateX() * 45) * 1.6);
                if (e.hiddenTimer <= 0) {
                    if (e.car != null) e.car.rot.setAngle(e.car.baseYaw);
                    e.model.setVisible(dist < 1100);
                    burst(ex, Constants.GROUND_Y - 10, ez, 6, 60, 5, 0.8, 0, false, null, Mats.DUST);
                    e.alert = 6;
                }
                continue;
            }
            if (dist > 1300) { e.model.setVisible(false); continue; }
            e.model.setVisible(dist < 1100);
            if (e.spawnTimer > 0) {
                e.spawnTimer -= dt;
                e.model.setTranslateY(Constants.GROUND_Y + 62 * Math.max(0, e.spawnTimer / 1.2));
                e.animate(dt, false, false);
                continue;
            }
            e.model.setTranslateY(Constants.GROUND_Y);
            if (e.stagger > 0) e.stagger -= dt;
            if (e.slamT > 0) e.slamT -= dt;
            e.attackCd -= dt;
            Building eb = findBuildingAt(ex, ez);
            double det = e.detect * (isCrouched.getAsBoolean() ? 0.6 : 1) * (isSprinting.getAsBoolean() ? 1.35 : 1);
            if (e.alert > 0) e.alert -= dt;
            boolean sees = dist < (eb == pb ? det : Math.min(det * 0.4, 160));
            if (e.blood && dist < 1100) sees = true;
            if (sees || e.alert > 0) {
                if (e.state != com.blackout.game.core.Constants.S_CHASE) burst(ex, Constants.GROUND_Y - 40 * e.sy, ez, 2, 20, 3, 0.5, 0, false, null, Mats.DUST);
                e.state = com.blackout.game.core.Constants.S_CHASE;
                e.lostTimer = 7;
            } else if (e.state == com.blackout.game.core.Constants.S_CHASE) {
                e.lostTimer -= dt;
                if (e.lostTimer <= 0) { e.state = com.blackout.game.core.Constants.S_IDLE; e.idleTimer = 2 + Constants.RNG.nextDouble() * 3; }
            }
            boolean moving = false, chasing = e.state == com.blackout.game.core.Constants.S_CHASE;
            double faceX = dx, faceZ = dz;
            double speed = baseSpeed * e.speedMul * (e.stagger > 0 ? 0.15 : 1);
            if (e.state == com.blackout.game.core.Constants.S_IDLE) {
                e.idleTimer -= dt;
                if (e.idleTimer <= 0) {
                    e.state = com.blackout.game.core.Constants.S_WANDER;
                    e.wanderT = 4 + Constants.RNG.nextDouble() * 4;
                    if (eb != null && !eb.spawn.isEmpty()) {
                        double[] sp = eb.spawn.get(Constants.RNG.nextInt(eb.spawn.size()));
                        e.wx = sp[0]; e.wz = sp[1];
                    } else {
                        e.wx = clampX(ex + (Constants.RNG.nextDouble() - 0.5) * 260);
                        e.wz = clampZ(ez + (Constants.RNG.nextDouble() - 0.5) * 260);
                    }
                }
            } else if (e.state == com.blackout.game.core.Constants.S_WANDER) {
                e.wanderT -= dt;
                faceX = e.wx - ex; faceZ = e.wz - ez;
                moving = moveEnemy(e, e.wx, e.wz, speed * 0.35, dt);
                if (dist(ex, ez, e.wx, e.wz) < 15 || e.wanderT <= 0) { e.state = com.blackout.game.core.Constants.S_IDLE; e.idleTimer = 2 + Constants.RNG.nextDouble() * 4; }
            } else if (e.type == com.blackout.game.core.Constants.Z_SMOKER) {
                e.castCd -= dt;
                if (e.windup > 0) {
                    e.windup -= dt;
                    if (e.stagger > 0.05) e.windup = -1;
                    else if (e.windup <= 0) { castSmoke(e, px, pz); e.castCd = 7; }
                } else if (dist <= e.reach) {
                    if (e.attackCd <= 0 && reachable) { damagePlayer.accept(8); e.attackCd = 1.2; }
                } else if (dist <= 380 && dist >= 150 && e.castCd <= 0 && eb == pb) {
                    e.windup = e.windupDur;
                } else if (dist > 240 || eb != pb) {
                    double[] t = steerTarget(e, eb, pb, px, pz);
                    faceX = t[0] - ex; faceZ = t[1] - ez;
                    moving = moveEnemy(e, t[0], t[1], speed, dt);
                }
                e.puffT -= dt;
                if (e.puffT <= 0) { e.puffT = 0.3; double ry = Math.toRadians(e.yaw); emit(smokeClouds, ex + Math.sin(ry) * 10, Constants.GROUND_Y - 92 * e.sy, ez + Math.cos(ry) * 10, 0, -14, 0, 1.6, -4, false, false, 1.8); }
            } else {
                if (e.windup > 0) {
                    e.windup -= dt;
                    if (e.stagger > 0.05 && e.type == com.blackout.game.core.Constants.Z_WALKER) e.windup = -1;
                    else if (e.windup <= 0) strike(e, dist, reachable);
                } else if (dist <= e.reach && eb == pb) {
                    if (e.attackCd <= 0 && reachable) e.windup = e.windupDur;
                } else {
                    double[] t = steerTarget(e, eb, pb, px, pz);
                    faceX = t[0] - ex; faceZ = t[1] - ez;
                    moving = moveEnemy(e, t[0], t[1], speed, dt);
                }
            }
            if (Math.abs(faceX) + Math.abs(faceZ) > 0.01 && (moving || e.windup > 0 || e.state != com.blackout.game.core.Constants.S_IDLE)) {
                double target = Math.toDegrees(Math.atan2(faceX, faceZ)), diff = target - e.yaw;
                while (diff > 180) diff -= 360;
                while (diff < -180) diff += 360;
                e.yaw += diff * Math.min(1, dt * 7);
                e.facing.setAngle(e.yaw);
            }
            if (e.type == com.blackout.game.core.Constants.Z_TANK && moving && dist < 650) {
                boolean step = Math.sin(e.walkPhase * 2) > 0.97;
                if (step && !e.stepFlag) { /* shake handled by Main */ }
                e.stepFlag = step;
            }
            e.animate(dt, moving, chasing);
        }

        // Separation
        for (int i = 0; i < enemies.size(); i++) {
            Enemy a = enemies.get(i);
            if (!a.alive || a.spawnTimer > 0 || a.hiddenTimer > 0) continue;
            for (int j = i + 1; j < enemies.size(); j++) {
                Enemy b = enemies.get(j);
                if (!b.alive || b.spawnTimer > 0 || b.hiddenTimer > 0) continue;
                double dx = b.model.getTranslateX() - a.model.getTranslateX(), dz = b.model.getTranslateZ() - a.model.getTranslateZ();
                double d = Math.sqrt(dx * dx + dz * dz), min = Math.min(80, 36 * (a.sx + b.sx) / 2);
                if (d < min && d > 0.01) {
                    double push = (min - d) * 0.5;
                    a.model.setTranslateX(a.model.getTranslateX() - dx / d * push);
                    a.model.setTranslateZ(a.model.getTranslateZ() - dz / d * push);
                    b.model.setTranslateX(b.model.getTranslateX() + dx / d * push);
                    b.model.setTranslateZ(b.model.getTranslateZ() + dz / d * push);
                }
            }
        }
    }

    private void strike(Enemy e, double d, boolean reachable) {
        e.attackCd = e.type == com.blackout.game.core.Constants.Z_RUNNER ? 0.8 : e.type == com.blackout.game.core.Constants.Z_TANK ? 1.8 : 1.0;
        if (e.type == com.blackout.game.core.Constants.Z_TANK) { e.slamT = 0.25; /* shake */ }
        if (d > e.reach * 1.25 || !reachable) return;
        if (e.type == com.blackout.game.core.Constants.Z_RUNNER) damagePlayer.accept(9999);
        else if (e.type == com.blackout.game.core.Constants.Z_TANK) damagePlayer.accept(9999);
        else damagePlayer.accept(14 + threat * 1.2);
    }

    private void castSmoke(Enemy e, double px, double pz) {
        SmokeCloud c = new SmokeCloud();
        c.x = px + (Constants.RNG.nextDouble() - 0.5) * 30;
        c.z = pz + (Constants.RNG.nextDouble() - 0.5) * 30;
        c.r = 95; c.life = 8;
        smokeClouds.add(c);
        double ex = e.model.getTranslateX(), ez = e.model.getTranslateZ(), ey = Constants.GROUND_Y - 92 * e.sy;
        for (int i = 0; i < 16; i++) {
            double t = (i + 1) / 16.0;
            emit(smokeClouds, ex, ey, ez, (c.x - ex) * 1.3 + (Constants.RNG.nextDouble() - 0.5) * 30, 10, (c.z - ez) * 1.3 + (Constants.RNG.nextDouble() - 0.5) * 30, 0.8 + t * 0.4, 0, false, false, 2.5);
        }
    }

    private Building findBuildingAt(double x, double z) {
        for (Building b : buildings) if (b.contains(x, z)) return b;
        return null;
    }

    // Distance
    private double dist(double x1, double z1, double x2, double z2) {
        double dx = x1 - x2, dz = z1 - z2;
        return Math.sqrt(dx * dx + dz * dz);
    }

    private double clampX(double x) { return Math.max(-Constants.WORLD_BOUNDARY, Math.min(Constants.WORLD_BOUNDARY, x)); }
    private double clampZ(double z) { return Math.max(-Constants.WORLD_BOUNDARY, Math.min(Constants.WORLD_MAX_Z, z)); }

    // Blocked check - needs access to collision grid
    // This is a placeholder - Main will provide the actual implementation
    private boolean blocked(double x, double z, double r, double fy, boolean isPlayer) {
        // Delegate to Main's collision system
        return false;
    }

    // Particle emission - delegate to CombatSystem or Main
    private void burst(double x, double y, double z, int count, double speed, double size, double life,
            double gravity, boolean blood, double[] dir, PhongMaterial mat) {
        // Delegate to CombatSystem
    }

    private void emit(List<SmokeCloud> clouds, double x, double y, double z, double vx, double vy, double vz, double life,
            double gravity, boolean bounce, boolean blood, double grow) {
        // Placeholder
    }

    // ===================== SOUND UPDATE =====================

    private void updateSounds(double dt) {
        long now = System.currentTimeMillis();
        // Remove sounds older than 10 seconds
        activeSounds.removeIf(se -> now - se.timestamp > 10000);
    }
}