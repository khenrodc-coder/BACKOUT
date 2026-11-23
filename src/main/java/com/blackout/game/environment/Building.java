package com.blackout.game.environment;

import java.util.ArrayList;
import java.util.List;
import javafx.scene.Group;
import javafx.scene.transform.Rotate;

public class Building {

    public final double x, z, w, d, h;
    public final int front;
    public boolean burning, doorOpen;
    /** Stable world-state id for streamed buildings (null for the always-loaded ones). */
    public String id;
    public double doorAmt, doorX, doorZ, outX, outZ, inX, inZ;
    public final Group group = new Group(), roof = new Group();
    public Rotate doorRot;
    public Obstacle doorObs;
    public final List<double[]> spawn = new ArrayList<>();
    /** Floor plan for houses built from {@link HouseLayout}; null for simple buildings. */
    public HouseLayout layout;

    public Building(double x, double z, double w, double d, double h, int front) {
        this.x = x;
        this.z = z;
        this.w = w;
        this.d = d;
        this.h = h;
        this.front = front;
    }

    public boolean contains(double px, double pz) {
        return Math.abs(px - x) < w / 2 && Math.abs(pz - z) < d / 2;
    }

    /**
     * Next point to head for when walking from one place inside this building to another (world coordinates), so that
     * walkers use the doorways between rooms. Returns the target itself when there is nothing in the way.
     */
    public double[] route(double fx, double fz, double tx, double tz) {
        if (layout == null) {
            return new double[]{tx, tz};
        }
        double[] r = layout.route(fx - x, (fz - z) * front, tx - x, (tz - z) * front);
        return new double[]{r[0] + x, r[1] * front + z};
    }
}
