package com.blackout.game.environment;

import javafx.scene.Group;
import javafx.scene.paint.Color;
import javafx.scene.paint.PhongMaterial;
import javafx.scene.shape.Box;
import javafx.scene.transform.Scale;
import static com.blackout.game.core.Constants.*;
import static com.blackout.game.core.Gfx.*;

public class Fire {

    public final Group flames = new Group();
    public final Box[] tongues = new Box[3];
    public final double[] heights = {16, 11, 8};
    public final double x, y, z, scale;
    public final boolean smoke;
    public DynLight dl;
    public double phase = RNG.nextDouble() * 6, emberT;

    public Fire(double x, double y, double z, double scale, boolean smoke) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.scale = scale;
        this.smoke = smoke;
        flames.setTranslateX(x);
        flames.setTranslateY(y);
        flames.setTranslateZ(z);
        flames.getTransforms().add(new Scale(scale, scale, scale));
        PhongMaterial[] fm = {glow(Color.rgb(255, 90, 20)), glow(Color.rgb(255, 170, 40)), glow(Color.rgb(255, 235, 140))};
        double[] widths = {13, 9, 5};
        for (int i = 0; i < 3; i++) {
            tongues[i] = new Box(widths[i], heights[i], widths[i]);
            tongues[i].setMaterial(fm[i]);
            tongues[i].setTranslateY(-heights[i] / 2);
            flames.getChildren().add(tongues[i]);
        }
    }
}
