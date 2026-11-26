package com.blackout.game.graphics;

import com.blackout.game.core.Constants;
import com.blackout.game.core.Gfx;
import com.blackout.game.systems.WeatherSystem;
import com.blackout.game.systems.DayNightSystem;

import javafx.scene.Group;
import javafx.scene.paint.Color;
import javafx.scene.paint.PhongMaterial;
import javafx.scene.shape.Box;
import javafx.scene.effect.ColorAdjust;

/**
 * Volumetric fog system using layered translucent quads with noise-based density variation.
 * Simulates atmospheric scattering and distance-based density.
 */
public final class VolumetricFog {

    private static final int FOG_LAYERS = 12;
    private static final double FOG_MAX_HEIGHT = 300;
    private static final double FOG_LAYER_HEIGHT = FOG_MAX_HEIGHT / FOG_LAYERS;
    private static final double FOG_QUAD_SIZE = 4000;

    private final Group world;
    private final Group fogGroup = new Group();
    private final Box[] fogQuads = new Box[FOG_LAYERS];
    private final PhongMaterial[] fogMaterials = new PhongMaterial[FOG_LAYERS];

    private double baseDensity = 0.0;
    private double targetDensity = 0.0;
    private double heightFalloff = 1.0;
    private boolean enabled = true;

    // Noise texture for density variation
    private final java.util.Random rng = new java.util.Random();

    public VolumetricFog(Group world) {
        this.world = world;
        world.getChildren().add(fogGroup);
        fogGroup.setMouseTransparent(true);
        initFogLayers();
    }

    private void initFogLayers() {
        for (int i = 0; i < FOG_LAYERS; i++) {
            double y = Constants.GROUND_Y + i * FOG_LAYER_HEIGHT;

            // Create noise texture for this layer
            PhongMaterial mat = createFogMaterial(i);
            fogMaterials[i] = mat;

            Box quad = new Box(FOG_QUAD_SIZE, FOG_LAYER_HEIGHT, FOG_QUAD_SIZE);
            quad.setMaterial(mat);
            quad.setTranslateX(0);
            quad.setTranslateY(y);
            quad.setTranslateZ(0);
            quad.setMouseTransparent(true);
            quad.setCullFace(javafx.scene.shape.CullFace.NONE);
            fogQuads[i] = quad;
            fogGroup.getChildren().add(quad);
        }
        fogGroup.setVisible(enabled);
    }

    private PhongMaterial createFogMaterial(int layer) {
        // Create procedural noise texture for fog density variation
        int size = 256;
        javafx.scene.image.WritableImage img = new javafx.scene.image.WritableImage(size, size);
        javafx.scene.image.PixelWriter pw = img.getPixelWriter();

        // Base fog color - slightly blue-gray
        Color baseColor = Color.rgb(180, 190, 210, 1.0);
        double layerHeight = layer / (double) FOG_LAYERS;

        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                // Multi-octave noise
                double nx = x / 16.0, ny = y / 16.0;
                double noise = 0;
                double amp = 1.0, freq = 1.0;
                for (int o = 0; o < 4; o++) {
                    noise += amp * simplexNoise(nx * freq, ny * freq);
                    amp *= 0.5;
                    freq *= 2.0;
                }
                noise = (noise + 1) / 2; // 0-1

                // Height falloff - less dense at higher layers
                double heightFactor = 1.0 - layerHeight * 0.7;
                noise *= heightFactor;

                // Add some vertical streaks
                double streak = Math.sin(y * 0.1 + layer * 0.5) * 0.1;
                noise = Math.max(0, Math.min(1, noise + streak));

                double alpha = noise;
                int a = (int) (alpha * 255);
                pw.setArgb(x, y, (a << 24) | (200 << 16) | (210 << 8) | 230);
            }
        }

        PhongMaterial mat = new PhongMaterial(Color.WHITE);
        mat.setDiffuseMap(img);
        mat.setSpecularColor(Color.BLACK);
        mat.setSelfIlluminationMap(img);
        return mat;
    }

    // Simple 2D simplex-like noise
    private double simplexNoise(double x, double y) {
        int ix = (int) Math.floor(x), iy = (int) Math.floor(y);
        double fx = x - ix, fy = y - iy;
        double u = fx * fx * (3 - 2 * fx);
        double v = fy * fy * (3 - 2 * fy);

        double n00 = hash(ix, iy);
        double n10 = hash(ix + 1, iy);
        double n01 = hash(ix, iy + 1);
        double n11 = hash(ix + 1, iy + 1);

        double nx0 = lerp(n00, n10, u);
        double nx1 = lerp(n01, n11, u);
        return lerp(nx0, nx1, v) * 2 - 1;
    }

    private double hash(int x, int y) {
        int n = x + y * 57;
        n = (n << 13) ^ n;
        return ((n * (n * n * 15731 + 789221) + 1376312589) & 0x7fffffff) / 1073741824.0;
    }

    private double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }

    public void update(double dt, double playerX, double playerZ, double cameraY,
            WeatherSystem weather, DayNightSystem dayNight) {
        if (!enabled) {
            fogGroup.setVisible(false);
            return;
        }
        fogGroup.setVisible(true);

        // Calculate target density based on weather, time, and altitude
        double weatherDensity = 0;
        if (weather != null) {
            switch (weather.getCurrentWeather()) {
                case FOG -> weatherDensity = 0.8;
                case RAIN -> weatherDensity = 0.3;
                case HEAVY_RAIN -> weatherDensity = 0.5;
                case THUNDERSTORM -> weatherDensity = 0.6;
                default -> weatherDensity = 0.05;
            }
        }

        // Time of day factor
        double timeDensity = 0;
        if (dayNight != null) {
            if (dayNight.isNightNow()) timeDensity = 0.15;
            else if (dayNight.hourOfDay() < 8 || dayNight.hourOfDay() > 18) timeDensity = 0.1;
        }

        // Altitude factor (camera height)
        double altitudeFactor = Math.max(0, 1 - (cameraY - Constants.GROUND_Y) / 500);

        targetDensity = (weatherDensity + timeDensity) * altitudeFactor;
        targetDensity = Math.max(0.01, Math.min(0.95, targetDensity));

        // Smooth transition
        baseDensity += (targetDensity - baseDensity) * dt * 0.5;

        // Update fog layers
        for (int i = 0; i < FOG_LAYERS; i++) {
            double layerHeight = i / (double) FOG_LAYERS;
            double layerDensity = baseDensity * (1 - layerHeight * 0.6);

            // Add subtle animation
            double time = System.currentTimeMillis() / 10000.0;
            layerDensity += Math.sin(time + i * 0.7) * 0.02;

            // Update material opacity via self-illumination map tint
            updateLayerOpacity(i, layerDensity);

            // Follow player horizontally
            fogQuads[i].setTranslateX(playerX);
            fogQuads[i].setTranslateZ(playerZ);
        }
    }

    private void updateLayerOpacity(int layer, double density) {
        // Modify the self-illumination map to adjust apparent density
        // In JavaFX, we can adjust the diffuse color alpha
        PhongMaterial mat = fogMaterials[layer];
        Color base = Color.rgb(180, 190, 210, density);
        mat.setDiffuseColor(base);
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        fogGroup.setVisible(enabled);
    }

    public void setBaseDensity(double density) {
        this.baseDensity = Math.max(0, Math.min(1, density));
    }

    public double getDensity() {
        return baseDensity;
    }

    public void dispose() {
        world.getChildren().remove(fogGroup);
    }
}