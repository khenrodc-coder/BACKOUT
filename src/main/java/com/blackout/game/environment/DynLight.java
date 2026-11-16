package com.blackout.game.environment;

import javafx.scene.PointLight;
import javafx.scene.paint.Color;
import static com.blackout.game.core.Constants.*;

public class DynLight {

    public final PointLight light;
    public final double x, z, phase = RNG.nextDouble() * 6.28;
    public final int kind;
    public final Color base;
    public boolean on;

    public DynLight(PointLight l, double x, double z, int kind, Color base) {
        this.light = l;
        this.x = x;
        this.z = z;
        this.kind = kind;
        this.base = base;
    }
}
