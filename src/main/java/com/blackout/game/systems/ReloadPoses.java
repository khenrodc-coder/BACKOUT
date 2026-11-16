package com.blackout.game.systems;

import com.blackout.game.entities.WeaponType;

/**
 * Weapon-specific reload animation. {@link #pose} returns joint offsets (degrees) to ADD on top of the normal ready pose for a
 * reload that is {@code p} (0..1) complete. Every curve starts and ends at exactly 0, so the arms never snap.
 * <ul>
 * <li>Pistol: quick magazine swap with one hand, then a slide rack.</li>
 * <li>Assault rifle: magazine out, magazine in, then pull the charging handle.</li>
 * <li>Shotgun: barrel up, three shells pushed in, then the pump.</li>
 * </ul>
 */
public final class ReloadPoses {

    public static final int LEFT_SHOULDER = 0, LEFT_ELBOW = 1, LEFT_YAW = 2, WEAPON_TILT = 3, RIGHT_SHOULDER = 4, RIGHT_ELBOW = 5;

    private ReloadPoses() {
    }

    /** Smooth 0 -> 1 -> 0 hump over [a, b]; exactly 0 outside. */
    static double bump(double p, double a, double b) {
        if (p <= a || p >= b) {
            return 0;
        }
        double s = Math.sin(Math.PI * (p - a) / (b - a));
        return s * s;
    }

    public static double[] pose(WeaponType w, double p) {
        p = Math.max(0, Math.min(1, p));
        double[] o = new double[6];
        switch (w) {
            case PISTOL: {
                double out = bump(p, 0.04, 0.62), rack = bump(p, 0.62, 0.88);
                o[LEFT_SHOULDER] = -50 * out - 16 * rack;
                o[LEFT_ELBOW] = 55 * out - 10 * rack;
                o[LEFT_YAW] = -14 * out;
                o[WEAPON_TILT] = -18 * bump(p, 0, 0.7) + 8 * rack;
                o[RIGHT_SHOULDER] = 5 * rack;
                o[RIGHT_ELBOW] = 12 * rack;
                break;
            }
            case ASSAULT_RIFLE: {
                double out = bump(p, 0.04, 0.5), in = bump(p, 0.5, 0.78), charge = bump(p, 0.78, 0.97);
                o[LEFT_SHOULDER] = -62 * out - 40 * in + 16 * charge;
                o[LEFT_ELBOW] = 62 * out + 40 * in - 22 * charge;
                o[LEFT_YAW] = -16 * out + 6 * charge;
                o[WEAPON_TILT] = -30 * bump(p, 0, 0.85);
                o[RIGHT_SHOULDER] = 4 * charge;
                break;
            }
            default: {
                double s1 = bump(p, 0.05, 0.3), s2 = bump(p, 0.3, 0.55), s3 = bump(p, 0.55, 0.8), pump = bump(p, 0.8, 0.97);
                double shells = s1 + s2 + s3;
                o[LEFT_SHOULDER] = -34 * shells + 20 * pump;
                o[LEFT_ELBOW] = 46 * shells - 32 * pump;
                o[LEFT_YAW] = -10 * shells;
                o[WEAPON_TILT] = 14 * bump(p, 0, 0.82) - 6 * pump;
                o[RIGHT_SHOULDER] = 6 * pump;
                break;
            }
        }
        return o;
    }
}
