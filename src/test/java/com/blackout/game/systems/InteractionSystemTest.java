package com.blackout.game.systems;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class InteractionSystemTest {

    static final class Target implements Interactable {
        final String name;
        final double x, z, range;
        int priority;
        boolean available = true, enabled = true;
        int used;

        Target(String name, double x, double z, double range) {
            this.name = name;
            this.x = x;
            this.z = z;
            this.range = range;
        }

        @Override public String prompt() { return name; }
        @Override public double x() { return x; }
        @Override public double z() { return z; }
        @Override public double range() { return range; }
        @Override public int priority() { return priority; }
        @Override public boolean available() { return available; }
        @Override public boolean enabled() { return enabled; }
        @Override public void interact() { used++; }
    }

    @Test
    void nothingInRangeGivesNoTarget() {
        InteractionSystem s = new InteractionSystem();
        s.register(new Target("door", 100, 0, 50));
        assertNull(s.update(0, 0));
        assertFalse(s.use());
    }

    @Test
    void nearestTargetWins() {
        InteractionSystem s = new InteractionSystem();
        Target far = new Target("far", 60, 0, 100), near = new Target("near", 20, 0, 100);
        s.register(far);
        s.register(near);
        assertSame(near, s.update(0, 0));
        assertTrue(s.use());
        assertEquals(1, near.used);
        assertEquals(0, far.used);
    }

    @Test
    void priorityBeatsASmallDistanceDifference() {
        InteractionSystem s = new InteractionSystem();
        Target door = new Target("door", 20, 0, 100), bed = new Target("bed", 35, 0, 100);
        bed.priority = 1;                                   // worth 20 units of closeness
        s.register(door);
        s.register(bed);
        assertSame(bed, s.update(0, 0));
        bed.available = false;
        assertSame(door, s.update(0, 0));
    }

    @Test
    void unavailableTargetsAreInvisibleAndDisabledOnesRefuse() {
        InteractionSystem s = new InteractionSystem();
        Target t = new Target("bed", 10, 0, 100);
        s.register(t);
        t.enabled = false;
        assertSame(t, s.update(0, 0));                      // prompt is still shown...
        assertFalse(s.use());                               // ...but it will not act
        assertEquals(0, t.used);
        t.available = false;
        assertNull(s.update(0, 0));
    }

    @Test
    void eachTargetUsesItsOwnRange() {
        InteractionSystem s = new InteractionSystem();
        s.register(new Target("small", 0, 80, 60));
        assertNull(s.update(0, 0));
        assertEquals("small", s.update(0, 30).prompt());
    }

    @Test
    void clearRemovesEverything() {
        InteractionSystem s = new InteractionSystem();
        s.register(new Target("x", 0, 0, 10));
        s.update(0, 0);
        s.clear();
        assertEquals(0, s.size());
        assertNull(s.current());
    }
}
