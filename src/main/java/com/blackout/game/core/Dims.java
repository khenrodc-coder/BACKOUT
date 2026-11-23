package com.blackout.game.core;

/**
 * World scale. The soldier rig is 118 units tall (measured), i.e. about 65 units per metre for a 1.8 m person. Everything built
 * as a house follows these numbers so doors, ceilings, beds and counters stay in proportion to the player.
 */
public final class Dims {

    private Dims() {
    }

    public static final double PLAYER_HEIGHT = 118;
    public static final double UNITS_PER_METRE = 65;

    /** Exterior wall thickness, interior partition thickness. */
    public static final double HOUSE_WALL_T = 8, PARTITION_T = 6;
    /** Ceiling height (about 2.6 m). */
    public static final double HOUSE_WALL_H = 168;
    /** Door opening: clears the player's head by 14 units and is wide enough for the player plus a rifle. */
    public static final double HOUSE_DOOR_W = 64, HOUSE_DOOR_H = 132;
    /** Windows. */
    public static final double WINDOW_W = 44, WINDOW_SILL = 62, WINDOW_HEAD = 122;
    /** Interior doorways (no door leaf) between rooms. */
    public static final double DOORWAY_W = 60, DOORWAY_H = 128;
    /** Clearance kept free around walkways when furniture is placed (player collision radius is 16, plus a safety margin). */
    public static final double PATH_RADIUS = 20;
}
