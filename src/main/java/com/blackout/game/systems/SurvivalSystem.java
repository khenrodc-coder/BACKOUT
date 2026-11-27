package com.blackout.game.systems;

import com.blackout.game.core.Gfx;
import com.blackout.game.entities.WeaponType;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Handles survival mechanics: hunger, thirst, fatigue, injuries, stamina, medical, temperature, radiation.
 * Phase 6 enhancements: detailed body parts, temperature, radiation, mental state, advanced crafting integration.
 */
public final class SurvivalSystem {

    // Core vitals (0-100)
    public double hunger = 100;
    public double thirst = 100;
    public double fatigue = 0;      // Increases over time, sleep reduces
    public double stamina = 100;
    public double health = 100;

    // Body part health (0-100 each)
    public double headHealth = 100;
    public double torsoHealth = 100;
    public double leftArmHealth = 100;
    public double rightArmHealth = 100;
    public double leftLegHealth = 100;
    public double rightLegHealth = 100;

    // Injury system
    public double bleeding = 0;     // 0-100, bandages reduce
    public double infection = 0;    // 0-100, antibiotics reduce
    public double radiation = 0;    // 0-100, anti-rad reduces
    public boolean fractured = false;
    public boolean burned = false;
    public boolean concussed = false;
    public boolean sprainedAnkle = false;
    public boolean sprainedWrist = false;

    // Environmental
    public double bodyTemp = 37.0;  // Celsius, normal 37
    public double wetness = 0;      // 0-100
    public double comfort = 50;     // 0-100, affects fatigue recovery

    // Mental state
    public double sanity = 100;     // 0-100, affects perception
    public double morale = 50;      // 0-100, affects stamina regen

    // Rates per real second
    private static final double HUNGER_RATE = 0.02;
    private static final double THIRST_RATE = 0.035;
    private static final double FATIGUE_RATE = 0.015;
    private static final double STAMINA_REGEN = 22;
    private static final double STAMINA_DRAIN = 30;

    private static final double BLEED_HEALTH_DRAIN = 5;
    private static final double INFECTION_HEALTH_DRAIN = 2;
    private static final double RADIATION_HEALTH_DRAIN = 1;
    private static final double TEMP_HEALTH_DRAIN = 0.5;

    private final Random rng;
    private double lastEatTime = 0;
    private double lastDrinkTime = 0;

    public SurvivalSystem(Random rng) {
        this.rng = rng;
    }

    public void update(double dt, boolean sprinting, boolean sleeping, boolean grounded,
                       double ambientTemp, boolean inWater, boolean nearFire, boolean bloodMoon) {
        if (sleeping) {
            // Sleep recovers everything but slowly
            hunger = Math.min(100, hunger + dt * 6);
            thirst = Math.min(100, thirst + dt * 8);
            fatigue = Math.max(0, fatigue - dt * 20);
            health = Math.min(100, health + dt * 3);
            bleeding = Math.max(0, bleeding - dt * 10);
            infection = Math.max(0, infection - dt * 5);
            radiation = Math.max(0, radiation - dt * 2);
            sanity = Math.min(100, sanity + dt * 2);
            morale = Math.min(100, morale + dt * 3);
            if (fatigue < 5) { fractured = false; burned = false; concussed = false; sprainedAnkle = false; sprainedWrist = false; }
            return;
        }

        // Environmental effects on body temp
        updateBodyTemp(dt, ambientTemp, inWater, nearFire, bloodMoon);

        // Wetness
        if (inWater) wetness = Math.min(100, wetness + dt * 20);
        else if (wetness > 0) wetness = Math.max(0, wetness - dt * 5);

        // Hunger
        double hungerMult = bodyTemp < 35 || bodyTemp > 39 ? 1.5 : 1.0;
        hunger = Math.max(0, hunger - HUNGER_RATE * hungerMult * dt);
        if (hunger < 20) stamina = Math.max(0, stamina - dt * 5);

        // Thirst
        double thirstMult = bodyTemp > 38 ? 1.5 : bodyTemp < 35 ? 0.8 : 1.0;
        thirst = Math.max(0, thirst - THIRST_RATE * thirstMult * dt);
        if (thirst < 15) health = Math.max(0, health - dt * 3);

        // Fatigue
        double fatigueMult = comfort < 30 ? 1.3 : comfort > 70 ? 0.8 : 1.0;
        fatigue = Math.min(100, fatigue + FATIGUE_RATE * fatigueMult * dt);
        if (fatigue > 80) stamina = Math.max(0, stamina - dt * 8);
        if (fatigue > 95) health = Math.max(0, health - dt * 1);

        // Stamina
        if (sprinting && grounded) {
            stamina = Math.max(0, stamina - STAMINA_DRAIN * dt);
            fatigue = Math.min(100, fatigue + dt * 0.01); // Sprinting increases fatigue
        } else {
            double mult = hunger < 20 ? 0.5 : thirst < 15 ? 0.6 : fatigue > 80 ? 0.4 : 1.0;
            mult *= morale < 30 ? 0.7 : morale > 70 ? 1.2 : 1.0;
            stamina = Math.min(100, stamina + STAMINA_REGEN * mult * dt);
        }

        // Bleeding
        if (bleeding > 0) {
            health = Math.max(0, health - BLEED_HEALTH_DRAIN * (bleeding / 100.0) * dt);
            bleeding = Math.max(0, bleeding - dt * (comfort > 50 ? 3 : 1.5));
        }

        // Infection
        if (infection > 0) {
            health = Math.max(0, health - INFECTION_HEALTH_DRAIN * (infection / 100.0) * dt);
            if (rng.nextDouble() < dt * 0.01) infection = Math.min(100, infection + 5);
        }

        // Radiation
        if (radiation > 0) {
            health = Math.max(0, health - RADIATION_HEALTH_DRAIN * (radiation / 100.0) * dt);
            if (rng.nextDouble() < dt * 0.005) infection = Math.min(100, infection + 3);
        }

        // Body part health effects
        updateBodyPartEffects(dt);

        // Sanity
        if (bloodMoon) sanity = Math.max(0, sanity - dt * 2);
        if (health < 30) sanity = Math.max(0, sanity - dt * 1);
        if (insanityEffects()) applyInsanityEffects(dt);

        // Morale
        morale = Math.max(0, Math.min(100, morale - dt * 0.1));
        if (hunger > 80 && thirst > 80) morale = Math.min(100, morale + dt * 0.5);
    }

    private void updateBodyTemp(double dt, double ambientTemp, boolean inWater, boolean nearFire, boolean bloodMoon) {
        double targetTemp = 37.0;

        if (inWater) targetTemp = ambientTemp;
        else if (nearFire) targetTemp = Math.min(39, ambientTemp + 10);
        else if (bloodMoon) targetTemp = Math.max(36, ambientTemp - 2);

        double diff = targetTemp - bodyTemp;
        double rate = inWater ? 0.5 : nearFire ? 0.3 : 0.1;
        if (wetness > 50) rate *= 1.5;

        bodyTemp += diff * rate * dt;
        bodyTemp = Math.max(25, Math.min(42, bodyTemp));

        // Hypothermia/hyperthermia damage
        if (bodyTemp < 35) {
            health = Math.max(0, health - TEMP_HEALTH_DRAIN * (35 - bodyTemp) * dt);
            stamina = Math.max(0, stamina - dt * 10);
        } else if (bodyTemp > 39) {
            health = Math.max(0, health - TEMP_HEALTH_DRAIN * (bodyTemp - 39) * dt);
            thirst = Math.max(0, thirst - dt * 5);
        }
    }

    private void updateBodyPartEffects(double dt) {
        // Leg injuries affect movement
        if (leftLegHealth < 50 || rightLegHealth < 50 || sprainedAnkle) {
            stamina = Math.max(0, stamina - dt * 5);
        }
        if (leftLegHealth < 30 || rightLegHealth < 30) {
            fatigue = Math.min(100, fatigue + dt * 2);
        }

        // Arm injuries affect combat
        if (leftArmHealth < 50 || rightArmHealth < 50 || sprainedWrist) {
            // Would affect weapon sway, reload speed
        }

        // Head injury
        if (headHealth < 70 || concussed) {
            sanity = Math.max(0, sanity - dt * 1);
        }

        // Torso injury affects overall health
        if (torsoHealth < 50) {
            health = Math.max(0, health - dt * 0.5);
        }
    }

    private boolean insanityEffects() {
        return sanity < 40;
    }

    private void applyInsanityEffects(double dt) {
        // Hallucinations, stamina drain, etc.
        stamina = Math.max(0, stamina - dt * 3);
        if (rng.nextDouble() < dt * 0.02) {
            // Spawn fake enemy sound
        }
    }

    // ===================== CONSUMABLES =====================
    public void consumeFood(double nutrition, double hydration, double comfortBonus) {
        hunger = Math.min(100, hunger + nutrition);
        thirst = Math.min(100, thirst + hydration);
        comfort = Math.min(100, comfort + comfortBonus);
        lastEatTime = 0;
        morale = Math.min(100, morale + 2);
    }

    public void drinkWater(double amount, boolean purified, double temp) {
        thirst = Math.min(100, thirst + amount);
        if (!purified && rng.nextDouble() < 0.3) {
            infection = Math.min(100, infection + 15);
        }
        if (temp < 5) bodyTemp = Math.max(35, bodyTemp - 1);
        else if (temp > 60) bodyTemp = Math.min(39, bodyTemp + 1);
        lastDrinkTime = 0;
    }

    public void useBandage() {
        if (bleeding > 0) {
            bleeding = Math.max(0, bleeding - 30);
            comfort = Math.min(100, comfort + 5);
        }
    }

    public void useAdvancedBandage() { // Hemostatic
        if (bleeding > 0) {
            bleeding = Math.max(0, bleeding - 60);
            comfort = Math.min(100, comfort + 10);
        }
    }

    public void useMedkit() {
        health = Math.min(100, health + 55);
        torsoHealth = Math.min(100, torsoHealth + 30);
        bleeding = Math.max(0, bleeding - 20);
        infection = Math.max(0, infection - 10);
    }

    public void useTraumaKit() {
        health = Math.min(100, health + 80);
        torsoHealth = Math.min(100, torsoHealth + 50);
        bleeding = Math.max(0, bleeding - 40);
        infection = Math.max(0, infection - 5);
        // Heals body parts
        double[] parts = getBodyParts();
        parts[0] = Math.min(100, parts[0] + 20); // head
        parts[1] = Math.min(100, parts[1] + 20); // torso
        parts[2] = Math.min(100, parts[2] + 20); // left arm
        parts[3] = Math.min(100, parts[3] + 20); // right arm
        parts[4] = Math.min(100, parts[4] + 20); // left leg
        parts[5] = Math.min(100, parts[5] + 20); // right leg
    }

    public void useAntibiotics() {
        infection = Math.max(0, infection - 40);
    }

    public void useBroadSpectrumAntibiotics() {
        infection = Math.max(0, infection - 70);
        radiation = Math.max(0, radiation - 10);
    }

    public void usePainkillers() {
        stamina = Math.min(100, stamina + 30);
        comfort = Math.min(100, comfort + 15);
        // Temporary - would wear off
    }

    public void useStimpack() {
        stamina = Math.min(100, stamina + 50);
        fatigue = Math.max(0, fatigue - 20);
        health = Math.min(100, health + 10);
    }

    public void useAntiRad() {
        radiation = Math.max(0, radiation - 30);
    }

    public void useAntiRadAdvanced() {
        radiation = Math.max(0, radiation - 60);
        infection = Math.max(0, infection - 10);
    }

    public void applySplint() {
        fractured = false;
        leftLegHealth = Math.min(100, leftLegHealth + 20);
        rightLegHealth = Math.min(100, rightLegHealth + 20);
        leftArmHealth = Math.min(100, leftArmHealth + 20);
        rightArmHealth = Math.min(100, rightArmHealth + 20);
    }

    public void treatBurn() {
        burned = false;
        bodyTemp = Math.max(36, bodyTemp - 1);
    }

    public void treatConcussion() {
        concussed = false;
        headHealth = Math.min(100, headHealth + 30);
        sanity = Math.min(100, sanity + 10);
    }

    // ===================== INJURY APPLICATION =====================
    public void applyBleeding(double amount) {
        bleeding = Math.min(100, bleeding + amount);
    }

    public void applyInfection(double amount) {
        infection = Math.min(100, infection + amount);
    }

    public void applyRadiation(double amount) {
        radiation = Math.min(100, radiation + amount);
    }

    public void applyFracture() {
        fractured = true;
        // Random limb
        switch (rng.nextInt(4)) {
            case 0 -> leftLegHealth = Math.min(leftLegHealth, 40);
            case 1 -> rightLegHealth = Math.min(rightLegHealth, 40);
            case 2 -> leftArmHealth = Math.min(leftArmHealth, 40);
            case 3 -> rightArmHealth = Math.min(rightArmHealth, 40);
        }
    }

    public void applyBurn() {
        burned = true;
        bodyTemp = Math.min(39, bodyTemp + 2);
        torsoHealth = Math.min(torsoHealth, 60);
    }

    public void applyConcussion() {
        concussed = true;
        headHealth = Math.min(headHealth, 50);
        sanity = Math.max(0, sanity - 15);
    }

    public void applySprain(boolean leg) {
        if (leg) sprainedAnkle = true;
        else sprainedWrist = true;
    }

    public void damageBodyPart(String part, double damage) {
        switch (part.toLowerCase()) {
            case "head" -> headHealth = Math.max(0, headHealth - damage);
            case "torso" -> torsoHealth = Math.max(0, torsoHealth - damage);
            case "leftarm" -> leftArmHealth = Math.max(0, leftArmHealth - damage);
            case "rightarm" -> rightArmHealth = Math.max(0, rightArmHealth - damage);
            case "leftleg" -> leftLegHealth = Math.max(0, leftLegHealth - damage);
            case "rightleg" -> rightLegHealth = Math.max(0, rightLegHealth - damage);
        }
    }

    private double[] getBodyParts() {
        return new double[]{headHealth, torsoHealth, leftArmHealth, rightArmHealth, leftLegHealth, rightLegHealth};
    }

    // ===================== STATUS CHECKS =====================
    public boolean isStarving() { return hunger <= 0; }
    public boolean isDehydrated() { return thirst <= 0; }
    public boolean isExhausted() { return fatigue >= 100; }
    public boolean isBleeding() { return bleeding > 0; }
    public boolean isInfected() { return infection > 0; }
    public boolean isIrradiated() { return radiation > 0; }
    public boolean isFractured() { return fractured; }
    public boolean isBurned() { return burned; }
    public boolean isConcussed() { return concussed; }
    public boolean isSprainedAnkle() { return sprainedAnkle; }
    public boolean isSprainedWrist() { return sprainedWrist; }
    public boolean isHypothermic() { return bodyTemp < 35; }
    public boolean isHyperthermic() { return bodyTemp > 39; }
    public boolean isInsane() { return sanity < 40; }

    public double getHunger() { return hunger; }
    public double getThirst() { return thirst; }
    public double getFatigue() { return fatigue; }
    public double getStamina() { return stamina; }
    public double getHealth() { return health; }
    public double getBleeding() { return bleeding; }
    public double getInfection() { return infection; }
    public double getRadiation() { return radiation; }
    public double getBodyTemp() { return bodyTemp; }
    public double getWetness() { return wetness; }
    public double getComfort() { return comfort; }
    public double getSanity() { return sanity; }
    public double getMorale() { return morale; }

    public void setHealth(double h) { health = Math.max(0, Math.min(100, h)); }
    public void setStamina(double s) { stamina = Math.max(0, Math.min(100, s)); }
    public void setSanity(double s) { sanity = Math.max(0, Math.min(100, s)); }
    public void setMorale(double m) { morale = Math.max(0, Math.min(100, m)); }

    // ===================== SAVE/LOAD DATA =====================
    public static class SaveData {
        public double hunger, thirst, fatigue, stamina, health;
        public double headHealth, torsoHealth, leftArmHealth, rightArmHealth, leftLegHealth, rightLegHealth;
        public double bleeding, infection, radiation;
        public boolean fractured, burned, concussed, sprainedAnkle, sprainedWrist;
        public double bodyTemp, wetness, comfort;
        public double sanity, morale;
    }

    public SaveData createSaveData() {
        SaveData d = new SaveData();
        d.hunger = hunger; d.thirst = thirst; d.fatigue = fatigue; d.stamina = stamina; d.health = health;
        d.headHealth = headHealth; d.torsoHealth = torsoHealth; d.leftArmHealth = leftArmHealth;
        d.rightArmHealth = rightArmHealth; d.leftLegHealth = leftLegHealth; d.rightLegHealth = rightLegHealth;
        d.bleeding = bleeding; d.infection = infection; d.radiation = radiation;
        d.fractured = fractured; d.burned = burned; d.concussed = concussed;
        d.sprainedAnkle = sprainedAnkle; d.sprainedWrist = sprainedWrist;
        d.bodyTemp = bodyTemp; d.wetness = wetness; d.comfort = comfort;
        d.sanity = sanity; d.morale = morale;
        return d;
    }

    public void loadSaveData(SaveData d) {
        hunger = d.hunger; thirst = d.thirst; fatigue = d.fatigue; stamina = d.stamina; health = d.health;
        headHealth = d.headHealth; torsoHealth = d.torsoHealth; leftArmHealth = d.leftArmHealth;
        rightArmHealth = d.rightArmHealth; leftLegHealth = d.leftLegHealth; rightLegHealth = d.rightLegHealth;
        bleeding = d.bleeding; infection = d.infection; radiation = d.radiation;
        fractured = d.fractured; burned = d.burned; concussed = d.concussed;
        sprainedAnkle = d.sprainedAnkle; sprainedWrist = d.sprainedWrist;
        bodyTemp = d.bodyTemp; wetness = d.wetness; comfort = d.comfort;
        sanity = d.sanity; morale = d.morale;
    }
}