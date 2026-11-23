package com.blackout.game.environment;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

import com.blackout.game.core.Gfx;
import com.blackout.game.core.Mats;
import com.blackout.game.world.ShopLayout;

import javafx.scene.Group;
import javafx.scene.paint.Color;
import javafx.scene.paint.PhongMaterial;

/** Shop fixtures (shelves stocked with goods, counters, fridge cabinets, crates). Models face +z and stand on the floor. */
public final class ShopProps {

    private ShopProps() {
    }

    private static final Map<Integer, PhongMaterial> CACHE = new HashMap<>();

    private static PhongMaterial color(int r, int g, int b) {
        return CACHE.computeIfAbsent((r << 16) | (g << 8) | b, k -> Gfx.mat(Color.rgb(r, g, b), Color.rgb(14, 14, 14), 6));
    }

    private static final PhongMaterial STEEL = Gfx.mat(Color.rgb(150, 154, 158), Color.rgb(120, 120, 124), 30), GLOW = Gfx.glow(Color.rgb(200, 230, 255)),
            WHITE = Gfx.mat(Color.rgb(226, 228, 230), Color.rgb(170, 170, 175), 60), CARD = color(150, 118, 78);
    private static final int[][] GOODS = {{200, 60, 50}, {60, 120, 200}, {230, 190, 50}, {70, 160, 90}, {210, 210, 205}, {150, 90, 160}, {230, 120, 40}};

    private static void b(Group g, PhongMaterial m, double w, double h, double d, double x, double y0, double z) {
        g.getChildren().add(Gfx.box(w, h, d, m, x, -(y0 + h / 2), z));
    }

    public static Group create(ShopLayout.Fixture f, Random rn) {
        Group g = new Group();
        double w = f.w, d = f.d, h = f.h;
        switch (f.kind) {
            case SHELF: {
                b(g, STEEL, 2, h, d, -w / 2 + 1, 0, 0);
                b(g, STEEL, 2, h, d, w / 2 - 1, 0, 0);
                b(g, color(70, 74, 78), w, h, 2, 0, 0, -d / 2 + 1);
                for (int i = 0; i < 4; i++) {
                    double y = 4 + i * 21;
                    b(g, STEEL, w - 4, 2, d - 2, 0, y, 0);
                    double x = -w / 2 + 5;
                    while (x < w / 2 - 8 && rn.nextDouble() < 0.93) {            // a gap now and then: shelves have been raided
                        double bw = 6 + rn.nextInt(6), bh = 9 + rn.nextInt(9);
                        int[] c = GOODS[rn.nextInt(GOODS.length)];
                        b(g, color(c[0], c[1], c[2]), bw, bh, d - 10, x + bw / 2, y + 2, 1);
                        x += bw + 1 + rn.nextInt(4);
                    }
                }
                b(g, STEEL, w, 2, d, 0, h - 2, 0);
                break;
            }
            case COUNTER:
                b(g, color(96, 80, 60), w, 36, d, 0, 0, 0);
                b(g, color(60, 62, 66), w + 2, 5, d + 3, 0, 36, 1);
                b(g, color(30, 32, 36), 18, 10, 14, w / 2 - 20, 41, -2);
                b(g, GLOW, 14, 6, 1, w / 2 - 20, 43, 5.2);
                break;
            case FRIDGE:
                b(g, WHITE, w, h, d, 0, 0, 0);
                b(g, Mats.GLASS_DARK, w - 6, h - 20, 1.5, 0, 8, d / 2 + 0.8);
                b(g, GLOW, w - 8, 2, 2, 0, h - 8, d / 2 + 0.5);
                break;
            default:   // CRATES
                b(g, CARD, w, h * 0.5, d, 0, 0, 0);
                b(g, color(130, 100, 64), w - 8, h * 0.5, d - 8, 2, h * 0.5, 1);
                break;
        }
        return g;
    }
}
