package com.blackout.game.environment;

import java.util.ArrayList;
import java.util.List;

import javafx.scene.Group;
import javafx.scene.transform.Rotate;

public class Building {

    public final double x, z, w, d, h;
    public final int front;
    public boolean burning, doorOpen;
    public double doorAmt, doorX, doorZ, outX, outZ, inX, inZ;
    public final Group group = new Group(), roof = new Group();
    public Rotate doorRot;
    public Obstacle doorObs;
    public final List<double[]> spawn = new ArrayList<>();

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
}
