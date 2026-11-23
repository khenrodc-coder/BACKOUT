package com.blackout.game.environment;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

import com.blackout.game.core.Gfx;
import com.blackout.game.core.Mats;
import com.blackout.game.environment.HouseLayout.Kind;
import com.blackout.game.environment.HouseLayout.Prop;

import javafx.scene.Group;
import javafx.scene.paint.Color;
import javafx.scene.paint.PhongMaterial;
import javafx.scene.transform.Rotate;

/**
 * Furniture models for {@link HouseBuilder}. Every model is built around the origin at floor level (y is negative upwards), facing
 * +z, with the footprint given by {@link HouseLayout#size(Kind)} so the 3D object matches its collision box exactly.
 */
public final class HouseProps {

    private HouseProps() {
    }

    private static final Map<Integer, PhongMaterial> CACHE = new HashMap<>();

    private static PhongMaterial color(int r, int g, int b) {
        return CACHE.computeIfAbsent((r << 16) | (g << 8) | b, k -> Gfx.mat(Color.rgb(r, g, b), Color.rgb(14, 14, 14), 6));
    }

    private static PhongMaterial shiny(int r, int g, int b) {
        return CACHE.computeIfAbsent(0x1000000 | (r << 16) | (g << 8) | b, k -> Gfx.mat(Color.rgb(r, g, b), Color.rgb(170, 170, 175), 60));
    }

    private static final PhongMaterial WHITE = shiny(226, 228, 230), STONE = color(70, 72, 76), BLACK = shiny(18, 18, 20),
            DARK_WOOD = Mats.WOOD_DARK, WOOD = Mats.WOOD, CARDBOARD = color(150, 118, 78), WATER = color(90, 130, 150),
            BLOOD = color(84, 12, 12);
    private static final int[][] FABRICS = {{96, 52, 52}, {58, 74, 100}, {86, 98, 76}, {120, 104, 84}, {64, 64, 70}, {110, 70, 50}};
    private static final int[][] RUGS = {{110, 54, 48}, {60, 76, 96}, {96, 84, 62}, {70, 90, 74}};

    /** Adds a box whose BOTTOM is {@code y0} above the floor. */
    private static void b(Group g, PhongMaterial m, double w, double h, double d, double x, double y0, double z) {
        g.getChildren().add(Gfx.box(w, h, d, m, x, -(y0 + h / 2), z));
    }

    private static PhongMaterial fabric(Random rn) {
        int[] f = FABRICS[rn.nextInt(FABRICS.length)];
        return color(f[0], f[1], f[2]);
    }

    public static Group create(Prop p, Random rn) {
        Group g = new Group();
        double w = p.w, d = p.d, h = p.h;
        switch (p.kind) {
            case BED: {
                PhongMaterial sheet = fabric(rn), pillow = color(205, 200, 190);
                b(g, DARK_WOOD, w, 12, d, 0, 5, 0);
                b(g, sheet, w - 4, 15, d - 6, 0, 17, 3);
                b(g, DARK_WOOD, w, 44, 6, 0, 5, -d / 2 + 3);
                b(g, pillow, 34, 7, 20, -20, 32, -d / 2 + 18);
                b(g, pillow, 34, 7, 20, 20, 32, -d / 2 + 18);
                b(g, color(FABRICS[0][0] + 20, FABRICS[0][1] + 10, FABRICS[0][2] + 10), w - 6, 4, 62, 0, 32, 22);
                break;
            }
            case NIGHTSTAND:
                b(g, WOOD, w, h - 3, d, 0, 0, 0);
                b(g, DARK_WOOD, w + 2, 3, d + 2, 0, h - 3, 0);
                b(g, DARK_WOOD, w - 8, 8, 1, 0, 14, d / 2 + 0.5);
                break;
            case WARDROBE:
                b(g, WOOD, w, h, d, 0, 0, 0);
                b(g, DARK_WOOD, w / 2 - 3, h - 14, 1.5, -w / 4, 7, d / 2 + 0.7);
                b(g, DARK_WOOD, w / 2 - 3, h - 14, 1.5, w / 4, 7, d / 2 + 0.7);
                b(g, Mats.CHROME, 2, 14, 2, -3, h / 2 - 8, d / 2 + 2);
                b(g, Mats.CHROME, 2, 14, 2, 3, h / 2 - 8, d / 2 + 2);
                break;
            case DRESSER: {
                b(g, WOOD, w, h - 3, d, 0, 0, 0);
                b(g, DARK_WOOD, w + 2, 3, d + 2, 0, h - 3, 0);
                for (int i = 0; i < 3; i++) {
                    double pull = rn.nextDouble() < 0.18 ? 9 : 0;           // the odd drawer left open
                    b(g, DARK_WOOD, w - 8, 14, 1.5 + pull, 0, 4 + i * 16.5, d / 2 + 0.7 + pull / 2);
                    b(g, Mats.CHROME, 12, 2, 2, 0, 10 + i * 16.5, d / 2 + 2 + pull);
                }
                break;
            }
            case SOFA:
            case ARMCHAIR: {
                PhongMaterial f = fabric(rn);
                boolean sofa = p.kind == Kind.SOFA;
                double arm = sofa ? 14 : 10;
                b(g, DARK_WOOD, w, 5, d, 0, 0, 0);
                b(g, f, w, 14, d, 0, 5, 0);
                b(g, f, w, 26, 14, 0, 14, -d / 2 + 7);
                b(g, f, arm, 22, d - 6, -w / 2 + arm / 2, 14, 3);
                b(g, f, arm, 22, d - 6, w / 2 - arm / 2, 14, 3);
                int seats = sofa ? 2 : 1;
                double sw = (w - 2 * arm - 4) / seats;
                for (int i = 0; i < seats; i++) {
                    b(g, f, sw - 2, 8, d - 22, -(w - 2 * arm) / 2 + sw * (i + 0.5) + 1, 19, 8);
                }
                break;
            }
            case COFFEE_TABLE:
                b(g, WOOD, w, 4, d, 0, h - 4, 0);
                for (int sx = -1; sx <= 1; sx += 2) {
                    for (int sz = -1; sz <= 1; sz += 2) {
                        b(g, DARK_WOOD, 4, h - 4, 4, sx * (w / 2 - 4), 0, sz * (d / 2 - 4));
                    }
                }
                b(g, color(190, 184, 170), 20, 2, 14, -8, h, 2);          // a magazine
                break;
            case TV_STAND:
                b(g, WOOD, w, h - 3, d, 0, 0, 0);
                b(g, DARK_WOOD, w + 2, 3, d + 2, 0, h - 3, 0);
                b(g, BLACK, 8, 6, 8, 0, h, 0);
                b(g, BLACK, 66, 40, 4, 0, h + 6, 0);
                b(g, shiny(30, 40, 46), 62, 36, 1, 0, h + 8, 2.2);
                break;
            case BOOKSHELF: {
                b(g, WOOD, 3, h, d, -w / 2 + 1.5, 0, 0);
                b(g, WOOD, 3, h, d, w / 2 - 1.5, 0, 0);
                b(g, WOOD, w, h, 2, 0, 0, -d / 2 + 1);
                for (int i = 0; i < 5; i++) {
                    double y = i * 22;
                    b(g, WOOD, w, 2.5, d, 0, y, 0);
                    double x = -w / 2 + 6;
                    while (x < w / 2 - 8 && i < 4) {
                        double bw = 3 + rn.nextInt(4), bh = 12 + rn.nextInt(8);
                        int[] c = FABRICS[rn.nextInt(FABRICS.length)];
                        b(g, color(c[0], c[1], c[2]), bw, bh, d - 8, x + bw / 2, y + 2.5, 0);
                        x += bw + 0.5;
                    }
                }
                b(g, WOOD, w, 2.5, d, 0, h - 2.5, 0);
                break;
            }
            case TABLE:
                b(g, WOOD, w, 4, d, 0, h - 4, 0);
                for (int sx = -1; sx <= 1; sx += 2) {
                    for (int sz = -1; sz <= 1; sz += 2) {
                        b(g, DARK_WOOD, 5, h - 4, 5, sx * (w / 2 - 5), 0, sz * (d / 2 - 5));
                    }
                }
                if (rn.nextDouble() < 0.5) {
                    b(g, color(210, 206, 196), 14, 2, 14, 10, h, 6);      // a plate left behind
                }
                break;
            case CHAIR: {
                Group c = new Group();
                b(c, WOOD, w, 4, d, 0, 26, 0);
                for (int sx = -1; sx <= 1; sx += 2) {
                    for (int sz = -1; sz <= 1; sz += 2) {
                        b(c, DARK_WOOD, 3, 26, 3, sx * (w / 2 - 3), 0, sz * (d / 2 - 3));
                    }
                }
                b(c, WOOD, w, 24, 3, 0, 30, -d / 2 + 2);
                if (p.fallen) {                                           // lying on its side
                    c.getTransforms().add(new Rotate(90, Rotate.Z_AXIS));
                    c.setTranslateY(-w / 2);
                }
                g.getChildren().add(c);
                break;
            }
            case COUNTER:
            case SINK_COUNTER:
            case STOVE_COUNTER: {
                b(g, WOOD, w, 52, d, 0, 0, 0);
                b(g, DARK_WOOD, w - 6, 40, 1.5, 0, 6, d / 2 + 0.7);
                b(g, STONE, w + 2, 4, d + 2, 0, 52, 0);
                if (p.kind == Kind.SINK_COUNTER) {
                    b(g, shiny(160, 164, 170), 36, 1, 24, 0, 56, 2);
                    b(g, Mats.CHROME, 2.5, 14, 2.5, 0, 56, -d / 2 + 5);
                    b(g, Mats.CHROME, 2.5, 2.5, 10, 0, 68, -d / 2 + 9);
                } else if (p.kind == Kind.STOVE_COUNTER) {
                    for (int sx = -1; sx <= 1; sx += 2) {
                        for (int sz = -1; sz <= 1; sz += 2) {
                            g.getChildren().add(Gfx.cylY(7, 1.2, BLACK, sx * 12, -56.6, sz * 8));
                        }
                    }
                    b(g, shiny(40, 44, 50), w - 14, 22, 1, 0, 10, d / 2 + 1.3);
                }
                break;
            }
            case FRIDGE:
                b(g, WHITE, w, h, d, 0, 0, 0);
                b(g, color(150, 154, 156), w - 4, 1, 1, 0, h * 0.62, d / 2 + 0.6);
                b(g, Mats.CHROME, 2, 26, 3, w / 2 - 7, h * 0.7, d / 2 + 2);
                b(g, Mats.CHROME, 2, 20, 3, w / 2 - 7, h * 0.28, d / 2 + 2);
                break;
            case TOILET:
                b(g, WHITE, w - 6, 8, d - 14, 0, 0, 5);
                b(g, WHITE, w - 8, 14, 22, 0, 8, 5);
                b(g, WHITE, w - 4, 18, 10, 0, 16, -d / 2 + 6);
                b(g, shiny(210, 212, 216), w - 10, 2, 22, 0, 22, 5);
                break;
            case SINK:
                b(g, WHITE, 10, 40, 10, 0, 0, -4);
                b(g, WHITE, w, 10, d - 4, 0, 40, -2);
                b(g, Mats.CHROME, 2.5, 8, 2.5, 0, 50, -d / 2 + 4);
                b(g, shiny(180, 190, 196), w - 4, 24, 1, 0, 54, -d / 2 + 0.6);   // mirror
                break;
            case TUB:
                b(g, WHITE, w, 28, d, 0, 0, 0);
                b(g, WATER, w - 10, 1, d - 10, 0, 28, 0);
                b(g, Mats.CHROME, 8, 8, 3, 0, 28, -d / 2 + 2);
                break;
            case LAMP:
                g.getChildren().add(Gfx.cylY(6, 3, DARK_WOOD, 0, -1.5, 0));
                g.getChildren().add(Gfx.cylY(1.2, 82, DARK_WOOD, 0, -43, 0));
                g.getChildren().add(Gfx.cylY(9, 18, color(200, 186, 150), 0, -90, 0));
                break;
            case RUG: {
                int[] c = RUGS[rn.nextInt(RUGS.length)];
                b(g, color(c[0] - 30, c[1] - 30, c[2] - 30), w, 0.5, d, 0, 0, 0);
                b(g, color(c[0], c[1], c[2]), w - 10, 0.7, d - 10, 0, 0, 0);
                break;
            }
            case BOX:
                b(g, CARDBOARD, w, h, d, 0, 0, 0);
                b(g, color(190, 170, 120), 5, 0.4, d + 0.4, 0, h, 0);
                if (rn.nextBoolean()) {
                    b(g, CARDBOARD, w - 6, 4, d - 6, 0, h, 0);
                }
                break;
            case CLOTHES:
                b(g, fabric(rn), w, 2.5, d, 0, 0, 0);
                b(g, fabric(rn), w - 8, 2, d - 6, 3, 2.5, 1);
                break;
            case BOOKS:
                b(g, color(110, 40, 40), w, 2.5, d, 0, 0, 0);
                b(g, color(40, 60, 100), w - 2, 2.5, d - 2, 1, 2.5, -1);
                break;
            case STAIN:
                b(g, BLOOD, w, h, d, 0, 0, 0);
                break;
            default:   // SHOES
                b(g, color(40, 34, 30), 5, 5, 11, -3.5, 0, 0);
                b(g, color(40, 34, 30), 5, 5, 11, 3.5, 0, 1);
                break;
        }
        return g;
    }
}
