package com.blackout.game.systems;

import com.blackout.game.entities.WeaponType;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Handles inventory: items, stacking, containers, crafting, weight limit.
 * New system for Phase 1 architecture refactoring.
 */
public final class InventorySystem {

    public static final int MAX_WEIGHT = 50; // kg
    public static final int HOTBAR_SLOTS = 6;
    public static final int INVENTORY_ROWS = 4;
    public static final int INVENTORY_COLS = 6;
    public static final int INVENTORY_SLOTS = INVENTORY_ROWS * INVENTORY_COLS;

    // Hotbar (quick slots 1-6)
    private final ItemStack[] hotbar = new ItemStack[HOTBAR_SLOTS];

    // Main inventory grid
    private final ItemStack[][] inventory = new ItemStack[INVENTORY_ROWS][INVENTORY_COLS];

    // Current weight
    private double currentWeight = 0;

    public InventorySystem() {
        // Initialize with starting items
        hotbar[0] = new ItemStack(ItemType.PISTOL, 1);
        hotbar[1] = new ItemStack(ItemType.ASSAULT_RIFLE, 1);
        hotbar[2] = new ItemStack(ItemType.SHOTGUN, 1);
        hotbar[3] = new ItemStack(ItemType.MEDKIT, 1);
        hotbar[4] = new ItemStack(ItemType.GRENADE, 2);
        hotbar[5] = new ItemStack(ItemType.FLASHLIGHT, 1);
    }

    public boolean addItem(ItemType type, int count) {
        double weight = type.getWeight() * count;
        if (currentWeight + weight > MAX_WEIGHT) return false;

        // Try to stack in hotbar first
        for (int i = 0; i < HOTBAR_SLOTS; i++) {
            if (hotbar[i] != null && hotbar[i].type == type && hotbar[i].canStack()) {
                int canAdd = Math.min(count, type.getMaxStack() - hotbar[i].count);
                hotbar[i].count += canAdd;
                currentWeight += canAdd * type.getWeight();
                count -= canAdd;
                if (count == 0) return true;
            }
        }

        // Try to stack in inventory
        for (int r = 0; r < INVENTORY_ROWS; r++) {
            for (int c = 0; c < INVENTORY_COLS; c++) {
                if (inventory[r][c] != null && inventory[r][c].type == type && inventory[r][c].canStack()) {
                    int canAdd = Math.min(count, type.getMaxStack() - inventory[r][c].count);
                    inventory[r][c].count += canAdd;
                    currentWeight += canAdd * type.getWeight();
                    count -= canAdd;
                    if (count == 0) return true;
                }
            }
        }

        // Find empty slot in hotbar
        for (int i = 0; i < HOTBAR_SLOTS; i++) {
            if (hotbar[i] == null) {
                hotbar[i] = new ItemStack(type, count);
                currentWeight += weight;
                return true;
            }
        }

        // Find empty slot in inventory
        for (int r = 0; r < INVENTORY_ROWS; r++) {
            for (int c = 0; c < INVENTORY_COLS; c++) {
                if (inventory[r][c] == null) {
                    inventory[r][c] = new ItemStack(type, count);
                    currentWeight += weight;
                    return true;
                }
            }
        }

        return false; // Full
    }

    public boolean removeItem(ItemType type, int count) {
        // Remove from hotbar first
        for (int i = 0; i < HOTBAR_SLOTS; i++) {
            if (hotbar[i] != null && hotbar[i].type == type) {
                int remove = Math.min(count, hotbar[i].count);
                hotbar[i].count -= remove;
                currentWeight -= remove * type.getWeight();
                count -= remove;
                if (hotbar[i].count <= 0) hotbar[i] = null;
                if (count == 0) return true;
            }
        }

        // Remove from inventory
        for (int r = 0; r < INVENTORY_ROWS; r++) {
            for (int c = 0; c < INVENTORY_COLS; c++) {
                if (inventory[r][c] != null && inventory[r][c].type == type) {
                    int remove = Math.min(count, inventory[r][c].count);
                    inventory[r][c].count -= remove;
                    currentWeight -= remove * type.getWeight();
                    count -= remove;
                    if (inventory[r][c].count <= 0) inventory[r][c] = null;
                    if (count == 0) return true;
                }
            }
        }

        return false;
    }

    public int getCount(ItemType type) {
        int total = 0;
        for (int i = 0; i < HOTBAR_SLOTS; i++) {
            if (hotbar[i] != null && hotbar[i].type == type) total += hotbar[i].count;
        }
        for (int r = 0; r < INVENTORY_ROWS; r++) {
            for (int c = 0; c < INVENTORY_COLS; c++) {
                if (inventory[r][c] != null && inventory[r][c].type == type) total += inventory[r][c].count;
            }
        }
        return total;
    }

    public ItemStack getHotbarItem(int slot) {
        return slot >= 0 && slot < HOTBAR_SLOTS ? hotbar[slot] : null;
    }

    public void setHotbarItem(int slot, ItemStack stack) {
        if (slot >= 0 && slot < HOTBAR_SLOTS) hotbar[slot] = stack;
    }

    public ItemStack getInventoryItem(int row, int col) {
        return (row >= 0 && row < INVENTORY_ROWS && col >= 0 && col < INVENTORY_COLS) ? inventory[row][col] : null;
    }

    public void setInventoryItem(int row, int col, ItemStack stack) {
        if (row >= 0 && row < INVENTORY_ROWS && col >= 0 && col < INVENTORY_COLS) inventory[row][col] = stack;
    }

    public double getCurrentWeight() { return currentWeight; }
    public double getMaxWeight() { return MAX_WEIGHT; }
    public double getWeightRatio() { return currentWeight / MAX_WEIGHT; }

    // ===================== CRAFTING =====================
    public boolean canCraft(Recipe recipe) {
        for (ItemStack req : recipe.ingredients) {
            if (getCount(req.type) < req.count) return false;
        }
        // Check weight for result
        return currentWeight + recipe.result.type.getWeight() * recipe.result.count <= MAX_WEIGHT;
    }

    public boolean craft(Recipe recipe) {
        if (!canCraft(recipe)) return false;
        for (ItemStack req : recipe.ingredients) {
            removeItem(req.type, req.count);
        }
        addItem(recipe.result.type, recipe.result.count);
        return true;
    }

    // ===================== DATA CLASSES =====================
    public enum ItemType {
        // Weapons
        PISTOL(1.0, 1, "Pistol"),
        ASSAULT_RIFLE(3.5, 1, "Assault Rifle"),
        SHOTGUN(3.8, 1, "Shotgun"),
        // Melee
        BASEBALL_BAT(1.2, 1, "Baseball Bat"),
        KNIFE(0.3, 1, "Knife"),
        AXE(2.0, 1, "Axe"),
        CROWBAR(2.5, 1, "Crowbar"),
        // Ammo
        PISTOL_AMMO(0.01, 30, "9mm Ammo"),
        RIFLE_AMMO(0.015, 30, "5.56mm Ammo"),
        SHOTGUN_SHELLS(0.03, 12, "12 Gauge Shells"),
        // Medical
        MEDKIT(0.5, 3, "Medkit"),
        BANDAGE(0.1, 10, "Bandage"),
        ANTIBIOTICS(0.1, 5, "Antibiotics"),
        PAINKILLERS(0.1, 10, "Painkillers"),
        SPLINT(0.3, 2, "Splint"),
        BLOOD_BAG(0.4, 1, "Blood Bag"),
        // Food/Water
        CANNED_FOOD(0.4, 5, "Canned Food"),
        MRE(0.6, 3, "MRE"),
        WATER_BOTTLE(0.5, 3, "Water Bottle"),
        PURIFICATION_TABLETS(0.05, 20, "Purification Tablets"),
        // Tools
        FLASHLIGHT(0.3, 1, "Flashlight"),
        GRENADE(0.4, 3, "Grenade"),
        LOCKPICK(0.05, 10, "Lockpick"),
        // Crafting materials
        SCRAP_METAL(0.2, 20, "Scrap Metal"),
        WOOD(0.3, 20, "Wood"),
        CLOTH(0.1, 15, "Cloth"),
        CHEMICALS(0.2, 10, "Chemicals"),
        ELECTRONICS(0.15, 10, "Electronics");

        private final double weight;
        private final int maxStack;
        private final String displayName;

        ItemType(double weight, int maxStack, String displayName) {
            this.weight = weight;
            this.maxStack = maxStack;
            this.displayName = displayName;
        }

        public double getWeight() { return weight; }
        public int getMaxStack() { return maxStack; }
        public String getDisplayName() { return displayName; }
    }

    public static class ItemStack {
        public ItemType type;
        public int count;
        public double durability = 1.0; // 0-1 for weapons/tools

        public ItemStack(ItemType type, int count) {
            this.type = type;
            this.count = count;
        }

        public boolean canStack() { return count < type.getMaxStack(); }
        public double getWeight() { return type.getWeight() * count; }
    }

    public static class Recipe {
        public final ItemStack result;
        public final ItemStack[] ingredients;

        public Recipe(ItemStack result, ItemStack... ingredients) {
            this.result = result;
            this.ingredients = ingredients;
        }

        // Built-in recipes
        public static final Recipe MEDKIT_FROM_BANDAGES = new Recipe(
                new ItemStack(ItemType.MEDKIT, 1),
                new ItemStack(ItemType.BANDAGE, 5),
                new ItemStack(ItemType.CHEMICALS, 1),
                new ItemStack(ItemType.CLOTH, 2)
        );

        public static final Recipe RIFLE_AMMO_FROM_SCRAP = new Recipe(
                new ItemStack(ItemType.RIFLE_AMMO, 20),
                new ItemStack(ItemType.SCRAP_METAL, 5),
                new ItemStack(ItemType.CHEMICALS, 2)
        );

        public static final Recipe PISTOL_AMMO_FROM_SCRAP = new Recipe(
                new ItemStack(ItemType.PISTOL_AMMO, 30),
                new ItemStack(ItemType.SCRAP_METAL, 3),
                new ItemStack(ItemType.CHEMICALS, 1)
        );

        public static final Recipe SHOTGUN_SHELLS_FROM_SCRAP = new Recipe(
                new ItemStack(ItemType.SHOTGUN_SHELLS, 12),
                new ItemStack(ItemType.SCRAP_METAL, 4),
                new ItemStack(ItemType.CHEMICALS, 1)
        );

        public static final Recipe BANDAGE_FROM_CLOTH = new Recipe(
                new ItemStack(ItemType.BANDAGE, 3),
                new ItemStack(ItemType.CLOTH, 1)
        );

        public static final Recipe MOLOTOV = new Recipe(
                new ItemStack(ItemType.GRENADE, 1), // Reusing grenade slot for molotov
                new ItemStack(ItemType.WATER_BOTTLE, 1),
                new ItemStack(ItemType.CHEMICALS, 1),
                new ItemStack(ItemType.CLOTH, 1)
        );

        public static final Recipe[] ALL_RECIPES = {
            MEDKIT_FROM_BANDAGES,
            RIFLE_AMMO_FROM_SCRAP,
            PISTOL_AMMO_FROM_SCRAP,
            SHOTGUN_SHELLS_FROM_SCRAP,
            BANDAGE_FROM_CLOTH,
            MOLOTOV
        };
    }
}