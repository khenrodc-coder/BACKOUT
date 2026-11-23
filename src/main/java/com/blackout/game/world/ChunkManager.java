package com.blackout.game.world;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Keeps the chunks around the player loaded and the rest unloaded. Pure logic: what a chunk actually contains is up to the
 * {@link Loader}. Chunks load nearest-first with a per-call budget (so entering a new area never builds everything in one frame)
 * and unload only after the player is clearly away from them (hysteresis, so walking back and forth over a border does not
 * thrash). All work happens on the caller's thread, which must be the JavaFX thread because loaders create scene nodes.
 */
public final class ChunkManager<T> {

    public interface Loader<T> {

        T load(int cx, int cz);

        void unload(int cx, int cz, T chunk);
    }

    public interface Exists {

        boolean test(int cx, int cz);
    }

    private static final class Entry<T> {

        final int cx, cz;
        final T chunk;

        Entry(int cx, int cz, T chunk) {
            this.cx = cx;
            this.cz = cz;
            this.chunk = chunk;
        }
    }

    private final double size, loadDist, unloadDist;
    private final int maxLoadsPerUpdate;
    private final Exists exists;
    private final Loader<T> loader;
    private final Map<Long, Entry<T>> loaded = new LinkedHashMap<>();

    private long totalLoads, totalUnloads, totalLoadNanos, maxLoadNanos;
    private int peakLoaded;

    /**
     * @param size chunk edge length (chunk cx covers x in [cx*size, (cx+1)*size))
     * @param loadDist a chunk is loaded when the player is within this distance of its rectangle
     * @param unloadDist a loaded chunk is dropped when the player is farther than this (at least loadDist + 25% of a chunk)
     * @param maxLoadsPerUpdate how many chunks may be built per {@link #update} call
     * @param exists which chunk coordinates hold anything at all
     */
    public ChunkManager(double size, double loadDist, double unloadDist, int maxLoadsPerUpdate, Exists exists, Loader<T> loader) {
        if (size <= 0 || maxLoadsPerUpdate < 1) {
            throw new IllegalArgumentException("size and budget must be positive");
        }
        this.size = size;
        this.loadDist = loadDist;
        this.unloadDist = Math.max(unloadDist, loadDist + size * 0.25);
        this.maxLoadsPerUpdate = maxLoadsPerUpdate;
        this.exists = exists;
        this.loader = loader;
    }

    private static long key(int cx, int cz) {
        return ((long) cx << 32) ^ (cz & 0xffffffffL);
    }

    private double rectDist(int cx, int cz, double px, double pz) {
        double x0 = cx * size, z0 = cz * size;
        double dx = Math.max(Math.max(x0 - px, px - (x0 + size)), 0), dz = Math.max(Math.max(z0 - pz, pz - (z0 + size)), 0);
        return Math.hypot(dx, dz);
    }

    /** Unloads far chunks, then loads the nearest missing ones (at most the budget). Returns how many were loaded. */
    public int update(double px, double pz) {
        List<Entry<T>> drop = new ArrayList<>();
        for (Entry<T> e : loaded.values()) {
            if (rectDist(e.cx, e.cz, px, pz) > unloadDist) {
                drop.add(e);
            }
        }
        for (Entry<T> e : drop) {
            loaded.remove(key(e.cx, e.cz));
            loader.unload(e.cx, e.cz, e.chunk);
            totalUnloads++;
        }
        int c0 = (int) Math.floor((px - loadDist) / size), c1 = (int) Math.floor((px + loadDist) / size);
        int r0 = (int) Math.floor((pz - loadDist) / size), r1 = (int) Math.floor((pz + loadDist) / size);
        List<int[]> want = new ArrayList<>();
        for (int cx = c0; cx <= c1; cx++) {
            for (int cz = r0; cz <= r1; cz++) {
                if (!loaded.containsKey(key(cx, cz)) && exists.test(cx, cz) && rectDist(cx, cz, px, pz) <= loadDist) {
                    want.add(new int[]{cx, cz});
                }
            }
        }
        want.sort((a, b) -> Double.compare(rectDist(a[0], a[1], px, pz), rectDist(b[0], b[1], px, pz)));
        int done = 0;
        for (int[] w : want) {
            if (done >= maxLoadsPerUpdate) {
                break;
            }
            long t0 = System.nanoTime();
            T chunk = loader.load(w[0], w[1]);
            long dt = System.nanoTime() - t0;
            totalLoadNanos += dt;
            maxLoadNanos = Math.max(maxLoadNanos, dt);
            loaded.put(key(w[0], w[1]), new Entry<>(w[0], w[1], chunk));
            totalLoads++;
            done++;
        }
        peakLoaded = Math.max(peakLoaded, loaded.size());
        return done;
    }

    /** Unloads everything (new game, loading a save, leaving to the menu). */
    public void unloadAll() {
        for (Entry<T> e : new ArrayList<>(loaded.values())) {
            loader.unload(e.cx, e.cz, e.chunk);
            totalUnloads++;
        }
        loaded.clear();
    }

    public boolean isLoaded(int cx, int cz) {
        return loaded.containsKey(key(cx, cz));
    }

    public int loadedCount() {
        return loaded.size();
    }

    public List<T> loadedChunks() {
        List<T> out = new ArrayList<>();
        for (Entry<T> e : loaded.values()) {
            out.add(e.chunk);
        }
        return out;
    }

    public long totalLoads() {
        return totalLoads;
    }

    public long totalUnloads() {
        return totalUnloads;
    }

    public int peakLoaded() {
        return peakLoaded;
    }

    /** Average / worst time to build one chunk, in milliseconds (profiling). */
    public double averageLoadMillis() {
        return totalLoads == 0 ? 0 : totalLoadNanos / 1e6 / totalLoads;
    }

    public double maxLoadMillis() {
        return maxLoadNanos / 1e6;
    }
}
