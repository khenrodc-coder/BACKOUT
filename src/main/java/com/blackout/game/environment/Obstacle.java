package com.blackout.game.environment;



public class Obstacle {

    public final double x, z, halfWidth, halfDepth, top;
    public final boolean zombiePass;
    public boolean active = true;

    public Obstacle(double x, double z, double hw, double hd, double top, boolean zp) {
        this.x = x;
        this.z = z;
        this.halfWidth = hw;
        this.halfDepth = hd;
        this.top = top;
        this.zombiePass = zp;
    }
}
