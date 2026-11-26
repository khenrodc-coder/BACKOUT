package com.blackout.game.core;

import java.util.Random;

public final class Constants {

    private Constants() {
    }

    /** Re-seeds the shared RNG. World generation consumes it first, so the same seed rebuilds the same map. */
    public static void reseed(long seed) {
        RNG.setSeed(seed);
    }

    public static final double GROUND_Y = 110;
    public static final double TANK_Y = 2.5;               // tank height scale
    public static final double TANK_XZ = 3.0;              // tank width scale
    public static final double CAMERA_DISTANCE = 600;
    public static final double WORLD_BOUNDARY = 1480;
    public static final double WORLD_MAX_Z = 3320;
    public static final double GUN_X = 8, GUN_Y = -11, GUN_Z = 40;
    public static final Random RNG = new Random();

    public static final int Z_WALKER = 0, Z_RUNNER = 1, Z_TANK = 2, Z_SMOKER = 3;
    public static final int S_IDLE = 0, S_WANDER = 1, S_CHASE = 2;
}
