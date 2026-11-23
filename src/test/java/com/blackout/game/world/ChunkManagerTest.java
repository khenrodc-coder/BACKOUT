package com.blackout.game.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

class ChunkManagerTest {

    private final List<String> log = new ArrayList<>();

    private ChunkManager<String> mgr(int budget) {
        return new ChunkManager<>(100, 150, 220, budget, (cx, cz) -> cx >= 0 && cx < 10 && cz >= 0 && cz < 10, new ChunkManager.Loader<String>() {
            @Override
            public String load(int cx, int cz) {
                log.add("L" + cx + "," + cz);
                return cx + "," + cz;
            }

            @Override
            public void unload(int cx, int cz, String c) {
                log.add("U" + cx + "," + cz);
            }
        });
    }

    @Test
    void loadsOnlyChunksNearThePlayerThatExist() {
        ChunkManager<String> m = mgr(100);
        m.update(50, 50);
        assertTrue(m.isLoaded(0, 0));
        assertTrue(m.isLoaded(1, 1));                    // within 150 of the player
        assertFalse(m.isLoaded(5, 5));                   // too far
        assertFalse(m.isLoaded(-1, 0));                  // does not exist
        for (String s : log) {
            assertTrue(s.startsWith("L"));
        }
    }

    @Test
    void loadsNearestFirstAndRespectsTheBudget() {
        ChunkManager<String> m = mgr(2);
        assertEquals(2, m.update(50, 50));
        assertTrue(m.isLoaded(0, 0), "the chunk the player stands in comes first");
        int before = m.loadedCount();
        m.update(50, 50);
        assertTrue(m.loadedCount() > before, "the rest follow on later updates");
        for (int i = 0; i < 20; i++) {
            m.update(50, 50);
        }
        int settled = m.loadedCount();
        assertEquals(0, m.update(50, 50));
        assertEquals(settled, m.loadedCount());
    }

    @Test
    void hysteresisKeepsChunksLoadedUntilTheyAreClearlyFarAway() {
        ChunkManager<String> m = mgr(100);
        m.update(50, 50);
        assertTrue(m.isLoaded(1, 1));
        m.update(50 + 160, 50);                           // chunk (1,1) is now 60 away: inside both radii
        assertTrue(m.isLoaded(0, 0));                     // 110 away: beyond load distance but inside unload distance
        m.update(50 + 400, 50);
        assertFalse(m.isLoaded(0, 0));
        assertTrue(log.contains("U0,0"));
    }

    @Test
    void walkingBackAndForthAcrossABorderDoesNotThrash() {
        ChunkManager<String> m = mgr(100);
        m.update(199, 60);
        long loads = m.totalLoads();
        for (int i = 0; i < 100; i++) {
            m.update(i % 2 == 0 ? 201 : 199, 60);
        }
        assertEquals(loads, m.totalLoads(), "no chunk should be rebuilt while crossing a border");
        assertEquals(0, m.totalUnloads());
    }

    @Test
    void unloadAllCallsTheLoaderForEveryChunk() {
        ChunkManager<String> m = mgr(100);
        m.update(500, 500);
        int n = m.loadedCount();
        assertTrue(n > 0);
        m.unloadAll();
        assertEquals(0, m.loadedCount());
        assertEquals(n, m.totalUnloads());
        long u = log.stream().filter(s -> s.startsWith("U")).count();
        assertEquals(n, u);
    }

    @Test
    void loadedCountStaysBoundedWhileTravellingAcrossTheWholeMap() {
        ChunkManager<String> m = mgr(3);
        for (double x = 0; x < 1000; x += 7) {
            m.update(x, x * 0.5);
        }
        assertTrue(m.peakLoaded() <= 25, "peak " + m.peakLoaded());   // about 5 x 5 chunks at most: load and unload radius keep it bounded
        assertTrue(m.totalUnloads() > 0);
        assertEquals(m.totalLoads() - m.totalUnloads(), m.loadedCount());
    }

    @Test
    void profilingCountersAreFilled() {
        ChunkManager<String> m = mgr(100);
        m.update(50, 50);
        assertTrue(m.totalLoads() > 0);
        assertTrue(m.maxLoadMillis() >= m.averageLoadMillis());
    }

    @Test
    void negativeCoordinatesWork() {
        ChunkManager<String> m = new ChunkManager<>(100, 120, 200, 50, (cx, cz) -> true, new ChunkManager.Loader<String>() {
            @Override
            public String load(int cx, int cz) {
                return cx + "," + cz;
            }

            @Override
            public void unload(int cx, int cz, String c) {
            }
        });
        m.update(-250, -50);
        assertTrue(m.isLoaded(-3, -1));
        assertTrue(m.isLoaded(-3, 0));
    }
}
