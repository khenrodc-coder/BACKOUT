package com.blackout.game.systems;

import com.blackout.game.environment.Building;
import com.blackout.game.environment.HouseLayout;
import com.blackout.game.entities.WeaponType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Phase 7: Safehouse system - discovery, upgrades, sleeping, storage, defense.
 * Players can claim buildings as safehouses, upgrade them, sleep to recover,
 * store items, and defend against zombie sieges.
 */
public final class SafehouseSystem {

    private SafehouseSystem() {}

    // ============================================================
    // Safehouse Data
    // ============================================================

    public enum SafehouseTier {
        NONE(0, "Abandoned", 0),
        TIER_1(1, "Basic Shelter", 1),      // Claimed, bed placed
        TIER_2(2, "Fortified", 3),           // Reinforced doors/windows
        TIER_3(3, "Stronghold", 6),          // Workbench, storage, power
        TIER_4(4, "Sanctuary", 10);          // Full facilities, auto-defenses

        public final int level;
        public final String name;
        public final int maxUpgrades;

        SafehouseTier(int level, String name, int maxUpgrades) {
            this.level = level;
            this.name = name;
            this.maxUpgrades = maxUpgrades;
        }

        public static SafehouseTier fromLevel(int level) {
            for (SafehouseTier t : values()) if (t.level == level) return t;
            return NONE;
        }
    }

    public enum FacilityType {
        BED("Bed", 1, "Sleep recovery", new String[]{"CLOTH", "WOOD"}, 0),
        SLEEPING_BAG("Sleeping Bag", 1, "Portable sleep", new String[]{"CLOTH"}, 0),
        STASH("Stash", 2, "Item storage (50 slots)", new String[]{"WOOD", "SCRAP_METAL", "DUCT_TAPE"}, 1),
        GUN_RACK("Gun Rack", 2, "Weapon display/storage", new String[]{"WOOD", "SCRAP_METAL"}, 1),
        AMMO_CABINET("Ammo Cabinet", 2, "Organized ammo storage", new String[]{"WOOD", "SCRAP_METAL", "DUCT_TAPE"}, 1),
        WORKBENCH("Workbench", 3, "Crafting station", new String[]{"SCRAP_METAL", "WOOD", "TOOLS", "ELECTRONICS"}, 2),
        COOKING_STATION("Cooking Station", 2, "Cook food, boil water", new String[]{"SCRAP_METAL", "WOOD", "CLOTH"}, 1),
        WATER_COLLECTOR("Water Collector", 2, "Passive water generation", new String[]{"SCRAP_METAL", "CLOTH", "PURIFICATION_TABLETS"}, 2),
        GENERATOR("Generator", 4, "Power for lights/stations", new String[]{"SCRAP_METAL", "ELECTRONICS", "CHEMICALS", "GUNPOWDER"}, 3),
        SOLAR_PANEL("Solar Panel", 3, "Passive power", new String[]{"ELECTRONICS", "SCRAP_METAL", "CHEMICALS"}, 3),
        REINFORCED_DOOR("Reinforced Door", 2, "Stronger door, slower breach", new String[]{"SCRAP_METAL", "WOOD", "DUCT_TAPE"}, 1),
        BOARDED_WINDOWS("Boarded Windows", 2, "Windows block entry", new String[]{"WOOD", "DUCT_TAPE", "SCRAP_METAL"}, 1),
        METAL_SHUTTERS("Metal Shutters", 4, "Windows immune to breach", new String[]{"SCRAP_METAL", "ELECTRONICS", "DUCT_TAPE"}, 3),
        PERIMETER_ALARM("Perimeter Alarm", 3, "Warns of approaching zombies", new String[]{"ELECTRONICS", "CHEMICALS", "BATTERY"}, 2),
        SPIKE_TRAP("Spike Trap", 2, "Damages zombies at entrance", new String[]{"SCRAP_METAL", "WOOD", "DUCT_TAPE"}, 1),
        LANDMINE("Landmine", 4, "High damage trap", new String[]{"SCRAP_METAL", "CHEMICALS", "ELECTRONICS", "GUNPOWDER"}, 3),
        GARDEN("Garden", 3, "Passive food production", new String[]{"WOOD", "CLOTH", "WATER_BOTTLE", "SEEDS"}, 2),
        RAIN_CATCHER("Rain Catcher", 2, "Passive water from rain", new String[]{"CLOTH", "SCRAP_METAL", "DUCT_TAPE"}, 1),
        MEDICAL_STATION("Medical Station", 4, "Auto-heal injuries over time", new String[]{"MEDKIT", "ANTIBIOTICS", "CHEMICALS", "ELECTRONICS"}, 3),
        RADIO("Radio", 2, "Receive broadcasts, call supply drops", new String[]{"ELECTRONICS", "SCRAP_METAL", "CHEMICALS"}, 1),
        SHOWER("Shower", 3, "Removes wetness, boosts morale", new String[]{"SCRAP_METAL", "CLOTH", "CHEMICALS", "DUCT_TAPE"}, 1),
        TOILET("Toilet", 2, "Sanity recovery", new String[]{"SCRAP_METAL", "CLOTH", "CHEMICALS"}, 1),
        HEATING("Heating", 3, "Prevents hypothermia indoors", new String[]{"SCRAP_METAL", "ELECTRONICS", "CHEMICALS", "DUCT_TAPE"}, 2),
        AIR_FILTRATION("Air Filtration", 4, "Removes radiation indoors", new String[]{"ELECTRONICS", "CHEMICALS", "SCRAP_METAL", "FILTER"}, 3);

        public final String displayName;
        public final int tierRequired;
        public final String description;
        public final String[] buildCost;
        public final int powerCost;

        FacilityType(String displayName, int tierRequired, String description, String[] buildCost, int powerCost) {
            this.displayName = displayName;
            this.tierRequired = tierRequired;
            this.description = description;
            this.buildCost = buildCost;
            this.powerCost = powerCost;
        }
    }

    public enum UpgradeType {
        // Structural
        REINFORCE_WALLS("Reinforce Walls", 1, 2, "Increases building HP", new String[]{"SCRAP_METAL", "WOOD", "DUCT_TAPE"}),
        REINFORCE_FLOOR("Reinforce Floor", 2, 3, "Prevents basement breaches", new String[]{"SCRAP_METAL", "WOOD"}),
        REINFORCE_ROOF("Reinforce Roof", 2, 2, "Prevents roof entry", new String[]{"SCRAP_METAL", "WOOD"}),

        // Doors/Windows
        STEEL_DOOR("Steel Door", 3, 4, "Nearly unbreachable door", new String[]{"SCRAP_METAL", "ELECTRONICS", "DUCT_TAPE", "CHEMICALS"}),
        BULLETPROOF_WINDOWS("Bulletproof Windows", 4, 4, "Windows immune to damage", new String[]{"SCRAP_METAL", "ELECTRONICS", "CHEMICALS"}),

        // Utilities
        ELECTRICAL_WIRING("Electrical Wiring", 2, 1, "Enables powered facilities", new String[]{"ELECTRONICS", "SCRAP_METAL", "DUCT_TAPE"}),
        PLUMBING("Plumbing", 3, 2, "Running water, enables shower", new String[]{"SCRAP_METAL", "CLOTH", "CHEMICALS", "DUCT_TAPE"}),
        HEATING("Heating System", 3, 2, "Prevents hypothermia indoors", new String[]{"SCRAP_METAL", "ELECTRONICS", "CHEMICALS", "DUCT_TAPE"}),
        AIR_FILTRATION("Air Filtration", 4, 3, "Removes radiation indoors", new String[]{"ELECTRONICS", "CHEMICALS", "SCRAP_METAL", "FILTER"}),

        // Storage
        EXPANDED_STASH("Expanded Stash", 2, 1, "Doubles stash capacity", new String[]{"WOOD", "SCRAP_METAL", "DUCT_TAPE"}),
        GUN_RACK("Gun Rack", 2, 1, "Weapon display/storage", new String[]{"WOOD", "SCRAP_METAL"}),
        AMMO_CABINET("Ammo Cabinet", 2, 1, "Organized ammo storage", new String[]{"WOOD", "SCRAP_METAL", "DUCT_TAPE"}),

        // Defense
        AUTO_TURRET("Auto Turret", 4, 5, "Shoots zombies in range", new String[]{"ELECTRONICS", "SCRAP_METAL", "CHEMICALS", "GUNPOWDER", "RIFLE_AMMO"}),
        SPOTLIGHT("Spotlight", 2, 1, "Illuminates area at night", new String[]{"ELECTRONICS", "SCRAP_METAL", "DUCT_TAPE"}),
        BARBED_WIRE("Barbed Wire", 2, 1, "Slows/damages perimeter zombies", new String[]{"SCRAP_METAL", "WOOD"}),
        CLAYMORE("Claymore Mine", 4, 3, "Command-detonated trap", new String[]{"SCRAP_METAL", "CHEMICALS", "ELECTRONICS", "GUNPOWDER"}),

        // Quality of Life
        SHOWER("Shower", 3, 1, "Removes wetness, boosts morale", new String[]{"SCRAP_METAL", "CLOTH", "CHEMICALS", "DUCT_TAPE"}),
        TOILET("Toilet", 2, 1, "Sanity recovery", new String[]{"SCRAP_METAL", "CLOTH", "CHEMICALS"}),
        REFRIGERATOR("Refrigerator", 3, 2, "Slows food spoilage", new String[]{"ELECTRONICS", "SCRAP_METAL", "CHEMICALS", "DUCT_TAPE"}),
        FREEZER("Freezer", 4, 2, "Preserves food indefinitely", new String[]{"ELECTRONICS", "SCRAP_METAL", "CHEMICALS", "DUCT_TAPE"});

        public final String displayName;
        public final int tierRequired;
        public final int maxLevel;
        public final String description;
        public final String[] cost;

        UpgradeType(String displayName, int tierRequired, int maxLevel, String description, String[] cost) {
            this.displayName = displayName;
            this.tierRequired = tierRequired;
            this.maxLevel = maxLevel;
            this.description = description;
            this.cost = cost;
        }
    }

    // ============================================================
    // Safehouse Instance
    // ============================================================

    public static final class Safehouse {
        public final Building building;
        public final String id;
        public SafehouseTier tier = SafehouseTier.NONE;
        public int claimTime = 0;           // Game days since claimed
        public final Map<FacilityType, Integer> facilities = new HashMap<>();
        public final Map<UpgradeType, Integer> upgrades = new HashMap<>();
        public int powerGenerated = 0;
        public int powerConsumed = 0;
        public int storedWater = 0;         // Liters
        public int storedPower = 0;         // Battery charge
        public double lastSleepRecovery = 0;
        public int daysSinceLastSiege = 999;
        public int siegeCount = 0;
        public boolean isActive = true;     // Player currently using
        public String customName = "";

        // Storage
        public final Map<String, Integer> storage = new HashMap<>(); // itemId -> count
        public int storageCapacity = 20;   // Base slots

        public Safehouse(Building building, String id) {
            this.building = building;
            this.id = id;
        }

        public int getPowerNet() {
            return powerGenerated - powerConsumed;
        }

        public boolean hasPower() {
            return getPowerNet() >= 0;
        }

        public boolean hasFacility(FacilityType f) {
            return facilities.getOrDefault(f, 0) > 0;
        }

        public int getFacilityLevel(FacilityType f) {
            return facilities.getOrDefault(f, 0);
        }

        public int getUpgradeLevel(UpgradeType u) {
            return upgrades.getOrDefault(u, 0);
        }

        public boolean canBuild(FacilityType f, SafehouseSystem system) {
            if (tier.level < f.tierRequired) return false;
            if (getFacilityLevel(f) >= (f == FacilityType.BED || f == FacilityType.SLEEPING_BAG ? 1 : 1)) return false;
            return system.hasResources(this, f.buildCost);
        }

        public boolean canUpgrade(UpgradeType u, SafehouseSystem system) {
            if (tier.level < u.tierRequired) return false;
            if (getUpgradeLevel(u) >= u.maxLevel) return false;
            return system.hasResources(this, u.cost);
        }

        public void addFacility(FacilityType f) {
            facilities.put(f, getFacilityLevel(f) + 1);
            powerConsumed += f.powerCost;
            updateStorageCapacity();
        }

        public void addUpgrade(UpgradeType u) {
            upgrades.put(u, getUpgradeLevel(u) + 1);
        }

        private void updateStorageCapacity() {
            storageCapacity = 20;
            if (hasFacility(FacilityType.STASH)) storageCapacity += 30;
            if (getUpgradeLevel(UpgradeType.EXPANDED_STASH) > 0) storageCapacity *= 2;
            if (hasFacility(FacilityType.GUN_RACK)) storageCapacity += 10;
            if (hasFacility(FacilityType.AMMO_CABINET)) storageCapacity += 10;
        }
    }

    // ============================================================
    // Safehouse Manager
    // ============================================================

    private final Map<String, Safehouse> safehouses = new HashMap<>();
    private final Random rng = new Random();
    private String currentSafehouseId = null;
    private int sleepCycles = 0;

    // ============================================================
    // Discovery & Claiming
    // ============================================================

    /**
     * Check if a building can be claimed as a safehouse.
     */
    public boolean canClaim(Building building) {
        if (building == null) return false;
        if (safehouses.containsKey(building.id)) return false;

        // Must have a bed or space for one
        if (building.layout != null) {
            for (HouseLayout.Room room : building.layout.rooms) {
                if (room.type == HouseLayout.RoomType.BEDROOM) return true;
            }
        }
        // Or be a certain size
        return building.w * building.d >= 100;
    }

    /**
     * Claim a building as a safehouse.
     */
    public Safehouse claim(Building building, String playerName) {
        if (!canClaim(building)) return null;

        Safehouse sh = new Safehouse(building, building.id);
        sh.tier = SafehouseTier.TIER_1;
        sh.customName = playerName + "'s Shelter";
        sh.claimTime = 0;
        sh.storageCapacity = 20;

        // Auto-add sleeping bag if no bed
        boolean hasBed = false;
        if (building.layout != null) {
            for (HouseLayout.Prop prop : building.layout.props) {
                if (prop.kind == HouseLayout.Kind.BED) {
                    hasBed = true;
                    break;
                }
            }
        }
        if (!hasBed) {
            sh.facilities.put(FacilityType.SLEEPING_BAG, 1);
        }

        safehouses.put(building.id, sh);
        currentSafehouseId = building.id;
        return sh;
    }

    /**
     * Abandon a safehouse.
     */
    public void abandon(String safehouseId) {
        safehouses.remove(safehouseId);
        if (currentSafehouseId != null && currentSafehouseId.equals(safehouseId)) {
            currentSafehouseId = null;
        }
    }

    public Safehouse get(String id) {
        return safehouses.get(id);
    }

    public Safehouse getCurrent() {
        return currentSafehouseId != null ? safehouses.get(currentSafehouseId) : null;
    }

    public void setCurrent(String id) {
        if (safehouses.containsKey(id)) {
            currentSafehouseId = id;
            // Deactivate others
            for (Safehouse sh : safehouses.values()) {
                sh.isActive = sh.id.equals(id);
            }
        }
    }

    public List<Safehouse> getAll() {
        return new ArrayList<>(safehouses.values());
    }

    // ============================================================
    // Building & Upgrading
    // ============================================================

    /**
     * Check if safehouse has resources for a cost.
     */
    public boolean hasResources(Safehouse sh, String[] cost) {
        if (cost == null || cost.length == 0) return true;
        for (String itemId : cost) {
            int have = sh.storage.getOrDefault(itemId, 0);
            if (have <= 0) return false;
        }
        return true;
    }

    /**
     * Consume resources for building.
     */
    public void consumeResources(Safehouse sh, String[] cost) {
        for (String itemId : cost) {
            int have = sh.storage.getOrDefault(itemId, 0);
            sh.storage.put(itemId, Math.max(0, have - 1));
        }
    }

    /**
     * Build a facility.
     */
    public boolean buildFacility(String safehouseId, FacilityType facility) {
        Safehouse sh = safehouses.get(safehouseId);
        if (sh == null) return false;
        if (!sh.canBuild(facility, this)) return false;

        consumeResources(sh, facility.buildCost);
        sh.addFacility(facility);
        checkTierUpgrade(sh);
        return true;
    }

    /**
     * Apply an upgrade.
     */
    public boolean applyUpgrade(String safehouseId, UpgradeType upgrade) {
        Safehouse sh = safehouses.get(safehouseId);
        if (sh == null) return false;
        if (!sh.canUpgrade(upgrade, this)) return false;

        consumeResources(sh, upgrade.cost);
        sh.addUpgrade(upgrade);
        checkTierUpgrade(sh);
        return true;
    }

    /**
     * Check and update safehouse tier based on facilities/upgrades.
     */
    private void checkTierUpgrade(Safehouse sh) {
        int facilityScore = 0;
        for (Map.Entry<FacilityType, Integer> e : sh.facilities.entrySet()) {
            facilityScore += e.getKey().tierRequired * e.getValue();
        }
        int upgradeScore = 0;
        for (Map.Entry<UpgradeType, Integer> e : sh.upgrades.entrySet()) {
            upgradeScore += e.getKey().tierRequired * e.getValue();
        }

        int totalScore = facilityScore + upgradeScore;

        SafehouseTier newTier;
        if (totalScore >= 20) newTier = SafehouseTier.TIER_4;
        else if (totalScore >= 10) newTier = SafehouseTier.TIER_3;
        else if (totalScore >= 4) newTier = SafehouseTier.TIER_2;
        else newTier = SafehouseTier.TIER_1;

        if (newTier.level > sh.tier.level) {
            sh.tier = newTier;
        }
    }

    // ============================================================
    // Sleeping Mechanics
    // ============================================================

    public enum SleepQuality {
        POOR(0.3, "Restless sleep", "You toss and turn. Nightmares plague you."),
        FAIR(0.5, "Light sleep", "You wake up a few times."),
        GOOD(0.7, "Deep sleep", "You feel somewhat rested."),
        EXCELLENT(1.0, "Perfect rest", "You wake up fully refreshed."),
        SANCTUARY(1.2, "Sanctuary sleep", "The ultimate rest. Body and mind heal completely.");

        public final double multiplier;
        public final String label;
        public final String description;

        SleepQuality(double multiplier, String label, String description) {
            this.multiplier = multiplier;
            this.label = label;
            this.description = description;
        }
    }

    public static final class SleepResult {
        public final boolean success;
        public final SleepQuality quality;
        public final double healthRecovered;
        public final double staminaRecovered;
        public final double fatigueReduced;
        public final double hungerLost;
        public final double thirstLost;
        public final double sanityRecovered;
        public final double moraleRecovered;
        public final double infectionReduced;
        public final double radiationReduced;
        public final double bleedingReduced;
        public final String message;
        public final boolean nightmare;
        public final String[] events; // Random events during sleep

        public SleepResult(boolean success, SleepQuality quality, double healthRecovered,
                           double staminaRecovered, double fatigueReduced, double hungerLost,
                           double thirstLost, double sanityRecovered, double moraleRecovered,
                           double infectionReduced, double radiationReduced, double bleedingReduced,
                           String message, boolean nightmare, String[] events) {
            this.success = success;
            this.quality = quality;
            this.healthRecovered = healthRecovered;
            this.staminaRecovered = staminaRecovered;
            this.fatigueReduced = fatigueReduced;
            this.hungerLost = hungerLost;
            this.thirstLost = thirstLost;
            this.sanityRecovered = sanityRecovered;
            this.moraleRecovered = moraleRecovered;
            this.infectionReduced = infectionReduced;
            this.radiationReduced = radiationReduced;
            this.bleedingReduced = bleedingReduced;
            this.message = message;
            this.nightmare = nightmare;
            this.events = events;
        }
    }

    /**
     * Attempt to sleep in a safehouse.
     */
    public SleepResult sleep(Safehouse sh, SurvivalSystem survival, double hours, boolean forced) {
        if (sh == null) {
            return new SleepResult(false, SleepQuality.POOR, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                    "No safehouse available. Find a bed or sleeping bag.", false, new String[0]);
        }

        // Determine sleep quality
        SleepQuality quality = calculateSleepQuality(sh, survival, hours);
        if (quality == SleepQuality.POOR && !forced) {
            return new SleepResult(false, quality, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                    "Sleep quality too poor. Upgrade your bed or wait until safer.", false, new String[0]);
        }

        double mult = quality.multiplier;

        // Base recovery rates per hour
        double healthPerHour = 5 * mult;
        double staminaPerHour = 15 * mult;
        double fatiguePerHour = 20 * mult;
        double sanityPerHour = 3 * mult;
        double moralePerHour = 5 * mult;
        double infectionPerHour = 2 * mult;
        double radiationPerHour = 1 * mult;
        double bleedingPerHour = 5 * mult;

        // Facility bonuses
        if (sh.hasFacility(FacilityType.BED)) {
            healthPerHour *= 1.3;
            staminaPerHour *= 1.2;
            fatiguePerHour *= 1.3;
        }
        if (sh.hasFacility(FacilityType.MEDICAL_STATION)) {
            infectionPerHour *= 2.0;
            bleedingPerHour *= 1.5;
            healthPerHour *= 1.2;
        }
        if (sh.hasFacility(FacilityType.COOKING_STATION)) {
            healthPerHour *= 1.1;
        }
        if (sh.hasFacility(FacilityType.SHOWER)) {
            moralePerHour *= 1.3;
        }
        if (sh.hasFacility(FacilityType.TOILET)) {
            sanityPerHour *= 1.2;
        }

        // Apply upgrades
        if (sh.getUpgradeLevel(UpgradeType.HEATING) > 0) {
            fatiguePerHour *= 1.1;
        }
        if (sh.getUpgradeLevel(UpgradeType.AIR_FILTRATION) > 0) {
            radiationPerHour *= 1.5;
        }

        // Calculate totals
        double healthRec = Math.min(100 - survival.getHealth(), healthPerHour * hours);
        double staminaRec = Math.min(100 - survival.getStamina(), staminaPerHour * hours);
        double fatigueRed = Math.min(survival.getFatigue(), fatiguePerHour * hours);
        double hungerLost = 2 * hours; // Hunger decreases while sleeping
        double thirstLost = 3 * hours;
        double sanityRec = Math.min(100 - survival.getSanity(), sanityPerHour * hours);
        double moraleRec = Math.min(100 - survival.getMorale(), moralePerHour * hours);
        double infectionRed = Math.min(survival.getInfection(), infectionPerHour * hours);
        double radiationRed = Math.min(survival.getRadiation(), radiationPerHour * hours);
        double bleedingRed = Math.min(survival.getBleeding(), bleedingPerHour * hours);

        // Apply to survival
        survival.setHealth(survival.getHealth() + healthRec);
        survival.setStamina(survival.getStamina() + staminaRec);
        survival.fatigue = Math.max(0, survival.getFatigue() - fatigueRed);
        survival.hunger = Math.max(0, survival.getHunger() - hungerLost);
        survival.thirst = Math.max(0, survival.getThirst() - thirstLost);
        survival.setSanity(survival.getSanity() + sanityRec);
        survival.setMorale(survival.getMorale() + moraleRec);
        survival.infection = Math.max(0, survival.getInfection() - infectionRed);
        survival.radiation = Math.max(0, survival.getRadiation() - radiationRed);
        survival.bleeding = Math.max(0, survival.getBleeding() - bleedingRed);

        // Nightmare chance
        boolean nightmare = false;
        if (survival.getSanity() < 40 && rng.nextDouble() < 0.3) {
            nightmare = true;
            sanityRec *= 0.5;
        }

        // Random events
        String[] events = generateSleepEvents(sh, survival, hours);

        sleepCycles++;
        sh.lastSleepRecovery = System.currentTimeMillis();

        return new SleepResult(true, quality, healthRec, staminaRec, fatigueRed,
                hungerLost, thirstLost, sanityRec, moraleRec, infectionRed,
                radiationRed, bleedingRed,
                quality.description + (nightmare ? " But a nightmare disturbed you." : ""),
                nightmare, events);
    }

    private SleepQuality calculateSleepQuality(Safehouse sh, SurvivalSystem survival, double hours) {
        // Base quality from tier
        double baseScore = switch (sh.tier) {
            case TIER_1 -> 0.3;
            case TIER_2 -> 0.5;
            case TIER_3 -> 0.7;
            case TIER_4 -> 1.0;
            default -> 0.1;
        };

        // Bed quality
        if (sh.hasFacility(FacilityType.BED)) baseScore += 0.2;
        else if (sh.hasFacility(FacilityType.SLEEPING_BAG)) baseScore += 0.1;

        // Facilities
        if (sh.hasFacility(FacilityType.SHOWER)) baseScore += 0.1;
        if (sh.hasFacility(FacilityType.TOILET)) baseScore += 0.1;
        if (sh.hasFacility(FacilityType.HEATING)) baseScore += 0.1;
        if (sh.hasFacility(FacilityType.AIR_FILTRATION)) baseScore += 0.1;

        // Survival state
        if (survival.getSanity() < 30) baseScore -= 0.3;
        else if (survival.getSanity() < 50) baseScore -= 0.15;
        if (survival.getHealth() < 30) baseScore -= 0.2;
        if (survival.isBleeding()) baseScore -= 0.15;
        if (survival.isInfected()) baseScore -= 0.15;
        if (survival.isIrradiated()) baseScore -= 0.1;

        // Environment
        if (sh.daysSinceLastSiege < 2) baseScore -= 0.2;
        if (sh.siegeCount > 5) baseScore -= 0.1;

        // Hours slept
        if (hours >= 8) baseScore += 0.1;
        else if (hours < 4) baseScore -= 0.2;

        baseScore = Math.max(0, Math.min(1.2, baseScore));

        if (baseScore >= 1.0) return SleepQuality.SANCTUARY;
        if (baseScore >= 0.8) return SleepQuality.EXCELLENT;
        if (baseScore >= 0.6) return SleepQuality.GOOD;
        if (baseScore >= 0.4) return SleepQuality.FAIR;
        return SleepQuality.POOR;
    }

    private String[] generateSleepEvents(Safehouse sh, SurvivalSystem survival, double hours) {
        List<String> events = new ArrayList<>();

        // Random events during sleep
        double eventChance = hours / 8.0 * 0.3;
        if (rng.nextDouble() < eventChance) {
            String[] possibleEvents = {
                    "You hear distant gunfire. The war never ends.",
                    "A zombie scratches at the door. Your reinforcements hold.",
                    "Rain patters on the roof. A grim lullaby.",
                    "You dream of the world before. It feels like another life.",
                    "Static from the radio. A voice cuts through... then silence.",
                    "Footsteps outside. Heavy. Many. They pass by.",
                    "A dog barks in the distance. Then a yelp. Silence.",
                    "You wake up thirsty. The water collector gurgled all night."
            };
            events.add(possibleEvents[rng.nextInt(possibleEvents.length)]);
        }

        // Resource generation from facilities
        if (sh.hasFacility(FacilityType.WATER_COLLECTOR)) {
            int water = rng.nextInt(3) + 1;
            events.add("Water collector yielded " + water + " liters.");
        }
        if (sh.hasFacility(FacilityType.RAIN_CATCHER) && rng.nextDouble() < 0.4) {
            int water = rng.nextInt(2) + 1;
            events.add("Rain catcher collected " + water + " liters.");
        }
        if (sh.hasFacility(FacilityType.GARDEN)) {
            int food = rng.nextInt(2) + 1;
            events.add("Garden produced " + food + " units of food.");
        }
        if (sh.hasFacility(FacilityType.GENERATOR) && sh.storedPower > 0) {
            sh.storedPower = Math.max(0, sh.storedPower - 10);
        }

        return events.toArray(new String[0]);
    }

    // ============================================================
    // Storage Management
    // ============================================================

    public boolean storeItem(Safehouse sh, String itemId, int count) {
        if (sh == null) return false;
        int current = sh.storage.getOrDefault(itemId, 0);
        if (current + count > sh.storageCapacity) return false;
        sh.storage.put(itemId, current + count);
        return true;
    }

    public boolean takeItem(Safehouse sh, String itemId, int count) {
        if (sh == null) return false;
        int current = sh.storage.getOrDefault(itemId, 0);
        if (current < count) return false;
        sh.storage.put(itemId, current - count);
        return true;
    }

    public int getStoredCount(Safehouse sh, String itemId) {
        return sh == null ? 0 : sh.storage.getOrDefault(itemId, 0);
    }

    public Map<String, Integer> getAllStored(Safehouse sh) {
        return sh == null ? new HashMap<>() : new HashMap<>(sh.storage);
    }

    // ============================================================
    // Siege Events
    // ============================================================

    public static final class SiegeEvent {
        public final Safehouse safehouse;
        public final int wave;
        public int zombiesRemaining;
        public final boolean breached;
        public final long startTime;

        public SiegeEvent(Safehouse sh, int wave) {
            this.safehouse = sh;
            this.wave = wave;
            this.zombiesRemaining = 5 + wave * 3;
            this.breached = false;
            this.startTime = System.currentTimeMillis();
        }
    }

    private SiegeEvent currentSiege = null;

    /**
     * Trigger a siege on the current safehouse.
     */
    public SiegeEvent triggerSiege(String safehouseId) {
        Safehouse sh = safehouses.get(safehouseId);
        if (sh == null) return null;

        int wave = sh.siegeCount + 1;
        currentSiege = new SiegeEvent(sh, wave);
        sh.siegeCount++;
        sh.daysSinceLastSiege = 0;
        return currentSiege;
    }

    /**
     * Update siege state.
     */
    public void updateSiege(double dt, SurvivalSystem survival, ZombieAISystem zombieAI) {
        if (currentSiege == null) return;

        // Siege mechanics would integrate with zombie AI
        // For now, just track time
        currentSiege.zombiesRemaining = Math.max(0, currentSiege.zombiesRemaining - (int)(dt * 0.5));

        if (currentSiege.zombiesRemaining <= 0) {
            // Siege survived
            Safehouse sh = currentSiege.safehouse;
            sh.daysSinceLastSiege = 0;
            currentSiege = null;

            // Rewards for surviving
            survival.setMorale(Math.min(100, survival.getMorale() + 10));
        }
    }

    public SiegeEvent getCurrentSiege() {
        return currentSiege;
    }

    // ============================================================
    // Daily Update
    // ============================================================

    public void dailyUpdate() {
        for (Safehouse sh : safehouses.values()) {
            sh.claimTime++;
            sh.daysSinceLastSiege = Math.min(999, sh.daysSinceLastSiege + 1);

            // Power management
            if (sh.hasFacility(FacilityType.GENERATOR)) {
                sh.powerGenerated += 50;
                // Consume fuel from storage
                int fuel = sh.storage.getOrDefault("GUNPOWDER", 0);
                if (fuel > 0) {
                    sh.storage.put("GUNPOWDER", fuel - 1);
                    sh.storedPower = Math.min(100, sh.storedPower + 20);
                }
            }
            if (sh.hasFacility(FacilityType.SOLAR_PANEL)) {
                sh.powerGenerated += 20;
            }

            // Facility production
            if (sh.hasFacility(FacilityType.WATER_COLLECTOR)) {
                sh.storedWater = Math.min(100, sh.storedWater + rng.nextInt(5) + 2);
            }
            if (sh.hasFacility(FacilityType.GARDEN)) {
                // Food production handled in sleep
            }

            // Battery drain
            sh.storedPower = Math.max(0, sh.storedPower - sh.powerConsumed);
        }
    }

    // ============================================================
    // Save/Load
    // ============================================================

    public static class SaveData {
        public String currentSafehouseId;
        public int sleepCycles;
        public Map<String, SafehouseData> safehouses = new HashMap<>();
    }

    public static class SafehouseData {
        public String id;
        public String buildingId;
        public int tier;
        public int claimTime;
        public String customName;
        public Map<FacilityType, Integer> facilities = new HashMap<>();
        public Map<UpgradeType, Integer> upgrades = new HashMap<>();
        public int powerGenerated;
        public int powerConsumed;
        public int storedWater;
        public int storedPower;
        public long lastSleepRecovery;
        public int daysSinceLastSiege;
        public int siegeCount;
        public boolean isActive;
        public Map<String, Integer> storage = new HashMap<>();
    }

    public SaveData createSaveData() {
        SaveData d = new SaveData();
        d.currentSafehouseId = currentSafehouseId;
        d.sleepCycles = sleepCycles;
        for (Safehouse sh : safehouses.values()) {
            SafehouseData sd = new SafehouseData();
            sd.id = sh.id;
            sd.buildingId = sh.building.id;
            sd.tier = sh.tier.level;
            sd.claimTime = sh.claimTime;
            sd.customName = sh.customName;
            sd.facilities.putAll(sh.facilities);
            sd.upgrades.putAll(sh.upgrades);
            sd.powerGenerated = sh.powerGenerated;
            sd.powerConsumed = sh.powerConsumed;
            sd.storedWater = sh.storedWater;
            sd.storedPower = sh.storedPower;
            sd.lastSleepRecovery = (long) sh.lastSleepRecovery;
            sd.daysSinceLastSiege = sh.daysSinceLastSiege;
            sd.siegeCount = sh.siegeCount;
            sd.isActive = sh.isActive;
            sd.storage.putAll(sh.storage);
            d.safehouses.put(sh.id, sd);
        }
        return d;
    }

    public void loadSaveData(SaveData d) {
        currentSafehouseId = d.currentSafehouseId;
        sleepCycles = d.sleepCycles;
        safehouses.clear();
        for (SafehouseData sd : d.safehouses.values()) {
            // Would need building reference to fully reconstruct
            // For now, create placeholder
            Safehouse sh = new Safehouse(null, sd.id);
            sh.tier = SafehouseTier.fromLevel(sd.tier);
            sh.claimTime = sd.claimTime;
            sh.customName = sd.customName;
            sh.facilities.putAll(sd.facilities);
            sh.upgrades.putAll(sd.upgrades);
            sh.powerGenerated = sd.powerGenerated;
            sh.powerConsumed = sd.powerConsumed;
            sh.storedWater = sd.storedWater;
            sh.storedPower = sd.storedPower;
            sh.lastSleepRecovery = sd.lastSleepRecovery;
            sh.daysSinceLastSiege = sd.daysSinceLastSiege;
            sh.siegeCount = sd.siegeCount;
            sh.isActive = sd.isActive;
            sh.storage.putAll(sd.storage);
            sh.updateStorageCapacity();
            safehouses.put(sd.id, sh);
        }
    }
}