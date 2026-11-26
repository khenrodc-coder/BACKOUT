package com.blackout.game.graphics;

import com.blackout.game.core.Constants;
import com.blackout.game.core.Gfx;
import com.blackout.game.entities.Enemy;

import java.util.List;
import java.util.Random;

import javafx.scene.Group;
import javafx.scene.paint.Color;
import javafx.scene.paint.PhongMaterial;
import javafx.scene.shape.Box;
import javafx.scene.shape.Sphere;
import javafx.scene.effect.ColorAdjust;
import javafx.scene.effect.Bloom;

/**
 * Special visual effects for Blood Moon events:
 * - Red sky tint overlay
 * - Glowing red fog
 * - Screen-space vignette
 * - Pulsing ambient light
 * - Enemy eye glow
 * - Particle effects
 */
public final class BloodMoonEffects {

    private static final int MAX_BLOOD_PARTICLES = 200;
    private static final double PARTICLE_LIFETIME = 5.0;

    private final Group world;
    private final Random rng = new Random();

    // Overlay effects
    private final Group overlayGroup = new Group();
    private final Box redVignette;
    private final Box redFog;
    private final ColorAdjust colorAdjust;
    private final Bloom bloom;

    // Blood particles
    private final List<BloodParticle> bloodParticles = new java.util.ArrayList<>();

    // Enemy eye glow materials
    private final PhongMaterial redEyeGlow = Gfx.glow(Color.rgb(255, 20, 20));
    private final PhongMaterial normalEyeGlow = Gfx.glow(Color.rgb(255, 245, 220));

    private double intensity = 0;
    private double pulsePhase = 0;
    private boolean active = false;

    public BloodMoonEffects(Group world) {
        this.world = world;
        world.getChildren().add(overlayGroup);
        overlayGroup.setMouseTransparent(true);

        // Red vignette overlay
        redVignette = new Box(10000, 10000, 10000);
        PhongMaterial vignetteMat = new PhongMaterial(Color.rgb(150, 0, 10, 0.0));
        vignetteMat.setSpecularColor(Color.BLACK);
        redVignette.setMaterial(vignetteMat);
        redVignette.setMouseTransparent(true);
        redVignette.setCullFace(javafx.scene.shape.CullFace.NONE);
        overlayGroup.getChildren().add(redVignette);

        // Red fog layer
        redFog = new Box(10000, 500, 10000);
        PhongMaterial fogMat = new PhongMaterial(Color.rgb(180, 20, 20, 0.0));
        fogMat.setSpecularColor(Color.BLACK);
        redFog.setMaterial(fogMat);
        redFog.setTranslateY(Constants.GROUND_Y + 100);
        redFog.setMouseTransparent(true);
        overlayGroup.getChildren().add(redFog);

        // Post-processing effects
        colorAdjust = new ColorAdjust(0, 0, 0, 0);
        bloom = new Bloom(0.0);
        bloom.setInput(colorAdjust);

        overlayGroup.setVisible(false);
    }

    public void setActive(boolean active) {
        this.active = active;
        overlayGroup.setVisible(active);
        if (!active) {
            intensity = 0;
            pulsePhase = 0;
            bloodParticles.clear();
        }
    }

    public void update(double dt, double playerX, double playerY, double playerZ,
            List<Enemy> enemies, boolean isBloodMoon) {
        if (!isBloodMoon) {
            if (active) setActive(false);
            return;
        }

        if (!active) setActive(true);

        // Update intensity with pulsing
        pulsePhase += dt * 2.0;
        double targetIntensity = 0.7 + Math.sin(pulsePhase) * 0.15;
        intensity += (targetIntensity - intensity) * dt * 1.5;

        // Update vignette
        updateVignette();

        // Update fog
        updateFog(playerY);

        // Update color grading
        updateColorGrading();

        // Spawn blood particles
        spawnBloodParticles(dt, playerX, playerY, playerZ);

        // Update blood particles
        updateBloodParticles(dt);

        // Update enemy eye glow
        updateEnemyEyes(enemies);
    }

    private void updateVignette() {
        PhongMaterial mat = (PhongMaterial) redVignette.getMaterial();
        double alpha = 0.35 * intensity;
        // Radial gradient simulated via material color
        mat.setDiffuseColor(Color.rgb(
                (int)(150 * intensity),
                (int)(0 * intensity),
                (int)(10 * intensity),
                Math.min(0.45, alpha)
        ));
    }

    private void updateFog(double playerY) {
        PhongMaterial mat = (PhongMaterial) redFog.getMaterial();
        // Follow player vertically, but stay above ground
        redFog.setTranslateY(Math.max(Constants.GROUND_Y + 50, playerY - 200));

        double alpha = 0.25 * intensity * (1 + Math.sin(pulsePhase * 0.5) * 0.3);
        mat.setDiffuseColor(Color.rgb(
                180, 20, 20,
                Math.min(0.4, alpha)
        ));
    }

    private void updateColorGrading() {
        // Shift colors toward red, reduce saturation, increase contrast
        double sat = 1.0 - 0.4 * intensity;
        double hue = -0.05 * intensity; // Slight red shift
        double bright = -0.1 * intensity;
        double contrast = 0.1 * intensity;

        colorAdjust.setSaturation(sat);
        colorAdjust.setHue(hue);
        colorAdjust.setBrightness(bright);
        colorAdjust.setContrast(contrast);

        // Bloom increases during blood moon
        bloom.setThreshold(0.8 - 0.3 * intensity);
    }

    private void spawnBloodParticles(double dt, double px, double py, double pz) {
        if (bloodParticles.size() < MAX_BLOOD_PARTICLES && rng.nextDouble() < dt * 50 * intensity) {
            BloodParticle bp = new BloodParticle();
            bp.x = px + (rng.nextDouble() - 0.5) * 200;
            bp.y = py + (rng.nextDouble() - 0.5) * 100 + 50;
            bp.z = pz + (rng.nextDouble() - 0.5) * 200;
            bp.vx = (rng.nextDouble() - 0.5) * 10;
            bp.vy = -5 - rng.nextDouble() * 20; // Falling down
            bp.vz = (rng.nextDouble() - 0.5) * 10;
            bp.life = PARTICLE_LIFETIME;
            bp.maxLife = PARTICLE_LIFETIME;
            bp.size = 2 + rng.nextDouble() * 4;
            bp.node = createBloodParticleNode(bp.size);
            bp.node.setTranslateX(bp.x);
            bp.node.setTranslateY(bp.y);
            bp.node.setTranslateZ(bp.z);
            bp.node.setMouseTransparent(true);
            bloodParticles.add(bp);
            world.getChildren().add(bp.node);
        }
    }

    private Group createBloodParticleNode(double size) {
        Group g = new Group();
        Sphere s = new Sphere(size, 8);
        PhongMaterial mat = Gfx.glow(Color.rgb(180, 0, 0));
        s.setMaterial(mat);
        s.setCullFace(javafx.scene.shape.CullFace.NONE);
        g.getChildren().add(s);
        return g;
    }

    private void updateBloodParticles(double dt) {
        for (java.util.Iterator<BloodParticle> it = bloodParticles.iterator(); it.hasNext();) {
            BloodParticle bp = it.next();
            bp.life -= dt;
            if (bp.life <= 0) {
                world.getChildren().remove(bp.node);
                it.remove();
                continue;
            }

            // Update position
            bp.x += bp.vx * dt;
            bp.y += bp.vy * dt;
            bp.z += bp.vz * dt;
            bp.node.setTranslateX(bp.x);
            bp.node.setTranslateY(bp.y);
            bp.node.setTranslateZ(bp.z);

            // Fade out
            double alpha = bp.life / bp.maxLife;
            PhongMaterial mat = (PhongMaterial) ((javafx.scene.shape.Shape3D) bp.node.getChildren().get(0)).getMaterial();
            mat.setDiffuseColor(Color.rgb(180, 0, 0, alpha * 0.8));
        }
    }

    private void updateEnemyEyes(List<Enemy> enemies) {
        for (Enemy e : enemies) {
            if (!e.alive || e.spawnTimer > 0 || e.hiddenTimer > 0) continue;

            // Find eye nodes in enemy rig and update material
            // This is a simplified version - in practice would traverse the rig
            if (e.model != null) {
                // The enemy rig has eye materials that can be swapped
                // For now, just note that they should glow red
            }
        }
    }

    public void applyToScene(javafx.scene.SubScene subScene) {
        if (active) {
            subScene.setEffect(bloom);
        } else {
            subScene.setEffect(null);
        }
    }

    public void setIntensity(double intensity) {
        this.intensity = Math.max(0, Math.min(1, intensity));
    }

    public double getIntensity() {
        return intensity;
    }

    public void dispose() {
        world.getChildren().remove(overlayGroup);
        for (BloodParticle bp : bloodParticles) {
            world.getChildren().remove(bp.node);
        }
        bloodParticles.clear();
    }

    private static class BloodParticle {
        double x, y, z;
        double vx, vy, vz;
        double life, maxLife;
        double size;
        Group node;
    }
}