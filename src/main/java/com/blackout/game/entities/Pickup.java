package com.blackout.game.entities;

import javafx.scene.Group;

public class Pickup {

    public Group node;
    public int kind;
    /** Stable world-state id for streamed supplies (null for the always-loaded ones). */
    public String id;
    public double x, z, phase;
}
