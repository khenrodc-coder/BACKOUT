package com.blackout.game.systems;

import com.blackout.game.core.Gfx;
import com.blackout.game.core.Mats;
import com.blackout.game.core.Constants;
import com.blackout.game.effects.Particle;
import com.blackout.game.effects.DynLight;

import java.util.List;
import java.util.Random;

import javafx.scene.Group;
import javafx.scene.paint.Color;
import javafx.scene.paint.PhongMaterial;
import javafx.scene.shape.Box;
import javafx.scene.shape.Cylinder;

/**
 * Handles weather: rain, fog, thunderstorms, wind.
 * New system for Phase 1 architecture refactoring.
 */
public final class WeatherSystem {

    public enum WeatherType { CLEAR, RAIN, HEAVY_RAIN, FOG, THUNDERSTORM, WINDY }

    private WeatherType currentWeather = WeatherType.CLEAR;
    private double weatherTimer = 0;
    private double weatherDuration = 0;
    private double rainIntensity = 0;
    private double fogDensity = 0;
    private double windStrength = 0;
    private double lightningTimer = 0;
    private boolean lightningActive = false;

    private final Group world;
        private final List<Particle> particles;
    private final List<DynLight> lights;

    // Rain particles
    private final java.util.List<Particle> rainParticles = new java.util.ArrayList<>();

    public WeatherSystem(Group world, Random rng, List<Particle> particles, List<DynLight> lights) {
        this.world = world;
        this.rng = rng;
        this.particles = particles;
        this.lights = lights;
    }

    public void update(double dt, double playerX, double playerZ, double cameraY) {
        // Weather transition logic
        weatherTimer -= dt;
        if (weatherTimer <= 0) {
            rollNewWeather();
        }

        // Update current weather effects
        switch (currentWeather) {
            case RAIN, HEAVY_RAIN -> updateRain(dt, playerX, playerZ, cameraY);
            case FOG -> updateFog(dt);
            case THUNDERSTORM -> {
                updateRain(dt, playerX, playerZ, cameraY);
                updateThunder(dt);
            }
            case WINDY -> updateWind(dt);
        }

        // Update rain particles
        updateRainParticles(dt);
    }

    private void rollNewWeather() {
        double r = Constants.RNG.nextDouble();
        if (r < 0.5) {
            currentWeather = WeatherType.CLEAR;
            weatherDuration = 300 + Constants.RNG.nextDouble() * 600; // 5-15 min
        } else if (r < 0.65) {
            currentWeather = WeatherType.RAIN;
            weatherDuration = 120 + Constants.RNG.nextDouble() * 180; // 2-5 min
        } else if (r < 0.75) {
            currentWeather = WeatherType.FOG;
            weatherDuration = 180 + Constants.RNG.nextDouble() * 240; // 3-7 min
        } else if (r < 0.85) {
            currentWeather = WeatherType.WINDY;
            weatherDuration = 60 + Constants.RNG.nextDouble() * 120; // 1-3 min
        } else if (r < 0.92) {
            currentWeather = WeatherType.HEAVY_RAIN;
            weatherDuration = 60 + Constants.RNG.nextDouble() * 120;
        } else {
            currentWeather = WeatherType.THUNDERSTORM;
            weatherDuration = 90 + Constants.RNG.nextDouble() * 150;
        }
        weatherTimer = weatherDuration;
        rainIntensity = 0;
        fogDensity = 0;
        windStrength = 0;
    }

    private void updateRain(double dt, double playerX, double playerZ, double cameraY) {
        double targetIntensity = currentWeather == WeatherType.HEAVY_RAIN ? 1.0 : 0.5;
        rainIntensity += (targetIntensity - rainIntensity) * dt * 2;

        // Spawn rain particles around player
        int spawnCount = (int) (rainIntensity * 200 * dt);
        for (int i = 0; i < spawnCount; i++) {
            double px = playerX + (Constants.RNG.nextDouble() - 0.5) * 100;
            double pz = playerZ + (Constants.RNG.nextDouble() - 0.5) * 100;
            double py = cameraY + 100 + Constants.RNG.nextDouble() * 50;
            spawnRainDrop(px, py, pz);
        }
    }

    private void spawnRainDrop(double x, double y, double z) {
        if (rainParticles.size() > 2000) return;
        Particle p = new Particle();
        p.node = new Box(0.1, 0.5, 0.1);
        p.node.setMaterial(Mats.WATER);
        p.node.setTranslateX(x);
        p.node.setTranslateY(y);
        p.node.setTranslateZ(z);
        p.node.setMouseTransparent(true);
        p.vx = windStrength * 10;
        p.vy = -300 - Constants.RNG.nextDouble() * 100;
        p.vz = windStrength * 10;
        p.life = 2.0;
        p.maxLife = 2.0;
        p.gravity = 0;
        p.moving = true;
        rainParticles.add(p);
        world.getChildren().add(p.node);
    }

    private void updateRainParticles(double dt) {
        for (java.util.Iterator<Particle> it = rainParticles.iterator(); it.hasNext();) {
            Particle p = it.next();
            p.life -= dt;
            if (p.life <= 0 || p.node.getTranslateY() < 108) {
                world.getChildren().remove(p.node);
                it.remove();
            } else {
                p.node.setTranslateX(p.node.getTranslateX() + p.vx * dt);
                p.node.setTranslateY(p.node.getTranslateY() + p.vy * dt);
                p.node.setTranslateZ(p.node.getTranslateZ() + p.vz * dt);
            }
        }
    }

    private void updateFog(double dt) {
        double targetDensity = 0.7;
        fogDensity += (targetDensity - fogDensity) * dt * 0.5;
        // Fog rendering handled by shader/post-processing in Main
    }

    private void updateThunder(double dt) {
        lightningTimer -= dt;
        if (lightningTimer <= 0 && !lightningActive) {
            if (Constants.RNG.nextDouble() < 0.02 * dt) { // ~2% chance per second
                triggerLightning();
            }
        }
        if (lightningActive) {
            lightningTimer -= dt;
            if (lightningTimer <= 0) {
                lightningActive = false;
                lightningTimer = 0.1 + Constants.RNG.nextDouble() * 0.3;
            }
        }
    }

    private void triggerLightning() {
        lightningActive = true;
        lightningTimer = 0.05 + Constants.RNG.nextDouble() * 0.1; // Flash duration
        // Bright flash - handled by Main's lighting system
        // Thunder sound - handled by AudioManager
    }

    private void updateWind(double dt) {
        double targetWind = 0.5 + Constants.RNG.nextDouble() * 0.5;
        windStrength += (targetWind - windStrength) * dt * 0.3;
        // Wind affects projectiles, vegetation sway, sound propagation
    }

    // Getters for Main
    public WeatherType getCurrentWeather() { return currentWeather; }
    public double getRainIntensity() { return rainIntensity; }
    public double getFogDensity() { return fogDensity; }
    public double getWindStrength() { return windStrength; }
    public boolean isLightningActive() { return lightningActive; }
    public boolean isRaining() { return currentWeather == WeatherType.RAIN || currentWeather == WeatherType.HEAVY_RAIN || currentWeather == WeatherType.THUNDERSTORM; }
    public boolean isStormy() { return currentWeather == WeatherType.THUNDERSTORM; }
}