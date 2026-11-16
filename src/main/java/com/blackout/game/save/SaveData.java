package com.blackout.game.save;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Everything needed to restore a run. Plain data: no JavaFX types. */
public final class SaveData {

    /** World seed: rebuilds the identical map (buildings, cars, trees, props). */
    public long seed;
    public long savedAtMillis;

    // clock / calendar (the Blood Moon follows from this, so it needs no extra field)
    public double gameMinutes;

    // player
    public double x, z, feetY, yaw, pitch;
    public double health = 100, stamina = 100;
    public int medkits, grenades, kills;
    public double survivalTime, threat;
    public boolean torchOn, firstPerson;

    // equipment (weapon enum names -> ammo)
    public String weapon = "PISTOL";
    public final Map<String, Integer> magazine = new LinkedHashMap<>();
    public final Map<String, Integer> reserve = new LinkedHashMap<>();

    // world changes
    /** One char per building in generation order: '1' = door open. */
    public String doors = "";
    /** One char per map cell: '1' = explored. */
    public String explored = "";
    /** One char per point of interest: '1' = discovered. */
    public String poiFound = "";
    public boolean hasWaypoint;
    public double waypointX, waypointZ;

    /** {type, x, z, health, yaw} */
    public final List<double[]> enemies = new ArrayList<>();
    /** {kind, x, z} */
    public final List<double[]> pickups = new ArrayList<>();
}
