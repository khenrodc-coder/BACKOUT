package com.blackout.game.systems;

import com.blackout.game.core.Constants;
import com.blackout.game.core.Settings;

import javafx.scene.PerspectiveCamera;
import javafx.scene.transform.Rotate;

/**
 * Handles camera logic: first/third person, FOV, smoothing, shake, roll.
 * Extracted from Main.java to reduce god-class size.
 */
public final class CameraController {

    private final PerspectiveCamera camera;
    private final Settings settings;

    // Camera state
    private boolean firstPerson = false;
    private double currentFov = 32;
    private double camRoll = 0;
    private double shake = 0;
    private double smoothedYaw = 0, smoothedPitch = -10.5;
    private double smoothedCamX = 0, smoothedCamY = 0, smoothedCamZ = 0;
    private double cameraYaw = 0, cameraPitch = -10.5;
    private double mouseX = 0, mouseY = 0;

    // References to player state (updated externally)
    private double playerYaw = 0;
    private double playerX = 0, playerZ = 0, feetY = Constants.GROUND_Y;
    private double crouchBlend = 0, insideBlend = 0, insideH = 0;
    private double walkPhase = 0, moveBlend = 0, aimBlend = 0;
    private double sprintBlend = 0, landBlend = 0, climbBlend = 0;
    private boolean isAiming = false, sprintingNow = false, crouched = false, climbing = false;
    private double cameraRecoil = 0, weaponKick = 0;
    private double strafeInput = 0;

    public CameraController(PerspectiveCamera camera, Settings settings) {
        this.camera = camera;
        this.settings = settings;
    }

    public void update(double dt) {
        // Smooth yaw/pitch
        smoothedYaw += (cameraYaw - smoothedYaw) * Math.min(1.0, dt * 20);
        smoothedPitch += ((cameraPitch + cameraRecoil) - smoothedPitch) * Math.min(1.0, dt * 20);

        double rad = Math.toRadians(smoothedYaw);
        double center = feetY - 55;
        double tx, ty, tz;

        if (firstPerson) {
            tx = playerX + Math.sin(rad) * 10;
            ty = center - 42 + 18 * crouchBlend + landBlend * 6 + Math.sin(walkPhase * 2) * 1.2 * moveBlend;
            tz = playerZ + Math.cos(rad) * 10;
        } else {
            double dst = (isAiming ? 0.6 : 1.0) * lerp(Constants.CAMERA_DISTANCE, 290, insideBlend);
            double hgt = lerp(isAiming ? 150 : 175, Math.max(175, insideH + 115), insideBlend) - 14 * crouchBlend;
            double shoulder = isAiming ? 28 : 0;
            tx = playerX - Math.sin(rad) * dst + Math.cos(rad) * shoulder;
            ty = center - hgt + Math.sin(walkPhase * 2) * 1.6 * moveBlend;
            tz = playerZ - Math.cos(rad) * dst - Math.sin(rad) * shoulder;
        }

        double lf = Math.min(1.0, dt * 16);
        smoothedCamX += (tx - smoothedCamX) * lf;
        smoothedCamY += (ty - smoothedCamY) * lf;
        smoothedCamZ += (tz - smoothedCamZ) * lf;

        double sx = shake > 0 ? (Math.random() - 0.5) * shake : 0;
        double sy = shake > 0 ? (Math.random() - 0.5) * shake : 0;
        shake = Math.max(0, shake - dt * 20);

        camera.setTranslateX(smoothedCamX + sx);
        camera.setTranslateY(smoothedCamY + sy);
        camera.setTranslateZ(smoothedCamZ);

        double fovT = firstPerson ? (isAiming ? 55 : 75) : (isAiming ? 24 : (sprintingNow ? 37 : 32));
        if (climbing) fovT += 4;
        currentFov += (fovT * settings.fovScale - currentFov) * Math.min(1.0, dt * 6);
        camera.setFieldOfView(currentFov);

        camRoll += (-strafeInput * 1.8 - camRoll) * Math.min(1.0, dt * 6);
        camera.getTransforms().setAll(
                new Rotate(smoothedYaw, Rotate.Y_AXIS),
                new Rotate(smoothedPitch, Rotate.X_AXIS),
                new Rotate(camRoll, Rotate.Z_AXIS));
    }

    public void toggleCameraMode() {
        firstPerson = !firstPerson;
    }

    public void addShake(double amount) {
        shake = Math.max(shake, amount);
    }

    public void addRecoil(double amount) {
        cameraRecoil = Math.min(cameraRecoil + amount, 9);
    }

    // Setters for player state
    public void setPlayerPosition(double x, double z, double feetY) {
        this.playerX = x; this.playerZ = z; this.feetY = feetY;
    }

    public void setPlayerYaw(double yaw) { this.playerYaw = yaw; }
    public void setCameraRotation(double yaw, double pitch) { this.cameraYaw = yaw; this.cameraPitch = pitch; }
    public void setMousePosition(double x, double y) { this.mouseX = x; this.mouseY = y; }

    public void setFirstPerson(boolean fp) { this.firstPerson = fp; }
    public boolean isFirstPerson() { return firstPerson; }

    public void setCrouchBlend(double v) { crouchBlend = v; }
    public void setInsideBlend(double v) { insideBlend = v; }
    public void setInsideH(double v) { insideH = v; }
    public void setWalkPhase(double v) { walkPhase = v; }
    public void setMoveBlend(double v) { moveBlend = v; }
    public void setAimBlend(double v) { aimBlend = v; }
    public void setSprintBlend(double v) { sprintBlend = v; }
    public void setLandBlend(double v) { landBlend = v; }
    public void setClimbBlend(double v) { climbBlend = v; }
    public void setIsAiming(boolean v) { isAiming = v; }
    public void setSprintingNow(boolean v) { sprintingNow = v; }
    public void setCrouched(boolean v) { crouched = v; }
    public void setClimbing(boolean v) { climbing = v; }
    public void setCameraRecoil(double v) { cameraRecoil = v; }
    public void setWeaponKick(double v) { weaponKick = v; }
    public void setStrafeInput(double v) { strafeInput = v; }

    public double getCurrentFov() { return currentFov; }
    public double getSmoothedYaw() { return smoothedYaw; }
    public double getSmoothedPitch() { return smoothedPitch; }
    public double getCamRoll() { return camRoll; }
    public double getShake() { return shake; }

    private static double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }
}