package com.blackout.game.entities;

import javafx.scene.Group;
import javafx.scene.shape.Sphere;
import javafx.scene.transform.Rotate;

public class Rig {

    public final Group root = new Group(), upper = new Group(), headGroup = new Group(), jawGroup = new Group();
    public final Rotate lean = new Rotate(0, 0, 12, 0, Rotate.X_AXIS);
    public final Rotate twist = new Rotate(0, 0, 12, 0, Rotate.Z_AXIS);
    public final Rotate lHipR = new Rotate(0, Rotate.X_AXIS), rHipR = new Rotate(0, Rotate.X_AXIS);
    public final Rotate lKneeR = new Rotate(0, Rotate.X_AXIS), rKneeR = new Rotate(0, Rotate.X_AXIS);
    public final Rotate lShR = new Rotate(0, Rotate.X_AXIS), rShR = new Rotate(0, Rotate.X_AXIS);
    public final Rotate lElbR = new Rotate(0, Rotate.X_AXIS), rElbR = new Rotate(0, Rotate.X_AXIS);
    public final Rotate lShYaw = new Rotate(0, Rotate.Y_AXIS), rShYaw = new Rotate(0, Rotate.Y_AXIS);
    public final Rotate headX = new Rotate(0, 0, -31, 0, Rotate.X_AXIS);
    public final Rotate headZ = new Rotate(0, 0, -31, 0, Rotate.Z_AXIS);
    public final Rotate jaw = new Rotate(0, 0, -40, 6, Rotate.X_AXIS);
    public Sphere sack;

    public Rig() {
        upper.getTransforms().addAll(twist, lean);
        headGroup.getTransforms().addAll(headZ, headX);
        jawGroup.getTransforms().add(jaw);
    }
}
