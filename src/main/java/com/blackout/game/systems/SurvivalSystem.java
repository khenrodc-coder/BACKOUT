package com.blackout.game.systems;

import com.blackout.game.core.Gfx;

import java.util.Random;

/**
 * Handles survival mechanics: hunger, thirst, fatigue, injuries, stamina, medical.
 * New system for Phase 1 architecture refactoring.
 */
public final class SurvivalSystem {

    // Core vitals (0-100)
    public double hunger = 100;
    public double thirst = 100;
    public double fatigue = 0;      // Increases over time, sleep reduces
    public double stamina = 100;
    public double health = 100;

    // Injury system
    public double bleeding = 0;     // 0-100, bandages reduce
    public double infection = 0;    // 0-100, antibiotics reduce
    public boolean fractured = false;
    public boolean burned = false;

    // Rates per real second
    private static final double HUNGER_RATE = 0.02;      // ~1.4 hrs to starve
    private static final double THIRST_RATE = 0.035;     // ~48 min to dehydrate
    private static final double FATIGUE_RATE = 0.015;    // ~1.8 hrs to exhausted
    private static final double STAMINA_REGEN = 22;      // per second when not sprinting
    private static final double STAMINA_DRAIN = 30;      // per second when sprinting

    // Injury effects
    private static final double BLEED_HEALTH_DRAIN = 5;  // per second when bleeding > 0
    private static final double INFECTION_HEALTH_DRAIN = 2; // per second when infected > 0

    private final Random rng;

    public SurvivalSystem(Random rng) {
        this.rng = rng;
    }

    public void update(double dt, boolean sprinting, boolean sleeping, boolean grounded) {
        if (sleeping) {
            // Sleep recovers everything
            hunger = Math.min(100, hunger + dt * 8);
            thirst = Math.min(100, thirst + dt * 10);
            fatigue = Math.max(0, fatigue - dt * 25);
            health = Math.min(100, health + dt * 5);
            bleeding = Math.max(0, bleeding - dt * 15);
            infection = Math.max(0, infection - dt * 8);
            if (fatigue < 5) { fractured = false; burned = false; }
            return;
        }

        // Hunger
        hunger = Math.max(0, hunger - HUNGER_RATE * dt);
        if (hunger < 20) {
            stamina = Math.max(0, stamina - dt * 5); // Weakness
        }

        // Thirst
        thirst = Math.max(0, thirst - THIRST_RATE * dt);
        if (thirst < 15) {
            health = Math.max(0, health - dt * 3); // Dehydration damage
        }

        // Fatigue
        fatigue = Math.min(100, fatigue + FATIGUE_RATE * dt);
        if (fatigue > 80) {
            stamina = Math.max(0, stamina - dt * 8); // Exhaustion
        }
        if (fatigue > 95) {
            health = Math.max(0, health - dt * 1); // Passing out damage
        }

        // Stamina
        if (sprinting && grounded) {
            stamina = Math.max(0, stamina - STAMINA_DRAIN * dt);
        } else {
            double mult = hunger < 20 ? 0.5 : thirst < 15 ? 0.6 : fatigue > 80 ? 0.4 : 1.0;
            stamina = Math.min(100, stamina + STAMINA_REGEN * mult * dt);
        }

        // Bleeding
        if (bleeding > 0) {
            health = Math.max(0, health - BLEED_HEALTH_DRAIN * (bleeding / 100.0) * dt);
            bleeding = Math.max(0, bleeding - dt * 2); // Natural clotting
        }

        // Infection
        if (infection > 0) {
            health = Math.max(0, health - INFECTION_HEALTH_DRAIN * (infection / 100.0) * dt);
            if (rng.nextDouble() < dt * 0.01) infection = Math.min(100, infection + 5); // Worsens randomly
        }
    }

    // ===================== CONSUMABLES =====================
    public void consumeFood(double nutrition, double hydration) {
        hunger = Math.min(100, hunger + nutrition);
        thirst = Math.min(100, thirst + hydration);
    }

    public void drinkWater(double amount, boolean purified) {
        thirst = Math.min(100, thirst + amount);
        if (!purified && rng.nextDouble() < 0.3) {
            infection = Math.min(100, infection + 15); // Dysentery risk
        }
    }

    public void useBandage() {
        if (bleeding > 0) {
            bleeding = Math.max(0, bleeding - 30);
        }
    }

    public void useMedkit() {
        health = Math.min(100, health + 55);
        bleeding = Math.max(0, bleeding - 20);
        infection = Math.max(0, infection - 10);
    }

    public void useAntibiotics() {
        infection = Math.max(0, infection - 40);
    }

    public void usePainkillers() {
        // Temporary stamina boost, ignores fatigue
        stamina = Math.min(100, stamina + 30);
    }

    public void applySplint() {
        fractured = false;
    }

    public void treatBurn() {
        burned = false;
    }

    // ===================== INJURY APPLICATION =====================
    public void applyBleeding(double amount) {
        bleeding = Math.min(100, bleeding + amount);
    }

    public void applyInfection(double amount) {
        infection = Math.min(100, infection + amount);
    }

    public void applyFracture() {
        fractured = true;
    }

    public void applyBurn() {
        burned = true;
    }

    // ===================== STATUS CHECKS =====================
    public boolean isStarving() { return hunger <= 0; }
    public boolean isDehydrated() { return thirst <= 0; }
    public boolean isExhausted() { return fatigue >= 100; }
    public boolean isBleeding() { return bleeding > 0; }
    public boolean isInfected() { return infection > 0; }
    public boolean isFractured() { return fractured; }
    public boolean isBurned() { return burned; }

    public double getHunger() { return hunger; }
    public double getThirst() { return thirst; }
    public double getFatigue() { return fatigue; }
    public double getStamina() { return stamina; }
    public double getHealth() { return health; }
    public double getBleeding() { return bleeding; }
    public double getInfection() { return infection; }

    public void setHealth(double h) { health = Math.max(0, Math.min(100, h)); }
    public void setStamina(double s) { stamina = Math.max(0, Math.min(100, s)); }
}