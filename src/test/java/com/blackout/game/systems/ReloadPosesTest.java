package com.blackout.game.systems;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.blackout.game.entities.WeaponType;

import org.junit.jupiter.api.Test;

class ReloadPosesTest {

    @Test
    void everyReloadStartsAndEndsAtTheReadyPose() {
        for (WeaponType w : WeaponType.values()) {
            for (double p : new double[]{0, 1, -0.5, 1.5}) {
                for (double v : ReloadPoses.pose(w, p)) {
                    assertEquals(0, v, 1e-9, w + " at p=" + p);
                }
            }
        }
    }

    @Test
    void motionIsSmoothSoTheArmsNeverSnap() {
        for (WeaponType w : WeaponType.values()) {
            double[] prev = ReloadPoses.pose(w, 0);
            for (int i = 1; i <= 1000; i++) {
                double[] cur = ReloadPoses.pose(w, i / 1000.0);
                for (int j = 0; j < 6; j++) {
                    assertTrue(Math.abs(cur[j] - prev[j]) < 1.5, w + " joint " + j + " jumps at " + i / 1000.0);
                    assertFalse(Double.isNaN(cur[j]));
                }
                prev = cur;
            }
        }
    }

    @Test
    void eachWeaponHasItsOwnSequence() {
        double[] pistol = ReloadPoses.pose(WeaponType.PISTOL, 0.4);
        double[] rifle = ReloadPoses.pose(WeaponType.ASSAULT_RIFLE, 0.4);
        double[] shotgun = ReloadPoses.pose(WeaponType.SHOTGUN, 0.4);
        assertFalse(java.util.Arrays.equals(pistol, rifle));
        assertFalse(java.util.Arrays.equals(rifle, shotgun));
        assertTrue(shotgun[ReloadPoses.WEAPON_TILT] > 0, "shotgun muzzle goes up while loading shells");
        assertTrue(rifle[ReloadPoses.WEAPON_TILT] < 0, "rifle muzzle goes down for the magazine change");
    }

    @Test
    void shotgunLoadsThreeShells() {
        // left shoulder dips once per shell: count local minima while the hand is loading
        int dips = 0;
        double prev = 0, prev2 = 0;
        for (int i = 0; i <= 800; i++) {
            double v = ReloadPoses.pose(WeaponType.SHOTGUN, i / 1000.0)[ReloadPoses.LEFT_SHOULDER];
            if (i > 1 && prev < prev2 && prev < v && prev < -10) {
                dips++;
            }
            prev2 = prev;
            prev = v;
        }
        assertEquals(3, dips);
    }
}
