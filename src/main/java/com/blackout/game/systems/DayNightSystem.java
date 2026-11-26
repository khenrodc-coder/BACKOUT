package com.blackout.game.systems;

import com.blackout.game.core.Gfx;

import java.util.Random;

import javafx.scene.PointLight;
import javafx.scene.AmbientLight;
import javafx.scene.paint.Color;
import javafx.scene.paint.PhongMaterial;
import javafx.scene.shape.Sphere;
import javafx.scene.image.WritableImage;
import javafx.scene.image.PixelWriter;

/**
 * Handles day/night cycle, calendar, blood moon, sky rendering, lighting.
 * Extracted from Main.java to reduce god-class size.
 */
public final class DayNightSystem {

    // Time constants
    public static final double TIME_SCALE = 2.0; // game minutes per real second
    private static final String[] MONTHS = {"JANUARY", "FEBRUARY", "MARCH", "APRIL", "MAY", "JUNE",
        "JULY", "AUGUST", "SEPTEMBER", "OCTOBER", "NOVEMBER", "DECEMBER"};

    // State
    public double gameMinutes = 7 * 60 + 30; // 07:30 AM
    public boolean bloodMoon = false;
    public double bloodBlend = 0; // 0-1 for smooth transitions
    public double daylightNow = 1;
    public double lastSkyMin = -999;
    public double lastSkyBlood = -1;
    public int lastClockMin = -1;

    // Sky/lighting
    private final Sphere sky;
    private final PointLight celestial;
    private final AmbientLight ambientLight;
    private final PhongMaterial skyMat;
    private final PhongMaterial voidMat;
    private final Random rng;

    public DayNightSystem(Sphere sky, PointLight celestial, AmbientLight ambientLight,
            PhongMaterial skyMat, PhongMaterial voidMat, Random rng) {
        this.sky = sky;
        this.celestial = celestial;
        this.ambientLight = ambientLight;
        this.skyMat = skyMat;
        this.voidMat = voidMat;
        this.rng = rng;
    }

    public void update(double dt, boolean sleeping, java.util.function.BiConsumer<String, Color> showBanner,
            java.util.function.BiConsumer<String, Color> status,
            java.util.function.Consumer<Boolean> onBloodMoonChanged) {
        if (!sleeping) {
            gameMinutes += dt * TIME_SCALE;
        }

        boolean bm = isBloodMoonTime();
        if (bm != bloodMoon) {
            bloodMoon = bm;
            onBloodMoonChanged.accept(bm);
            showBanner.accept(bm ? "THE BLOOD MOON RISES" : "THE BLOOD MOON FADES", bm ? Color.CRIMSON : Color.LIGHTGRAY);
            status.accept(bm ? "BLOOD MOON: THE DEAD ARE RUNNING!" : "THE BLOOD MOON HAS ENDED",
                    bm ? Color.CRIMSON : Color.LIGHTGRAY);
        }

        double target = bloodMoon ? 1 : 0;
        bloodBlend += (target - bloodBlend) * Math.min(1, dt * 0.8);
        if (Math.abs(bloodBlend - target) < 0.02) bloodBlend = target;

        updateLighting();
        if (Math.abs(gameMinutes - lastSkyMin) >= 10 || Math.abs(bloodBlend - lastSkyBlood) > 0.1
                || (bloodBlend == target && lastSkyBlood != target)) {
            refreshSky();
        }

        int mi = (int) gameMinutes;
        if (mi != lastClockMin) {
            lastClockMin = mi;
        }
    }

    private void updateLighting() {
        double h = hourOfDay(), d = daylightAt(h), warm = 4 * d * (1 - d), bb = bloodBlend;
        daylightNow = d;
        Color moonC = mixC(Color.rgb(150, 175, 235), Color.rgb(215, 36, 34), bb);
        Color sunC = mixC(Color.rgb(235, 228, 205), Color.rgb(255, 150, 80), warm);
        celestial.setColor(mixC(moonC, sunC, d));
        Color ambN = mixC(Color.rgb(62, 74, 102), Color.rgb(122, 34, 40), bb);
        Color ambD = mixC(Color.rgb(112, 120, 134), Color.rgb(150, 112, 92), warm * 0.6);
        ambientLight.setColor(mixC(ambN, ambD, d));
        double f = d > 0.25 ? clamp01((h - 6) / 13.0) : clamp01(((h - 19 + 24) % 24) / 11.0);
        celestial.setTranslateX((f - 0.5) * 2600);
        celestial.setTranslateY(-Math.sin(Math.PI * f) * 1500 - 100);
        celestial.setTranslateZ(-700);
        if (voidMat != null) {
            voidMat.setDiffuseColor(voidColor(d));
        }
    }

    private void refreshSky() {
        double h = hourOfDay(), d = daylightAt(h), warm = 4 * d * (1 - d), bb = bloodBlend;
        int w = 512, hgt = 256;
        double hv = 0.52;
        WritableImage img = new WritableImage(w, hgt);
        PixelWriter pw = img.getPixelWriter();
        Color top = mixC(mixC(Color.rgb(3, 5, 14), Color.rgb(40, 3, 7), bb), Color.rgb(58, 118, 200), d);
        Color hor = mixC(mixC(mixC(Color.rgb(30, 44, 70), Color.rgb(105, 16, 16), bb), Color.rgb(165, 205, 235), d), Color.rgb(255, 140, 70), warm * 0.75);
        Color vc = voidColor(d);
        for (int y = 0; y < hgt; y++) {
            double v = y / (double) (hgt - 1);
            for (int x = 0; x < w; x++) {
                double r, g, b;
                if (v < hv) {
                    double t = Math.pow(v / hv, 1.7);
                    r = (top.getRed() * (1 - t) + hor.getRed() * t) * 255;
                    g = (top.getGreen() * (1 - t) + hor.getGreen() * t) * 255;
                    b = (top.getBlue() * (1 - t) + hor.getBlue() * t) * 255;
                    double cl = Math.sin(x * 0.042 + y * 0.1) * Math.sin(x * 0.022 - y * 0.062) + Math.sin(x * 0.094 + 2) * 0.4;
                    if (cl > 0.2) {
                        double c = (cl - 0.2) * 14 * (1 - t * 0.5) * (0.4 + 1.4 * d);
                        r += c; g += c; b += c * 1.2;
                    }
                } else {
                    double t = Math.min(1, (v - hv) / 0.08);
                    r = (hor.getRed() * (1 - t) + vc.getRed() * t) * 255;
                    g = (hor.getGreen() * (1 - t) + vc.getGreen() * t) * 255;
                    b = (hor.getBlue() * (1 - t) + vc.getBlue() * t) * 255;
                }
                pw.setArgb(x, y, argb(r, g, b));
            }
        }
        double night = 1 - d;
        if (night > 0.05) {
            int n = (int) (700 * night);
            for (int i = 0; i < n; i++) {
                double br = (110 + rng.nextInt(145)) * night;
                pw.setArgb(rng.nextInt(w), rng.nextInt((int) (hgt * 0.46)), argb(br, br * (1 - bb * 0.5), br * (1 - bb * 0.6) + 10));
            }
        }
        double fs = (h - 6) / 13.0;
        if (fs >= 0 && fs <= 1) {
            drawDisc(img, (0.25 + 0.5 * fs) * w, (hv - Math.sin(Math.PI * fs) * 0.40) * hgt, 10, 46,
                    Color.rgb(255, 248, 220), Color.rgb(255, 200, 110), 0.7);
        }
        double fm = ((h - 19 + 24) % 24) / 11.0;
        if (fm >= 0 && fm <= 1 && d < 0.6) {
            double vis = 1 - d / 0.6;
            Color core = mixC(Color.rgb(235, 240, 255), Color.rgb(150, 12, 18), bb);
            Color glw = mixC(Color.rgb(150, 175, 235), Color.rgb(255, 20, 20), bb);
            drawDisc(img, (0.25 + 0.5 * fm) * w, (hv - Math.sin(Math.PI * fm) * 0.40) * hgt,
                    11 + bb * 2, 40 + bb * 24, core, glw, (0.5 + 0.4 * bb) * vis);
        }
        skyMat.setSelfIlluminationMap(img);
        lastSkyMin = gameMinutes;
        lastSkyBlood = bloodBlend;
    }

    private void drawDisc(WritableImage img, double cx, double cy, double rad, double glowRad, Color core, Color glw, double strength) {
        PixelWriter pw = img.getPixelWriter();
        int w = (int) img.getWidth(), hh = (int) img.getHeight();
        for (int y = (int) (cy - glowRad); y <= cy + glowRad; y++) {
            for (int x = (int) (cx - glowRad); x <= cx + glowRad; x++) {
                if (x < 0 || y < 0 || x >= w || y >= hh) continue;
                double dd = Math.sqrt((x - cx) * (x - cx) + (y - cy) * (y - cy));
                if (dd < rad) {
                    pw.setArgb(x, y, argb(core.getRed() * 255, core.getGreen() * 255, core.getBlue() * 255));
                } else if (dd < glowRad) {
                    double a = Math.pow(1 - (dd - rad) / (glowRad - rad), 2.2) * strength;
                    int o = img.getPixelReader().getArgb(x, y);
                    pw.setArgb(x, y, argb(((o >> 16) & 255) + glw.getRed() * 255 * a,
                            ((o >> 8) & 255) + glw.getGreen() * 255 * a, (o & 255) + glw.getBlue() * 255 * a));
                }
            }
        }
    }

    private static int argb(double r, double g, double b) {
        return 0xFF000000 | ((int) Math.max(0, Math.min(255, r)) << 16) | ((int) Math.max(0, Math.min(255, g)) << 8) | (int) Math.max(0, Math.min(255, b));
    }

    private static Color mixC(Color a, Color b, double t) {
        t = clamp01(t);
        return Color.color(a.getRed() + (b.getRed() - a.getRed()) * t,
                a.getGreen() + (b.getGreen() - a.getGreen()) * t,
                a.getBlue() + (b.getBlue() - a.getBlue()) * t);
    }

    private static Color voidColor(double d) {
        return mixC(Color.rgb(9, 12, 12), Color.rgb(62, 80, 54), d);
    }

    private static double clamp01(double v) { return Math.max(0, Math.min(1, v)); }

    private static double smooth(double t) {
        t = clamp01(t);
        return t * t * (3 - 2 * t);
    }

    private static double daylightAt(double h) {
        if (h < 5) return 0;
        if (h < 7.5) return smooth((h - 5) / 2.5);
        if (h < 17) return 1;
        if (h < 19.5) return 1 - smooth((h - 17) / 2.5);
        return 0;
    }

    public double hourOfDay() { return (gameMinutes % 1440) / 60.0; }

    public boolean isNightNow() {
        double h = hourOfDay();
        return h >= 20 || h < 6;
    }

    public boolean isBloodMoonTime() {
        if (!isNightNow()) return false;
        long day = (long) gameMinutes / 1440;
        long nightDay = hourOfDay() < 6 ? day - 1 : day;
        if (nightDay < 0) return false;
        return (dateFromDay(nightDay)[2] - 1) / 7 == 2; // third week (days 15-21)
    }

    private static int[] dateFromDay(long day) {
        int y = 2099, m = 0;
        long d = day;
        while (d >= daysIn(y, m)) {
            d -= daysIn(y, m);
            if (++m == 12) { m = 0; y++; }
        }
        return new int[]{y, m, (int) d + 1};
    }

    private static boolean leap(int y) { return y % 4 == 0 && (y % 100 != 0 || y % 400 == 0); }
    private static int daysIn(int y, int m) {
        int[] d = {31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31};
        return m == 1 && leap(y) ? 29 : d[m];
    }

    public String dateString(long day) {
        int[] d = dateFromDay(day);
        return MONTHS[d[1]] + " " + d[2] + ", " + d[0];
    }

    public String getTimeString() {
        int mi = (int) gameMinutes;
        int mod = mi % 1440, hr = mod / 60, mn = mod % 60;
        return String.format("%02d:%02d %s", hr % 12 == 0 ? 12 : hr % 12, mn, hr >= 12 ? "PM" : "AM");
    }

    public String getPeriodString() {
        double h = hourOfDay();
        String p = h >= 6 && h < 12 ? "MORNING" : h >= 12 && h < 17 ? "AFTERNOON" : h >= 17 && h < 20 ? "EVENING" : "NIGHT";
        return bloodMoon ? p + "  -  BLOOD MOON" : p;
    }

    public Color getPeriodColor() {
        double h = hourOfDay();
        return bloodMoon ? Color.CRIMSON : h >= 6 && h < 17 ? Color.GOLD : h >= 17 && h < 20 ? Color.ORANGE : Color.LIGHTSKYBLUE;
    }

    public double getDaylight() { return daylightNow; }
    public boolean isBloodMoon() { return bloodMoon; }
    public double getBloodBlend() { return bloodBlend; }
    public double getGameMinutes() { return gameMinutes; }
    public void setGameMinutes(double m) { gameMinutes = m; }
}