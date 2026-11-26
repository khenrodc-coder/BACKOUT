package com.blackout.game.graphics;

import com.blackout.game.core.Constants;
import com.blackout.game.systems.DayNightSystem;

import javafx.scene.Group;
import javafx.scene.paint.Color;
import javafx.scene.paint.PhongMaterial;
import javafx.scene.shape.Sphere;
import javafx.scene.image.WritableImage;
import javafx.scene.image.PixelWriter;

/**
 * Advanced sky and atmosphere rendering with:
 * - Dynamic gradient sky with sun/moon position
 * - Cloud layers with animation
 * - Star field at night
 * - Blood moon special effects
 * - Atmospheric scattering simulation
 */
public final class SkyAtmosphere {

    private static final int SKY_RESOLUTION = 1024;
    private static final int SKY_HEIGHT = 512;
    private static final double SKY_SPHERE_RADIUS = 5000;

    private final Group world;
    private final Sphere skySphere;
    private final PhongMaterial skyMaterial;
    private final DayNightSystem dayNight;

    // Cloud layers
    private final Sphere cloudLayer1, cloudLayer2;
    private final PhongMaterial cloudMaterial1, cloudMaterial2;
    private double cloudOffset1 = 0, cloudOffset2 = 0;

    // Stars
    private final Sphere starSphere;
    private final PhongMaterial starMaterial;
    private double starVisibility = 0;

    // Blood moon
    private double bloodMoonIntensity = 0;

    public SkyAtmosphere(Group world, DayNightSystem dayNight) {
        this.world = world;
        this.dayNight = dayNight;

        // Main sky sphere
        skyMaterial = new PhongMaterial(Color.BLACK);
        skyMaterial.setSpecularColor(Color.BLACK);
        skySphere = new Sphere(SKY_SPHERE_RADIUS, 64);
        skySphere.setMaterial(skyMaterial);
        skySphere.setCullFace(javafx.scene.shape.CullFace.NONE);
        skySphere.setMouseTransparent(true);
        world.getChildren().add(skySphere);

        // Cloud layer 1 - lower, faster
        cloudMaterial1 = new PhongMaterial(Color.WHITE);
        cloudMaterial1.setSpecularColor(Color.BLACK);
        cloudLayer1 = new Sphere(SKY_SPHERE_RADIUS + 100, 48);
        cloudLayer1.setMaterial(cloudMaterial1);
        cloudLayer1.setCullFace(javafx.scene.shape.CullFace.NONE);
        cloudLayer1.setMouseTransparent(true);
        world.getChildren().add(cloudLayer1);

        // Cloud layer 2 - higher, slower
        cloudMaterial2 = new PhongMaterial(Color.WHITE);
        cloudMaterial2.setSpecularColor(Color.BLACK);
        cloudLayer2 = new Sphere(SKY_SPHERE_RADIUS + 200, 48);
        cloudLayer2.setMaterial(cloudMaterial2);
        cloudLayer2.setCullFace(javafx.scene.shape.CullFace.NONE);
        cloudLayer2.setMouseTransparent(true);
        world.getChildren().add(cloudLayer2);

        // Star sphere (inside sky sphere, only visible at night)
        starMaterial = new PhongMaterial(Color.BLACK);
        starMaterial.setSpecularColor(Color.BLACK);
        starSphere = new Sphere(SKY_SPHERE_RADIUS - 100, 32);
        starSphere.setMaterial(starMaterial);
        starSphere.setCullFace(javafx.scene.shape.CullFace.NONE);
        starSphere.setMouseTransparent(true);
        world.getChildren().add(starSphere);

        // Initial render
        renderSky();
        renderClouds();
        renderStars();
    }

    public void update(double dt, double cameraX, double cameraY, double cameraZ) {
        // Follow camera
        skySphere.setTranslateX(cameraX);
        skySphere.setTranslateY(cameraY);
        skySphere.setTranslateZ(cameraZ);
        cloudLayer1.setTranslateX(cameraX);
        cloudLayer1.setTranslateY(cameraY);
        cloudLayer1.setTranslateZ(cameraZ);
        cloudLayer2.setTranslateX(cameraX);
        cloudLayer2.setTranslateY(cameraY);
        cloudLayer2.setTranslateZ(cameraZ);
        starSphere.setTranslateX(cameraX);
        starSphere.setTranslateY(cameraY);
        starSphere.setTranslateZ(cameraZ);

        // Animate clouds
        cloudOffset1 += dt * 0.0002;
        cloudOffset2 -= dt * 0.0001;
        updateClouds();

        // Update star visibility based on time
        updateStars();

        // Re-render sky periodically
        if (dayNight != null && (Math.abs(dayNight.getGameMinutes() - lastSkyRenderTime) >= 5 || Math.abs(bloodMoonIntensity - lastBloodMoonIntensity) > 0.05)) {
            renderSky();
            lastSkyRenderTime = dayNight.getGameMinutes();
            lastBloodMoonIntensity = bloodMoonIntensity;
        }

        // Update blood moon intensity
        if (dayNight != null) {
            double target = dayNight.isBloodMoon() ? 1.0 : 0.0;
            bloodMoonIntensity += (target - bloodMoonIntensity) * dt * 1.0;
        }
    }

    private double lastSkyRenderTime = -999;
    private double lastBloodMoonIntensity = -1;

    public boolean isBloodMoon() {
        return dayNight != null && dayNight.isBloodMoon();
    }

    private void renderSky() {
        if (dayNight == null) return;

        double h = dayNight.hourOfDay();
        double d = dayNight.getDaylight();
        double warm = 4 * d * (1 - d);
        double bb = bloodMoonIntensity;

        WritableImage img = new WritableImage(SKY_RESOLUTION, SKY_HEIGHT);
        PixelWriter pw = img.getPixelWriter();

        // Sky colors
        Color zenithColor = getZenithColor(d, bb);
        Color horizonColor = getHorizonColor(d, warm, bb);
        Color voidColor = getVoidColor(d, bb);

        double horizonV = 0.5;

        for (int y = 0; y < SKY_HEIGHT; y++) {
            double v = y / (double) (SKY_HEIGHT - 1);
            for (int x = 0; x < SKY_RESOLUTION; x++) {
                Color c;
                if (v < horizonV) {
                    // Sky gradient
                    double t = Math.pow(v / horizonV, 1.5);
                    c = lerpColor(zenithColor, horizonColor, t);

                    // Cloud-like noise in upper atmosphere
                    double noise = Math.sin(x * 0.008 + y * 0.02) * Math.sin(x * 0.004 - y * 0.012)
                            + Math.sin(x * 0.018 + 2) * 0.3;
                    if (noise > 0.15) {
                        double cloudAmt = (noise - 0.15) * 8 * (1 - t * 0.3) * (0.3 + 1.5 * d);
                        c = lerpColor(c, Color.WHITE, Math.min(1, cloudAmt * 0.3));
                    }
                } else {
                    // Void/space
                    double t = Math.min(1, (v - horizonV) / 0.1);
                    c = lerpColor(horizonColor, voidColor, t);
                }

                // Stars at night (only in upper portion)
                if (v < 0.45 && starVisibility > 0) {
                    double starNoise = hash((int)(x * 0.5), (int)(y * 0.5));
                    if (starNoise < 0.0002 * starVisibility) {
                        double brightness = 0.7 + hash(x, y) * 0.3;
                        c = Color.color(
                                Math.min(1, c.getRed() + brightness),
                                Math.min(1, c.getGreen() + brightness * (1 - bb * 0.4)),
                                Math.min(1, c.getBlue() + brightness * (1 - bb * 0.5))
                        );
                    }
                }

                pw.setArgb(x, y, toARGB(c));
            }
        }

        // Sun
        double fs = (h - 6) / 13.0;
        if (fs >= 0 && fs <= 1) {
            drawDisc(img, (0.25 + 0.5 * fs) * SKY_RESOLUTION,
                    (horizonV - Math.sin(Math.PI * fs) * 0.35) * SKY_HEIGHT,
                    25, 100,
                    Color.rgb(255, 248, 220),
                    Color.rgb(255, 200, 110),
                    0.8);
        }

        // Moon
        double fm = ((h - 19 + 24) % 24) / 11.0;
        if (fm >= 0 && fm <= 1 && d < 0.6) {
            double vis = 1 - d / 0.6;
            Color moonCore = lerpColor(Color.rgb(235, 240, 255), Color.rgb(180, 20, 30), bb);
            Color moonGlow = lerpColor(Color.rgb(150, 175, 235), Color.rgb(255, 30, 30), bb);
            drawDisc(img, (0.25 + 0.5 * fm) * SKY_RESOLUTION,
                    (horizonV - Math.sin(Math.PI * fm) * 0.35) * SKY_HEIGHT,
                    28 + bb * 6, 85 + bb * 50,
                    moonCore, moonGlow, (0.6 + 0.5 * bb) * vis);
        }

        skyMaterial.setSelfIlluminationMap(img);
    }

    private void renderClouds() {
        // Cloud layer 1 - denser, lower
        WritableImage cloudImg1 = new WritableImage(SKY_RESOLUTION, SKY_HEIGHT);
        PixelWriter pw1 = cloudImg1.getPixelWriter();

        // Cloud layer 2 - wispy, higher
        WritableImage cloudImg2 = new WritableImage(SKY_RESOLUTION, SKY_HEIGHT);
        PixelWriter pw2 = cloudImg2.getPixelWriter();

        for (int y = 0; y < SKY_HEIGHT; y++) {
            for (int x = 0; x < SKY_RESOLUTION; x++) {
                // Layer 1 - main clouds
                double nx1 = (x + cloudOffset1 * SKY_RESOLUTION) * 0.002;
                double ny1 = y * 0.003;
                double cloud1 = fbm(nx1, ny1, 4);
                cloud1 = smoothstep(0.55, 0.75, cloud1);

                // Layer 2 - high cirrus
                double nx2 = (x - cloudOffset2 * SKY_RESOLUTION) * 0.0015;
                double ny2 = y * 0.002;
                double cloud2 = fbm(nx2, ny2, 3);
                cloud2 = smoothstep(0.65, 0.8, cloud2) * 0.4;

                double combined = Math.max(cloud1, cloud2);

                // Only show clouds in upper sky
                double v = y / (double) SKY_HEIGHT;
                if (v < 0.5) {
                    combined *= smoothstep(0.5, 0.3, v);
                } else {
                    combined = 0;
                }

                Color c1 = Color.rgb(255, 255, 255, combined * 0.6);
                Color c2 = Color.rgb(240, 240, 255, combined * 0.3);

                pw1.setArgb(x, y, toARGB(c1));
                pw2.setArgb(x, y, toARGB(c2));
            }
        }

        cloudMaterial1.setDiffuseMap(cloudImg1);
        cloudMaterial1.setSelfIlluminationMap(cloudImg1);
        cloudMaterial2.setDiffuseMap(cloudImg2);
        cloudMaterial2.setSelfIlluminationMap(cloudImg2);
    }

    private void renderStars() {
        WritableImage starImg = new WritableImage(SKY_RESOLUTION, SKY_HEIGHT);
        PixelWriter pw = starImg.getPixelWriter();

        for (int y = 0; y < SKY_HEIGHT; y++) {
            for (int x = 0; x < SKY_RESOLUTION; x++) {
                double h = hash(x, y);
                double brightness = 0;
                if (h < 0.0001) brightness = 1.0;
                else if (h < 0.0003) brightness = 0.6;
                else if (h < 0.0008) brightness = 0.3;

                brightness *= starVisibility;

                if (brightness > 0) {
                    pw.setArgb(x, y, toARGB(Color.rgb(
                            (int)(255 * brightness),
                            (int)(250 * brightness),
                            (int)(240 * brightness)
                    )));
                } else {
                    pw.setArgb(x, y, 0);
                }
            }
        }

        starMaterial.setDiffuseMap(starImg);
        starMaterial.setSelfIlluminationMap(starImg);
    }

    private void updateClouds() {
        // Clouds are animated via offset in renderClouds
        // Just re-render periodically
        if (System.currentTimeMillis() / 1000 % 30 == 0) {
            renderClouds();
        }
    }

    private void updateStars() {
        if (dayNight == null) return;
        double target = dayNight.isNightNow() ? 1.0 : 0.0;
        if (dayNight.getDaylight() > 0.1) target = 0;
        starVisibility += (target - starVisibility) * 0.01;

        if (Math.abs(starVisibility - target) > 0.01) {
            renderStars();
        }
    }

    // Noise functions
    private double fbm(double x, double y, int octaves) {
        double value = 0;
        double amplitude = 1.0;
        double frequency = 1.0;
        double maxValue = 0;

        for (int i = 0; i < octaves; i++) {
            value += amplitude * simplexNoise(x * frequency, y * frequency);
            maxValue += amplitude;
            amplitude *= 0.5;
            frequency *= 2.0;
        }

        return value / maxValue;
    }

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

    private double smoothstep(double edge0, double edge1, double x) {
        double t = Math.max(0, Math.min(1, (x - edge0) / (edge1 - edge0)));
        return t * t * (3 - 2 * t);
    }

    private Color lerpColor(Color a, Color b, double t) {
        t = Math.max(0, Math.min(1, t));
        return Color.color(
                a.getRed() + (b.getRed() - a.getRed()) * t,
                a.getGreen() + (b.getGreen() - a.getGreen()) * t,
                a.getBlue() + (b.getBlue() - a.getBlue()) * t
        );
    }

    private Color getZenithColor(double daylight, double bloodMoon) {
        Color nightZenith = Color.rgb(3, 5, 14);
        Color bloodZenith = Color.rgb(40, 3, 7);
        Color dayZenith = Color.rgb(58, 118, 200);

        Color base = lerpColor(nightZenith, bloodZenith, bloodMoon);
        return lerpColor(base, dayZenith, daylight);
    }

    private Color getHorizonColor(double daylight, double warm, double bloodMoon) {
        Color nightHorizon = Color.rgb(30, 44, 70);
        Color bloodHorizon = Color.rgb(105, 16, 16);
        Color dayHorizon1 = Color.rgb(165, 205, 235);
        Color dayHorizon2 = Color.rgb(255, 140, 70);

        Color base = lerpColor(nightHorizon, bloodHorizon, bloodMoon);
        Color dayBase = lerpColor(dayHorizon1, dayHorizon2, warm * 0.75);
        return lerpColor(base, dayBase, daylight);
    }

    private Color getVoidColor(double daylight, double bloodMoon) {
        Color voidNight = Color.rgb(9, 12, 12);
        Color voidBlood = Color.rgb(62, 80, 54);
        return lerpColor(voidNight, voidBlood, bloodMoon);
    }

    private void drawDisc(WritableImage img, double cx, double cy, double rad, double glowRad, Color core, Color glw, double strength) {
        PixelWriter pw = img.getPixelWriter();
        int w = (int) img.getWidth(), h = (int) img.getHeight();

        int x0 = Math.max(0, (int)(cx - glowRad));
        int x1 = Math.min(w - 1, (int)(cx + glowRad));
        int y0 = Math.max(0, (int)(cy - glowRad));
        int y1 = Math.min(h - 1, (int)(cy + glowRad));

        for (int y = y0; y <= y1; y++) {
            for (int x = x0; x <= x1; x++) {
                double dd = Math.sqrt((x - cx) * (x - cx) + (y - cy) * (y - cy));
                if (dd < rad) {
                    pw.setArgb(x, y, toARGB(core));
                } else if (dd < glowRad) {
                    double a = Math.pow(1 - (dd - rad) / (glowRad - rad), 2.2) * strength;
                    int o = img.getPixelReader().getArgb(x, y);
                    pw.setArgb(x, y, toARGB(
                            lerpColor(
                                    new Color(((o >> 16) & 255) / 255.0, ((o >> 8) & 255) / 255.0, (o & 255) / 255.0, 1),
                                    glw, a
                            )
                    ));
                }
            }
        }
    }

    private int toARGB(Color c) {
        int r = (int) Math.max(0, Math.min(255, c.getRed() * 255));
        int g = (int) Math.max(0, Math.min(255, c.getGreen() * 255));
        int b = (int) Math.max(0, Math.min(255, c.getBlue() * 255));
        int a = (int) Math.max(0, Math.min(255, c.getOpacity() * 255));
        return (a << 24) | (r << 16) | (g << 8) | b;
    }
}