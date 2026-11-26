package com.blackout.game.systems;

import com.blackout.game.core.Gfx;
import com.blackout.game.entities.Rig;
import com.blackout.game.systems.ReloadPoses;
import com.blackout.game.entities.WeaponType;
import com.blackout.game.effects.Particle;

import javafx.scene.Group;
import javafx.scene.PointLight;
import javafx.scene.transform.Rotate;

import java.util.List;

/**
 * Handles player animation: movement, aiming, reloading, climbing, weapon sway.
 * Extracted from Main.java to reduce god-class size.
 */
public final class AnimationController {

    private final Rig playerRig;
    private final Group weaponModel;
    private final Group muzzleFlash;
    private final Rotate weaponPitch;

    // Animation state
    private double animationTime = 0;
    private double walkPhase = 0, climbPhase = 0;
    private double moveBlend = 0, aimBlend = 0, crouchBlend = 0;
    private double airBlend = 0, climbBlend = 0, sprintBlend = 0;
    private double swapBlend = 0, landBlend = 0;
    private double weaponKick = 0;
    private double stepTimer = 0;
    private double reloadTimer = 0, reloadDur = 1;
    private double throwT = 0, medT = 0;
    private double hitReact = 0, hitSide = 0;
    private double reachBlend = 0;

    // Input state
    private boolean isAiming = false, sprinting = false, crouched = false, climbing = false;
    private boolean throwing = false, threw = false, usingMed = false, firing = false;
    private int heldItem = 0, prevHeld = 0;
    private WeaponType currentWeapon = WeaponType.PISTOL;

    // References
    private final java.util.function.DoubleSupplier getFeetY;
    private final java.util.function.DoubleSupplier getPlayerX;
    private final java.util.function.DoubleSupplier getPlayerZ;
    private final java.util.function.Consumer<Particle> emitParticle;
    private final java.util.function.BooleanSupplier isGrounded;
    private final java.util.function.DoubleSupplier getCameraPitch;
    private final java.util.function.DoubleSupplier getStrafeInput;
    private final java.util.function.Consumer<Double> addShake;

    public AnimationController(Rig playerRig, Group weaponModel, Group muzzleFlash, Rotate weaponPitch,
            java.util.function.DoubleSupplier getFeetY,
            java.util.function.DoubleSupplier getPlayerX,
            java.util.function.DoubleSupplier getPlayerZ,
            java.util.function.Consumer<Particle> emitParticle,
            java.util.function.BooleanSupplier isGrounded,
            java.util.function.DoubleSupplier getCameraPitch,
            java.util.function.DoubleSupplier getStrafeInput,
            java.util.function.Consumer<Double> addShake) {
        this.playerRig = playerRig;
        this.weaponModel = weaponModel;
        this.muzzleFlash = muzzleFlash;
        this.weaponPitch = weaponPitch;
        this.getFeetY = getFeetY;
        this.getPlayerX = getPlayerX;
        this.getPlayerZ = getPlayerZ;
        this.emitParticle = emitParticle;
        this.isGrounded = isGrounded;
        this.getCameraPitch = getCameraPitch;
        this.getStrafeInput = getStrafeInput;
        this.addShake = addShake;
    }

    public void update(double dt, boolean moving, boolean sprinting, double walkDir) {
        this.sprinting = sprinting;
        animationTime += dt;

        // Decay values
        cameraRecoil = Math.max(0, cameraRecoil - 12 * dt);
        weaponKick = Math.max(0, weaponKick - dt * 9);
        swapBlend = Math.max(0, swapBlend - dt * 4.5);
        landBlend = Math.max(0, landBlend - dt * 3.5);
        hitReact = Math.max(0, hitReact - dt * 3.2);
        if (muzzleFlashTimer > 0) {
            muzzleFlashTimer -= dt;
            if (muzzleFlashTimer <= 0) {
                muzzleFlash.setVisible(false);
                muzzleLight.setLightOn(false);
            }
        }

        double k = Math.min(1, dt * 10);
        moveBlend += ((moving ? 1 : 0) - moveBlend) * k;
        aimBlend += ((isAiming ? 1 : 0) - aimBlend) * k;
        crouchBlend += ((crouched ? 1 : 0) - crouchBlend) * Math.min(1, dt * 9);
        airBlend += (((!isGrounded.getAsBoolean() && !climbing) ? 1 : 0) - airBlend) * Math.min(1, dt * 12);
        climbBlend += ((climbing ? 1 : 0) - climbBlend) * k;
        sprintBlend += ((sprinting ? 1 : 0) - sprintBlend) * k;

        if (moving && !climbing) {
            walkPhase += dt * (sprinting ? 13 : crouched ? 6.5 : 9.5);
        }
        if (climbing) {
            climbPhase += dt * 11;
        }

        Rig r = playerRig;
        double gnd = 1 - airBlend;
        double amp = (sprinting ? 42 : crouched ? 18 : 27) * moveBlend * walkDir * gnd;
        double sw = Math.sin(walkPhase) * amp;
        double kneeAmp = (sprinting ? 62 : crouched ? 22 : 42) * moveBlend * gnd;
        double cs = Math.sin(climbPhase) * climbBlend;
        double hipBase = 55 * crouchBlend + 38 * airBlend + 28 * landBlend;
        double kneeBase = -110 * crouchBlend - 75 * airBlend - 55 * landBlend;
        r.lHipR.setAngle(sw + hipBase + 45 * cs);
        r.rHipR.setAngle(-sw + hipBase - 45 * cs);
        r.lKneeR.setAngle(-Math.max(0, Math.cos(walkPhase)) * kneeAmp + kneeBase - 70 * Math.max(0, cs));
        r.rKneeR.setAngle(-Math.max(0, -Math.cos(walkPhase)) * kneeAmp + kneeBase - 70 * Math.max(0, -cs));
        r.root.setTranslateY(17.9 * crouchBlend + 6 * landBlend);

        double breath = Math.sin(animationTime * 2.2);
        double lean = getCameraPitch.getAsDouble() * 0.35 - 2 * moveBlend - 9 * sprintBlend + weaponKick * 4 + breath * 0.5 - aimBlend
                - 11 * crouchBlend - 9 * landBlend - 14 * climbBlend;
        r.lean.setAngle(lean + 13 * hitReact);
        r.twist.setAngle(Math.sin(walkPhase) * 2.5 * moveBlend + cs * 4 + hitSide * 10 * hitReact);
        r.headX.setAngle(-(lean - getCameraPitch.getAsDouble() * 0.35) * 0.6 + getCameraPitch.getAsDouble() * 0.45);
        r.headZ.setAngle(Math.sin(animationTime * 0.8) * 0.8);

        double[] p = armPose();
        double ap = getCameraPitch.getAsDouble() * 0.65, kick = weaponKick * 6, aimUp = aimBlend * 6;
        double rs = p[0] + ap + kick + aimUp + breath * 0.8 - 28 * sprintBlend;
        double re = p[1] - kick * 0.4 + 12 * sprintBlend;
        double ls = p[3] + ap + kick * 0.8 + aimUp + breath * 0.8 - 28 * sprintBlend;
        double le = p[4], ry = p[2], ly = p[5], reloadTilt = 0;
        if (reloadTimer > 0) {
            double[] rp = ReloadPoses.pose(currentWeapon, 1 - reloadTimer / reloadDur);
            ls += rp[ReloadPoses.LEFT_SHOULDER];
            le += rp[ReloadPoses.LEFT_ELBOW];
            ly += rp[ReloadPoses.LEFT_YAW];
            rs += rp[ReloadPoses.RIGHT_SHOULDER];
            re += rp[ReloadPoses.RIGHT_ELBOW];
            reloadTilt = rp[ReloadPoses.WEAPON_TILT];
        }
        if (throwing) {
            double a;
            if (throwT < 0.28) a = lerp(0, -115, throwT / 0.28);
            else if (throwT < 0.4) a = lerp(-115, 70, (throwT - 0.28) / 0.12);
            else a = lerp(70, 0, Math.min(1, (throwT - 0.4) / 0.25));
            rs += a;
        }
        if (usingMed) {
            double pul = Math.sin(medT * 9) * 3;
            rs += pul; ls += pul;
        }
        reachBlend += ((/* lock check */ false) ? 1 : 0 - reachBlend) * Math.min(1, dt * 14);
        if (reachBlend > 0.001) {
            rs = lerp(rs, 84, reachBlend);
            re = lerp(re, 14, reachBlend);
            ry = lerp(ry, 0, reachBlend);
        }
        rs = lerp(rs, 165 + 12 * Math.sin(climbPhase + 1), climbBlend);
        ls = lerp(ls, 165 + 12 * Math.sin(climbPhase), climbBlend);
        re = lerp(re, 8, climbBlend);
        le = lerp(le, 8, climbBlend);
        rs += 24 * airBlend;
        ls += 24 * airBlend;
        r.rShR.setAngle(rs);
        r.rElbR.setAngle(re);
        r.rShYaw.setAngle(ry);
        r.lShR.setAngle(ls);
        r.lElbR.setAngle(le);
        r.lShYaw.setAngle(ly);

        if (weaponModel != null) {
            double bobX = Math.sin(walkPhase * 2) * 0.9 * moveBlend;
            double bobY = Math.abs(Math.cos(walkPhase)) * 1.6 * moveBlend;
            double back = currentWeapon == WeaponType.SHOTGUN ? 8 : 5;
            weaponModel.setTranslateX(Gfx.GUN_X + bobX - aimBlend * 2);
            weaponModel.setTranslateY(Gfx.GUN_Y + breath * 0.6 + bobY - weaponKick * 1.4 + swapBlend * 16 + 5 * sprintBlend);
            weaponModel.setTranslateZ(Gfx.GUN_Z - weaponKick * back - 6 * sprintBlend);
            weaponPitch.setAngle(getCameraPitch.getAsDouble() * 0.65 + weaponKick * (currentWeapon == WeaponType.SHOTGUN ? 16 : 9) + reloadTilt - 15 * sprintBlend);
        }

        // Footstep particles
        if (moving && isGrounded.getAsBoolean() && !climbing) {
            stepTimer -= dt;
            if (stepTimer <= 0) {
                stepTimer = sprinting ? 0.17 : crouched ? 0.45 : 0.3;
                double px = getPlayerX.getAsDouble(), pz = getPlayerZ.getAsDouble(), fy = getFeetY.getAsDouble();
                emitParticle.accept(new Particle(
                        px + (Math.random() - 0.5) * 10, fy - 3, pz + (Math.random() - 0.5) * 10,
                        (Math.random() - 0.5) * 14, -14, (Math.random() - 0.5) * 14,
                        0.6, 0, false, false, 0, null
                ));
            }
        }
    }

    // Muzzle flash timer and light (need to be tracked)
    private double muzzleFlashTimer = 0;
    private PointLight muzzleLight;
    private double cameraRecoil = 0;

    public void setMuzzleLight(PointLight l) { muzzleLight = l; }
    public void setMuzzleFlashTimer(double v) { muzzleFlashTimer = v; }
    public void setCameraRecoil(double v) { cameraRecoil = v; }
    public void setWeaponKick(double v) { weaponKick = v; }
    public void setReloadTimer(double v) { reloadTimer = v; }
    public void setReloadDur(double v) { reloadDur = v; }
    public void setThrowing(boolean v) { throwing = v; }
    public void setThrew(boolean v) { threw = v; }
    public void setThrowT(double v) { throwT = v; }
    public void setUsingMed(boolean v) { usingMed = v; }
    public void setMedT(double v) { medT = v; }
    public void setHitReact(double v) { hitReact = v; }
    public void setHitSide(double v) { hitSide = v; }
    public void setReachBlend(double v) { reachBlend = v; }
    public void setHeldItem(int v) { heldItem = v; }
    public void setPrevHeld(int v) { prevHeld = v; }
    public void setCurrentWeapon(WeaponType v) { currentWeapon = v; }
    public void setIsAiming(boolean v) { isAiming = v; }
    public void setCrouched(boolean v) { crouched = v; }
    public void setClimbing(boolean v) { climbing = v; }
    public void setFiring(boolean v) { firing = v; }

    public double getWalkPhase() { return walkPhase; }
    public double getMoveBlend() { return moveBlend; }
    public double getWeaponKick() { return weaponKick; }
    public double getSwapBlend() { return swapBlend; }
    public double getCameraRecoil() { return cameraRecoil; }

    private double[] armPose() {
        if (heldItem == 1) {
            return new double[]{40, 60, -8, 18, 25, 6};
        }
        if (heldItem == 2) {
            return new double[]{50, 65, -14, 50, 65, 22};
        }
        switch (currentWeapon) {
            case PISTOL:
                return new double[]{55, 38, -12, 55, 38, 32};
            case ASSAULT_RIFLE:
                return new double[]{38, 58, -8, 78, 6, 38};
            default:
                return new double[]{40, 55, -8, 76, 8, 36};
        }
    }

    private static double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }

    // Simple Particle class for footstep emission
    public static class Particle {
        public double x, y, z, vx, vy, vz, life, gravity;
        public boolean bounce, blood;
        public PhongMaterial material;

        public Particle(double x, double y, double z, double vx, double vy, double vz,
                double life, double gravity, boolean bounce, boolean blood, double grow, PhongMaterial material) {
            this.x = x; this.y = y; this.z = z;
            this.vx = vx; this.vy = vy; this.vz = vz;
            this.life = life; this.gravity = gravity;
            this.bounce = bounce; this.blood = blood;
            this.material = material;
        }
    }
}