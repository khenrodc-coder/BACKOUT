package com.blackout.game.save;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.zip.CRC32;

import com.blackout.game.core.FileUtil;

/**
 * Versioned, crash-safe save file. Text format (key=value lines) closed by a CRC32 line so truncated or edited files are
 * detected. Writes go to a temp file first; the previous good save is kept as a backup and used if the main file is bad.
 */
public final class SaveManager {

    public static final int VERSION = 1;
    private static final String HEADER = "BLACKOUT_SAVE";
    private static final int MAX_ENTITIES = 400;

    public enum Status { MISSING, OK, CORRUPT, NEWER_VERSION }

    public record LoadResult(Status status, SaveData data, String message) {
    }

    private final Path file, backup;

    public SaveManager(Path dir) {
        this.file = dir.resolve("save1.dat");
        this.backup = dir.resolve("save1.bak");
    }

    public static SaveManager standard() {
        return new SaveManager(FileUtil.dataDir());
    }

    public Path file() {
        return file;
    }

    public boolean hasValidSave() {
        return load().status() == Status.OK;
    }

    public void delete() {
        try {
            Files.deleteIfExists(file);
            Files.deleteIfExists(backup);
        } catch (IOException e) {
            System.err.println("Could not delete save: " + e.getMessage());
        }
    }

    // ---------------------------------------------------------------- writing

    public void save(SaveData d) throws IOException {
        byte[] bytes = serialize(d).getBytes(StandardCharsets.UTF_8);
        if (Files.isRegularFile(file) && read(file).status() == Status.OK) {
            Files.copy(file, backup, StandardCopyOption.REPLACE_EXISTING); // keep the last good save
        }
        FileUtil.atomicWrite(file, bytes);
    }

    static String serialize(SaveData d) {
        StringBuilder sb = new StringBuilder();
        sb.append(HEADER).append('\n');
        put(sb, "version", VERSION);
        put(sb, "seed", d.seed);
        put(sb, "savedAt", d.savedAtMillis);
        put(sb, "gameMinutes", d.gameMinutes);
        put(sb, "x", d.x);
        put(sb, "z", d.z);
        put(sb, "feetY", d.feetY);
        put(sb, "yaw", d.yaw);
        put(sb, "pitch", d.pitch);
        put(sb, "health", d.health);
        put(sb, "stamina", d.stamina);
        put(sb, "medkits", d.medkits);
        put(sb, "grenades", d.grenades);
        put(sb, "kills", d.kills);
        put(sb, "survivalTime", d.survivalTime);
        put(sb, "threat", d.threat);
        put(sb, "torchOn", d.torchOn);
        put(sb, "firstPerson", d.firstPerson);
        put(sb, "weapon", d.weapon);
        for (Map.Entry<String, Integer> e : d.magazine.entrySet()) {
            put(sb, "mag." + e.getKey(), e.getValue());
        }
        for (Map.Entry<String, Integer> e : d.reserve.entrySet()) {
            put(sb, "res." + e.getKey(), e.getValue());
        }
        put(sb, "doors", d.doors);
        put(sb, "explored", d.explored);
        put(sb, "poiFound", d.poiFound);
        put(sb, "hasWaypoint", d.hasWaypoint);
        put(sb, "waypointX", d.waypointX);
        put(sb, "waypointZ", d.waypointZ);
        for (int i = 0; i < d.enemies.size(); i++) {
            put(sb, "enemy." + i, join(d.enemies.get(i)));
        }
        for (int i = 0; i < d.pickups.size(); i++) {
            put(sb, "pickup." + i, join(d.pickups.get(i)));
        }
        CRC32 crc = new CRC32();
        crc.update(sb.toString().getBytes(StandardCharsets.UTF_8));
        sb.append("crc=").append(Long.toHexString(crc.getValue())).append('\n');
        return sb.toString();
    }

    private static void put(StringBuilder sb, String k, Object v) {
        sb.append(k).append('=').append(v).append('\n');
    }

    private static String join(double[] a) {
        StringBuilder s = new StringBuilder();
        for (int i = 0; i < a.length; i++) {
            if (i > 0) {
                s.append(',');
            }
            s.append(a[i]);
        }
        return s.toString();
    }

    // ---------------------------------------------------------------- reading

    /** Loads the main save, falling back to the backup if the main file is damaged. */
    public LoadResult load() {
        LoadResult main = read(file);
        if (main.status() == Status.OK || main.status() == Status.NEWER_VERSION) {
            return main;
        }
        if (Files.isRegularFile(backup)) {
            LoadResult b = read(backup);
            if (b.status() == Status.OK) {
                return new LoadResult(Status.OK, b.data(), "Main save was damaged - restored the previous save.");
            }
        }
        return main;
    }

    private static LoadResult read(Path p) {
        if (!Files.isRegularFile(p)) {
            return new LoadResult(Status.MISSING, null, "No saved game.");
        }
        String text;
        try {
            text = Files.readString(p, StandardCharsets.UTF_8);
        } catch (IOException | RuntimeException e) {
            return new LoadResult(Status.CORRUPT, null, "Save file could not be read.");
        }
        return parse(text);
    }

    static LoadResult parse(String text) {
        try {
            int crcAt = text.lastIndexOf("crc=");
            if (!text.startsWith(HEADER + "\n") || crcAt < 0) {
                return bad("Save file is not valid.");
            }
            CRC32 crc = new CRC32();
            crc.update(text.substring(0, crcAt).getBytes(StandardCharsets.UTF_8));
            String want = text.substring(crcAt + 4).trim();
            if (!Long.toHexString(crc.getValue()).equals(want)) {
                return bad("Save file is damaged (checksum mismatch).");
            }
            Map<String, String> kv = new LinkedHashMap<>();
            for (String line : text.substring(HEADER.length() + 1, crcAt).split("\n")) {
                int eq = line.indexOf('=');
                if (eq > 0) {
                    kv.put(line.substring(0, eq), line.substring(eq + 1));
                }
            }
            int version = (int) num(kv, "version", 0, 1_000_000);
            if (version > VERSION) {
                return new LoadResult(Status.NEWER_VERSION, null, "Save was made by a newer version of the game.");
            }
            SaveData d = new SaveData();
            d.seed = Long.parseLong(kv.getOrDefault("seed", "x"));   // exact: a double would lose bits
            d.savedAtMillis = (long) num(kv, "savedAt", 0, 9.3e18);
            d.gameMinutes = num(kv, "gameMinutes", 0, 1e9);
            d.x = num(kv, "x", -5000, 5000);
            d.z = num(kv, "z", -5000, 5000);
            d.feetY = num(kv, "feetY", -2000, 2000);
            d.yaw = num(kv, "yaw", -1e6, 1e6);
            d.pitch = num(kv, "pitch", -90, 90);
            d.health = num(kv, "health", 1, 100);          // a save with 0 health would be a corpse
            d.stamina = num(kv, "stamina", 0, 100);
            d.medkits = (int) num(kv, "medkits", 0, 999);
            d.grenades = (int) num(kv, "grenades", 0, 999);
            d.kills = (int) num(kv, "kills", 0, 1e9);
            d.survivalTime = num(kv, "survivalTime", 0, 1e9);
            d.threat = num(kv, "threat", 0, 1e6);
            d.torchOn = bool(kv, "torchOn");
            d.firstPerson = bool(kv, "firstPerson");
            d.weapon = str(kv, "weapon", "[A-Z_]{1,24}");
            for (Map.Entry<String, String> e : kv.entrySet()) {
                if (e.getKey().startsWith("mag.")) {
                    d.magazine.put(e.getKey().substring(4), (int) num(kv, e.getKey(), 0, 9999));
                } else if (e.getKey().startsWith("res.")) {
                    d.reserve.put(e.getKey().substring(4), (int) num(kv, e.getKey(), 0, 9999));
                }
            }
            d.doors = str(kv, "doors", "[01]{0,5000}");
            d.explored = str(kv, "explored", "[01]{0,10000}");
            d.poiFound = str(kv, "poiFound", "[01]{0,500}");
            d.hasWaypoint = bool(kv, "hasWaypoint");
            d.waypointX = num(kv, "waypointX", -5000, 5000);
            d.waypointZ = num(kv, "waypointZ", -5000, 5000);
            for (int i = 0; i < MAX_ENTITIES && kv.containsKey("enemy." + i); i++) {
                d.enemies.add(list(kv.get("enemy." + i), 5));
            }
            for (int i = 0; i < MAX_ENTITIES && kv.containsKey("pickup." + i); i++) {
                d.pickups.add(list(kv.get("pickup." + i), 3));
            }
            return new LoadResult(Status.OK, d, "OK");
        } catch (RuntimeException e) {
            return bad("Save file is damaged: " + e.getMessage());
        }
    }

    private static LoadResult bad(String m) {
        return new LoadResult(Status.CORRUPT, null, m);
    }

    private static double num(Map<String, String> kv, String key, double lo, double hi) {
        String v = kv.get(key);
        if (v == null) {
            throw new IllegalArgumentException("missing " + key);
        }
        double d = Double.parseDouble(v);
        if (Double.isNaN(d) || Double.isInfinite(d) || d < lo || d > hi) {
            throw new IllegalArgumentException(key + " out of range");
        }
        return d;
    }

    private static boolean bool(Map<String, String> kv, String key) {
        String v = kv.get(key);
        if (!"true".equals(v) && !"false".equals(v)) {
            throw new IllegalArgumentException("bad " + key);
        }
        return Boolean.parseBoolean(v);
    }

    private static String str(Map<String, String> kv, String key, String regex) {
        String v = kv.get(key);
        if (v == null || !Pattern.matches(regex, v)) {
            throw new IllegalArgumentException("bad " + key);
        }
        return v;
    }

    private static double[] list(String v, int n) {
        String[] parts = v.split(",", -1);
        if (parts.length != n) {
            throw new IllegalArgumentException("bad list");
        }
        double[] out = new double[n];
        for (int i = 0; i < n; i++) {
            out[i] = Double.parseDouble(parts[i]);
            if (Double.isNaN(out[i]) || Double.isInfinite(out[i])) {
                throw new IllegalArgumentException("bad number");
            }
        }
        return out;
    }
}
