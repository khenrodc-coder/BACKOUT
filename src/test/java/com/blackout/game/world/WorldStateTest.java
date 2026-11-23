package com.blackout.game.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

class WorldStateTest {

    @Test
    void doorsAndPickupsAreRemembered() {
        WorldState w = new WorldState();
        String d = WorldState.id('d', 3, 5, 0), p = WorldState.id('p', -1, 4, 12);
        assertFalse(w.isDoorOpen(d));
        w.setDoorOpen(d, true);
        w.markTaken(p);
        assertTrue(w.isDoorOpen(d));
        assertTrue(w.isTaken(p));
        w.setDoorOpen(d, false);
        assertFalse(w.isDoorOpen(d));
    }

    @Test
    void invalidIdsAreIgnored() {
        WorldState w = new WorldState();
        w.setDoorOpen("../../etc/passwd", true);
        w.setDoorOpen("d:1:2", true);
        w.markTaken("x\ny");
        w.markTaken(null);
        assertEquals(0, w.size());
        assertFalse(WorldState.validId("d:12345:1:1"));
        assertTrue(WorldState.validId("d:-3:5:0"));
    }

    @Test
    void loadReplacesEverythingAndFiltersGarbage() {
        WorldState w = new WorldState();
        w.setDoorOpen("d:1:1:1", true);
        w.load(List.of("d:2:2:2", "garbage"), List.of("p:3:3:3", "also bad"));
        assertFalse(w.isDoorOpen("d:1:1:1"));
        assertTrue(w.isDoorOpen("d:2:2:2"));
        assertTrue(w.isTaken("p:3:3:3"));
        assertEquals(2, w.size());
        assertEquals(List.of("d:2:2:2"), w.openDoorIds());
    }
}
