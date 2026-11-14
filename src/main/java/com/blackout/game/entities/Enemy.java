package com.blackout.game.entities;

import javafx.scene.Group;
import javafx.scene.shape.Cylinder;
import javafx.scene.transform.Rotate;
import javafx.scene.transform.Scale;
import com.blackout.game.environment.Car;
import static com.blackout.game.core.Constants.*;
import static com.blackout.game.core.Gfx.*;
import static com.blackout.game.entities.RigFactory.*;

public class Enemy {

    public final Group model = new Group(), inner;
    public final Rotate facing = new Rotate(0, Rotate.Y_AXIS);
    public final Rotate fall = new Rotate(0, Rotate.X_AXIS);
    public final Scale scaleT;
    public final Cylinder shadow;
    public final Rig origRig;
    public Rig rig, bloodRig;
    public final int origType;
    public int type;
    public double sx, sy, speedMul, reach, radius, detect, windupDur, staggerMul, maxHealth;
    public final double limp, phaseOff, jit, wSpd;
    public boolean alive = true, stepFlag, blood;
    public double health, attackCd = 0.5, windup = -1, animationTime, walkPhase, spawnTimer, stagger, deathTime, yaw, alert, lostTimer,
            idleTimer, wanderT, wx, wz, slamT, castCd = 2, stuck, sideT, sideDir = 1, hiddenTimer, deathDir = 1, puffT;
    public int state = S_IDLE, exitStage, enterStage;
    public Car car;

    public Enemy(double x, double z, int type) {
        this.origType = type;
        jit = 0.97 + RNG.nextDouble() * 0.1;
        wSpd = 0.85 + RNG.nextDouble() * 0.3;
        limp = 0.4 + RNG.nextDouble() * 0.5;
        phaseOff = RNG.nextDouble() * 6.28;
        applyStats(type, false);
        health = maxHealth;
        origRig = buildZombie(type, false);
        rig = origRig;
        inner = new Group(rig.root);
        inner.setTranslateY(-55);
<<<<<<< HEAD
        shadow = cylY(1, 0.4, SHADOW_MAT, 0, -0.3, 0, 32);
=======
        shadow = cylY(1, 0.4, SHADOW_MAT, 0, -0.3, 0);
>>>>>>> 7ab94253a3fe49932fb5341cdbe322138aacef9c
        shadow.setScaleX(22);
        shadow.setScaleZ(22);
        scaleT = new Scale(sx, sy, sx);
        model.getChildren().addAll(shadow, inner);
        model.getTransforms().addAll(facing, fall, scaleT);
        model.setTranslateX(x);
        model.setTranslateY(GROUND_Y + 62);
        model.setTranslateZ(z);
        yaw = RNG.nextDouble() * 360;
        facing.setAngle(yaw);
    }

    public void applyStats(int t, boolean bl) {
        type = t;
        if (bl) {
            sy = 1.0;
            sx = 0.92;
            maxHealth = 90;
            speedMul = 2.8;
            reach = 52;
            radius = 17;
            detect = 900;
            windupDur = 0.28;
            staggerMul = 0.5;
            return;
        }
        sy = t == Z_TANK ? TANK_Y : t == Z_RUNNER ? 0.98 : t == Z_SMOKER ? 1.05 : jit;
        sx = t == Z_TANK ? TANK_XZ : t == Z_RUNNER ? 0.9 : t == Z_SMOKER ? 1.0 : jit;
        maxHealth = t == Z_TANK ? 900 : t == Z_RUNNER ? 60 : t == Z_SMOKER ? 140 : 100;
        speedMul = t == Z_RUNNER ? 2.1 : t == Z_TANK ? 0.42 : t == Z_SMOKER ? 0.8 : wSpd;
        reach = t == Z_TANK ? 108 : t == Z_RUNNER ? 50 : 52;
        radius = Math.min(18 * sx, 30);
        detect = t == Z_RUNNER ? 460 : t == Z_SMOKER ? 440 : t == Z_TANK ? 360 : 380;
        windupDur = t == Z_RUNNER ? 0.32 : t == Z_TANK ? 0.95 : t == Z_SMOKER ? 1.1 : 0.45;
        staggerMul = t == Z_TANK ? 0.15 : 1;
    }

    /**
     * Blood Moon transformation: any zombie becomes a black, red-eyed, fast
     * Runner; reverts when the event ends.
     */
    public void setBlood(boolean on) {
        if (!alive || on == blood) {
            return;
        }
        double frac = Math.max(0.05, health / maxHealth);
        blood = on;
        if (on) {
            applyStats(Z_RUNNER, true);
            if (bloodRig == null) {
                bloodRig = buildZombie(Z_RUNNER, true);
            }
            rig = bloodRig;
            state = S_CHASE;
            lostTimer = 9999;
        } else {
            applyStats(origType, false);
            rig = origRig;
            state = S_IDLE;
            idleTimer = 1 + RNG.nextDouble() * 3;
            lostTimer = 0;
            alert = 0;
        }
        health = Math.max(1, frac * maxHealth);
        inner.getChildren().setAll(rig.root);
        scaleT.setX(sx);
        scaleT.setY(sy);
        scaleT.setZ(sx);
        windup = -1;
        stagger = 0;
        slamT = 0;
        castCd = 2;
    }

    public void animate(double dt, boolean moving, boolean chasing) {
        animationTime += dt;
        double sp = (type == Z_RUNNER ? 12 : type == Z_TANK ? 4.2 : type == Z_SMOKER ? 6 : 7.5) * (chasing ? 1 : 0.55);
        if (moving) {
            walkPhase += dt * sp;
        }
        double p = walkPhase + phaseOff, t = animationTime + phaseOff;
        double amp = type == Z_RUNNER ? 40 : type == Z_TANK ? 20 : 24, blend = moving ? 1 : 0.2;
        rig.lHipR.setAngle(Math.sin(p) * amp * blend);
        rig.rHipR.setAngle(-Math.sin(p) * amp * limp * blend);
        rig.lKneeR.setAngle(-Math.max(0, Math.cos(p)) * 35 * blend);
        rig.rKneeR.setAngle(-Math.max(0, -Math.cos(p)) * 20 * limp * blend);
        double wp = windup > 0 && windupDur > 0 ? 1 - windup / windupDur : 0;
        double ls, rs, le, re, lean = type == Z_RUNNER ? -26 : type == Z_TANK ? -8 : -14;
        if (!chasing) {
            ls = 18 + Math.sin(t * 1.3) * 4;
            rs = 22 + Math.sin(t * 1.1) * 4;
            le = 12;
            re = 14;
            lean = -8;
        } else if (type == Z_RUNNER && moving && wp == 0) {
            ls = 55 - Math.sin(p) * 40;
            rs = 55 + Math.sin(p) * 40;
            le = 50;
            re = 50;
        } else if (type == Z_TANK) {
            ls = rs = slamT > 0 ? 20 + 150 * (slamT / 0.25) : 70 + 100 * wp;
            le = re = 10;
            lean -= 12 * wp;
        } else if (type == Z_SMOKER) {
            ls = 60 + Math.sin(t * 1.7) * 5 - 20 * wp;
            rs = 64 + Math.sin(t * 1.4) * 5 - 20 * wp;
            le = 14;
            re = 18;
        } else {
            ls = 78 + Math.sin(t * 1.7) * 7 + 40 * wp;
            rs = 84 + Math.sin(t * 1.4) * 8 + 40 * wp;
            le = 14 + Math.sin(t * 2.1) * 6;
            re = 22 + Math.sin(t * 1.9) * 6;
            lean -= (type == Z_RUNNER ? 16 : 8) * wp;
        }
        rig.lShR.setAngle(ls);
        rig.rShR.setAngle(rs);
        rig.lElbR.setAngle(le);
        rig.rElbR.setAngle(re);
        rig.lShYaw.setAngle(3);
        rig.rShYaw.setAngle(-3);
        rig.lean.setAngle(lean + (stagger > 0 ? stagger / 0.22 * 28 : 0));
        rig.twist.setAngle(Math.sin(p) * 5);
        rig.headX.setAngle(-8 + Math.sin(t * 1.3) * 5 + (type == Z_SMOKER ? 45 * wp : 0));
        rig.headZ.setAngle(Math.sin(t * 0.9) * 9);
        double open = windup > 0 ? 10 + 30 * wp : 10 + Math.abs(Math.sin(t * 2.2)) * 10;
        rig.jaw.setAngle(-open);
        if (rig.sack != null) {
            double s = 1 + 0.45 * wp;
            rig.sack.setScaleX(s);
            rig.sack.setScaleY(s);
            rig.sack.setScaleZ(0.8 * s);
        }
    }
}
