package com.blackout.game.core;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/** Persistent user settings (graphics, audio, mouse, FOV, fullscreen, key bindings). */
public final class Settings {

    public enum Quality {
        LOW("Low", 0.55, false), MEDIUM("Medium", 0.8, true), HIGH("High", 1.0, true);

        public final String label;
        /** Multiplier applied to every draw distance. */
        public final double viewScale;
        public final boolean bloom;

        Quality(String label, double viewScale, boolean bloom) {
            this.label = label;
            this.viewScale = viewScale;
            this.bloom = bloom;
        }
    }

    private static Settings instance;

    public Quality quality = Quality.HIGH;
    /** 0..1. Stored now; used once the game has sound. */
    public double volume = 0.8;
    public double sensitivity = 1.0;
    public double fovScale = 1.0;
    public boolean fullscreen = true;
    public final InputMap input = new InputMap();

    private final Path file;

    public Settings(Path dir) {
        this.file = dir.resolve("settings.properties");
    }

    public static synchronized Settings get() {
        if (instance == null) {
            instance = new Settings(FileUtil.dataDir());
            instance.load();
        }
        return instance;
    }

    public void resetDefaults() {
        quality = Quality.HIGH;
        volume = 0.8;
        sensitivity = 1.0;
        fovScale = 1.0;
        fullscreen = true;
        input.resetDefaults();
    }

    private static double clamp(double v, double lo, double hi) {
        return Double.isNaN(v) ? lo : Math.max(lo, Math.min(hi, v));
    }

    public void load() {
        if (!Files.isRegularFile(file)) {
            return;
        }
        Properties p = new Properties();
        try (InputStream in = Files.newInputStream(file)) {
            p.load(in);
        } catch (IOException | IllegalArgumentException e) {
            return; // unreadable: keep defaults
        }
        try {
            quality = Quality.valueOf(p.getProperty("quality", quality.name()));
        } catch (IllegalArgumentException ignored) {
            // keep default
        }
        volume = clamp(parse(p, "volume", volume), 0, 1);
        sensitivity = clamp(parse(p, "sensitivity", sensitivity), 0.3, 2.5);
        fovScale = clamp(parse(p, "fovScale", fovScale), 0.8, 1.3);
        fullscreen = Boolean.parseBoolean(p.getProperty("fullscreen", Boolean.toString(fullscreen)));
        if ("2".equals(p.getProperty("settingsVersion"))) {
            input.read(p);       // older files used separate door / sleep keys: keep the new defaults
        }
    }

    private static double parse(Properties p, String key, double def) {
        try {
            return Double.parseDouble(p.getProperty(key, Double.toString(def)));
        } catch (NumberFormatException e) {
            return def;
        }
    }

    public void save() {
        Properties p = new Properties();
        p.setProperty("settingsVersion", "2");
        p.setProperty("quality", quality.name());
        p.setProperty("volume", Double.toString(volume));
        p.setProperty("sensitivity", Double.toString(sensitivity));
        p.setProperty("fovScale", Double.toString(fovScale));
        p.setProperty("fullscreen", Boolean.toString(fullscreen));
        input.write(p);
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            p.store(out, "BLACKOUT settings");
            FileUtil.atomicWrite(file, out.toByteArray());
        } catch (IOException e) {
            System.err.println("Could not save settings: " + e.getMessage());
        }
    }
}
