package com.blackout.game.environment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import com.blackout.game.core.Dims;

import org.junit.jupiter.api.Test;

class HouseLayoutTest {

    private static final int SEEDS = 300;

    private static HouseLayout make(int seed) {
        Random sz = new Random(seed * 31L + 5);
        double w = 300 + sz.nextInt(141), d = 310 + sz.nextInt(51);
        return HouseLayout.generate(w, d, new Random(seed), seed % 5 == 4);
    }

    private static boolean inRoom(HouseLayout L, double[] a) {
        for (HouseLayout.Room r : L.rooms) {
            if (a[0] >= r.x0 - 0.6 && a[2] <= r.x1 + 0.6 && a[1] >= r.z0 - 0.6 && a[3] <= r.z1 + 0.6) {
                return true;
            }
        }
        return false;
    }

    private static List<double[]> wallRects(HouseLayout L) {
        List<double[]> out = new ArrayList<>();
        double t = L.P / 2;
        for (HouseLayout.Wall w : L.walls) {
            if (!w.alongX) {
                out.add(new double[]{w.fixed - t, w.from, w.fixed + t, w.to});
                continue;
            }
            List<double[]> ops = new ArrayList<>(w.openings);
            ops.sort((a, b) -> Double.compare(a[0], b[0]));
            double cur = w.from;
            for (double[] o : ops) {
                out.add(new double[]{cur, w.fixed - t, o[0] - o[1] / 2, w.fixed + t});
                cur = o[0] + o[1] / 2;
            }
            out.add(new double[]{cur, w.fixed - t, w.to, w.fixed + t});
        }
        return out;
    }

    @Test
    void roomsAreBigEnoughForTheirFurniture() {
        for (int s = 0; s < SEEDS; s++) {
            HouseLayout L = make(s);
            assertTrue(L.bedroom.x1 - L.bedroom.x0 >= 176 - 0.01, "bedroom width seed " + s);
            assertTrue(L.bedroom.z1 - L.bedroom.z0 >= 136 - 0.01, "bedroom depth seed " + s);
            assertTrue(L.front.z1 - L.front.z0 >= 150, "living depth seed " + s);
            assertTrue(L.bath.x1 - L.bath.x0 >= 92 - 0.01, "bath width seed " + s);
        }
    }

    @Test
    void theFrontDoorReachesEveryDoorway() {
        for (int s = 0; s < SEEDS; s++) {
            assertTrue(make(s).allDoorwaysReachable(), "seed " + s);
        }
    }

    @Test
    void furnitureStaysInsideRoomsAndNeverOverlaps() {
        for (int s = 0; s < SEEDS; s++) {
            HouseLayout L = make(s);
            List<HouseLayout.Prop> solid = new ArrayList<>();
            for (HouseLayout.Prop p : L.props) {
                assertTrue(inRoom(L, p.aabb()), p.kind + " leaves its room, seed " + s);
                if (p.collide) {
                    solid.add(p);
                }
            }
            for (int i = 0; i < solid.size(); i++) {
                for (int j = i + 1; j < solid.size(); j++) {
                    double[] a = solid.get(i).aabb(), b = solid.get(j).aabb();
                    boolean overlap = a[0] < b[2] - 0.01 && a[2] > b[0] + 0.01 && a[1] < b[3] - 0.01 && a[3] > b[1] + 0.01;
                    assertFalse(overlap, solid.get(i).kind + " overlaps " + solid.get(j).kind + ", seed " + s);
                }
            }
        }
    }

    @Test
    void doorsAndDoorwaysStayClear() {
        for (int s = 0; s < SEEDS; s++) {
            HouseLayout L = make(s);
            double zf = L.innerD / 2;
            double[][] zones = new double[1 + L.doorways.size()][];
            zones[0] = new double[]{L.doorX - 32, zf - 40, L.doorX + 32, zf};
            for (int i = 0; i < L.doorways.size(); i++) {
                HouseLayout.Doorway dw = L.doorways.get(i);
                zones[1 + i] = new double[]{dw.cx - dw.width / 2, L.zp - 30, dw.cx + dw.width / 2, L.zp + 30};
            }
            for (HouseLayout.Prop p : L.props) {
                if (!p.collide) {
                    continue;
                }
                double[] a = p.aabb();
                for (double[] z : zones) {
                    boolean overlap = a[0] < z[2] && a[2] > z[0] && a[1] < z[3] && a[3] > z[1];
                    assertFalse(overlap, p.kind + " blocks a door, seed " + s);
                }
            }
        }
    }

    @Test
    void everyHouseHasABedAgainstTheBackWall() {
        for (int s = 0; s < SEEDS; s++) {
            HouseLayout L = make(s);
            HouseLayout.Prop bed = null;
            for (HouseLayout.Prop p : L.props) {
                if (p.kind == HouseLayout.Kind.BED) {
                    bed = p;
                }
            }
            assertNotNull(bed, "no bed, seed " + s);
            assertTrue(L.bedroom.contains(bed.x, bed.z), "bed outside the bedroom, seed " + s);
            assertTrue(bed.aabb()[1] - L.bedroom.z0 < 4, "bed not against the back wall, seed " + s);
        }
    }

    @Test
    void spawnAndLootPointsAreFreeAndReachable() {
        for (int s = 0; s < SEEDS; s++) {
            HouseLayout L = make(s);
            assertFalse(L.spawnPoints.isEmpty(), "no spawn points, seed " + s);
            for (double[] p : L.spawnPoints) {
                assertNotNull(L.roomAt(p[0], p[1]), "spawn outside rooms, seed " + s);
                assertTrue(L.isReachable(p[0], p[1]), "unreachable spawn, seed " + s);
                for (HouseLayout.Prop pr : L.props) {
                    if (pr.collide) {
                        double[] a = pr.aabb();
                        double dx = Math.max(Math.max(a[0] - p[0], p[0] - a[2]), 0), dz = Math.max(Math.max(a[1] - p[1], p[1] - a[3]), 0);
                        assertTrue(Math.hypot(dx, dz) >= 33, "spawn hugs " + pr.kind + ", seed " + s);
                    }
                }
            }
            for (double[] p : L.lootSpots) {
                assertTrue(L.isReachable(p[0], p[1]), "unreachable loot, seed " + s);
            }
        }
    }

    @Test
    void windowsAreInsideTheirWallsAndAwayFromTheDoor() {
        for (int s = 0; s < SEEDS; s++) {
            HouseLayout L = make(s);
            for (HouseLayout.Opening o : L.openings) {
                double lo = o.center - o.width / 2, hi = o.center + o.width / 2;
                boolean alongX = o.side == HouseLayout.Side.FRONT || o.side == HouseLayout.Side.BACK;
                double limit = (alongX ? L.innerW : L.innerD) / 2;
                assertTrue(lo >= -limit && hi <= limit, "opening outside wall, seed " + s);
                if (!o.door && o.side == HouseLayout.Side.FRONT) {
                    assertTrue(hi <= L.doorX - Dims.HOUSE_DOOR_W / 2 + 0.01 || lo >= L.doorX + Dims.HOUSE_DOOR_W / 2 - 0.01, "window overlaps door, seed " + s);
                }
                if (!o.door && o.side == HouseLayout.Side.BACK) {
                    boolean inBed = lo >= L.bedroom.x0 && hi <= L.bedroom.x1, inBath = lo >= L.bath.x0 && hi <= L.bath.x1;
                    assertTrue(inBed || inBath, "back window crosses the divider, seed " + s);
                }
                if (!o.door && !alongX) {
                    boolean inFront = lo >= L.front.z0 && hi <= L.front.z1, inBack = hi <= L.bedroom.z1 + 0.01;
                    assertTrue(inFront || inBack, "side window crosses the partition, seed " + s);
                }
            }
            long doors = L.openings.stream().filter(o -> o.door).count();
            assertEquals(1, doors);
        }
    }

    @Test
    void aWalkerFollowingRouteNeverPassesThroughWalls() {
        for (int s = 0; s < 80; s++) {
            HouseLayout L = make(s);
            List<double[]> walls = wallRects(L);
            List<double[]> spots = new ArrayList<>(L.spawnPoints);
            spots.add(new double[]{L.doorX, L.innerD / 2 - 30});
            for (double[] from : spots) {
                for (double[] to : spots) {
                    double x = from[0], z = from[1];
                    int steps = 0;
                    while (Math.hypot(to[0] - x, to[1] - z) > 8 && steps++ < 900) {
                        double[] t = L.route(x, z, to[0], to[1]);
                        double dx = t[0] - x, dz = t[1] - z, len = Math.hypot(dx, dz);
                        if (len > 1e-6) {
                            double step = Math.min(5, len);
                            x += dx / len * step;
                            z += dz / len * step;
                        }
                        for (double[] w : walls) {
                            assertFalse(x > w[0] + 0.2 && x < w[2] - 0.2 && z > w[1] + 0.2 && z < w[3] - 0.2, "walked through a wall, seed " + s);
                        }
                    }
                    assertTrue(Math.hypot(to[0] - x, to[1] - z) <= 8, "never arrived, seed " + s);
                }
            }
        }
    }

    @Test
    void sameSeedGivesTheSameHouse() {
        HouseLayout a = make(42), b = make(42);
        assertEquals(a.props.size(), b.props.size());
        for (int i = 0; i < a.props.size(); i++) {
            assertEquals(a.props.get(i).kind, b.props.get(i).kind);
            assertEquals(a.props.get(i).x, b.props.get(i).x);
            assertEquals(a.props.get(i).z, b.props.get(i).z);
        }
        assertEquals(a.doorX, b.doorX);
    }

    @Test
    void ruinedHousesAreMessier() {
        int clean = 0, ruined = 0;
        for (int s = 0; s < 100; s++) {
            HouseLayout c = HouseLayout.generate(360, 336, new Random(s), false), r = HouseLayout.generate(360, 336, new Random(s), true);
            clean += (int) c.props.stream().filter(p -> p.kind == HouseLayout.Kind.STAIN).count();
            ruined += (int) r.props.stream().filter(p -> p.kind == HouseLayout.Kind.STAIN).count();
        }
        assertEquals(0, clean);
        assertTrue(ruined > 50, "expected stains in ruined houses, got " + ruined);
    }

    @Test
    void mostHousesOfferSomewhereToLeaveSupplies() {
        int withLoot = 0;
        for (int s = 0; s < SEEDS; s++) {
            if (!make(s).lootSpots.isEmpty()) {
                withLoot++;
            }
        }
        assertTrue(withLoot >= SEEDS * 0.9, "only " + withLoot + " of " + SEEDS + " houses have a loot spot");
    }
}
