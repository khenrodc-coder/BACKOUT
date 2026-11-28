package com.blackout.game.systems;

import com.blackout.game.core.Gfx;
import com.blackout.game.core.Mats;
import com.blackout.game.core.Constants;
import com.blackout.game.effects.*;
import com.blackout.game.entities.*;
import com.blackout.game.entities.WeaponType;
import com.blackout.game.environment.DynLight;
import com.blackout.game.systems.AudioManager;

import javafx.scene.Group;
import javafx.scene.PointLight;
import javafx.scene.paint.Color;
import javafx.scene.paint.PhongMaterial;
import javafx.scene.shape.Box;
import javafx.scene.shape.Sphere;
import javafx.scene.transform.Rotate;
import javafx.scene.text.Text;

import java.util.Iterator;
import java.util.List;

import java.util.Iterator;
import java.util.List;
import java.util.Random;

import javafx.scene.Group;
import javafx.scene.PointLight;
import javafx.scene.paint.Color;
import javafx.scene.paint.PhongMaterial;
import javafx.scene.shape.Box;
import javafx.scene.shape.Sphere;
import javafx.scene.transform.Rotate;
import javafx.scene.text.Text;

/**
 * Handles all combat logic: shooting, reloading, damage, tracers, particles, muzzle flash.
 * Extracted from Main.java to reduce god-class size.
 */
public final class CombatSystem {

    private final Group world;
    private final AudioManager audioManager;

    // Weapon state (owned by Main but managed here)
    private WeaponType currentWeapon = WeaponType.PISTOL;
    private final java.util.EnumMap<WeaponType, Integer> magazineAmmo = new java.util.EnumMap<>(WeaponType.class);
    private final java.util.EnumMap<WeaponType, Integer> reserveAmmo = new java.util.EnumMap<>(WeaponType.class);
    private long lastShotTime = 0;

    // Effects lists (shared with Main)
    private final List<Particle> particles;
    private final List<Tracer> tracers;
    private final List<Explosion> explosions;
    private final List<Grenade> grenadeList;
    private final List<SmokeCloud> smokeClouds;
    private final List<DynLight> lights;

    // Muzzle flash
    private Group muzzleFlash;
    private PointLight muzzleLight;
    private double muzzleLocalY = -9, muzzleLocalZ = 34;
    private double muzzleFlashTimer = 0;

    // Hit marker
    private final Text hitMarker;
    private double hitMarkerTimer = 0;
    private boolean hitHead;

    // Recoil
    private double cameraRecoil = 0;
    private double weaponKick = 0;
    private final double recoilRecovery = 12;

    // Grenade throwing
    private boolean throwing = false, threw = false;
    private double throwT = 0;

    // Reloading
    private double reloadTimer = 0, reloadDur = 1;

    // Medkit
    private boolean usingMed = false;
    private double medT = 0;
    private int prevHeld = 0;
    private int heldItem = 0;

    // Damage flash
    private double damageFlash = 0;

    // Choke/gas
    private double choke = 0;

    public CombatSystem(Group world, Random rng,
            List<Particle> particles, List<Tracer> tracers,
            List<Explosion> explosions, List<Grenade> grenadeList,
            List<SmokeCloud> smokeClouds, List<DynLight> lights,
            Text hitMarker, AudioManager audioManager) {
        this.world = world;
        this.audioManager = audioManager;
        this.particles = particles;
        this.tracers = tracers;
        this.explosions = explosions;
        this.grenadeList = grenadeList;
        this.smokeClouds = smokeClouds;
        this.lights = lights;
        this.hitMarker = hitMarker;
        initializeWeaponAmmo();
    }

    private void initializeWeaponAmmo() {
        for (WeaponType w : WeaponType.values()) {
            magazineAmmo.put(w, w.magazineSize);
            reserveAmmo.put(w, w.startingReserve);
        }
        currentWeapon = WeaponType.PISTOL;
    }

    public void rebuildWeaponModel() {
        // Called from Main when weapon changes
    }

    // ===================== WEAPON HELPERS =====================
    private double kickFor(WeaponType w) {
        return w == WeaponType.PISTOL ? 0.8 : w == WeaponType.ASSAULT_RIFLE ? 0.5 : 1.0;
    }

    private double reloadTimeFor(WeaponType w) {
        return w == WeaponType.PISTOL ? 0.9 : w == WeaponType.ASSAULT_RIFLE ? 1.6 : 2.0;
    }

    // ===================== SHOOTING =====================
    public void shoot(double playerYaw, double cameraYaw, double cameraPitch,
            double playerX, double playerY, double playerZ,
            double feetY, double crouchBlend, boolean crouched,
            boolean isAiming, boolean firing, int heldItem,
            boolean paused, boolean gameOver, boolean sleeping,
            boolean climbing, boolean usingMed,
            List<Enemy> enemies,
            java.util.function.DoubleSupplier alertZombies) {

        if (paused || gameOver || sleeping || heldItem != 0 || reloadTimer > 0 || climbing || usingMed) {
            return;
        }
        long now = System.nanoTime();
        if (now - lastShotTime < currentWeapon.cooldown) {
            return;
        }
        int curAmmo = magazineAmmo.get(currentWeapon);
        if (curAmmo <= 0) {
            if (reserveAmmo.get(currentWeapon) > 0) {
                reload();
            }
            return;
        }
        lastShotTime = now;
        magazineAmmo.put(currentWeapon, curAmmo - 1);
        weaponKick = kickFor(currentWeapon);
        cameraRecoil = Math.min(cameraRecoil + (currentWeapon == WeaponType.SHOTGUN ? 4 : 1.8), 9);
        if (muzzleFlash != null) {
            muzzleFlash.setVisible(true);
            muzzleFlash.setRotationAxis(Rotate.Z_AXIS);
            muzzleFlash.setRotate(Constants.RNG.nextDouble() * 90);
        }
        if (muzzleLight != null) muzzleLight.setLightOn(true);
        muzzleFlashTimer = 0.06;
        alertZombies.getAsDouble();

        // Play weapon shot sound
        audioManager.playWeaponShot(currentWeapon);

        double[] mw = muzzleWorld(playerYaw, feetY, crouchBlend, playerX, playerZ);
        double fx = Math.sin(Math.toRadians(playerYaw)), fz = Math.cos(Math.toRadians(playerYaw));
        for (int i = 0; i < 3; i++) {
            emit(mw[0], mw[1], mw[2],
                    fx * (140 + Constants.RNG.nextDouble() * 140) + (Constants.RNG.nextDouble() - 0.5) * 60,
                    (Constants.RNG.nextDouble() - 0.5) * 60,
                    fz * (140 + Constants.RNG.nextDouble() * 140) + (Constants.RNG.nextDouble() - 0.5) * 60,
                    0.18, 300, false, false, 0, Mats.SPARK);
        }
        emit(playerX + fx * (Gfx.GUN_Z + 6) + fz * (Gfx.GUN_X + 6),
                playerY + Gfx.GUN_Y - 4 + 17.9 * crouchBlend,
                playerZ + fz * (Gfx.GUN_Z + 6) - fx * (Gfx.GUN_X + 6),
                fz * (70 + Constants.RNG.nextDouble() * 40), -(80 + Constants.RNG.nextDouble() * 50),
                -fx * (70 + Constants.RNG.nextDouble() * 40), 3, 520, true, false, 0, Mats.BRASS);

        double cx = mw[0], cy = mw[1], cz = mw[2]; // Using muzzle world for ray origin
        boolean hit = false, elim = false, headshot = false;
        for (int i = 0; i < currentWeapon.pellets; i++) {
            double sYaw = (Constants.RNG.nextDouble() * 2 - 1) * currentWeapon.spread * (isAiming ? 0.4 : crouched ? 0.7 : 1.0);
            double sPitch = (Constants.RNG.nextDouble() * 2 - 1) * currentWeapon.spread * (isAiming ? 0.4 : crouched ? 0.7 : 1.0);
            double[] dir = rayDir(cameraYaw + sYaw, cameraPitch + sPitch);
            Enemy target = findTarget(dir, currentWeapon.range, cx, cy, cz, enemies);
            boolean head = hitHead;
            double d = target != null ? hitDist : currentWeapon.range * 0.8;
            boolean groundHit = false;
            if (target == null && dir[1] > 0.001) {
                double tg = (Constants.GROUND_Y - cy) / dir[1];
                if (tg > 0 && tg < d) {
                    d = tg;
                    groundHit = true;
                }
            }
            double hx = cx + dir[0] * d, hy = cy + dir[1] * d, hz = cz + dir[2] * d;
            if (i < 3) {
                spawnTracer(mw[0], mw[1], mw[2], hx, hy, hz);
            }
            if (groundHit && i < 3) {
                burst(hx, Constants.GROUND_Y - 3, hz, 5, 150, 1.1, 0.35, 400, false, null, Mats.SPARK);
                burst(hx, Constants.GROUND_Y - 3, hz, 3, 40, 4, 0.7, 0, false, null, Mats.DUST);
            }
            if (target != null && target.alive) {
                if (head) headshot = true;
                damageEnemy(target, currentWeapon.damage * (head ? 2.0 : 1.0), hx, hy, hz, dir, head);
                if (!target.alive) elim = true;
                hit = true;
            }
        }
        if (hit) {
            hitMarker.setFill(elim ? Color.ORANGERED : headshot ? Color.GOLD : Color.WHITE);
            hitMarker.setVisible(true);
            hitMarkerTimer = 0.2;
        } else {
            hitMarker.setVisible(false);
        }
    }

    // ===================== RELOADING =====================
    public void reload() {
        if (reloadTimer > 0 || heldItem != 0) return;
        if (magazineAmmo.get(currentWeapon) >= currentWeapon.magazineSize || reserveAmmo.get(currentWeapon) <= 0) {
            return;
        }
        reloadDur = reloadTimeFor(currentWeapon);
        reloadTimer = reloadDur;
        audioManager.playReload(currentWeapon);
    }

    public void updateActions(double dt) {
        if (reloadTimer > 0) {
            reloadTimer -= dt;
            if (reloadTimer <= 0) {
                int cur = magazineAmmo.get(currentWeapon), res = reserveAmmo.get(currentWeapon);
                int amt = Math.min(currentWeapon.magazineSize - cur, res);
                magazineAmmo.put(currentWeapon, cur + amt);
                reserveAmmo.put(currentWeapon, res - amt);
            }
        }
        if (usingMed) {
            medT += dt;
            if (medT >= 1.6) {
                usingMed = false;
                heldItem = prevHeld;
                rebuildWeaponModel();
            }
        }
        if (throwing) {
            throwT += dt;
            if (!threw && throwT >= 0.32) {
                threw = true;
                // Grenade creation handled by Main
            }
            if (throwT >= 0.7) {
                throwing = false;
            }
        }
        if (muzzleFlashTimer > 0) {
            muzzleFlashTimer -= dt;
            if (muzzleFlashTimer <= 0) {
                if (muzzleFlash != null) muzzleFlash.setVisible(false);
                if (muzzleLight != null) muzzleLight.setLightOn(false);
            }
        }
        cameraRecoil = Math.max(0, cameraRecoil - recoilRecovery * dt);
        weaponKick = Math.max(0, weaponKick - dt * 9);
        if (hitMarkerTimer > 0) {
            hitMarkerTimer -= dt;
            if (hitMarkerTimer <= 0) hitMarker.setVisible(false);
        }
        damageFlash = Math.max(0, damageFlash - dt * 1.8);
    }

    public void startThrow() {
        if (throwing) return;
        throwing = true;
        threw = false;
        throwT = 0;
    }

    public void useMedkit(int medkits) {
        if (usingMed || medkits <= 0) return;
        usingMed = true;
        medT = 0;
        prevHeld = heldItem;
        heldItem = 2;
        rebuildWeaponModel();
    }

    public void cycleWeapon(int dir) {
        int cur = heldItem != 0 ? 0 : currentWeapon.ordinal();
        equipSlot(((cur + dir) % 3 + 3) % 3);
    }

    public void equipSlot(int i) {
        if (i <= 2) {
            WeaponType w = i == 0 ? WeaponType.PISTOL : i == 1 ? WeaponType.ASSAULT_RIFLE : WeaponType.SHOTGUN;
            if (currentWeapon == w && heldItem == 0) return;
            heldItem = 0;
            currentWeapon = w;
            lastShotTime = 0;
            rebuildWeaponModel();
        } else if (i == 3) {
            useMedkit(0); // medkits checked inside
        } else if (i == 4) {
            // grenade handled by Main
        } else {
            // torch handled by Main
        }
    }

    // ===================== HELPERS =====================
    private double[] muzzleWorld(double playerYaw, double feetY, double crouchBlend, double playerX, double playerZ) {
        double fx = Math.sin(Math.toRadians(playerYaw)), fz = Math.cos(Math.toRadians(playerYaw));
        double fd = Gfx.GUN_Z + muzzleLocalZ + 4;
        return new double[]{
            playerX + fx * fd + fz * Gfx.GUN_X,
            feetY - 55 + Gfx.GUN_Y + muzzleLocalY + 17.9 * crouchBlend,
            playerZ + fz * fd - fx * Gfx.GUN_X
        };
    }

    private double[] rayDir(double yawDeg, double pitchDeg) {
        double yaw = Math.toRadians(yawDeg), pitch = Math.toRadians(pitchDeg);
        return new double[]{Math.sin(yaw) * Math.cos(pitch), -Math.sin(pitch), Math.cos(yaw) * Math.cos(pitch)};
    }

    private double hitDist = 0;

    private Enemy findTarget(double[] dir, double range, double cx, double cy, double cz, List<Enemy> enemies) {
        Enemy best = null;
        double bestT = range;
        boolean bestHead = false;
        for (Enemy e : enemies) {
            if (!e.alive || e.spawnTimer > 0 || e.hiddenTimer > 0 || !e.model.isVisible()) continue;
            double ex = e.model.getTranslateX(), ez = e.model.getTranslateZ();
            double th = raySphere(cx, cy, cz, dir, ex, Constants.GROUND_Y - 100 * e.sy, ez, 16 * e.sx);
            double tc = raySphere(cx, cy, cz, dir, ex, Constants.GROUND_Y - 62 * e.sy, ez, 30 * e.sx);
            double tl = raySphere(cx, cy, cz, dir, ex, Constants.GROUND_Y - 32 * e.sy, ez, 24 * e.sx);
            double tb = tc < 0 ? tl : (tl < 0 ? tc : Math.min(tc, tl));
            double t;
            boolean head;
            if (th >= 0 && (tb < 0 || th <= tb + 25 * e.sy)) {
                t = th; head = true;
            } else if (tb >= 0) {
                t = tb; head = false;
            } else continue;
            if (t < bestT) {
                bestT = t; best = e; bestHead = head;
            }
        }
        hitDist = bestT;
        hitHead = bestHead;
        return best;
    }

    private static double raySphere(double ox, double oy, double oz, double[] d, double sx, double sy, double sz, double r) {
        double lx = sx - ox, ly = sy - oy, lz = sz - oz;
        double t = lx * d[0] + ly * d[1] + lz * d[2];
        if (t <= 0) return -1;
        double perp2 = lx * lx + ly * ly + lz * lz - t * t;
        if (perp2 > r * r) return -1;
        return Math.max(0.1, t - Math.sqrt(r * r - perp2));
    }

    private void damageEnemy(Enemy e, double dmg, double hx, double hy, double hz, double[] dir, boolean head) {
        if (!e.alive) return;
        e.health -= dmg;
        e.stagger = 0.22 * e.staggerMul;
        e.alert = 15;
        if (e.stagger > 0.1 && e.type == com.blackout.game.core.Constants.Z_WALKER) e.windup = -1;
        burst(hx, hy, hz, head ? 9 : 5, 200, 2.2, 1.0, 500, true, dir, Mats.BLOOD);
        if (e.health <= 0) killEnemy(e, hx, hy, hz, dir);
    }

    private void killEnemy(Enemy e, double hx, double hy, double hz, double[] dir) {
        e.alive = false;
        e.deathDir = Constants.RNG.nextBoolean() ? 1 : -1;
        burst(hx, hy, hz, 14, 260, 2.6, 1.2, 500, true, dir, Mats.BLOOD);
        // Play zombie death sound
        audioManager.playZombieSound(e.type, "death");
        if (e.type == com.blackout.game.core.Constants.Z_SMOKER) {
            SmokeCloud c = new SmokeCloud();
            c.x = e.model.getTranslateX();
            c.z = e.model.getTranslateZ();
            c.r = 70; c.life = 4;
            smokeClouds.add(c);
        }
    }

    private void spawnTracer(double sx, double sy, double sz, double ex, double ey, double ez) {
        double dx = ex - sx, dy = ey - sy, dz = ez - sz, len = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (len < 2) return;
        Box b = new Box(0.9, 0.9, len);
        b.setMaterial(Mats.TRACER);
        b.setMouseTransparent(true);
        b.setTranslateX((sx + ex) / 2);
        b.setTranslateY((sy + ey) / 2);
        b.setTranslateZ((sz + ez) / 2);
        b.getTransforms().setAll(
                new Rotate(Math.toDegrees(Math.atan2(dx, dz)), Rotate.Y_AXIS),
                new Rotate(Math.toDegrees(Math.asin(-dy / len)), Rotate.X_AXIS));
        Tracer t = new Tracer();
        t.node = b;
        t.life = 0.07;
        tracers.add(t);
        world.getChildren().add(b);
    }

    private void emit(double x, double y, double z, double vx, double vy, double vz, double life,
            double gravity, boolean bounce, boolean blood, double grow, PhongMaterial mat) {
        if (particles.size() >= 380) {
            Particle old = particles.remove(0);
            world.getChildren().remove(old.node);
        }
        Particle p = new Particle();
        p.node = new Box(1, 1, 1);
        p.node.setMaterial(mat);
        p.node.setTranslateX(x);
        p.node.setTranslateY(y);
        p.node.setTranslateZ(z);
        p.node.setMouseTransparent(true);
        p.vx = vx; p.vy = vy; p.vz = vz;
        p.life = life; p.maxLife = life;
        p.fade = Math.min(0.4, life * 0.5);
        p.gravity = gravity;
        p.bounce = bounce;
        p.blood = blood;
        p.grow = grow;
        p.moving = true;
        particles.add(p);
        world.getChildren().add(p.node);
    }

    private void burst(double x, double y, double z, int count, double speed, double size, double life,
            double gravity, boolean blood, double[] dir, PhongMaterial mat) {
        for (int i = 0; i < count; i++) {
            double vx = (Constants.RNG.nextDouble() - 0.5) * speed, vy = (Constants.RNG.nextDouble() - 0.7) * speed, vz = (Constants.RNG.nextDouble() - 0.5) * speed;
            if (dir != null) {
                vx += dir[0] * speed * 0.5;
                vy += dir[1] * speed * 0.5;
                vz += dir[2] * speed * 0.5;
            }
            emit(x, y, z, vx, vy, vz, life * (0.6 + Constants.RNG.nextDouble() * 0.8), gravity, false, blood, 0, mat);
        }
    }

    // Getters for Main
    public WeaponType getCurrentWeapon() { return currentWeapon; }
    public int getMagazineAmmo(WeaponType w) { return magazineAmmo.get(w); }
    public int getReserveAmmo(WeaponType w) { return reserveAmmo.get(w); }
    public double getCameraRecoil() { return cameraRecoil; }
    public double getWeaponKick() { return weaponKick; }
    public double getDamageFlash() { return damageFlash; }
    public double getChoke() { return choke; }
    public boolean isThrowing() { return throwing; }
    public double getThrowT() { return throwT; }
    public boolean isUsingMed() { return usingMed; }
    public int getHeldItem() { return heldItem; }
    public void setHeldItem(int v) { heldItem = v; }
    public double getReloadTimer() { return reloadTimer; }
    public void setMuzzleFlash(Group g, PointLight l, double y, double z) { muzzleFlash = g; muzzleLight = l; muzzleLocalY = y; muzzleLocalZ = z; }
}