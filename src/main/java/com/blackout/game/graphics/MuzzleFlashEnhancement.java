package com.blackout.game.graphics;

import com.blackout.game.core.Constants;
import com.blackout.game.core.Gfx;
import com.blackout.game.entities.WeaponType;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import javafx.scene.Group;
import javafx.scene.PointLight;
import javafx.scene.paint.Color;
import javafx.scene.paint.PhongMaterial;
import javafx.scene.shape.Box;
import javafx.scene.shape.Cylinder;
import javafx.scene.shape.Sphere;
import javafx.scene.transform.Rotate;

/**
 * Enhanced muzzle flash system with:
 * - Dynamic light that casts on nearby geometry
 * - Multi-layer particle burst (core, flash, smoke, sparks)
 * - Screen shake integration
 * - Weapon-specific flash patterns
 * - Light falloff calculation
 */
public final class MuzzleFlashEnhancement {

    private static final int MAX_FLASH_PARTICLES = 50;
    private static final double FLASH_DURATION = 0.08;

    private final Group world;
    private final Random rng = new Random();

    private final List<FlashInstance> activeFlashes = new ArrayList<>();

    // Pre-built flash components per weapon type
    private final FlashTemplate pistolFlash;
    private final FlashTemplate rifleFlash;
    private final FlashTemplate shotgunFlash;

    public MuzzleFlashEnhancement(Group world) {
        this.world = world;

        // Build flash templates
        pistolFlash = buildFlashTemplate(WeaponType.PISTOL);
        rifleFlash = buildFlashTemplate(WeaponType.ASSAULT_RIFLE);
        shotgunFlash = buildFlashTemplate(WeaponType.SHOTGUN);
    }

    private FlashTemplate buildFlashTemplate(WeaponType weapon) {
        FlashTemplate t = new FlashTemplate();
        t.weapon = weapon;

        switch (weapon) {
            case PISTOL -> {
                t.coreSize = 3.0;
                t.flashScale = 0.9;
                t.flashColor = Color.rgb(255, 250, 215);
                t.outerColor = Color.rgb(255, 150, 40);
                t.lightColor = Color.rgb(255, 170, 80);
                t.lightRange = 400;
                t.particleCount = 12;
                t.smokeAmount = 3;
            }
            case ASSAULT_RIFLE -> {
                t.coreSize = 4.0;
                t.flashScale = 1.25;
                t.flashColor = Color.rgb(255, 255, 220);
                t.outerColor = Color.rgb(255, 180, 50);
                t.lightColor = Color.rgb(255, 200, 100);
                t.lightRange = 550;
                t.particleCount = 18;
                t.smokeAmount = 5;
            }
            case SHOTGUN -> {
                t.coreSize = 5.5;
                t.flashScale = 1.7;
                t.flashColor = Color.rgb(255, 240, 200);
                t.outerColor = Color.rgb(255, 120, 30);
                t.lightColor = Color.rgb(255, 150, 60);
                t.lightRange = 650;
                t.particleCount = 25;
                t.smokeAmount = 8;
            }
        }
        return t;
    }

    public void triggerFlash(WeaponType weapon, double x, double y, double z,
            double yaw, double pitch, java.util.function.Consumer<Double> addScreenShake) {
        FlashTemplate template = getTemplate(weapon);
        FlashInstance flash = new FlashInstance(template, x, y, z, yaw, pitch);
        activeFlashes.add(flash);
        world.getChildren().add(flash.root);

        // Trigger screen shake
        if (addScreenShake != null) {
            double shakeAmt = switch (weapon) {
                case PISTOL -> 1.8;
                case ASSAULT_RIFLE -> 1.2;
                case SHOTGUN -> 5.0;
            };
            addScreenShake.accept(shakeAmt);
        }
    }

    private FlashTemplate getTemplate(WeaponType weapon) {
        return switch (weapon) {
            case PISTOL -> pistolFlash;
            case ASSAULT_RIFLE -> rifleFlash;
            case SHOTGUN -> shotgunFlash;
        };
    }

    public void update(double dt) {
        for (java.util.Iterator<FlashInstance> it = activeFlashes.iterator(); it.hasNext();) {
            FlashInstance f = it.next();
            f.life -= dt;
            if (f.life <= 0) {
                world.getChildren().remove(f.root);
                it.remove();
                continue;
            }
            f.update(dt);
        }
    }

    public void clear() {
        for (FlashInstance f : activeFlashes) {
            world.getChildren().remove(f.root);
        }
        activeFlashes.clear();
    }

    private static class FlashTemplate {
        WeaponType weapon;
        double coreSize;
        double flashScale;
        Color flashColor;
        Color outerColor;
        Color lightColor;
        double lightRange;
        int particleCount;
        int smokeAmount;
    }

    private class FlashInstance {
        final FlashTemplate template;
        final Group root = new Group();
        final Group coreGroup = new Group();
        final Group particleGroup = new Group();
        final PointLight light;
        double life = FLASH_DURATION;
        private double rotation = 0;

        public FlashInstance(FlashTemplate template, double x, double y, double z, double yaw, double pitch) {
            this.template = template;
            root.setMouseTransparent(true);
            root.getChildren().addAll(coreGroup, particleGroup);

            // Build core flash geometry
            buildCoreFlash(x, y, z, yaw, pitch);

            // Dynamic light
            light = new PointLight(template.lightColor);
            light.setTranslateX(x);
            light.setTranslateY(y);
            light.setTranslateZ(z);
            light.setMaxRange(template.lightRange);
            root.getChildren().add(light);

            // Particles
            spawnParticles(x, y, z, yaw, pitch);

            // Random rotation for variety
            rotation = rng.nextDouble() * 360;
            coreGroup.setRotationAxis(Rotate.Z_AXIS);
            coreGroup.setRotate(rotation);
        }

        private void buildCoreFlash(double x, double y, double z, double yaw, double pitch) {
            double s = template.flashScale;

            // Core sphere
            Sphere core = new Sphere(template.coreSize * s, 12);
            PhongMaterial coreMat = Gfx.glow(template.flashColor);
            core.setMaterial(coreMat);
            core.setTranslateX(x);
            core.setTranslateY(y);
            core.setTranslateZ(z);
            core.setCullFace(javafx.scene.shape.CullFace.NONE);
            coreGroup.getChildren().add(core);

            // Cross flash quads
            Box flash1 = new Box(2 * s, 24 * s, 36 * s);
            flash1.setMaterial(Gfx.glow(template.outerColor));
            flash1.setTranslateX(x);
            flash1.setTranslateY(y);
            flash1.setTranslateZ(z);
            flash1.setMouseTransparent(true);
            coreGroup.getChildren().add(flash1);

            Box flash2 = new Box(24 * s, 2 * s, 36 * s);
            flash2.setMaterial(Gfx.glow(template.outerColor));
            flash2.setTranslateX(x);
            flash2.setTranslateY(y);
            flash2.setTranslateZ(z);
            flash2.setMouseTransparent(true);
            coreGroup.getChildren().add(flash2);

            // Elongated core
            Box coreBeam = new Box(1.2 * s, 7 * s, 42 * s);
            coreBeam.setMaterial(Gfx.glow(template.flashColor));
            coreBeam.setTranslateX(x);
            coreBeam.setTranslateY(y);
            coreBeam.setTranslateZ(z + 21 * s);
            coreGroup.getChildren().add(coreBeam);
        }

        private void spawnParticles(double x, double y, double z, double yaw, double pitch) {
            double fx = Math.sin(Math.toRadians(yaw));
            double fy = -Math.sin(Math.toRadians(pitch));
            double fz = Math.cos(Math.toRadians(yaw));

            // Spark particles
            for (int i = 0; i < template.particleCount; i++) {
                SparkParticle sp = new SparkParticle();
                sp.life = 0.2 + rng.nextDouble() * 0.15;
                sp.maxLife = sp.life;
                sp.x = x + fx * 5;
                sp.y = y + fy * 5;
                sp.z = z + fz * 5;

                double spread = 0.5;
                sp.vx = fx * (80 + rng.nextDouble() * 120) + (rng.nextDouble() - 0.5) * 100 * spread;
                sp.vy = fy * (80 + rng.nextDouble() * 120) + (rng.nextDouble() - 0.5) * 100 * spread + 20;
                sp.vz = fz * (80 + rng.nextDouble() * 120) + (rng.nextDouble() - 0.5) * 100 * spread;

                Group sparkGroup = new Group();
            Box spark = new Box(1.5, 1.5, 1.5);
            sparkGroup.getChildren().add(spark);
            sparkGroup.setMouseTransparent(true);
            sp.node = sparkGroup;
            spark.setMaterial(Gfx.glow(Color.rgb(255, 220 + rng.nextInt(35), 120)));
            spark.setTranslateX(sp.x);
            spark.setTranslateY(sp.y);
            spark.setTranslateZ(sp.z);
            spark.setMouseTransparent(true);
            particleGroup.getChildren().add(sparkGroup);
            }

            // Smoke particles
            for (int i = 0; i < template.smokeAmount; i++) {
                SmokeParticle sm = new SmokeParticle();
                sm.life = 0.4 + rng.nextDouble() * 0.3;
                sm.maxLife = sm.life;
                sm.x = x + (rng.nextDouble() - 0.5) * 10;
                sm.y = y + (rng.nextDouble() - 0.5) * 10;
                sm.z = z + (rng.nextDouble() - 0.5) * 10;
                sm.vx = (rng.nextDouble() - 0.5) * 20;
                sm.vy = 10 + rng.nextDouble() * 30;
                sm.vz = (rng.nextDouble() - 0.5) * 20;

                Group puffGroup = new Group();
                Box puff = new Box(4, 4, 4);
                puffGroup.getChildren().add(puff);
                puffGroup.setMouseTransparent(true);
                sm.node = puffGroup;
                PhongMaterial mat = new PhongMaterial(Color.rgb(70, 70, 70, 0.4));
                mat.setSpecularColor(Color.BLACK);
                puff.setMaterial(mat);
                puff.setTranslateX(sm.x);
                puff.setTranslateY(sm.y);
                puff.setTranslateZ(sm.z);
                puff.setMouseTransparent(true);
                puff.setCullFace(javafx.scene.shape.CullFace.NONE);
                particleGroup.getChildren().add(puffGroup);
            }

            // Brass casing ejection
            Box brass = new Box(1.5, 3, 1.5);
            brass.setMaterial(Gfx.mat(Color.rgb(200, 160, 60), Color.WHITE, 90));
            brass.setTranslateX(x + fx * 10 + fy * 8);
            brass.setTranslateY(y - 4);
            brass.setTranslateZ(z + fz * 10 - fx * 8);
            brass.setMouseTransparent(true);
            particleGroup.getChildren().add(brass);
        }

        public void update(double dt) {
            life -= dt;
            double progress = 1 - life / FLASH_DURATION;

            // Fade core
            double alpha = Math.max(0, 1 - progress * 2);
            for (javafx.scene.Node n : coreGroup.getChildren()) {
                if (n instanceof javafx.scene.shape.Shape3D s) {
                    PhongMaterial mat = (PhongMaterial) s.getMaterial();
                    if (mat != null) {
                        Color c = mat.getDiffuseColor();
                        mat.setDiffuseColor(new Color(c.getRed(), c.getGreen(), c.getBlue(), alpha));
                    }
                }
            }

            // Fade particles
            for (javafx.scene.Node n : particleGroup.getChildren()) {
                if (n instanceof javafx.scene.shape.Shape3D s) {
                    PhongMaterial mat = (PhongMaterial) s.getMaterial();
                    if (mat != null) {
                        Color c = mat.getDiffuseColor();
                        mat.setDiffuseColor(new Color(c.getRed(), c.getGreen(), c.getBlue(), alpha));
                    }
                }
            }

            // Dim light
            light.setColor(Color.color(
                    template.lightColor.getRed() * alpha,
                    template.lightColor.getGreen() * alpha,
                    template.lightColor.getBlue() * alpha
            ));

            // Rotate core slightly for dynamic feel
            rotation += dt * 720;
            coreGroup.setRotate(rotation);
        }
    }

    private static class SparkParticle {
        Group node;
        double x, y, z;
        double vx, vy, vz;
        double life, maxLife;
    }

    private static class SmokeParticle {
        Group node;
        double x, y, z;
        double vx, vy, vz;
        double life, maxLife;
    }
}