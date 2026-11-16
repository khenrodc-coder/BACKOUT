package com.blackout.game.entities;



public enum WeaponType {
    PISTOL("PISTOL", 12, 48, 52, 1200, 250_000_000L, 1, 0.4),
    ASSAULT_RIFLE("ASSAULT RIFLE", 30, 120, 30, 1200, 95_000_000L, 1, 1.0),
    SHOTGUN("SHOTGUN", 6, 36, 20, 550, 750_000_000L, 8, 6.5);

    public final String displayName;
    public final int magazineSize, startingReserve, pellets;
    public final double damage, range, spread;
    public final long cooldown;

    WeaponType(String n, int mag, int res, double dmg, double range, long cd, int pellets, double spread) {
        this.displayName = n;
        this.magazineSize = mag;
        this.startingReserve = res;
        this.damage = dmg;
        this.range = range;
        this.cooldown = cd;
        this.pellets = pellets;
        this.spread = spread;
    }
}
