package com.blackout.game.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.blackout.game.world.DistrictPlan.Kind;
import com.blackout.game.world.DistrictPlan.Lot;
import com.blackout.game.world.DistrictPlan.Plan;

import org.junit.jupiter.api.Test;

class DistrictPlanTest {

    private static final long[] SEEDS = {1, 2, 3, 99, 4242, 20261117, -7046029254386353131L, 31337};

    private interface PlanCheck {

        void run(long seed, Plan p);
    }

    private static void forEachPlan(PlanCheck c) {
        for (long s : SEEDS) {
            for (int cx = DistrictPlan.MIN_CX; cx <= DistrictPlan.MAX_CX; cx++) {
                for (int cz = DistrictPlan.MIN_CZ; cz <= DistrictPlan.MAX_CZ; cz++) {
                    c.run(s, DistrictPlan.generate(s, cx, cz));
                }
            }
        }
    }

    @Test
    void theDistrictIsA6By4GridNorthOfTheTown() {
        assertTrue(DistrictPlan.exists(-3, 4));
        assertTrue(DistrictPlan.exists(2, 7));
        assertFalse(DistrictPlan.exists(3, 4));
        assertFalse(DistrictPlan.exists(0, 3));
        assertFalse(DistrictPlan.exists(0, 8));
    }

    @Test
    void aChunkNeverDependsOnLoadOrder() {
        Plan a = DistrictPlan.generate(4242, -1, 5);
        DistrictPlan.generate(4242, 2, 7);
        Plan b = DistrictPlan.generate(4242, -1, 5);
        assertEquals(a.pattern, b.pattern);
        assertEquals(a.lots.size(), b.lots.size());
        assertEquals(a.cars.size(), b.cars.size());
        for (int i = 0; i < a.lots.size(); i++) {
            assertEquals(a.lots.get(i).x, b.lots.get(i).x);
            assertEquals(a.lots.get(i).kind, b.lots.get(i).kind);
        }
    }

    @Test
    void differentSeedsGiveDifferentCities() {
        Set<String> patterns = new HashSet<>();
        for (long s : SEEDS) {
            StringBuilder sb = new StringBuilder();
            for (int cx = -3; cx <= 2; cx++) {
                for (int cz = 4; cz <= 7; cz++) {
                    sb.append(DistrictPlan.generate(s, cx, cz).pattern.charAt(0));
                }
            }
            patterns.add(sb.toString());
        }
        assertTrue(patterns.size() >= SEEDS.length - 1, "cities look too alike: " + patterns.size());
    }

    @Test
    void everyCityHasExactlyOneSupermarketAndOneGasStation() {
        for (long s : SEEDS) {
            int sup = 0, gas = 0;
            for (int cx = -3; cx <= 2; cx++) {
                for (int cz = 4; cz <= 7; cz++) {
                    for (Lot l : DistrictPlan.generate(s, cx, cz).lots) {
                        if (l.kind == Kind.SUPERMARKET) {
                            sup++;
                        }
                        if (l.kind == Kind.GAS_SHOP) {
                            gas++;
                        }
                    }
                }
            }
            assertEquals(1, sup, "seed " + s);
            assertEquals(1, gas, "seed " + s);
        }
    }

    @Test
    void landmarksPointAtRealBuildings() {
        for (long s : SEEDS) {
            List<Object[]> lm = DistrictPlan.landmarks(s);
            assertEquals(3, lm.size());
            for (Object[] l : lm) {
                int cx = (int) Math.floor((double) l[1] / DistrictPlan.CHUNK), cz = (int) Math.floor((double) l[2] / DistrictPlan.CHUNK);
                assertTrue(DistrictPlan.exists(cx, cz), l[0] + " outside the district, seed " + s);
            }
        }
    }

    @Test
    void lotsStayInsideTheirBlockAndSolidLotsNeverOverlap() {
        forEachPlan((seed, p) -> {
            for (Lot l : p.lots) {
                double[] a = l.rect();
                assertTrue(a[0] >= p.ix0 - 0.01 && a[2] <= p.ix1 + 0.01 && a[1] >= p.iz0 - 0.01 && a[3] <= p.iz1 + 0.01,
                        l.kind + " leaves its block in " + p.cx + "," + p.cz + " seed " + seed);
            }
            for (int i = 0; i < p.lots.size(); i++) {
                for (int j = i + 1; j < p.lots.size(); j++) {
                    Lot a = p.lots.get(i), b = p.lots.get(j);
                    if (!a.solid() || !b.solid()) {
                        continue;
                    }
                    double[] r = a.rect(), q = b.rect();
                    boolean overlap = r[0] < q[2] + 6 && r[2] > q[0] - 6 && r[1] < q[3] + 6 && r[3] > q[1] - 6;
                    assertFalse(overlap, a.kind + " and " + b.kind + " too close in " + p.cx + "," + p.cz + " seed " + seed);
                }
            }
        });
    }

    @Test
    void shopDoorsFaceAStreetAndHaveClearSpaceInFront() {
        forEachPlan((seed, p) -> {
            for (Lot l : p.lots) {
                if (!l.enterable()) {
                    continue;
                }
                assertTrue(l.front == 1 || l.front == -1);
                double[] a = l.rect();
                double edge = l.front == 1 ? p.iz1 - a[3] : a[1] - p.iz0;
                assertTrue(edge < 1, l.kind + " door does not face the street, seed " + seed);
                for (Lot o : p.lots) {
                    if (o != l && o.solid()) {
                        double[] q = o.rect();
                        double fz0 = l.front == 1 ? a[3] : a[1] - 70, fz1 = l.front == 1 ? a[3] + 70 : a[1];
                        boolean block = q[0] < a[2] && q[2] > a[0] && q[1] < fz1 && q[3] > fz0;
                        assertFalse(block, o.kind + " blocks the door of " + l.kind + ", seed " + seed);
                    }
                }
            }
        });
    }

    @Test
    void carsStayOnParkingOrKerbsAndNeverInsideBuildings() {
        forEachPlan((seed, p) -> {
            for (DistrictPlan.Car c : p.cars) {
                for (Lot l : p.lots) {
                    if (l.solid()) {
                        double[] a = l.rect();
                        boolean inside = c.x > a[0] - 20 && c.x < a[2] + 20 && c.z > a[1] - 20 && c.z < a[3] + 20;
                        assertFalse(inside, "car inside " + l.kind + " in " + p.cx + "," + p.cz + " seed " + seed);
                    }
                }
                assertTrue(c.x > p.x0 - 40 && c.x < p.x1 + 40 && c.z > p.z0 - 40 && c.z < p.z1 + 40);
            }
        });
    }

    @Test
    void treesAndBenchesAreNotInsideBuildings() {
        forEachPlan((seed, p) -> {
            for (DistrictPlan.Spot t : p.trees) {
                for (Lot l : p.lots) {
                    if (l.solid()) {
                        double[] a = l.rect();
                        assertFalse(t.x > a[0] && t.x < a[2] && t.z > a[1] && t.z < a[3], "tree inside " + l.kind + " seed " + seed);
                    }
                }
            }
        });
    }

    @Test
    void streetLightsAreNotInFrontOfDoors() {
        forEachPlan((seed, p) -> {
            for (double[] li : p.lights) {
                for (Lot l : p.lots) {
                    if (l.enterable()) {
                        assertTrue(Math.abs(li[0] - l.x) > l.w / 2 - 6 || Math.abs(li[1] - l.z) > l.d / 2 + 80, "light in front of a door, seed " + seed);
                    }
                }
            }
        });
    }

    @Test
    void noCarEverBlocksTheApproachToAShopDoor() {
        forEachPlan((seed, p) -> {
            for (DistrictPlan.Car c : p.cars) {
                assertFalse(DistrictPlan.blocksDoor(p, c.x, c.z, c.yaw), "car blocks a door in " + p.cx + "," + p.cz + " seed " + seed);
            }
        });
    }
}
