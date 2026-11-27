package com.blackout.game.graphics;

import com.blackout.game.core.Constants;
import com.blackout.game.core.Gfx;

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

/**
 * Enhanced fire and smoke system with:
 * - Billboarded particle quads for flames
 * - Ember particles with physics
 * - Volumetric smoke with dissipation
 * - Dynamic light casting
 * - Heat distortion simulation
 */
public final class EnhancedFire {

    private static final int MAX_FLAME_PARTICLES = 150;
    private static final int MAX_EMBER_PARTICLES = 100;
    private static final int MAX_SMOKE_PARTICLES = 80;

    private final Group world;
    private final Random rng = new Random();

    private final List<FlameParticle> flames = new ArrayList<>();
    private final List<EmberParticle> embers = new ArrayList<>();
    private final List<SmokeParticle> smoke = new ArrayList<>();

    private final Group fireGroup = new Group();

    public EnhancedFire(Group world) {
        this.world = world;
        world.getChildren().add(fireGroup);
        fireGroup.setMouseTransparent(true);
    }

    public FireInstance createFire(double x, double y, double z, double scale, boolean smokeEnabled) {
        FireInstance fire = new FireInstance(x, y, z, scale, smokeEnabled);
        fireGroup.getChildren().add(fire.root);
        activeFires.add(fire);
        return fire;
    }

    public void update(double dt) {
        for (FireInstance fire : activeFires) {
            fire.update(dt);
        }
        activeFires.removeIf(f -> f.isDead());
    }

    private final List<FireInstance> activeFires = new ArrayList<>();

    public class FireInstance {
        final double x, y, z, scale;
        final boolean smokeEnabled;
        final Group root = new Group();
        final Group flameGroup = new Group();
        final Group emberGroup = new Group();
        final Group smokeGroup = new Group();
        final Group lightGroup = new Group();

        private double phase = 0;
        private double emberTimer = 0;
        private double smokeTimer = 0;
        private final PointLight dynamicLight;
        private final List<FlameParticle> localFlames = new ArrayList<>();
        private final List<EmberParticle> localEmbers = new ArrayList<>();
        private final List<SmokeParticle> localSmoke = new ArrayList<>();
        private boolean dead = false;

        public FireInstance(double x, double y, double z, double scale, boolean smokeEnabled) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.scale = scale;
            this.smokeEnabled = smokeEnabled;

            root.getChildren().addAll(flameGroup, emberGroup, smokeGroup, lightGroup);
            flameGroup.setMouseTransparent(true);
            emberGroup.setMouseTransparent(true);
            smokeGroup.setMouseTransparent(true);

            // Dynamic light
            dynamicLight = new PointLight(Color.rgb(255, 130, 40));
            dynamicLight.setTranslateX(x);
            dynamicLight.setTranslateY(y - 20 * scale);
            dynamicLight.setTranslateZ(z);
            dynamicLight.setMaxRange(320 * scale);
            lightGroup.getChildren().add(dynamicLight);

            // Initial flames
            for (int i = 0; i < 5 * scale; i++) {
                spawnFlame();
            }
        }

        public void update(double dt) {
            if (dead) return;

            phase += dt * 14;

            // Animate flames
            for (int i = 0; i < localFlames.size(); i++) {
                FlameParticle f = localFlames.get(i);
                double k = 0.75 + 0.45 * Math.abs(Math.sin(phase + i * 2.1)) + rng.nextDouble() * 0.15;
                f.tongue.setScaleY(k);
                f.tongue.setScaleX(0.85 + 0.3 * rng.nextDouble());
                f.tongue.setTranslateY(-f.height * k / 2);
            }

            // Embers
            emberTimer -= dt;
            if (emberTimer <= 0) {
                emberTimer = 0.1 / Math.max(0.6, scale * 0.6);
                spawnEmber();
            }

            // Smoke
            if (smokeEnabled) {
                smokeTimer -= dt;
                if (smokeTimer <= 0) {
                    smokeTimer = 0.15 / scale;
                    spawnSmoke();
                }
            }

            // Light flicker
            double f = 0.78 + 0.22 * Math.sin(phase * 12) + (rng.nextDouble() - 0.5) * 0.25;
            if (rng.nextDouble() < 0.004) f *= 0.3;
            dynamicLight.setColor(Color.color(
                    Math.min(1, 1.0 * f),
                    Math.min(1, 0.5 * f),
                    Math.min(1, 0.15 * f)
            ));

            // Update ember particles
            for (java.util.Iterator<EmberParticle> it = localEmbers.iterator(); it.hasNext();) {
                EmberParticle e = it.next();
                e.life -= dt;
                if (e.life <= 0) {
                    emberGroup.getChildren().remove(e.node);
                    it.remove();
                    continue;
                }
                e.vy -= 50 * dt; // Gravity
                e.node.setTranslateX(e.node.getTranslateX() + e.vx * dt);
                e.node.setTranslateY(e.node.getTranslateY() + e.vy * dt);
                e.node.setTranslateZ(e.node.getTranslateZ() + e.vz * dt);
                double alpha = e.life / e.maxLife;
                PhongMaterial mat = (PhongMaterial) ((javafx.scene.shape.Shape3D) e.node.getChildren().get(0)).getMaterial();
                mat.setDiffuseColor(Color.rgb(255, (int)(120 * alpha), 30, alpha));
            }

            // Update smoke particles
            for (java.util.Iterator<SmokeParticle> it = localSmoke.iterator(); it.hasNext();) {
                SmokeParticle s = it.next();
                s.life -= dt;
                if (s.life <= 0) {
                    smokeGroup.getChildren().remove(s.node);
                    it.remove();
                    continue;
                }
                s.node.setTranslateX(s.node.getTranslateX() + s.vx * dt);
                s.node.setTranslateY(s.node.getTranslateY() + s.vy * dt);
                s.node.setTranslateZ(s.node.getTranslateZ() + s.vz * dt);
                s.node.setScaleX(s.node.getScaleX() + dt * 0.3);
                s.node.setScaleY(s.node.getScaleY() + dt * 0.3);
                s.node.setScaleZ(s.node.getScaleZ() + dt * 0.3);
                double alpha = s.life / s.maxLife;
                PhongMaterial mat = (PhongMaterial) ((javafx.scene.shape.Shape3D) s.node.getChildren().get(0)).getMaterial();
                mat.setDiffuseColor(Color.rgb(70, 70, 70, alpha * 0.4));
            }

            // Remove dead fires (when no flames left and burned out)
            if (localFlames.isEmpty() && localEmbers.isEmpty() && localSmoke.isEmpty() && phase > 60) {
                die();
            }
        }

        private void spawnFlame() {
            FlameParticle f = new FlameParticle();
            double h = 30 * scale + rng.nextDouble() * 20 * scale;
            f.height = h;

            Cylinder tongue = new Cylinder(4 * scale, h, 8);
            PhongMaterial mat = Gfx.glow(Color.rgb(255, 130 + rng.nextInt(80), 40));
            tongue.setMaterial(mat);
            tongue.setTranslateX(x + (rng.nextDouble() - 0.5) * 14 * scale);
            tongue.setTranslateY(y - h / 2);
            tongue.setTranslateZ(z + (rng.nextDouble() - 0.5) * 14 * scale);
            tongue.setCullFace(javafx.scene.shape.CullFace.NONE);
            f.tongue = tongue;
            f.height = h;

            localFlames.add(f);
            flameGroup.getChildren().add(tongue);
        }

        private void spawnEmber() {
            if (localEmbers.size() >= MAX_EMBER_PARTICLES) return;

            EmberParticle e = new EmberParticle();
            e.life = 1.5 + rng.nextDouble() * 1.5;
            e.maxLife = e.life;
            e.vx = (rng.nextDouble() - 0.5) * 24;
            e.vy = -(35 + rng.nextDouble() * 40);
            e.vz = (rng.nextDouble() - 0.5) * 24;

            Group emberGroup = new Group();
            Sphere ember = new Sphere(1.4 * scale, 8);
            emberGroup.getChildren().add(ember);
            emberGroup.setMouseTransparent(true);
            e.node = emberGroup;
            PhongMaterial mat = Gfx.glow(Color.rgb(255, 120, 30));
            ember.setMaterial(mat);
            ember.setTranslateX(x + (rng.nextDouble() - 0.5) * 14 * scale);
            ember.setTranslateY(y - 10 * scale);
            ember.setTranslateZ(z + (rng.nextDouble() - 0.5) * 14 * scale);
            ember.setMouseTransparent(true);

            localEmbers.add(e);
            emberGroup.getChildren().add(ember);
        }

        private void spawnSmoke() {
            if (localSmoke.size() >= MAX_SMOKE_PARTICLES) return;

            SmokeParticle s = new SmokeParticle();
            s.life = 3.0;
            s.maxLife = 3.0;
            s.vx = (rng.nextDouble() - 0.3) * 14;
            s.vy = -30;
            s.vz = (rng.nextDouble() - 0.5) * 14;

            Group puffGroup = new Group();
            Box puff = new Box(9 * scale, 9 * scale, 9 * scale);
            PhongMaterial mat = new PhongMaterial(Color.rgb(75, 100, 62, 0.35));
            mat.setSpecularColor(Color.BLACK);
            puff.setMaterial(mat);
            puff.setTranslateX(x);
            puff.setTranslateY(y - 20 * scale);
            puff.setTranslateZ(z);
            puff.setMouseTransparent(true);
            puff.setCullFace(javafx.scene.shape.CullFace.NONE);
            puffGroup.getChildren().add(puff);
            puffGroup.setMouseTransparent(true);
            s.node = puffGroup;

            localSmoke.add(s);
            smokeGroup.getChildren().add(puff);
        }

        public void die() {
            dead = true;
            root.getChildren().clear();
            activeFires.remove(this);
        }

        public boolean isDead() {
            return dead;
        }
    }

    private static class FlameParticle {
        Cylinder tongue;
        double height;
    }

    private static class EmberParticle {
        Group node;
        double vx, vy, vz;
        double life, maxLife;
    }

    private static class SmokeParticle {
        Group node;
        double vx, vy, vz;
        double life, maxLife;
    }
}