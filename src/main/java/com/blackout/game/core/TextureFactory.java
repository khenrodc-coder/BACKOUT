package com.blackout.game.core;

import java.util.Random;

import javafx.scene.image.PixelWriter;
import javafx.scene.image.WritableImage;

/**
 * Procedural, tileable textures (no external files needed). Every method returns a fresh image; {@link Mats} caches them.
 * All colours are given as 0-255 RGB.
 */
public final class TextureFactory {

    private interface Px {
        void f(int x, int y, double u, double v, double[] c);
    }

    private static final class Noise {

        final int cx, cy;
        final double[][] g;

        Noise(int cx, int cy, long seed) {
            this.cx = cx;
            this.cy = cy;
            g = new double[cx][cy];
            Random r = new Random(seed);
            for (int i = 0; i < cx; i++) {
                for (int j = 0; j < cy; j++) {
                    g[i][j] = r.nextDouble();
                }
            }
        }

        double get(double u, double v) {
            u -= Math.floor(u);
            v -= Math.floor(v);
            double x = u * cx, y = v * cy;
            int i = (int) x, j = (int) y;
            double fx = x - i, fy = y - j;
            fx = fx * fx * (3 - 2 * fx);
            fy = fy * fy * (3 - 2 * fy);
            int i2 = (i + 1) % cx, j2 = (j + 1) % cy;
            double a = g[i][j] + (g[i2][j] - g[i][j]) * fx, b = g[i][j2] + (g[i2][j2] - g[i][j2]) * fx;
            return a + (b - a) * fy;
        }
    }

    private static final Noise[] N = {new Noise(4, 4, 11), new Noise(8, 8, 12), new Noise(16, 16, 13), new Noise(32, 32, 14)};
    private static final Noise STREAK = new Noise(24, 3, 15);

    private TextureFactory() {
    }

    private static double fbm(double u, double v) {
        return 0.5 * N[0].get(u, v) + 0.25 * N[1].get(u, v) + 0.15 * N[2].get(u, v) + 0.10 * N[3].get(u, v);
    }

    private static double hash(int x, int y) {
        double s = Math.sin(x * 12.9898 + y * 78.233) * 43758.5453;
        return s - Math.floor(s);
    }

    private static double sm(double a, double b, double t) {
        t = Math.max(0, Math.min(1, (t - a) / (b - a)));
        return t * t * (3 - 2 * t);
    }

    private static WritableImage make(int w, int h, Px px) {
        WritableImage img = new WritableImage(w, h);
        PixelWriter pw = img.getPixelWriter();
        double[] c = new double[4];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                c[3] = 255;
                px.f(x, y, (x + 0.5) / w, (y + 0.5) / h, c);
                pw.setArgb(x, y, ((int) clamp(c[3]) << 24) | ((int) clamp(c[0]) << 16) | ((int) clamp(c[1]) << 8) | (int) clamp(c[2]));
            }
        }
        return img;
    }

    private static double clamp(double v) {
        return Math.max(0, Math.min(255, v));
    }

    private static void set(double[] c, double r, double g, double b) {
        c[0] = r;
        c[1] = g;
        c[2] = b;
    }

    private static void mix(double[] c, double r, double g, double b, double t) {
        c[0] += (r - c[0]) * t;
        c[1] += (g - c[1]) * t;
        c[2] += (b - c[2]) * t;
    }

    private static void mul(double[] c, double k) {
        c[0] *= k;
        c[1] *= k;
        c[2] *= k;
    }

    private static boolean[][] cracks(int s, int n, long seed) {
        boolean[][] g = new boolean[s][s];
        Random r = new Random(seed);
        for (int k = 0; k < n; k++) {
            double x = r.nextDouble() * s, y = r.nextDouble() * s, a = r.nextDouble() * 6.28;
            int len = 25 + r.nextInt(70);
            for (int i = 0; i < len; i++) {
                a += (r.nextDouble() - 0.5) * 0.7;
                x += Math.cos(a);
                y += Math.sin(a);
                g[((int) x % s + s) % s][((int) y % s + s) % s] = true;
                if (r.nextDouble() < 0.04) {
                    double bx = x, by = y, ba = a + (r.nextBoolean() ? 1 : -1) * 0.9;
                    for (int j = 0; j < len / 3; j++) {
                        ba += (r.nextDouble() - 0.5) * 0.6;
                        bx += Math.cos(ba);
                        by += Math.sin(ba);
                        g[((int) bx % s + s) % s][((int) by % s + s) % s] = true;
                    }
                }
            }
        }
        return g;
    }

    // ---------------- terrain ----------------
    public static WritableImage grass(int s, double r, double g, double b, double dryAmount) {
        return make(s, s, (x, y, u, v, c) -> {
            double n = fbm(u * 2, v * 2), blade = N[3].get(u * 8, v * 8) * 0.35 + hash(x, y) * 0.18;
            set(c, r, g, b);
            mul(c, 0.55 + 0.9 * n + blade - 0.2);
            double dry = sm(0.55, 0.72, fbm(u * 3 + 0.3, v * 3 + 0.7));
            mix(c, 120, 100, 58, dry * dryAmount);
            mix(c, 30, 40, 22, sm(0.6, 0.8, fbm(u * 5 + 0.1, v * 5)) * 0.5);
        });
    }

    public static WritableImage dirt(int s, double r, double g, double b, boolean wet) {
        return make(s, s, (x, y, u, v, c) -> {
            set(c, r, g, b);
            mul(c, 0.6 + 0.8 * fbm(u * 3, v * 3) + (hash(x, y) - 0.5) * 0.15);
            if (hash(x * 3, y * 7) > 0.985) {
                mix(c, 150, 140, 125, 0.8);
            }
            if (wet) {
                mul(c, 0.7 + 0.3 * sm(0.4, 0.6, fbm(u * 4, v * 4)));
            }
        });
    }

    public static WritableImage gravel(int s) {
        return make(s, s, (x, y, u, v, c) -> {
            double h = hash(x / 2, y / 2);
            set(c, 105, 100, 94);
            mul(c, 0.55 + h * 0.7 + 0.3 * fbm(u * 4, v * 4));
            if (hash(x, y) > 0.93) {
                mul(c, 0.6);
            }
        });
    }

    public static WritableImage asphalt(int s, long seed) {
        boolean[][] cr = cracks(s, 9, seed);
        return make(s, s, (x, y, u, v, c) -> {
            set(c, 54, 56, 58);
            mul(c, 0.7 + 0.5 * fbm(u * 3, v * 3) + (hash(x, y) - 0.5) * 0.18);
            if (hash(x * 5, y * 3) > 0.98) {
                mix(c, 150, 150, 150, 0.6);
            }
            mul(c, 1 - 0.35 * sm(0.58, 0.75, fbm(u * 2 + 0.4, v * 2)));
            mix(c, 70, 66, 60, 0.35 * sm(0.62, 0.8, fbm(u * 3 + 0.8, v * 3 + 0.2)));
            if (cr[x][y]) {
                mul(c, 0.25);
            }
        });
    }

    // ---------------- building materials ----------------
    public static WritableImage brick(int s, double r, double g, double b, long seed) {
        boolean[][] cr = cracks(s, 6, seed);
        return make(s, s, (x, y, u, v, c) -> {
            int row = y / 16, off = row % 2 == 0 ? 0 : 20, bx = (x + off) % 40, by = y % 16;
            boolean mortar = bx < 2 || by < 2;
            if (mortar) {
                set(c, 120, 116, 108);
                mul(c, 0.7 + 0.4 * hash(x, y));
            } else {
                double vr = hash(row, (x + off) / 40);
                set(c, r, g, b);
                mul(c, 0.72 + 0.5 * vr + 0.25 * fbm(u * 6, v * 6) - 0.1);
            }
            double streak = STREAK.get(u * 3 + 0.17, v * 0.5);
            mul(c, 1 - 0.35 * sm(0.55, 0.8, streak) * (0.4 + v));
            mix(c, 55, 82, 40, sm(0.62, 0.78, fbm(u * 5, v * 4)) * 0.75 * sm(0.55, 1.0, v));
            if (cr[x][y]) {
                mul(c, 0.2);
            }
        });
    }

    public static WritableImage plaster(int s, double r, double g, double b, long seed) {
        boolean[][] cr = cracks(s, 8, seed);
        return make(s, s, (x, y, u, v, c) -> {
            set(c, r, g, b);
            mul(c, 0.78 + 0.35 * fbm(u * 4, v * 4) + (hash(x, y) - 0.5) * 0.08);
            double peel = sm(0.55, 0.62, fbm(u * 3 + 0.5, v * 3 + 0.2));
            mix(c, 110, 108, 100, peel * 0.9);
            if (peel > 0.5 && hash(x / 3, y / 3) > 0.7) {
                mul(c, 0.75);
            }
            mul(c, 1 - 0.45 * sm(0.55, 0.82, STREAK.get(u * 2 + 0.3, v * 0.5)) * (0.3 + v));
            mix(c, 50, 78, 38, sm(0.64, 0.8, fbm(u * 6 + 0.3, v * 3)) * 0.7 * sm(0.6, 1, v));
            if (cr[x][y]) {
                mul(c, 0.25);
            }
        });
    }

    /**
     * One window bay of a building facade (tile it up and across). Style 0: glass office curtain wall, 1: brick apartment with
     * small windows, 2: concrete block with medium windows.
     */
    public static WritableImage facade(int s, double r, double g, double b, int style, long seed) {
        return make(s, s, (x, y, u, v, c) -> {
            set(c, r, g, b);
            mul(c, 0.78 + 0.34 * fbm(u * 3 + seed, v * 3) + (hash(x, y) - 0.5) * 0.08);
            if (style == 1) {
                double rows = v * 12, off = ((int) rows % 2) * 0.5, col = (u * 6 + off) % 1;
                if (rows % 1 < 0.1 || col < 0.06) {
                    mul(c, 0.8);
                }
            }
            double x0 = style == 0 ? 0.04 : style == 1 ? 0.27 : 0.17, x1 = 1 - x0;
            double y0 = style == 0 ? 0.08 : 0.2, y1 = style == 0 ? 0.84 : 0.8;
            boolean glass = u >= x0 && u <= x1 && v >= y0 && v <= y1;
            boolean frame = u >= x0 - 0.03 && u <= x1 + 0.03 && v >= y0 - 0.03 && v <= y1 + 0.03;
            if (glass) {
                double t = (v - y0) / (y1 - y0);
                set(c, 24 + 46 * (1 - t), 40 + 56 * (1 - t), 58 + 62 * (1 - t));
                mul(c, 0.9 + 0.2 * fbm(u * 6, v * 6));
                if (Math.abs(u - 0.5) < 0.012 || (style != 0 && Math.abs(v - (y0 + y1) / 2) < 0.012)) {
                    set(c, 26, 28, 30);
                }
            } else if (frame) {
                set(c, 30, 32, 34);
            } else if (v > y1 + 0.03 && v < y1 + 0.07 && u > x0 - 0.05 && u < x1 + 0.05) {
                mul(c, 1.25);                                   // sill
            }
        });
    }

    public static WritableImage concrete(int s, long seed) {
        boolean[][] cr = cracks(s, 10, seed);
        return make(s, s, (x, y, u, v, c) -> {
            set(c, 122, 124, 118);
            mul(c, 0.72 + 0.45 * fbm(u * 4, v * 4) + (hash(x, y) - 0.5) * 0.12);
            mul(c, 1 - 0.4 * sm(0.55, 0.85, STREAK.get(u * 2, v * 0.4)) * (0.3 + v));
            mix(c, 60, 84, 44, sm(0.66, 0.82, fbm(u * 5 + 0.7, v * 5)) * 0.65);
            mix(c, 120, 70, 40, sm(0.7, 0.85, fbm(u * 6, v * 2 + 0.3)) * 0.4 * v);
            if (cr[x][y]) {
                mul(c, 0.22);
            }
        });
    }

    public static WritableImage wood(int s, double r, double g, double b, double paint, long seed) {
        return make(s, s, (x, y, u, v, c) -> {
            int plank = x / 24, px = x % 24;
            double grain = STREAK.get(u * 12 + hash(plank, 3), v * 0.5 + hash(plank, 9)) * 0.5 + hash(plank, 1) * 0.5;
            set(c, r, g, b);
            mul(c, 0.55 + 0.8 * grain);
            mix(c, 120, 118, 110, 0.35 * sm(0.4, 0.7, fbm(u * 3, v * 3)));
            if (paint > 0) {
                double flake = sm(0.5, 0.6, fbm(u * 4 + hash(plank, 5), v * 4));
                mix(c, 168, 160, 140, paint * (1 - flake));
            }
            if (px < 1 || px > 22) {
                mul(c, 0.35);
            }
            mul(c, 1 - 0.3 * sm(0.6, 0.85, STREAK.get(u * 5, v * 0.3)) * (0.3 + v));
        });
    }

    public static WritableImage rust(int s, double r, double g, double b, long seed) {
        return make(s, s, (x, y, u, v, c) -> {
            set(c, r, g, b);
            mul(c, 0.7 + 0.5 * fbm(u * 6, v * 6) + (hash(x, y) - 0.5) * 0.1);
            double rs = sm(0.45, 0.66, fbm(u * 3 + 0.2, v * 3 + 0.9) + 0.25 * STREAK.get(u * 8, v * 0.5));
            mix(c, 150, 74, 32, rs * 0.85);
            mix(c, 90, 44, 22, sm(0.6, 0.8, fbm(u * 8, v * 8)) * rs * 0.5);
            if (hash(x * 3, y) > 0.995 && rs < 0.3) {
                mix(c, 200, 200, 205, 0.7);
            }
        });
    }

    public static WritableImage carPaint(int s, double r, double g, double b, long seed) {
        Random rn = new Random(seed);
        double[][] sc = new double[26][4];
        for (double[] q : sc) {
            q[0] = rn.nextDouble() * s;
            q[1] = rn.nextDouble() * s;
            q[2] = rn.nextDouble() * 6.28;
            q[3] = 6 + rn.nextDouble() * 20;
        }
        boolean[][] scratch = new boolean[s][s];
        for (double[] q : sc) {
            for (int i = 0; i < q[3]; i++) {
                int px = (int) (q[0] + Math.cos(q[2]) * i), py = (int) (q[1] + Math.sin(q[2]) * i);
                if (px >= 0 && py >= 0 && px < s && py < s) {
                    scratch[px][py] = true;
                }
            }
        }
        return make(s, s, (x, y, u, v, c) -> {
            set(c, r, g, b);
            mul(c, 0.5 + 0.55 * fbm(u * 3, v * 3));
            mix(c, 150, 145, 135, 0.35 * sm(0.5, 0.7, fbm(u * 2 + 0.6, v * 2)));
            double rs = sm(0.52, 0.7, fbm(u * 4 + 0.3, v * 4 + 0.1) + 0.25 * v);
            mix(c, 140, 70, 30, rs * 0.8);
            mix(c, 70, 58, 44, 0.35 * v);
            if (scratch[x][y]) {
                mix(c, 190, 188, 180, 0.7);
            }
        });
    }

    public static WritableImage fabric(int s, double r, double g, double b) {
        return make(s, s, (x, y, u, v, c) -> {
            set(c, r, g, b);
            mul(c, 0.78 + 0.25 * ((x + y) % 4 < 2 ? 1 : 0.6) + 0.35 * fbm(u * 5, v * 5) - 0.2);
            mul(c, 1 - 0.45 * sm(0.62, 0.8, fbm(u * 3 + 0.4, v * 4 + 0.2)));
            mix(c, 70, 58, 44, 0.35 * sm(0.55, 1, v));
            if (y % 32 == 0 || x % 64 == 0) {
                mul(c, 0.7);
            }
        });
    }

    // ---------------- vegetation ----------------
    public static WritableImage bark(int s, double r, double g, double b, boolean birch) {
        return make(s, s, (x, y, u, v, c) -> {
            double st = STREAK.get(u * 2, v * 1);
            set(c, r, g, b);
            mul(c, 0.5 + 0.9 * (0.5 * st + 0.5 * N[2].get(u * 2, v * 6)));
            if (birch) {
                double band = sm(0.62, 0.7, N[2].get(u * 1, v * 8));
                mix(c, 25, 24, 22, band * 0.9);
            }
            if (hash(x, y) > 0.97) {
                mul(c, 0.6);
            }
        });
    }

    public static WritableImage leaf(int s, double r, double g, double b) {
        return make(s, s, (x, y, u, v, c) -> {
            set(c, r, g, b);
            double cl = N[3].get(u * 4, v * 4) * 0.6 + N[2].get(u * 2, v * 2) * 0.4;
            mul(c, 0.4 + 1.1 * cl + (hash(x, y) - 0.5) * 0.3);
            if (hash(x / 2, y / 2) > 0.9) {
                mix(c, r * 1.6, g * 1.5, b * 1.2, 0.5);
            }
            mix(c, 120, 90, 40, 0.25 * sm(0.62, 0.8, fbm(u * 3 + 0.5, v * 3)));
        });
    }

    public static WritableImage grassBlades(int w, int h, long seed, double r, double g, double b) {
        WritableImage img = new WritableImage(w, h);
        PixelWriter pw = img.getPixelWriter();
        Random rn = new Random(seed);
        int blades = 16;
        for (int k = 0; k < blades; k++) {
            double bx = rn.nextDouble() * w, tipX = bx + (rn.nextDouble() - 0.5) * w * 0.5, tipH = h * (0.45 + rn.nextDouble() * 0.55);
            double sh = 0.6 + rn.nextDouble() * 0.7, bw = 1.5 + rn.nextDouble() * 1.5;
            for (int j = 0; j < tipH; j++) {
                double t = j / tipH, cx = bx + (tipX - bx) * t * t, half = bw * (1 - t) + 0.4;
                for (int x = (int) (cx - half); x <= cx + half; x++) {
                    int y = h - 1 - j;
                    if (x >= 0 && x < w && y >= 0 && y < h) {
                        double l = 0.45 + 0.7 * t;
                        pw.setArgb(x, y, 0xFF000000 | ((int) clamp(r * l * sh) << 16) | ((int) clamp(g * l * sh) << 8) | (int) clamp(b * l * sh));
                    }
                }
            }
        }
        return img;
    }

    public static WritableImage decal(int s, double r, double g, double b) {
        return make(s, s, (x, y, u, v, c) -> {
            double dx = u - 0.5, dy = v - 0.5, d = Math.sqrt(dx * dx + dy * dy) * 2;
            double edge = d + (fbm(u * 5, v * 5) - 0.5) * 0.7;
            set(c, r, g, b);
            mul(c, 0.7 + 0.6 * fbm(u * 6, v * 6) + (hash(x, y) - 0.5) * 0.15);
            c[3] = 255 * (1 - sm(0.45, 0.9, edge));
        });
    }

    public static WritableImage mist(int s) {
        return make(s, s, (x, y, u, v, c) -> {
            set(c, 150, 158, 168);
            c[3] = 255 * sm(0.38, 0.75, fbm(u * 2, v * 2)) * 0.75;
        });
    }

    // ---------------- water ----------------
    public static WritableImage water(int s, double shift, double r, double g, double b) {
        return make(s, s, (x, y, u, v, c) -> {
            double us = u - shift;
            double w1 = Math.sin(Math.PI * 2 * (3 * us + fbm(us * 2, v * 2) * 2)), w2 = Math.sin(Math.PI * 2 * (5 * us - 4 * v + fbm(us * 3 + 0.4, v * 3) * 1.5));
            double h = 0.5 + 0.25 * w1 + 0.2 * w2;
            set(c, r, g, b);
            mul(c, 0.65 + 0.7 * h);
            double glint = Math.pow(Math.max(0, w1 * w2), 3);
            mix(c, 210, 225, 235, Math.min(0.6, glint * 1.3));
            mix(c, 200, 205, 200, 0.35 * sm(0.68, 0.82, fbm(us * 8 + 0.2, v * 8)));
            c[3] = 225;
        });
    }
}
