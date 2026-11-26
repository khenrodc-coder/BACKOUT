package com.blackout.game.graphics;

import com.blackout.game.core.Constants;
import com.blackout.game.core.Gfx;
import com.blackout.game.entities.Enemy;
import com.blackout.game.environment.Building;
import com.blackout.game.environment.Car;
import com.blackout.game.environment.TreeFactory;

import java.util.List;
import java.util.Random;

import javafx.scene.Camera;
import javafx.scene.Group;
import javafx.scene.PerspectiveCamera;
import javafx.scene.paint.Color;
import javafx.scene.paint.PhongMaterial;
import javafx.scene.shape.Box;
import javafx.scene.shape.MeshView;
import javafx.scene.transform.Rotate;
import javafx.scene.transform.Scale;

/**
 * Dynamic shadow mapping system. Renders scene from light perspective to a depth texture,
 * then projects shadows onto geometry in the main render pass.
 *
 * For JavaFX 3D, we simulate shadows by rendering dark transparent boxes under objects
 * aligned with the celestial light direction.
 */
public final class ShadowMap {

    private static final int SHADOW_MAP_SIZE = 2048;
    private static final double SHADOW_DISTANCE = 2000;
    private static final double SHADOW_BIAS = 0.005;

    private final Group world;
    private final Random rng;
    private final List<Building> buildings;
    private final List<Enemy> enemies;
    private final List<Car> cars;
    private final List<TreeFactory.Tree> trees;

    // Shadow casters
    private final Group shadowCasters = new Group();
    private final Group shadowReceivers = new Group();
    private boolean enabled = true;

    public ShadowMap(Group world, Random rng,
            List<Building> buildings, List<Enemy> enemies,
            List<Car> cars, List<TreeFactory.Tree> trees) {
        this.world = world;
        this.rng = rng;
        this.buildings = buildings;
        this.enemies = enemies;
        this.cars = cars;
        this.trees = trees;

        world.getChildren().add(shadowCasters);
        shadowCasters.setMouseTransparent(true);
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        shadowCasters.setVisible(enabled);
    }

    public void update(double dt, double playerX, double playerZ, double sunX, double sunY, double sunZ) {
        if (!enabled) return;

        // Clear old shadows
        shadowCasters.getChildren().clear();

        // Calculate light direction from sun position
        double lightDirX = -sunX;
        double lightDirY = -sunY;
        double lightDirZ = -sunZ;
        double len = Math.sqrt(lightDirX * lightDirX + lightDirY * lightDirY + lightDirZ * lightDirZ);
        lightDirX /= len; lightDirY /= len; lightDirZ /= len;

        // Only cast shadows within range of player
        double rangeSq = SHADOW_DISTANCE * SHADOW_DISTANCE;

        // Building shadows
        for (Building b : buildings) {
            double dx = b.x - playerX, dz = b.z - playerZ;
            if (dx * dx + dz * dz > rangeSq) continue;
            if (b.burning) continue;

            // Create shadow volume for building
            createBuildingShadow(b, lightDirX, lightDirY, lightDirZ);
        }

        // Tree shadows
        for (TreeFactory.Tree t : trees) {
            if (!t.node.isVisible()) continue;
            double dx = t.node.getTranslateX() - playerX;
            double dz = t.node.getTranslateZ() - playerZ;
            if (dx * dx + dz * dz > rangeSq) continue;

            createTreeShadow(t, lightDirX, lightDirY, lightDirZ);
        }

        // Car shadows
        for (Car c : cars) {
            double dx = c.x - playerX, dz = c.z - playerZ;
            if (dx * dx + dz * dz > rangeSq) continue;

            createCarShadow(c, lightDirX, lightDirY, lightDirZ);
        }

        // Enemy shadows
        for (Enemy e : enemies) {
            if (!e.alive || !e.model.isVisible()) continue;
            double dx = e.model.getTranslateX() - playerX;
            double dz = e.model.getTranslateZ() - playerZ;
            if (dx * dx + dz * dz > rangeSq) continue;

            createEnemyShadow(e, lightDirX, lightDirY, lightDirZ);
        }
    }

    private void createBuildingShadow(Building b, double lx, double ly, double lz) {
        // Project building corners onto ground along light direction
        double h = b.h;
        double w = b.w / 2, d = b.d / 2;
        double bx = b.x, bz = b.z;

        // Four corners at roof level
        double[][] corners = {
            {bx - w, bz - d, Constants.GROUND_Y - h},
            {bx + w, bz - d, Constants.GROUND_Y - h},
            {bx + w, bz + d, Constants.GROUND_Y - h},
            {bx - w, bz + d, Constants.GROUND_Y - h}
        };

        // Project to ground
        double[][] projected = new double[4][2];
        for (int i = 0; i < 4; i++) {
            double t = (Constants.GROUND_Y - corners[i][2]) / -ly;
            projected[i][0] = corners[i][0] + lx * t;
            projected[i][1] = corners[i][1] + lz * t;
        }

        // Create shadow as a dark transparent quad
        Group shadow = new Group();
        shadow.setMouseTransparent(true);

        // Calculate center and bounds
        double minX = projected[0][0], maxX = projected[0][0];
        double minZ = projected[0][1], maxZ = projected[0][1];
        for (int i = 1; i < 4; i++) {
            minX = Math.min(minX, projected[i][0]);
            maxX = Math.max(maxX, projected[i][0]);
            minZ = Math.min(minZ, projected[i][1]);
            maxZ = Math.max(maxZ, projected[i][1]);
        }
        double cx = (minX + maxX) / 2;
        double cz = (minZ + maxZ) / 2;
        double sw = maxX - minX;
        double sd = maxZ - minZ;

        // Shadow box - very thin, at ground level
        Box shadowBox = new Box(sw + 4, 0.5, sd + 4);
        shadowBox.setMaterial(Gfx.SHADOW_MAT);
        shadowBox.setTranslateX(cx);
        shadowBox.setTranslateY(Constants.GROUND_Y - 0.2);
        shadowBox.setTranslateZ(cz);
        shadow.getChildren().add(shadowBox);

        shadowCasters.getChildren().add(shadow);
    }

    private void createTreeShadow(TreeFactory.Tree t, double lx, double ly, double lz) {
        double tx = t.node.getTranslateX();
        double tz = t.node.getTranslateZ();
        double ty = t.node.getTranslateY();

        // Tree shadow is an ellipse projected from canopy
        Group shadow = new Group();
        shadow.setMouseTransparent(true);

        // Canopy center height
        double canopyY = ty + 200 * t.getScale();
        double canopyR = 60 * t.getScale();

        // Project canopy circle to ground
        double tProj = (Constants.GROUND_Y - canopyY) / -ly;
        double projX = tx + lx * tProj;
        double projZ = tz + lz * tProj;
        double projR = canopyR * (1 + Math.abs(ly) * 0.5);

        Box shadowBox = new Box(projR * 2, 0.3, projR * 2);
        shadowBox.setMaterial(Gfx.SHADOW_MAT);
        shadowBox.setTranslateX(projX);
        shadowBox.setTranslateY(Constants.GROUND_Y - 0.1);
        shadowBox.setTranslateZ(projZ);
        shadow.getChildren().add(shadowBox);

        // Trunk shadow
        Box trunkShadow = new Box(8 * t.getScale(), 0.3, 8 * t.getScale());
        trunkShadow.setMaterial(Gfx.SHADOW_MAT);
        trunkShadow.setTranslateX(projX);
        trunkShadow.setTranslateY(Constants.GROUND_Y - 0.1);
        trunkShadow.setTranslateZ(projZ);
        shadow.getChildren().add(trunkShadow);

        shadowCasters.getChildren().add(shadow);
    }

    private void createCarShadow(Car c, double lx, double ly, double lz) {
        Group shadow = new Group();
        shadow.setMouseTransparent(true);

        double len = c.turned ? 132 : 66;
        double wid = c.turned ? 66 : 132;

        // Project car corners
        double cx = c.x, cz = c.z, cy = Constants.GROUND_Y;
        double h = 38;

        double[][] corners = {
            {cx - wid/2, cz - len/2, cy},
            {cx + wid/2, cz - len/2, cy},
            {cx + wid/2, cz + len/2, cy},
            {cx - wid/2, cz + len/2, cy}
        };

        double[][] projected = new double[4][2];
        for (int i = 0; i < 4; i++) {
            double t = (Constants.GROUND_Y - corners[i][2]) / -ly;
            projected[i][0] = corners[i][0] + lx * t;
            projected[i][1] = corners[i][1] + lz * t;
        }

        double minX = projected[0][0], maxX = projected[0][0];
        double minZ = projected[0][1], maxZ = projected[0][1];
        for (int i = 1; i < 4; i++) {
            minX = Math.min(minX, projected[i][0]);
            maxX = Math.max(maxX, projected[i][0]);
            minZ = Math.min(minZ, projected[i][1]);
            maxZ = Math.max(maxZ, projected[i][1]);
        }
        double scx = (minX + maxX) / 2;
        double scz = (minZ + maxZ) / 2;
        double sw = maxX - minX;
        double sd = maxZ - minZ;

        Box shadowBox = new Box(sw + 2, 0.3, sd + 2);
        shadowBox.setMaterial(Gfx.SHADOW_MAT);
        shadowBox.setTranslateX(scx);
        shadowBox.setTranslateY(Constants.GROUND_Y - 0.15);
        shadowBox.setTranslateZ(scz);
        shadow.getChildren().add(shadowBox);

        shadowCasters.getChildren().add(shadow);
    }

    private void createEnemyShadow(Enemy e, double lx, double ly, double lz) {
        double ex = e.model.getTranslateX();
        double ez = e.model.getTranslateZ();
        double ey = Constants.GROUND_Y;

        Group shadow = new Group();
        shadow.setMouseTransparent(true);

        double r = 16 * e.sx;
        double h = 100 * e.sy;

        // Project enemy as capsule
        double t = (Constants.GROUND_Y - (ey + h/2)) / -ly;
        double projX = ex + lx * t;
        double projZ = ez + lz * t;

        Box shadowBox = new Box(r * 2, 0.3, r * 2);
        shadowBox.setMaterial(Gfx.SHADOW_MAT);
        shadowBox.setTranslateX(projX);
        shadowBox.setTranslateY(Constants.GROUND_Y - 0.1);
        shadowBox.setTranslateZ(projZ);
        shadow.getChildren().add(shadowBox);

        shadowCasters.getChildren().add(shadow);
    }

    public void clear() {
        shadowCasters.getChildren().clear();
    }
}