package com.blackout.game.core;

import java.util.HashMap;
import java.util.Map;

import javafx.scene.image.Image;
import javafx.scene.paint.Color;
import javafx.scene.paint.PhongMaterial;

/** Central cache of realistic materials so every model reuses the same few textures. */
public final class Mats {

    public static final int WATER_FRAMES = 8;

    public static final PhongMaterial GRASS_A = Gfx.tex(TextureFactory.grass(256, 58, 76, 36, 0.5), Color.rgb(8, 10, 8), 4);
    public static final PhongMaterial GRASS_B = Gfx.tex(TextureFactory.grass(256, 66, 80, 40, 0.8), Color.rgb(8, 10, 8), 4);
    public static final PhongMaterial GRASS_C = Gfx.tex(TextureFactory.grass(256, 48, 66, 34, 0.3), Color.rgb(8, 10, 8), 4);
    public static final PhongMaterial DIRT = Gfx.tex(TextureFactory.dirt(256, 96, 78, 56, false), Color.rgb(10, 10, 8), 4);
    public static final PhongMaterial MUD = Gfx.tex(TextureFactory.dirt(256, 62, 48, 36, true), Color.rgb(60, 60, 55), 40);
    public static final PhongMaterial GRAVEL = Gfx.tex(TextureFactory.gravel(256), Color.rgb(20, 20, 20), 6);
    public static final PhongMaterial ASPHALT = Gfx.tex(TextureFactory.asphalt(256, 77), Color.rgb(46, 46, 50), 26);
    public static final PhongMaterial CONCRETE = Gfx.tex(TextureFactory.concrete(256, 31), Color.rgb(30, 32, 30), 12);
    public static final PhongMaterial RUST = Gfx.tex(TextureFactory.rust(256, 92, 94, 98, 5), Color.rgb(110, 110, 115), 40);
    public static final PhongMaterial BRICK_RED = Gfx.tex(TextureFactory.brick(256, 150, 74, 56, 41), Color.rgb(18, 18, 18), 6);
    public static final PhongMaterial BRICK_BROWN = Gfx.tex(TextureFactory.brick(256, 116, 86, 62, 42), Color.rgb(18, 18, 18), 6);
    public static final PhongMaterial PLASTER_BEIGE = Gfx.tex(TextureFactory.plaster(256, 190, 178, 150, 51), Color.rgb(20, 20, 20), 8);
    public static final PhongMaterial PLASTER_BLUE = Gfx.tex(TextureFactory.plaster(256, 120, 148, 170, 52), Color.rgb(20, 20, 20), 8);
    public static final PhongMaterial PLASTER_GREEN = Gfx.tex(TextureFactory.plaster(256, 128, 156, 126, 53), Color.rgb(20, 20, 20), 8);
    public static final PhongMaterial PLASTER_GREY = Gfx.tex(TextureFactory.plaster(256, 150, 150, 144, 54), Color.rgb(20, 20, 20), 8);
    public static final PhongMaterial SIDING_WHITE = Gfx.tex(TextureFactory.wood(256, 150, 120, 84, 0.85, 61), Color.rgb(16, 16, 16), 8);
    public static final PhongMaterial SIDING_RED = Gfx.tex(TextureFactory.wood(256, 110, 60, 46, 0.5, 62), Color.rgb(16, 16, 16), 8);
    public static final PhongMaterial WOOD = Gfx.tex(TextureFactory.wood(256, 112, 82, 52, 0, 63), Color.rgb(24, 20, 16), 10);
    public static final PhongMaterial WOOD_DARK = Gfx.tex(TextureFactory.wood(256, 70, 52, 38, 0, 64), Color.rgb(14, 12, 10), 8);
    public static final PhongMaterial FABRIC_OLIVE = Gfx.tex(TextureFactory.fabric(128, 84, 98, 76), Color.rgb(10, 10, 10), 4);
    public static final PhongMaterial FABRIC_PANTS = Gfx.tex(TextureFactory.fabric(128, 70, 74, 66), Color.rgb(10, 10, 10), 4);
    public static final PhongMaterial FABRIC_DARK = Gfx.tex(TextureFactory.fabric(128, 38, 42, 44), Color.rgb(10, 10, 10), 4);
    public static final PhongMaterial FABRIC_BROWN = Gfx.tex(TextureFactory.fabric(128, 104, 80, 60), Color.rgb(10, 10, 10), 4);
    public static final PhongMaterial BARK = Gfx.tex(TextureFactory.bark(128, 74, 56, 40, false), Color.rgb(6, 6, 6), 3);
    public static final PhongMaterial BARK_DEAD = Gfx.tex(TextureFactory.bark(128, 58, 52, 46, false), Color.rgb(6, 6, 6), 3);
    public static final PhongMaterial BARK_BIRCH = Gfx.tex(TextureFactory.bark(128, 200, 196, 186, true), Color.rgb(10, 10, 10), 4);
    public static final PhongMaterial LEAF_OAK = Gfx.tex(TextureFactory.leaf(128, 52, 86, 34), Color.rgb(8, 10, 8), 4);
    public static final PhongMaterial LEAF_AUTUMN = Gfx.tex(TextureFactory.leaf(128, 128, 96, 34), Color.rgb(8, 10, 8), 4);
    public static final PhongMaterial LEAF_BIRCH = Gfx.tex(TextureFactory.leaf(128, 98, 126, 48), Color.rgb(8, 10, 8), 4);
    public static final PhongMaterial NEEDLE = Gfx.tex(TextureFactory.leaf(128, 28, 56, 36), Color.rgb(6, 8, 6), 4);
    public static final PhongMaterial ROCK = Gfx.tex(TextureFactory.concrete(128, 9), Color.rgb(14, 14, 16), 8);
    public static final PhongMaterial GLASS = Gfx.mat(Color.rgb(24, 38, 52, 0.55), Color.rgb(200, 220, 240), 120);
    public static final PhongMaterial GLASS_DARK = Gfx.mat(Color.rgb(10, 14, 20, 0.85), Color.rgb(160, 180, 210), 100);
    public static final PhongMaterial TIRE = Gfx.mat(Color.rgb(14, 14, 15), Color.rgb(36, 36, 38), 8);
    public static final PhongMaterial CHROME = Gfx.mat(Color.rgb(150, 154, 160), Color.WHITE, 90);
    public static final PhongMaterial BLACK_METAL = Gfx.mat(Color.rgb(24, 26, 28), Color.rgb(90, 92, 96), 40);
    public static final PhongMaterial STEEL_GREY = Gfx.mat(Color.rgb(88, 94, 98), Color.rgb(150, 156, 160), 50);
    public static final PhongMaterial TAR_ROOF = Gfx.mat(Color.rgb(46, 44, 42), Color.rgb(12, 12, 12), 4);
    public static final PhongMaterial TILE_ROOF = Gfx.tex(TextureFactory.brick(256, 112, 52, 38, 71), Color.rgb(14, 14, 14), 6);
    public static final PhongMaterial SLATE_ROOF = Gfx.tex(TextureFactory.brick(256, 62, 66, 72, 72), Color.rgb(20, 20, 22), 10);

    private static final Image[] WATER = new Image[WATER_FRAMES];
    private static final Map<Integer, PhongMaterial> PAINT = new HashMap<>();
    private static final Map<Integer, PhongMaterial> BLADES = new HashMap<>();

    private Mats() {
    }

    public static Image waterFrame(int i) {
        i = ((i % WATER_FRAMES) + WATER_FRAMES) % WATER_FRAMES;
        if (WATER[i] == null) {
            WATER[i] = TextureFactory.water(128, i / (double) WATER_FRAMES, 36, 78, 98);
        }
        return WATER[i];
    }

    public static PhongMaterial paint(Color c) {
        int key = (int) (c.getRed() * 255) << 16 | (int) (c.getGreen() * 255) << 8 | (int) (c.getBlue() * 255);
        return PAINT.computeIfAbsent(key, k -> Gfx.tex(TextureFactory.carPaint(128, c.getRed() * 255, c.getGreen() * 255, c.getBlue() * 255, k), Color.rgb(90, 90, 92), 50));
    }

    /** Alpha-cutout grass texture variants (0-3). */
    public static PhongMaterial blades(int variant) {
        return BLADES.computeIfAbsent(variant, k -> {
            double[][] cols = {{70, 104, 40}, {88, 110, 44}, {104, 100, 52}, {56, 88, 46}};
            double[] c = cols[k % 4];
            return Gfx.tex(TextureFactory.grassBlades(64, 64, 100 + k, c[0], c[1], c[2]), Color.BLACK, 1);
        });
    }

    public static PhongMaterial decal(int kind) {
        return BLADES.computeIfAbsent(100 + kind, k -> {
            double[][] cols = {{92, 74, 52}, {52, 40, 30}, {110, 104, 96}, {96, 70, 34}};
            double[] c = cols[kind % 4];
            return Gfx.tex(TextureFactory.decal(128, c[0], c[1], c[2]), Color.BLACK, 1);
        });
    }

    public static PhongMaterial mist() {
        return BLADES.computeIfAbsent(200, k -> Gfx.tex(TextureFactory.mist(128), Color.BLACK, 1));
    }

    public static PhongMaterial wall(int style) {
        switch (((style % 8) + 8) % 8) {
            case 0:
                return BRICK_RED;
            case 1:
                return BRICK_BROWN;
            case 2:
                return PLASTER_BEIGE;
            case 3:
                return PLASTER_BLUE;
            case 4:
                return PLASTER_GREEN;
            case 5:
                return SIDING_WHITE;
            case 6:
                return SIDING_RED;
            default:
                return PLASTER_GREY;
        }
    }
}
