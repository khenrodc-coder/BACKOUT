package com.blackout.game.entities;

import javafx.scene.Group;
import javafx.scene.paint.Color;
import javafx.scene.paint.PhongMaterial;
import javafx.scene.transform.Rotate;
import static com.blackout.game.core.Constants.*;
import static com.blackout.game.core.Gfx.*;

public final class RigFactory {

    private RigFactory() {
    }

    public static Group limb(Rotate yaw, Rotate pitch, Rotate bend, double x, double y, double w, double d, double upperLen,
            double lowerLen, PhongMaterial mUp, PhongMaterial mLow, PhongMaterial mEnd, double endH, double endD, double endZ) {
        Group g = new Group();
        g.setTranslateX(x);
        g.setTranslateY(y);
        if (yaw != null) {
            g.getTransforms().add(yaw);
        }
        g.getTransforms().add(pitch);
        g.getChildren().add(box(w, upperLen, d, mUp, 0, upperLen / 2, 0));
        Group low = new Group();
        low.setTranslateY(upperLen);
        low.getTransforms().add(bend);
        low.getChildren().addAll(sph(w * 0.5, mUp, 0, 0, 0, 1, 1, 1), box(w * 0.88, lowerLen, d * 0.9, mLow, 0, lowerLen / 2, 0),
                box(w * 0.95, endH, endD, mEnd, 0, lowerLen - endH / 2 + 1, endZ));
        g.getChildren().add(low);
        return g;
    }

    public static Rig buildSoldier() {
        Rig r = new Rig();
        PhongMaterial cloth = mat(Color.rgb(52, 64, 56), Color.rgb(50, 60, 55), 8);
        PhongMaterial pants = mat(Color.rgb(44, 52, 48), Color.rgb(40, 48, 44), 8);
        PhongMaterial vest = mat(Color.rgb(30, 34, 37), Color.rgb(90, 98, 105), 28);
        PhongMaterial pad = mat(Color.rgb(22, 24, 26), Color.rgb(110, 115, 120), 40);
        PhongMaterial glove = mat(Color.rgb(16, 17, 19), Color.rgb(70, 70, 75), 30);
        PhongMaterial boot = mat(Color.rgb(24, 21, 18), Color.rgb(80, 75, 70), 40);
        PhongMaterial mask = mat(Color.rgb(26, 28, 30), Color.rgb(50, 50, 55), 10);
        PhongMaterial helmet = mat(Color.rgb(58, 70, 56), Color.rgb(150, 160, 145), 52);
        PhongMaterial lens = glow(Color.rgb(70, 210, 255));
        PhongMaterial accent = glow(Color.rgb(255, 150, 30));
        r.upper.getChildren().addAll(
                box(36, 40, 21, cloth, 0, -9, 0), box(38, 30, 24, vest, 0, -11, 0.5), box(26, 22, 3, pad, 0, -13, 13),
                box(7, 2, 0.6, accent, 0, -21, 14.7), box(8, 8, 5, pad, -11, 2, 12), box(8, 8, 5, pad, 11, 2, 12),
                box(38, 4, 23, pad, 0, 4, 0), box(28, 34, 13, pad, 0, -12, -17), cylX(6, 28, cloth, 0, -32, -18),
                box(10, 6, 10, mask, 0, -31, 0), sph(8.5, pad, -25, -27, 0, 1, 0.8, 1), sph(8.5, pad, 25, -27, 0, 1, 0.8, 1));
        r.headGroup.getChildren().addAll(
                box(21, 24, 21, mask, 0, -45, 0), box(19.5, 5, 2.5, lens, 0, -45, 10.8),
                sph(13, helmet, 0, -52, -0.5, 1.12, 0.85, 1.18), box(3, 6, 4, pad, -11.5, -46, 2),
                box(3, 6, 4, pad, 11.5, -46, 2), box(2, 2, 2, accent, 11.5, -50, 3));
        r.upper.getChildren().add(r.headGroup);
        r.upper.getChildren().add(limb(r.lShYaw, r.lShR, r.lElbR, -24, -27, 11, 12, 24, 22, cloth, cloth, glove, 9, 10, 0));
        r.upper.getChildren().add(limb(r.rShYaw, r.rShR, r.rElbR, 24, -27, 11, 12, 24, 22, cloth, cloth, glove, 9, 10, 0));
        Group lLeg = limb(null, r.lHipR, r.lKneeR, -9, 12, 15, 17, 22, 20, pants, pants, boot, 9, 22, 4);
        Group rLeg = limb(null, r.rHipR, r.rKneeR, 9, 12, 15, 17, 22, 20, pants, pants, boot, 9, 22, 4);
        r.root.getChildren().addAll(box(34, 10, 20, pants, 0, 11, 0), r.upper, lLeg, rLeg);
        return r;
    }

    public static Rig buildZombie(int type, boolean bl) {
        Rig r = new Rig();
        Color skinC = type == Z_RUNNER ? Color.rgb(138, 150, 112) : type == Z_TANK ? Color.rgb(108, 118, 96)
                : type == Z_SMOKER ? Color.rgb(112, 128, 116)
                        : Color.rgb(122 + RNG.nextInt(16), 140 + RNG.nextInt(12), 106 + RNG.nextInt(10));
        Color[] shirts = {Color.rgb(78, 84, 70), Color.rgb(96, 74, 64), Color.rgb(62, 70, 88), Color.rgb(84, 66, 66), Color.rgb(58, 62, 56)};
        PhongMaterial skin = bl ? mat(Color.rgb(20, 20, 23), Color.rgb(70, 70, 85), 30) : mat(skinC, Color.rgb(30, 36, 30), 6);
        PhongMaterial shirt = bl ? mat(Color.rgb(14, 14, 16), Color.rgb(40, 40, 50), 10) : mat(type == Z_TANK ? Color.rgb(50, 52, 50) : shirts[RNG.nextInt(shirts.length)], Color.rgb(25, 25, 25), 4);
        PhongMaterial pants = bl ? mat(Color.rgb(12, 12, 14), Color.rgb(30, 30, 36), 8) : mat(Color.rgb(46 + RNG.nextInt(14), 44, 42), Color.rgb(20, 20, 20), 4);
        PhongMaterial blood = mat(Color.rgb(96, 10, 10), Color.rgb(200, 50, 50), 48);
        PhongMaterial flesh = mat(Color.rgb(130, 45, 52), Color.rgb(200, 110, 110), 30);
        PhongMaterial dark = mat(Color.rgb(16, 14, 13), Color.rgb(20, 20, 20), 4);
        PhongMaterial bone = mat(Color.rgb(205, 196, 165), Color.rgb(120, 120, 100), 20);
        PhongMaterial eye = bl ? glow(Color.rgb(255, 10, 10)) : glow(type == Z_RUNNER ? Color.rgb(255, 40, 30) : type == Z_TANK ? Color.rgb(255, 140, 30)
                : type == Z_SMOKER ? Color.rgb(190, 90, 255) : Color.rgb(210, 255, 70));
        Group up = r.upper;
        up.getChildren().addAll(
                box(34, 40, 20, shirt, 0, -9, 0), box(11, 7, 21, shirt, -9, 11, 0), box(9, 5, 21, shirt, 10, 10, 0),
                box(15, 19, 1, blood, -6, -12, 10.4), box(9, 11, 1.2, flesh, -6, -11, 10.6),
                box(11, 1.4, 1.6, bone, -6, -15, 11.1), box(11, 1.4, 1.6, bone, -6, -11.4, 11.1), box(11, 1.4, 1.6, bone, -6, -7.8, 11.1),
                box(8, 6, 1, blood, 10, -22, 10.4), box(12, 16, 1, blood, 5, -10, -10.4), box(9, 7, 9, skin, 0, -31, 0),
                sph(7, shirt, -22, -27, 0, 1, 1, 1), sph(7, shirt, 22, -27, 0, 1, 1, 1));
        if (type == Z_TANK) {
            up.getChildren().addAll(sph(10, flesh, 8, -20, -14, 1, 1, 1), sph(6, blood, -12, -30, -8, 1, 1, 1), sph(5, flesh, 14, -32, 4, 1, 1, 1));
        }
        if (type == Z_SMOKER) {
            r.sack = sph(13, mat(Color.rgb(90, 140, 70), Color.rgb(180, 220, 150), 60), 0, -4, 12, 1, 1, 0.8);
            up.getChildren().addAll(r.sack, sph(4, glow(Color.rgb(120, 255, 90)), 5, -8, 20, 1, 1, 1));
        }
        r.headGroup.getChildren().addAll(
                box(21, 16, 21, skin, 0, -49, 0), box(19, 8, 19, skin, 0, -41, 0),
                box(5.5, 4.5, 1, dark, -5, -48, 10.4), box(5.5, 4.5, 1, dark, 5, -48, 10.4),
                box(3.6, 2.8, 0.8, eye, -5, -48, 10.95), box(3.6, 2.8, 0.8, eye, 5, -48, 10.95), box(3, 5, 3, flesh, 0, -44, 11),
                box(14, 4, 14, dark, -2, -58.5, -2), box(8, 3, 8, dark, 6, -57, 4), box(6, 1, 6, blood, 4, -57.6, 3));
        for (int i = 0; i < 4; i++) {
            r.headGroup.getChildren().add(box(3, 3, 1, bone, -4.5 + 3 * i, -38.5, 9.6));
        }
        r.jawGroup.getChildren().addAll(box(17, 6, 17, skin, 0, -35.5, 1.5), box(8, 1.5, 8, flesh, 0, -38.5, 2.5),
                box(2.5, 2.5, 1, bone, -4, -38, 9.4), box(2.5, 2.5, 1, bone, 0, -38, 9.4), box(2.5, 2.5, 1, bone, 4, -38, 9.4));
        if (type == Z_SMOKER) {
            r.jawGroup.getChildren().add(sph(3.5, glow(Color.rgb(120, 255, 90)), 0, -38.5, 6, 1, 1, 1));
        }
        if (type == Z_RUNNER) {
            r.jawGroup.getChildren().add(box(15, 2, 1, blood, 0, -34, 10));
        }
        if (bl) {
            r.jawGroup.getChildren().add(box(14, 1.5, 12, glow(Color.rgb(255, 20, 20)), 0, -38.6, 3));
            r.headGroup.getChildren().addAll(box(2, 7, 2, dark, -7, -60, -2), box(2, 9, 2, dark, 0, -61, -3), box(2, 7, 2, dark, 7, -60, -2),
                    box(7, 1.2, 1, glow(Color.rgb(255, 10, 10)), -5, -51.5, 10.9), box(7, 1.2, 1, glow(Color.rgb(255, 10, 10)), 5, -51.5, 10.9));
            up.getChildren().addAll(box(2.5, 10, 2.5, dark, -14, -34, -6), box(2.5, 12, 2.5, dark, 0, -36, -9), box(2.5, 10, 2.5, dark, 14, -34, -6));
        }
        r.headGroup.getChildren().add(r.jawGroup);
        up.getChildren().add(r.headGroup);
        Group lArm = limb(r.lShYaw, r.lShR, r.lElbR, -23, -27, 10.5, 11, 24, 22, shirt, skin, skin, 8, 9, 0);
        Group rArm = limb(r.rShYaw, r.rShR, r.rElbR, 23, -27, 10.5, 11, 24, 22, shirt, skin, skin, 8, 9, 0);
        if (type == Z_TANK) {
            ((Group) lArm.getChildren().get(1)).getChildren().add(sph(9, skin, 0, 26, 0, 1, 1, 1));
            ((Group) rArm.getChildren().get(1)).getChildren().add(sph(9, skin, 0, 26, 0, 1, 1, 1));
        }
        up.getChildren().addAll(lArm, rArm);
        Group lLeg = limb(null, r.lHipR, r.lKneeR, -9, 12, 15, 16, 22, 20, pants, pants, dark, 9, 21, 4);
        Group rLeg = limb(null, r.rHipR, r.rKneeR, 9, 12, 15, 16, 22, 20, pants, blood, dark, 9, 21, 4);
        r.root.getChildren().addAll(box(32, 10, 19, pants, 0, 11, 0), r.upper, lLeg, rLeg);
        return r;
    }
}
