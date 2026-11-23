package com.blackout.game.world;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;

/**
 * Persistent changes to streamed parts of the world, kept as plain data so they survive a chunk being unloaded (and are written
 * to the save file). Objects get stable ids such as {@code d:3:5:0} (door 0 in chunk 3,5) or {@code p:3:5:2} (pickup 2 there).
 */
public final class WorldState {

    private static final Pattern ID = Pattern.compile("[a-z]:-?\\d{1,4}:-?\\d{1,4}:\\d{1,3}");
    private static final int MAX = 4000;

    private final Set<String> openDoors = new TreeSet<>(), taken = new TreeSet<>();

    public static boolean validId(String id) {
        return id != null && ID.matcher(id).matches();
    }

    public static String id(char kind, int cx, int cz, int index) {
        return kind + ":" + cx + ":" + cz + ":" + index;
    }

    public void setDoorOpen(String id, boolean open) {
        if (!validId(id)) {
            return;
        }
        if (open) {
            if (openDoors.size() < MAX) {
                openDoors.add(id);
            }
        } else {
            openDoors.remove(id);
        }
    }

    public boolean isDoorOpen(String id) {
        return openDoors.contains(id);
    }

    public void markTaken(String id) {
        if (validId(id) && taken.size() < MAX) {
            taken.add(id);
        }
    }

    public boolean isTaken(String id) {
        return taken.contains(id);
    }

    public List<String> openDoorIds() {
        return new ArrayList<>(openDoors);
    }

    public List<String> takenIds() {
        return new ArrayList<>(taken);
    }

    public void clear() {
        openDoors.clear();
        taken.clear();
    }

    /** Replaces the contents; ids that do not look like ids are ignored (a damaged save cannot inject garbage). */
    public void load(Collection<String> doors, Collection<String> takenIds) {
        clear();
        for (String d : doors) {
            setDoorOpen(d, true);
        }
        for (String t : takenIds) {
            markTaken(t);
        }
    }

    /** Fewer entries per chunk is better; used by tests. */
    public int size() {
        return openDoors.size() + taken.size();
    }
}
