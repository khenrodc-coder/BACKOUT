package com.blackout.game.save;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SaveManagerTest {

    @TempDir
    Path dir;
    SaveManager sm;

    @BeforeEach
    void setUp() {
        sm = new SaveManager(dir);
    }

    private static SaveData sample() {
        SaveData d = new SaveData();
        d.seed = -7_046_029_254_386_353_131L;          // needs all 64 bits
        d.savedAtMillis = 1_800_000_000_000L;
        d.gameMinutes = 3 * 1440 + 19 * 60 + 5.5;
        d.x = 123.5;
        d.z = -456.25;
        d.feetY = 110;
        d.yaw = 723.5;
        d.pitch = -12.5;
        d.health = 63.5;
        d.stamina = 40;
        d.medkits = 3;
        d.grenades = 1;
        d.kills = 42;
        d.survivalTime = 912.4;
        d.threat = 7;
        d.torchOn = true;
        d.firstPerson = true;
        d.weapon = "ASSAULT_RIFLE";
        d.magazine.put("PISTOL", 7);
        d.magazine.put("ASSAULT_RIFLE", 30);
        d.reserve.put("PISTOL", 20);
        d.reserve.put("ASSAULT_RIFLE", 90);
        d.doors = "0110";
        d.explored = "1".repeat(30) + "0".repeat(870);
        d.poiFound = "101";
        d.hasWaypoint = true;
        d.waypointX = 800;
        d.waypointZ = -300;
        d.enemies.add(new double[]{2, 100, 200, 450, 90});
        d.enemies.add(new double[]{0, -50, 75.5, 100, 270});
        d.pickups.add(new double[]{1, 10, 20});
        d.openDoorIds.add("d:-1:5:0");
        d.openDoorIds.add("d:2:7:1");
        d.takenIds.add("p:0:4:3");
        return d;
    }

    @Test
    void missingFileIsReportedAsMissing() {
        assertEquals(SaveManager.Status.MISSING, sm.load().status());
        assertFalse(sm.hasValidSave());
    }

    @Test
    void roundTripKeepsEverything() throws IOException {
        SaveData in = sample();
        sm.save(in);
        SaveManager.LoadResult r = sm.load();
        assertEquals(SaveManager.Status.OK, r.status());
        SaveData out = r.data();
        assertEquals(in.seed, out.seed);
        assertEquals(in.gameMinutes, out.gameMinutes);
        assertEquals(in.x, out.x);
        assertEquals(in.z, out.z);
        assertEquals(in.yaw, out.yaw);
        assertEquals(in.health, out.health);
        assertEquals(in.kills, out.kills);
        assertEquals(in.weapon, out.weapon);
        assertEquals(in.magazine, out.magazine);
        assertEquals(in.reserve, out.reserve);
        assertEquals(in.doors, out.doors);
        assertEquals(in.explored, out.explored);
        assertEquals(in.poiFound, out.poiFound);
        assertTrue(out.hasWaypoint);
        assertEquals(800, out.waypointX);
        assertTrue(out.torchOn);
        assertTrue(out.firstPerson);
        assertEquals(2, out.enemies.size());
        assertEquals(450, out.enemies.get(0)[3]);
        assertEquals(1, out.pickups.size());
        assertEquals(java.util.List.of("d:-1:5:0", "d:2:7:1"), out.openDoorIds);
        assertEquals(java.util.List.of("p:0:4:3"), out.takenIds);
        assertTrue(sm.hasValidSave());
    }

    @Test
    void tamperedFileIsRejected() throws IOException {
        sm.save(sample());
        String text = Files.readString(sm.file());
        Files.writeString(sm.file(), text.replace("kills=42", "kills=9999"));
        assertEquals(SaveManager.Status.CORRUPT, sm.load().status());
    }

    @Test
    void truncatedFileIsRejected() throws IOException {
        sm.save(sample());
        byte[] b = Files.readAllBytes(sm.file());
        Files.write(sm.file(), java.util.Arrays.copyOf(b, b.length / 2));
        assertEquals(SaveManager.Status.CORRUPT, sm.load().status());
    }

    @Test
    void garbageIsRejectedWithoutThrowing() throws IOException {
        Files.write(sm.file(), "hello world".getBytes(StandardCharsets.UTF_8));
        assertEquals(SaveManager.Status.CORRUPT, sm.load().status());
        Files.write(sm.file(), new byte[]{(byte) 0xff, (byte) 0xfe, 0, 1, 2});
        assertEquals(SaveManager.Status.CORRUPT, sm.load().status());
    }

    @Test
    void outOfRangeValuesAreRejected() {
        SaveData d = sample();
        d.health = 0;     // a save of a dead player is invalid
        assertEquals(SaveManager.Status.CORRUPT, SaveManager.parse(SaveManager.serialize(d)).status());
        d = sample();
        d.x = Double.NaN;
        assertEquals(SaveManager.Status.CORRUPT, SaveManager.parse(SaveManager.serialize(d)).status());
    }

    @Test
    void newerVersionIsReported() {
        String s = SaveManager.serialize(sample());
        int crcAt = s.lastIndexOf("crc=");
        String body = s.substring(0, crcAt).replace("version=1", "version=99");
        java.util.zip.CRC32 crc = new java.util.zip.CRC32();
        crc.update(body.getBytes(StandardCharsets.UTF_8));
        String file = body + "crc=" + Long.toHexString(crc.getValue()) + "\n";
        assertEquals(SaveManager.Status.NEWER_VERSION, SaveManager.parse(file).status());
    }

    @Test
    void damagedMainSaveFallsBackToBackup() throws IOException {
        SaveData first = sample();
        first.kills = 10;
        sm.save(first);
        SaveData second = sample();
        second.kills = 20;
        sm.save(second);                                // first becomes the backup
        Files.writeString(sm.file(), "broken");
        SaveManager.LoadResult r = sm.load();
        assertEquals(SaveManager.Status.OK, r.status());
        assertNotNull(r.data());
        assertEquals(10, r.data().kills);
    }

    @Test
    void savingNeverLeavesTempFileBehind() throws IOException {
        sm.save(sample());
        try (var s = Files.list(dir)) {
            assertTrue(s.noneMatch(p -> p.getFileName().toString().endsWith(".tmp")));
        }
    }

    @Test
    void deleteRemovesSaveAndBackup() throws IOException {
        sm.save(sample());
        sm.save(sample());
        sm.delete();
        assertEquals(SaveManager.Status.MISSING, sm.load().status());
    }

    private static String resign(String body) {
        java.util.zip.CRC32 crc = new java.util.zip.CRC32();
        crc.update(body.getBytes(StandardCharsets.UTF_8));
        return body + "crc=" + Long.toHexString(crc.getValue()) + "\n";
    }

    @Test
    void savesFromBeforeTheStreamedWorldStillLoad() {
        String s = SaveManager.serialize(sample());
        String body = s.substring(0, s.lastIndexOf("crc=")).replaceAll("ws\\.(doors|taken)=[^\\n]*\\n", "");
        SaveManager.LoadResult r = SaveManager.parse(resign(body));
        assertEquals(SaveManager.Status.OK, r.status());
        assertTrue(r.data().openDoorIds.isEmpty());
        assertTrue(r.data().takenIds.isEmpty());
    }

    @Test
    void garbageWorldStateIdsAreDroppedNotTrusted() {
        String s = SaveManager.serialize(sample());
        String body = s.substring(0, s.lastIndexOf("crc=")).replaceAll("ws\\.doors=[^\\n]*\\n", "ws.doors=d:1:1:1,../../x,d:9:9:9\n");
        SaveManager.LoadResult r = SaveManager.parse(resign(body));
        assertEquals(SaveManager.Status.OK, r.status());
        assertEquals(java.util.List.of("d:1:1:1", "d:9:9:9"), r.data().openDoorIds);
    }
}
