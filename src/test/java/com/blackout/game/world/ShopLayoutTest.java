package com.blackout.game.world;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayDeque;
import java.util.Random;

import org.junit.jupiter.api.Test;

class ShopLayoutTest {

    private static final double R = 20, CELL = 6;

    private static boolean[][] reach(ShopLayout L, double doorX) {
        int nx = (int) Math.ceil(L.innerW / CELL), nz = (int) Math.ceil(L.innerD / CELL);
        boolean[][] free = new boolean[nx][nz], seen = new boolean[nx][nz];
        for (int i = 0; i < nx; i++) {
            for (int j = 0; j < nz; j++) {
                double x = -L.innerW / 2 + (i + .5) * CELL, z = -L.innerD / 2 + (j + .5) * CELL;
                free[i][j] = Math.abs(x) <= L.innerW / 2 - R && Math.abs(z) <= L.innerD / 2 - R && L.clearance(x, z) >= R;
            }
        }
        int sx = (int) ((doorX + L.innerW / 2) / CELL), sz = (int) ((L.innerD / 2 - R - 8 + L.innerD / 2) / CELL);
        ArrayDeque<int[]> q = new ArrayDeque<>();
        if (free[sx][sz]) {
            q.add(new int[]{sx, sz});
            seen[sx][sz] = true;
        }
        int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        while (!q.isEmpty()) {
            int[] c = q.poll();
            for (int[] d : dirs) {
                int x = c[0] + d[0], z = c[1] + d[1];
                if (x >= 0 && z >= 0 && x < nx && z < nz && free[x][z] && !seen[x][z]) {
                    seen[x][z] = true;
                    q.add(new int[]{x, z});
                }
            }
        }
        return seen;
    }

    private static boolean reachable(ShopLayout L, boolean[][] seen, double x, double z) {
        int i = (int) ((x + L.innerW / 2) / CELL), j = (int) ((z + L.innerD / 2) / CELL);
        return i >= 0 && j >= 0 && i < seen.length && j < seen[0].length && seen[i][j];
    }

    @Test
    void everySpawnAndLootPointIsReachableFromTheDoor() {
        double[][] sizes = {{149, 200, 0}, {125, 200, 0}, {260, 200, 1}, {140, 110, 0}};
        for (double[] sz : sizes) {
            boolean sup = sz[2] == 1;
            for (int s = 0; s < 150; s++) {
                Random rn = new Random(s);
                ShopLayout L = ShopLayout.generate(sz[0], sz[1], rn, sup);
                boolean[][] seen = reach(L, 0);
                assertFalse(L.spawnPoints.isEmpty(), "no spawn points, seed " + s);
                for (double[] p : L.spawnPoints) {
                    assertTrue(reachable(L, seen, p[0], p[1]), (sup ? "supermarket" : "shop") + " " + sz[0] + "x" + sz[1] + " spawn unreachable, seed " + s);
                }
                for (double[] p : L.lootSpots) {
                    assertTrue(reachable(L, seen, p[0], p[1]), "loot unreachable, seed " + s);
                }
            }
        }
    }

    @Test
    void theEntranceStaysFree() {
        for (int s = 0; s < 100; s++) {
            Random rn = new Random(s);
            double[] sz = new double[][]{{149, 200, 0}, {260, 200, 1}, {140, 110, 0}, {125, 200, 0}}[s % 4];
            ShopLayout L = ShopLayout.generate(sz[0], sz[1], rn, sz[2] == 1);
            double zf = L.innerD / 2;
            for (ShopLayout.Fixture f : L.fixtures) {
                double[] a = f.aabb();
                boolean inLane = a[0] < 32 && a[2] > -32 && a[3] > zf - L.entrance;
                assertFalse(inLane, f.kind + " blocks the entrance, seed " + s);
            }
        }
    }

    @Test
    void fixturesStayInsideTheWalls() {
        for (int s = 0; s < 100; s++) {
            Random rn = new Random(s);
            double[] sz = new double[][]{{149, 200, 0}, {260, 200, 1}, {140, 110, 0}, {125, 200, 0}}[s % 4];
            ShopLayout L = ShopLayout.generate(sz[0], sz[1], rn, sz[2] == 1);
            for (ShopLayout.Fixture f : L.fixtures) {
                double[] a = f.aabb();
                assertTrue(a[0] >= -L.innerW / 2 - 0.01 && a[2] <= L.innerW / 2 + 0.01 && a[1] >= -L.innerD / 2 - 0.01 && a[3] <= L.innerD / 2 + 0.01,
                        f.kind + " pokes through a wall, seed " + s);
            }
        }
    }

    @Test
    void shopsAreActuallyStocked() {
        ShopLayout shop = ShopLayout.generate(149, 200, new Random(1), false), market = ShopLayout.generate(260, 200, new Random(1), true);
        assertTrue(shop.fixtures.stream().filter(f -> f.kind == ShopLayout.Kind.SHELF).count() >= 2);
        assertTrue(market.fixtures.stream().filter(f -> f.kind == ShopLayout.Kind.SHELF).count() >= 4);
        assertTrue(market.fixtures.stream().anyMatch(f -> f.kind == ShopLayout.Kind.FRIDGE));
    }
}
