package com.blackout.game.graphics;

import com.blackout.game.core.Constants;
import com.blackout.game.core.Gfx;
import com.blackout.game.effects.Particle;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import javafx.scene.Group;
import javafx.scene.paint.Color;
import javafx.scene.paint.PhongMaterial;
import javafx.scene.shape.Box;
import javafx.scene.shape.Cylinder;
import javafx.scene.shape.Sphere;

/**
 * Bullet impact effects system with:
 * - Surface-specific decals (blood, sparks, dust, wood chips)
 * - Impact particle bursts
 * - Temporary decal meshes on surfaces
 * - Ricochet effects for hard surfaces
 */
public final class ImpactEffects {

    private static final int MAX_DECALS = 200;
    private static final double DECAL_LIFETIME = 30.0; // seconds

    private final Group world;
    private final Random rng = new Random();

    private final List<Decal> decals = new ArrayList<>();
    private final List<ImpactEffect> activeImpacts = new ArrayList<>();

    public ImpactEffects(Group world) {
        this.world = world;
    }

    public void createImpact(ImpactType type, double x, double y, double z,
            double nx, double ny, double nz, double[] rayDir) {
        // Create impact burst
        ImpactEffect effect = new ImpactEffect(type, x, y, z, nx, ny, nz, rayDir);
        activeImpacts.add(effect);
        world.getChildren().add(effect.root);

        // Create permanent decal on surface
        if (type != ImpactType.AIR) {
            createDecal(type, x, y, z, nx, ny, nz);
        }
    }

    private void createDecal(ImpactType type, double x, double y, double z,
            double nx, double ny, double nz) {
        if (decals.size() >= MAX_DECALS) {
            Decal old = decals.remove(0);
            world.getChildren().remove(old.node);
        }

        Decal decal = new Decal();
        decal.type = type;
        decal.x = x;
        decal.y = y;
        decal.z = z;
        decal.nx = nx;
        decal.ny = ny;
        decal.nz = nz;
        decal.life = DECAL_LIFETIME;
        decal.maxLife = DECAL_LIFETIME;
        decal.rotation = rng.nextDouble() * 360;
        decal.size = 4 + rng.nextDouble() * 8;

        // Create decal geometry based on surface normal
        Group g = new Group();
        g.setMouseTransparent(true);

        PhongMaterial mat = getDecalMaterial(type);
        Box decalBox = new Box(decal.size, 0.5, decal.size);
        decalBox.setMaterial(mat);
        decalBox.setMouseTransparent(true);
        g.getChildren().add(decalBox);

        // Orient to surface normal
        orientToNormal(g, nx, ny, nz);
        g.setTranslateX(x);
        g.setTranslateY(y);
        g.setTranslateZ(z);

        decal.node = g;
        decals.add(decal);
        world.getChildren().add(g);
    }

    private PhongMaterial getDecalMaterial(ImpactType type) {
        return switch (type) {
            case BLOOD -> Gfx.mat(Color.rgb(120, 8, 8, 0.7), Color.BLACK, 1);
            case SPARK -> Gfx.mat(Color.rgb(255, 220, 120, 0.5), Color.BLACK, 1);
            case DUST -> Gfx.mat(Color.rgb(110, 105, 95, 0.6), Color.BLACK, 1);
            case WOOD -> Gfx.mat(Color.rgb(112, 82, 48, 0.7), Color.BLACK, 1);
            case CONCRETE -> Gfx.mat(Color.rgb(80, 80, 85, 0.6), Color.BLACK, 1);
            case METAL -> Gfx.mat(Color.rgb(150, 150, 160, 0.5), Color.BLACK, 1);
            case GLASS -> Gfx.mat(Color.rgb(200, 220, 255, 0.4), Color.BLACK, 1);
            case WATER -> Gfx.mat(Color.rgb(100, 150, 200, 0.5), Color.BLACK, 1);
            default -> Gfx.mat(Color.rgb(100, 100, 100, 0.5), Color.BLACK, 1);
        };
    }

    private void orientToNormal(Group g, double nx, double ny, double nz) {
        // Calculate rotation to align quad with surface normal
        // Default quad faces +Y, we need to rotate it to face the normal
        if (Math.abs(ny - 1.0) < 0.01) {
            // Facing up - no rotation needed
            return;
        }
        if (Math.abs(ny + 1.0) < 0.01) {
            // Facing down - flip
            g.getTransforms().add(new javafx.scene.transform.Rotate(180, Rotate.X_AXIS));
            return;
        }

        // Rotate to face normal
        double yaw = Math.toDegrees(Math.atan2(nx, nz));
        double pitch = Math.toDegrees(Math.asin(Math.max(-1, Math.min(1, ny))));
        g.getTransforms().addAll(
                new javafx.scene.transform.Rotate(yaw, Rotate.Y_AXIS),
                new javafx.scene.transform.Rotate(-pitch, Rotate.X_AXIS)
        );
    }

    public void update(double dt) {
        // Update active impact effects
        for (java.util.Iterator<ImpactEffect> it = activeImpacts.iterator(); it.hasNext();) {
            ImpactEffect e = it.next();
            e.life -= dt;
            if (e.life <= 0) {
                world.getChildren().remove(e.root);
                it.remove();
                continue;
            }
            e.update(dt);
        }

        // Update decals (fade out over time)
        for (java.util.Iterator<Decal> it = decals.iterator(); it.hasNext();) {
            Decal d = it.next();
            d.life -= dt;
            if (d.life <= 0) {
                world.getChildren().remove(d.node);
                it.remove();
                continue;
            }
            double alpha = d.life / d.maxLife;
            PhongMaterial mat = (PhongMaterial) d.node.getChildren().get(0).getMaterial();
            Color c = mat.getDiffuseColor();
            mat.setDiffuseColor(new Color(c.getRed(), c.getGreen(), c.getBlue(), alpha * 0.7));
        }
    }

    public void clear() {
        for (ImpactEffect e : activeImpacts) {
            world.getChildren().remove(e.root);
        }
        activeImpacts.clear();
        for (Decal d : decals) {
            world.getChildren().remove(d.node);
        }
        decals.clear();
    }

    public enum ImpactType {
        BLOOD,     // Zombie/enemy hit
        SPARK,     // Metal/concrete ricochet
        DUST,      // Dirt/concrete
        WOOD,      // Wood surfaces
        CONCRETE,  // Concrete/stone
        METAL,     // Metal surfaces
        GLASS,     // Windows
        WATER,     // Water surface
        AIR        // No surface hit (tracer end)
    }

    private static class Decal {
        ImpactType type;
        double x, y, z;
        double nx, ny, nz;
        double life, maxLife;
        double rotation;
        double size;
        Group node;
    }

    private class ImpactEffect {
        final ImpactType type;
        final double x, y, z;
        final double nx, ny, nz;
        final double[] rayDir;
        final Group root = new Group();
        double life = 0.5;
        private final List<ImpactParticle> particles = new ArrayList<>();

        public ImpactEffect(ImpactType type, double x, double y, double z,
                double nx, double ny, double nz, double[] rayDir) {
            this.type = type;
            this.x = x; this.y = y; this.z = z;
            this.nx = nx; this.ny = ny; this.nz = nz;
            this.rayDir = rayDir;
            root.setMouseTransparent(true);
            spawnParticles();
        }

        private void spawnParticles() {
            int count = switch (type) {
                case BLOOD -> 12;
                case SPARK -> 20;
                case DUST -> 15;
                case WOOD -> 10;
                case CONCRETE -> 18;
                case METAL -> 16;
                case GLASS -> 25;
                case WATER -> 8;
                default -> 5;
            };

            PhongMaterial mat = getParticleMaterial(type);

            for (int i = 0; i < count; i++) {
                ImpactParticle p = new ImpactParticle();
                p.life = 0.2 + rng.nextDouble() * 0.3;
                p.maxLife = p.life;
                p.x = x; p.y = y; p.z = z;

                // Velocity based on surface normal and ray direction
                double reflectX = rayDir[0] - 2 * (rayDir[0] * nx + rayDir[1] * ny + rayDir[2] * nz) * nx;
                double reflectY = rayDir[1] - 2 * (rayDir[0] * nx + rayDir[1] * ny + rayDir[2] * nz) * ny;
                double reflectZ = rayDir[2] - 2 * (rayDir[0] * nx + rayDir[1] * ny + rayDir[2] * nz) * nz;

                double spread = 0.8;
                p.vx = (reflectX + (rng.nextDouble() - 0.5) * spread) * (40 + rng.nextDouble() * 80);
                p.vy = (reflectY + (rng.nextDouble() - 0.5) * spread) * (40 + rng.nextDouble() * 80) + 20;
                p.vz = (reflectZ + (rng.nextDouble() - 0.5) * spread) * (40 + rng.nextDouble() * 80);

                double size = 1 + rng.nextDouble() * 2;
                Box box = new Box(size, size, size);
                box.setMaterial(mat);
                box.setTranslateX(p.x);
                box.setTranslateY(p.y);
                box.setTranslateZ(p.z);
                box.setMouseTransparent(true);
                p.node = box;
                particles.add(p);
                root.getChildren().add(box);
            }
        }

        private PhongMaterial getParticleMaterial(ImpactType type) {
            return switch (type) {
                case BLOOD -> Gfx.glow(Color.rgb(180, 0, 0));
                case SPARK -> Gfx.glow(Color.rgb(255, 220, 120));
                case DUST -> Gfx.mat(Color.rgb(110, 105, 95, 0.6), Color.BLACK, 1);
                case WOOD -> Gfx.mat(Color.rgb(112, 82, 48, 0.7), Color.BLACK, 1);
                case CONCRETE -> Gfx.mat(Color.rgb(100, 100, 105, 0.6), Color.BLACK, 1);
                case METAL -> Gfx.glow(Color.rgb(200, 200, 220));
                case GLASS -> Gfx.glow(Color.rgb(220, 230, 255));
                case WATER -> Gfx.glow(Color.rgb(100, 180, 255));
                default -> Gfx.glow(Color.rgb(200, 200, 200));
            };
        }

        public void update(double dt) {
            for (ImpactParticle p : particles) {
                p.life -= dt;
                if (p.life <= 0) continue;

                p.vy -= 200 * dt; // Gravity
                p.x += p.vx * dt;
                p.y += p.vy * dt;
                p.z += p.vz * dt;
                p.node.setTranslateX(p.x);
                p.node.setTranslateY(p.y);
                p.node.setTranslateZ(p.z);

                double alpha = p.life / p.maxLife;
                PhongMaterial mat = (PhongMaterial) p.node.getMaterial();
                Color c = mat.getDiffuseColor();
                mat.setDiffuseColor(new Color(c.getRed(), c.getGreen(), c.getBlue(), alpha));
            }
        }
    }

    private static class ImpactParticle {
        double x, y, z;
        double vx, vy, vz;
        double life, maxLife;
        Group node;
    }
}